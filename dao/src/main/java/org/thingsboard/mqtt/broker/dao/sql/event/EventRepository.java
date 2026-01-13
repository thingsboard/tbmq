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
package org.thingsboard.mqtt.broker.dao.sql.event;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.thingsboard.mqtt.broker.common.data.event.Event;
import org.thingsboard.mqtt.broker.dao.model.sql.event.EventEntity;

import java.util.List;
import java.util.UUID;

public interface EventRepository<T extends EventEntity<V>, V extends Event> {

    List<T> findLatestEvents(UUID entityId, int limit);

    Page<T> findEvents(UUID entityId, Long startTime, Long endTime, Pageable pageable);

    void removeEvents(UUID entityId, Long startTime, Long endTime);

}
