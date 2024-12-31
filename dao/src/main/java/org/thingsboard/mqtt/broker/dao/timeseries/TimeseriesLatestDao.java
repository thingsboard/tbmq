/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
import org.thingsboard.mqtt.broker.common.data.kv.DeleteTsKvQuery;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvEntry;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvLatestRemovingResult;

import java.util.List;
import java.util.Optional;

public interface TimeseriesLatestDao {

    /**
     * Optional TsKvEntry if the value is present in the DB
     *
     */
    ListenableFuture<Optional<TsKvEntry>> findLatestOpt(String entityId, String key);

    /**
     * Returns new BasicTsKvEntry(System.currentTimeMillis(), new LongDataEntry(key, null)) if the value is NOT present in the DB
     *
     */
    ListenableFuture<TsKvEntry> findLatest(String entityId, String key);

    ListenableFuture<List<TsKvEntry>> findAllLatest(String entityId);

    ListenableFuture<List<Optional<TsKvEntry>>> findAllLatestForNode(String entityId);

    ListenableFuture<List<Optional<TsKvEntry>>> findAllLatestForClient(String entityId);

    ListenableFuture<Void> saveLatest(String entityId, TsKvEntry tsKvEntry);

    ListenableFuture<TsKvLatestRemovingResult> removeLatest(String entityId, String key);

    ListenableFuture<TsKvLatestRemovingResult> removeLatest(String entityId, DeleteTsKvQuery query);

}
