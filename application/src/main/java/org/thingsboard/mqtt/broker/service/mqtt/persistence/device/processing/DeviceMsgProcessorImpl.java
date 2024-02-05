/**
 * Copyright © 2016-2023 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.mqtt.broker.service.mqtt.persistence.device.processing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.util.BrokerConstants;
import org.thingsboard.mqtt.broker.dao.DbConnectionChecker;
import org.thingsboard.mqtt.broker.dao.client.device.DevicePacketIdAndSerialNumberService;
import org.thingsboard.mqtt.broker.dao.client.device.PacketIdAndSerialNumber;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgService;
import org.thingsboard.mqtt.broker.dto.PacketIdAndSerialNumberDto;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos.PublishMsgProto;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.service.processing.downlink.DownLinkProxy;
import org.thingsboard.mqtt.broker.service.stats.DeviceProcessorStats;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceMsgProcessorImpl implements DeviceMsgProcessor {

    private final ClientSessionCache clientSessionCache;
    private final ClientLogger clientLogger;
    private final DbConnectionChecker dbConnectionChecker;
    private final DownLinkProxy downLinkProxy;
    private final DeviceMsgAcknowledgeStrategyFactory ackStrategyFactory;
    private final DeviceMsgService deviceMsgService;
    private final DevicePacketIdAndSerialNumberService serialNumberService;

    @Override
    public List<DevicePublishMsg> persistMessages(List<TbProtoQueueMsg<PublishMsgProto>> messages, DeviceProcessorStats stats, String consumerId) {
        Set<String> clientIds = messages.stream().map(TbProtoQueueMsg::getKey).collect(Collectors.toSet());
        for (String clientId : clientIds) {
            clientLogger.logEvent(clientId, this.getClass(), "Start persisting DEVICE msg");
        }

        List<DevicePublishMsg> devicePublishMessages = toDevicePublishMsgs(messages);

        Map<String, PacketIdAndSerialNumber> lastPacketIdAndSerialNumbers = null;
        boolean isDbConnected = dbConnectionChecker.isDbConnected()
                && (lastPacketIdAndSerialNumbers = tryGetLastPacketIdAndSerialNumber(clientIds)) != null;
        if (isDbConnected) {
            persistDeviceMsgs(devicePublishMessages, lastPacketIdAndSerialNumbers, consumerId, stats);
        }

        for (String clientId : clientIds) {
            clientLogger.logEvent(clientId, this.getClass(), "Finished persisting DEVICE msg");
        }
        return devicePublishMessages;
    }

    @Override
    public void deliverMessages(List<DevicePublishMsg> devicePublishMessages) {
        for (DevicePublishMsg devicePublishMsg : devicePublishMessages) {
            ClientSessionInfo clientSessionInfo = clientSessionCache.getClientSessionInfo(devicePublishMsg.getClientId());
            if (clientSessionInfo == null) {
                if (log.isDebugEnabled()) {
                    log.debug("[{}] Client session is not found for persisted messages.", devicePublishMsg.getClientId());
                }
            } else if (!clientSessionInfo.isConnected()) {
                if (log.isTraceEnabled()) {
                    log.trace("[{}] Client session is disconnected.", devicePublishMsg.getClientId());
                }
            } else {
                String targetServiceId = clientSessionInfo.getServiceId();
                if (messageWasPersisted(devicePublishMsg)) {
                    downLinkProxy.sendPersistentMsg(
                            targetServiceId,
                            devicePublishMsg.getClientId(),
                            devicePublishMsg);
                } else {
                    downLinkProxy.sendBasicMsg(
                            targetServiceId,
                            devicePublishMsg.getClientId(),
                            ProtoConverter.convertToPublishMsgProto(devicePublishMsg));
                }
            }
        }
    }

    private boolean messageWasPersisted(DevicePublishMsg devicePublishMsg) {
        return !devicePublishMsg.getPacketId().equals(BrokerConstants.BLANK_PACKET_ID) && !devicePublishMsg.getSerialNumber().equals(BrokerConstants.BLANK_SERIAL_NUMBER);
    }

    private void persistDeviceMsgs(List<DevicePublishMsg> devicePublishMessages,
                                   Map<String, PacketIdAndSerialNumber> lastPacketIdAndSerialNumbers,
                                   String consumerId, DeviceProcessorStats stats) {
        setPacketIdAndSerialNumber(devicePublishMessages, lastPacketIdAndSerialNumbers);

        DeviceAckStrategy ackStrategy = ackStrategyFactory.newInstance(consumerId);
        DevicePackProcessingContext ctx = new DevicePackProcessingContext(devicePublishMessages);
        while (!Thread.interrupted()) {
            try {
                // TODO: think if we need transaction here
                // TODO: think about case when client is 'clearing session' at this moment
                serialNumberService.saveLastSerialNumbers(lastPacketIdAndSerialNumbers);
                deviceMsgService.save(devicePublishMessages, ctx.detectMsgDuplication());
                ctx.onSuccess();
            } catch (DuplicateKeyException e) {
                log.warn("[{}] Duplicate serial number detected, will save with rewrite", consumerId, e);
                ctx.disableMsgDuplicationDetection();
            } catch (Exception e) {
                log.warn("[{}] Failed to save device messages", consumerId, e);
            }

            DeviceProcessingDecision decision = ackStrategy.analyze(ctx);

            stats.log(devicePublishMessages.size(), ctx.isSuccessful(), decision.isCommit());

            if (decision.isCommit()) {
                break;
            }
        }
    }

    private void setPacketIdAndSerialNumber(List<DevicePublishMsg> devicePublishMessages,
                                            Map<String, PacketIdAndSerialNumber> lastPacketIdAndSerialNumbers) {
        for (DevicePublishMsg devicePublishMessage : devicePublishMessages) {
            PacketIdAndSerialNumberDto packetIdAndSerialNumberDto = getAndIncrementPacketIdAndSerialNumber(
                    lastPacketIdAndSerialNumbers,
                    devicePublishMessage.getClientId());

            devicePublishMessage.setPacketId(packetIdAndSerialNumberDto.getPacketId());
            devicePublishMessage.setSerialNumber(packetIdAndSerialNumberDto.getSerialNumber());
        }
    }

    private Map<String, PacketIdAndSerialNumber> tryGetLastPacketIdAndSerialNumber(Set<String> clientIds) {
        try {
            return serialNumberService.getLastPacketIdAndSerialNumber(clientIds);
        } catch (DataAccessResourceFailureException e) {
            log.warn("Cannot get last packetId and serialNumbers since database connection is lost.");
            return null;
        }
    }

    private PacketIdAndSerialNumberDto getAndIncrementPacketIdAndSerialNumber(Map<String, PacketIdAndSerialNumber> lastPacketIdAndSerialNumbers,
                                                                              String clientId) {
        PacketIdAndSerialNumber packetIdAndSerialNumber = lastPacketIdAndSerialNumbers.computeIfAbsent(clientId, id ->
                new PacketIdAndSerialNumber(new AtomicInteger(0), new AtomicLong(-1)));
        AtomicInteger packetIdAtomic = packetIdAndSerialNumber.getPacketId();
        packetIdAtomic.incrementAndGet();
        packetIdAtomic.compareAndSet(0xffff, 1);
        return new PacketIdAndSerialNumberDto(packetIdAtomic.get(), packetIdAndSerialNumber.getSerialNumber().incrementAndGet());
    }

    private List<DevicePublishMsg> toDevicePublishMsgs(List<TbProtoQueueMsg<PublishMsgProto>> msgs) {
        return msgs.stream()
                .map(protoMsg -> ProtoConverter.protoToDevicePublishMsg(protoMsg.getKey(), protoMsg.getValue(), protoMsg.getHeaders()))
                .collect(Collectors.toList());
    }
}
