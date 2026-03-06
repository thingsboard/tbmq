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

import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.DeferredResult;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvEntry;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvQuery;

import java.util.List;

public interface TbTimeseriesService {

    void deleteLatestTimeseries(String entityId, String keysStr, boolean deleteClientSessionCachedStats, User currentUser, DeferredResult<ResponseEntity> result);

    void deleteEntityTimeseries(String entityId, List<TsKvQuery> queries, User currentUser, DeferredResult<ResponseEntity> result);

    void saveEntityTimeseries(String entityId, List<TsKvEntry> entries, User currentUser, DeferredResult<ResponseEntity> result);

}
