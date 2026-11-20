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
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.kv.Aggregation;
import org.thingsboard.mqtt.broker.common.data.kv.BaseReadTsKvQuery;
import org.thingsboard.mqtt.broker.common.data.kv.BasicTsKvEntry;
import org.thingsboard.mqtt.broker.common.data.kv.DeleteTsKvQuery;
import org.thingsboard.mqtt.broker.common.data.kv.LongDataEntry;
import org.thingsboard.mqtt.broker.common.data.kv.ReadTsKvQuery;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvEntry;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvLatestRemovingResult;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.dao.dictionary.KeyDictionaryDao;
import org.thingsboard.mqtt.broker.dao.rocksdb.RocksDbStore;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreColumnFamily;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreWriteClass;
import org.thingsboard.mqtt.broker.dao.sqlts.AggregationTimeseriesDao;
import org.thingsboard.mqtt.broker.dao.timeseries.TimeseriesLatestDao;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The latest time series values of the standalone edition, in {@code ts_latest}: one entry per (entity, key), keyed by
 * {@link TsKeys#latestKey}, its value as {@link TsValueCodec#encodeLatest} encodes it. A save replaces the entry at
 * once ({@link StoreWriteClass#NEW_DATA}) whatever its timestamp, as the SQL upsert does. The answers are the SQL DAO's
 * ({@code SqlTimeseriesLatestDao}); every future is complete when the call returns, unless a removal rewrites the
 * latest value, and a failure of the store is a failed future.
 */
@Component
@StandaloneComponent
public class RocksDbTimeseriesLatestDao implements TimeseriesLatestDao {

    private static final StoreColumnFamily FAMILY = StoreColumnFamily.TS_LATEST;

    private final RocksDbStore store;
    private final KeyDictionaryDao keyDictionaryDao;
    private final AggregationTimeseriesDao aggregationTimeseriesDao;

    public RocksDbTimeseriesLatestDao(RocksDbStore store, KeyDictionaryDao keyDictionaryDao,
                                      AggregationTimeseriesDao aggregationTimeseriesDao) {
        this.store = store;
        this.keyDictionaryDao = keyDictionaryDao;
        this.aggregationTimeseriesDao = aggregationTimeseriesDao;
    }

    @Override
    public ListenableFuture<Optional<TsKvEntry>> findLatestOpt(String entityId, String key) {
        return Futures.submit(() -> Optional.ofNullable(doFindLatest(entityId, key)), MoreExecutors.directExecutor());
    }

    /**
     * A key without a latest value reads as a {@code null} long at the start of the current minute, as in SQL.
     */
    @Override
    public ListenableFuture<TsKvEntry> findLatest(String entityId, String key) {
        return Futures.submit(() -> {
            TsKvEntry latest = doFindLatest(entityId, key);
            return latest != null ? latest : new BasicTsKvEntry(startOfCurrentMinute(), new LongDataEntry(key, null));
        }, MoreExecutors.directExecutor());
    }

    /**
     * The latest values of the entity, in the order of their key ids. A value whose key id has no dictionary entry is
     * left out, as the SQL join with the dictionary leaves it out.
     */
    @Override
    public ListenableFuture<List<TsKvEntry>> findAllLatest(String entityId) {
        return Futures.submit(() -> store.scan(FAMILY, TsKeys.latestEntityLower(entityId), TsKeys.latestEntityUpper(entityId), iterator -> {
            List<TsKvEntry> entries = new ArrayList<>();
            for (; iterator.isValid(); iterator.next()) {
                String key = keyDictionaryDao.getKey(TsKeys.keyIdOfLatest(iterator.key()));
                if (key != null) {
                    entries.add(TsValueCodec.decodeLatest(key, iterator.value()));
                }
            }
            return entries;
        }), MoreExecutors.directExecutor());
    }

    @Override
    public ListenableFuture<List<Optional<TsKvEntry>>> findAllLatestForNode(String entityId) {
        return findLatestOfKeys(entityId, BrokerConstants.HISTORICAL_KEYS);
    }

    @Override
    public ListenableFuture<List<Optional<TsKvEntry>>> findAllLatestForClient(String entityId) {
        return findLatestOfKeys(entityId, BrokerConstants.CLIENT_SESSION_METRIC_KEYS);
    }

    @Override
    public ListenableFuture<Void> saveLatest(String entityId, TsKvEntry tsKvEntry) {
        return Futures.submit(() -> doSaveLatest(entityId, tsKvEntry), MoreExecutors.directExecutor());
    }

    /**
     * Removes the latest value of the key. As in SQL, the result says it is removed whenever the key has a dictionary
     * entry, whether or not the entity had a value of it.
     */
    @Override
    public ListenableFuture<TsKvLatestRemovingResult> removeLatest(String entityId, String key) {
        return Futures.submit(() -> {
            Integer keyId = keyDictionaryDao.getKeyId(key);
            if (keyId == null) {
                return new TsKvLatestRemovingResult(key, false);
            }
            store.delete(StoreWriteClass.DELETE, FAMILY, TsKeys.latestKey(entityId, keyId));
            return new TsKvLatestRemovingResult(key, true);
        }, MoreExecutors.directExecutor());
    }

    /**
     * Removes the latest value of the key when its timestamp is in {@code [startTs, endTs)}. When the query asks for a
     * rewrite, the newest time series entry before {@code startTs - 1} becomes the latest value and is the data of the
     * result, as in SQL.
     */
    @Override
    public ListenableFuture<TsKvLatestRemovingResult> removeLatest(String entityId, DeleteTsKvQuery query) {
        return Futures.submitAsync(() -> {
            String key = query.getKey();
            TsKvEntry latest = doFindLatest(entityId, key);
            if (latest == null) {
                return Futures.immediateFuture(new TsKvLatestRemovingResult(key, false));
            }
            long ts = latest.getTs();
            if (ts < query.getStartTs() || ts >= query.getEndTs()) {
                return Futures.immediateFuture(new TsKvLatestRemovingResult(key, false));
            }
            store.delete(StoreWriteClass.DELETE, FAMILY, TsKeys.latestKey(entityId, keyDictionaryDao.getOrSaveKeyId(key)));
            if (!query.getRewriteLatestIfDeleted()) {
                return Futures.immediateFuture(new TsKvLatestRemovingResult(key, true));
            }
            return rewriteLatest(entityId, query);
        }, MoreExecutors.directExecutor());
    }

    private ListenableFuture<TsKvLatestRemovingResult> rewriteLatest(String entityId, DeleteTsKvQuery query) {
        long startTs = 0;
        long endTs = query.getStartTs() - 1;
        ReadTsKvQuery newLatestQuery = new BaseReadTsKvQuery(query.getKey(), startTs, endTs, endTs - startTs, 1,
                Aggregation.NONE, BrokerConstants.DESC_ORDER);
        return Futures.transform(aggregationTimeseriesDao.findAllAsync(entityId, newLatestQuery), entries -> {
            if (entries.size() == 1) {
                TsKvEntry entry = entries.get(0);
                doSaveLatest(entityId, entry);
                return new TsKvLatestRemovingResult(entry);
            }
            return new TsKvLatestRemovingResult(query.getKey(), true);
        }, MoreExecutors.directExecutor());
    }

    private ListenableFuture<List<Optional<TsKvEntry>>> findLatestOfKeys(String entityId, List<String> keys) {
        return Futures.submit(() -> {
            List<Optional<TsKvEntry>> entries = new ArrayList<>(keys.size());
            for (String key : keys) {
                entries.add(Optional.ofNullable(doFindLatest(entityId, key)));
            }
            return entries;
        }, MoreExecutors.directExecutor());
    }

    private TsKvEntry doFindLatest(String entityId, String key) {
        Integer keyId = keyDictionaryDao.getKeyId(key);
        if (keyId == null) {
            return null;
        }
        byte[] value = store.get(FAMILY, TsKeys.latestKey(entityId, keyId));
        return value == null ? null : TsValueCodec.decodeLatest(key, value);
    }

    private void doSaveLatest(String entityId, TsKvEntry tsKvEntry) {
        int keyId = keyDictionaryDao.getOrSaveKeyId(tsKvEntry.getKey());
        store.put(StoreWriteClass.NEW_DATA, FAMILY, TsKeys.latestKey(entityId, keyId), TsValueCodec.encodeLatest(tsKvEntry));
    }

    private static long startOfCurrentMinute() {
        return Instant.now().truncatedTo(ChronoUnit.MINUTES).toEpochMilli();
    }

}
