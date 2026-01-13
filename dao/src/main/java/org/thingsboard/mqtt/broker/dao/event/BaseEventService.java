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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.event.ErrorEvent;
import org.thingsboard.mqtt.broker.common.data.event.Event;
import org.thingsboard.mqtt.broker.common.data.event.EventFilter;
import org.thingsboard.mqtt.broker.common.data.event.EventInfo;
import org.thingsboard.mqtt.broker.common.data.event.EventType;
import org.thingsboard.mqtt.broker.common.data.event.LifecycleEvent;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.TimePageLink;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.dao.service.DataValidator;
import org.thingsboard.mqtt.broker.exception.DataValidationException;

import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class BaseEventService implements EventService {

    @Value("${sql.events.max-symbols:4096}")
    private int maxEventSymbols;

    private final EventDao eventDao;

    @Override
    public ListenableFuture<Void> saveAsync(Event event) {
        eventValidator.validate(event);
        checkAndTruncateEvent(event);
        return eventDao.saveAsync(event);
    }

    private void checkAndTruncateEvent(Event event) {
        switch (event.getType()) {
            case LC_EVENT:
                LifecycleEvent lcEvent = (LifecycleEvent) event;
                truncateField(lcEvent, LifecycleEvent::getError, LifecycleEvent::setError);
                break;
            case ERROR:
                ErrorEvent eEvent = (ErrorEvent) event;
                truncateField(eEvent, ErrorEvent::getError, ErrorEvent::setError);
                break;
        }
    }

    private <T extends Event> void truncateField(T event, Function<T, String> getter, BiConsumer<T, String> setter) {
        var str = getter.apply(event);
        str = StringUtils.truncate(str, maxEventSymbols);
        setter.accept(event, str);
    }

    @Override
    public PageData<EventInfo> findEvents(UUID entityId, EventType eventType, TimePageLink pageLink) {
        return convert(eventDao.findEvents(entityId, eventType, pageLink));
    }

    @Override
    public List<EventInfo> findLatestEvents(UUID entityId, EventType eventType, int limit) {
        return convert(eventDao.findLatestEvents(entityId, eventType, limit));
    }

    @Override
    public PageData<EventInfo> findEventsByFilter(UUID entityId, EventFilter eventFilter, TimePageLink pageLink) {
        return convert(eventDao.findEventByFilter(entityId, eventFilter, pageLink));
    }

    @Override
    public void removeEvents(UUID entityId) {
        removeEvents(entityId, null, null, null);
    }

    @Override
    public void removeEvents(UUID entityId, EventFilter eventFilter, Long startTime, Long endTime) {
        if (eventFilter == null) {
            eventDao.removeEvents(entityId, startTime, endTime);
        } else {
            eventDao.removeEvents(entityId, eventFilter, startTime, endTime);
        }
    }

    @Override
    public void cleanupEvents(long eventExpTs, boolean cleanupDb) {
        eventDao.cleanupEvents(eventExpTs, cleanupDb);
    }

    private PageData<EventInfo> convert(PageData<? extends Event> pd) {
        return new PageData<>(pd.getData() == null ? null :
                pd.getData().stream().map(Event::toInfo).collect(Collectors.toList())
                , pd.getTotalPages(), pd.getTotalElements(), pd.hasNext());
    }

    private List<EventInfo> convert(List<? extends Event> list) {
        return list == null ? null : list.stream().map(Event::toInfo).collect(Collectors.toList());
    }

    private final DataValidator<Event> eventValidator =
            new DataValidator<>() {

                @Override
                protected void validateDataImpl(Event event) {
                    if (event.getEntityId() == null) {
                        throw new DataValidationException("Entity id should be specified!");
                    }
                    if (StringUtils.isEmpty(event.getServiceId())) {
                        throw new DataValidationException("Service id should be specified!");
                    }
                }
            };
}
