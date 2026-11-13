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
package org.thingsboard.mqtt.broker.standalone.stats;

import com.google.common.util.concurrent.ListenableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvEntry;
import org.thingsboard.mqtt.broker.common.util.DonAsynchron;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.config.HistoricalDataReportProperties;
import org.thingsboard.mqtt.broker.dao.timeseries.TimeseriesService;
import org.thingsboard.mqtt.broker.gen.queue.ToUsageStatsMsgProto;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.service.historical.stats.HistoricalStatsTotalAccumulator;
import org.thingsboard.mqtt.broker.service.historical.stats.HistoricalStatsTotalPublisher;

import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.ENTITY_ID_TOTAL;

/**
 * The only node is the whole cluster, so its per-node usage entry is saved under the total entity directly instead
 * of through a topic, summed by the same {@link HistoricalStatsTotalAccumulator} as the cluster total consumer.
 * Does nothing when historical data reporting is disabled.
 */
@Slf4j
@Component
@StandaloneComponent
@RequiredArgsConstructor
public class StandaloneHistoricalStatsTotalService implements HistoricalStatsTotalPublisher {

    private final HistoricalStatsTotalAccumulator accumulator;
    private final TimeseriesService timeseriesService;
    private final HistoricalDataReportProperties historicalDataReportProperties;

    @Override
    public void publish(ToUsageStatsMsgProto msg, TbQueueCallback callback) {
        if (historicalDataReportProperties.isDisabled()) {
            return;
        }
        TsKvEntry entry;
        ListenableFuture<Void> saved;
        // Serialized, so totals of the same ts are saved in the order they were summed: called from the stats
        // client's reporting thread and from its shutdown flush.
        synchronized (this) {
            try {
                entry = accumulator.accumulate(msg);
                if (entry == null) {
                    // Not a total key: the cluster topic accepts such a message and its consumer skips it.
                    callback.onSuccess(null);
                    return;
                }
                saved = timeseriesService.save(ENTITY_ID_TOTAL, entry);
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                callback.onFailure(e);
                return;
            }
        }
        DonAsynchron.withCallback(saved,
                unused -> {
                    log.trace("[{}] Successfully saved timeseries for key {} with value {}", ENTITY_ID_TOTAL, entry.getKey(), entry.getValue());
                    callback.onSuccess(null);
                },
                callback::onFailure);
    }

}
