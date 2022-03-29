/**
 * Copyright © 2016-2020 The Thingsboard Authors
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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.dto.DetailedClientSessionInfoDto;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscriptionAdminService;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscriptionCache;
import org.thingsboard.mqtt.broker.service.subscription.SubscriptionMaintenanceService;
import org.thingsboard.mqtt.broker.service.subscription.TopicSubscription;

import java.util.Set;

@RestController
@RequestMapping("/api/subscription")
public class SubscriptionController extends BaseController {

    @Autowired
    private SubscriptionMaintenanceService subscriptionMaintenanceService;
    @Autowired
    private ClientSubscriptionCache clientSubscriptionCache;
    @Autowired
    private ClientSubscriptionAdminService subscriptionAdminService;

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(method = RequestMethod.POST)
    @ResponseBody
    public DetailedClientSessionInfoDto updateSubscriptions(@RequestBody DetailedClientSessionInfoDto detailedClientSessionInfoDto) throws ThingsboardException {
        checkNotNull(detailedClientSessionInfoDto);
        checkNotNull(detailedClientSessionInfoDto.getSubscriptions());
        try {
            subscriptionAdminService.updateSubscriptions(detailedClientSessionInfoDto.getClientId(), detailedClientSessionInfoDto.getSubscriptions());
            return detailedClientSessionInfoDto;
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/topic-trie/clear", method = RequestMethod.DELETE)
    @ResponseBody
    public void clearEmptySubscriptionNodes() throws ThingsboardException {
        try {
            subscriptionMaintenanceService.clearEmptyTopicNodes();
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/{clientId}", method = RequestMethod.GET)
    @ResponseBody
    public Set<TopicSubscription> getClientSubscriptions(@PathVariable("clientId") String clientId) throws ThingsboardException {
        try {
            return clientSubscriptionCache.getClientSubscriptions(clientId);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

}
