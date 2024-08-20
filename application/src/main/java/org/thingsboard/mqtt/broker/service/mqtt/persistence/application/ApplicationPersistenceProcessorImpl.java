/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttDisconnectMsg;
import org.thingsboard.mqtt.broker.actors.client.state.ClientActorStateInfo;
import org.thingsboard.mqtt.broker.actors.client.state.SessionState;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.common.data.mqtt.MsgExpiryResult;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos.PublishMsgProto;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.queue.TbQueueConsumer;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.ApplicationPersistenceMsgQueueFactory;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsgDeliveryService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.data.ApplicationSharedSubscriptionCtx;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.data.ApplicationSharedSubscriptionJob;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing.ApplicationAckStrategy;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing.ApplicationMsgAcknowledgeStrategyFactory;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing.ApplicationPackProcessingCtx;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing.ApplicationPackProcessingResult;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing.ApplicationPersistedMsgCtx;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing.ApplicationPersistedMsgCtxService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing.ApplicationProcessingDecision;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing.ApplicationPubRelMsgCtx;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing.ApplicationSubmitStrategy;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing.ApplicationSubmitStrategyFactory;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing.PersistedMsg;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing.PersistedPubRelMsg;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing.PersistedPublishMsg;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.topic.ApplicationTopicService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.util.ApplicationClientHelperService;
import org.thingsboard.mqtt.broker.service.stats.ApplicationProcessorStats;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;
import org.thingsboard.mqtt.broker.util.MqttPropertiesUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApplicationPersistenceProcessorImpl implements ApplicationPersistenceProcessor {

    private final ConcurrentMap<String, ApplicationPackProcessingCtx> packProcessingCtxMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Future<?>> processingFutures = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, Set<ApplicationSharedSubscriptionCtx>> sharedSubscriptionsPackProcessingCtxMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, List<ApplicationSharedSubscriptionJob>> sharedSubscriptionsProcessingJobs = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ApplicationPersistedMsgCtx> persistedMsgCtxMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConcurrentMap<TopicSharedSubscription, TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishMsgProto>>>>
            sharedSubscriptionConsumers = new ConcurrentHashMap<>();

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
    private final ApplicationClientHelperService appClientHelperService;
    private final boolean isTraceEnabled = log.isTraceEnabled();
    private final boolean isDebugEnabled = log.isDebugEnabled();

    @Value("${queue.application-persisted-msg.poll-interval}")
    private long pollDuration;
    @Value("${queue.application-persisted-msg.pack-processing-timeout}")
    private long packProcessingTimeout;
    @Value("${queue.application-persisted-msg.shared-topic-validation:true}")
    private boolean validateSharedTopicFilter;

    private volatile boolean stopped = false;
    private ExecutorService persistedMsgsConsumerExecutor;
    private ExecutorService sharedSubsMsgsConsumerExecutor;

    @PostConstruct
    public void init() {
        statsManager.registerActiveApplicationProcessorsStats(processingFutures);
        statsManager.registerActiveSharedApplicationProcessorsStats(sharedSubscriptionsProcessingJobs);
        persistedMsgsConsumerExecutor = ThingsBoardExecutors.initCachedExecutorService("application-persisted-msg-consumers");
        sharedSubsMsgsConsumerExecutor = ThingsBoardExecutors.initCachedExecutorService("application-shared-subs-msg-consumers");
    }

    @Override
    public void processPubAck(String clientId, int packetId) {
        ApplicationPackProcessingCtx processingContext = packProcessingCtxMap.get(clientId);
        if (processingContext == null) {
            if (isDebugEnabled) {
                log.debug("[{}] Cannot find main processing context for client on PubAck. PacketId - {}.", clientId, packetId);
            }
            processPubAckInSharedCtx(clientId, packetId, "[{}] Cannot find processing contexts for client on PubAck. PacketId - {}.");
        } else {
            var ack = processingContext.onPubAck(packetId);
            if (ack) {
                if (isDebugEnabled) {
                    log.debug("[{}] PubAck packet [{}] processed successfully from main context", clientId, packetId);
                }
                return;
            }
            processPubAckInSharedCtx(clientId, packetId, "[{}] Cannot find shared subscriptions processing contexts for client on PubAck. PacketId - {}.");
        }
    }

    private void processPubAckInSharedCtx(String clientId, int packetId, String format) {
        Set<ApplicationSharedSubscriptionCtx> contexts = sharedSubscriptionsPackProcessingCtxMap.get(clientId);
        if (CollectionUtils.isEmpty(contexts)) {
            log.warn(format, clientId, packetId);
            return;
        }
        for (ApplicationSharedSubscriptionCtx ctx : contexts) {
            var acknowledged = ctx.getPackProcessingCtx().onPubAck(packetId);
            if (acknowledged) {
                if (isDebugEnabled) {
                    log.debug("[{}] PubAck packet [{}] processed successfully from shared contexts", clientId, packetId);
                }
                return;
            }
        }
    }

    @Override
    public void processPubRec(ClientSessionCtx clientSessionCtx, int packetId) {
        String clientId = clientSessionCtx.getClientId();
        ApplicationPackProcessingCtx processingContext = packProcessingCtxMap.get(clientId);
        if (processingContext == null) {
            if (isDebugEnabled) {
                log.debug("[{}] Cannot find main processing context for client on PubRec. PacketId - {}.", clientId, packetId);
            }
            processPubRecInSharedCtx(clientSessionCtx, packetId,
                    "[{}] Cannot find processing contexts for client on PubRec. PacketId - {}.", true);
        } else {
            var ack = processingContext.onPubRec(packetId, true);
            if (ack) {
                if (isDebugEnabled) {
                    log.debug("[{}] PubRec packet [{}] processed successfully from main context", clientId, packetId);
                }
                return;
            }
            processPubRecInSharedCtx(clientSessionCtx, packetId,
                    "[{}] Cannot find shared subscriptions processing contexts for client on PubRec. PacketId - {}.", true);
        }
    }

    @Override
    public void processPubRecNoPubRelDelivery(ClientSessionCtx clientSessionCtx, int packetId) {
        String clientId = clientSessionCtx.getClientId();
        ApplicationPackProcessingCtx processingContext = packProcessingCtxMap.get(clientId);
        if (processingContext == null) {
            if (isDebugEnabled) {
                log.debug("[{}] Cannot find main processing context for client on PubRec no PubRel delivery. PacketId - {}.", clientId, packetId);
            }
            processPubRecInSharedCtx(clientSessionCtx, packetId,
                    "[{}] Cannot find processing contexts for client on PubRec no PubRel delivery. PacketId - {}.", false);
        } else {
            var ack = processingContext.onPubRec(packetId, false);
            if (ack) {
                if (isDebugEnabled) {
                    log.debug("[{}] PubRec no PubRel delivery packet [{}] processed successfully from main context", clientId, packetId);
                }
                return;
            }
            processPubRecInSharedCtx(clientSessionCtx, packetId,
                    "[{}] Cannot find shared subscriptions processing contexts for client on PubRec no PubRel delivery. PacketId - {}.", false);
        }
    }

    private void processPubRecInSharedCtx(ClientSessionCtx clientSessionCtx, int packetId, String format, boolean sendPubRelMsg) {
        Set<ApplicationSharedSubscriptionCtx> contexts = sharedSubscriptionsPackProcessingCtxMap.get(clientSessionCtx.getClientId());
        if (CollectionUtils.isEmpty(contexts)) {
            log.warn(format, clientSessionCtx.getClientId(), packetId);
            if (sendPubRelMsg) {
                publishMsgDeliveryService.sendPubRelMsgToClient(clientSessionCtx, packetId);
            }
            return;
        }
        for (ApplicationSharedSubscriptionCtx ctx : contexts) {
            var acknowledged = ctx.getPackProcessingCtx().onPubRec(packetId, sendPubRelMsg);
            if (acknowledged) {
                if (isDebugEnabled) {
                    log.debug("[{}] PubRec packet [{}] processed successfully from shared contexts", clientSessionCtx.getClientId(), packetId);
                }
                return;
            }
        }
        if (sendPubRelMsg) {
            publishMsgDeliveryService.sendPubRelMsgToClient(clientSessionCtx, packetId);
        }
    }

    @Override
    public void processPubComp(String clientId, int packetId) {
        ApplicationPackProcessingCtx processingContext = packProcessingCtxMap.get(clientId);
        if (processingContext == null) {
            if (isDebugEnabled) {
                log.debug("[{}] Cannot find main processing context for client on PubComp. PacketId - {}.", clientId, packetId);
            }
            processPubCompInSharedCtx(clientId, packetId, "[{}] Cannot find processing contexts for client on PubComp. PacketId - {}.");
        } else {
            var ack = processingContext.onPubComp(packetId);
            if (ack) {
                if (isDebugEnabled) {
                    log.debug("[{}] PubComp packet [{}] processed successfully from main context", clientId, packetId);
                }
                return;
            }
            processPubCompInSharedCtx(clientId, packetId, "[{}] Cannot find shared subscriptions processing contexts for client on PubComp. PacketId - {}.");
        }
    }

    private void processPubCompInSharedCtx(String clientId, int packetId, String format) {
        Set<ApplicationSharedSubscriptionCtx> contexts = sharedSubscriptionsPackProcessingCtxMap.get(clientId);
        if (CollectionUtils.isEmpty(contexts)) {
            log.warn(format, clientId, packetId);
            return;
        }
        for (ApplicationSharedSubscriptionCtx ctx : contexts) {
            var acknowledged = ctx.getPackProcessingCtx().onPubComp(packetId);
            if (acknowledged) {
                if (isDebugEnabled) {
                    log.debug("[{}] PubComp packet [{}] processed successfully from shared contexts", clientId, packetId);
                }
                return;
            }
        }
    }

    @Override
    public void startProcessingSharedSubscriptions(ClientSessionCtx clientSessionCtx, Set<TopicSharedSubscription> subscriptions) {
        if (CollectionUtils.isEmpty(subscriptions)) {
            return;
        }
        String clientId = clientSessionCtx.getClientId();
        clientLogger.logEvent(clientId, this.getClass(), "Starting processing shared subscriptions persisted messages");
        if (log.isDebugEnabled()) {
            log.debug("[{}][{}] Starting persisted shared subscriptions processing.", clientId, subscriptions);
        }

        ApplicationPersistedMsgCtx persistedMsgCtx = persistedMsgCtxMap.getOrDefault(clientId, new ApplicationPersistedMsgCtx());

        for (TopicSharedSubscription subscription : subscriptions) {
            TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishMsgProto>> consumer = initSharedConsumerIfNotPresent(clientId, subscription);
            if (consumer == null) {
                continue;
            }
            ApplicationSharedSubscriptionJob job = new ApplicationSharedSubscriptionJob(subscription, null, false);
            Future<?> future = sharedSubsMsgsConsumerExecutor.submit(() -> {
                try {
                    ApplicationProcessorStats stats = statsManager.createSharedApplicationProcessorStats(clientId, subscription);

                    ApplicationPubRelMsgCtx applicationPubRelMsgCtx = new ApplicationPubRelMsgCtx(Sets.newConcurrentHashSet());
                    while (isJobActive(job)) {
                        try {
                            List<TbProtoQueueMsg<PublishMsgProto>> publishProtoMessages = consumer.poll(pollDuration);
                            if (publishProtoMessages.isEmpty() && applicationPubRelMsgCtx.nothingToDeliver()) {
                                continue;
                            }

                            long packProcessingStart = System.nanoTime();
                            ApplicationSubmitStrategy submitStrategy = submitStrategyFactory.newInstance(clientId);

                            List<PersistedMsg> messagesToDeliver = getMessagesToDeliver(
                                    applicationPubRelMsgCtx,
                                    clientSessionCtx,
                                    persistedMsgCtx,
                                    publishProtoMessages,
                                    subscription);
                            submitStrategy.init(messagesToDeliver);

                            if (isDebugEnabled) {
                                log.debug("[{}] Start processing pack {}", clientId, messagesToDeliver);
                            }

                            applicationPubRelMsgCtx = new ApplicationPubRelMsgCtx(Sets.newConcurrentHashSet());
                            while (isJobActive(job)) {
                                ApplicationPackProcessingCtx ctx = newPackProcessingCtx(submitStrategy, applicationPubRelMsgCtx, stats);
                                int totalPublishMsgs = ctx.getPublishPendingMsgMap().size();
                                int totalPubRelMsgs = ctx.getPubRelPendingMsgMap().size();
                                cachePackProcessingCtx(clientId, subscription, ctx);

                                process(submitStrategy, clientSessionCtx, clientId);

                                if (isJobActive(job)) {
                                    ctx.await(packProcessingTimeout, TimeUnit.MILLISECONDS);
                                }

                                if (analyzeIfProcessingDone(clientId, consumer, stats, submitStrategy, ctx, totalPublishMsgs, totalPubRelMsgs))
                                    break;
                            }
                            if (isTraceEnabled) {
                                log.trace("[{}] Pack processing took {} ms, pack size - {}",
                                        clientId, (double) (System.nanoTime() - packProcessingStart) / 1_000_000, messagesToDeliver.size());
                            }
                        } catch (Exception e) {
                            if (isJobActive(job)) {
                                log.warn("[{}] Failed to process messages from shared queue.", clientId, e);
                                try {
                                    Thread.sleep(pollDuration);
                                } catch (InterruptedException e2) {
                                    if (isTraceEnabled) {
                                        log.trace("Failed to wait until the server has capacity to handle new requests", e2);
                                    }
                                }
                            }
                        }
                    }
                    if (log.isDebugEnabled()) {
                        log.debug("[{}] Shared subs Application persisted messages consumer stopped.", clientId);
                    }
                } catch (Exception e) {
                    log.warn("[{}][{}] Failed to start processing shared subs messages.", clientId, subscription, e);
                    disconnectClient(clientId, clientSessionCtx.getSessionId());
                }
            });
            job.setFuture(future);
            List<ApplicationSharedSubscriptionJob> jobs =
                    sharedSubscriptionsProcessingJobs.computeIfAbsent(clientId, s -> new CopyOnWriteArrayList<>());
            jobs.add(job);
        }
    }

    private List<PersistedMsg> getMessagesToDeliver(ApplicationPubRelMsgCtx applicationPubRelMsgCtx,
                                                    ClientSessionCtx clientSessionCtx,
                                                    ApplicationPersistedMsgCtx persistedMsgCtx,
                                                    List<TbProtoQueueMsg<PublishMsgProto>> publishProtoMessages,
                                                    TopicSharedSubscription subscription) {
        List<PersistedPubRelMsg> pubRelMessagesToDeliver = applicationPubRelMsgCtx.toSortedPubRelMessagesToDeliver();
        List<PersistedPublishMsg> publishMessagesToDeliver = toPublishMessagesToDeliver(
                clientSessionCtx,
                persistedMsgCtx,
                publishProtoMessages,
                subscription
        );

        return collectMessagesToDeliver(pubRelMessagesToDeliver, publishMessagesToDeliver);
    }

    private boolean analyzeIfProcessingDone(String clientId,
                                            TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishMsgProto>> consumer,
                                            ApplicationProcessorStats stats,
                                            ApplicationSubmitStrategy submitStrategy,
                                            ApplicationPackProcessingCtx ctx,
                                            int totalPublishMsgs,
                                            int totalPubRelMsgs) {
        ApplicationAckStrategy ackStrategy = acknowledgeStrategyFactory.newInstance(clientId);

        ApplicationPackProcessingResult result = new ApplicationPackProcessingResult(ctx);
        ApplicationProcessingDecision decision = ackStrategy.analyze(result);

        stats.log(totalPublishMsgs, totalPubRelMsgs, result, decision.isCommit());

        if (decision.isCommit()) {
            ctx.clear();
            consumer.commitSync();
            return true;
        } else {
            submitStrategy.update(decision.getReprocessMap());
        }
        return false;
    }

    private void cachePackProcessingCtx(String clientId,
                                        TopicSharedSubscription subscription,
                                        ApplicationPackProcessingCtx packProcessingCtx) {
        Set<ApplicationSharedSubscriptionCtx> contexts =
                sharedSubscriptionsPackProcessingCtxMap.computeIfAbsent(clientId, s -> Sets.newConcurrentHashSet());
        contexts.removeIf(ctx -> ctx.getSubscription().equals(subscription));
        contexts.add(new ApplicationSharedSubscriptionCtx(subscription, packProcessingCtx));
    }

    private ApplicationPackProcessingCtx newPackProcessingCtx(ApplicationSubmitStrategy submitStrategy,
                                                              ApplicationPubRelMsgCtx applicationPubRelMsgCtx,
                                                              ApplicationProcessorStats stats) {
        return new ApplicationPackProcessingCtx(submitStrategy, applicationPubRelMsgCtx, stats);
    }

    private TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishMsgProto>> initSharedConsumerIfNotPresent(String clientId,
                                                                                                             TopicSharedSubscription subscription) {
        ConcurrentMap<TopicSharedSubscription, TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishMsgProto>>> sharedSubsToConsumerMap =
                sharedSubscriptionConsumers.computeIfAbsent(clientId, s -> new ConcurrentHashMap<>());
        if (sharedSubsToConsumerMap.containsKey(subscription)) {
            log.info("[{}][{}] Consumer is already initialized", clientId, subscription);
            return null;
        }

        TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishMsgProto>> consumer = initSharedConsumer(clientId, subscription);
        sharedSubsToConsumerMap.put(subscription, consumer);

        return consumer;
    }

    private TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishMsgProto>> initSharedConsumer(String clientId, TopicSharedSubscription subscription) {
        String sharedAppTopic = appClientHelperService.getSharedAppTopic(subscription.getTopicFilter(), validateSharedTopicFilter);
        String sharedAppConsumerGroup = appClientHelperService.getSharedAppConsumerGroup(subscription, sharedAppTopic);
        String sharedConsumerId = getSharedConsumerId(clientId);
        TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishMsgProto>> consumer = applicationPersistenceMsgQueueFactory
                .createConsumerForSharedTopic(
                        sharedAppTopic,
                        sharedAppConsumerGroup,
                        sharedConsumerId);
        consumer.subscribe();
        return consumer;
    }

    private String getSharedConsumerId(String clientId) {
        return serviceInfoProvider.getServiceId() + "-" + clientId + "-" + RandomStringUtils.randomAlphabetic(5);
    }

    private void disconnectClient(String clientId, UUID sessionId) {
        disconnectClient(clientId, sessionId, "Failed to start processing shared topic messages");
    }

    @Override
    public void startProcessingPersistedMessages(ClientActorStateInfo clientState) {
        String clientId = clientState.getClientId();
        String clientTopic = applicationTopicService.createTopic(clientId);
        clientLogger.logEvent(clientId, this.getClass(), "Starting processing persisted messages");
        if (log.isDebugEnabled()) {
            log.debug("[{}] Starting persisted messages processing.", clientId);
        }
        TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishMsgProto>> consumer = initConsumer(clientId, clientTopic);
        Future<?> future = persistedMsgsConsumerExecutor.submit(() -> {
            try {
                processPersistedMessages(consumer, clientState);
            } catch (Exception e) {
                log.warn("[{}] Failed to start processing persisted messages.", clientId, e);
                disconnectClient(clientId, clientState);
            } finally {
                consumer.unsubscribeAndClose();
            }
        });
        processingFutures.put(clientId, future);
    }

    private void disconnectClient(String clientId, ClientActorStateInfo clientState) {
        disconnectClient(clientId, clientState.getCurrentSessionCtx().getSessionId(), "Failed to start processing persisted messages");
    }

    private void disconnectClient(String clientId, UUID sessionId, String message) {
        clientMqttActorManager.disconnect(clientId, new MqttDisconnectMsg(
                sessionId,
                new DisconnectReason(
                        DisconnectReasonType.ON_ERROR,
                        message)));
    }

    @Override
    public void stopProcessingPersistedMessages(String clientId) {
        if (log.isDebugEnabled()) {
            log.debug("[{}] Stopping persisted messages processing.", clientId);
        }
        clientLogger.logEvent(clientId, this.getClass(), "Stopping processing persisted messages");
        cancelMainProcessing(clientId);
        cancelSharedSubscriptionProcessing(clientId);
        stopSharedSubscriptionConsumers(clientId);

        ApplicationPackProcessingCtx processingContext = collectPackProcessingCtx(clientId);
        unacknowledgedPersistedMsgCtxService.saveContext(clientId, processingContext);
        persistedMsgCtxMap.remove(clientId);
    }

    private void stopSharedSubscriptionConsumers(String clientId) {
        ConcurrentMap<TopicSharedSubscription, TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishMsgProto>>> map =
                sharedSubscriptionConsumers.remove(clientId);
        if (!CollectionUtils.isEmpty(map)) {
            map.values().forEach(TbQueueConsumer::unsubscribeAndClose);
        }
    }

    private void cancelSharedSubscriptionProcessing(String clientId) {
        List<ApplicationSharedSubscriptionJob> jobs = sharedSubscriptionsProcessingJobs.remove(clientId);
        if (CollectionUtils.isEmpty(jobs)) {
            if (log.isDebugEnabled()) {
                log.debug("[{}] Cannot find shared processing futures for client.", clientId);
            }
        } else {
            try {
                jobs.forEach(this::cancelJob);
                statsManager.clearSharedApplicationProcessorStats(clientId);
            } catch (Exception e) {
                log.warn("[{}] Exception stopping future for client", clientId, e);
            }
        }
    }

    private void cancelMainProcessing(String clientId) {
        Future<?> processingFuture = processingFutures.remove(clientId);
        if (processingFuture == null) {
            log.warn("[{}] Cannot find processing future for client.", clientId);
        } else {
            try {
                processingFuture.cancel(true);
                statsManager.clearApplicationProcessorStats(clientId);
            } catch (Exception e) {
                log.warn("[{}] Exception stopping future for client.", clientId, e);
            }
        }
    }

    private ApplicationPackProcessingCtx collectPackProcessingCtx(String clientId) {
        ApplicationPackProcessingCtx processingContext = packProcessingCtxMap.remove(clientId);
        if (processingContext == null) {
            processingContext = new ApplicationPackProcessingCtx(clientId);
        }
        Set<ApplicationSharedSubscriptionCtx> contexts = sharedSubscriptionsPackProcessingCtxMap.remove(clientId);
        if (!CollectionUtils.isEmpty(contexts)) {
            for (ApplicationSharedSubscriptionCtx ctx : contexts) {
                processingContext.getPublishPendingMsgMap().putAll(ctx.getPackProcessingCtx().getPublishPendingMsgMap());
                processingContext.getPubRelPendingMsgMap().putAll(ctx.getPackProcessingCtx().getPubRelPendingMsgMap());
                processingContext.getPubRelMsgCtx().getPubRelMessagesToDeliver().addAll(ctx.getPackProcessingCtx().getPubRelMsgCtx().getPubRelMessagesToDeliver());
            }
        }
        return processingContext;
    }

    @Override
    public void stopProcessingSharedSubscriptions(ClientSessionCtx clientSessionCtx, Set<TopicSharedSubscription> subscriptions) {
        if (CollectionUtils.isEmpty(subscriptions)) {
            return;
        }
        var clientId = clientSessionCtx.getClientId();
        if (log.isDebugEnabled()) {
            log.debug("[{}] Stopping processing shared subscriptions.", clientId);
        }
        clientLogger.logEvent(clientId, this.getClass(), "Stopping processing shared subscriptions");
        stopAndRemoveSharedSubscriptionJobs(subscriptions, clientId);
        stopAndRemoveSharedSubscriptionConsumers(subscriptions, clientId);
        removeSharedSubscriptionContexts(subscriptions, clientId);
    }

    private void stopAndRemoveSharedSubscriptionConsumers(Set<TopicSharedSubscription> subscriptions, String clientId) {
        ConcurrentMap<TopicSharedSubscription, TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishMsgProto>>> sharedSubsToConsumerMap =
                sharedSubscriptionConsumers.get(clientId);
        if (!CollectionUtils.isEmpty(sharedSubsToConsumerMap)) {
            sharedSubsToConsumerMap.forEach((subscription, consumer) -> {
                if (subscriptions.contains(subscription)) {
                    consumer.unsubscribeAndClose();
                    sharedSubsToConsumerMap.remove(subscription);
                }
            });
        }
    }

    private void removeSharedSubscriptionContexts(Set<TopicSharedSubscription> subscriptions, String clientId) {
        Set<ApplicationSharedSubscriptionCtx> contexts = sharedSubscriptionsPackProcessingCtxMap.get(clientId);
        if (!CollectionUtils.isEmpty(contexts)) {
            contexts.removeIf(ctx -> subscriptions.contains(ctx.getSubscription()));
        }
    }

    private void stopAndRemoveSharedSubscriptionJobs(Set<TopicSharedSubscription> subscriptions, String clientId) {
        List<ApplicationSharedSubscriptionJob> jobs = sharedSubscriptionsProcessingJobs.get(clientId);
        if (!CollectionUtils.isEmpty(jobs)) {
            try {
                jobs.removeAll(collectCancelledJobs(subscriptions, clientId, jobs));
            } catch (Exception e) {
                log.warn("[{}] Exception stopping future for client", clientId, e);
            }
        }
    }

    List<ApplicationSharedSubscriptionJob> collectCancelledJobs(Set<TopicSharedSubscription> subscriptions,
                                                                String clientId,
                                                                List<ApplicationSharedSubscriptionJob> jobs) {
        return jobs.stream()
                .filter(job -> subscriptions.contains(job.getSubscription()))
                .peek(job -> {
                    cancelJob(job);
                    statsManager.clearSharedApplicationProcessorStats(clientId, job.getSubscription());
                })
                .collect(Collectors.toList());
    }

    private void cancelJob(ApplicationSharedSubscriptionJob job) {
        if (log.isDebugEnabled()) {
            log.debug("Canceling job {}", job);
        }
        job.getFuture().cancel(true);
        job.setInterrupted(true);
    }

    @Override
    public void clearPersistedMsgs(String clientId) {
        clientLogger.logEvent(clientId, this.getClass(), "Clearing persisted messages");
        String applicationConsumerGroup = appClientHelperService.getAppConsumerGroup(clientId);
        if (log.isDebugEnabled()) {
            log.debug("[{}] Clearing consumer group {} for application.", clientId, applicationConsumerGroup);
        }
        queueAdmin.deleteConsumerGroups(Collections.singleton(applicationConsumerGroup));
        if (log.isDebugEnabled()) {
            log.debug("[{}] Clearing application session context.", clientId);
        }
        unacknowledgedPersistedMsgCtxService.clearContext(clientId);
        persistedMsgCtxMap.remove(clientId);
    }

    private TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishMsgProto>> initConsumer(String clientId, String clientTopic) {
        TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishMsgProto>> consumer = createConsumer(clientId, clientTopic);
        try {
            consumer.assignPartition(0);

            Optional<Long> committedOffset = consumer.getCommittedOffset(consumer.getTopic(), 0);
            if (committedOffset.isEmpty()) {
                long endOffset = consumer.getEndOffset(consumer.getTopic(), 0);
                consumer.commit(0, endOffset);
            }
            return consumer;
        } catch (Exception e) {
            log.error("[{}] Failed to init application client consumer.", clientId, e);
            consumer.unsubscribeAndClose();
            throw e;
        }
    }

    private TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishMsgProto>> createConsumer(String clientId, String clientTopic) {
        return applicationPersistenceMsgQueueFactory
                .createConsumer(
                        clientTopic,
                        appClientHelperService.getAppConsumerGroup(clientId),
                        getConsumerId(clientId));
    }

    private String getConsumerId(String clientId) {
        return serviceInfoProvider.getServiceId() + "-" + clientId;
    }

    private void processPersistedMessages(TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishMsgProto>> consumer,
                                          ClientActorStateInfo clientState) {
        ClientSessionCtx clientSessionCtx = clientState.getCurrentSessionCtx();
        String clientId = clientSessionCtx.getClientId();
        UUID sessionId = clientSessionCtx.getSessionId();

        ApplicationProcessorStats stats = statsManager.createApplicationProcessorStats(clientId);

        ApplicationPersistedMsgCtx persistedMsgCtx = unacknowledgedPersistedMsgCtxService.loadPersistedMsgCtx(clientId);
        persistedMsgCtxMap.put(clientId, persistedMsgCtx);

        // TODO: make consistent with logic for DEVICES
        clientSessionCtx.getMsgIdSeq().updateMsgIdSequence(persistedMsgCtx.getLastPacketId());

        ApplicationPubRelMsgCtx applicationPubRelMsgCtx = persistedMsgCtxToPubRelMsgCtx(persistedMsgCtx);

        while (isClientConnected(sessionId, clientState)) {
            try {
                List<TbProtoQueueMsg<PublishMsgProto>> publishProtoMessages = consumer.poll(pollDuration);
                if (publishProtoMessages.isEmpty() && applicationPubRelMsgCtx.nothingToDeliver()) {
                    continue;
                }

                long packProcessingStart = System.nanoTime();
                ApplicationSubmitStrategy submitStrategy = submitStrategyFactory.newInstance(clientId);

                List<PersistedMsg> messagesToDeliver = getMessagesToDeliver(
                        applicationPubRelMsgCtx,
                        clientSessionCtx,
                        persistedMsgCtx,
                        publishProtoMessages,
                        null);
                submitStrategy.init(messagesToDeliver);

                applicationPubRelMsgCtx = new ApplicationPubRelMsgCtx(Sets.newConcurrentHashSet());
                while (isClientConnected(sessionId, clientState)) {
                    ApplicationPackProcessingCtx ctx = newPackProcessingCtx(submitStrategy, applicationPubRelMsgCtx, stats);
                    int totalPublishMsgs = ctx.getPublishPendingMsgMap().size();
                    int totalPubRelMsgs = ctx.getPubRelPendingMsgMap().size();
                    packProcessingCtxMap.put(clientId, ctx);

                    process(submitStrategy, clientSessionCtx, clientId);

                    if (isClientConnected(sessionId, clientState)) {
                        ctx.await(packProcessingTimeout, TimeUnit.MILLISECONDS);
                    }

                    if (analyzeIfProcessingDone(clientId, consumer, stats, submitStrategy, ctx, totalPublishMsgs, totalPubRelMsgs))
                        break;
                }
                if (isTraceEnabled) {
                    log.trace("[{}] Pack processing took {} ms, pack size - {}",
                            clientId, (double) (System.nanoTime() - packProcessingStart) / 1_000_000, messagesToDeliver.size());
                }
            } catch (Exception e) {
                if (isClientConnected(sessionId, clientState)) {
                    log.warn("[{}] Failed to process messages from queue.", clientId, e);
                    try {
                        Thread.sleep(pollDuration);
                    } catch (InterruptedException e2) {
                        if (isTraceEnabled) {
                            log.trace("Failed to wait until the server has capacity to handle new requests", e2);
                        }
                    }
                }
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("[{}] Application persisted messages consumer stopped.", clientId);
        }
    }

    private void process(ApplicationSubmitStrategy submitStrategy, ClientSessionCtx clientSessionCtx, String clientId) {
        if (isDebugEnabled) {
            log.debug("[{}] Start sending the pack of messages from processing ctx: {}", clientId, submitStrategy.getOrderedMessages());
        }
        submitStrategy.process(msg -> {
            switch (msg.getPacketType()) {
                case PUBLISH:
                    PublishMsg publishMsg = ((PersistedPublishMsg) msg).getPublishMsg();
                    publishMsgDeliveryService.sendPublishMsgToClientWithoutFlush(clientSessionCtx, publishMsg);
                    break;
                case PUBREL:
                    publishMsgDeliveryService.sendPubRelMsgToClientWithoutFlush(clientSessionCtx, msg.getPacketId());
                    break;
            }
            clientLogger.logEvent(clientId, this.getClass(), "Delivered msg to application client");
        });
        clientSessionCtx.getChannel().flush();
    }

    private int getMinQoSValue(TopicSharedSubscription subscription, int publishMsgQos) {
        return subscription == null ? publishMsgQos : Math.min(subscription.getQos(), publishMsgQos);
    }

    private List<PersistedMsg> collectMessagesToDeliver(List<PersistedPubRelMsg> pubRelMessagesToDeliver,
                                                        List<PersistedPublishMsg> publishMessagesToDeliver) {
        List<PersistedMsg> messagesToDeliver = new ArrayList<>(publishMessagesToDeliver.size());
        if (!CollectionUtils.isEmpty(pubRelMessagesToDeliver)) {
            messagesToDeliver.addAll(pubRelMessagesToDeliver);
        }
        if (!CollectionUtils.isEmpty(publishMessagesToDeliver)) {
            messagesToDeliver.addAll(publishMessagesToDeliver);
        }
        return messagesToDeliver;
    }

    private ApplicationPubRelMsgCtx persistedMsgCtxToPubRelMsgCtx(ApplicationPersistedMsgCtx persistedMsgCtx) {
        return new ApplicationPubRelMsgCtx(
                persistedMsgCtx.getPubRelMsgIds().entrySet().stream()
                        .map(entry -> new PersistedPubRelMsg(entry.getValue(), entry.getKey()))
                        .collect(Collectors.toSet())
        );
    }

    private List<PersistedPublishMsg> toPublishMessagesToDeliver(ClientSessionCtx clientSessionCtx,
                                                                 ApplicationPersistedMsgCtx persistedMsgCtx,
                                                                 List<TbProtoQueueMsg<PublishMsgProto>> publishProtoMessages,
                                                                 TopicSharedSubscription subscription) {
        long currentTs = System.currentTimeMillis();
        List<PersistedPublishMsg> result = new ArrayList<>(publishProtoMessages.size());
        for (TbProtoQueueMsg<PublishMsgProto> msg : publishProtoMessages) {
            MsgExpiryResult msgExpiryResult = MqttPropertiesUtil.getMsgExpiryResult(msg.getHeaders(), currentTs);
            if (msgExpiryResult.isExpired()) {
                continue;
            }

            var msgPacketId = persistedMsgCtx.getMsgPacketId(msg.getOffset());
            int packetId = msgPacketId != null ? msgPacketId : clientSessionCtx.getMsgIdSeq().nextMsgId();
            boolean isDup = msgPacketId != null;
            int minQoSValue = getMinQoSValue(subscription, msg.getValue().getQos());

            PublishMsg publishMsg = ProtoConverter.convertToPublishMsg(msg.getValue(), packetId, minQoSValue, isDup);
            if (msgExpiryResult.isMsgExpiryIntervalPresent()) {
                MqttPropertiesUtil.addMsgExpiryIntervalToProps(publishMsg.getProperties(), msgExpiryResult.getMsgExpiryInterval());
            }
            result.add(new PersistedPublishMsg(publishMsg, msg.getOffset(), subscription != null));
        }
        return result;
    }

    private boolean isClientConnected(UUID sessionId, ClientActorStateInfo clientState) {
        return isProcessorActive()
                && clientState.getCurrentSessionId().equals(sessionId)
                && clientState.getCurrentSessionState() == SessionState.CONNECTED;
    }

    private boolean isJobActive(ApplicationSharedSubscriptionJob job) {
        return isProcessorActive() && !job.interrupted();
    }

    private boolean isProcessorActive() {
        return !stopped && !Thread.interrupted();
    }

    @PreDestroy
    public void destroy() {
        stopped = true;
        sharedSubscriptionsProcessingJobs.forEach((clientId, jobs) -> jobs.forEach(j -> j.getFuture().cancel(true)));
        processingFutures.forEach((clientId, future) -> {
            future.cancel(true);
            log.info("[{}] Saving processing context before shutting down.", clientId);
            ApplicationPackProcessingCtx processingContext = collectPackProcessingCtx(clientId);
            try {
                unacknowledgedPersistedMsgCtxService.saveContext(clientId, processingContext);
            } catch (Exception e) {
                log.warn("[{}] Failed to save APPLICATION context.", clientId);
            }
        });
        shutdownExecutor(persistedMsgsConsumerExecutor, "persistence");
        shutdownExecutor(sharedSubsMsgsConsumerExecutor, "shared subscriptions");
    }

    private void shutdownExecutor(ExecutorService service, String name) {
        if (service == null) {
            return;
        }
        service.shutdown();
        try {
            boolean terminationSuccessful = service.awaitTermination(3, TimeUnit.SECONDS);
            log.info("Application {} consumers' executor termination is: [{}]", name, terminationSuccessful ? "successful" : "failed");
        } catch (InterruptedException e) {
            log.warn("Failed to stop application {} consumers' executor gracefully due to interruption!", name, e);
        }
    }
}
