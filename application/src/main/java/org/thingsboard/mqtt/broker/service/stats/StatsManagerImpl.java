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
package org.thingsboard.mqtt.broker.service.stats;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.actors.ActorStatsManager;
import org.thingsboard.mqtt.broker.common.stats.MessagesStats;
import org.thingsboard.mqtt.broker.common.stats.MessagesStatsFormatter;
import org.thingsboard.mqtt.broker.common.stats.ResettableTimer;
import org.thingsboard.mqtt.broker.common.stats.StatsConstantNames;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsType;
import org.thingsboard.mqtt.broker.dao.sql.SqlQueueStatsManager;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.queue.stats.ConsumerStatsManager;
import org.thingsboard.mqtt.broker.queue.stats.ProducerStatsManager;
import org.thingsboard.mqtt.broker.queue.stats.Timer;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.data.ApplicationSharedSubscriptionJob;
import org.thingsboard.mqtt.broker.service.stats.timer.DeliveryTimerStats;
import org.thingsboard.mqtt.broker.service.stats.timer.PublishMsgProcessingTimerStats;
import org.thingsboard.mqtt.broker.service.stats.timer.RetainedMsgTimerStats;
import org.thingsboard.mqtt.broker.service.stats.timer.SubscriptionTimerStats;
import org.thingsboard.mqtt.broker.service.stats.timer.TimerStats;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "stats", value = "enabled", havingValue = "true")
public class StatsManagerImpl implements StatsManager, ActorStatsManager, SqlQueueStatsManager, ProducerStatsManager, ConsumerStatsManager {

    private final List<MessagesStats> managedStats = new CopyOnWriteArrayList<>();
    private final List<Gauge> gauges = new CopyOnWriteArrayList<>();

    private final List<PublishMsgConsumerStats> managedPublishMsgConsumerStats = new CopyOnWriteArrayList<>();
    private final List<ClientSessionEventConsumerStats> managedClientSessionEventConsumerStats = new CopyOnWriteArrayList<>();
    private final List<DeviceProcessorStats> managedDeviceProcessorStats = new CopyOnWriteArrayList<>();
    private final Map<String, ApplicationProcessorStats> managedApplicationProcessorStats = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> sharedSubscriptionCompoundClientIds = new ConcurrentHashMap<>();
    private final Map<String, ResettableTimer> managedQueueProducers = new ConcurrentHashMap<>();
    private final Map<String, ResettableTimer> managedQueueConsumers = new ConcurrentHashMap<>();
    private final StatsFactory statsFactory;

    private ClientSubscriptionConsumerStats managedClientSubscriptionConsumerStats;
    private RetainedMsgConsumerStats retainedMsgConsumerStats;
    private ActorStats clientActorStats;
    private ActorStats persistedDeviceActorStats;
    private FlowControlStats flowControlStats;
    private DroppedMsgStats droppedMsgStats;
    private DroppedLifecycleEventStats droppedLifecycleEventStats;
    private ClientDisconnectStats clientDisconnectStats;

    @Value("${stats.application-processor.enabled}")
    private boolean applicationProcessorStatsEnabled;

    private TimerStats timerStats;

    @PostConstruct
    public void init() {
        this.timerStats = new TimerStats(statsFactory);
        this.managedClientSubscriptionConsumerStats = new DefaultClientSubscriptionConsumerStats(statsFactory);
        this.retainedMsgConsumerStats = new DefaultRetainedMsgConsumerStats(statsFactory);
        this.clientActorStats = new DefaultActorStats(statsFactory, StatsType.CLIENT_ACTOR);
        this.persistedDeviceActorStats = new DefaultActorStats(statsFactory, StatsType.PERSISTED_DEVICE_ACTOR);
        DefaultFlowControlStats defaultFlowControlStats = new DefaultFlowControlStats(statsFactory);
        this.flowControlStats = defaultFlowControlStats;
        gauges.add(new Gauge(StatsType.FLOW_CONTROL.getPrintName() + ".inflightCount", defaultFlowControlStats::getInflightCount));
        gauges.add(new Gauge(StatsType.FLOW_CONTROL.getPrintName() + ".delayedQueueSize", defaultFlowControlStats::getDelayedQueueSize));
        this.droppedMsgStats = new DefaultDroppedMsgStats(statsFactory);
        this.droppedLifecycleEventStats = new DefaultDroppedLifecycleEventStats(statsFactory);
        this.clientDisconnectStats = new DefaultClientDisconnectStats(statsFactory);
    }

