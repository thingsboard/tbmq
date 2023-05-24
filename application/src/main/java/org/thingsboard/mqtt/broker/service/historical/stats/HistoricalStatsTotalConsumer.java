/**
 * Copyright © 2016-2023 The Thingsboard Authors
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
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.actors.client.service.session.ClientSessionService;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.ClientSubscriptionService;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.common.data.kv.BasicTsKvEntry;
import org.thingsboard.mqtt.broker.common.data.kv.LongDataEntry;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvEntry;
import org.thingsboard.mqtt.broker.common.util.DonAsynchron;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.dao.timeseries.TimeseriesService;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueConsumer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.HistoricalDataQueueFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.thingsboard.mqtt.broker.common.util.BrokerConstants.ENTITY_ID_TOTAL;
import static org.thingsboard.mqtt.broker.common.util.BrokerConstants.HISTORICAL_KEYS_STATS;
import static org.thingsboard.mqtt.broker.common.util.BrokerConstants.SESSIONS;
import static org.thingsboard.mqtt.broker.common.util.BrokerConstants.SUBSCRIPTIONS;

@Slf4j
@Component
@RequiredArgsConstructor
public class HistoricalStatsTotalConsumer {

    private ExecutorService consumerExecutorSubscriptionAndSession;
    private ExecutorService consumerExecutorStatsTotal;
    private final HistoricalDataQueueFactory historicalDataQueueFactory;
    private final HistoricalStatsTotalHelper helper;
    private final ServiceInfoProvider serviceInfoProvider;
    private final ClientSessionService clientSessionService;
    private final ClientSubscriptionService clientSubscriptionService;
    private final TimeseriesService timeseriesService;

    private volatile boolean stopped = false;

    @Value("${historical-data-report.enabled:true}")
    private boolean enabled;

    @Value("${historical-data-report.interval}")
    private int interval;

    @Value("${queue.historical-data-total.poll-interval}")
    private long pollDuration;

    private TbQueueConsumer<TbProtoQueueMsg<QueueProtos.ToUsageStatsMsgProto>> consumer;
    @Setter
    private Map<String, TsMsgTotalPair> totalMessageCounter;

    @PostConstruct
    private void init() {
        if (!enabled) return;

        initConsumer();
        initExecutors();
        initTotalMessageMap();
        consumerExecutorStatsTotal.execute(this::processHistoricalDataStatsTotal);
    }

    @Scheduled(cron = "0 0/${historical-data-report.interval} * * * *", zone = "${historical-data-report.zone}")
    private void process() {
        if (enabled) {
            consumerExecutorSubscriptionAndSession.execute(this::processSessionAndSubscriptionStatsTotal);
        }
    }

    private void processHistoricalDataStatsTotal() {
        while (!stopped) {
            try {
                List<TbProtoQueueMsg<QueueProtos.ToUsageStatsMsgProto>> msgs = consumer.poll(pollDuration);
                if (msgs.isEmpty()) {
                    continue;
                }
                for (TbProtoQueueMsg<QueueProtos.ToUsageStatsMsgProto> msg : msgs) {
                    processSaveHistoricalStatsTotal(msg);
                }
                consumer.commitSync();
            } catch (Exception e) {
                if (!stopped) {
                    log.error("Failed to process messages from queue.", e);
                    try {
                        Thread.sleep(pollDuration);
                    } catch (InterruptedException e2) {
                        if (log.isTraceEnabled()) {
                            log.trace("Failed to wait until the server has capacity to handle new requests", e2);
                        }
                    }
                }
            }
        }
        log.info("Historical Data Total Consumer stopped.");
    }

    private void processSessionAndSubscriptionStatsTotal() {
        long clientSessionCount = clientSessionService.getClientSessionsCount();
        long clientSubscriptionCount = clientSubscriptionService.getClientSubscriptionsCount();

        List<TsKvEntry> entries = new ArrayList<>();
        entries.add(new BasicTsKvEntry(System.currentTimeMillis(),
                new LongDataEntry(SESSIONS, clientSessionCount)));
        entries.add(new BasicTsKvEntry(System.currentTimeMillis(),
                new LongDataEntry(SUBSCRIPTIONS, clientSubscriptionCount)));

        ListenableFuture<Void> savedTsFuture = timeseriesService.save(ENTITY_ID_TOTAL, entries);
        DonAsynchron.withCallback(savedTsFuture, unused -> {
            if (log.isTraceEnabled()) {
                log.trace("[{}] Successfully save timeseries entries {}", ENTITY_ID_TOTAL, entries);
            }
        }, throwable -> log.error("[{}] Failed to save timeseries entries {}", ENTITY_ID_TOTAL, entries));
    }

    private void processSaveHistoricalStatsTotal(TbProtoQueueMsg<QueueProtos.ToUsageStatsMsgProto> msg) throws ExecutionException, InterruptedException {
        String key = msg.getValue().getUsageStats().getKey();
        TsMsgTotalPair pair = calculatePairUsingProvidedMsg(msg);
        TsKvEntry tsKvEntry = new BasicTsKvEntry(pair.getTs(), new LongDataEntry(key, pair.getTotalMsgCounter()));

        ListenableFuture<Void> savedTsFuture = timeseriesService.save(ENTITY_ID_TOTAL, tsKvEntry);
        DonAsynchron.withCallback(savedTsFuture, unused -> {
            if (log.isTraceEnabled()) {
                log.trace("[{}] Successfully save timeseries for key {} with value {}", ENTITY_ID_TOTAL, tsKvEntry.getKey(), tsKvEntry.getValue());
            }
        }, throwable -> log.error("[{}] Failed to save timeseries for key {} with value {}", ENTITY_ID_TOTAL, tsKvEntry.getKey(), tsKvEntry.getValue()));
    }

    protected TsMsgTotalPair calculatePairUsingProvidedMsg(TbProtoQueueMsg<QueueProtos.ToUsageStatsMsgProto> msg) {
        TsMsgTotalPair pair = getTotalMessageCounterPair(msg);
        if (pair.getTs() < msg.getValue().getTs()) {
            pair.setTs(msg.getValue().getTs());
            pair.setTotalMsgCounter(msg.getValue().getUsageStats().getValue());
        } else {
            pair.addTotalMsg(msg.getValue().getUsageStats().getValue());
        }
        return pair;
    }


    protected TsMsgTotalPair getTotalMessageCounterPair(TbProtoQueueMsg<QueueProtos.ToUsageStatsMsgProto> msg) {
        String key = msg.getValue().getUsageStats().getKey();
        TsMsgTotalPair msgTotalPair = totalMessageCounter.get(key);
        if (msgTotalPair.isEmpty()) {
            TsKvEntry latest = findLatestTimeseriesForInterval(key, msg.getValue().getTs());
            if (latest != null) {
                setTsAndTotalMessageCount(msgTotalPair, latest.getTs(), latest.getLongValue().orElse(0L));
            } else {
                setTsAndTotalMessageCount(msgTotalPair, msg.getValue().getTs(), 0L);
            }
        }
        return msgTotalPair;
    }

    @SneakyThrows
    private TsKvEntry findLatestTimeseriesForInterval(String key, long msgTs) {
        long intervalInMillis = 60000L * interval;
        List<TsKvEntry> latestTs = timeseriesService.findLatest(ENTITY_ID_TOTAL, List.of(key)).get();

        if (!latestTs.isEmpty()) {
            TsKvEntry latest = latestTs.get(0);
            if (latest != null && msgTs <= latest.getTs() + intervalInMillis) {
                return latest;
            }
        }
        return null;
    }

    private void setTsAndTotalMessageCount(TsMsgTotalPair pair, long initialTimestamp, long initialCounter) {
        pair.setTs(initialTimestamp);
        pair.setTotalMsgCounter(initialCounter);
    }

    private void initTotalMessageMap() {
        if (totalMessageCounter == null) {
            totalMessageCounter = new HashMap<>();
            for (String key : HISTORICAL_KEYS_STATS) {
                totalMessageCounter.put(key, new TsMsgTotalPair());
            }
        }
    }

    private void initConsumer() {
        String topic = helper.getTopic();
        this.consumer = historicalDataQueueFactory.createConsumer(topic, serviceInfoProvider.getServiceId());
        this.consumer.subscribe();
    }

    private void initExecutors() {
        consumerExecutorSubscriptionAndSession = Executors.newSingleThreadExecutor(ThingsBoardThreadFactory.forName("historical-session-subscription-total-consumer"));
        consumerExecutorStatsTotal = Executors.newSingleThreadExecutor(ThingsBoardThreadFactory.forName("historical-data-stats-total-consumer"));
    }

    @PreDestroy
    public void destroy() {
        stopped = true;
        consumerExecutorStatsTotal.shutdownNow();
        consumerExecutorSubscriptionAndSession.shutdown();
        if (consumer != null) {
            consumer.unsubscribeAndClose();
        }
    }

    @Data
    @NoArgsConstructor
    protected static class TsMsgTotalPair {
        private long ts;
        private long totalMsgCounter;

        private void addTotalMsg(long counter) {
            totalMsgCounter += counter;
        }

        private boolean isEmpty() {
            return ts == 0 && totalMsgCounter == 0;
        }
    }
}
