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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.kv.CleanUpResult;
import org.thingsboard.mqtt.broker.common.data.kv.ReadTsKvQuery;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvEntry;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvQuery;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.dao.dictionary.KeyDictionaryDao;
import org.thingsboard.mqtt.broker.dao.rocksdb.RocksDbStore;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreColumnFamily;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreWriteClass;
import org.thingsboard.mqtt.broker.dao.rocksdb.entity.EntityKeys;
import org.thingsboard.mqtt.broker.dao.timeseries.TimeseriesDao;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The time series of the standalone edition, in {@code ts}: one entry per (entity, key, timestamp), keyed as
 * {@link TsKeys} lays out, its value as {@link TsValueCodec} encodes it. A save writes its entry at once
 * ({@link StoreWriteClass#NEW_DATA}) and replaces an entry of the same timestamp, as the SQL upsert does. The reads are
 * {@link RocksDbAggregationTimeseriesDao}'s. Every future is complete when the call returns; a failure of the store is
 * a failed future. The SQL partitioning settings do not apply: the store has no partitions.
 */
@Slf4j
@Component
@StandaloneComponent
public class RocksDbTimeseriesDao implements TimeseriesDao {

    private static final StoreColumnFamily FAMILY = StoreColumnFamily.TS;
    // the ranges of one clean-up batch
    private static final int CLEAN_UP_BATCH_RANGES = 1000;

    private final RocksDbStore store;
    private final KeyDictionaryDao keyDictionaryDao;
    private final RocksDbAggregationTimeseriesDao aggregationDao;

    public RocksDbTimeseriesDao(RocksDbStore store, KeyDictionaryDao keyDictionaryDao, RocksDbAggregationTimeseriesDao aggregationDao) {
        this.store = store;
        this.keyDictionaryDao = keyDictionaryDao;
        this.aggregationDao = aggregationDao;
    }

    /**
     * The results of the queries, one after the other.
     */
    @Override
    public ListenableFuture<List<TsKvEntry>> findAllAsync(String entityId, List<ReadTsKvQuery> queries) {
        return Futures.submit(() -> {
            List<TsKvEntry> entries = new ArrayList<>();
            for (ReadTsKvQuery query : queries) {
                entries.addAll(aggregationDao.find(entityId, query));
            }
            return entries;
        }, MoreExecutors.directExecutor());
    }

    @Override
    public ListenableFuture<Void> save(String entityId, TsKvEntry tsKvEntry) {
        return Futures.submit(() -> {
            int keyId = keyDictionaryDao.getOrSaveKeyId(tsKvEntry.getKey());
            store.put(StoreWriteClass.NEW_DATA, FAMILY, TsKeys.tsKey(entityId, keyId, tsKvEntry.getTs()), TsValueCodec.encode(tsKvEntry));
        }, MoreExecutors.directExecutor());
    }

    /**
     * Deletes the entries of the key in {@code [startTs, endTs)}.
     */
    @Override
    public ListenableFuture<Void> remove(String entityId, TsKvQuery query) {
        return Futures.submit(() -> {
            Integer keyId = keyDictionaryDao.getKeyId(query.getKey());
            if (keyId == null) {
                return;
            }
            byte[] lower = TsKeys.tsLower(entityId, keyId, query.getStartTs());
            byte[] upper = TsKeys.tsUpper(entityId, keyId, query.getEndTs());
            if (Arrays.compareUnsigned(lower, upper) < 0) {
                store.write(StoreWriteClass.DELETE, batch -> batch.deleteRange(FAMILY, lower, upper));
            }
        }, MoreExecutors.directExecutor());
    }

    /**
     * Deletes every entry older than {@code now - systemTtl}, of every (entity, key) pair in {@code ts}, whether or not
     * the pair still has a latest value or the key a dictionary entry. The pairs are found by skipping through the
     * family: after the old entries of a pair are counted, the scan seeks past the pair. The old entries of each pair
     * are then range-deleted. The result counts the deleted entries and no partitions, the store having none; a
     * failure is logged and counts nothing, as the SQL DAO does.
     */
    @Override
    public CleanUpResult cleanUp(long systemTtl) {
        log.info("Going to cleanup old timeseries data using ttl: {}s", systemTtl);
        try {
            long deleted = cleanUpData(System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(systemTtl));
            log.info("Total telemetry removed stats by TTL {}!", deleted);
            return new CleanUpResult(0, deleted);
        } catch (Exception e) {
            log.error("Failed to execute cleanup using ttl {}", systemTtl, e);
            return new CleanUpResult(0, 0);
        }
    }

    private long cleanUpData(long expirationTime) {
        if (expirationTime <= 0) {
            return 0;
        }
        // each range is {the pair, the key of the expiration time in the pair}
        List<byte[][]> ranges = new ArrayList<>();
        long deleted = store.scan(FAMILY, null, null, iterator -> {
            long count = 0;
            while (iterator.isValid()) {
                byte[] pair = TsKeys.pairOf(iterator.key());
                byte[] expired = TsKeys.withTs(pair, expirationTime);
                long pairCount = 0;
                for (; iterator.isValid() && Arrays.compareUnsigned(iterator.key(), expired) < 0; iterator.next()) {
                    pairCount++;
                }
                if (pairCount > 0) {
                    ranges.add(new byte[][]{pair, expired});
                    count += pairCount;
                }
                iterator.seek(EntityKeys.upperBound(pair));
            }
            return count;
        });
        for (int from = 0; from < ranges.size(); from += CLEAN_UP_BATCH_RANGES) {
            List<byte[][]> chunk = ranges.subList(from, Math.min(from + CLEAN_UP_BATCH_RANGES, ranges.size()));
            store.write(StoreWriteClass.DELETE, batch -> chunk.forEach(range -> batch.deleteRange(FAMILY, range[0], range[1])));
        }
        return deleted;
    }

}