    @PreDestroy
    public void destroy() {
        log.info("Print Stats before exit");
        printStats();
    }

    @Override
    public TbQueueCallback wrapTbQueueCallback(TbQueueCallback queueCallback, MessagesStats stats) {
        return new StatsQueueCallback(queueCallback, stats);
    }

    @Override
    public MessagesStats createMsgDispatcherPublishStats() {
        log.trace("Creating MsgDispatcherPublishStats");
        MessagesStats stats = statsFactory.createMessagesStats(StatsType.MSG_DISPATCHER_PRODUCER.getPrintName());
        managedStats.add(stats);
        return stats;
    }

    @Override
    public DroppedMsgStats getDroppedMsgStats() {
        return droppedMsgStats;
    }

    @Override
    public DroppedLifecycleEventStats getDroppedLifecycleEventStats() {
        return droppedLifecycleEventStats;
    }

    @Override
    public ClientDisconnectStats getClientDisconnectStats() {
        return clientDisconnectStats;
    }

    @Override
    public ClientSessionEventConsumerStats createClientSessionEventConsumerStats(String consumerId) {
        log.trace("Creating ClientSessionEventConsumerStats, consumerId - {}", consumerId);
        ClientSessionEventConsumerStats stats = new DefaultClientSessionEventConsumerStats(consumerId, statsFactory);
        managedClientSessionEventConsumerStats.add(stats);
        return stats;
    }

    @Override
    public PublishMsgConsumerStats createPublishMsgConsumerStats(String consumerId) {
        log.trace("Creating PublishMsgConsumerStats, consumerId - {}", consumerId);
        PublishMsgConsumerStats stats = new DefaultPublishMsgConsumerStats(consumerId, statsFactory);
        managedPublishMsgConsumerStats.add(stats);
        return stats;
    }

    @Override
    public DeviceProcessorStats createDeviceProcessorStats(String consumerId) {
        log.trace("Creating DeviceProcessorStats, consumerId - {}", consumerId);
        DeviceProcessorStats stats = new DefaultDeviceProcessorStats(consumerId, statsFactory);
        managedDeviceProcessorStats.add(stats);
        return stats;
    }

    @Override
    public ApplicationProcessorStats createApplicationProcessorStats(String clientId) {
        log.trace("Creating ApplicationProcessorStats, clientId - {}", clientId);
        if (applicationProcessorStatsEnabled) {
            ApplicationProcessorStats stats = new DefaultApplicationProcessorStats(clientId, statsFactory);
            managedApplicationProcessorStats.put(clientId, stats);
            return stats;
        } else {
            return StubApplicationProcessorStats.STUB_APPLICATION_PROCESSOR_STATS;
        }
    }

    @Override
    public ApplicationProcessorStats createSharedApplicationProcessorStats(String clientId, TopicSharedSubscription subscription) {
        log.trace("Creating SharedApplicationProcessorStats, clientId - {}", clientId);
        if (applicationProcessorStatsEnabled) {
            var compoundClientId = getCompoundClientId(clientId, subscription);

            ApplicationProcessorStats stats = new DefaultApplicationProcessorStats(compoundClientId, statsFactory);
            managedApplicationProcessorStats.put(compoundClientId, stats);

            Set<String> clientIds = sharedSubscriptionCompoundClientIds.computeIfAbsent(clientId, s -> ConcurrentHashMap.newKeySet());
            clientIds.add(compoundClientId);

            return stats;
        } else {
            return StubApplicationProcessorStats.STUB_APPLICATION_PROCESSOR_STATS;
        }
    }

