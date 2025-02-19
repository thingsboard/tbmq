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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.SubscriptionService;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.subscription.ClientSubscriptionQuery;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.dao.topic.TopicValidationService;
import org.thingsboard.mqtt.broker.dto.ClientIdSubscriptionInfoDto;
import org.thingsboard.mqtt.broker.dto.ClientSubscriptionInfoDto;
import org.thingsboard.mqtt.broker.dto.SharedSubscriptionDto;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscriptionAdminService;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscriptionCache;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscriptionPageService;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptionQuery;

import java.util.List;
import java.util.Set;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/subscription")
public class SubscriptionController extends BaseController {

    private final SubscriptionService subscriptionService;
    private final ClientSubscriptionCache clientSubscriptionCache;
    private final ClientSubscriptionAdminService subscriptionAdminService;
    private final TopicValidationService topicValidationService;
    private final ClientSubscriptionPageService clientSubscriptionPageService;

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(method = RequestMethod.POST)
    @ResponseBody
    public ClientIdSubscriptionInfoDto updateSubscriptions(@RequestBody ClientIdSubscriptionInfoDto clientIdSubscriptionInfoDto) throws ThingsboardException {
        checkNotNull(clientIdSubscriptionInfoDto);
        checkNotNull(clientIdSubscriptionInfoDto.getSubscriptions());

        try {
            subscriptionAdminService.updateSubscriptions(clientIdSubscriptionInfoDto.getClientId(), clientIdSubscriptionInfoDto.getSubscriptions());
            return clientIdSubscriptionInfoDto;
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/topic-trie/clear", method = RequestMethod.DELETE)
    @ResponseBody
    public void clearEmptySubscriptionNodes() throws ThingsboardException {
        try {
            subscriptionService.clearEmptyTopicNodes();
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "", params = {"clientId"}, method = RequestMethod.GET)
    @ResponseBody
    public Set<TopicSubscription> getClientSubscriptions(@RequestParam String clientId) throws ThingsboardException {
        try {
            return checkNotNull(clientSubscriptionCache.getClientSubscriptions(clientId));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
    public PageData<SharedSubscriptionDto> getSharedSubscriptions(@RequestParam int pageSize,
                                                                  @RequestParam int page,
                                                                  @RequestParam(required = false) String textSearch,
                                                                  @RequestParam(required = false) String sortProperty,
                                                                  @RequestParam(required = false) String sortOrder,
                                                                  @RequestParam(required = false) String shareNameSearch,
                                                                  @RequestParam(required = false) String clientIdSearch) throws ThingsboardException {
        try {
            PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
            return checkNotNull(sharedSubscriptionPaginationService.getSharedSubscriptions(
                    new SharedSubscriptionQuery(pageLink, shareNameSearch, clientIdSearch)
            ));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/all", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
    public PageData<ClientSubscriptionInfoDto> getAllClientSubscriptions(@RequestParam int pageSize,
                                                                         @RequestParam int page,
                                                                         @RequestParam(required = false) String textSearch,
                                                                         @RequestParam(required = false) String sortProperty,
                                                                         @RequestParam(required = false) String sortOrder,
                                                                         @RequestParam(required = false) String clientId,
                                                                         @RequestParam(required = false) String topicFilter,
                                                                         @RequestParam(required = false) String[] qosList,
                                                                         @RequestParam(required = false) String[] noLocalList,
                                                                         @RequestParam(required = false) String[] retainAsPublishList,
                                                                         @RequestParam(required = false) String[] retainHandlingList,
                                                                         @RequestParam(required = false) Integer subscriptionId) throws ThingsboardException {
        try {
            Set<Integer> allQos = collectIntegerQueryParams(qosList);
            List<Boolean> allNoLocal = collectBooleanQueryParams(noLocalList);
            List<Boolean> allRetainAsPublish = collectBooleanQueryParams(retainAsPublishList);
            Set<Integer> allRetainHandling = collectIntegerQueryParams(retainHandlingList);

            PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);

            return checkNotNull(clientSubscriptionPageService.getClientSubscriptions(
                    new ClientSubscriptionQuery(pageLink, clientId, topicFilter, allQos, allNoLocal, allRetainAsPublish, allRetainHandling, subscriptionId)
            ));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "integration", params = {"integrationId"}, method = RequestMethod.GET)
    @ResponseBody
    public Set<String> getIntegrationSubscriptions(@RequestParam String integrationId) throws ThingsboardException {
        return checkNotNull(clientSubscriptionCache.getIntegrationSubscriptions(toUUID(integrationId)));
    }
}
