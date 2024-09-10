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
package org.thingsboard.mqtt.broker.dao.timeseries;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.common.data.StringUtils;
import org.thingsboard.mqtt.broker.common.data.kv.Aggregation;
import org.thingsboard.mqtt.broker.common.data.kv.BaseDeleteTsKvQuery;
import org.thingsboard.mqtt.broker.common.data.kv.CleanUpResult;
import org.thingsboard.mqtt.broker.common.data.kv.DeleteTsKvQuery;
import org.thingsboard.mqtt.broker.common.data.kv.ReadTsKvQuery;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvEntry;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvLatestRemovingResult;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvQuery;
import org.thingsboard.mqtt.broker.dao.exception.IncorrectParameterException;
import org.thingsboard.mqtt.broker.dao.service.Validator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class BaseTimeseriesService implements TimeseriesService {

    private static final int INSERTS_PER_ENTRY = 2; // save into ts_kv and ts_kv_latest

    private final TimeseriesDao timeseriesDao;
    private final TimeseriesLatestDao timeseriesLatestDao;

    @Value("${database.ts_max_intervals}")
    private long maxTsIntervals;

    @Override
    public ListenableFuture<List<TsKvEntry>> findAll(String entityId, List<ReadTsKvQuery> queries) {
        validate(entityId);
        for (ReadTsKvQuery query : queries) {
            validate(query);
        }
        return timeseriesDao.findAllAsync(entityId, queries);
    }

    @Override
    public ListenableFuture<Optional<TsKvEntry>> findLatestOpt(String entityId, String key) {
        validate(entityId);
        return timeseriesLatestDao.findLatestOpt(entityId, key);
    }

    @Override
    public ListenableFuture<List<TsKvEntry>> findLatest(String entityId, Collection<String> keys) {
        validate(entityId);
        List<ListenableFuture<TsKvEntry>> futures = new ArrayList<>(keys.size());
        keys.forEach(key -> Validator.validateString(key, "Incorrect key " + key));
        keys.forEach(key -> futures.add(timeseriesLatestDao.findLatest(entityId, key)));
        return Futures.allAsList(futures);
    }

    @Override
    public ListenableFuture<List<TsKvEntry>> findAllLatest(String entityId) {
        validate(entityId);
        return timeseriesLatestDao.findAllLatest(entityId);
    }

    @Override
    public ListenableFuture<Void> save(String entityId, List<TsKvEntry> tsKvEntries) {
        validate(entityId);
        if (CollectionUtils.isEmpty(tsKvEntries)) {
            throw new IncorrectParameterException("Key value entries can't be null or empty");
        }
        return doSave(entityId, tsKvEntries);
    }

    @Override
    public ListenableFuture<Void> save(String entityId, TsKvEntry tsKvEntry) {
        return save(entityId, Collections.singletonList(tsKvEntry));
    }

    @Override
    public ListenableFuture<List<Void>> saveLatest(String entityId, List<TsKvEntry> tsKvEntries) {
        List<ListenableFuture<Void>> futures = new ArrayList<>(tsKvEntries.size());
        for (TsKvEntry tsKvEntry : tsKvEntries) {
            futures.add(timeseriesLatestDao.saveLatest(entityId, tsKvEntry));
        }
        return Futures.allAsList(futures);
    }

    @Override
    public ListenableFuture<List<Void>> remove(String entityId, List<TsKvQuery> deleteTsKvQueries) {
        validate(entityId);
        deleteTsKvQueries.forEach(BaseTimeseriesService::validate);
        List<ListenableFuture<Void>> futures = new ArrayList<>(deleteTsKvQueries.size());
        for (TsKvQuery tsKvQuery : deleteTsKvQueries) {
            futures.add(Futures.transform(timeseriesDao.remove(entityId, tsKvQuery), v -> null, MoreExecutors.directExecutor()));
        }
        return Futures.allAsList(futures);
    }

    @Override
    public ListenableFuture<TsKvLatestRemovingResult> removeLatest(String entityId, String key) {
        validate(entityId);
        validate(key);
        return timeseriesLatestDao.removeLatest(entityId, key);
    }

    @Override
    public ListenableFuture<List<TsKvLatestRemovingResult>> removeLatest(String entityId, Collection<String> keys) {
        validate(entityId);
        List<ListenableFuture<TsKvLatestRemovingResult>> futures = new ArrayList<>(keys.size());
        for (String key : keys) {
            DeleteTsKvQuery query = new BaseDeleteTsKvQuery(key, 0, System.currentTimeMillis(), false);
            futures.add(timeseriesLatestDao.removeLatest(entityId, query));
        }
        return Futures.allAsList(futures);
    }

    @Override
    public ListenableFuture<Collection<String>> removeAllLatest(String entityId) {
        validate(entityId);
        return Futures.transformAsync(this.findAllLatest(entityId), latest -> {
            if (!CollectionUtils.isEmpty(latest)) {
                Collection<String> keys = latest.stream().map(TsKvEntry::getKey).collect(Collectors.toList());
                return Futures.transform(this.removeLatest(entityId, keys), res -> keys, MoreExecutors.directExecutor());
            } else {
                return Futures.immediateFuture(Collections.emptyList());
            }
        }, MoreExecutors.directExecutor());
    }

    @Override
    public CleanUpResult cleanUp(long systemTtl) {
        if (systemTtl <= 0) {
            log.info("System TTL should be greater than 0 to clean up the data!");
            return CleanUpResult.newInstance();
        }
        return timeseriesDao.cleanUp(systemTtl);
    }

    private ListenableFuture<Void> doSave(String entityId, List<TsKvEntry> tsKvEntries) {
        List<ListenableFuture<Void>> futures = new ArrayList<>(tsKvEntries.size() * INSERTS_PER_ENTRY);
        for (TsKvEntry tsKvEntry : tsKvEntries) {
            if (tsKvEntry == null) {
                throw new IncorrectParameterException("Key value entry can't be null");
            }
            saveAndRegisterFutures(futures, entityId, tsKvEntry);
        }
        return Futures.transform(Futures.allAsList(futures), voids -> null, MoreExecutors.directExecutor());
    }

    private void saveAndRegisterFutures(List<ListenableFuture<Void>> futures, String entityId, TsKvEntry tsKvEntry) {
        futures.add(timeseriesDao.save(entityId, tsKvEntry));
        futures.add(timeseriesLatestDao.saveLatest(entityId, tsKvEntry));
    }

    private static void validate(String entityId) {
        Validator.validateString(entityId, "Incorrect entityId " + entityId);
    }

    private void validate(ReadTsKvQuery query) {
        if (query == null) {
            throw new IncorrectParameterException("ReadTsKvQuery can't be null");
        } else if (StringUtils.isBlank(query.getKey())) {
            throw new IncorrectParameterException("Incorrect ReadTsKvQuery. Key can't be empty");
        } else if (query.getAggregation() == null) {
            throw new IncorrectParameterException("Incorrect ReadTsKvQuery. Aggregation can't be empty");
        }
        if (!Aggregation.NONE.equals(query.getAggregation())) {
            long step = Math.max(query.getInterval(), 1000);
            long intervalCounts = (query.getEndTs() - query.getStartTs()) / step;
            if (intervalCounts > maxTsIntervals || intervalCounts < 0) {
                throw new IncorrectParameterException("Incorrect TsKvQuery. Number of intervals is to high - " + intervalCounts + ". " +
                        "Please increase 'interval' parameter for your query or reduce the time range of the query.");
            }
        }
    }

    private static void validate(TsKvQuery query) {
        if (query == null) {
            throw new IncorrectParameterException("DeleteTsKvQuery can't be null");
        } else if (StringUtils.isBlank(query.getKey())) {
            throw new IncorrectParameterException("Incorrect DeleteTsKvQuery. Key can't be empty");
        }
    }
}