    private String getCompoundClientId(String clientId, TopicSharedSubscription subscription) {
        return clientId + "_" + subscription.getShareName() + "_" + subscription.getTopicFilter();
    }

    @Override
    public ClientSubscriptionConsumerStats getClientSubscriptionConsumerStats() {
        return managedClientSubscriptionConsumerStats;
    }

    @Override
    public RetainedMsgConsumerStats getRetainedMsgConsumerStats() {
        return retainedMsgConsumerStats;
    }

    @Override
    public void clearApplicationProcessorStats(String clientId) {
        log.trace("Clearing ApplicationProcessorStats, clientId - {}", clientId);
        printAndRemoveApplicationStatsOnClear(managedApplicationProcessorStats.remove(clientId));
    }

    @Override
    public void clearSharedApplicationProcessorStats(String clientId) {
        log.trace("Clearing SharedApplicationProcessorStats, clientId - {}", clientId);
        Set<String> clientIds = sharedSubscriptionCompoundClientIds.remove(clientId);
        if (CollectionUtils.isEmpty(clientIds)) {
            return;
        }
        for (String compoundClientId : clientIds) {
            printAndRemoveApplicationStatsOnClear(managedApplicationProcessorStats.remove(compoundClientId));
        }
    }

    @Override
    public void clearSharedApplicationProcessorStats(String clientId, TopicSharedSubscription subscription) {
        log.trace("Clearing SharedApplicationProcessorStats, clientId - {}, subscription - {}", clientId, subscription);

        var compoundClientId = getCompoundClientId(clientId, subscription);

        Set<String> clientIds = sharedSubscriptionCompoundClientIds.get(clientId);
        if (CollectionUtils.isEmpty(clientIds)) {
            return;
        }
        clientIds.remove(compoundClientId);

        printAndRemoveApplicationStatsOnClear(managedApplicationProcessorStats.remove(compoundClientId));
        if (clientIds.isEmpty()) {
            sharedSubscriptionCompoundClientIds.remove(clientId);
        }
    }

    private void printAndRemoveApplicationStatsOnClear(ApplicationProcessorStats stats) {
        if (stats != null) {
            log.info("[{}][{}] Stats on clear", StatsType.APP_PROCESSOR.getPrintName(), stats.getClientId());
            printApplicationProcessorStats(stats);
            // Deregister the per-client counters so they stop being scraped and don't leak the
            // Micrometer registry on client/shared-subscription churn. The appProcessor.latency
            // timers carry no clientId tag (one shared set across all clients), so they are
            // intentionally left registered.
            stats.getStatsCounters().forEach(statsFactory::remove);
        }
    }

    @Override
    public AtomicInteger createNonWritableClientsGauge() {
        log.trace("Creating NonWritableClientsGauge");
        AtomicInteger sizeGauge = statsFactory.createGauge(StatsType.NON_WRITABLE_CLIENTS.getPrintName(), new AtomicInteger(0));
        gauges.add(new Gauge(StatsType.NON_WRITABLE_CLIENTS.getPrintName(), sizeGauge::get));
        return sizeGauge;
    }

    @Override
    public AtomicInteger createSubscriptionSizeGauge() {
        log.trace("Creating SubscriptionSizeGauge");
        AtomicInteger sizeGauge = statsFactory.createGauge(StatsType.SUBSCRIPTION_TOPIC_TRIE_SIZE.getPrintName(), new AtomicInteger(0));
        gauges.add(new Gauge(StatsType.SUBSCRIPTION_TOPIC_TRIE_SIZE.getPrintName(), sizeGauge::get));
        return sizeGauge;
    }

    @Override
    public AtomicInteger createRetainMsgSizeGauge() {
        log.trace("Creating RetainMsgSizeGauge");
        AtomicInteger sizeGauge = statsFactory.createGauge(StatsType.RETAIN_MSG_TRIE_SIZE.getPrintName(), new AtomicInteger(0));
        gauges.add(new Gauge(StatsType.RETAIN_MSG_TRIE_SIZE.getPrintName(), sizeGauge::get));
        return sizeGauge;
    }

