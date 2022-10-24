/**
 * Copyright © 2016-2022 The Thingsboard Authors
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

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.actors.ActorStatsManager;
import org.thingsboard.mqtt.broker.common.stats.MessagesStats;
import org.thingsboard.mqtt.broker.common.stats.ResettableTimer;
import org.thingsboard.mqtt.broker.common.stats.StatsConstantNames;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;
import org.thingsboard.mqtt.broker.dao.sql.SqlQueueStatsManager;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.queue.stats.ConsumerStatsManager;
import org.thingsboard.mqtt.broker.queue.stats.ProducerStatsManager;
import org.thingsboard.mqtt.broker.queue.stats.Timer;
import org.thingsboard.mqtt.broker.service.stats.timer.DeliveryTimerStats;
import org.thingsboard.mqtt.broker.service.stats.timer.PublishMsgProcessingTimerStats;
import org.thingsboard.mqtt.broker.service.stats.timer.RetainedMsgTimerStats;
import org.thingsboard.mqtt.broker.service.stats.timer.SubscriptionTimerStats;
import org.thingsboard.mqtt.broker.service.stats.timer.TimerStats;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Service
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "stats", value = "enabled", havingValue = "true")
public class StatsManagerImpl implements StatsManager, ActorStatsManager, SqlQueueStatsManager, ProducerStatsManager, ConsumerStatsManager {
    private final List<MessagesStats> managedStats = new CopyOnWriteArrayList<>();
    private final List<Gauge> gauges = new CopyOnWriteArrayList<>();

    private final List<PublishMsgConsumerStats> managedPublishMsgConsumerStats = new CopyOnWriteArrayList<>();
    private final List<ClientSessionEventConsumerStats> managedClientSessionEventConsumerStats = new CopyOnWriteArrayList<>();
    private final List<DeviceProcessorStats> managedDeviceProcessorStats = new CopyOnWriteArrayList<>();
    private final Map<String, ApplicationProcessorStats> managedApplicationProcessorStats = new ConcurrentHashMap<>();
    private final Map<String, List<String>> sharedSubscriptionCompoundClientIds = new ConcurrentHashMap<>();
    private final Map<String, ResettableTimer> managedQueueProducers = new ConcurrentHashMap<>();
    private final Map<String, ResettableTimer> managedQueueConsumers = new ConcurrentHashMap<>();
    private final List<Gauge> managedProducerQueues = new CopyOnWriteArrayList<>();
    private ClientSubscriptionConsumerStats managedClientSubscriptionConsumerStats;
    private RetainedMsgConsumerStats retainedMsgConsumerStats;
    private ClientActorStats clientActorStats;

    @Value("${stats.application-processor.enabled}")
    private Boolean applicationProcessorStatsEnabled;

    private final StatsFactory statsFactory;
    private TimerStats timerStats;

    @PostConstruct
    public void init() {
        this.timerStats = new TimerStats(statsFactory);
        this.managedClientSubscriptionConsumerStats = new DefaultClientSubscriptionConsumerStats(statsFactory);
        this.retainedMsgConsumerStats = new DefaultRetainedMsgConsumerStats(statsFactory);
        this.clientActorStats = new DefaultClientActorStats(statsFactory);
    }

    @Override
    public TbQueueCallback wrapTbQueueCallback(TbQueueCallback queueCallback, MessagesStats stats) {
        return new StatsQueueCallback(queueCallback, stats);
    }

    @Override
    public MessagesStats createMsgDispatcherPublishStats() {
        log.trace("Creating MsgDispatcherPublishStats.");
        MessagesStats stats = statsFactory.createMessagesStats(StatsType.MSG_DISPATCHER_PRODUCER.getPrintName());
        managedStats.add(stats);
        return stats;
    }

    @Override
    public ClientSessionEventConsumerStats createClientSessionEventConsumerStats(String consumerId) {
        log.trace("Creating ClientSessionEventConsumerStats, consumerId - {}.", consumerId);
        ClientSessionEventConsumerStats stats = new DefaultClientSessionEventConsumerStats(consumerId, statsFactory);
        managedClientSessionEventConsumerStats.add(stats);
        return stats;
    }

    @Override
    public PublishMsgConsumerStats createPublishMsgConsumerStats(String consumerId) {
        log.trace("Creating PublishMsgConsumerStats, consumerId - {}.", consumerId);
        PublishMsgConsumerStats stats = new DefaultPublishMsgConsumerStats(consumerId, statsFactory);
        managedPublishMsgConsumerStats.add(stats);
        return stats;
    }

    @Override
    public DeviceProcessorStats createDeviceProcessorStats(String consumerId) {
        log.trace("Creating DeviceProcessorStats, consumerId - {}.", consumerId);
        DeviceProcessorStats stats = new DefaultDeviceProcessorStats(consumerId, statsFactory);
        managedDeviceProcessorStats.add(stats);
        return stats;
    }

    @Override
    public ApplicationProcessorStats createApplicationProcessorStats(String clientId) {
        log.trace("Creating ApplicationProcessorStats, clientId - {}.", clientId);
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
        log.trace("Creating SharedApplicationProcessorStats, clientId - {}.", clientId);
        if (applicationProcessorStatsEnabled) {
            var compoundClientId = getCompoundClientId(clientId, subscription);

            ApplicationProcessorStats stats = new DefaultApplicationProcessorStats(compoundClientId, statsFactory);
            managedApplicationProcessorStats.put(compoundClientId, stats);

            List<String> clientIds = sharedSubscriptionCompoundClientIds.computeIfAbsent(clientId, s -> new ArrayList<>());
            clientIds.add(compoundClientId);

            return stats;
        } else {
            return StubApplicationProcessorStats.STUB_APPLICATION_PROCESSOR_STATS;
        }
    }

    private String getCompoundClientId(String clientId, TopicSharedSubscription subscription) {
        return clientId + "_" + subscription.getShareName() + "_" + subscription.getTopic();
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
        log.trace("Clearing ApplicationProcessorStats, clientId - {}.", clientId);
        ApplicationProcessorStats stats = managedApplicationProcessorStats.get(clientId);
        if (stats != null && stats.isActive()) {
            stats.disable();
        }
    }

    @Override
    public void clearSharedApplicationProcessorStats(String clientId) {
        log.trace("Clearing SharedApplicationProcessorStats, clientId - {}.", clientId);
        List<String> clientIds = sharedSubscriptionCompoundClientIds.get(clientId);
        if (CollectionUtils.isEmpty(clientIds)) {
            return;
        }
        for (String compoundClientId : clientIds) {
            ApplicationProcessorStats stats = managedApplicationProcessorStats.get(compoundClientId);
            if (stats != null && stats.isActive()) {
                stats.disable();
            }
        }
    }

    @Override
    public void clearSharedApplicationProcessorStats(String clientId, TopicSharedSubscription subscription) {
        log.trace("Clearing SharedApplicationProcessorStats, clientId - {}, subscription - {}.", clientId, subscription);

        var compoundClientId = getCompoundClientId(clientId, subscription);

        List<String> clientIds = sharedSubscriptionCompoundClientIds.get(clientId);
        if (CollectionUtils.isEmpty(clientIds)) {
            return;
        }
        clientIds.remove(compoundClientId);

        ApplicationProcessorStats stats = managedApplicationProcessorStats.get(compoundClientId);
        if (stats != null && stats.isActive()) {
            stats.disable();
        }
    }

    @Override
    public AtomicInteger createSubscriptionSizeCounter() {
        log.trace("Creating SubscriptionSizeCounter.");
        AtomicInteger sizeGauge = statsFactory.createGauge(StatsType.SUBSCRIPTION_TOPIC_TRIE_SIZE.getPrintName(), new AtomicInteger(0));
        gauges.add(new Gauge(StatsType.SUBSCRIPTION_TOPIC_TRIE_SIZE.getPrintName(), sizeGauge::get));
        return sizeGauge;
    }

    @Override
    public AtomicInteger createRetainMsgSizeCounter() {
        log.trace("Creating RetainMsgSizeCounter.");
        AtomicInteger sizeGauge = statsFactory.createGauge(StatsType.RETAIN_MSG_TRIE_SIZE.getPrintName(), new AtomicInteger(0));
        gauges.add(new Gauge(StatsType.RETAIN_MSG_TRIE_SIZE.getPrintName(), sizeGauge::get));
        return sizeGauge;
    }

    @Override
    public void registerLastWillStats(Map<?, ?> lastWillMsgsMap) {
        log.trace("Registering LastWillStats.");
        statsFactory.createGauge(StatsType.LAST_WILL_CLIENTS.getPrintName(), lastWillMsgsMap, Map::size);
        gauges.add(new Gauge(StatsType.LAST_WILL_CLIENTS.getPrintName(), lastWillMsgsMap::size));
    }

    @Override
    public void registerActiveSessionsStats(Map<?, ?> sessionsMap) {
        log.trace("Registering SessionsStats.");
        statsFactory.createGauge(StatsType.CONNECTED_SESSIONS.getPrintName(), sessionsMap, Map::size);
        gauges.add(new Gauge(StatsType.CONNECTED_SESSIONS.getPrintName(), sessionsMap::size));
    }

    @Override
    public void registerAllClientSessionsStats(Map<?, ?> clientSessionsMap) {
        log.trace("Registering AllClientSessionsStats.");
        statsFactory.createGauge(StatsType.ALL_CLIENT_SESSIONS.getPrintName(), clientSessionsMap, Map::size);
        gauges.add(new Gauge(StatsType.ALL_CLIENT_SESSIONS.getPrintName(), clientSessionsMap::size));
    }

    @Override
    public void registerClientSubscriptionsStats(Map<?, ?> clientSubscriptionsMap) {
        log.trace("Registering ClientSubscriptionsStats.");
        statsFactory.createGauge(StatsType.CLIENT_SUBSCRIPTIONS.getPrintName(), clientSubscriptionsMap, Map::size);
        gauges.add(new Gauge(StatsType.CLIENT_SUBSCRIPTIONS.getPrintName(), clientSubscriptionsMap::size));
    }

    @Override
    public void registerRetainedMsgStats(Map<?, ?> retainedMessagesMap) {
        log.trace("Registering RetainedMsgStats.");
        statsFactory.createGauge(StatsType.RETAINED_MESSAGES.getPrintName(), retainedMessagesMap, Map::size);
        gauges.add(new Gauge(StatsType.RETAINED_MESSAGES.getPrintName(), retainedMessagesMap::size));
    }

    @Override
    public void registerActiveApplicationProcessorsStats(Map<?, ?> processingFuturesMap) {
        log.trace("Registering ActiveApplicationProcessorsStats.");
        statsFactory.createGauge(StatsType.ACTIVE_APP_PROCESSORS.getPrintName(), processingFuturesMap, Map::size);
        gauges.add(new Gauge(StatsType.ACTIVE_APP_PROCESSORS.getPrintName(), processingFuturesMap::size));
    }

    @Override
    public void registerActiveSharedApplicationProcessorsStats(Map<?, ?> processingFuturesMap) {
        log.trace("Registering ActiveSharedApplicationProcessorsStats.");
        statsFactory.createGauge(StatsType.ACTIVE_SHARED_APP_PROCESSORS.getPrintName(), processingFuturesMap, Map::size);
        gauges.add(new Gauge(StatsType.ACTIVE_SHARED_APP_PROCESSORS.getPrintName(), processingFuturesMap::size));
    }

    @Override
    public void registerActorsStats(Map<?, ?> actorsMap) {
        log.trace("Registering ActorsStats.");
        statsFactory.createGauge(StatsType.RUNNING_ACTORS.getPrintName(), actorsMap, Map::size);
        gauges.add(new Gauge(StatsType.RUNNING_ACTORS.getPrintName(), actorsMap::size));
    }

    @Override
    public AtomicLong createSubscriptionTrieNodesCounter() {
        log.trace("Creating SubscriptionTrieNodesCounter.");
        AtomicLong sizeGauge = statsFactory.createGauge(StatsType.SUBSCRIPTION_TRIE_NODES.getPrintName(), new AtomicLong(0));
        gauges.add(new Gauge(StatsType.SUBSCRIPTION_TRIE_NODES.getPrintName(), sizeGauge::get));
        return sizeGauge;
    }

    @Override
    public AtomicLong createRetainMsgTrieNodesCounter() {
        log.trace("Creating RetainMsgTrieNodesCounter.");
        AtomicLong sizeGauge = statsFactory.createGauge(StatsType.RETAIN_MSG_TRIE_NODES.getPrintName(), new AtomicLong(0));
        gauges.add(new Gauge(StatsType.RETAIN_MSG_TRIE_NODES.getPrintName(), sizeGauge::get));
        return sizeGauge;
    }

    @Override
    public MessagesStats createSqlQueueStats(String queueName, int queueIndex) {
        log.trace("Creating SqlQueueStats, queueName - {}, queueIndex - {}.", queueName, queueIndex);
        MessagesStats stats = statsFactory.createMessagesStats(StatsType.SQL_QUEUE.getPrintName() + "." + queueName,
                "queueIndex", String.valueOf(queueIndex));
        managedStats.add(stats);
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
                "consumerId", clientId,
                "operation", "syncCommit"));
        managedQueueConsumers.put(clientId, timer);
        return timer::logTime;
    }

    @Override
    public void registerProducerQueue(String queueName, Queue<?> queue) {
        statsFactory.createGauge(StatsType.PENDING_PRODUCER_MESSAGES.getPrintName(), queue, Queue::size, "queueName", queueName);
        managedProducerQueues.add(new Gauge(queueName, queue::size));
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
    public ClientActorStats getClientActorStats() {
        return clientActorStats;
    }

    @Scheduled(fixedDelayString = "${stats.print-interval-ms}")
    public void printStats() {
        log.info("----------------------------------------------------------------");
        for (MessagesStats stats : managedStats) {
            String statsStr = StatsConstantNames.TOTAL_MSGS + " = [" + stats.getTotal() + "] " +
                    StatsConstantNames.SUCCESSFUL_MSGS + " = [" + stats.getSuccessful() + "] " +
                    StatsConstantNames.FAILED_MSGS + " = [" + stats.getFailed() + "] ";
            log.info("[{}] Stats: {}", stats.getName(), statsStr);
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
            log.info("[{}][{}] Stats: {}", StatsType.DEVICE_PROCESSOR.getPrintName(), stats.getConsumerId(), statsStr);
            stats.reset();
        }

        if (applicationProcessorStatsEnabled) {
            for (ApplicationProcessorStats stats : new ArrayList<>(managedApplicationProcessorStats.values())) {
                String msgStatsStr = stats.getStatsCounters().stream()
                        .map(statsCounter -> statsCounter.getName() + " = [" + statsCounter.get() + "]")
                        .collect(Collectors.joining(" "));
                String latencyStatsStr = stats.getLatencyTimers().entrySet().stream()
                        .map(entry -> entry.getKey() + " = [" + entry.getValue().getCount() + "|" + entry.getValue().getAvg() + "|" + entry.getValue().getMax() + "]")
                        .collect(Collectors.joining(" "));
                log.info("[{}][{}] Latency Stats: {}, Processing Stats: {}", StatsType.APP_PROCESSOR.getPrintName(), stats.getClientId(), latencyStatsStr, msgStatsStr);
                if (!stats.isActive()) {
                    log.trace("[{}] Clearing inactive APPLICATION stats", stats.getClientId());
                    managedApplicationProcessorStats.computeIfPresent(stats.getClientId(), (clientId, oldStats) -> oldStats.isActive() ? oldStats : null);
                } else {
                    stats.reset();
                }
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

        StringBuilder gaugeLogBuilder = new StringBuilder();
        for (Gauge gauge : gauges) {
            gaugeLogBuilder.append(gauge.getName()).append(" = [").append(gauge.getValueSupplier().get().intValue()).append("] ");
        }
        log.info("Gauges Stats: {}", gaugeLogBuilder.toString());

        StringBuilder producerGaugeLogBuilder = new StringBuilder();
        for (Gauge gauge : managedProducerQueues) {
            producerGaugeLogBuilder.append(gauge.getName()).append(" = [").append(gauge.getValueSupplier().get().intValue()).append("] ");
        }
        log.info("Producer Gauges Stats: {}", producerGaugeLogBuilder.toString());

        StringBuilder clientActorLogBuilder = new StringBuilder();
        clientActorLogBuilder.append("msgInQueueTime").append(" = [").append(clientActorStats.getMsgCount()).append(" | ")
                .append(clientActorStats.getQueueTimeAvg()).append(" | ")
                .append(clientActorStats.getQueueTimeMax()).append("] ")
        ;
        clientActorStats.getTimers().forEach((msgType, timer) -> {
            clientActorLogBuilder.append(msgType).append(" = [").append(timer.getCount()).append(" | ")
                    .append(timer.getAvg()).append("] ");
        });
        clientActorStats.reset();
        log.info("Client Actor Average Stats: {}", clientActorLogBuilder.toString());

        StringBuilder timerLogBuilder = new StringBuilder();
        for (ResettableTimer resettableTimer : timerStats.getTimers()) {
            timerLogBuilder.append(resettableTimer.getTimer().getId().getName()).append(" = [").append(resettableTimer.getCount()).append(" | ")
                    .append(resettableTimer.getAvg()).append("] ");
            resettableTimer.reset();
        }
        log.info("Timer Average Stats: {}", timerLogBuilder.toString());

        StringBuilder queueProducerLogBuilder = new StringBuilder();
        managedQueueProducers.forEach((producerId, timer) -> {
            queueProducerLogBuilder.append(producerId).append(" = [").append(timer.getCount()).append(" | ")
                    .append(timer.getAvg()).append("] ");
            timer.reset();
        });
        log.info("Queue Producer Send Time Average Stats: {}", queueProducerLogBuilder.toString());

        StringBuilder queueConsumerLogBuilder = new StringBuilder();
        managedQueueConsumers.forEach((consumerId, timer) -> {
            queueConsumerLogBuilder.append(consumerId).append(" = [").append(timer.getCount()).append(" | ")
                    .append(timer.getAvg()).append("] ");
            timer.reset();
        });
        log.info("Queue Consumer Commit Time Average Stats: {}", queueConsumerLogBuilder.toString());
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
