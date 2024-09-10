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
package org.thingsboard.mqtt.broker.dao.sqlts;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.kv.Aggregation;
import org.thingsboard.mqtt.broker.common.data.kv.BaseReadTsKvQuery;
import org.thingsboard.mqtt.broker.common.data.kv.BasicTsKvEntry;
import org.thingsboard.mqtt.broker.common.data.kv.DeleteTsKvQuery;
import org.thingsboard.mqtt.broker.common.data.kv.LongDataEntry;
import org.thingsboard.mqtt.broker.common.data.kv.ReadTsKvQuery;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvEntry;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvLatestRemovingResult;
import org.thingsboard.mqtt.broker.common.util.BrokerConstants;
import org.thingsboard.mqtt.broker.dao.DaoUtil;
import org.thingsboard.mqtt.broker.dao.dictionary.KeyDictionaryDao;
import org.thingsboard.mqtt.broker.dao.model.sqlts.AbstractTsKvEntity;
import org.thingsboard.mqtt.broker.dao.model.sqlts.latest.TsKvLatestCompositeKey;
import org.thingsboard.mqtt.broker.dao.model.sqlts.latest.TsKvLatestEntity;
import org.thingsboard.mqtt.broker.dao.sql.SqlQueueStatsManager;
import org.thingsboard.mqtt.broker.dao.sql.TbSqlBlockingQueuePool;
import org.thingsboard.mqtt.broker.dao.sql.TbSqlQueueParams;
import org.thingsboard.mqtt.broker.dao.sqlts.insert.latest.InsertLatestTsRepository;
import org.thingsboard.mqtt.broker.dao.sqlts.latest.TsKvLatestRepository;
import org.thingsboard.mqtt.broker.dao.timeseries.TimeseriesLatestDao;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Slf4j
@Component
public class SqlTimeseriesLatestDao extends BaseAbstractSqlTimeseriesDao implements TimeseriesLatestDao {

    private static final String DESC_ORDER = "DESC";

    @Autowired
    private TsKvLatestRepository tsKvLatestRepository;

    @Autowired
    protected AggregationTimeseriesDao aggregationTimeseriesDao;

    @Autowired
    private InsertLatestTsRepository insertLatestTsRepository;

    @Autowired(required = false)
    private SqlQueueStatsManager statsManager;

    @Autowired
    private KeyDictionaryDao keyDictionaryDao;

    @Value("${sql.ts_latest.batch_size:1000}")
    private int tsLatestBatchSize;

    @Value("${sql.ts_latest.batch_max_delay:100}")
    private long tsLatestMaxDelay;

    @Value("${sql.ts_latest.batch_threads:4}")
    private int tsLatestBatchThreads;

    @Value("${sql.batch_sort:true}")
    private boolean batchSortEnabled;

    private TbSqlBlockingQueuePool<TsKvLatestEntity> tsLatestQueue;

    @PostConstruct
    protected void init() {
        TbSqlQueueParams tsLatestParams = TbSqlQueueParams.builder()
                .queueName("LatestTimeseriesQueue")
                .batchSize(tsLatestBatchSize)
                .maxDelay(tsLatestMaxDelay)
                .batchSortEnabled(batchSortEnabled)
                .build();

        Function<TsKvLatestEntity, Integer> hashcodeFunction = entity -> entity.getEntityId().hashCode();
        Comparator<TsKvLatestEntity> tsKvEntityComparator = Comparator.comparing((Function<TsKvLatestEntity, String>) AbstractTsKvEntity::getEntityId)
                .thenComparingInt(AbstractTsKvEntity::getKey);

        tsLatestQueue = TbSqlBlockingQueuePool.<TsKvLatestEntity>builder()
                .params(tsLatestParams)
                .maxThreads(tsLatestBatchThreads)
                .queueIndexHashFunction(hashcodeFunction)
                .processingFunction(v -> insertLatestTsRepository.saveOrUpdate(v))
                .statsManager(statsManager)
                .batchUpdateComparator(tsKvEntityComparator)
                .build();

        tsLatestQueue.init();
    }

    @PreDestroy
    protected void destroy() {
        if (tsLatestQueue != null) {
            tsLatestQueue.destroy();
        }
    }

    @Override
    public ListenableFuture<Void> saveLatest(String entityId, TsKvEntry tsKvEntry) {
        return getSaveLatestFuture(entityId, tsKvEntry);
    }

    @Override
    public ListenableFuture<TsKvLatestRemovingResult> removeLatest(String entityId, String key) {
        Integer keyId = keyDictionaryDao.getKeyId(key);
        if (keyId == null) {
            return Futures.immediateFuture(new TsKvLatestRemovingResult(key, false));
        }
        ListenableFuture<?> future = service.submit(() -> tsKvLatestRepository.deleteById(new TsKvLatestCompositeKey(entityId, keyId)));
        return Futures.transform(future, v -> new TsKvLatestRemovingResult(key, true), MoreExecutors.directExecutor());
    }

