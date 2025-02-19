/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.integration.ComponentLifecycleEvent;
import org.thingsboard.mqtt.broker.common.data.integration.IntegrationType;
import org.thingsboard.mqtt.broker.common.stats.DefaultCounter;
import org.thingsboard.mqtt.broker.common.stats.MessagesStats;
import org.thingsboard.mqtt.broker.common.stats.StatsConstantNames;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsType;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;
import org.thingsboard.mqtt.broker.integration.api.IntegrationStatisticsService;
import org.thingsboard.mqtt.broker.integration.api.data.IntegrationStatisticsKey;
import org.thingsboard.mqtt.broker.integration.api.data.IntegrationStatisticsMetricName;
import org.thingsboard.mqtt.broker.queue.TbmqOrIntegrationExecutorComponent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
@TbmqOrIntegrationExecutorComponent
@ConditionalOnProperty(prefix = "stats.ie", value = "enabled", havingValue = "true")
public class DefaultIntegrationStatisticsService implements IntegrationStatisticsService {

    private static final String STATS_KEY_COUNTER = StatsType.INTEGRATION.getPrintName() + "_stats_counter";
    private static final String STATS_KEY_GAUGE = StatsType.INTEGRATION.getPrintName() + "_stats_gauge";

    private final Map<IntegrationStatisticsKey, DefaultCounter> counters = new ConcurrentHashMap<>();
    private final Map<IntegrationStatisticsKey, AtomicLong> gauges = new ConcurrentHashMap<>();
    private final List<MessagesStats> managedStats = new CopyOnWriteArrayList<>();

    private final StatsFactory statsFactory;

    private final ScheduledExecutorService scheduler = ThingsBoardExecutors.newSingleScheduledThreadPool("ie-stats-service");

    @Value("${stats.ie.print-interval-ms:60000}")
    private long printIntervalMs;

    @PostConstruct
    public void init() {
        scheduler.scheduleAtFixedRate(this::printStats, printIntervalMs, printIntervalMs, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void shutdown() {
        if (scheduler != null) {
            ThingsBoardExecutors.shutdownAndAwaitTermination(scheduler, "Integration Statistics");
        }
    }

    @Override
    public MessagesStats createIeUplinkPublishStats() {
        log.trace("Creating IeUplinkPublishStats");
        MessagesStats stats = statsFactory.createMessagesStats(StatsType.IE_UPLINK_PRODUCER.getPrintName());
        managedStats.add(stats);
        return stats;
    }

    @Override
    public void onIntegrationStateUpdate(IntegrationType integrationType, ComponentLifecycleEvent state, boolean success) {
        try {
            if (ComponentLifecycleEvent.STARTED.equals(state)) {
                incrementCounter(new IntegrationStatisticsKey(IntegrationStatisticsMetricName.START, success, integrationType));
            } else if (!success || ComponentLifecycleEvent.FAILED.equals(state)) {
                incrementCounter(new IntegrationStatisticsKey(IntegrationStatisticsMetricName.START, false, integrationType));
            }
        } catch (Exception e) {
            log.error("[{}][{}][{}] Failed to process onIntegrationStateUpdate. ", integrationType, state, success, e);
        }
    }

    @Override
    public void onIntegrationsCountUpdate(IntegrationType integrationType, int started, int failed) {
        try {
            setGaugeValue(new IntegrationStatisticsKey(IntegrationStatisticsMetricName.START, true, integrationType), started);
            setGaugeValue(new IntegrationStatisticsKey(IntegrationStatisticsMetricName.START, false, integrationType), failed);
        } catch (Exception e) {
            log.error("onIntegrationsCountUpdate type: [{}], started: [{}], failed: [{}]", integrationType, started, failed, e);
        }
    }

    @Override
    public void onUplinkMsg(IntegrationType integrationType, boolean success) {
        onMsg(IntegrationStatisticsMetricName.MSGS_UPLINK, integrationType, success);
    }

    @Override
    public void onDownlinkMsg(IntegrationType integrationType, boolean success) {
        onMsg(IntegrationStatisticsMetricName.MSGS_DOWNLINK, integrationType, success);
    }

    private void onMsg(IntegrationStatisticsMetricName metric, IntegrationType integrationType, boolean success) {
        try {
            incrementCounter(new IntegrationStatisticsKey(metric, success, integrationType));
        } catch (Exception e) {
            log.error("[{}][{}] onMsg: [{}]", metric, integrationType, success, e);
        }
    }

    @Override
    public void printStats() {
        log.info("----------------------------------------------------------------");
        if (!counters.isEmpty()) {
            StringBuilder stats = new StringBuilder();
            counters.forEach((key, value) -> stats.append(key).append(" = [").append(value.get()).append("] "));
            log.info("Integration Stats: {}", stats);
            reset();
        }
        if (!gauges.isEmpty()) {
            StringBuilder gaugeLogBuilder = new StringBuilder();
            gauges.forEach((key, value) -> gaugeLogBuilder.append(key).append(" = [").append(value.get()).append("] "));
            log.info("Gauges Stats: {}", gaugeLogBuilder);
        }
        for (MessagesStats stats : managedStats) {
            String statsStr = StatsConstantNames.QUEUE_SIZE + " = [" + stats.getCurrentQueueSize() + "] " +
                    StatsConstantNames.TOTAL_MSGS + " = [" + stats.getTotal() + "] " +
                    StatsConstantNames.SUCCESSFUL_MSGS + " = [" + stats.getSuccessful() + "] " +
                    StatsConstantNames.FAILED_MSGS + " = [" + stats.getFailed() + "] ";
            log.info("[{}] Integration Queue Stats: {}", stats.getName(), statsStr);
            stats.reset();
        }
    }

    @Override
    public void reset() {
        counters.values().forEach(DefaultCounter::clear);
    }

    private void incrementCounter(IntegrationStatisticsKey tags) {
        getOrCreateStatsCounter(tags).increment();
    }

    private void setGaugeValue(IntegrationStatisticsKey tags, int value) {
        getOrCreateStatsGauge(tags).set(value);
    }

    private DefaultCounter getOrCreateStatsCounter(IntegrationStatisticsKey tags) {
        return counters.computeIfAbsent(tags, s ->
                statsFactory.createDefaultCounter(STATS_KEY_COUNTER, tags.getTags()));
    }

    private AtomicLong getOrCreateStatsGauge(IntegrationStatisticsKey tags) {
        return gauges.computeIfAbsent(tags, s ->
                statsFactory.createGauge(STATS_KEY_GAUGE, new AtomicLong(0), tags.getTags()));
    }

}
