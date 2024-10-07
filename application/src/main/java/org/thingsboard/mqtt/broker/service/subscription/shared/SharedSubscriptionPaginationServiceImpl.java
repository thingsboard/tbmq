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
package org.thingsboard.mqtt.broker.service.subscription.shared;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.ClientSessionState;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.util.ComparableUtil;
import org.thingsboard.mqtt.broker.dto.SharedSubscriptionDto;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SharedSubscriptionPaginationServiceImpl implements SharedSubscriptionPaginationService {

    private final SharedSubscriptionCacheService sharedSubscriptionCacheService;
    private final ClientSessionCache clientSessionCache;

    @Override
    public PageData<SharedSubscriptionDto> getSharedSubscriptions(SharedSubscriptionQuery sharedSubscriptionQuery) {
        if (log.isTraceEnabled()) {
            log.trace("Executing getSharedSubscriptions by query: {}", sharedSubscriptionQuery);
        }
        var pageLink = sharedSubscriptionQuery.getPageLink();

        Map<TopicSharedSubscription, SharedSubscriptions> allSharedSubscriptions = sharedSubscriptionCacheService.getAllSharedSubscriptions();
        if (CollectionUtils.isEmpty(allSharedSubscriptions)) {
            return new PageData<>(Collections.emptyList(), 0, 0, false);
        }
        List<SharedSubscriptionDto> filteredSubscriptions = new ArrayList<>(allSharedSubscriptions.size());

        for (Map.Entry<TopicSharedSubscription, SharedSubscriptions> sharedSubscriptionsEntry : allSharedSubscriptions.entrySet()) {
            String topicFilter = sharedSubscriptionsEntry.getKey().getTopicFilter();
            if (isFieldValueDoesNotMatchFilter(pageLink.getTextSearch(), topicFilter)) {
                continue;
            }
            String shareName = sharedSubscriptionsEntry.getKey().getShareName();
            if (isFieldValueDoesNotMatchFilter(sharedSubscriptionQuery.getShareNameSearch(), shareName)) {
                continue;
            }

            filterSubscriptionsByClientId(filteredSubscriptions, sharedSubscriptionQuery, sharedSubscriptionsEntry.getValue().getDeviceSubscriptions(), shareName, topicFilter);
            filterSubscriptionsByClientId(filteredSubscriptions, sharedSubscriptionQuery, sharedSubscriptionsEntry.getValue().getApplicationSubscriptions(), shareName, topicFilter);
        }

        List<SharedSubscriptionDto> data = filteredSubscriptions.stream()
                .sorted(sorted(pageLink))
                .skip((long) pageLink.getPage() * pageLink.getPageSize())
                .limit(pageLink.getPageSize())
                .collect(Collectors.toList());

        return PageData.of(data, filteredSubscriptions.size(), pageLink);
    }

    private void filterSubscriptionsByClientId(List<SharedSubscriptionDto> filteredSubscriptions, SharedSubscriptionQuery sharedSubscriptionQuery,
                                               Set<Subscription> subscriptions, String shareName, String topicFilter) {
        if (anyClientMatchFilter(sharedSubscriptionQuery.getClientIdSearch(), subscriptions)) {
            filteredSubscriptions.add(
                    new SharedSubscriptionDto(
                            shareName,
                            topicFilter,
                            toClientSessionStates(subscriptions))
            );
        }
    }

    private boolean isFieldValueDoesNotMatchFilter(String searchStr, String value) {
        return searchStr != null && !StringUtils.containsIgnoreCase(value, searchStr);
    }

    private List<ClientSessionState> toClientSessionStates(Set<Subscription> allSubscriptions) {
        return allSubscriptions
                .stream()
                .map(this::getClientSessionState)
                .filter(Objects::nonNull)
                .toList();
    }

    private boolean anyClientMatchFilter(String clientIdSearch, Set<Subscription> subscriptions) {
        if (CollectionUtils.isEmpty(subscriptions)) {
            return false;
        }
        if (clientIdSearch == null) {
            return true;
        }
        Subscription anySubscription = subscriptions
                .stream()
                .filter(sub -> StringUtils.containsIgnoreCase(sub.getClientId(), clientIdSearch))
                .findAny()
                .orElse(null);
        return anySubscription != null;
    }

    private ClientSessionState getClientSessionState(Subscription subscription) {
        ClientSessionInfo clientSessionInfo = clientSessionCache.getClientSessionInfo(subscription.getClientId());
        return clientSessionInfo == null ? null : newClientSessionState(clientSessionInfo);
    }

    private ClientSessionState newClientSessionState(ClientSessionInfo clientSessionInfo) {
        return new ClientSessionState(clientSessionInfo.getClientId(), clientSessionInfo.getType(), clientSessionInfo.isConnected());
    }

    private Comparator<? super SharedSubscriptionDto> sorted(PageLink pageLink) {
        return ComparableUtil.sorted(pageLink, SharedSubscriptionDto::getComparator);
    }

}
