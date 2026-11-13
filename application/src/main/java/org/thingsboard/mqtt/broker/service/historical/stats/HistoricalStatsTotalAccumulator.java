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

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.kv.BasicTsKvEntry;
import org.thingsboard.mqtt.broker.common.data.kv.LongDataEntry;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvEntry;
import org.thingsboard.mqtt.broker.config.HistoricalDataReportProperties;
import org.thingsboard.mqtt.broker.dao.timeseries.TimeseriesService;
import org.thingsboard.mqtt.broker.gen.queue.ToUsageStatsMsgProto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.ENTITY_ID_TOTAL;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.MSG_RELATED_HISTORICAL_KEYS;

/**
 * Sums the per-node usage entries of one interval into the broker-wide total of each message-related key. The held
 * (ts, total) of a key is seeded from the latest stored total when that is still within one interval, so a restart
 * inside an interval adds to the stored total instead of replacing it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HistoricalStatsTotalAccumulator {

    public static final long ONE_MINUTE_MS = 60000L;

    private final TimeseriesService timeseriesService;
    private final HistoricalDataReportProperties historicalDataReportProperties;

    private final Map<String, TsMsgTotalPair> totalStatsMap = initTotalMessageMap();

    /**
     * @return the total entry to save under the total entity, or {@code null} when the key is not a historical key
     */
    public synchronized TsKvEntry accumulate(ToUsageStatsMsgProto msg) {
        String key = msg.getUsageStats().getKey();
        if (!totalStatsMap.containsKey(key)) {
            // Unknown/removed historical key (e.g. a 'processedBytes' message from a not-yet-upgraded node
            // during a rolling upgrade). Ignore it so a single stale message can't NPE and poison the whole
            // aggregation loop — an uncommitted batch would otherwise be re-polled forever.
            log.debug("[{}] Ignoring historical stats for unknown key {}", ENTITY_ID_TOTAL, key);
            return null;
        }
        long msgTs = msg.getTs();
        TsMsgTotalPair pair = calculatePairUsingProvidedMsg(msg);

        long ts;
        long value;
        if (pair.getTs() > msgTs) {
            // Message is from an older interval (e.g. after interval change or shutdown with future ts).
            // Save it at its own timestamp to avoid data loss, without affecting the current pair.
            ts = msgTs;
            value = msg.getUsageStats().getValue();
            log.warn("[{}] Received out-of-order historical stats for key {} at ts {} while current pair ts is {}. " +
                    "Saving at message ts directly. This may happen after a restart with a different reporting interval.", ENTITY_ID_TOTAL, key, ts, pair.getTs());
        } else {
            ts = pair.getTs();
            value = pair.getTotalMsgCounter();
        }
        return new BasicTsKvEntry(ts, new LongDataEntry(key, value));
    }

    // Package-private for the same-package unit test; not an extension point.
    TsMsgTotalPair calculatePairUsingProvidedMsg(ToUsageStatsMsgProto msg) {
        TsMsgTotalPair pair = getTotalMessageCounterPair(msg);
        long msgTs = msg.getTs();
        if (pair.getTs() < msgTs) {
            pair.setTs(msgTs);
            pair.setTotalMsgCounter(msg.getUsageStats().getValue());
        } else if (pair.getTs() == msgTs) {
            pair.addTotalMsg(msg.getUsageStats().getValue());
        }
        // else pair.ts > msgTs: stale message from an older interval — skip to avoid inflating the current total
        return pair;
    }

    TsMsgTotalPair getTotalMessageCounterPair(ToUsageStatsMsgProto msg) {
        String key = msg.getUsageStats().getKey();
        TsMsgTotalPair msgTotalPair = totalStatsMap.get(key);
        if (msgTotalPair.isEmpty()) {
            TsKvEntry latest = findLatestTimeseriesForInterval(key, msg.getTs());
            if (latest != null) {
                setTsAndTotalMessageCount(msgTotalPair, latest.getTs(), latest.getLongValue().orElse(0L));
            } else {
                setTsAndTotalMessageCount(msgTotalPair, msg.getTs(), 0L);
            }
        }
        return msgTotalPair;
    }

    @SneakyThrows
    private TsKvEntry findLatestTimeseriesForInterval(String key, long msgTs) {
        long intervalMs = ONE_MINUTE_MS * historicalDataReportProperties.getInterval();
        List<TsKvEntry> latestTs = timeseriesService.findLatest(ENTITY_ID_TOTAL, List.of(key)).get();

        if (!latestTs.isEmpty()) {
            TsKvEntry latest = latestTs.get(0);
            if (latest != null && msgTs <= latest.getTs() + intervalMs) {
                return latest;
            }
        }
        return null;
    }

    private void setTsAndTotalMessageCount(TsMsgTotalPair pair, long ts, long counter) {
        pair.setTs(ts);
        pair.setTotalMsgCounter(counter);
    }

    private static Map<String, TsMsgTotalPair> initTotalMessageMap() {
        Map<String, TsMsgTotalPair> totalStatsMap = new HashMap<>();
        for (String key : MSG_RELATED_HISTORICAL_KEYS) {
            totalStatsMap.put(key, new TsMsgTotalPair());
        }
        return totalStatsMap;
    }

    @Data
    @NoArgsConstructor
    static class TsMsgTotalPair {

        private long ts;
        private long totalMsgCounter;

        private void addTotalMsg(long counter) {
            totalMsgCounter += counter;
        }

        private boolean isEmpty() {
            return ts == 0;
        }
    }
}
