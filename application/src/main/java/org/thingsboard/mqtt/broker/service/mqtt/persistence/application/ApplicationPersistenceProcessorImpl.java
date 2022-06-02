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

import com.google.common.collect.Sets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.actors.client.messages.DisconnectMsg;
import org.thingsboard.mqtt.broker.actors.client.state.ClientActorStateInfo;
import org.thingsboard.mqtt.broker.actors.client.state.SessionState;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos.PublishMsgProto;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.ApplicationPersistenceMsgQueueFactory;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsgDeliveryService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing.ApplicationAckStrategy;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing.ApplicationMsgAcknowledgeStrategyFactory;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing.ApplicationPackProcessingContext;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing.ApplicationPackProcessingResult;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing.ApplicationPersistedMsgCtx;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing.ApplicationPersistedMsgCtxService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing.ApplicationProcessingDecision;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing.ApplicationSubmitStrategy;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing.ApplicationSubmitStrategyFactory;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing.PersistedMsg;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing.PersistedPubRelMsg;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing.PersistedPublishMsg;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.topic.ApplicationTopicService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.util.MqttApplicationClientUtil;
import org.thingsboard.mqtt.broker.service.stats.ApplicationProcessorStats;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.thingsboard.mqtt.broker.service.mqtt.persistence.application.util.MqttApplicationClientUtil.getConsumerGroup;
import static org.thingsboard.mqtt.broker.service.mqtt.persistence.application.util.MqttApplicationClientUtil.getTopic;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApplicationPersistenceProcessorImpl implements ApplicationPersistenceProcessor {

    private final ConcurrentMap<String, ApplicationPackProcessingContext> processingContextMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Future<?>> processingFutures = new ConcurrentHashMap<>();

    private final ApplicationMsgAcknowledgeStrategyFactory acknowledgeStrategyFactory;
    private final ApplicationSubmitStrategyFactory submitStrategyFactory;
    private final ApplicationPersistenceMsgQueueFactory applicationPersistenceMsgQueueFactory;
    private final PublishMsgDeliveryService publishMsgDeliveryService;
    private final TbQueueAdmin queueAdmin;
    private final StatsManager statsManager;
    private final ApplicationPersistedMsgCtxService unacknowledgedPersistedMsgCtxService;
    private final ClientMqttActorManager clientMqttActorManager;
    private final ServiceInfoProvider serviceInfoProvider;
    private final ClientLogger clientLogger;
    private final ApplicationTopicService applicationTopicService;

    private final ExecutorService persistedMsgsConsumeExecutor = Executors.newCachedThreadPool(ThingsBoardThreadFactory.forName("application-persisted-msg-consumers"));

    @Value("${queue.application-persisted-msg.poll-interval}")
    private long pollDuration;
    @Value("${queue.application-persisted-msg.pack-processing-timeout}")
    private long packProcessingTimeout;

    @PostConstruct
    public void init() {
        statsManager.registerActiveApplicationProcessorsStats(processingFutures);
    }

    @Override
    public void processPubAck(String clientId, int packetId) {
        log.trace("[{}] Acknowledged packet {}", clientId, packetId);
        ApplicationPackProcessingContext processingContext = processingContextMap.get(clientId);
        if (processingContext == null) {
            log.warn("[{}] Cannot find processing context for client. PacketId - {}.", clientId, packetId);
        } else {
            processingContext.onPubAck(packetId);
        }
    }

    @Override
    public void processPubRec(ClientSessionCtx clientSessionCtx, int packetId) {
        String clientId = clientSessionCtx.getClientId();
        log.trace("[{}] Received packet {}", clientId, packetId);
        ApplicationPackProcessingContext processingContext = processingContextMap.get(clientId);
        if (processingContext == null) {
            log.warn("[{}] Cannot find processing context for client. PacketId - {}.", clientId, packetId);
            publishMsgDeliveryService.sendPubRelMsgToClient(clientSessionCtx, packetId);
        } else {
            processingContext.onPubRec(packetId);
        }
    }

    @Override
    public void processPubComp(String clientId, int packetId) {
        log.trace("[{}] Completed packet {}", clientId, packetId);
        ApplicationPackProcessingContext processingContext = processingContextMap.get(clientId);
        if (processingContext == null) {
            log.warn("[{}] Cannot find processing context for client. PacketId - {}.", clientId, packetId);
        } else {
            processingContext.onPubComp(packetId);
        }
    }

    @Override
    public void startProcessingPersistedMessages(ClientActorStateInfo clientState) {
        String clientId = clientState.getClientId();
        applicationTopicService.createTopic(clientId);
        clientLogger.logEvent(clientId, this.getClass(), "Starting processing persisted messages");
        log.debug("[{}] Starting persisted messages processing.", clientId);
        ClientSessionCtx clientSessionCtx = clientState.getCurrentSessionCtx();
        TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishMsgProto>> consumer;
        consumer = initConsumer(clientId);
        Future<?> future = persistedMsgsConsumeExecutor.submit(() -> {
            try {
                processPersistedMessages(consumer, clientSessionCtx, clientState);
            } catch (Exception e) {
                log.warn("[{}] Failed to start processing persisted messages.", clientId, e);
                clientMqttActorManager.disconnect(clientId, new DisconnectMsg(
                        clientSessionCtx.getSessionId(),
                        new DisconnectReason(
                                DisconnectReasonType.ON_ERROR,
                                "Failed to start processing persisted messages")));
            } finally {
                consumer.unsubscribeAndClose();
            }
        });
        processingFutures.put(clientId, future);
    }

    @Override
    public void stopProcessingPersistedMessages(String clientId) {
        log.debug("[{}] Stopping persisted messages processing.", clientId);
        clientLogger.logEvent(clientId, this.getClass(), "Stopping processing persisted messages");
        Future<?> processingFuture = processingFutures.remove(clientId);
        if (processingFuture == null) {
            log.warn("[{}] Cannot find processing future for client.", clientId);
        } else {
            try {
                processingFuture.cancel(true);
                statsManager.clearApplicationProcessorStats(clientId);
            } catch (Exception e) {
                log.warn("[{}] Exception stopping future for client. Exception - {}, reason - {}.", clientId, e.getClass().getSimpleName(), e.getMessage());
            }
        }
        ApplicationPackProcessingContext processingContext = processingContextMap.remove(clientId);
        unacknowledgedPersistedMsgCtxService.saveContext(clientId, processingContext);
    }

    @Override
    public void clearPersistedMsgs(String clientId) {
        clientLogger.logEvent(clientId, this.getClass(), "Clearing persisted messages");
        String applicationConsumerGroup = MqttApplicationClientUtil.getConsumerGroup(clientId);
        log.debug("[{}] Clearing consumer group {} for application.", clientId, applicationConsumerGroup);
        // TODO: make async
        queueAdmin.deleteConsumerGroups(Collections.singleton(applicationConsumerGroup));
        log.debug("[{}] Clearing application session context.", clientId);
        unacknowledgedPersistedMsgCtxService.clearContext(clientId);
    }

    // TODO: make async
    private TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishMsgProto>> initConsumer(String clientId) {
        TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishMsgProto>> consumer = applicationPersistenceMsgQueueFactory
                .createConsumer(getTopic(clientId), getConsumerGroup(clientId), serviceInfoProvider.getServiceId() + "-" + clientId);
        try {
            consumer.assignPartition(0);

            Optional<Long> committedOffset = consumer.getCommittedOffset(consumer.getTopic(), 0);
            if (committedOffset.isEmpty()) {
                long endOffset = consumer.getEndOffset(consumer.getTopic(), 0);
                consumer.commit(0, endOffset);
            }
            return consumer;
        } catch (Exception e) {
            consumer.unsubscribeAndClose();
            throw e;
        }
    }

    private void processPersistedMessages(TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishMsgProto>> consumer,
                                          ClientSessionCtx clientSessionCtx, ClientActorStateInfo clientState) {
        String clientId = clientSessionCtx.getClientId();
        UUID sessionId = clientSessionCtx.getSessionId();

        ApplicationProcessorStats stats = statsManager.createApplicationProcessorStats(clientId);

        ApplicationPersistedMsgCtx persistedMsgCtx = unacknowledgedPersistedMsgCtxService.loadPersistedMsgCtx(clientId);

        // TODO: make consistent with logic for DEVICES
        clientSessionCtx.getMsgIdSeq().updateMsgIdSequence(persistedMsgCtx.getLastPacketId());


        Collection<PersistedPubRelMsg> pendingPubRelMessages = persistedMsgCtx.getPubRelMsgIds().entrySet().stream()
                .map(entry -> new PersistedPubRelMsg(entry.getValue(), entry.getKey()))
                .collect(Collectors.toList());

        while (isClientConnected(sessionId, clientState)) {
            try {
                List<TbProtoQueueMsg<PublishMsgProto>> publishProtoMessages = consumer.poll(pollDuration);
                if (publishProtoMessages.isEmpty() && pendingPubRelMessages.isEmpty()) {
                    continue;
                }
                long packProcessingStart = System.nanoTime();
                ApplicationAckStrategy ackStrategy = acknowledgeStrategyFactory.newInstance(clientId);
                ApplicationSubmitStrategy submitStrategy = submitStrategyFactory.newInstance(clientId);

                List<PersistedPubRelMsg> pubRelMessagesToDeliver = pendingPubRelMessages.stream()
                        .sorted(Comparator.comparingLong(PersistedMsg::getPacketOffset))
                        .collect(Collectors.toList());
                List<PersistedPublishMsg> publishMessagesToDeliver = extractPublishMessagesToDeliver(clientSessionCtx, persistedMsgCtx, publishProtoMessages);
                List<PersistedMsg> messagesToDeliver = new ArrayList<>();
                messagesToDeliver.addAll(pubRelMessagesToDeliver);
                messagesToDeliver.addAll(publishMessagesToDeliver);
                submitStrategy.init(messagesToDeliver);

                // TODO: refactor this
                pendingPubRelMessages = Sets.newConcurrentHashSet();
                while (isClientConnected(sessionId, clientState)) {
                    ApplicationPackProcessingContext ctx = new ApplicationPackProcessingContext(submitStrategy, pendingPubRelMessages, stats);
                    int totalPublishMsgs = ctx.getPublishPendingMsgMap().size();
                    int totalPubRelMsgs = ctx.getPubRelPendingMsgMap().size();
                    processingContextMap.put(clientId, ctx);
                    submitStrategy.process(msg -> {
                        switch (msg.getPacketType()) {
                            case PUBLISH:
                                PublishMsg publishMsg = ((PersistedPublishMsg) msg).getPublishMsg();
                                publishMsgDeliveryService.sendPublishMsgToClient(clientSessionCtx, publishMsg);
                                break;
                            case PUBREL:
                                publishMsgDeliveryService.sendPubRelMsgToClient(clientSessionCtx, msg.getPacketId());
                                break;
                        }
                        clientLogger.logEvent(clientId, this.getClass(), "Delivered msg to application client");
                    });

                    if (isClientConnected(sessionId, clientState)) {
                        ctx.await(packProcessingTimeout, TimeUnit.MILLISECONDS);
                    }

                    ApplicationPackProcessingResult result = new ApplicationPackProcessingResult(ctx);
                    ApplicationProcessingDecision decision = ackStrategy.analyze(result);

                    stats.log(totalPublishMsgs, totalPubRelMsgs, result, decision.isCommit());

                    if (decision.isCommit()) {
                        ctx.clear();
                        consumer.commitSync();
                        break;
                    } else {
                        submitStrategy.update(decision.getReprocessMap());
                    }
                }
                log.trace("[{}] Pack processing took {} ms, pack size - {}",
                        clientId, (double) (System.nanoTime() - packProcessingStart) / 1_000_000, messagesToDeliver.size());

            } catch (Exception e) {
                // TODO: think if we need to drop session in this case
                if (isClientConnected(sessionId, clientState)) {
                    log.warn("[{}] Failed to process messages from queue.", clientId, e);
                    try {
                        Thread.sleep(pollDuration);
                    } catch (InterruptedException e2) {
                        log.trace("Failed to wait until the server has capacity to handle new requests", e2);
                    }
                }
            }
        }
        log.debug("[{}] Application persisted messages consumer stopped.", clientId);

    }

    private List<PersistedPublishMsg> extractPublishMessagesToDeliver(ClientSessionCtx clientSessionCtx,
                                                                      ApplicationPersistedMsgCtx persistedMsgCtx,
                                                                      List<TbProtoQueueMsg<PublishMsgProto>> publishProtoMessages) {
        return publishProtoMessages.stream()
                .map(msg -> {
                    Integer msgPacketId = persistedMsgCtx.getMsgPacketId(msg.getOffset());
                    int packetId = msgPacketId != null ? msgPacketId : clientSessionCtx.getMsgIdSeq().nextMsgId();
                    boolean isDup = msgPacketId != null;
                    PublishMsgProto persistedMsgProto = msg.getValue();
                    PublishMsg publishMsg = ProtoConverter.convertToPublishMsg(persistedMsgProto).toBuilder()
                            .packetId(packetId)
                            .isDup(isDup)
                            .build();
                    return new PersistedPublishMsg(publishMsg, msg.getOffset());
                })
                .sorted(Comparator.comparingLong(PersistedPublishMsg::getPacketOffset))
                .collect(Collectors.toList());
    }

    private boolean isClientConnected(UUID sessionId, ClientActorStateInfo clientState) {
        return !Thread.interrupted()
                && clientState.getCurrentSessionId().equals(sessionId)
                && clientState.getCurrentSessionState() == SessionState.CONNECTED;
    }

    @PreDestroy
    public void destroy() {
        processingFutures.forEach((clientId, future) -> {
            future.cancel(true);
            log.info("[{}] Saving processing context before shutting down.", clientId);
            ApplicationPackProcessingContext processingContext = processingContextMap.remove(clientId);
            try {
                unacknowledgedPersistedMsgCtxService.saveContext(clientId, processingContext);
            } catch (Exception e) {
                log.warn("[{}] Failed to save APPLICATION context.", clientId);
            }
        });
        persistedMsgsConsumeExecutor.shutdownNow();
    }
}
