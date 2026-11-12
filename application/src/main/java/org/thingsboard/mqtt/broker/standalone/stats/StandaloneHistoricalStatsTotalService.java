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
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.actors.client.service.session.ClientSessionService;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.ClientSubscriptionService;
import org.thingsboard.mqtt.broker.common.data.kv.BasicTsKvEntry;
import org.thingsboard.mqtt.broker.common.data.kv.LongDataEntry;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvEntry;
import org.thingsboard.mqtt.broker.common.util.DonAsynchron;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.config.HistoricalDataReportProperties;
import org.thingsboard.mqtt.broker.dao.timeseries.TimeseriesService;
import org.thingsboard.mqtt.broker.gen.queue.ToUsageStatsMsgProto;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.service.historical.stats.HistoricalStatsTotalPublisher;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgListenerService;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.time.ZoneOffset.UTC;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.ENTITY_ID_TOTAL;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.RETAINED_MSGS;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.SESSIONS;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.SUBSCRIPTIONS;

/**
 * The only node is the whole cluster, so its per-node usage entry is already the total: it is saved under the
 * total entity directly instead of through a topic, accumulating the entries of one interval the way the cluster
 * total consumer does. Also writes the session, subscription and retained message counts on the same cron.
 * Does nothing when historical data reporting is disabled.
 */
@Slf4j
@Component
@StandaloneComponent
@RequiredArgsConstructor
public class StandaloneHistoricalStatsTotalService implements HistoricalStatsTotalPublisher {

    private static final long ONE_MINUTE_MS = TimeUnit.MINUTES.toMillis(1);

    private final ClientSessionService clientSessionService;
    private final ClientSubscriptionService clientSubscriptionService;
    private final RetainedMsgListenerService retainedMsgService;
    private final TimeseriesService timeseriesService;
    private final HistoricalDataReportProperties historicalDataReportProperties;

    private final Map<String, TsMsgTotal> totalStatsMap = new HashMap<>();
    private ExecutorService countsProcessingExecutor;

    @PostConstruct
    void init() {
        if (historicalDataReportProperties.isDisabled()) {
            return;
        }
        countsProcessingExecutor = Executors.newSingleThreadExecutor(ThingsBoardThreadFactory.forName("historical-stats-sessions-consumer"));
    }

    @Override
    public void publish(ToUsageStatsMsgProto msg, TbQueueCallback callback) {
        if (historicalDataReportProperties.isDisabled()) {
            return;
        }
        TsKvEntry entry;
        ListenableFuture<Void> saved;
        // Serialized: called from the stats client's reporting thread and from its shutdown flush.
        synchronized (totalStatsMap) {
            try {
                entry = new BasicTsKvEntry(msg.getTs(), new LongDataEntry(msg.getUsageStats().getKey(), totalFor(msg)));
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

    /**
     * Same accumulation as the cluster total consumer: the held (ts, value) of a key is seeded from the stored latest
     * total, and a message of the same interval adds to it, so a partial interval flushed on shutdown is not replaced
     * by the first report after a restart (the store upserts on the same ts). A newer message replaces the held pair;
     * an older one is saved as it is and leaves the held pair untouched.
     */
    private long totalFor(ToUsageStatsMsgProto msg) throws Exception {
        String key = msg.getUsageStats().getKey();
        long msgTs = msg.getTs();
        long msgValue = msg.getUsageStats().getValue();
        TsMsgTotal held = totalStatsMap.get(key);
        if (held == null) {
            held = seedFromStoredTotal(key, msgTs);
            totalStatsMap.put(key, held);
        }
        if (held.ts < msgTs) {
            held.ts = msgTs;
            held.value = msgValue;
            return msgValue;
        }
        if (held.ts == msgTs) {
            held.value += msgValue;
            return held.value;
        }
        return msgValue;
    }

    private TsMsgTotal seedFromStoredTotal(String key, long msgTs) throws Exception {
        long intervalMs = ONE_MINUTE_MS * historicalDataReportProperties.getInterval();
        List<TsKvEntry> latestTs = timeseriesService.findLatest(ENTITY_ID_TOTAL, List.of(key)).get();
        if (!latestTs.isEmpty()) {
            TsKvEntry latest = latestTs.get(0);
            if (latest != null && msgTs <= latest.getTs() + intervalMs) {
                return new TsMsgTotal(latest.getTs(), latest.getLongValue().orElse(0L));
            }
        }
        return new TsMsgTotal(msgTs, 0L);
    }

    @Scheduled(cron = "#{@historicalDataReportProperties.cron}", zone = "#{@historicalDataReportProperties.zone}")
    public void process() {
        if (historicalDataReportProperties.isDisabled()) {
            return;
        }
        if (checkAllStatsServicesReady()) {
            countsProcessingExecutor.execute(this::processCountStats);
        }
    }

    private void processCountStats() {
        long ts = getStartOfCurrentMinute();
        List<TsKvEntry> entries = new ArrayList<>(3);
        entries.add(new BasicTsKvEntry(ts, new LongDataEntry(SESSIONS, (long) clientSessionService.getClientSessionsCount())));
        entries.add(new BasicTsKvEntry(ts, new LongDataEntry(SUBSCRIPTIONS, clientSubscriptionService.getClientSubscriptionsCount())));
        entries.add(new BasicTsKvEntry(ts, new LongDataEntry(RETAINED_MSGS, (long) retainedMsgService.getRetainedMessagesCount())));

        ListenableFuture<Void> savedTsFuture = timeseriesService.save(ENTITY_ID_TOTAL, entries);
        DonAsynchron.withCallback(savedTsFuture,
                unused -> log.trace("[{}] Successfully saved timeseries entries {}", ENTITY_ID_TOTAL, entries),
                throwable -> log.error("[{}] Failed to save timeseries entries {}", ENTITY_ID_TOTAL, entries, throwable));
    }

    private boolean checkAllStatsServicesReady() {
        if (clientSessionService.isInitialized()
                && clientSubscriptionService.isInitialized()
                && retainedMsgService.isInitialized()) {
            return true;
        }
        log.debug("Skipping scheduled stats report: Core system state not fully initialized.");
        return false;
    }

    private long getStartOfCurrentMinute() {
        return LocalDateTime.now(UTC).atZone(UTC).truncatedTo(ChronoUnit.MINUTES).toInstant().toEpochMilli();
    }

    @PreDestroy
    void destroy() {
        if (countsProcessingExecutor != null) {
            ThingsBoardExecutors.shutdownAndAwaitTermination(countsProcessingExecutor, "Historical session stats");
        }
    }

    private static final class TsMsgTotal {

        private long ts;
        private long value;

        private TsMsgTotal(long ts, long value) {
            this.ts = ts;
            this.value = value;
        }
    }

}
