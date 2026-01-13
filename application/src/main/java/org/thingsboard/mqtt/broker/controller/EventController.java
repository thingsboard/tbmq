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
package org.thingsboard.mqtt.broker.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.mqtt.broker.common.data.event.EventFilter;
import org.thingsboard.mqtt.broker.common.data.event.EventInfo;
import org.thingsboard.mqtt.broker.common.data.event.EventType;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.TimePageLink;
import org.thingsboard.mqtt.broker.dao.event.EventService;

import java.util.UUID;

import static org.thingsboard.mqtt.broker.controller.ControllerConstants.ENTITY_ID;

@RestController
@RequestMapping("/api/events/{entityId}")
@RequiredArgsConstructor
public class EventController extends BaseController {

    private final EventService eventService;

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/{eventType}")
    public PageData<EventInfo> getEvents(
            @PathVariable(ENTITY_ID) String strEntityId,
            @PathVariable("eventType") String eventType,
            @RequestParam int pageSize,
            @RequestParam int page,
            @RequestParam(required = false) String textSearch,
            @RequestParam(required = false) String sortProperty,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime) throws ThingsboardException {
        checkParameter("EntityId", strEntityId);
        UUID entityId = toUUID(strEntityId);
        TimePageLink pageLink = createTimePageLink(pageSize, page, textSearch, sortProperty, sortOrder, startTime, endTime);
        return checkNotNull(eventService.findEvents(entityId, resolveEventType(eventType), pageLink));
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @PostMapping
    public PageData<EventInfo> getEvents(
            @PathVariable(ENTITY_ID) String strEntityId,
            @RequestParam int pageSize,
            @RequestParam int page,
            @RequestBody EventFilter eventFilter,
            @RequestParam(required = false) String textSearch,
            @RequestParam(required = false) String sortProperty,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime) throws ThingsboardException {
        checkParameter("EntityId", strEntityId);
        UUID entityId = toUUID(strEntityId);
        TimePageLink pageLink = createTimePageLink(pageSize, page, textSearch, sortProperty, sortOrder, startTime, endTime);
        return checkNotNull(eventService.findEventsByFilter(entityId, eventFilter, pageLink));
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @PostMapping(value = "/clear")
    @ResponseStatus(HttpStatus.OK)
    public void clearEvents(@PathVariable(ENTITY_ID) String strEntityId,
                            @RequestParam(required = false) Long startTime,
                            @RequestParam(required = false) Long endTime,
                            @RequestBody EventFilter eventFilter) throws ThingsboardException {
        checkParameter("EntityId", strEntityId);
        UUID entityId = toUUID(strEntityId);

        eventService.removeEvents(entityId, eventFilter, startTime, endTime);
    }

    private EventType resolveEventType(String eventType) throws ThingsboardException {
        for (var et : EventType.values()) {
            if (et.name().equalsIgnoreCase(eventType) || et.getName().equalsIgnoreCase(eventType)) {
                return et;
            }
        }
        throw new ThingsboardException("Event type: '" + eventType + "' is not supported!", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
    }

}