    @Override
    public ListenableFuture<TsKvLatestRemovingResult> removeLatest(String entityId, DeleteTsKvQuery query) {
        return getRemoveLatestFuture(entityId, query);
    }

    @Override
    public ListenableFuture<Optional<TsKvEntry>> findLatestOpt(String entityId, String key) {
        return service.submit(() -> Optional.ofNullable(doFindLatest(entityId, key)));
    }

    @Override
    public ListenableFuture<TsKvEntry> findLatest(String entityId, String key) {
        return service.submit(() -> getLatestTsKvEntry(entityId, key));
    }

    @Override
    public ListenableFuture<List<TsKvEntry>> findAllLatest(String entityId) {
        List<ListenableFuture<TsKvEntry>> futures = new ArrayList<>(BrokerConstants.HISTORICAL_KEYS.size());
        for (String key : BrokerConstants.HISTORICAL_KEYS) {
            futures.add(findLatest(entityId, key));
        }
        return Futures.allAsList(futures);
    }

    private ListenableFuture<TsKvLatestRemovingResult> getNewLatestEntryFuture(String entityId, DeleteTsKvQuery query) {
        ListenableFuture<List<TsKvEntry>> future = findNewLatestEntryFuture(entityId, query);
        return Futures.transformAsync(future, entryList -> {
            if (entryList.size() == 1) {
                TsKvEntry entry = entryList.get(0);
                return Futures.transform(getSaveLatestFuture(entityId, entry), v -> new TsKvLatestRemovingResult(entry), MoreExecutors.directExecutor());
            } else {
                log.trace("Could not find new latest value for [{}], key - {}", entityId, query.getKey());
            }
            return Futures.immediateFuture(new TsKvLatestRemovingResult(query.getKey(), true));
        }, service);
    }

    private ListenableFuture<List<TsKvEntry>> findNewLatestEntryFuture(String entityId, DeleteTsKvQuery query) {
        long startTs = 0;
        long endTs = query.getStartTs() - 1;
        ReadTsKvQuery findNewLatestQuery = new BaseReadTsKvQuery(query.getKey(), startTs, endTs, endTs - startTs, 1,
                Aggregation.NONE, DESC_ORDER);
        return aggregationTimeseriesDao.findAllAsync(entityId, findNewLatestQuery);
    }

    protected TsKvEntry doFindLatest(String entityId, String key) {
        TsKvLatestCompositeKey compositeKey =
                new TsKvLatestCompositeKey(
                        entityId,
                        keyDictionaryDao.getOrSaveKeyId(key));
        Optional<TsKvLatestEntity> entry = tsKvLatestRepository.findById(compositeKey);
        if (entry.isPresent()) {
            TsKvLatestEntity tsKvLatestEntity = entry.get();
            tsKvLatestEntity.setStrKey(key);
            return DaoUtil.getData(tsKvLatestEntity);
        } else {
            return null;
        }
    }

    protected ListenableFuture<TsKvLatestRemovingResult> getRemoveLatestFuture(String entityId, DeleteTsKvQuery query) {
        ListenableFuture<TsKvEntry> latestFuture = service.submit(() -> doFindLatest(entityId, query.getKey()));
        return Futures.transformAsync(latestFuture, latest -> {
            if (latest == null) {
                return Futures.immediateFuture(new TsKvLatestRemovingResult(query.getKey(), false));
            }
            boolean isRemoved = false;
            long ts = latest.getTs();
            if (ts >= query.getStartTs() && ts < query.getEndTs()) {
                TsKvLatestEntity latestEntity = new TsKvLatestEntity();
                latestEntity.setEntityId(entityId);
                latestEntity.setKey(keyDictionaryDao.getOrSaveKeyId(query.getKey()));
                tsKvLatestRepository.delete(latestEntity);
                isRemoved = true;
                if (query.getRewriteLatestIfDeleted()) {
                    return getNewLatestEntryFuture(entityId, query);
                }
            }
            return Futures.immediateFuture(new TsKvLatestRemovingResult(query.getKey(), isRemoved));
        }, MoreExecutors.directExecutor());
    }

    protected ListenableFuture<Void> getSaveLatestFuture(String entityId, TsKvEntry tsKvEntry) {
        TsKvLatestEntity latestEntity = new TsKvLatestEntity();
        latestEntity.setEntityId(entityId);
        latestEntity.setTs(tsKvEntry.getTs());
        latestEntity.setKey(keyDictionaryDao.getOrSaveKeyId(tsKvEntry.getKey()));
        latestEntity.setLongValue(tsKvEntry.getLongValue().orElse(null));

        return tsLatestQueue.add(latestEntity);
    }

    private TsKvEntry getLatestTsKvEntry(String entityId, String key) {
        TsKvEntry latest = doFindLatest(entityId, key);
        if (latest == null) {
            latest = new BasicTsKvEntry(System.currentTimeMillis(), new LongDataEntry(key, null));
        }
        return latest;
    }

}
