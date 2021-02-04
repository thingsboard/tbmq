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
package org.thingsboard.mqtt.broker.service.mqtt.persistence;

import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.ApplicationPersistenceMsgQueueFactory;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.mqtt.PacketIdAndOffset;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import javax.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
@RequiredArgsConstructor
public class DefaultApplicationPersistenceProcessor implements ApplicationPersistenceProcessor {

    private final Map<String, PersistenceConsumerInfo> applicationPersistentConsumers = new ConcurrentHashMap<>();

    private final ApplicationPersistenceMsgQueueFactory applicationPersistenceMsgQueueFactory;
    private final MqttMessageGenerator mqttMessageGenerator;
    private final ExecutorService persistedMsgsConsumeExecutor = Executors.newCachedThreadPool(ThingsBoardThreadFactory.forName("persisted-msg-consumers"));

    @Value("${queue.application-persisted-msg.poll-interval}")
    private long pollDuration;

    @Override
    public void acknowledgeSuccessfulDelivery(String clientId, long offset) {
        log.trace("Executing acknowledgeSuccessfulDelivery [{}][{}]", clientId, offset);
        PersistenceConsumerInfo persistenceConsumerInfo = getPersistenceConsumerInfo(clientId);
        AtomicLong persistedOffset = persistenceConsumerInfo.persistedOffset;
        long previousOffsetValue = persistedOffset.get();
        if (previousOffsetValue > offset) {
            log.warn("[{}] Previous offset is bigger than new. Previous offset - {}, new offset - {}.",
                    clientId, previousOffsetValue, offset);
            return;
        }
        if (previousOffsetValue != offset - 1) {
            log.debug("[{}] New offset is bigger than previous by {} points.", clientId, offset - previousOffsetValue);
        }
        persistedOffset.getAndSet(offset);

        TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> consumer = persistenceConsumerInfo.consumer;
        consumer.commit(0, offset);
    }

    @Override
    public void startConsumingPersistedMsgs(String clientId, ClientSessionCtx clientSessionCtx) {
        clientSessionCtx.getIsProcessingPersistedMsgs().getAndSet(true);
        persistedMsgsConsumeExecutor.execute(() -> {
            PersistenceConsumerInfo persistenceConsumerInfo = getPersistenceConsumerInfo(clientId);

            List<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> persistedMsgList;
            long currentOffset = persistenceConsumerInfo.persistedOffset.get();
            do {
                persistedMsgList = persistenceConsumerInfo.consumer.poll(pollDuration);
                for (TbProtoQueueMsg<QueueProtos.PublishMsgProto> publishMsgProtoTbProtoQueueMsg : persistedMsgList) {
                    QueueProtos.PublishMsgProto publishMsgProto = publishMsgProtoTbProtoQueueMsg.getValue();
                    MqttPublishMessage mqttPubMsg = mqttMessageGenerator.createPubMsg(publishMsgProto.getPacketId(), publishMsgProto.getTopicName(),
                            MqttQoS.valueOf(publishMsgProto.getQos()), publishMsgProto.getPayload().toByteArray());
                    clientSessionCtx.getPacketsInfoQueue().add(new PacketIdAndOffset(publishMsgProto.getPacketId(), currentOffset++));
                    try {
                        clientSessionCtx.getChannel().writeAndFlush(mqttPubMsg);
                    } catch (Exception e) {
                        log.warn("[{}][{}] Failed to send publish msg to MQTT client.", clientId, clientSessionCtx.getSessionId());
                        log.trace("Detailed error:", e);
                    }
                }
            } while (!persistedMsgList.isEmpty());
            persistenceConsumerInfo.consumer.unsubscribeAndClose();
            applicationPersistentConsumers.remove(clientId);
            clientSessionCtx.getIsProcessingPersistedMsgs().getAndSet(true);
        });
    }

    private PersistenceConsumerInfo getPersistenceConsumerInfo(String clientId) {
        return applicationPersistentConsumers.computeIfAbsent(clientId, id -> {
                    TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> consumer = applicationPersistenceMsgQueueFactory.createConsumer(clientId);
                    // TODO test if we don't skip any message
                    consumer.assignPartition(0);
                    long currentOffset = consumer.getOffset(consumer.getTopic(), 0);
                    return new PersistenceConsumerInfo(consumer, new AtomicLong(currentOffset));
                }
        );
    }

    @PreDestroy
    public void destroy() {
        for (PersistenceConsumerInfo persistenceConsumerInfo : applicationPersistentConsumers.values()) {
            persistenceConsumerInfo.consumer.unsubscribeAndClose();
        }
    }

    @AllArgsConstructor
    private static class PersistenceConsumerInfo {
        private final TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> consumer;
        private final AtomicLong persistedOffset;
    }
}
