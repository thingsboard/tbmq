/**
 * Copyright © 2016-2026 The Thingsboard Authors
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
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.common.data.mqtt.MsgExpiryResult;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;
import org.thingsboard.mqtt.broker.gen.queue.PublishMsgProto;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.queue.TbQueueConsumer;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.ApplicationPersistenceMsgQueueFactory;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMsgDeliveryService;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.data.ApplicationSharedSubscriptionCtx;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.data.ApplicationSharedSubscriptionJob;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.delivery.AppMsgDeliveryStrategy;
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
import org.thingsboard.mqtt.broker.util.MqttQosUtil;

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

    private final ConcurrentMap<String, ApplicationPackProcessingCtx> mainPackProcessingCtxMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Future<?>> mainProcessingFutures = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishMsgProto>>> mainConsumers = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, ApplicationPersistedMsgCtx> persistedMsgCtxMap = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, Set<ApplicationSharedSubscriptionCtx>> sharedPackProcessingCtxMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, List<ApplicationSharedSubscriptionJob>> sharedProcessingJobs = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConcurrentMap<TopicSharedSubscription, TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishMsgProto>>>>
            sharedSubscriptionConsumers = new ConcurrentHashMap<>();

    private final ApplicationMsgAcknowledgeStrategyFactory acknowledgeStrategyFactory;
    private final ApplicationSubmitStrategyFactory submitStrategyFactory;
    private final ApplicationPersistenceMsgQueueFactory applicationPersistenceMsgQueueFactory;
    private final MqttMsgDeliveryService mqttMsgDeliveryService;
    private final TbQueueAdmin queueAdmin;
    private final StatsManager statsManager;
    private final ApplicationPersistedMsgCtxService unacknowledgedPersistedMsgCtxService;
    private final ClientMqttActorManager clientMqttActorManager;
    private final ServiceInfoProvider serviceInfoProvider;
    private final ClientLogger clientLogger;
    private final ApplicationTopicService applicationTopicService;
    private final ApplicationClientHelperService appClientHelperService;
    private final AppMsgDeliveryStrategy appMsgDeliveryStrategy;
    private final boolean isTraceEnabled = log.isTraceEnabled();
    private final boolean isDebugEnabled = log.isDebugEnabled();

    @Value("${queue.application-persisted-msg.poll-interval}")
    private long pollDuration;
    @Value("${queue.application-persisted-msg.pack-processing-timeout}")
    private long packProcessingTimeout;
    @Value("${queue.application-persisted-msg.shared-topic-validation:true}")
    private boolean validateSharedTopicFilter;

    private volatile boolean stopped = false;
    private ExecutorService persistedMessageConsumerExecutor;
    private ExecutorService sharedSubscriptionConsumerExecutor;

    @PostConstruct
    public void init() {
        statsManager.registerActiveApplicationProcessorsStats(mainProcessingFutures);
        statsManager.registerActiveSharedApplicationProcessorsStats(sharedProcessingJobs);
        persistedMessageConsumerExecutor = ThingsBoardExecutors.initCachedExecutorService("application-persisted-msg-consumers");
        sharedSubscriptionConsumerExecutor = ThingsBoardExecutors.initCachedExecutorService("application-shared-subs-msg-consumers");
        log.info("Application persistence processor initialized");
    }

    @Override
    public void processPubAck(String clientId, int packetId) {
        ApplicationPackProcessingCtx processingContext = mainPackProcessingCtxMap.get(clientId);
        if (processingContext == null) {
            if (isDebugEnabled) {
                log.debug("[{}] No main processing context for PubAck, packetId: {}", clientId, packetId);
            }
            processPubAckInSharedCtx(clientId, packetId, "[{}] No processing context found for PubAck, packetId: {}");
        } else {
            var handled = processingContext.onPubAck(packetId);
            if (handled) {
                if (isDebugEnabled) {
                    log.debug("[{}] PubAck processed from main context, packetId: {}", clientId, packetId);
                }
                return;
            }
            processPubAckInSharedCtx(clientId, packetId, "[{}] No shared subscription context found for PubAck, packetId: {}");
        }
    }

    private void processPubAckInSharedCtx(String clientId, int packetId, String format) {
        Set<ApplicationSharedSubscriptionCtx> contexts = sharedPackProcessingCtxMap.get(clientId);
        if (CollectionUtils.isEmpty(contexts)) {
            log.warn(format, clientId, packetId);
            return;
        }
        for (ApplicationSharedSubscriptionCtx ctx : contexts) {
            var acknowledged = ctx.getPackProcessingCtx().onPubAck(packetId);
            if (acknowledged) {
                if (isDebugEnabled) {
                    log.debug("[{}] PubAck processed from shared subscription context, packetId: {}", clientId, packetId);
                }
                return;
            }
        }
    }

    @Override
    public void processPubRec(ClientSessionCtx clientSessionCtx, int packetId) {
        String clientId = clientSessionCtx.getClientId();
        ApplicationPackProcessingCtx processingContext = mainPackProcessingCtxMap.get(clientId);
        if (processingContext == null) {
            if (isDebugEnabled) {
                log.debug("[{}] No main processing context for PubRec, packetId: {}", clientId, packetId);
            }
            processPubRecInSharedCtx(clientSessionCtx, packetId,
                    "[{}] No processing context found for PubRec, packetId: {}", true);
        } else {
            var handled = processingContext.onPubRec(packetId, true);
            if (handled) {
                if (isDebugEnabled) {
                    log.debug("[{}] PubRec processed from main context, packetId: {}", clientId, packetId);
                }
                return;
            }
            processPubRecInSharedCtx(clientSessionCtx, packetId,
                    "[{}] No shared subscription context found for PubRec, packetId: {}", true);
        }
    }

    @Override
    public void processPubRecNoPubRelDelivery(ClientSessionCtx clientSessionCtx, int packetId) {
        String clientId = clientSessionCtx.getClientId();
        ApplicationPackProcessingCtx processingContext = mainPackProcessingCtxMap.get(clientId);
        if (processingContext == null) {
            if (isDebugEnabled) {
                log.debug("[{}] No main processing context for PubRec (no PubRel delivery), packetId: {}", clientId, packetId);
            }
            processPubRecInSharedCtx(clientSessionCtx, packetId,
                    "[{}] No processing context found for PubRec (no PubRel delivery), packetId: {}", false);
        } else {
            var handled = processingContext.onPubRec(packetId, false);
            if (handled) {
                if (isDebugEnabled) {
                    log.debug("[{}] PubRec (no PubRel delivery) processed from main context, packetId: {}", clientId, packetId);
                }
                return;
            }
            processPubRecInSharedCtx(clientSessionCtx, packetId,
                    "[{}] No shared subscription context found for PubRec (no PubRel delivery), packetId: {}", false);
        }
    }

    private void processPubRecInSharedCtx(ClientSessionCtx clientSessionCtx, int packetId, String format, boolean sendPubRelMsg) {
        Set<ApplicationSharedSubscriptionCtx> contexts = sharedPackProcessingCtxMap.get(clientSessionCtx.getClientId());
        if (CollectionUtils.isEmpty(contexts)) {
            log.warn(format, clientSessionCtx.getClientId(), packetId);
            if (sendPubRelMsg) {
                mqttMsgDeliveryService.sendPubRelMsgToClient(clientSessionCtx, packetId);
            }
            return;
        }
        for (ApplicationSharedSubscriptionCtx ctx : contexts) {
            var acknowledged = ctx.getPackProcessingCtx().onPubRec(packetId, sendPubRelMsg);
            if (acknowledged) {
                if (isDebugEnabled) {
                    log.debug("[{}] PubRec processed from shared subscription context, packetId: {}", clientSessionCtx.getClientId(), packetId);
                }
                return;
            }
        }
        if (sendPubRelMsg) {
            mqttMsgDeliveryService.sendPubRelMsgToClient(clientSessionCtx, packetId);
        }
    }

    @Override
    public void processPubComp(String clientId, int packetId) {
        ApplicationPackProcessingCtx processingContext = mainPackProcessingCtxMap.get(clientId);
        if (processingContext == null) {
            if (isDebugEnabled) {
                log.debug("[{}] No main processing context for PubComp, packetId: {}", clientId, packetId);
            }
            processPubCompInSharedCtx(clientId, packetId, "[{}] No processing context found for PubComp, packetId: {}");
        } else {
            var handled = processingContext.onPubComp(packetId);
            if (handled) {
                if (isDebugEnabled) {
                    log.debug("[{}] PubComp processed from main context, packetId: {}", clientId, packetId);
                }
                return;
            }
            processPubCompInSharedCtx(clientId, packetId, "[{}] No shared subscription context found for PubComp, packetId: {}");
        }
    }

    private void processPubCompInSharedCtx(String clientId, int packetId, String format) {
        Set<ApplicationSharedSubscriptionCtx> contexts = sharedPackProcessingCtxMap.get(clientId);
        if (CollectionUtils.isEmpty(contexts)) {
            log.warn(format, clientId, packetId);
            return;
        }
        for (ApplicationSharedSubscriptionCtx ctx : contexts) {
            var acknowledged = ctx.getPackProcessingCtx().onPubComp(packetId);
            if (acknowledged) {
                if (isDebugEnabled) {
                    log.debug("[{}] PubComp processed from shared subscription context, packetId: {}", clientId, packetId);
                }
                return;
            }
        }
    }

    @Override
    public void processChannelWritable(ClientActorStateInfo clientState) {
        String clientId = clientState.getClientId();
        log.trace("[{}] Channel is writable", clientId);
        if (mainConsumers.containsKey(clientId)) {
            resumeMainConsumer(clientId);
        } else {
            log.warn("[{}] Client is not active during channel writable event. Start processing", clientId);
            startProcessingPersistedMessages(clientState);
        }
        resumeSharedSubscriptionConsumers(clientId);
    }

    private void resumeMainConsumer(String clientId) {
        TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishMsgProto>> mainConsumer = mainConsumers.get(clientId);
        if (mainConsumer != null) {
            log.trace("[{}] Resuming main consumer", clientId);
            mainConsumer.resume();
        }
    }

    private void resumeSharedSubscriptionConsumers(String clientId) {
        var sharedSubsToConsumerMap = sharedSubscriptionConsumers.get(clientId);
        if (!CollectionUtils.isEmpty(sharedSubsToConsumerMap)) {
            log.trace("[{}] Resuming {} shared subscription consumer(s)", clientId, sharedSubsToConsumerMap.size());
            sharedSubsToConsumerMap.values().forEach(TbQueueConsumer::resume);
        }
    }

    @Override
    public void processChannelNonWritable(String clientId) {
        log.trace("[{}] Channel is not writable, pausing consumers", clientId);
        pauseAllConsumers(clientId);
    }

    private void pauseAllConsumers(String clientId) {
        TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishMsgProto>> mainConsumer = mainConsumers.get(clientId);
        if (mainConsumer != null) {
            log.trace("[{}] Pausing main consumer", clientId);
            mainConsumer.pause();
        }
        var sharedSubsToConsumerMap = sharedSubscriptionConsumers.get(clientId);
        if (!CollectionUtils.isEmpty(sharedSubsToConsumerMap)) {
            log.trace("[{}] Pausing {} shared subscription consumer(s)", clientId, sharedSubsToConsumerMap.size());
            sharedSubsToConsumerMap.values().forEach(TbQueueConsumer::pause);
        }
    }

    @Override
    public void startProcessingSharedSubscriptions(ClientSessionCtx clientSessionCtx, Set<TopicSharedSubscription> subscriptions) {
        if (CollectionUtils.isEmpty(subscriptions)) {
            return;
        }
        String clientId = clientSessionCtx.getClientId();
        clientLogger.logEvent(clientId, this.getClass(), "Starting processing shared subscriptions persisted messages");
        log.debug("[{}][{}] Starting persisted shared subscriptions processing", clientId, subscriptions);

        ApplicationPersistedMsgCtx persistedMsgCtx = persistedMsgCtxMap.getOrDefault(clientId, new ApplicationPersistedMsgCtx());

        for (TopicSharedSubscription subscription : subscriptions) {
            TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishMsgProto>> consumer = initSharedConsumerIfNotPresent(clientId, subscription);
            if (consumer == null) {
                continue;
            }
            ApplicationSharedSubscriptionJob job = new ApplicationSharedSubscriptionJob(subscription, null, false);
            Future<?> future = sharedSubscriptionConsumerExecutor.submit(
                    () -> processSharedSubscriptionMessages(job, consumer, clientSessionCtx, persistedMsgCtx, subscription));
            job.setFuture(future);
            sharedProcessingJobs
                    .computeIfAbsent(clientId, s -> new CopyOnWriteArrayList<>())
                    .add(job);
        }
    }

    private void processSharedSubscriptionMessages(ApplicationSharedSubscriptionJob job,
                                                   TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishMsgProto>> consumer,
                                                   ClientSessionCtx clientSessionCtx,
                                                   ApplicationPersistedMsgCtx persistedMsgCtx,
                                                   TopicSharedSubscription subscription) {
        String clientId = clientSessionCtx.getClientId();
        try {
            ApplicationProcessorStats stats = statsManager.createSharedApplicationProcessorStats(clientId, subscription);
            ApplicationPubRelMsgCtx pubRelMsgCtx = new ApplicationPubRelMsgCtx(Sets.newConcurrentHashSet());

            while (isJobActive(job)) {
                try {
                    List<TbProtoQueueMsg<PublishMsgProto>> messages = consumer.poll(pollDuration);
                    if (messages.isEmpty() && pubRelMsgCtx.nothingToDeliver()) {
                        continue;
                    }
                    pubRelMsgCtx = processSharedPack(pubRelMsgCtx, messages, clientSessionCtx, persistedMsgCtx, subscription, consumer, stats, job);
                } catch (Exception e) {
                    if (isJobActive(job)) {
                        log.warn("[{}] Failed to process messages from shared queue", clientId, e);
                        sleepOnError();
                    }
                }
            }
            log.debug("[{}] Shared subscription persisted messages consumer stopped", clientId);
        } catch (Exception e) {
            log.warn("[{}][{}] Failed to process shared subscription persisted messages", clientId, subscription, e);
            disconnectClient(clientId, clientSessionCtx.getSessionId());
        }
    }

    private ApplicationPubRelMsgCtx processSharedPack(ApplicationPubRelMsgCtx pubRelMsgCtx,
                                                      List<TbProtoQueueMsg<PublishMsgProto>> messages,
                                                      ClientSessionCtx clientSessionCtx,
                                                      ApplicationPersistedMsgCtx persistedMsgCtx,
                                                      TopicSharedSubscription subscription,
                                                      TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishMsgProto>> consumer,
                                                      ApplicationProcessorStats stats,
                                                      ApplicationSharedSubscriptionJob job) throws InterruptedException {
        String clientId = clientSessionCtx.getClientId();
        long packStart = System.nanoTime();

        ApplicationSubmitStrategy submitStrategy = submitStrategyFactory.newInstance(clientId);
        List<PersistedMsg> messagesToDeliver = buildMessagesToDeliver(pubRelMsgCtx, clientSessionCtx, persistedMsgCtx, messages, subscription);
        submitStrategy.init(messagesToDeliver);

        if (isTraceEnabled) {
            log.trace("[{}] Starting shared subscription pack, {} messages to deliver", clientId, messagesToDeliver.size());
        }

        ApplicationPubRelMsgCtx newPubRelMsgCtx = new ApplicationPubRelMsgCtx(Sets.newConcurrentHashSet());
        while (isJobActive(job)) {
            ApplicationPackProcessingCtx ctx = createPackProcessingCtx(submitStrategy, newPubRelMsgCtx, stats);
            int totalPublishMsgs = ctx.getPublishPendingMsgMap().size();
            int totalPubRelMsgs = ctx.getPubRelPendingMsgMap().size();
            updateSharedPackProcessingCtx(clientId, subscription, ctx);

            deliverMessages(submitStrategy, clientSessionCtx);

            if (isJobActive(job)) {
                log.trace("[{}] Awaiting shared subscription pack acknowledgement", clientId);
                ctx.await(packProcessingTimeout, TimeUnit.MILLISECONDS);
            }

            if (tryCommitPack(clientId, consumer, stats, submitStrategy, ctx, totalPublishMsgs, totalPubRelMsgs)) {
                break;
            }
        }

        log.trace("[{}] Pack processing took {} ms, pack size - {}",
                clientId, (double) (System.nanoTime() - packStart) / 1_000_000, messagesToDeliver.size());
        return newPubRelMsgCtx;
    }

    private List<PersistedMsg> buildMessagesToDeliver(ApplicationPubRelMsgCtx applicationPubRelMsgCtx,
                                                      ClientSessionCtx clientSessionCtx,
                                                      ApplicationPersistedMsgCtx persistedMsgCtx,
                                                      List<TbProtoQueueMsg<PublishMsgProto>> publishProtoMessages,
                                                      TopicSharedSubscription subscription) {
        List<PersistedPubRelMsg> pubRelMessagesToDeliver = applicationPubRelMsgCtx.toSortedPubRelMessagesToDeliver();
        List<PersistedPublishMsg> publishMessagesToDeliver = buildPublishMessagesToDeliver(
                clientSessionCtx, persistedMsgCtx, publishProtoMessages, subscription);
        return mergeMessagesToDeliver(pubRelMessagesToDeliver, publishMessagesToDeliver);
    }

    private boolean tryCommitPack(String clientId,
                                  TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishMsgProto>> consumer,
                                  ApplicationProcessorStats stats,
                                  ApplicationSubmitStrategy submitStrategy,
                                  ApplicationPackProcessingCtx ctx,
                                  int totalPublishMsgs,
                                  int totalPubRelMsgs) {
        log.trace("[{}] Analyzing pack processing result", clientId);
        ApplicationAckStrategy ackStrategy = acknowledgeStrategyFactory.newInstance(clientId);

        ApplicationPackProcessingResult result = new ApplicationPackProcessingResult(ctx);
        ApplicationProcessingDecision decision = ackStrategy.analyze(result);

        stats.log(totalPublishMsgs, totalPubRelMsgs, result, decision.isCommit());

        if (decision.isCommit()) {
            log.debug("[{}] Committing pack", clientId);
            ctx.clear();
            consumer.commitSync();
            return true;
        } else {
            if (isDebugEnabled) {
                log.debug("[{}] Reprocessing {} message(s)", clientId, decision.getReprocessMap().size());
            }
            submitStrategy.update(decision.getReprocessMap());
        }
        return false;
    }

    private void updateSharedPackProcessingCtx(String clientId,
                                               TopicSharedSubscription subscription,
                                               ApplicationPackProcessingCtx packProcessingCtx) {
        Set<ApplicationSharedSubscriptionCtx> contexts =
                sharedPackProcessingCtxMap.computeIfAbsent(clientId, s -> Sets.newConcurrentHashSet());
        contexts.removeIf(ctx -> ctx.getSubscription().equals(subscription));
        contexts.add(new ApplicationSharedSubscriptionCtx(subscription, packProcessingCtx));
    }

    private ApplicationPackProcessingCtx createPackProcessingCtx(ApplicationSubmitStrategy submitStrategy,
                                                                 ApplicationPubRelMsgCtx applicationPubRelMsgCtx,
                                                                 ApplicationProcessorStats stats) {
        return new ApplicationPackProcessingCtx(submitStrategy, applicationPubRelMsgCtx, stats);
    }

    private TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishMsgProto>> initSharedConsumerIfNotPresent(String clientId,
                                                                                                             TopicSharedSubscription subscription) {
        var sharedSubsToConsumerMap = sharedSubscriptionConsumers.computeIfAbsent(clientId, s -> new ConcurrentHashMap<>());
        if (sharedSubsToConsumerMap.containsKey(subscription)) {
            log.debug("[{}][{}] Shared subscription consumer already initialized, skipping", clientId, subscription);
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
        log.debug("[{}][{}] Initializing shared subscription consumer, topic: '{}', group: '{}'",
                clientId, subscription, sharedAppTopic, sharedAppConsumerGroup);
        var consumer = applicationPersistenceMsgQueueFactory.createConsumerForSharedTopic(sharedAppTopic, sharedAppConsumerGroup, sharedConsumerId);
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
        Future<?> existingFuture = mainProcessingFutures.get(clientId);
        if (existingFuture != null && !existingFuture.isDone()) {
            log.warn("[{}] Processing already active, skipping duplicate start", clientId);
            return;
        }
        String clientTopic = applicationTopicService.createTopic(clientId);
        clientLogger.logEvent(clientId, this.getClass(), "Starting processing persisted messages");
        log.debug("[{}] Starting persisted messages processing", clientId);
        TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishMsgProto>> consumer = initConsumer(clientId, clientTopic);
        mainConsumers.put(clientId, consumer);
        Future<?> future = persistedMessageConsumerExecutor.submit(() -> {
            try {
                processPersistedMessages(consumer, clientState);
            } catch (Exception e) {
                log.warn("[{}] Failed to start processing persisted messages", clientId, e);
                disconnectClient(clientId, clientState);
            } finally {
                consumer.unsubscribeAndClose();
                mainConsumers.remove(clientId);
            }
        });
        mainProcessingFutures.put(clientId, future);
    }

    private void disconnectClient(String clientId, ClientActorStateInfo clientState) {
        disconnectClient(clientId, clientState.getCurrentSessionCtx().getSessionId(), "Failed to start processing persisted messages");
    }

    private void disconnectClient(String clientId, UUID sessionId, String message) {
        clientMqttActorManager.disconnect(clientId, new MqttDisconnectMsg(
                sessionId,
                new DisconnectReason(DisconnectReasonType.ON_ERROR, message)));
    }

    @Override
    public void stopProcessingPersistedMessages(String clientId) {
        log.debug("[{}] Stopping persisted messages processing", clientId);
        clientLogger.logEvent(clientId, this.getClass(), "Stopping processing persisted messages");
        cancelMainProcessing(clientId);
        cancelSharedSubscriptionProcessing(clientId);
        stopSharedSubscriptionConsumers(clientId);

        ApplicationPackProcessingCtx processingContext = mergePackProcessingContexts(clientId);
        unacknowledgedPersistedMsgCtxService.saveContext(clientId, processingContext);
        persistedMsgCtxMap.remove(clientId);
    }

    private void stopSharedSubscriptionConsumers(String clientId) {
        ConcurrentMap<TopicSharedSubscription, TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishMsgProto>>> map =
                sharedSubscriptionConsumers.remove(clientId);
        if (!CollectionUtils.isEmpty(map)) {
            log.debug("[{}] Stopping {} shared subscription consumer(s)", clientId, map.size());
            map.values().forEach(TbQueueConsumer::unsubscribeAndClose);
        }
    }

    private void cancelSharedSubscriptionProcessing(String clientId) {
        List<ApplicationSharedSubscriptionJob> jobs = sharedProcessingJobs.remove(clientId);
        if (CollectionUtils.isEmpty(jobs)) {
            log.debug("[{}] No shared processing jobs found for client", clientId);
        } else {
            try {
                jobs.forEach(this::cancelJob);
                statsManager.clearSharedApplicationProcessorStats(clientId);
            } catch (Exception e) {
                log.warn("[{}] Failed to cancel shared subscription processing jobs", clientId, e);
            }
        }
    }

    private void cancelMainProcessing(String clientId) {
        mainConsumers.remove(clientId);
        Future<?> processingFuture = mainProcessingFutures.remove(clientId);
        if (processingFuture == null) {
            log.warn("[{}] No main processing future found for client", clientId);
        } else {
            try {
                processingFuture.cancel(false);
                statsManager.clearApplicationProcessorStats(clientId);
            } catch (Exception e) {
                log.warn("[{}] Failed to cancel main processing future", clientId, e);
            }
        }
    }

    private ApplicationPackProcessingCtx mergePackProcessingContexts(String clientId) {
        ApplicationPackProcessingCtx processingContext = mainPackProcessingCtxMap.remove(clientId);
        if (processingContext == null) {
            processingContext = new ApplicationPackProcessingCtx(clientId);
        }
        Set<ApplicationSharedSubscriptionCtx> contexts = sharedPackProcessingCtxMap.remove(clientId);
        if (!CollectionUtils.isEmpty(contexts)) {
            log.debug("[{}] Merging {} shared subscription processing context(s) into main context", clientId, contexts.size());
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
        log.debug("[{}] Stopping processing shared subscriptions", clientId);
        clientLogger.logEvent(clientId, this.getClass(), "Stopping processing shared subscriptions");
        stopAndRemoveSharedSubscriptionJobs(subscriptions, clientId);
        stopAndRemoveSharedSubscriptionConsumers(subscriptions, clientId);
        removeSharedSubscriptionContexts(subscriptions, clientId);
    }

    private void stopAndRemoveSharedSubscriptionConsumers(Set<TopicSharedSubscription> subscriptions, String clientId) {
        var sharedSubsToConsumerMap = sharedSubscriptionConsumers.get(clientId);
        if (CollectionUtils.isEmpty(sharedSubsToConsumerMap)) {
            return;
        }
        for (TopicSharedSubscription subscription : subscriptions) {
            TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishMsgProto>> consumer = sharedSubsToConsumerMap.remove(subscription);
            if (consumer != null) {
                consumer.unsubscribeAndClose();
            }
        }
    }

    private void removeSharedSubscriptionContexts(Set<TopicSharedSubscription> subscriptions, String clientId) {
        Set<ApplicationSharedSubscriptionCtx> contexts = sharedPackProcessingCtxMap.get(clientId);
        if (!CollectionUtils.isEmpty(contexts)) {
            contexts.removeIf(ctx -> subscriptions.contains(ctx.getSubscription()));
        }
    }

    private void stopAndRemoveSharedSubscriptionJobs(Set<TopicSharedSubscription> subscriptions, String clientId) {
        List<ApplicationSharedSubscriptionJob> jobs = sharedProcessingJobs.get(clientId);
        if (!CollectionUtils.isEmpty(jobs)) {
            try {
                jobs.removeAll(cancelAndCollectJobs(subscriptions, clientId, jobs));
            } catch (Exception e) {
                log.warn("[{}] Failed to stop shared subscription jobs", clientId, e);
            }
        }
    }

    List<ApplicationSharedSubscriptionJob> cancelAndCollectJobs(Set<TopicSharedSubscription> subscriptions,
                                                                String clientId,
                                                                List<ApplicationSharedSubscriptionJob> jobs) {
        List<ApplicationSharedSubscriptionJob> cancelledJobs = jobs.stream()
                .filter(job -> subscriptions.contains(job.getSubscription()))
                .collect(Collectors.toList());
        cancelledJobs.forEach(job -> {
            cancelJob(job);
            statsManager.clearSharedApplicationProcessorStats(clientId, job.getSubscription());
        });
        return cancelledJobs;
    }

    private void cancelJob(ApplicationSharedSubscriptionJob job) {
        if (isDebugEnabled) {
            log.debug("Cancelling shared subscription job for subscription {}", job.getSubscription());
        }
        Future<?> future = job.getFuture();
        if (future != null) {
            future.cancel(false);
        }
        job.setInterrupted(true);
    }

    @Override
    public void clearPersistedMessages(String clientId) {
        clientLogger.logEvent(clientId, this.getClass(), "Clearing persisted messages");
        String applicationConsumerGroup = appClientHelperService.getAppConsumerGroup(clientId);
        log.debug("[{}] Clearing consumer group {} for application", clientId, applicationConsumerGroup);
        queueAdmin.deleteConsumerGroups(Collections.singleton(applicationConsumerGroup));
        log.debug("[{}] Clearing application session context", clientId);
        unacknowledgedPersistedMsgCtxService.clearContext(clientId);
        persistedMsgCtxMap.remove(clientId);
    }

    private TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishMsgProto>> initConsumer(String clientId, String clientTopic) {
        TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishMsgProto>> consumer = createConsumer(clientId, clientTopic);
        try {
            consumer.assignPartition(0);

            Optional<Long> committedOffset = consumer.getCommittedOffset(consumer.getTopic(), 0);
            log.debug("[{}] Loaded committed offset: {}", clientId, committedOffset);
            if (committedOffset.isEmpty()) {
                long endOffset = consumer.getEndOffset(consumer.getTopic(), 0);
                consumer.commit(0, endOffset);
                log.debug("[{}] Committed endOffset {}", clientId, endOffset);
            }
            return consumer;
        } catch (Exception e) {
            log.error("[{}] Failed to init application client consumer", clientId, e);
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
        log.info("[{}] Loaded persisted message context: {}", clientId, persistedMsgCtx);
        persistedMsgCtxMap.put(clientId, persistedMsgCtx);

        // TODO: make consistent with logic for DEVICES
        clientSessionCtx.getMsgIdSeq().updateMsgIdSequence(persistedMsgCtx.getLastPacketId());

        ApplicationPubRelMsgCtx pubRelMsgCtx = buildPubRelMsgCtx(persistedMsgCtx);

        while (isClientSessionActive(sessionId, clientState)) {
            try {
                List<TbProtoQueueMsg<PublishMsgProto>> messages = consumer.poll(pollDuration);
                if (messages.isEmpty() && pubRelMsgCtx.nothingToDeliver()) {
                    continue;
                }
                pubRelMsgCtx = processMainPack(pubRelMsgCtx, messages, clientSessionCtx, persistedMsgCtx, consumer, stats, sessionId, clientState);
            } catch (Exception e) {
                if (isClientSessionActive(sessionId, clientState)) {
                    log.warn("[{}] Failed to process messages from queue", clientId, e);
                    sleepOnError();
                }
            }
        }
        log.debug("[{}] Application persisted messages consumer stopped", clientId);
    }

    private ApplicationPubRelMsgCtx processMainPack(ApplicationPubRelMsgCtx pubRelMsgCtx,
                                                    List<TbProtoQueueMsg<PublishMsgProto>> messages,
                                                    ClientSessionCtx clientSessionCtx,
                                                    ApplicationPersistedMsgCtx persistedMsgCtx,
                                                    TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishMsgProto>> consumer,
                                                    ApplicationProcessorStats stats,
                                                    UUID sessionId,
                                                    ClientActorStateInfo clientState) throws InterruptedException {
        String clientId = clientSessionCtx.getClientId();
        long packStart = System.nanoTime();

        ApplicationSubmitStrategy submitStrategy = submitStrategyFactory.newInstance(clientId);
        List<PersistedMsg> messagesToDeliver = buildMessagesToDeliver(pubRelMsgCtx, clientSessionCtx, persistedMsgCtx, messages, null);
        submitStrategy.init(messagesToDeliver);

        if (isDebugEnabled) {
            log.debug("[{}] Starting main pack, {} messages to deliver", clientId, messagesToDeliver.size());
        }

        ApplicationPubRelMsgCtx newPubRelMsgCtx = new ApplicationPubRelMsgCtx(Sets.newConcurrentHashSet());
        while (isClientSessionActive(sessionId, clientState)) {
            ApplicationPackProcessingCtx ctx = createPackProcessingCtx(submitStrategy, newPubRelMsgCtx, stats);
            int totalPublishMsgs = ctx.getPublishPendingMsgMap().size();
            int totalPubRelMsgs = ctx.getPubRelPendingMsgMap().size();
            mainPackProcessingCtxMap.put(clientId, ctx);

            deliverMessages(submitStrategy, clientSessionCtx);

            if (isClientSessionActive(sessionId, clientState)) {
                log.trace("[{}] Awaiting pack acknowledgement", clientId);
                ctx.await(packProcessingTimeout, TimeUnit.MILLISECONDS);
            }

            if (tryCommitPack(clientId, consumer, stats, submitStrategy, ctx, totalPublishMsgs, totalPubRelMsgs)) {
                break;
            }
        }

        log.trace("[{}] Pack processing took {} ms, pack size - {}",
                clientId, (double) (System.nanoTime() - packStart) / 1_000_000, messagesToDeliver.size());
        return newPubRelMsgCtx;
    }

    private void deliverMessages(ApplicationSubmitStrategy submitStrategy, ClientSessionCtx clientSessionCtx) {
        appMsgDeliveryStrategy.process(submitStrategy, clientSessionCtx);
    }

    private int resolveQos(TopicSharedSubscription subscription, int publishMsgQos) {
        return subscription == null ? publishMsgQos : MqttQosUtil.downgradeQos(subscription, publishMsgQos);
    }

    private List<PersistedMsg> mergeMessagesToDeliver(List<PersistedPubRelMsg> pubRelMessagesToDeliver,
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

    private ApplicationPubRelMsgCtx buildPubRelMsgCtx(ApplicationPersistedMsgCtx persistedMsgCtx) {
        return new ApplicationPubRelMsgCtx(
                persistedMsgCtx.getPubRelMsgIds().entrySet().stream()
                        .map(entry -> new PersistedPubRelMsg(entry.getValue(), entry.getKey()))
                        .collect(Collectors.toSet())
        );
    }

    private List<PersistedPublishMsg> buildPublishMessagesToDeliver(ClientSessionCtx clientSessionCtx,
                                                                    ApplicationPersistedMsgCtx persistedMsgCtx,
                                                                    List<TbProtoQueueMsg<PublishMsgProto>> publishProtoMessages,
                                                                    TopicSharedSubscription subscription) {
        long currentTs = System.currentTimeMillis();
        String clientId = clientSessionCtx.getClientId();
        List<PersistedPublishMsg> result = new ArrayList<>(publishProtoMessages.size());
        for (TbProtoQueueMsg<PublishMsgProto> msg : publishProtoMessages) {
            MsgExpiryResult msgExpiryResult = MqttPropertiesUtil.getMsgExpiryResult(msg.getHeaders(), currentTs);
            if (msgExpiryResult.isExpired()) {
                if (isTraceEnabled) {
                    log.trace("[{}] Message at offset {} has expired, skipping", clientId, msg.getOffset());
                }
                continue;
            }

            var msgPacketId = persistedMsgCtx.getMsgPacketId(msg.getOffset());
            int packetId = msgPacketId != null ? msgPacketId : clientSessionCtx.getMsgIdSeq().nextMsgId();
            boolean isDup = msgPacketId != null;
            int qos = resolveQos(subscription, msg.getValue().getQos());
            int subscriptionId = subscription == null ? -1 : subscription.getSubscriptionId();

            PublishMsg publishMsg = ProtoConverter.convertToPublishMsg(msg.getValue(), packetId, qos, isDup, subscriptionId);
            if (msgExpiryResult.isMsgExpiryIntervalPresent()) {
                MqttPropertiesUtil.addMsgExpiryIntervalToProps(publishMsg.getProperties(), msgExpiryResult.getMsgExpiryInterval());
            }
            result.add(new PersistedPublishMsg(publishMsg, msg.getOffset(), subscription != null));
        }
        return result;
    }

    private boolean isClientSessionActive(UUID sessionId, ClientActorStateInfo clientState) {
        return isProcessorActive()
                && clientState.getCurrentSessionId().equals(sessionId)
                && clientState.getCurrentSessionState().isMqttProcessable();
    }

    private boolean isJobActive(ApplicationSharedSubscriptionJob job) {
        return isProcessorActive() && !job.isInterrupted();
    }

    private boolean isProcessorActive() {
        return !stopped && !Thread.currentThread().isInterrupted();
    }

    private void sleepOnError() {
        try {
            Thread.sleep(pollDuration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (isTraceEnabled) {
                log.trace("Thread interrupted during error backoff sleep", e);
            }
        }
    }

    @PreDestroy
    public void destroy() {
        log.info("Destroying application persistence processor");
        stopped = true;
        sharedProcessingJobs.forEach((clientId, jobs) -> jobs.forEach(this::cancelJob));
        sharedSubscriptionConsumers.forEach((clientId, consumers) ->
                consumers.values().forEach(TbQueueConsumer::unsubscribeAndClose));
        mainProcessingFutures.forEach((clientId, future) -> {
            future.cancel(false);
            log.info("[{}] Saving processing context before shutting down", clientId);
            ApplicationPackProcessingCtx processingContext = mergePackProcessingContexts(clientId);
            try {
                unacknowledgedPersistedMsgCtxService.saveContext(clientId, processingContext);
            } catch (Exception e) {
                log.warn("[{}] Failed to save application processing context", clientId, e);
            }
        });
        ThingsBoardExecutors.shutdownAndAwaitTermination(persistedMessageConsumerExecutor, "Application persisted message consumers'");
        ThingsBoardExecutors.shutdownAndAwaitTermination(sharedSubscriptionConsumerExecutor, "Application shared subs consumers'");
    }

}
