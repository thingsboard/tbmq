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
package org.thingsboard.mqtt.broker.service.historical.stats;

import com.google.common.util.concurrent.ListenableFuture;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvEntry;
import org.thingsboard.mqtt.broker.common.util.DonAsynchron;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.common.util.edition.ClusterComponent;
import org.thingsboard.mqtt.broker.config.HistoricalDataReportProperties;
import org.thingsboard.mqtt.broker.dao.timeseries.TimeseriesService;
import org.thingsboard.mqtt.broker.gen.queue.ToUsageStatsMsgProto;
import org.thingsboard.mqtt.broker.queue.TbQueueConsumer;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.HistoricalDataQueueFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.ENTITY_ID_TOTAL;

@Slf4j
@Component
@ClusterComponent
@RequiredArgsConstructor
public class HistoricalStatsTotalConsumer {

    private final HistoricalDataQueueFactory historicalDataQueueFactory;
    private final HistoricalStatsTotalHelper helper;
    private final ServiceInfoProvider serviceInfoProvider;
    private final HistoricalStatsTotalAccumulator accumulator;
    private final TimeseriesService timeseriesService;
    private final HistoricalDataReportProperties historicalDataReportProperties;

    private volatile boolean stopped = false;

    @Value("${queue.historical-data-total.poll-interval}")
    private long pollDuration;

    private ExecutorService totalStatsProcessingExecutor;
    private TbQueueConsumer<TbProtoQueueMsg<ToUsageStatsMsgProto>> consumer;

    @PostConstruct
    private void init() {
        if (historicalDataReportProperties.isDisabled()) {
            return;
        }

        initConsumer();
        initExecutors();
        totalStatsProcessingExecutor.execute(this::processHistoricalDataTotalStats);
    }

    private void processHistoricalDataTotalStats() {
        while (!stopped) {
            try {
                List<TbProtoQueueMsg<ToUsageStatsMsgProto>> msgs = consumer.poll(pollDuration);
                if (msgs.isEmpty()) {
                    continue;
                }
                for (TbProtoQueueMsg<ToUsageStatsMsgProto> msg : msgs) {
                    processSaveHistoricalStatsTotal(msg);
                }
                consumer.commitSync();
            } catch (Exception e) {
                if (!stopped) {
                    log.error("Failed to process messages from queue.", e);
                    try {
                        Thread.sleep(pollDuration);
                    } catch (InterruptedException e2) {
                        log.trace("Failed to wait until the server has capacity to handle new requests", e2);
                    }
                }
            }
        }
        log.info("Historical Data Total Consumer stopped.");
    }

    // Package-private (not private) so the same-package unit test can invoke it directly; not an extension point.
    void processSaveHistoricalStatsTotal(TbProtoQueueMsg<ToUsageStatsMsgProto> msg) {
        TsKvEntry tsKvEntry = accumulator.accumulate(msg.getValue());
        if (tsKvEntry == null) {
            return;
        }
        ListenableFuture<Void> savedTsFuture = timeseriesService.save(ENTITY_ID_TOTAL, tsKvEntry);
        DonAsynchron.withCallback(savedTsFuture,
                unused -> log.trace("[{}] Successfully saved timeseries for key {} with value {}", ENTITY_ID_TOTAL, tsKvEntry.getKey(), tsKvEntry.getValue()),
                throwable -> log.error("[{}] Failed to save timeseries for key {} with value {}", ENTITY_ID_TOTAL, tsKvEntry.getKey(), tsKvEntry.getValue(), throwable));
    }

    private void initConsumer() {
        this.consumer = historicalDataQueueFactory.createConsumer(helper.getTopic(), serviceInfoProvider.getServiceId());
        this.consumer.subscribe();
    }

    private void initExecutors() {
        totalStatsProcessingExecutor = Executors.newSingleThreadExecutor(ThingsBoardThreadFactory.forName("historical-stats-total-consumer"));
    }

    @PreDestroy
    public void destroy() {
        if (historicalDataReportProperties.isDisabled()) {
            return;
        }
        stopped = true;
        if (totalStatsProcessingExecutor != null) {
            ThingsBoardExecutors.shutdownAndAwaitTermination(totalStatsProcessingExecutor, "Historical total stats");
        }
        if (consumer != null) {
            consumer.unsubscribeAndClose();
        }
    }

}
