/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.kv.BasicTsKvEntry;
import org.thingsboard.mqtt.broker.common.data.kv.LongDataEntry;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvEntry;
import org.thingsboard.mqtt.broker.common.util.DonAsynchron;
import org.thingsboard.mqtt.broker.dao.timeseries.TimeseriesService;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos.ToUsageStatsMsgProto;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos.UsageStatsKVProto;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.HistoricalDataQueueFactory;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static java.time.ZoneOffset.UTC;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.MSG_RELATED_HISTORICAL_KEYS;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.PROCESSED_BYTES;

@Data
@Component
@Slf4j
@RequiredArgsConstructor
public class TbMessageStatsReportClientImpl implements TbMessageStatsReportClient {

    @Value("${historical-data-report.enabled:true}")
    private boolean enabled;

    @Value("${historical-data-report.interval:5}")
    private long interval;

    private final HistoricalDataQueueFactory historicalDataQueueFactory;
    private final ServiceInfoProvider serviceInfoProvider;
    private final TimeseriesService timeseriesService;
    private final HistoricalStatsTotalHelper helper;

    private String serviceId;
    private ConcurrentMap<String, AtomicLong> stats;
    private ConcurrentMap<String, ConcurrentMap<String, ClientSessionMetricState>> clientSessionsStats;
    private TbQueueProducer<TbProtoQueueMsg<QueueProtos.ToUsageStatsMsgProto>> historicalStatsProducer;

    @PostConstruct
    void init() {
        if (!enabled) return;
        validateIntervalAndThrowExceptionOnInvalid();

        serviceId = serviceInfoProvider.getServiceId();
        historicalStatsProducer = historicalDataQueueFactory.createProducer(serviceId);
        stats = new ConcurrentHashMap<>();
        clientSessionsStats = new ConcurrentHashMap<>();
        for (String key : MSG_RELATED_HISTORICAL_KEYS) {
            stats.put(key, new AtomicLong(0));
        }
    }

    @Scheduled(cron = "0 0/${historical-data-report.interval} * * * *", zone = "${historical-data-report.zone}")
    private void process() {
        if (enabled) {
            long startOfCurrentMinute = getStartOfCurrentMinute();
            reportAndPersistStats(startOfCurrentMinute);
            reportClientSessionsStats(startOfCurrentMinute);
        }
    }

    void reportClientSessionsStats(long ts) {
        List<ListenableFuture<List<Void>>> futures = new ArrayList<>();
        clientSessionsStats.forEach((clientId, clientStatsMap) -> {

            List<TsKvEntry> tsKvEntries = clientStatsMap
                    .entrySet()
                    .stream()
                    .filter(entry -> entry.getValue().isValueChangedSinceLastUpdate())
                    .peek(entry -> entry.getValue().setValueChangedSinceLastUpdate(false))
                    .map(entry -> new BasicTsKvEntry(ts, new LongDataEntry(entry.getKey(), entry.getValue().getCounter().get())))
                    .collect(Collectors.toList());

            if (!tsKvEntries.isEmpty()) {
                futures.add(timeseriesService.saveLatest(clientId, tsKvEntries));
            }
        });
        if (futures.isEmpty()) return;
        DonAsynchron.withCallback(Futures.allAsList(futures),
                lists -> log.debug("Successfully persisted client sessions latest"),
                throwable -> log.warn("Failed to persist client sessions latest", throwable));
    }

