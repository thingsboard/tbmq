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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.common.data.queue.TopicInfo;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.queue.TbQueueMetadataService;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.ApplicationPersistenceMsgQueueFactory;
import org.thingsboard.mqtt.broker.queue.provider.ApplicationPublishCtxQueueFactory;
import org.thingsboard.mqtt.broker.service.mqtt.ClientSession;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.mqtt.LastPublishCtx;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.service.mqtt.PacketIdAndOffset;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RequiredArgsConstructor
@Service
@Qualifier("ApplicationPersistenceSessionHandler")
public class ApplicationPersistenceSessionHandler implements PersistenceSessionHandler {
    private final MqttMessageGenerator mqttMessageGenerator;
    private final ApplicationPersistenceMsgQueueFactory applicationPersistenceMsgQueueFactory;
    private final ApplicationPublishCtxQueueFactory applicationPublishCtxQueueFactory;

    private final Map<String, TbQueueProducer<TbProtoQueueMsg<QueueProtos.PublishMsgProto>>> applicationProducers = new ConcurrentHashMap<>();
    private final Map<String, LastPublishCtx> lastPublishCtxMap = new ConcurrentHashMap<>();

    private TbQueueProducer<TbProtoQueueMsg<QueueProtos.LastPublishCtxProto>> publishCtxProducer;
    private TbQueueMetadataService metadataService;

    @Value("${queue.application-publish-ctx.poll-interval}")
    private long pollDuration;

    @PostConstruct
    public void init() {
        this.metadataService = applicationPersistenceMsgQueueFactory.createMetadataService("application-metadata");
        this.publishCtxProducer = applicationPublishCtxQueueFactory.createProducer();

        TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.LastPublishCtxProto>> consumer = applicationPublishCtxQueueFactory.createConsumer();
        consumer.subscribe();
        consumer.seekToTheBeginning();
        List<TbProtoQueueMsg<QueueProtos.LastPublishCtxProto>> messages;
        do {
            try {
                messages = consumer.poll(pollDuration);
                for (TbProtoQueueMsg<QueueProtos.LastPublishCtxProto> msg : messages) {
                    String clientId = msg.getKey();
                    LastPublishCtx lastPublishCtx = ProtoConverter.convertToLastPublishCtx(msg.getValue());
                    long endOffset = metadataService.getEndOffset(applicationPersistenceMsgQueueFactory.getTopic(clientId), 0);
                    long lastPublishedOffset = lastPublishCtx.getOffset().get();
                    if (lastPublishedOffset != endOffset) {
                        log.warn("[{}] Last publish offset differs from endOffset. Last publish end - {}, end offset - {}.",
                                clientId, lastPublishedOffset, endOffset);
                        lastPublishCtx.getOffset().getAndSet(Math.max(endOffset, lastPublishedOffset));
                    }
                    lastPublishCtxMap.put(clientId, lastPublishCtx);
                }
                consumer.commit();
            } catch (Exception e) {
                log.error("Failed to load persisted publish contexts.", e);
                throw e;
            }
        } while (!messages.isEmpty());
        consumer.unsubscribeAndClose();
    }

    @Override
    public void processMsgPersistenceSessions(QueueProtos.PublishMsgProto publishMsgProto, Collection<Subscription> msgSubscriptions) {
        for (Subscription subscription : msgSubscriptions) {
            ClientSession clientSession = subscription.getClientSession();
            String clientId = clientSession.getClientInfo().getClientId();
            TbQueueProducer<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> applicationProducer = applicationProducers
                    // TODO it's possible that msg was pushed to kafka but was not acknowledged, in this case we'll have wrong offset (less than real)
                    .computeIfAbsent(clientId, id -> applicationPersistenceMsgQueueFactory.createProducer(clientId));

            int minQoSValue = Math.min(subscription.getMqttQoS().value(), publishMsgProto.getQos());

            LastPublishCtx lastPublishCtx = lastPublishCtxMap.computeIfAbsent(clientId, ignored -> new LastPublishCtx(0, 0));
            PacketIdAndOffset nextPacketIdAndOffset = lastPublishCtx.getNextPacketIdAndOffset();
            QueueProtos.PublishMsgProto persistedPublishMsgProto = QueueProtos.PublishMsgProto.newBuilder(publishMsgProto)
                    .setPacketId(nextPacketIdAndOffset.getPacketId())
                    .setQos(minQoSValue)
                    .build();

            TopicInfo applicationMsgTopicInfo = new TopicInfo(applicationProducer.getDefaultTopic());
            applicationProducer.send(applicationMsgTopicInfo, new TbProtoQueueMsg<>(persistedPublishMsgProto.getTopicName(), persistedPublishMsgProto),
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

            TopicInfo publishCtxTopicInfo = new TopicInfo(publishCtxProducer.getDefaultTopic());
            publishCtxProducer.send(publishCtxTopicInfo, new TbProtoQueueMsg<>(clientId, ProtoConverter.convertToLastPublishCtxProto(nextPacketIdAndOffset)),
                    new TbQueueCallback() {
                        @Override
                        public void onSuccess(TbQueueMsgMetadata metadata) {
                            log.trace("[{}] Successfully sent last publish context to the queue.", clientId);
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            // TODO what if we failed to publish latest context?
                            log.error("[{}] Failed to send last publish context to the queue for MQTT topic {}. Reason - {}.",
                                    clientId, publishMsgProto.getTopicName(), t.getMessage());
                            log.debug("Detailed error: ", t);
                        }
                    });

            ClientSessionCtx sessionCtx = subscription.getSessionCtx();
            if (sessionCtx != null && sessionCtx.isConnected() && !sessionCtx.getIsProcessingPersistedMsgs().get()) {
                sessionCtx.getPacketsInfoQueue().add(nextPacketIdAndOffset);
                try {
                    MqttPublishMessage mqttPubMsg = mqttMessageGenerator.createPubMsg(nextPacketIdAndOffset.getPacketId(),
                            persistedPublishMsgProto.getTopicName(), MqttQoS.valueOf(persistedPublishMsgProto.getQos()),
                            persistedPublishMsgProto.getPayload().toByteArray());
                    sessionCtx.getChannel().writeAndFlush(mqttPubMsg);
                } catch (Exception e) {
                    log.warn("[{}][{}] Failed to send publish msg to MQTT client.", clientId, sessionCtx.getSessionId());
                    log.trace("Detailed error:", e);
                }
            }
        }
    }

    @Override
    public void clearPersistedMsgs(String clientId) {
        LastPublishCtx removedCtx = lastPublishCtxMap.remove(clientId);
        if (removedCtx == null) {
            log.trace("[{}] No persisted publish context found.", clientId);
            return;
        }
        TopicInfo topicInfo = new TopicInfo(publishCtxProducer.getDefaultTopic());
        publishCtxProducer.send(topicInfo, new TbProtoQueueMsg<>(clientId, QueueProtos.LastPublishCtxProto.newBuilder().build()),
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

        metadataService.deleteTopic(applicationPersistenceMsgQueueFactory.getTopic(clientId));
    }

    @PreDestroy
    public void destroy() {
        if (metadataService != null) {
            metadataService.close();
        }
        if (publishCtxProducer != null) {
            publishCtxProducer.stop();
        }
        for (TbQueueProducer<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> producer : applicationProducers.values()) {
            producer.stop();
        }
    }
}
