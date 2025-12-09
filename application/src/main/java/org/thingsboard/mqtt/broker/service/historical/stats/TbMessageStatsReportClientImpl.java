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
package org.thingsboard.mqtt.broker.service.historical.stats;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.kv.BasicTsKvEntry;
import org.thingsboard.mqtt.broker.common.data.kv.LongDataEntry;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvEntry;
import org.thingsboard.mqtt.broker.common.util.DonAsynchron;
import org.thingsboard.mqtt.broker.config.HistoricalDataReportProperties;
import org.thingsboard.mqtt.broker.dao.timeseries.TimeseriesService;
import org.thingsboard.mqtt.broker.gen.queue.ToUsageStatsMsgProto;
import org.thingsboard.mqtt.broker.gen.queue.UsageStatsKVProto;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.HistoricalDataQueueFactory;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static java.time.ZoneOffset.UTC;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.MSG_RELATED_HISTORICAL_KEYS;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.MSG_RELATED_HISTORICAL_KEYS_COUNT;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.PROCESSED_BYTES;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.RECEIVED_PUBLISH_MSGS;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.SENT_PUBLISH_MSGS;
import static org.thingsboard.mqtt.broker.service.historical.stats.HistoricalStatsTotalConsumer.ONE_MINUTE_MS;

@Data
@Component
@Slf4j
@RequiredArgsConstructor
public class TbMessageStatsReportClientImpl implements TbMessageStatsReportClient {

    private final HistoricalDataQueueFactory historicalDataQueueFactory;
    private final ServiceInfoProvider serviceInfoProvider;
    private final TimeseriesService timeseriesService;
    private final HistoricalStatsTotalHelper helper;
    private final HistoricalDataReportProperties historicalDataReportProperties;

    private String serviceId;
    private ConcurrentMap<String, AtomicLong> stats;
    private ConcurrentMap<String, ConcurrentMap<String, ClientSessionMetricState>> clientSessionsStats;
    private TbQueueProducer<TbProtoQueueMsg<ToUsageStatsMsgProto>> historicalStatsProducer;

    @PostConstruct
    void init() {
        if (historicalDataReportProperties.isDisabled()) {
            return;
        }

        serviceId = serviceInfoProvider.getServiceId();
        historicalStatsProducer = historicalDataQueueFactory.createProducer(serviceId);
        stats = new ConcurrentHashMap<>();
        clientSessionsStats = new ConcurrentHashMap<>();
        for (String key : MSG_RELATED_HISTORICAL_KEYS) {
            stats.put(key, new AtomicLong(0));
        }
    }

