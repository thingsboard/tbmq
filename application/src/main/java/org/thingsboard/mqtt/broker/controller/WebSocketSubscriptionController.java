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
package org.thingsboard.mqtt.broker.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketSubscription;
import org.thingsboard.mqtt.broker.dao.ws.WebSocketSubscriptionService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ws/subscription")
@Slf4j
public class WebSocketSubscriptionController extends BaseController {

    private final WebSocketSubscriptionService webSocketSubscriptionService;

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @PostMapping
    public WebSocketSubscription saveWebSocketSubscription(@RequestBody WebSocketSubscription subscription) throws ThingsboardException {
        return checkNotNull(webSocketSubscriptionService.saveWebSocketSubscription(subscription));
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "", params = {"webSocketConnectionId"})
    public List<WebSocketSubscription> getWebSocketSubscriptions(@RequestParam String webSocketConnectionId) throws ThingsboardException {
        checkParameter("webSocketConnectionId", webSocketConnectionId);
        return checkNotNull(webSocketSubscriptionService.getWebSocketSubscriptions(toUUID(webSocketConnectionId)));
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/{id}")
    public WebSocketSubscription getWebSocketSubscriptionById(@PathVariable String id) throws ThingsboardException {
        checkParameter("id", id);
        return checkNotNull(webSocketSubscriptionService.getWebSocketSubscriptionById(toUUID(id)));
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @DeleteMapping(value = "/{id}")
    public void deleteWebSocketSubscription(@PathVariable String id) throws ThingsboardException {
        checkParameter("id", id);
        webSocketSubscriptionService.deleteWebSocketSubscription(toUUID(id));
    }

}
