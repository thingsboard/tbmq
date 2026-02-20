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
package org.thingsboard.mqtt.broker.service.entity.timeseries;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.DeferredResult;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.id.HasId;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvEntry;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvLatestRemovingResult;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvQuery;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.dao.timeseries.TimeseriesService;
import org.thingsboard.mqtt.broker.service.entity.AbstractTbEntityService;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionStatsService;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultTbTimeseriesService extends AbstractTbEntityService implements TbTimeseriesService {

    private final TimeseriesService timeseriesService;
    private final ClientSessionStatsService clientSessionStatsService;

    @Override
    public void deleteLatestTimeseries(String entityId, String keysStr, boolean deleteClientSessionCachedStats, User currentUser, DeferredResult<ResponseEntity> result) {
        ListenableFuture<Collection<String>> future;
        if (StringUtils.isEmpty(keysStr)) {
            future = timeseriesService.removeAllLatest(entityId);
        } else {
            List<String> keyList = Arrays.asList(keysStr.split(BrokerConstants.COMMA));
            future = Futures.transform(
                    timeseriesService.removeLatestKeys(entityId, keyList),
                    list -> list.stream().map(TsKvLatestRemovingResult::getKey).collect(Collectors.toList()),
                    MoreExecutors.directExecutor());
        }
        Futures.addCallback(future, new FutureCallback<>() {
            @Override
            public void onSuccess(Collection<String> keys) {
                log.debug("[{}] Successfully deleted latest time series {}", entityId, keys);
                if (deleteClientSessionCachedStats) {
                    clientSessionStatsService.broadcastCleanupClientSessionStatsRequest(entityId);
                }
                result.setResult(new ResponseEntity<>(HttpStatus.OK));
            }

            @Override
            public void onFailure(Throwable t) {
                log.warn("[{}] Failed to delete latest time series", entityId, t);
                result.setResult(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));
            }
        }, MoreExecutors.directExecutor());
    }

    @Override
    public void deleteEntityTimeseries(String entityId, List<TsKvQuery> queries, User currentUser, DeferredResult<ResponseEntity> result) {
        Futures.addCallback(timeseriesService.remove(entityId, queries), new FutureCallback<@Nullable List<Void>>() {
            @Override
            public void onSuccess(@Nullable List<Void> res) {
                result.setResult(new ResponseEntity<>(HttpStatus.OK));
            }

            @Override
            public void onFailure(Throwable t) {
                result.setResult(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));
            }
        }, MoreExecutors.directExecutor());
    }

    @Override
    public void saveEntityTimeseries(String entityId, List<TsKvEntry> entries, User currentUser, DeferredResult<ResponseEntity> result) {
        Futures.addCallback(timeseriesService.save(entityId, entries), new FutureCallback<@Nullable Void>() {
            @Override
            public void onSuccess(@Nullable Void unused) {
                result.setResult(new ResponseEntity<>(HttpStatus.OK));
            }

            @Override
            public void onFailure(Throwable t) {
                Exception e = t instanceof Exception ex ? ex : new RuntimeException(t);
                result.setResult(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));
            }
        }, MoreExecutors.directExecutor());
    }

}
