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
import org.thingsboard.mqtt.broker.config.HistoricalDataReportProperties;
import org.thingsboard.mqtt.broker.dao.timeseries.TimeseriesService;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgListenerService;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.time.ZoneOffset.UTC;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.ENTITY_ID_TOTAL;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.RETAINED_MSGS;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.SESSIONS;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.SUBSCRIPTIONS;

/**
 * Saves the broker-wide session, subscription and retained message counts under the total entity on the
 * historical data cron, once the services that hold them are initialized.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HistoricalStatsCountsReporter {

    private final ClientSessionService clientSessionService;
    private final ClientSubscriptionService clientSubscriptionService;
    private final RetainedMsgListenerService retainedMsgService;
    private final TimeseriesService timeseriesService;
    private final HistoricalDataReportProperties historicalDataReportProperties;

    private ExecutorService sessionsProcessingExecutor;

    @PostConstruct
    void init() {
        if (historicalDataReportProperties.isDisabled()) {
            return;
        }
        sessionsProcessingExecutor = Executors.newSingleThreadExecutor(ThingsBoardThreadFactory.forName("historical-stats-sessions-consumer"));
    }

    @Scheduled(cron = "#{@historicalDataReportProperties.cron}", zone = "#{@historicalDataReportProperties.zone}")
    public void process() {
        if (historicalDataReportProperties.isDisabled()) {
            return;
        }
        boolean isSystemReady = checkAllStatsServicesReady();
        if (isSystemReady) {
            sessionsProcessingExecutor.execute(this::processCountStats);
        }
    }

    private void processCountStats() {
        long ts = getStartOfCurrentMinute();
        long clientSessionCount = clientSessionService.getClientSessionsCount();
        long clientSubscriptionCount = clientSubscriptionService.getClientSubscriptionsCount();
        long retainedMessagesCount = retainedMsgService.getRetainedMessagesCount();
        List<TsKvEntry> entries = new ArrayList<>(3);
        entries.add(new BasicTsKvEntry(ts,
                new LongDataEntry(SESSIONS, clientSessionCount)));
        entries.add(new BasicTsKvEntry(ts,
                new LongDataEntry(SUBSCRIPTIONS, clientSubscriptionCount)));
        entries.add(new BasicTsKvEntry(ts,
                new LongDataEntry(RETAINED_MSGS, retainedMessagesCount)));

        ListenableFuture<Void> savedTsFuture = timeseriesService.save(ENTITY_ID_TOTAL, entries);
        DonAsynchron.withCallback(savedTsFuture,
                unused -> log.trace("[{}] Successfully saved timeseries entries {}", ENTITY_ID_TOTAL, entries),
                throwable -> log.error("[{}] Failed to save timeseries entries {}", ENTITY_ID_TOTAL, entries, throwable));
    }

    private boolean checkAllStatsServicesReady() {
        boolean sessionsReady = clientSessionService.isInitialized();
        boolean subscriptionsReady = clientSubscriptionService.isInitialized();
        boolean retainedMsgsReady = retainedMsgService.isInitialized();

        if (sessionsReady && subscriptionsReady && retainedMsgsReady) {
            return true;
        } else {
            log.debug("Skipping scheduled stats report: Core system state not fully initialized.");
            return false;
        }
    }

    private long getStartOfCurrentMinute() {
        return LocalDateTime.now(UTC).atZone(UTC).truncatedTo(ChronoUnit.MINUTES).toInstant().toEpochMilli();
    }

    @PreDestroy
    void destroy() {
        if (sessionsProcessingExecutor != null) {
            ThingsBoardExecutors.shutdownAndAwaitTermination(sessionsProcessingExecutor, "Historical session stats");
        }
    }

}
