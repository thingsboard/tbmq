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
package org.thingsboard.mqtt.broker.integration.service.processing;

import com.google.common.collect.Maps;
import com.google.protobuf.GeneratedMessageV3;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.util.CallbackUtil;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;
import org.thingsboard.mqtt.broker.gen.integration.ClientLifecycleEventMsgProto;
import org.thingsboard.mqtt.broker.gen.integration.PublishIntegrationMsgProto;
import org.thingsboard.mqtt.broker.integration.api.IntegrationStatisticsService;
import org.thingsboard.mqtt.broker.integration.api.TbPlatformIntegration;
import org.thingsboard.mqtt.broker.integration.api.data.IntegrationPackProcessingContext;
import org.thingsboard.mqtt.broker.integration.api.data.IntegrationPackProcessingResult;
import org.thingsboard.mqtt.broker.integration.api.stats.IntegrationProcessorStats;
import org.thingsboard.mqtt.broker.integration.service.data.IntegrationHolder;
import org.thingsboard.mqtt.broker.integration.service.processing.backpressure.IntegrationAckStrategy;
import org.thingsboard.mqtt.broker.integration.service.processing.backpressure.IntegrationAckStrategyFactory;
import org.thingsboard.mqtt.broker.integration.service.processing.backpressure.IntegrationProcessingDecision;
import org.thingsboard.mqtt.broker.integration.service.processing.backpressure.IntegrationSubmitStrategy;
import org.thingsboard.mqtt.broker.integration.service.processing.backpressure.IntegrationSubmitStrategyFactory;
import org.thingsboard.mqtt.broker.integration.service.processing.callback.BaseIntegrationMsgCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.integration.IntegrationMsgQueueProvider;
import org.thingsboard.mqtt.broker.service.queue.IntegrationTopicService;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static java.lang.Long.MAX_VALUE;

@Service
@Slf4j
@RequiredArgsConstructor
public class IntegrationMsgProcessorImpl implements IntegrationMsgProcessor {

    private final ConcurrentMap<String, IntegrationHolder> integrations = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, IntegrationHolder> eventIntegrations = new ConcurrentHashMap<>();

    private final IntegrationMsgQueueProvider integrationMsgQueueProvider;
    private final IntegrationTopicService integrationTopicService;
    private final IntegrationAckStrategyFactory ackStrategyFactory;
    private final IntegrationSubmitStrategyFactory submitStrategyFactory;
    private final Optional<IntegrationStatisticsService> statsService;

    private volatile boolean stopped = false;
    private ExecutorService integrationMsgsConsumerExecutor;
    private ScheduledExecutorService taskExecutor;

    @Value("${queue.integration-msg.poll-interval:100}")
    private long pollDuration;
    @Value("${queue.integration-msg.pack-processing-timeout:30000}")
    private long packProcessingTimeout;
    @Value("${queue.integration-msg.event-poll-interval:100}")
    private long eventPollDuration;
    @Value("${queue.integration-msg.event-pack-processing-timeout:30000}")
    private long eventPackProcessingTimeout;

    @PostConstruct
    public void init() {
        log.info("Initializing IE msg processor");
        integrationMsgsConsumerExecutor = ThingsBoardExecutors.initCachedExecutorService("ie-msg-consumers");
        taskExecutor = ThingsBoardExecutors.newSingleScheduledThreadPool("ie-msg-task");
    }

    @PreDestroy
    public void destroy() {
        log.info("Destroying IE msg processor");
        stopped = true;
        integrations.forEach((integrationId, integration) -> stopIntegrationCancelTask(integration));
        integrations.clear();
        eventIntegrations.forEach((id, holder) -> stopIntegrationCancelTask(holder));
        eventIntegrations.clear();
        if (integrationMsgsConsumerExecutor != null) {
            ThingsBoardExecutors.shutdownAndAwaitTermination(integrationMsgsConsumerExecutor, "IE msg consumers'");
        }
        if (taskExecutor != null) {
            ThingsBoardExecutors.shutdownAndAwaitTermination(taskExecutor, "IE msg task'");
        }
    }

