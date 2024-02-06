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
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.dao.topic.TopicValidationService;
import org.thingsboard.mqtt.broker.dto.DetailedClientSessionInfoDto;
import org.thingsboard.mqtt.broker.dto.SubscriptionInfoDto;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscriptionAdminService;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscriptionCache;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptionDto;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptionQuery;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/subscription")
public class SubscriptionController extends BaseController {

    private final SubscriptionService subscriptionService;
    private final ClientSubscriptionCache clientSubscriptionCache;
    private final ClientSubscriptionAdminService subscriptionAdminService;
    private final TopicValidationService topicValidationService;

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(method = RequestMethod.POST)
    @ResponseBody
    public DetailedClientSessionInfoDto updateSubscriptions(@RequestBody DetailedClientSessionInfoDto detailedClientSessionInfoDto) throws ThingsboardException {
        checkNotNull(detailedClientSessionInfoDto);
        checkNotNull(detailedClientSessionInfoDto.getSubscriptions());

        try {
            detailedClientSessionInfoDto.getSubscriptions().forEach(subscriptionInfoDto ->
                    topicValidationService.validateTopicFilter(subscriptionInfoDto.getTopicFilter()));

            List<SubscriptionInfoDto> subscriptions = filterOutSharedSubscriptions(detailedClientSessionInfoDto.getSubscriptions());
            subscriptionAdminService.updateSubscriptions(detailedClientSessionInfoDto.getClientId(), subscriptions);
            return detailedClientSessionInfoDto;
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    private List<SubscriptionInfoDto> filterOutSharedSubscriptions(List<SubscriptionInfoDto> subscriptions) {
        return subscriptions
                .stream()
                .filter(subscription -> subscription.getShareName() == null)
                .collect(Collectors.toList());
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

}
