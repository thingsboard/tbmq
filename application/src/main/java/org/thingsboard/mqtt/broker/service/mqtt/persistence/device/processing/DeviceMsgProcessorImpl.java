/**
 * Copyright © 2016-2020 The Thingsboard Authors
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

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.data.PersistedPacketType;
import org.thingsboard.mqtt.broker.dao.DbConnectionChecker;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgService;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.mqtt.ClientSession;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionReader;
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
    private static final int BLANK_PACKET_ID = -1;
    private static final long BLANK_SERIAL_NUMBER = -1L;

    @Value("${queue.device-persisted-msg.detect-msg-duplication:false}")
    private boolean detectMsgDuplication;

    private final ClientSessionReader clientSessionReader;
    private final ClientLogger clientLogger;
    private final DbConnectionChecker dbConnectionChecker;
    private final DownLinkProxy downLinkProxy;
    private final DeviceMsgAcknowledgeStrategyFactory ackStrategyFactory;
    private final DeviceMsgService deviceMsgService;
    private final DevicePacketIdAndSerialNumberService serialNumberService;

    @Override
    public List<DevicePublishMsg> persistMessages(List<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> messages, DeviceProcessorStats stats, String consumerId) {
        Set<String> clientIds = messages.stream().map(TbProtoQueueMsg::getKey).collect(Collectors.toSet());
        for (String clientId : clientIds) {
            clientLogger.logEvent(clientId, this.getClass(), "Start persisting DEVICE msg");
        }

        List<DevicePublishMsg> devicePublishMessages = toDevicePublishMsgs(messages);
        Map<String, PacketIdAndSerialNumber> lastPacketIdAndSerialNumbers = null;
        boolean isDbConnected = dbConnectionChecker.isDbConnected()
                && (lastPacketIdAndSerialNumbers = tryGetLastPacketIdAndSerialNumber(clientIds)) != null;
        if (isDbConnected) {
            persistDeviceMsgs(devicePublishMessages, lastPacketIdAndSerialNumbers, consumerId, stats);;
        }

        for (String clientId : clientIds) {
            clientLogger.logEvent(clientId, this.getClass(), "Finished persisting DEVICE msg");
        }
        return devicePublishMessages;
    }

    @Override
    public void deliverMessages(List<DevicePublishMsg> devicePublishMessages) {
        for (DevicePublishMsg devicePublishMsg : devicePublishMessages) {
            ClientSession clientSession = clientSessionReader.getClientSession(devicePublishMsg.getClientId());
            if (clientSession == null) {
                log.debug("[{}] Client session not found for persisted msg.", devicePublishMsg.getClientId());
            } else if (!clientSession.isConnected()) {
                // TODO: think if it's OK to ignore msg if session is 'disconnected'
                log.trace("[{}] Client session is disconnected.", devicePublishMsg.getClientId());
            } else {
                String targetServiceId = clientSession.getSessionInfo().getServiceId();
                if (messageWasPersisted(devicePublishMsg)) {
                    downLinkProxy.sendPersistentMsg(targetServiceId, devicePublishMsg.getClientId(), ProtoConverter.toDevicePublishMsgProto(devicePublishMsg));
                } else {
                    downLinkProxy.sendBasicMsg(targetServiceId, devicePublishMsg.getClientId(), ProtoConverter.convertToPublishProtoMessage(devicePublishMsg));
                }
            }
        }
    }

    private boolean messageWasPersisted(DevicePublishMsg devicePublishMsg) {
        return !devicePublishMsg.getPacketId().equals(BLANK_PACKET_ID) && !devicePublishMsg.getSerialNumber().equals(BLANK_SERIAL_NUMBER);
    }

    private void persistDeviceMsgs(List<DevicePublishMsg> devicePublishMessages, Map<String, PacketIdAndSerialNumber> lastPacketIdAndSerialNumbers, String consumerId, DeviceProcessorStats stats) {
        setPacketIdAndSerialNumber(devicePublishMessages, lastPacketIdAndSerialNumbers);

        DeviceAckStrategy ackStrategy = ackStrategyFactory.newInstance(consumerId);
        DevicePackProcessingContext ctx = new DevicePackProcessingContext(devicePublishMessages, detectMsgDuplication);
        while (!Thread.interrupted()) {
            try {
                // TODO: think if we need transaction here
                // TODO: think about case when client is 'clearing session' at this moment
                serialNumberService.saveLastSerialNumbers(lastPacketIdAndSerialNumbers);
                deviceMsgService.save(devicePublishMessages, ctx.detectMsgDuplication());
                ctx.onSuccess();
            } catch (DuplicateKeyException e) {
                log.warn("[{}] Duplicate serial number detected, will save with rewrite, detailed error - {}", consumerId, e.getMessage());
                ctx.disableMsgDuplicationDetection();
            } catch (Exception e) {
                log.warn("[{}] Failed to save device messages. Exception - {}, reason - {}.", consumerId, e.getClass().getSimpleName(), e.getMessage());
            }

            DeviceProcessingDecision decision = ackStrategy.analyze(ctx);

            stats.log(devicePublishMessages.size(), ctx.isSuccessful(), decision.isCommit());

            if (decision.isCommit()) {
                break;
            }
        }
    }

    private void setPacketIdAndSerialNumber(List<DevicePublishMsg> devicePublishMessages, Map<String, PacketIdAndSerialNumber> lastPacketIdAndSerialNumbers) {
        for (DevicePublishMsg devicePublishMessage : devicePublishMessages) {
            PacketIdAndSerialNumberDto packetIdAndSerialNumberDto = getAndIncrementPacketIdAndSerialNumberDto(lastPacketIdAndSerialNumbers, devicePublishMessage.getClientId());
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

    private PacketIdAndSerialNumberDto getAndIncrementPacketIdAndSerialNumberDto(Map<String, PacketIdAndSerialNumber> lastPacketIdAndSerialNumbers, String clientId) {
        PacketIdAndSerialNumber packetIdAndSerialNumber = lastPacketIdAndSerialNumbers.computeIfAbsent(clientId, id ->
                new PacketIdAndSerialNumber(new AtomicInteger(1), new AtomicLong(0)));
        AtomicInteger packetIdAtomic = packetIdAndSerialNumber.getPacketId();
        packetIdAtomic.incrementAndGet();
        packetIdAtomic.compareAndSet(0xffff, 1);
        return new PacketIdAndSerialNumberDto(packetIdAtomic.get(), packetIdAndSerialNumber.getSerialNumber().incrementAndGet());
    }

    @Getter
    @AllArgsConstructor
    private static class PacketIdAndSerialNumberDto {
        private final int packetId;
        private final long serialNumber;
    }

    private List<DevicePublishMsg> toDevicePublishMsgs(List<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> msgs) {
        return msgs.stream()
                .map(protoMsg -> ProtoConverter.toDevicePublishMsg(protoMsg.getKey(), protoMsg.getValue()))
                .map(devicePublishMsg -> devicePublishMsg.toBuilder()
                        .packetId(BLANK_PACKET_ID)
                        .serialNumber(BLANK_SERIAL_NUMBER)
                        .packetType(PersistedPacketType.PUBLISH)
                        .time(System.currentTimeMillis())
                        .build())
                .collect(Collectors.toList());
    }
}
