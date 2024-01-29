/**
 * Copyright © 2016-2023 The Thingsboard Authors
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

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "", method = RequestMethod.POST)
    @ResponseBody
    public WebSocketSubscription saveWebSocketSubscription(@RequestBody WebSocketSubscription subscription) throws ThingsboardException {
        checkNotNull(subscription);
        try {
            return checkNotNull(webSocketSubscriptionService.saveWebSocketSubscription(subscription));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "", params = {"webSocketConnectionId"}, method = RequestMethod.GET)
    @ResponseBody
    public List<WebSocketSubscription> getWebSocketConnections(@RequestParam String webSocketConnectionId) throws ThingsboardException {
        checkParameter("webSocketConnectionId", webSocketConnectionId);
        try {
            return checkNotNull(webSocketSubscriptionService.getWebSocketSubscriptions(toUUID(webSocketConnectionId)));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/{id}", method = RequestMethod.GET)
    public WebSocketSubscription getWebSocketSubscriptionById(@PathVariable String id) throws ThingsboardException {
        try {
            return checkNotNull(webSocketSubscriptionService.getWebSocketSubscriptionById(toUUID(id)));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE)
    public void deleteWebSocketSubscription(@PathVariable String id) throws ThingsboardException {
        try {
            webSocketSubscriptionService.deleteWebSocketSubscription(toUUID(id));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

}