    void reportAndPersistStats(long ts) {
        List<ToUsageStatsMsgProto> report = new ArrayList<>();

        for (String key : MSG_RELATED_HISTORICAL_KEYS) {
            long value = stats.get(key).get();

            UsageStatsKVProto.Builder statsItem = UsageStatsKVProto.newBuilder()
                    .setKey(key)
                    .setValue(value);
            ToUsageStatsMsgProto.Builder statsMsg = ToUsageStatsMsgProto.newBuilder();
            statsMsg.setServiceId(serviceId);
            statsMsg.setTs(ts);
            statsMsg.setUsageStats(statsItem.build());
            report.add(statsMsg.build());
            stats.get(key).set(0);
        }

        List<ListenableFuture<Void>> futures = new ArrayList<>();
        report.forEach(statsMsg -> {
                    futures.add(timeseriesService.save(statsMsg.getServiceId(), new BasicTsKvEntry(
                            statsMsg.getTs(), new LongDataEntry(statsMsg.getUsageStats().getKey(), statsMsg.getUsageStats().getValue()))));

                    historicalStatsProducer.send(helper.getTopic(), null, new TbProtoQueueMsg<>(statsMsg), new TbQueueCallback() {
                        @Override
                        public void onSuccess(TbQueueMsgMetadata metadata) {
                            if (log.isTraceEnabled()) {
                                log.trace("[{}] Historical data {} sent successfully.", statsMsg.getServiceId(), statsMsg);
                            }
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            log.warn("[{}] Failed to send message for historical data {}.", statsMsg.getServiceId(), statsMsg, t);
                        }
                    });
                }
        );

        if (!report.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("Reporting data usage statistics {}", report.size());
            }
            DonAsynchron.withCallback(Futures.allAsList(futures), unused -> {
                if (log.isTraceEnabled()) {
                    log.trace("[{}] Successfully saved timeseries for stats report client", serviceId);
                }
            }, throwable -> log.error("[{}] Failed to save timeseries", serviceId, throwable));
        }
    }

    @Override
    public void reportStats(String key) {
        if (enabled) {
            AtomicLong al = stats.get(key);
            al.incrementAndGet();
        }
    }

    @Override
    public void reportTraffic(long bytes) {
        if (enabled) {
            AtomicLong al = stats.get(PROCESSED_BYTES);
            al.addAndGet(bytes);
        }
    }

    @Override
    public void reportClientSendStats(String clientId, int qos) {
        if (enabled) {
            reportClientStats(clientId, BrokerConstants.SENT_PUBLISH_MSGS, BrokerConstants.getQosSentStatsKey(qos));
        }
    }

    @Override
    public void reportClientReceiveStats(String clientId, int qos) {
        if (enabled) {
            reportClientStats(clientId, BrokerConstants.RECEIVED_PUBLISH_MSGS, BrokerConstants.getQosReceivedStatsKey(qos));
        }
    }

    @Override
    public void removeClient(String clientId) {
        if (enabled) {
            clientSessionsStats.remove(clientId);
        }
    }

    private void reportClientStats(String clientId, String clientStatsKey, String clientQosStatsKey) {
        var clientStatsMap = clientSessionsStats.computeIfAbsent(clientId, s -> new ConcurrentHashMap<>());

        ClientSessionMetricState metricState = clientStatsMap.computeIfAbsent(clientStatsKey, s -> newClientSessionMetricState());
        metricState.getCounter().incrementAndGet();
        metricState.setValueChangedSinceLastUpdate(true);

        ClientSessionMetricState qosMetricState = clientStatsMap.computeIfAbsent(clientQosStatsKey, s -> newClientSessionMetricState());
        qosMetricState.getCounter().incrementAndGet();
        qosMetricState.setValueChangedSinceLastUpdate(true);
    }

    private ClientSessionMetricState newClientSessionMetricState() {
        return ClientSessionMetricState.newClientSessionMetricState();
    }

    void validateIntervalAndThrowExceptionOnInvalid() {
        if (interval < 1 || interval > 60) {
            String message = String.format("The interval value provided is not within the correct range of 1 to 60 minutes, current value %d", interval);
            log.error(message);
            throw new RuntimeException(message);
        }
    }

    private long getStartOfCurrentMinute() {
        return LocalDateTime.now(UTC).atZone(UTC).truncatedTo(ChronoUnit.MINUTES).toInstant().toEpochMilli();
    }

}