    @Override
    public void registerLastWillStats(Map<?, ?> lastWillMsgsMap) {
        log.trace("Registering LastWillStats");
        statsFactory.createGauge(StatsType.LAST_WILL_CLIENTS.getPrintName(), lastWillMsgsMap, Map::size);
        gauges.add(new Gauge(StatsType.LAST_WILL_CLIENTS.getPrintName(), lastWillMsgsMap::size));
    }

    @Override
    public void registerActiveSessionsStats(Map<?, ?> sessionsMap) {
        log.trace("Registering ActiveSessionsStats");
        statsFactory.createGauge(StatsType.CONNECTED_SESSIONS.getPrintName(), sessionsMap, Map::size);
        gauges.add(new Gauge(StatsType.CONNECTED_SESSIONS.getPrintName(), sessionsMap::size));
    }

    @Override
    public AtomicLong registerActiveSslSessionsStats() {
        log.trace("Creating ActiveSslSessionsStats");
        AtomicLong sizeGauge = statsFactory.createGauge(StatsType.CONNECTED_SSL_SESSIONS.getPrintName(), new AtomicLong(0));
        gauges.add(new Gauge(StatsType.CONNECTED_SSL_SESSIONS.getPrintName(), sizeGauge::get));
        return sizeGauge;
    }

    @Override
    public void registerAllClientSessionsStats(Map<?, ?> clientSessionsMap) {
        log.trace("Registering AllClientSessionsStats");
        statsFactory.createGauge(StatsType.ALL_CLIENT_SESSIONS.getPrintName(), clientSessionsMap, Map::size);
        gauges.add(new Gauge(StatsType.ALL_CLIENT_SESSIONS.getPrintName(), clientSessionsMap::size));
    }

    @Override
    public void registerSubscriptionsStats(LongAdder subscriptionCount) {
        log.trace("Registering SubscriptionsStats");
        // Total subscription count across all clients — maintained incrementally by ClientSubscriptionService
        // (see #getClientSubscriptionsCount) so this reads O(1) instead of summing every client's set per scrape.
        // NOT the number of clients that have subscriptions.
        statsFactory.createGauge(StatsType.SUBSCRIPTIONS.getPrintName(), subscriptionCount, LongAdder::sum);
        gauges.add(new Gauge(StatsType.SUBSCRIPTIONS.getPrintName(), subscriptionCount::sum));
    }

    @Override
    public void registerRetainedMsgStats(Map<?, ?> retainedMessagesMap) {
        log.trace("Registering RetainedMsgStats");
        statsFactory.createGauge(StatsType.RETAINED_MESSAGES.getPrintName(), retainedMessagesMap, Map::size);
        gauges.add(new Gauge(StatsType.RETAINED_MESSAGES.getPrintName(), retainedMessagesMap::size));
    }

    @Override
    public void registerActiveApplicationProcessorsStats(Map<?, ?> processingFuturesMap) {
        log.trace("Registering ActiveApplicationProcessorsStats");
        statsFactory.createGauge(StatsType.ACTIVE_APP_PROCESSORS.getPrintName(), processingFuturesMap, Map::size);
        gauges.add(new Gauge(StatsType.ACTIVE_APP_PROCESSORS.getPrintName(), processingFuturesMap::size));
    }

    @Override
    public void registerActiveSharedApplicationProcessorsStats(Map<String, List<ApplicationSharedSubscriptionJob>> processingFuturesMap) {
        log.trace("Registering ActiveSharedApplicationProcessorsStats");
        statsFactory.createGauge(StatsType.ACTIVE_SHARED_APP_PROCESSORS.getPrintName(), processingFuturesMap, this::getSum);
        gauges.add(new Gauge(StatsType.ACTIVE_SHARED_APP_PROCESSORS.getPrintName(), () -> getSum(processingFuturesMap)));
    }

    private int getSum(Map<String, List<ApplicationSharedSubscriptionJob>> processingFuturesMap) {
        return processingFuturesMap.values().stream().mapToInt(List::size).sum();
    }

