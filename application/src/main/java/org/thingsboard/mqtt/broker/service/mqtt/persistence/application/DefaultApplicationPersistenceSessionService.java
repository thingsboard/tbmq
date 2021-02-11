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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.common.data.queue.TopicInfo;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.ApplicationPersistenceMsgQueueFactory;
import org.thingsboard.mqtt.broker.queue.provider.ApplicationPublishCtxQueueFactory;
import org.thingsboard.mqtt.broker.service.mqtt.LastPublishCtx;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultApplicationPersistenceSessionService implements ApplicationPersistenceSessionService {
    private final QueueProtos.LastPublishCtxProto EMPTY_LAST_PUBLISH_CTX_PROTO = QueueProtos.LastPublishCtxProto.newBuilder().build();

    private final ApplicationPersistenceMsgQueueFactory applicationPersistenceMsgQueueFactory;
    private final ApplicationPublishCtxQueueFactory applicationPublishCtxQueueFactory;

    private final Map<String, TbQueueProducer<TbProtoQueueMsg<QueueProtos.PublishMsgProto>>> applicationProducers = new ConcurrentHashMap<>();
    private final Map<String, LastPublishCtx> lastPublishCtxMap = new ConcurrentHashMap<>();

    private TbQueueProducer<TbProtoQueueMsg<QueueProtos.LastPublishCtxProto>> publishCtxProducer;

    @Value("${queue.application-publish-ctx.poll-interval}")
    private long pollDuration;

    @PostConstruct
    public void init() {
        this.publishCtxProducer = applicationPublishCtxQueueFactory.createProducer();

        loadPersistedLastPublishCtx();
    }

    @Override
    public void processMsgPersistence(String clientId, int subscriptionQoSValue, QueueProtos.PublishMsgProto receivedPublishMsgProto) {
        // We have to set 'packetId' here and not on the side of the consumer because if the message was sent and not acknowledged, it needs to be sent again with the same 'packetId'
        LastPublishCtx lastPublishCtx = lastPublishCtxMap.computeIfAbsent(clientId, ignored -> new LastPublishCtx(0));
        int nextPacketId = lastPublishCtx.getNextPacketId();
        int minQoSValue = Math.min(subscriptionQoSValue, receivedPublishMsgProto.getQos());
        QueueProtos.PublishMsgProto persistedPublishMsgProto = QueueProtos.PublishMsgProto.newBuilder(receivedPublishMsgProto)
                .setPacketId(nextPacketId)
                .setQos(minQoSValue)
                .build();

        savePublishMsgProto(clientId, persistedPublishMsgProto);
        saveLastPublishCtx(clientId, nextPacketId);
    }

    @Override
    public void clearPersistedCtx(String clientId) {
        clearContext(clientId);
        clearProducer(clientId);
    }

    private void clearProducer(String clientId) {
        TbQueueProducer<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> removedProducer = applicationProducers.remove(clientId);
        if (removedProducer != null) {
            removedProducer.stop();
        } else {
            log.trace("[{}] No producer found.", clientId);
        }
    }

    private void clearContext(String clientId) {
        LastPublishCtx removedCtx = lastPublishCtxMap.remove(clientId);
        if (removedCtx != null) {
            clearLastPublishCtxInQueue(clientId);
        } else {
            log.trace("[{}] No persisted publish context found.", clientId);
        }
    }

    private void savePublishMsgProto(String clientId, QueueProtos.PublishMsgProto publishMsgProto) {
        TbQueueProducer<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> applicationProducer = applicationProducers.computeIfAbsent(clientId,
                id -> applicationPersistenceMsgQueueFactory.createProducer(clientId));
        applicationProducer.send(new TbProtoQueueMsg<>(publishMsgProto.getTopicName(), publishMsgProto),
                new TbQueueCallback() {
                    @Override
                    public void onSuccess(TbQueueMsgMetadata metadata) {
                        log.trace("[{}] Successfully sent publish msg to the queue.", clientId);
                    }
                    @Override
                    public void onFailure(Throwable t) {
                        // TODO do we need to decrement offset counter?
                        log.error("[{}] Failed to send publish msg to the queue for MQTT topic {}. Reason - {}.",
                                clientId, publishMsgProto.getTopicName(), t.getMessage());
                        log.debug("Detailed error: ", t);
                    }
                });
    }

    private void saveLastPublishCtx(String clientId, int packetId) {
        publishCtxProducer.send(new TbProtoQueueMsg<>(clientId, ProtoConverter.createLastPublishCtxProto(packetId)),
                new TbQueueCallback() {
                    @Override
                    public void onSuccess(TbQueueMsgMetadata metadata) {
                        log.trace("[{}] Successfully sent last publish context to the queue.", clientId);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        // TODO what if we failed to publish latest context?
                        log.error("[{}] Failed to send last publish context to the queue. Reason - {}.",
                                clientId, t.getMessage());
                        log.debug("Detailed error: ", t);
                    }
                });
    }

    private void clearLastPublishCtxInQueue(String clientId) {
        publishCtxProducer.send(new TbProtoQueueMsg<>(clientId, EMPTY_LAST_PUBLISH_CTX_PROTO),
                new TbQueueCallback() {
                    @Override
                    public void onSuccess(TbQueueMsgMetadata metadata) {
                        log.trace("[{}] Successfully cleared persisted publish context.", clientId);
                    }
                    @Override
                    public void onFailure(Throwable t) {
                        log.warn("[{}] Failed to clear persisted publish context.", clientId);
                        log.trace("Detailed error:", t);
                    }
                });
    }

    private void loadPersistedLastPublishCtx() {
        TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.LastPublishCtxProto>> consumer = applicationPublishCtxQueueFactory.createConsumer();
        consumer.assignAllPartitions();
        consumer.seekToTheBeginning();
        List<TbProtoQueueMsg<QueueProtos.LastPublishCtxProto>> messages;
        do {
            try {
                messages = consumer.poll(pollDuration);
                for (TbProtoQueueMsg<QueueProtos.LastPublishCtxProto> msg : messages) {
                    String clientId = msg.getKey();
                    if (isLastPublishCtxProtoEmpty(msg.getValue())) {
                        // this means Kafka log compaction service haven't cleared empty message yet
                        log.debug("[{}] Encountered empty LastPublishCtx.", clientId);
                        lastPublishCtxMap.remove(clientId);
                    } else {
                        LastPublishCtx lastPublishCtx = ProtoConverter.convertToLastPublishCtx(msg.getValue());
                        lastPublishCtxMap.put(clientId, lastPublishCtx);
                    }
                }
                consumer.commit();
            } catch (Exception e) {
                log.error("Failed to load persisted publish contexts.", e);
                throw e;
            }
        } while (!messages.isEmpty());
        consumer.unsubscribeAndClose();
    }

    private boolean isLastPublishCtxProtoEmpty(QueueProtos.LastPublishCtxProto lastPublishCtxProto) {
        return lastPublishCtxProto.getPacketId() == 0;
    }

    @PreDestroy
    public void destroy() {
        if (publishCtxProducer != null) {
            publishCtxProducer.stop();
        }
        for (TbQueueProducer<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> producer : applicationProducers.values()) {
            producer.stop();
        }
    }
}
