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
package org.thingsboard.mqtt.broker.dao.event;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.thingsboard.mqtt.broker.common.data.event.Event;
import org.thingsboard.mqtt.broker.common.data.event.EventInfo;
import org.thingsboard.mqtt.broker.common.data.event.EventType;
import org.thingsboard.mqtt.broker.common.data.event.LifecycleEvent;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.SortOrder;
import org.thingsboard.mqtt.broker.common.data.page.TimePageLink;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.service.AbstractServiceTest;

import java.text.ParseException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.apache.commons.lang3.time.DateFormatUtils.ISO_8601_EXTENDED_DATETIME_TIME_ZONE_FORMAT;

@DaoSqlTest
public class BaseEventServiceTest extends AbstractServiceTest {

    @Autowired
    EventService eventService;

    long timeBeforeStartTime;
    long startTime;
    long eventTime;
    long endTime;
    long timeAfterEndTime;

    @Before
    public void before() throws ParseException {
        timeBeforeStartTime = ISO_8601_EXTENDED_DATETIME_TIME_ZONE_FORMAT.parse("2016-11-01T11:30:00Z").getTime();
        startTime = ISO_8601_EXTENDED_DATETIME_TIME_ZONE_FORMAT.parse("2016-11-01T12:00:00Z").getTime();
        eventTime = ISO_8601_EXTENDED_DATETIME_TIME_ZONE_FORMAT.parse("2016-11-01T12:30:00Z").getTime();
        endTime = ISO_8601_EXTENDED_DATETIME_TIME_ZONE_FORMAT.parse("2016-11-01T13:00:00Z").getTime();
        timeAfterEndTime = ISO_8601_EXTENDED_DATETIME_TIME_ZONE_FORMAT.parse("2016-11-01T13:30:30Z").getTime();
    }

    @After
    public void after() {
        eventService.cleanupEvents(timeAfterEndTime + 1, true);
    }

    @Test
    public void saveEvent() throws Exception {
        UUID entityId = UUID.randomUUID();
        LifecycleEvent event = generateEvent(entityId);
        eventService.saveAsync(event).get(5, TimeUnit.SECONDS);
        List<EventInfo> loaded = eventService.findLatestEvents(entityId, event.getType(), 1);
        Assert.assertNotNull(loaded);
        Assert.assertEquals(1, loaded.size());
        Assert.assertEquals("TEST", loaded.get(0).getBody().get("event").asText());
    }

    @Test
    public void findEventsByTypeAndTimeAscOrder() throws Exception {
        UUID customerId = UUID.randomUUID();
        saveEventWithProvidedTime(timeBeforeStartTime, customerId);
        Event savedEvent = saveEventWithProvidedTime(eventTime, customerId);
        Event savedEvent2 = saveEventWithProvidedTime(eventTime + 1, customerId);
        Event savedEvent3 = saveEventWithProvidedTime(eventTime + 2, customerId);
        saveEventWithProvidedTime(timeAfterEndTime, customerId);

        TimePageLink timePageLink = new TimePageLink(2, 0, "", new SortOrder("ts"), startTime, endTime);

        PageData<EventInfo> events = eventService.findEvents(customerId, EventType.LC_EVENT, timePageLink);

        Assert.assertNotNull(events.getData());
        Assert.assertEquals(2, events.getData().size());
        Assert.assertEquals(savedEvent.getId(), events.getData().get(0).getId());
        Assert.assertEquals(savedEvent2.getId(), events.getData().get(1).getId());
        Assert.assertTrue(events.hasNext());

        events = eventService.findEvents(customerId, EventType.LC_EVENT, timePageLink.nextPageLink());

        Assert.assertNotNull(events.getData());
        Assert.assertEquals(1, events.getData().size());
        Assert.assertEquals(savedEvent3.getId(), events.getData().get(0).getId());
        Assert.assertFalse(events.hasNext());
    }

    @Test
    public void findEventsByTypeAndTimeDescOrder() throws Exception {
        UUID customerId = UUID.randomUUID();
        saveEventWithProvidedTime(timeBeforeStartTime, customerId);
        Event savedEvent = saveEventWithProvidedTime(eventTime, customerId);
        Event savedEvent2 = saveEventWithProvidedTime(eventTime + 1, customerId);
        Event savedEvent3 = saveEventWithProvidedTime(eventTime + 2, customerId);
        saveEventWithProvidedTime(timeAfterEndTime, customerId);

        TimePageLink timePageLink = new TimePageLink(2, 0, "", new SortOrder("ts", SortOrder.Direction.DESC), startTime, endTime);

        PageData<EventInfo> events = eventService.findEvents(customerId, EventType.LC_EVENT, timePageLink);

        Assert.assertNotNull(events.getData());
        Assert.assertEquals(2, events.getData().size());
        Assert.assertEquals(savedEvent3.getId(), events.getData().get(0).getId());
        Assert.assertEquals(savedEvent2.getId(), events.getData().get(1).getId());
        Assert.assertTrue(events.hasNext());

        events = eventService.findEvents(customerId, EventType.LC_EVENT, timePageLink.nextPageLink());

        Assert.assertNotNull(events.getData());
        Assert.assertEquals(1, events.getData().size());
        Assert.assertEquals(savedEvent.getId(), events.getData().get(0).getId());
        Assert.assertFalse(events.hasNext());
    }

    private Event saveEventWithProvidedTime(long time, UUID entityId) throws Exception {
        return saveEventWithProvidedTimeAndEntityId(time, entityId);
    }

    private Event saveEventWithProvidedTimeAndEntityId(long time, UUID entityId) throws Exception {
        LifecycleEvent event = generateEvent(entityId);
        event.setId(UUID.randomUUID());
        event.setCreatedTime(time);
        eventService.saveAsync(event).get();
        return event;
    }

}
