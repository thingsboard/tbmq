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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
public class WebSocketConnectionController extends BaseController {

    private final WebSocketConnectionService webSocketConnectionService;

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @PostMapping
    public WebSocketConnection saveWebSocketConnection(@RequestBody WebSocketConnection connection) throws ThingsboardException {
        return checkNotNull(webSocketConnectionService.saveWebSocketConnection(connection));
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "", params = {"pageSize", "page"})
    public PageData<WebSocketConnectionDto> getWebSocketConnections(@RequestParam int pageSize,
                                                                    @RequestParam int page,
                                                                    @RequestParam(required = false) String textSearch,
                                                                    @RequestParam(required = false) String sortProperty,
                                                                    @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        UUID userId = getCurrentUser().getId();
        PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
        return checkNotNull(webSocketConnectionService.getWebSocketConnections(userId, pageLink));
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/{id}")
    public WebSocketConnection getWebSocketConnectionById(@PathVariable String id) throws ThingsboardException {
        checkParameter("id", id);
        return checkNotNull(webSocketConnectionService.getWebSocketConnectionById(toUUID(id)).orElse(null));
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "", params = {"name"})
    public WebSocketConnection getWebSocketConnectionByName(@RequestParam String name) throws ThingsboardException {
        checkParameter("name", name);
        UUID userId = getCurrentUser().getId();
        return checkNotNull(webSocketConnectionService.findWebSocketConnectionByName(userId, name));
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @DeleteMapping(value = "/{id}")
    public void deleteWebSocketConnection(@PathVariable String id) throws ThingsboardException {
        WebSocketConnection webSocketConnection = checkNotNull(webSocketConnectionService.getWebSocketConnectionById(toUUID(id)).orElse(null));
        clientSessionCleanUpService.disconnectClientSession(webSocketConnection.getConfiguration().getClientId());

        webSocketConnectionService.deleteWebSocketConnection(toUUID(id));
    }

}
