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
import org.thingsboard.mqtt.broker.common.data.event.EventType;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.TimePageLink;

import java.util.List;
import java.util.UUID;

/**
 * The Interface EventDao.
 */
public interface EventDao {

    /**
     * Save or update event object async
     *
     * @param event the event object
     * @return saved event object future
     */
    ListenableFuture<Void> saveAsync(Event event);

    /**
     * Find events by entityId, eventType and pageLink.
     *
     * @param entityId the entityId
     * @param eventType the eventType
     * @param pageLink the pageLink
     * @return the event list
     */
    PageData<? extends Event> findEvents(UUID entityId, EventType eventType, TimePageLink pageLink);

    PageData<? extends Event> findEventByFilter(UUID entityId, EventFilter eventFilter, TimePageLink pageLink);

    /**
     * Find latest events by entityId, eventType, and limit.
     *
     * @param entityId the entityId
     * @param eventType the eventType
     * @param limit the limit
     * @return the event list
     */
    List<? extends Event> findLatestEvents(UUID entityId, EventType eventType, int limit);

    /**
     * Executes procedure to cleanup old events.
     * @param eventExpTs the expiration time of the events
     * @param cleanupDb
     */
    void cleanupEvents(long eventExpTs, boolean cleanupDb);

    /**
     * Removes all events for the specified entity and time interval
     *
     * @param entityId
     * @param startTime
     * @param endTime
     */
    void removeEvents(UUID entityId, Long startTime, Long endTime);

    /**
     *
     * Removes all events for the specified entity, event filter and time interval
     *
     * @param entityId
     * @param eventFilter
     * @param startTime
     * @param endTime
     */
    void removeEvents(UUID entityId, EventFilter eventFilter, Long startTime, Long endTime);

}
