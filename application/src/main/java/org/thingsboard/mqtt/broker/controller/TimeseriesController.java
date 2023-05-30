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
package org.thingsboard.mqtt.broker.controller;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;
import org.thingsboard.mqtt.broker.common.data.StringUtils;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.kv.Aggregation;
import org.thingsboard.mqtt.broker.common.data.kv.BaseReadTsKvQuery;
import org.thingsboard.mqtt.broker.common.data.kv.BaseTsKvQuery;
import org.thingsboard.mqtt.broker.common.data.kv.BasicTsKvEntry;
import org.thingsboard.mqtt.broker.common.data.kv.KvEntry;
import org.thingsboard.mqtt.broker.common.data.kv.ReadTsKvQuery;
import org.thingsboard.mqtt.broker.common.data.kv.TsData;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvEntry;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvQuery;
import org.thingsboard.mqtt.broker.common.util.JsonConverter;
import org.thingsboard.mqtt.broker.dao.timeseries.TimeseriesService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/timeseries")
@Slf4j
@RequiredArgsConstructor
public class TimeseriesController extends BaseController {

    private final TimeseriesService timeseriesService;

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/{entityId}/latest", method = RequestMethod.GET)
    @ResponseBody
    public DeferredResult<ResponseEntity> getLatestTimeseries(
            @PathVariable("entityId") String entityId,
            @RequestParam(name = "keys", required = false) String keysStr,
            @RequestParam(name = "useStrictDataTypes", required = false, defaultValue = "true") Boolean useStrictDataTypes) throws ThingsboardException {
        try {
            DeferredResult<ResponseEntity> result = new DeferredResult<>();
            ListenableFuture<List<TsKvEntry>> future;
            if (StringUtils.isEmpty(keysStr)) {
                future = timeseriesService.findAllLatest(entityId);
            } else {
                future = timeseriesService.findLatest(entityId, toKeysList(keysStr));
            }
            Futures.addCallback(future, getTsKvListCallback(result, useStrictDataTypes), MoreExecutors.directExecutor());
            return result;
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/{entityId}/values", method = RequestMethod.GET, params = {"keys", "startTs", "endTs"})
    @ResponseBody
    public DeferredResult<ResponseEntity> getTimeseries(
            @PathVariable("entityId") String entityId,
            @RequestParam(name = "keys") String keys,
            @RequestParam(name = "startTs") Long startTs,
            @RequestParam(name = "endTs") Long endTs,
            @RequestParam(name = "interval", defaultValue = "0") Long interval,
            @RequestParam(name = "limit", defaultValue = "100") Integer limit,
            @RequestParam(name = "agg", defaultValue = "NONE") String aggStr,
            @RequestParam(name = "orderBy", defaultValue = "DESC") String orderBy,
            @RequestParam(name = "useStrictDataTypes", required = false, defaultValue = "true") Boolean useStrictDataTypes) throws ThingsboardException {
        try {
            DeferredResult<ResponseEntity> result = new DeferredResult<>();

            // If interval is 0, convert this to a NONE aggregation, which is probably what the user really wanted
            Aggregation agg = interval == 0L ? Aggregation.valueOf(Aggregation.NONE.name()) : Aggregation.valueOf(aggStr);
            List<ReadTsKvQuery> queries = toKeysList(keys).stream().map(key -> new BaseReadTsKvQuery(key, startTs, endTs, interval, limit, agg, orderBy))
                    .collect(Collectors.toList());

            Futures.addCallback(timeseriesService.findAll(entityId, queries), getTsKvListCallback(result, useStrictDataTypes), MoreExecutors.directExecutor());
            return result;
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/{entityId}/save", method = RequestMethod.POST)
    @ResponseBody
    public DeferredResult<ResponseEntity> saveEntityTelemetry(
            @PathVariable("entityId") String entityId,
            @RequestBody String requestBody) throws ThingsboardException {
        try {
            return saveTelemetry(entityId, requestBody);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    private DeferredResult<ResponseEntity> saveTelemetry(String entityId, String requestBody) throws ThingsboardException {
        DeferredResult<ResponseEntity> result = new DeferredResult<>();
        Map<Long, List<KvEntry>> telemetryRequest;
        JsonElement telemetryJson;
        try {
            telemetryJson = new JsonParser().parse(requestBody);
        } catch (Exception e) {
            return getImmediateDeferredResult("Unable to parse timeseries payload: Invalid JSON body!");
        }
        try {
            telemetryRequest = JsonConverter.convertToTelemetry(telemetryJson, System.currentTimeMillis());
        } catch (Exception e) {
            return getImmediateDeferredResult("Unable to parse timeseries payload. Invalid JSON body: " + e.getMessage());
        }
        List<TsKvEntry> entries = new ArrayList<>();
        for (Map.Entry<Long, List<KvEntry>> entry : telemetryRequest.entrySet()) {
            for (KvEntry kv : entry.getValue()) {
                entries.add(new BasicTsKvEntry(entry.getKey(), kv));
            }
        }
        if (entries.isEmpty()) {
            return getImmediateDeferredResult("No timeseries data found in request body!");
        }
        ListenableFuture<Void> save = timeseriesService.save(entityId, entries);
        Futures.addCallback(save, new FutureCallback<>() {
            @Override
            public void onSuccess(@Nullable Void unused) {
                result.setResult(new ResponseEntity<>(HttpStatus.OK));
            }

            @Override
            public void onFailure(Throwable throwable) {
                result.setResult(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));
            }
        }, MoreExecutors.directExecutor());
        return result;
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/{entityId}/delete", method = RequestMethod.DELETE)
    @ResponseBody
    public DeferredResult<ResponseEntity> deleteEntityTimeseries(
            @PathVariable("entityId") String entityId,
            @RequestParam(name = "keys") String keysStr,
            @RequestParam(name = "deleteAllDataForKeys", defaultValue = "false") boolean deleteAllDataForKeys,
            @RequestParam(name = "startTs", required = false) Long startTs,
            @RequestParam(name = "endTs", required = false) Long endTs) throws ThingsboardException {
        try {
            return deleteTimeseries(entityId, keysStr, deleteAllDataForKeys, startTs, endTs);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    private DeferredResult<ResponseEntity> deleteTimeseries(String entityId, String keysStr, boolean deleteAllDataForKeys,
                                                            Long startTs, Long endTs) throws ThingsboardException {
        List<String> keys = toKeysList(keysStr);
        if (keys.isEmpty()) {
            return getImmediateDeferredResult("Empty keys: " + keysStr);
        }

        long deleteFromTs;
        long deleteToTs;
        if (deleteAllDataForKeys) {
            deleteFromTs = 0L;
            deleteToTs = System.currentTimeMillis();
        } else {
            if (startTs == null || endTs == null) {
                return getImmediateDeferredResult("When deleteAllDataForKeys is false, start and end timestamp values shouldn't be empty");
            } else {
                deleteFromTs = startTs;
                deleteToTs = endTs;
            }
        }

        DeferredResult<ResponseEntity> result = new DeferredResult<>();

        List<TsKvQuery> deleteTsKvQueries = new ArrayList<>();
        for (String key : keys) {
            deleteTsKvQueries.add(new BaseTsKvQuery(key, deleteFromTs, deleteToTs));
        }

        ListenableFuture<List<Void>> future = timeseriesService.remove(entityId, deleteTsKvQueries);
        Futures.addCallback(future, new FutureCallback<>() {
            @Override
            public void onSuccess(@Nullable List<Void> res) {
                result.setResult(new ResponseEntity<>(HttpStatus.OK));
            }

            @Override
            public void onFailure(Throwable throwable) {
                result.setResult(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));
            }
        }, MoreExecutors.directExecutor());
        return result;
    }

    private FutureCallback<List<TsKvEntry>> getTsKvListCallback(final DeferredResult<ResponseEntity> response, Boolean useStrictDataTypes) {
        return new FutureCallback<>() {
            @Override
            public void onSuccess(List<TsKvEntry> data) {
                if (CollectionUtils.isEmpty(data)) {
                    response.setResult(new ResponseEntity<>(HttpStatus.NOT_FOUND));
                    return;
                }
                Map<String, List<TsData>> result = new LinkedHashMap<>();
                for (TsKvEntry entry : data) {
                    if (entry == null) {
                        continue;
                    }
                    Object value = useStrictDataTypes ? entry.getValue() : entry.getValueAsString();
                    result.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(new TsData(entry.getTs(), value));
                }
                response.setResult(new ResponseEntity<>(result, HttpStatus.OK));
            }

            @Override
            public void onFailure(Throwable e) {
                log.error("Failed to fetch historical data", e);
                handleError(e, response, HttpStatus.INTERNAL_SERVER_ERROR);
            }
        };
    }

    private List<String> toKeysList(String keys) {
        List<String> keyList = null;
        if (!StringUtils.isEmpty(keys)) {
            keyList = Arrays.asList(keys.split(","));
        }
        return keyList;
    }

    private DeferredResult<ResponseEntity> getImmediateDeferredResult(String message) {
        DeferredResult<ResponseEntity> result = new DeferredResult<>();
        result.setResult(new ResponseEntity<>(message, HttpStatus.BAD_REQUEST));
        return result;
    }

}