    @Override
    public void registerActorsStats(Map<?, ?> actorsMap) {
        log.trace("Registering ActorsStats");
        statsFactory.createGauge(StatsType.RUNNING_ACTORS.getPrintName(), actorsMap, Map::size);
        gauges.add(new Gauge(StatsType.RUNNING_ACTORS.getPrintName(), actorsMap::size));
    }

    @Override
    public AtomicLong createSubscriptionTrieNodesGauge() {
        log.trace("Creating SubscriptionTrieNodesGauge");
        AtomicLong sizeGauge = statsFactory.createGauge(StatsType.SUBSCRIPTION_TRIE_NODES.getPrintName(), new AtomicLong(0));
        gauges.add(new Gauge(StatsType.SUBSCRIPTION_TRIE_NODES.getPrintName(), sizeGauge::get));
        return sizeGauge;
    }

    @Override
    public AtomicLong createRetainMsgTrieNodesGauge() {
        log.trace("Creating RetainMsgTrieNodesGauge");
        AtomicLong sizeGauge = statsFactory.createGauge(StatsType.RETAIN_MSG_TRIE_NODES.getPrintName(), new AtomicLong(0));
        gauges.add(new Gauge(StatsType.RETAIN_MSG_TRIE_NODES.getPrintName(), sizeGauge::get));
        return sizeGauge;
    }

    @Override
    public MessagesStats createSqlQueueStats(String queueName, int queueIndex) {
        log.trace("Creating SqlQueueStats, queueName - {}, queueIndex - {}", queueName, queueIndex);
        String statsKey = StatsType.SQL_QUEUE.getPrintName();
        // Carry the queue name as a `queueName` tag on a single `sqlQueue` metric rather than baking it into
        // the metric name (`sqlQueue.<queueName>`); a stable name with a bounded tag is the Prometheus-idiomatic
        // shape and lets consumers aggregate across queues. The counters and the queueSize gauge below must
        // share the same tag set so consumers can correlate throughput with depth per queue, so build it once.
        String[] tags = {"queueName", queueName, "queueIndex", String.valueOf(queueIndex)};
        MessagesStats stats = statsFactory.createMessagesStats(statsKey, tags);
        managedStats.add(stats);
        // Export the live SQL queue depth as a Micrometer gauge (backpressure/durability signal that was
        // previously computed and logged but never scraped). The queue::size supplier is wired later by
        // TbSqlBlockingQueue#init, so getCurrentQueueSize() reports 0 until then. Micrometer holds only a
        // weak reference to the gauge's state object, but the stats instance is strong-held by managedStats
        // above for the process lifetime, so it will not be GC'd (which would make the gauge report NaN).
        statsFactory.createGauge(statsKey + "." + StatsConstantNames.QUEUE_SIZE, stats,
                MessagesStats::getCurrentQueueSize, tags);
        return stats;
    }

    @Override
    public Timer createSendTimer(String clientId) {
        ResettableTimer timer = new ResettableTimer(statsFactory.createTimer(StatsType.QUEUE_PRODUCER.getPrintName(), "producerId", clientId));
        managedQueueProducers.put(clientId, timer);
        return timer::logTime;
    }

    @Override
    public Timer createCommitTimer(String clientId) {
        ResettableTimer timer = new ResettableTimer(statsFactory.createTimer(StatsType.QUEUE_CONSUMER.getPrintName(),
                "consumerId", clientId));
        managedQueueConsumers.put(clientId, timer);
        return timer::logTime;
    }

    @Override
    public SubscriptionTimerStats getSubscriptionTimerStats() {
        return timerStats;
    }

    @Override
    public RetainedMsgTimerStats getRetainedMsgTimerStats() {
        return timerStats;
    }

    @Override
    public PublishMsgProcessingTimerStats getPublishMsgProcessingTimerStats() {
        return timerStats;
    }

    @Override
    public DeliveryTimerStats getDeliveryTimerStats() {
        return timerStats;
    }

    @Override
    public ActorStats getClientActorStats() {
        return clientActorStats;
    }

    @Override
    public ActorStats getPersistedDeviceActorStats() {
        return persistedDeviceActorStats;
    }

