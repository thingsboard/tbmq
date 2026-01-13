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
package org.thingsboard.mqtt.broker.dao.event;

import com.google.common.util.concurrent.ListenableFuture;
import org.thingsboard.mqtt.broker.common.data.event.Event;
import org.thingsboard.mqtt.broker.common.data.event.EventFilter;
import org.thingsboard.mqtt.broker.common.data.event.EventInfo;
import org.thingsboard.mqtt.broker.common.data.event.EventType;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.TimePageLink;

import java.util.List;
import java.util.UUID;

public interface EventService {

    ListenableFuture<Void> saveAsync(Event event);

    PageData<EventInfo> findEvents(UUID entityId, EventType eventType, TimePageLink pageLink);

    List<EventInfo> findLatestEvents(UUID entityId, EventType eventType, int limit);

    PageData<EventInfo> findEventsByFilter(UUID entityId, EventFilter eventFilter, TimePageLink pageLink);

    void removeEvents(UUID entityId);

    void removeEvents(UUID entityId, EventFilter eventFilter, Long startTime, Long endTime);

    void cleanupEvents(long eventExpTs, boolean cleanupDb);

}
