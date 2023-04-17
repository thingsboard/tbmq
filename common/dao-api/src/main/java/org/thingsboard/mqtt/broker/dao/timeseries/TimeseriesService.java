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
package org.thingsboard.mqtt.broker.dao.timeseries;

import com.google.common.util.concurrent.ListenableFuture;
import org.thingsboard.mqtt.broker.common.data.kv.CleanUpResult;
import org.thingsboard.mqtt.broker.common.data.kv.ReadTsKvQuery;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvEntry;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvQuery;

import java.util.Collection;
import java.util.List;

public interface TimeseriesService {

    ListenableFuture<List<TsKvEntry>> findAll(String entityId, List<ReadTsKvQuery> queries);

    ListenableFuture<List<TsKvEntry>> findLatest(String entityId, Collection<String> keys);

    ListenableFuture<List<TsKvEntry>> findAllLatest(String entityId);

    ListenableFuture<Void> save(String entityId, List<TsKvEntry> tsKvEntries);

    ListenableFuture<Void> save(String entityId, TsKvEntry tsKvEntry);

    ListenableFuture<List<Void>> remove(String entityId, List<TsKvQuery> queries);

    CleanUpResult cleanUp(long systemTtl);
}
