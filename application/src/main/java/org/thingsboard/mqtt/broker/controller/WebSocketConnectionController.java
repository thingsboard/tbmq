/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.mqtt.broker.common.data.dto.WebSocketConnectionDto;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketConnection;
import org.thingsboard.mqtt.broker.dao.ws.WebSocketConnectionService;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ws/connection")
@Slf4j
public class WebSocketConnectionController extends BaseController {

    private final WebSocketConnectionService webSocketConnectionService;

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "", method = RequestMethod.POST)
    @ResponseBody
    public WebSocketConnection saveWebSocketConnection(@RequestBody WebSocketConnection connection) throws ThingsboardException {
        checkNotNull(connection);
        try {
            return checkNotNull(webSocketConnectionService.saveWebSocketConnection(connection));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
    public PageData<WebSocketConnectionDto> getWebSocketConnections(@RequestParam int pageSize,
                                                                    @RequestParam int page,
                                                                    @RequestParam(required = false) String textSearch,
                                                                    @RequestParam(required = false) String sortProperty,
                                                                    @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        try {
            UUID userId = getCurrentUser().getId();
            PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
            return checkNotNull(webSocketConnectionService.getWebSocketConnections(userId, pageLink));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/{id}", method = RequestMethod.GET)
    public WebSocketConnection getWebSocketConnectionById(@PathVariable String id) throws ThingsboardException {
        try {
            return checkNotNull(webSocketConnectionService.getWebSocketConnectionById(toUUID(id)).orElse(null));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "", params = {"name"}, method = RequestMethod.GET)
    public WebSocketConnection getWebSocketConnectionByName(@RequestParam String name) throws ThingsboardException {
        checkParameter("name", name);
        try {
            UUID userId = getCurrentUser().getId();
            return checkNotNull(webSocketConnectionService.findWebSocketConnectionByName(userId, name));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE)
    public void deleteWebSocketConnection(@PathVariable String id) throws ThingsboardException {
        try {
            WebSocketConnection webSocketConnection = checkNotNull(webSocketConnectionService.getWebSocketConnectionById(toUUID(id)).orElse(null));
            clientSessionCleanUpService.disconnectClientSession(webSocketConnection.getConfiguration().getClientId());

            webSocketConnectionService.deleteWebSocketConnection(toUUID(id));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

}