    @Override
    public void startProcessingIntegrationMessages(TbPlatformIntegration integration) {
        String integrationId = integration.getIntegrationId();
        if (integrations.containsKey(integrationId)) {
            log.info("[{}][{}] The processor is already running. Skipping start processing request", integrationId, integration.getLifecycleMsg().getName());
            return;
        }

        String integrationTopic = integrationTopicService.createTopic(integrationId);
        log.info("[{}] Starting integration messages processing", integrationId);
        TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishIntegrationMsgProto>> consumer = initConsumer(integrationId, integrationTopic);
        IntegrationHolder integrationHolder = new IntegrationHolder(integration);
        Future<?> future = integrationMsgsConsumerExecutor.submit(() -> {
            try {
                processMessages(consumer, integrationHolder);
            } finally {
                consumer.unsubscribeAndClose();
            }
        });
        integrationHolder.setFuture(future);
        integrations.put(integrationId, integrationHolder);
        try {
            startProcessingEventsIfOptedIn(integration);
        } catch (Exception e) {
            log.warn("[{}] Failed to start lifecycle-events processing; continuing with data only", integrationId, e);
        }
    }

    @Override
    public void stopProcessingIntegrationMessages(String integrationId) {
        log.info("[{}] Stopping integration messages processing", integrationId);
        IntegrationHolder integrationHolder = integrations.remove(integrationId);
        if (integrationHolder == null) {
            log.warn("[{}] Cannot find integration for integrationId", integrationId);
        } else {
            try {
                stopIntegrationCancelTask(integrationHolder);
                statsService.ifPresent(svc -> svc.clearIntegrationProcessorStats(integrationHolder.getIntegrationUuid()));
            } catch (Exception e) {
                log.warn("[{}] Exception stopping future for integration", integrationId, e);
            }
        }
        IntegrationHolder eventHolder = eventIntegrations.remove(integrationId);
        if (eventHolder != null) {
            try {
                stopIntegrationCancelTask(eventHolder);
                statsService.ifPresent(svc -> svc.clearIntegrationEventProcessorStats(eventHolder.getIntegrationUuid()));
            } catch (Exception e) {
                log.warn("[{}] Exception stopping events future for integration", integrationId, e);
            }
        }
    }

    @Override
    public void clearIntegrationMessages(String integrationId) {
        log.debug("[{}] Clearing consumer group and topic for integration", integrationId);
        taskExecutor.schedule(() -> {
            try {
                integrationTopicService.deleteTopic(integrationId, CallbackUtil.createCallback(
                        () -> {
                        }, throwable -> {
                        }));
                integrationTopicService.deleteEventTopic(integrationId, CallbackUtil.createCallback(
                        () -> {
                        }, throwable -> {
                        }));
            } catch (Exception e) {
                log.warn("[{}] Exception clearing consumer group and topic for integration", integrationId, e);
            }
        }, 10, TimeUnit.SECONDS);
    }

    private TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishIntegrationMsgProto>> initConsumer(String integrationId, String topic) {
        TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishIntegrationMsgProto>> consumer = createConsumer(integrationId, topic);
        try {
            consumer.assignPartition(0);

            Optional<Long> committedOffset = consumer.getCommittedOffset(consumer.getTopic(), 0);
            if (committedOffset.isEmpty()) {
                long endOffset = consumer.getEndOffset(consumer.getTopic(), 0);
                consumer.commit(0, endOffset);
            }
            return consumer;
        } catch (Exception e) {
            log.error("[{}] Failed to init integration consumer", integrationId, e);
            consumer.unsubscribeAndClose();
            throw e;
        }
    }

    private TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishIntegrationMsgProto>> createConsumer(String integrationId, String topic) {
        return integrationMsgQueueProvider
                .getIeMsgConsumer(
                        topic,
                        integrationTopicService.getConsumerGroup(integrationId),
                        integrationId);
    }

