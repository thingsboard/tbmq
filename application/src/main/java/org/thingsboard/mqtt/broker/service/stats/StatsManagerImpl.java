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
package org.thingsboard.mqtt.broker.service.stats;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.actors.ActorStatsManager;
import org.thingsboard.mqtt.broker.common.stats.MessagesStats;
import org.thingsboard.mqtt.broker.common.stats.StatsConstantNames;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StubMessagesStats;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatsManagerImpl implements StatsManager, ActorStatsManager {
    private final List<MessagesStats> managedStats = new CopyOnWriteArrayList<>();
    private final List<Gauge> gauges = new CopyOnWriteArrayList<>();

    private final List<PublishMsgConsumerStats> managedPublishMsgConsumerStats = new CopyOnWriteArrayList<>();
    private final List<DeviceProcessorStats> managedDeviceProcessorStats = new CopyOnWriteArrayList<>();
    private final Map<String, ApplicationProcessorStats> managedApplicationProcessorStats = new ConcurrentHashMap<>();

    @Value("${stats.enabled}")
    private Boolean statsEnabled;
    @Value("${stats.application-processor.enabled}")
    private Boolean applicationProcessorStatsEnabled;

    private final StatsFactory statsFactory;

    @Override
    public TbQueueCallback wrapTbQueueCallback(TbQueueCallback queueCallback, MessagesStats stats) {
        return statsEnabled ? new StatsQueueCallback(queueCallback, stats) : queueCallback;
    }

    @Override
    public MessagesStats createMsgDispatcherPublishStats() {
        log.trace("Creating MsgDispatcherPublishStats.");
        if (statsEnabled) {
            MessagesStats stats = statsFactory.createMessagesStats(StatsType.MSG_DISPATCHER_PRODUCER.getPrintName());
            managedStats.add(stats);
            return stats;
        } else {
            return StubMessagesStats.STUB_MESSAGE_STATS;
        }
    }

    @Override
    public PublishMsgConsumerStats createPublishMsgConsumerStats(String consumerId) {
        log.trace("Creating PublishMsgConsumerStats, consumerId - {}.", consumerId);
        if (statsEnabled) {
            PublishMsgConsumerStats stats = new DefaultPublishMsgConsumerStats(consumerId, statsFactory);
            managedPublishMsgConsumerStats.add(stats);
            return stats;
        } else {
            return StubPublishMsgConsumerStats.STUB_PUBLISH_MSG_CONSUMER_STATS;
        }
    }

    @Override
    public DeviceProcessorStats createDeviceProcessorStats(String consumerId) {
        log.trace("Creating DeviceProcessorStats, consumerId - {}.", consumerId);
        if (statsEnabled) {
            DeviceProcessorStats stats = new DefaultDeviceProcessorStats(consumerId, statsFactory);
            managedDeviceProcessorStats.add(stats);
            return stats;
        } else {
            return StubDeviceProcessorStats.STUB_DEVICE_PROCESSOR_STATS;
        }
    }

    @Override
    public ApplicationProcessorStats createApplicationProcessorStats(String clientId) {
        log.trace("Creating ApplicationProcessorStats, clientId - {}.", clientId);
        if (statsEnabled && applicationProcessorStatsEnabled) {
            ApplicationProcessorStats stats = new DefaultApplicationProcessorStats(clientId, statsFactory);
            managedApplicationProcessorStats.put(clientId, stats);
            return stats;
        } else {
            return StubApplicationProcessorStats.STUB_APPLICATION_PROCESSOR_STATS;
        }
    }

    @Override
    public void clearApplicationProcessorStats(String clientId) {
        log.trace("Clearing ApplicationProcessorStats, clientId - {}.", clientId);
        managedApplicationProcessorStats.remove(clientId);
    }

    @Override
    public AtomicInteger createSubscriptionSizeCounter() {
        log.trace("Creating SubscriptionSizeCounter.");
        if (statsEnabled) {
            AtomicInteger sizeGauge = statsFactory.createGauge(StatsType.SUBSCRIPTION_TOPIC_TRIE_SIZE.getPrintName(), new AtomicInteger(0));
            gauges.add(new Gauge(StatsType.SUBSCRIPTION_TOPIC_TRIE_SIZE.getPrintName(), sizeGauge::get));
            return sizeGauge;
        } else {
            return new AtomicInteger(0);
        }
    }

    @Override
    public void registerLastWillStats(Map<?, ?> lastWillMsgsMap) {
        log.trace("Registering LastWillStats.");
        if (statsEnabled) {
            statsFactory.createGauge(StatsType.LAST_WILL_CLIENTS.getPrintName(), lastWillMsgsMap, Map::size);
            gauges.add(new Gauge(StatsType.LAST_WILL_CLIENTS.getPrintName(), lastWillMsgsMap::size));
        }
    }

    @Override
    public void registerActiveSessionsStats(Map<?, ?> sessionsMap) {
        log.trace("Registering SessionsStats.");
        if (statsEnabled) {
            statsFactory.createGauge(StatsType.CONNECTED_SESSIONS.getPrintName(), sessionsMap, Map::size);
            gauges.add(new Gauge(StatsType.CONNECTED_SESSIONS.getPrintName(), sessionsMap::size));
        }
    }

    @Override
    public void registerAllClientSessionsStats(Map<?, ?> clientSessionsMap) {
        log.trace("Registering AllClientSessionsStats.");
        if (statsEnabled) {
            statsFactory.createGauge(StatsType.ALL_CLIENT_SESSIONS.getPrintName(), clientSessionsMap, Map::size);
            gauges.add(new Gauge(StatsType.ALL_CLIENT_SESSIONS.getPrintName(), clientSessionsMap::size));
        }
    }

    @Override
    public void registerClientSubscriptionsStats(Map<?, ?> clientSubscriptionsMap) {
        log.trace("Registering ClientSubscriptionsStats.");
        if (statsEnabled) {
            statsFactory.createGauge(StatsType.CLIENT_SUBSCRIPTIONS.getPrintName(), clientSubscriptionsMap, Map::size);
            gauges.add(new Gauge(StatsType.CLIENT_SUBSCRIPTIONS.getPrintName(), clientSubscriptionsMap::size));
        }
    }

    @Override
    public void registerActiveApplicationProcessorsStats(Map<?, ?> processingFuturesMap) {
        log.trace("Registering ActiveApplicationProcessorsStats.");
        if (statsEnabled) {
            statsFactory.createGauge(StatsType.ACTIVE_APP_PROCESSORS.getPrintName(), processingFuturesMap, Map::size);
            gauges.add(new Gauge(StatsType.ACTIVE_APP_PROCESSORS.getPrintName(), processingFuturesMap::size));
        }
    }

    @Override
    public void registerActorsStats(Map<?, ?> actorsMap) {
        log.trace("Registering ActorsStats.");
        if (statsEnabled) {
            statsFactory.createGauge(StatsType.RUNNING_ACTORS.getPrintName(), actorsMap, Map::size);
            gauges.add(new Gauge(StatsType.RUNNING_ACTORS.getPrintName(), actorsMap::size));
        }
    }

    @Override
    public AtomicLong createSubscriptionTrieNodesCounter() {
        log.trace("Creating SubscriptionTrieNodesCounter.");
        if (statsEnabled) {
            AtomicLong sizeGauge = statsFactory.createGauge(StatsType.SUBSCRIPTION_TRIE_NODES.getPrintName(), new AtomicLong(0));
            gauges.add(new Gauge(StatsType.SUBSCRIPTION_TRIE_NODES.getPrintName(), sizeGauge::get));
            return sizeGauge;
        } else {
            return new AtomicLong(0);
        }
    }

    @Scheduled(fixedDelayString = "${stats.print-interval-ms}")
    public void printStats() {
        if (!statsEnabled) {
            return;
        }

        for (MessagesStats stats : managedStats) {
            String statsStr = StatsConstantNames.TOTAL_MSGS + " = [" + stats.getTotal() + "] " +
                    StatsConstantNames.SUCCESSFUL_MSGS + " = [" + stats.getSuccessful() + "] " +
                    StatsConstantNames.FAILED_MSGS + " = [" + stats.getFailed() + "] ";
            log.info("[{}] Stats: {}", stats.getName(), statsStr);
            stats.reset();
        }

        for (PublishMsgConsumerStats stats : managedPublishMsgConsumerStats) {
            String statsStr = stats.getStatsCounters().stream()
                    .map(statsCounter -> statsCounter.getName() + " = [" + statsCounter.get() + "]")
                    .collect(Collectors.joining(" "));
            log.info("[{}][{}] Stats: {}", StatsType.PUBLISH_MSG_CONSUMER.getPrintName(), stats.getConsumerId(), statsStr);
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
            for (ApplicationProcessorStats stats : managedApplicationProcessorStats.values()) {
                String statsStr = stats.getStatsCounters().stream()
                        .map(statsCounter -> statsCounter.getName() + " = [" + statsCounter.get() + "]")
                        .collect(Collectors.joining(" "));
                log.info("[{}][{}] Stats: {}", StatsType.APP_PROCESSOR.getPrintName(), stats.getClientId(), statsStr);
                stats.reset();
            }
        }

        StringBuilder gaugeLogBuilder = new StringBuilder();
        for (Gauge gauge : gauges) {
            gaugeLogBuilder.append(gauge.getName()).append(" = [").append(gauge.getValueSupplier().get().intValue()).append("] ");
        }
        log.info("Gauges Stats: {}", gaugeLogBuilder.toString());
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