    @Override
    public FlowControlStats getFlowControlStats() {
        return flowControlStats;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Scheduled(fixedDelayString = "${stats.print-interval-ms}")
    public void printStats() {
        log.info("----------------------------------------------------------------");
        for (MessagesStats stats : managedStats) {
            log.info("[{}] Stats: {}", stats.getName(), MessagesStatsFormatter.format(stats));
            stats.reset();
        }

        for (PublishMsgConsumerStats stats : managedPublishMsgConsumerStats) {
            String countersStats = stats.getStatsCounters().stream()
                    .map(statsCounter -> statsCounter.getName() + " = [" + statsCounter.get() + "]")
                    .collect(Collectors.joining(" "));
            log.info("[{}][{}] Average pack size - {}, pack processing time - {}, msg processing time - {} ms, counters stats: {}", StatsType.PUBLISH_MSG_CONSUMER.getPrintName(), stats.getConsumerId(),
                    stats.getAvgPackSize(), stats.getAvgPackProcessingTime(), stats.getAvgMsgProcessingTime(), countersStats);
            stats.reset();
        }

        for (ClientSessionEventConsumerStats stats : managedClientSessionEventConsumerStats) {
            log.info("[{}][{}] Average pack size - {}, pack processing time - {}", StatsType.CLIENT_SESSION_EVENT_CONSUMER.getPrintName(), stats.getConsumerId(),
                    stats.getAvgPackSize(), stats.getAvgPackProcessingTime());
            stats.reset();
        }

        for (DeviceProcessorStats stats : managedDeviceProcessorStats) {
            String statsStr = stats.getStatsCounters().stream()
                    .map(statsCounter -> statsCounter.getName() + " = [" + statsCounter.get() + "]")
                    .collect(Collectors.joining(" "));
            log.info("[{}][{}] Average pack size - {}, pack processing time - {}, client msgs processing time - {} ms, counters stats: {}", StatsType.DEVICE_PROCESSOR.getPrintName(), stats.getConsumerId(),
                    stats.getAvgPackSize(), stats.getAvgPackProcessingTime(), stats.getAvgClientIdMsgPackProcessingTime(), statsStr);
            stats.reset();
        }

        if (applicationProcessorStatsEnabled) {
            for (ApplicationProcessorStats stats : new ArrayList<>(managedApplicationProcessorStats.values())) {
                printApplicationProcessorStats(stats);
                stats.reset();
            }
        }

        String statsStr = managedClientSubscriptionConsumerStats.getStatsCounters().stream()
                .map(statsCounter -> statsCounter.getName() + " = [" + statsCounter.get() + "]")
                .collect(Collectors.joining(" "));
        log.info("[{}] Stats: {}", StatsType.CLIENT_SUBSCRIPTIONS_CONSUMER.getPrintName(), statsStr);
        managedClientSubscriptionConsumerStats.reset();

        String retainedMsgStatsStr = retainedMsgConsumerStats.getStatsCounters().stream()
                .map(statsCounter -> statsCounter.getName() + " = [" + statsCounter.get() + "]")
                .collect(Collectors.joining(" "));
        log.info("[{}] Stats: {}", StatsType.RETAINED_MSG_CONSUMER.getPrintName(), retainedMsgStatsStr);
        retainedMsgConsumerStats.reset();

        String flowControlStatsStr = flowControlStats.getStatsCounters().stream()
                .map(statsCounter -> statsCounter.getName() + " = [" + statsCounter.get() + "]")
                .collect(Collectors.joining(" "));
        log.info("[{}] Stats: {}", StatsType.FLOW_CONTROL.getPrintName(), flowControlStatsStr);
        flowControlStats.reset();

        log.info("[{}] Stats: count = [{}]", StatsType.DROPPED_MSGS.getPrintName(), droppedMsgStats.getCount());
        droppedMsgStats.reset();

        log.info("[{}] Stats: count = [{}]", StatsType.DROPPED_LIFECYCLE_EVENTS.getPrintName(), droppedLifecycleEventStats.getCount());
        droppedLifecycleEventStats.reset();

        log.info("[{}] Stats: count = [{}]", StatsType.CLIENT_DISCONNECTS.getPrintName(), clientDisconnectStats.getCount());
        clientDisconnectStats.reset();

        StringBuilder gaugeLogBuilder = new StringBuilder();
        for (Gauge gauge : gauges) {
            gaugeLogBuilder.append(gauge.getName()).append(" = [").append(gauge.getValueSupplier().get().intValue()).append("] ");
        }
        log.info("Gauges Stats: {}", gaugeLogBuilder);

        printActorStats(clientActorStats, "Client Actor");
        printActorStats(persistedDeviceActorStats, "Device Actor");

        StringBuilder timerLogBuilder = new StringBuilder();
        for (ResettableTimer resettableTimer : timerStats.getTimers()) {
            timerLogBuilder.append(resettableTimer.getTimer().getId().getName()).append(" = [").append(resettableTimer.getCount()).append(" | ")
                    .append(resettableTimer.getAvg()).append("] ");
            resettableTimer.reset();
        }
        log.info("Timer Average Stats: {}", timerLogBuilder);

        StringBuilder queueProducerLogBuilder = new StringBuilder();
        managedQueueProducers.forEach((producerId, timer) -> {
            queueProducerLogBuilder.append(producerId).append(" = [").append(timer.getCount()).append(" | ")
                    .append(timer.getAvg()).append("] ");
            timer.reset();
        });
        log.info("Queue Producer Send Time Average Stats: {}", queueProducerLogBuilder);

        StringBuilder queueConsumerLogBuilder = new StringBuilder();
        managedQueueConsumers.forEach((consumerId, timer) -> {
            queueConsumerLogBuilder.append(consumerId).append(" = [").append(timer.getCount()).append(" | ")
                    .append(timer.getAvg()).append("] ");
            timer.reset();
        });
        log.info("Queue Consumer Commit Time Average Stats: {}", queueConsumerLogBuilder);
    }

    private void printActorStats(ActorStats stats, String label) {
        StringBuilder sb = new StringBuilder();
        sb.append("msgInQueueTime").append(" = [").append(stats.getMsgCount()).append(" | ")
                .append(stats.getQueueTimeAvg()).append(" | ")
                .append(stats.getQueueTimeMax()).append("] ");
        stats.getTimers().forEach((msgType, timer) ->
                sb.append(msgType).append(" = [").append(timer.getCount()).append(" | ")
                        .append(timer.getAvg()).append("] "));
        stats.reset();
        log.info("{} Average Stats: {}", label, sb);
    }

    private void printApplicationProcessorStats(ApplicationProcessorStats stats) {
        String msgStatsStr = stats.getStatsCounters().stream()
                .map(statsCounter -> statsCounter.getName() + " = [" + statsCounter.get() + "]")
                .collect(Collectors.joining(" "));
        String latencyStatsStr = stats.getLatencyTimers().entrySet().stream()
                .map(entry -> entry.getKey() + " = [" + entry.getValue().getCount() + "|" + entry.getValue().getAvg() + "|" + entry.getValue().getMax() + "]")
                .collect(Collectors.joining(" "));
        log.info("[{}][{}] Latency Stats: {}, Processing Stats: {}", StatsType.APP_PROCESSOR.getPrintName(), stats.getClientId(), latencyStatsStr, msgStatsStr);
    }

    @AllArgsConstructor
    @Getter
    private static class Gauge {
        private final String name;
        private final Supplier<Number> valueSupplier;
    }

    @AllArgsConstructor
    private static class StatsQueueCallback implements TbQueueCallback {
        private final TbQueueCallback callback;
        private final MessagesStats stats;

        @Override
        public void onSuccess(TbQueueMsgMetadata metadata) {
            stats.incrementSuccessful();
            if (callback != null) {
                callback.onSuccess(metadata);
            }
        }

        @Override
        public void onFailure(Throwable t) {
            stats.incrementFailed();
            if (callback != null) {
                callback.onFailure(t);
            }
        }
    }
}
