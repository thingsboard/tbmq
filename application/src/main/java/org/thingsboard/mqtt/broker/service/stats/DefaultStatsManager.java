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
import org.thingsboard.mqtt.broker.common.stats.MessagesStats;
import org.thingsboard.mqtt.broker.common.stats.StatsConstantNames;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StubMessagesStats;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultStatsManager implements StatsManager {
    private final List<MessagesStats> managedStats = new CopyOnWriteArrayList<>();
    private final List<Gauge> gauges = new CopyOnWriteArrayList<>();

    @Value("${stats.enabled}")
    private Boolean statsEnabled;

    private final AtomicInteger publishConsumerId = new AtomicInteger(0);

    private final StatsFactory statsFactory;

    @Override
    public TbQueueCallback wrapTbQueueCallback(TbQueueCallback queueCallback, MessagesStats stats) {
        return statsEnabled ? new StatsQueueCallback(queueCallback, stats) : queueCallback;
    }

    @Override
    public MessagesStats createMsgDispatcherPublishStats() {
        if (statsEnabled) {
            MessagesStats stats = statsFactory.createMessagesStats(StatsType.MSG_DISPATCHER_PRODUCER.getPrintName());
            managedStats.add(stats);
            return stats;
        } else {
            return StubMessagesStats.STUB_MESSAGE_STATS;
        }
    }

    @Override
    public MessagesStats createPublishMsgConsumerStats() {
        if (statsEnabled) {
            MessagesStats stats = statsFactory.createMessagesStats(StatsType.PUBLISH_MSG_CONSUMER.getPrintName() + "." + publishConsumerId.getAndIncrement());
            managedStats.add(stats);
            return stats;
        } else {
            return StubMessagesStats.STUB_MESSAGE_STATS;
        }
    }

    @Override
    public AtomicInteger createSubscriptionSizeCounter() {
        if (statsEnabled) {
            AtomicInteger sizeGauge = statsFactory.createGauge(StatsType.SUBSCRIPTION_TOPIC_TRIE_SIZE.getPrintName(), new AtomicInteger(0));
            gauges.add(new Gauge(StatsType.SUBSCRIPTION_TOPIC_TRIE_SIZE.getPrintName(), sizeGauge::get));
            return sizeGauge;
        } else {
            return new AtomicInteger(0);
        }
    }

    @Override
    public AtomicInteger createLastWillCounter() {
        if (statsEnabled) {
            AtomicInteger sizeGauge = statsFactory.createGauge(StatsType.LAST_WILL_CLIENTS.getPrintName(), new AtomicInteger(0));
            gauges.add(new Gauge(StatsType.LAST_WILL_CLIENTS.getPrintName(), sizeGauge::get));
            return sizeGauge;
        } else {
            return new AtomicInteger(0);
        }
    }

    @Override
    public void registerSessionsStats(Map<?, ?> sessionsMap) {
        if (statsEnabled) {
            statsFactory.createGauge(StatsType.CONNECTED_SESSIONS.getPrintName(), sessionsMap, Map::size);
            gauges.add(new Gauge(StatsType.CONNECTED_SESSIONS.getPrintName(), sessionsMap::size));
        }
    }

    @Override
    public void registerActiveApplicationProcessorsCounter(Map<?, ?> processingFuturesMap) {
        if (statsEnabled) {
            statsFactory.createGauge(StatsType.ACTIVE_APP_PROCESSORS.getPrintName(), processingFuturesMap, Map::size);
            gauges.add(new Gauge(StatsType.ACTIVE_APP_PROCESSORS.getPrintName(), processingFuturesMap::size));
        }
    }

    @Override
    public AtomicLong createSubscriptionTrieNodesCounter() {
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
