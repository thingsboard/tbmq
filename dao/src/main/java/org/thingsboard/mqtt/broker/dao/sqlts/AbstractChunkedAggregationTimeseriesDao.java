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
package org.thingsboard.mqtt.broker.dao.sqlts;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.common.data.kv.Aggregation;
import org.thingsboard.mqtt.broker.common.data.kv.BaseReadTsKvQuery;
import org.thingsboard.mqtt.broker.common.data.kv.DeleteTsKvQuery;
import org.thingsboard.mqtt.broker.common.data.kv.ReadTsKvQuery;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvEntry;
import org.thingsboard.mqtt.broker.common.util.BrokerConstants;
import org.thingsboard.mqtt.broker.dao.DaoUtil;
import org.thingsboard.mqtt.broker.dao.model.sqlts.AbstractTsKvEntity;
import org.thingsboard.mqtt.broker.dao.model.sqlts.TsKvEntity;
import org.thingsboard.mqtt.broker.dao.sql.SqlQueueStatsManager;
import org.thingsboard.mqtt.broker.dao.sql.TbSqlBlockingQueuePool;
import org.thingsboard.mqtt.broker.dao.sql.TbSqlQueueParams;
import org.thingsboard.mqtt.broker.dao.sqlts.insert.InsertTsRepository;
import org.thingsboard.mqtt.broker.dao.sqlts.ts.TsKvRepository;
import org.thingsboard.mqtt.broker.dao.timeseries.TimeseriesDao;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Slf4j
public abstract class AbstractChunkedAggregationTimeseriesDao extends AbstractSqlTimeseriesDao implements TimeseriesDao {

    @Autowired
    protected TsKvRepository tsKvRepository;
    @Autowired
    protected InsertTsRepository<TsKvEntity> insertRepository;
    @Autowired(required = false)
    private SqlQueueStatsManager statsManager;

    protected TbSqlBlockingQueuePool<TsKvEntity> tsQueue;

    @PostConstruct
    protected void init() {
        TbSqlQueueParams tsParams = TbSqlQueueParams.builder()
                .queueName("TimeseriesQueue")
                .batchSize(tsBatchSize)
                .maxDelay(tsMaxDelay)
                .batchSortEnabled(batchSortEnabled)
                .build();

        Function<TsKvEntity, Integer> hashcodeFunction = entity -> entity.getEntityId().hashCode();
        Comparator<TsKvEntity> tsKvEntityComparator = Comparator.comparing((Function<TsKvEntity, String>) AbstractTsKvEntity::getEntityId)
                .thenComparing(AbstractTsKvEntity::getKey)
                .thenComparing(AbstractTsKvEntity::getTs);

        tsQueue = TbSqlBlockingQueuePool.<TsKvEntity>builder()
                .params(tsParams)
                .maxThreads(tsBatchThreads)
                .queueIndexHashFunction(hashcodeFunction)
                .processingFunction(v -> insertRepository.saveOrUpdate(v))
                .statsManager(statsManager)
                .batchUpdateComparator(tsKvEntityComparator)
                .build();
        tsQueue.init();
    }

    @PreDestroy
    protected void destroy() {
        if (tsQueue != null) {
            tsQueue.destroy();
        }
    }

    @Override
    public ListenableFuture<List<TsKvEntry>> findAllAsync(String entityId, List<ReadTsKvQuery> queries) {
        return processFindAllAsync(entityId, queries);
    }

    @Override
    public ListenableFuture<Void> remove(String entityId, DeleteTsKvQuery query) {
        return service.submit(() -> {
            tsKvRepository.delete(
                    entityId,
                    getOrSaveKeyId(query.getKey()),
                    query.getStartTs(),
                    query.getEndTs());
            return null;
        });
    }

    @Override
    public ListenableFuture<TsKvEntry> findLatest(String entityId, String key) {
        ListenableFuture<List<TsKvEntry>> future = findAllAsync(entityId, createQuery(key));
        return Futures.transform(future, tsKvEntries -> {
            if (!CollectionUtils.isEmpty(tsKvEntries)) {
                return tsKvEntries.get(0);
            }
            return null;
        }, MoreExecutors.directExecutor());
    }