    private void startProcessingEventsIfOptedIn(TbPlatformIntegration integration) {
        String integrationId = integration.getIntegrationId();
        if (!IntegrationEventsOptInUtil.isOptedIn(integration.getLifecycleMsg())) {
            log.debug("[{}] Integration is not opted in for lifecycle events. Skipping events consumer start", integrationId);
            return;
        }
        if (eventIntegrations.containsKey(integrationId)) {
            log.info("[{}] The events processor is already running. Skipping start", integrationId);
            return;
        }
        String eventTopic = integrationTopicService.createEventTopic(integrationId);
        log.info("[{}] Starting integration lifecycle-events processing", integrationId);
        TbQueueControlledOffsetConsumer<TbProtoQueueMsg<ClientLifecycleEventMsgProto>> consumer =
                initEventConsumer(integrationId, eventTopic);
        IntegrationHolder holder = new IntegrationHolder(integration);
        Future<?> future = integrationMsgsConsumerExecutor.submit(() -> {
            try {
                processEvents(consumer, holder);
            } finally {
                consumer.unsubscribeAndClose();
            }
        });
        holder.setFuture(future);
        eventIntegrations.put(integrationId, holder);
    }

    private TbQueueControlledOffsetConsumer<TbProtoQueueMsg<ClientLifecycleEventMsgProto>> initEventConsumer(String integrationId, String topic) {
        TbQueueControlledOffsetConsumer<TbProtoQueueMsg<ClientLifecycleEventMsgProto>> consumer =
                integrationMsgQueueProvider.getIeEventMsgConsumer(
                        topic, integrationTopicService.getEventConsumerGroup(integrationId), integrationId);
        try {
            consumer.assignPartition(0);
            Optional<Long> committedOffset = consumer.getCommittedOffset(consumer.getTopic(), 0);
            if (committedOffset.isEmpty()) {
                long endOffset = consumer.getEndOffset(consumer.getTopic(), 0);
                consumer.commit(0, endOffset);
            }
            return consumer;
        } catch (Exception e) {
            log.error("[{}] Failed to init integration events consumer", integrationId, e);
            consumer.unsubscribeAndClose();
            throw e;
        }
    }

    private void processEvents(TbQueueControlledOffsetConsumer<TbProtoQueueMsg<ClientLifecycleEventMsgProto>> consumer,
                               IntegrationHolder holder) {
        IntegrationProcessorStats stats = statsService
                .map(svc -> svc.createIntegrationEventProcessorStats(holder.getIntegrationUuid()))
                .orElse(null);
        processQueue(consumer, holder, this::dispatchEvent, stats, "events",
                eventPollDuration, eventPackProcessingTimeout,
                () -> ackStrategyFactory.newEventInstance(holder.getIntegrationId()));
    }

    void dispatchEvent(IntegrationHolder holder, UUID packetId, ClientLifecycleEventMsgProto event,
                       IntegrationPackProcessingContext<ClientLifecycleEventMsgProto> ctx) {
        holder.getIntegration().processLifecycleEvent(event, new BaseIntegrationMsgCallback(packetId, ctx));
    }

    private void processMessages(TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishIntegrationMsgProto>> consumer,
                                 IntegrationHolder holder) {
        IntegrationProcessorStats stats = statsService
                .map(svc -> svc.createIntegrationProcessorStats(holder.getIntegrationUuid()))
                .orElse(null);
        processQueue(consumer, holder, this::dispatchMessage, stats, "messages",
                pollDuration, packProcessingTimeout,
                () -> ackStrategyFactory.newInstance(holder.getIntegrationId()));
    }

    void dispatchMessage(IntegrationHolder holder, UUID packetId, PublishIntegrationMsgProto msg,
                         IntegrationPackProcessingContext<PublishIntegrationMsgProto> ctx) {
        holder.getIntegration().process(msg, new BaseIntegrationMsgCallback(packetId, ctx));
    }