    @PreDestroy
    private void destroy() {
        if (historicalDataReportProperties.isEnabled()) {
            CountDownLatch latch = new CountDownLatch(MSG_RELATED_HISTORICAL_KEYS_COUNT);
            reportAndPersistStats(getStartOfNextInterval(), latch); // sync; block to persist in Kafka
            try {
                if (!latch.await(5, TimeUnit.SECONDS)) {
                    log.warn("[{}] Timeout waiting for historical messages to be sent on shutdown", serviceId);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[{}] Interrupted while flushing stats on shutdown", serviceId, e);
            }
        }
        if (historicalStatsProducer != null) {
            historicalStatsProducer.stop();
        }
    }

    @Scheduled(cron = "#{@historicalDataReportProperties.cron}", zone = "#{@historicalDataReportProperties.zone}")
    public void process() {
        if (historicalDataReportProperties.isEnabled()) {
            long startOfCurrentMinute = getStartOfCurrentMinute();
            reportAndPersistStats(startOfCurrentMinute, null); // async; don't block
            reportClientSessionsStats(startOfCurrentMinute);
        }
    }

    void reportClientSessionsStats(long ts) {
        List<ListenableFuture<List<Void>>> futures = new ArrayList<>();
        clientSessionsStats.forEach((clientId, clientStatsMap) -> {

            List<TsKvEntry> tsKvEntries = clientStatsMap
                    .entrySet()
                    .stream()
                    .filter(entry -> entry.getValue().getAndResetValueChanged())
                    .map(entry -> new BasicTsKvEntry(ts, new LongDataEntry(entry.getKey(), entry.getValue().getCount())))
                    .collect(Collectors.toList());

            if (!tsKvEntries.isEmpty()) {
                futures.add(timeseriesService.saveLatest(clientId, tsKvEntries));
            }
        });
        if (futures.isEmpty()) return;
        DonAsynchron.withCallback(Futures.allAsList(futures),
                lists -> log.debug("Successfully persisted client sessions latest"),
                throwable -> log.warn("Failed to persist client sessions latest", throwable));

        if (log.isDebugEnabled()) {
            log.debug("Top publisher MQTT clients:");
            logTopMqttClients(SENT_PUBLISH_MSGS, "[PUB][clientId={}] {}={}");

            log.debug("Top subscriber MQTT clients:");
            logTopMqttClients(RECEIVED_PUBLISH_MSGS, "[SUB][clientId={}] {}={}");
        }
    }

    private void logTopMqttClients(String metric, String msg) {
        clientSessionsStats.entrySet().stream()
                .map(entry -> {
                    var metricState = entry.getValue().get(metric);
                    long cnt = (metricState != null) ? metricState.getCount() : 0L;
                    return Map.entry(entry.getKey(), cnt);
                })
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .forEach(entry -> TbMessageStatsReportClientImpl.log.debug(msg, entry.getKey(), metric, entry.getValue()));
    }

    void reportAndPersistStats(long ts, CountDownLatch latch) {
        List<ToUsageStatsMsgProto> report = new ArrayList<>(MSG_RELATED_HISTORICAL_KEYS_COUNT);

        for (String key : MSG_RELATED_HISTORICAL_KEYS) {
            long value = stats.get(key).getAndSet(0L);

            UsageStatsKVProto.Builder statsItem = UsageStatsKVProto.newBuilder()
                    .setKey(key)
                    .setValue(value);
            ToUsageStatsMsgProto.Builder statsMsg = ToUsageStatsMsgProto.newBuilder();
            statsMsg.setServiceId(serviceId);
            statsMsg.setTs(ts);
            statsMsg.setUsageStats(statsItem.build());
            report.add(statsMsg.build());
        }

        List<ListenableFuture<Void>> futures = new ArrayList<>(MSG_RELATED_HISTORICAL_KEYS_COUNT);
        report.forEach(statsMsg -> {
                    futures.add(timeseriesService.save(statsMsg.getServiceId(), new BasicTsKvEntry(
                            statsMsg.getTs(), new LongDataEntry(statsMsg.getUsageStats().getKey(), statsMsg.getUsageStats().getValue()))));

                    historicalStatsProducer.send(helper.getTopic(), null, new TbProtoQueueMsg<>(statsMsg), new TbQueueCallback() {
                        @Override
                        public void onSuccess(TbQueueMsgMetadata metadata) {
                            log.trace("[{}] Historical data {} sent successfully.", statsMsg.getServiceId(), statsMsg);
                            if (latch != null) {
                                latch.countDown();
                            }
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            log.warn("[{}] Failed to send message for historical data {}.", statsMsg.getServiceId(), statsMsg, t);
                            if (latch != null) {
                                latch.countDown();
                            }
                        }
                    });
                }
        );

        log.trace("Reporting data usage statistics {}", report);
        DonAsynchron.withCallback(Futures.allAsList(futures),
                unused -> log.trace("[{}] Successfully saved time series for stats report client", serviceId),
                throwable -> log.error("[{}] Failed to save time series", serviceId, throwable));
    }

    @Override
    public void reportStats(String key) {
        if (historicalDataReportProperties.isEnabled()) {
            AtomicLong al = stats.get(key);
            al.incrementAndGet();
        }
    }

    @Override
    public void reportTraffic(long bytes) {
        if (historicalDataReportProperties.isEnabled()) {
            AtomicLong al = stats.get(PROCESSED_BYTES);
            al.addAndGet(bytes);
        }
    }

    @Override
    public void reportClientSendStats(String clientId, int qos) {
        if (historicalDataReportProperties.isEnabled()) {
            reportClientStats(clientId, SENT_PUBLISH_MSGS, BrokerConstants.getQosSentStatsKey(qos));
        }
    }

    @Override
    public void reportClientReceiveStats(String clientId, int qos) {
        if (historicalDataReportProperties.isEnabled()) {
            reportClientStats(clientId, RECEIVED_PUBLISH_MSGS, BrokerConstants.getQosReceivedStatsKey(qos));
        }
    }

    @Override
    public void removeClient(String clientId) {
        if (historicalDataReportProperties.isEnabled()) {
            clientSessionsStats.remove(clientId);
        }
    }

    private void reportClientStats(String clientId, String clientStatsKey, String clientQosStatsKey) {
        var clientStatsMap = clientSessionsStats.computeIfAbsent(clientId, s -> new ConcurrentHashMap<>());

        clientStatsMap.computeIfAbsent(clientStatsKey, s -> newClientSessionMetricState())
                .incrementAndSetValueChanged();

        clientStatsMap.computeIfAbsent(clientQosStatsKey, s -> newClientSessionMetricState())
                .incrementAndSetValueChanged();
    }

    private ClientSessionMetricState newClientSessionMetricState() {
        return ClientSessionMetricState.newClientSessionMetricState();
    }

    private long getStartOfCurrentMinute() {
        return LocalDateTime.now(UTC).atZone(UTC).truncatedTo(ChronoUnit.MINUTES).toInstant().toEpochMilli();
    }

    private long getStartOfNextInterval() {
        return getStartOfCurrentMinute() + historicalDataReportProperties.getInterval() * ONE_MINUTE_MS;
    }

}