    @Override
    public ListenableFuture<List<TsKvEntry>> findAllLatest(String entityId) {
        List<ReadTsKvQuery> queries = new ArrayList<>();
        for (String key : BrokerConstants.HISTORICAL_KEYS) {
            queries.add(createQuery(key));
        }
        return findAllAsync(entityId, queries);
    }

    @Override
    public ListenableFuture<List<TsKvEntry>> findAllAsync(String entityId, ReadTsKvQuery query) {
        if (query.getAggregation() == Aggregation.NONE) {
            return Futures.immediateFuture(findAllAsyncWithLimit(entityId, query));
        } else {
            List<ListenableFuture<Optional<TsKvEntity>>> futures = new ArrayList<>();
            long startPeriod = query.getStartTs();
            long endPeriod = Math.max(query.getStartTs() + 1, query.getEndTs());
            long step = query.getInterval();
            while (startPeriod < endPeriod) {
                long startTs = startPeriod;
                long endTs = Math.min(startPeriod + step, endPeriod);
                long ts = startTs + (endTs - startTs) / 2;
                ListenableFuture<Optional<TsKvEntity>> aggregateTsKvEntry = findAndAggregateAsync(entityId, query.getKey(), startTs, endTs, ts, query.getAggregation());
                futures.add(aggregateTsKvEntry);
                startPeriod = endTs;
            }
            return getTsKvEntriesFuture(Futures.allAsList(futures));
        }
    }

    private BaseReadTsKvQuery createQuery(String key) {
        return new BaseReadTsKvQuery(key, 0, System.currentTimeMillis(), 1, "DESC");
    }

    private List<TsKvEntry> findAllAsyncWithLimit(String entityId, ReadTsKvQuery query) {
        Integer keyId = getOrSaveKeyId(query.getKey());
        List<TsKvEntity> tsKvEntities = tsKvRepository.findAllWithLimit(
                entityId,
                keyId,
                query.getStartTs(),
                query.getEndTs(),
                PageRequest.of(0, query.getLimit(), Direction.fromString(query.getOrder()), "ts"));
        tsKvEntities.forEach(tsKvEntity -> tsKvEntity.setStrKey(query.getKey()));
        return DaoUtil.convertDataList(tsKvEntities);
    }

    ListenableFuture<Optional<TsKvEntity>> findAndAggregateAsync(String entityId, String key, long startTs, long endTs, long ts, Aggregation aggregation) {
        return service.submit(() -> {
            TsKvEntity entity = switchAggregation(entityId, key, startTs, endTs, aggregation);
            if (entity != null && entity.isNotEmpty()) {
                entity.setEntityId(entityId);
                entity.setStrKey(key);
                entity.setTs(ts);
                return Optional.of(entity);
            } else {
                return Optional.empty();
            }
        });
    }

    protected TsKvEntity switchAggregation(String entityId, String key, long startTs, long endTs, Aggregation aggregation) {
        var keyId = getOrSaveKeyId(key);
        switch (aggregation) {
            case AVG:
                return tsKvRepository.findAvg(entityId, keyId, startTs, endTs);
            case MAX:
                var max = tsKvRepository.findNumericMax(entityId, keyId, startTs, endTs);
                if (max.isNotEmpty()) {
                    return max;
                } else {
                    return null;
                }
            case MIN:
                var min = tsKvRepository.findNumericMin(entityId, keyId, startTs, endTs);
                if (min.isNotEmpty()) {
                    return min;
                } else {
                    return null;
                }
            case SUM:
                return tsKvRepository.findSum(entityId, keyId, startTs, endTs);
            case COUNT:
                return tsKvRepository.findCount(entityId, keyId, startTs, endTs);
            default:
                throw new IllegalArgumentException("Not supported aggregation type: " + aggregation);
        }
    }
}
