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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.device;

import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgService;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueConsumer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.DevicePersistenceMsgQueueFactory;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsgDeliveryService;
import org.thingsboard.mqtt.broker.service.mqtt.client.ClientSessionCtxService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.device.processing.DeviceAckStrategy;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.device.processing.DeviceMsgAcknowledgeStrategyFactory;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.device.processing.DeviceMsgSerialNumberService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.device.processing.DevicePackProcessingContext;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.device.processing.DeviceProcessingDecision;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.SessionState;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceMsgQueueConsumerImpl implements DeviceMsgQueueConsumer {
    private final ExecutorService consumersExecutor = Executors.newCachedThreadPool(ThingsBoardThreadFactory.forName("device-persisted-msg-consumer"));
    private final List<TbQueueConsumer<TbProtoQueueMsg<QueueProtos.DevicePublishMsgProto>>> consumers = new ArrayList<>();

    private volatile boolean stopped = false;

    @Value("${queue.device-persisted-msg.consumers-count}")
    private int consumersCount;
    @Value("${queue.device-persisted-msg.poll-interval}")
    private long pollDuration;

    private final DevicePersistenceMsgQueueFactory devicePersistenceMsgQueueFactory;
    private final DeviceMsgAcknowledgeStrategyFactory ackStrategyFactory;
    private final DeviceMsgService deviceMsgService;
    private final DeviceMsgSerialNumberService serialNumberService;
    private final ClientSessionCtxService clientSessionCtxService;
    private final PublishMsgDeliveryService msgDeliveryService;


    @PostConstruct
    public void init() {
        for (int i = 0; i < consumersCount; i++) {
            String consumerId = Integer.toString(i);
            TbQueueConsumer<TbProtoQueueMsg<QueueProtos.DevicePublishMsgProto>> consumer = devicePersistenceMsgQueueFactory.createConsumer(consumerId);
            consumers.add(consumer);
            consumer.subscribe();
            launchConsumer(consumerId, consumer);
        }
    }

    private void launchConsumer(String consumerId, TbQueueConsumer<TbProtoQueueMsg<QueueProtos.DevicePublishMsgProto>> consumer) {
        consumersExecutor.submit(() -> {
            while (!stopped) {
                try {
                    List<TbProtoQueueMsg<QueueProtos.DevicePublishMsgProto>> msgs = consumer.poll(pollDuration);
                    if (msgs.isEmpty()) {
                        continue;
                    }

                    Map<String, AtomicLong> lastSerialNumbers = getSerialNumbersMap(msgs);

                    List<DevicePublishMsg> devicePublishMessages = toDevicePublishMsgs(msgs,
                            clientId -> {
                                AtomicLong clientLastSerialNumber = lastSerialNumbers.computeIfAbsent(clientId, id -> new AtomicLong(0));
                                return clientLastSerialNumber.incrementAndGet();
                            });

                    Map<String, Long> newSerialNumbers = lastSerialNumbers.entrySet().stream()
                            .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().get()));

                    DeviceAckStrategy ackStrategy = ackStrategyFactory.newInstance(consumerId);
                    DevicePackProcessingContext ctx = new DevicePackProcessingContext(devicePublishMessages);
                    while (!stopped) {
                        try {
                            // TODO: think if we need transaction here
                            serialNumberService.saveLastSerialNumbers(newSerialNumbers);
                            deviceMsgService.save(devicePublishMessages);
                            ctx.onSuccess();
                        } catch (Exception e) {
                            log.warn("[{}] Failed to save device messages.", consumerId);
                        }

                        DeviceProcessingDecision decision = ackStrategy.analyze(ctx);
                        if (decision.isCommit()) {
                            consumer.commit();
                            break;
                        }
                    }

                    // TODO: push to DEVICE actor after saving in DB
                    // TODO: add logic for saving packetId after sending to client
                    // TODO: add logic for deleting device's message after acknowledge
                    for (DevicePublishMsg devicePublishMsg : devicePublishMessages) {
                        String clientId = devicePublishMsg.getClientId();
                        ClientSessionCtx sessionCtx = clientSessionCtxService.getClientSessionCtx(clientId);
                        if (sessionCtx != null && sessionCtx.getSessionState() == SessionState.CONNECTED) {
                            int packetId = sessionCtx.nextMsgId();
                            deviceMsgService.updatePacketId(clientId, devicePublishMsg.getSerialNumber(), packetId);
                            msgDeliveryService.sendPublishMsgToClient(sessionCtx, packetId, devicePublishMsg.getTopic(),
                                    MqttQoS.valueOf(devicePublishMsg.getQos()), false, devicePublishMsg.getPayload());
                        }
                    }
                } catch (Exception e) {
                    if (!stopped) {
                        log.error("[{}] Failed to process messages from queue.", consumerId, e);
                        try {
                            Thread.sleep(pollDuration);
                        } catch (InterruptedException e2) {
                            log.trace("[{}] Failed to wait until the server has capacity to handle new requests", consumerId, e2);
                        }
                    }
                }
            }
            log.info("[{}] Device Persisted Msg Consumer stopped.", consumerId);
        });
    }

    private Map<String, AtomicLong> getSerialNumbersMap(List<TbProtoQueueMsg<QueueProtos.DevicePublishMsgProto>> msgs) {
        Set<String> clientIds = msgs.stream().map(TbProtoQueueMsg::getKey).collect(Collectors.toSet());
        return serialNumberService.getLastSerialNumbers(clientIds).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> new AtomicLong(entry.getValue())));
    }

    private List<DevicePublishMsg> toDevicePublishMsgs(List<TbProtoQueueMsg<QueueProtos.DevicePublishMsgProto>> msgs,
                                                       Function<String, Long> getSerialNumberFunction) {
        return msgs.stream()
                .map(TbProtoQueueMsg::getValue)
                .map(ProtoConverter::toDevicePublishMsg)
                .map(devicePublishMsg -> devicePublishMsg.toBuilder()
                        .serialNumber(getSerialNumberFunction.apply(devicePublishMsg.getClientId()))
                        .time(System.currentTimeMillis())
                        .build())
                .collect(Collectors.toList());
    }

    @PreDestroy
    public void destroy() {
        stopped = true;
        consumers.forEach(TbQueueConsumer::unsubscribeAndClose);
        consumersExecutor.shutdownNow();
    }

}
