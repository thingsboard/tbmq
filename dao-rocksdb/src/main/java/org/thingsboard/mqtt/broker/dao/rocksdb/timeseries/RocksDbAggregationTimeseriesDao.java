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
package org.thingsboard.mqtt.broker.dao.rocksdb.timeseries;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import org.rocksdb.RocksIterator;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.kv.Aggregation;
import org.thingsboard.mqtt.broker.common.data.kv.BasicTsKvEntry;
import org.thingsboard.mqtt.broker.common.data.kv.DoubleDataEntry;
import org.thingsboard.mqtt.broker.common.data.kv.KvEntry;
import org.thingsboard.mqtt.broker.common.data.kv.LongDataEntry;
import org.thingsboard.mqtt.broker.common.data.kv.ReadTsKvQuery;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvEntry;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.dao.dictionary.KeyDictionaryDao;
import org.thingsboard.mqtt.broker.dao.rocksdb.RocksDbStore;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreColumnFamily;
import org.thingsboard.mqtt.broker.dao.sqlts.AggregationTimeseriesDao;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * The time series reads of the standalone edition, from {@code ts}: a read of the raw entries
 * ({@link Aggregation#NONE}) and the aggregations, answered as the SQL DAO ({@code AbstractChunkedAggregationTimeseriesDao}
 * over {@code TsKvRepository}) answers them. Every future is complete when the call returns.
 * <ul>
 *   <li>{@link Aggregation#NONE}: the entries in {@code [startTs, endTs)}, at most {@code limit}, the oldest first for
 *   the order {@code ASC} and the newest first for {@code DESC}, the order read without regard to case.</li>
 *   <li>The aggregations: one scan of {@code [startTs, max(startTs + 1, endTs))}, cut into buckets of {@code interval}
 *   from {@code startTs}, the last one shorter when the range ends inside it. A bucket without entries gives no result;
 *   the result of one with entries has the timestamp {@code start + (end - start) / 2} and, as the SQL queries read the
 *   long column only, counts only the long values: AVG is a double, their sum divided by their count or 0.0 when there
 *   are none; SUM is a long, 0 when there are none; MIN and MAX are longs and give no result when there are none; COUNT
 *   is a long, the number of long values. The results are in the order of the buckets, reversed for the order
 *   {@code DESC} exactly.</li>
 * </ul>
 */
@Component
@StandaloneComponent
public class RocksDbAggregationTimeseriesDao implements AggregationTimeseriesDao {

    private static final StoreColumnFamily FAMILY = StoreColumnFamily.TS;

    private final RocksDbStore store;
    private final KeyDictionaryDao keyDictionaryDao;

    public RocksDbAggregationTimeseriesDao(RocksDbStore store, KeyDictionaryDao keyDictionaryDao) {
        this.store = store;
        this.keyDictionaryDao = keyDictionaryDao;
    }

    @Override
    public ListenableFuture<List<TsKvEntry>> findAllAsync(String entityId, ReadTsKvQuery query) {
        return Futures.submit(() -> find(entityId, query), MoreExecutors.directExecutor());
    }

    /**
     * @throws IllegalArgumentException for a read of the raw entries with a limit below 1 or an order other than
     *                                  {@code ASC} and {@code DESC}, as the SQL DAO refuses them, and for an aggregation
     *                                  with an interval below 1, which the SQL DAO never finishes
     */
    List<TsKvEntry> find(String entityId, ReadTsKvQuery query) {
        if (query.getAggregation() == Aggregation.NONE) {
            return findWithLimit(entityId, query);
        }
        return aggregate(entityId, query);
    }

    private List<TsKvEntry> findWithLimit(String entityId, ReadTsKvQuery query) {
        int limit = query.getLimit();
        if (limit < 1) {
            throw new IllegalArgumentException("The limit of a time series read must be above 0, got " + limit);
        }
        boolean descending = isDescending(query.getOrder());
        String key = query.getKey();
        Integer keyId = keyDictionaryDao.getKeyId(key);
        if (keyId == null) {
            return new ArrayList<>();
        }
        byte[] lower = TsKeys.tsLower(entityId, keyId, query.getStartTs());
        byte[] upper = TsKeys.tsUpper(entityId, keyId, query.getEndTs());
        if (Arrays.compareUnsigned(lower, upper) >= 0) {
            return new ArrayList<>();
        }
        return store.scan(FAMILY, lower, upper, iterator -> {
            List<TsKvEntry> entries = new ArrayList<>();
            if (descending) {
                iterator.seekToLast();
            }
            for (; iterator.isValid() && entries.size() < limit; step(iterator, descending)) {
                entries.add(new BasicTsKvEntry(TsKeys.tsOf(iterator.key()), TsValueCodec.decode(key, iterator.value())));
            }
            return entries;
        });
    }

    private List<TsKvEntry> aggregate(String entityId, ReadTsKvQuery query) {
        long interval = query.getInterval();
        if (interval < 1) {
            throw new IllegalArgumentException("The interval of a time series aggregation must be above 0, got " + interval);
        }
        Aggregation aggregation = query.getAggregation();
        String key = query.getKey();
        Integer keyId = keyDictionaryDao.getKeyId(key);
        if (keyId == null) {
            return new ArrayList<>();
        }
        long startTs = query.getStartTs();
        long endPeriod = Math.max(startTs + 1, query.getEndTs());
        byte[] lower = TsKeys.tsLower(entityId, keyId, startTs);
        byte[] upper = TsKeys.tsUpper(entityId, keyId, endPeriod);
        if (Arrays.compareUnsigned(lower, upper) >= 0) {
            return new ArrayList<>();
        }
        List<TsKvEntry> results = store.scan(FAMILY, lower, upper, iterator -> {
            List<TsKvEntry> found = new ArrayList<>();
            Bucket bucket = null;
            for (; iterator.isValid(); iterator.next()) {
                long ts = TsKeys.tsOf(iterator.key());
                if (bucket == null || ts >= bucket.end) {
                    if (bucket != null) {
                        bucket.result(key, aggregation).ifPresent(found::add);
                    }
                    long bucketStart = startTs + (ts - startTs) / interval * interval;
                    // saturated: the bucket ends with the range even when start + interval overflows
                    long bucketEnd = interval >= endPeriod - bucketStart ? endPeriod : bucketStart + interval;
                    bucket = new Bucket(bucketStart, bucketEnd);
                }
                bucket.add(TsValueCodec.decode(key, iterator.value()));
            }
            if (bucket != null) {
                bucket.result(key, aggregation).ifPresent(found::add);
            }
            return found;
        });
        if (BrokerConstants.DESC_ORDER.equals(query.getOrder())) {
            Collections.reverse(results);
        }
        return results;
    }

    private static boolean isDescending(String order) {
        if (BrokerConstants.DESC_ORDER.equalsIgnoreCase(order)) {
            return true;
        }
        if (BrokerConstants.ASC_ORDER.equalsIgnoreCase(order)) {
            return false;
        }
        throw new IllegalArgumentException("The order of a time series read must be ASC or DESC, got " + order);
    }

    private static void step(RocksIterator iterator, boolean descending) {
        if (descending) {
            iterator.prev();
        } else {
            iterator.next();
        }
    }

    /**
     * The entries of one bucket, as the SQL aggregation queries see them: rows, of which only the long values count.
     */
    private static final class Bucket {

        private final long start;
        private final long end;
        private long longCount;
        private long sum;
        private long min = Long.MAX_VALUE;
        private long max = Long.MIN_VALUE;

        private Bucket(long start, long end) {
            this.start = start;
            this.end = end;
        }

        private void add(KvEntry entry) {
            Optional<Long> value = entry.getLongValue();
            if (value.isPresent()) {
                long v = value.get();
                longCount++;
                sum += v;
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
        }

        /**
         * Called only on a bucket with at least one entry.
         */
        private Optional<TsKvEntry> result(String key, Aggregation aggregation) {
            KvEntry value = switch (aggregation) {
                case AVG -> new DoubleDataEntry(key, longCount > 0 ? (double) sum / longCount : 0.0);
                case SUM -> new LongDataEntry(key, sum);
                case MIN -> longCount > 0 ? new LongDataEntry(key, min) : null;
                case MAX -> longCount > 0 ? new LongDataEntry(key, max) : null;
                case COUNT -> new LongDataEntry(key, longCount);
                case NONE -> throw new IllegalArgumentException("Not supported aggregation type: " + aggregation);
            };
            return value == null ? Optional.empty() : Optional.of(new BasicTsKvEntry(start + (end - start) / 2, value));
        }

    }

}