    /**
     * Single consume/ack/commit loop shared by the data ({@link PublishIntegrationMsgProto}) and lifecycle-event
     * ({@link ClientLifecycleEventMsgProto}) streams. Parameterized by the proto type, the per-message dispatcher,
     * an optional stats sink (data stream only), a {@code kind} label used purely for logging, and the per-stream
     * poll interval, pack-processing timeout, and ack-strategy supplier (data and events are configured
     * independently under {@code queue.integration-msg} / {@code queue.integration-msg.event-*}).
     */
    private <T extends GeneratedMessageV3> void processQueue(TbQueueControlledOffsetConsumer<TbProtoQueueMsg<T>> consumer,
                                  IntegrationHolder holder,
                                  IntegrationDispatcher<T> dispatcher,
                                  IntegrationProcessorStats stats,
                                  String kind,
                                  long pollDuration,
                                  long packProcessingTimeout,
                                  Supplier<IntegrationAckStrategy<T>> ackStrategyProvider) {
        final AtomicLong counter = new AtomicLong(0);
        while (isProcessorActive(holder)) {
            try {
                List<TbProtoQueueMsg<T>> messages = consumer.poll(pollDuration);
                if (messages.isEmpty()) {
                    continue;
                }

                IntegrationAckStrategy<T> ackStrategy = ackStrategyProvider.get();
                IntegrationSubmitStrategy<T> submitStrategy = submitStrategyFactory.newInstance(holder.getIntegrationId());

                long packId = counter.incrementAndGet();
                if (packId == MAX_VALUE) {
                    counter.set(0);
                }

                var pendingMsgMap = toPendingMap(messages, packId);
                submitStrategy.init(pendingMsgMap);

                while (isProcessorActive(holder)) {
                    IntegrationPackProcessingContext<T> ctx =
                            new IntegrationPackProcessingContext<>(holder.getIntegrationId(), submitStrategy.getPendingMap());
                    int totalMsgCount = pendingMsgMap.size();

                    submitStrategy.process(entry -> dispatcher.dispatch(holder, entry.getKey(), entry.getValue(), ctx));

                    if (isProcessorActive(holder)) {
                        ctx.await(packProcessingTimeout, TimeUnit.MILLISECONDS);
                    }
                    IntegrationPackProcessingResult<T> result = new IntegrationPackProcessingResult<>(ctx);
                    ctx.cleanup();
                    IntegrationProcessingDecision<T> decision = ackStrategy.analyze(result);

                    if (stats != null) {
                        stats.log(totalMsgCount, result, decision.isCommit());
                    }

                    if (decision.isCommit()) {
                        consumer.commitSync();
                        break;
                    } else {
                        submitStrategy.update(decision.getReprocessMap());
                    }
                }
            } catch (Exception e) {
                if (isProcessorActive(holder)) {
                    log.warn("[{}] Failed to process {} from queue.", holder.getIntegrationId(), kind, e);
                    try {
                        Thread.sleep(pollDuration);
                    } catch (InterruptedException e2) {
                        log.trace("Failed to wait until the server has capacity to handle new {}", kind, e2);
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        log.info("[{}] IE {} consumer stopped.", holder.getIntegrationId(), kind);
    }

    private boolean isProcessorActive(IntegrationHolder integrationHolder) {
        return !stopped && !Thread.interrupted() && !integrationHolder.isStopped();
    }

    private void stopIntegrationCancelTask(IntegrationHolder integration) {
        integration.getFuture().cancel(false);
        integration.setStopped(true);
    }

    private <T extends GeneratedMessageV3> Map<UUID, T> toPendingMap(List<TbProtoQueueMsg<T>> msgs, long packId) {
        Map<UUID, T> map = Maps.newLinkedHashMapWithExpectedSize(msgs.size());
        int i = 0;
        for (var msg : msgs) {
            map.put(new UUID(packId, i++), msg.getValue());
        }
        return map;
    }

    @FunctionalInterface
    private interface IntegrationDispatcher<T> {
        void dispatch(IntegrationHolder holder, UUID packetId, T msg, IntegrationPackProcessingContext<T> ctx);
    }
}
