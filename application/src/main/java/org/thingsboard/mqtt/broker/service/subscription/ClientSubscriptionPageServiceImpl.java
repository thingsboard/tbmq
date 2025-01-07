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
package org.thingsboard.mqtt.broker.service.subscription;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.subscription.ClientSubscriptionQuery;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.common.data.util.ComparableUtil;
import org.thingsboard.mqtt.broker.dto.ClientSubscriptionInfoDto;
import org.thingsboard.mqtt.broker.dto.SubscriptionInfoDto;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientSubscriptionPageServiceImpl implements ClientSubscriptionPageService {

    private final ClientSubscriptionCache clientSubscriptionCache;

    @Override
    public PageData<ClientSubscriptionInfoDto> getClientSubscriptions(ClientSubscriptionQuery query) {
        Map<String, Set<TopicSubscription>> allClientSubscriptions = clientSubscriptionCache.getAllClientSubscriptions();
        if (CollectionUtils.isEmpty(allClientSubscriptions)) {
            return PageData.emptyPageData();
        }

        Set<Integer> qosSet = query.getQosSet();
        List<Boolean> noLocalList = query.getNoLocalList();
        List<Boolean> retainAsPublishList = query.getRetainAsPublishList();
        Set<Integer> retainHandlingSet = query.getRetainHandlingSet();

        List<ClientSubscriptionInfoDto> filteredSubscriptions = filterByClientId(query, allClientSubscriptions)
                .parallelStream()
                .filter(subscriptionInfoDto -> filterClientSubscriptionByTopicFilter(query, subscriptionInfoDto))
                .filter(subscriptionInfoDto -> filterClientSubscriptionByQos(qosSet, subscriptionInfoDto))
                .filter(subscriptionInfoDto -> filterClientSubscriptionBySubOption(noLocalList, subscriptionInfoDto.getSubscription().getOptions().isNoLocal()))
                .filter(subscriptionInfoDto -> filterClientSubscriptionBySubOption(retainAsPublishList, subscriptionInfoDto.getSubscription().getOptions().isRetainAsPublish()))
                .filter(subscriptionInfoDto -> filterClientSubscriptionByRetainHandling(retainHandlingSet, subscriptionInfoDto))
                .filter(subscriptionInfoDto -> filterBySubscriptionId(query, subscriptionInfoDto))
                .collect(Collectors.toList());

        return mapToPageDataResponse(filteredSubscriptions, query.getPageLink());
    }

    private List<ClientSubscriptionInfoDto> filterByClientId(ClientSubscriptionQuery query, Map<String, Set<TopicSubscription>> allClientSubscriptions) {
        return allClientSubscriptions
                .entrySet()
                .parallelStream()
                .filter(subscritionsEntry -> filterClientSubscriptionsByTextSearch(query.getPageLink().getTextSearch(), subscritionsEntry.getKey()))
                .filter(subscritionsEntry -> filterClientSubscriptionsByClientId(query, subscritionsEntry.getKey()))
                .flatMap(subscritionsEntry ->
                        subscritionsEntry
                                .getValue()
                                .stream()
                                .map(topicSubscription -> newClientSubscriptionInfoDto(subscritionsEntry.getKey(), topicSubscription)))
                .collect(Collectors.toList());
    }

    private PageData<ClientSubscriptionInfoDto> mapToPageDataResponse(List<ClientSubscriptionInfoDto> filteredClientSubscriptions, PageLink pageLink) {
        List<ClientSubscriptionInfoDto> data = filteredClientSubscriptions.stream()
                .sorted(sorted(pageLink))
                .skip((long) pageLink.getPage() * pageLink.getPageSize())
                .limit(pageLink.getPageSize())
                .collect(Collectors.toList());

        return PageData.of(data, filteredClientSubscriptions.size(), pageLink);
    }

    private Comparator<? super ClientSubscriptionInfoDto> sorted(PageLink pageLink) {
        return ComparableUtil.sorted(pageLink, ClientSubscriptionInfoDto::getComparator);
    }

    private boolean filterClientSubscriptionsByTextSearch(String textSearch, String clientId) {
        if (textSearch != null) {
            return clientId.toLowerCase().contains(textSearch.toLowerCase());
        }
        return true;
    }

    private boolean filterClientSubscriptionsByClientId(ClientSubscriptionQuery query, String clientId) {
        if (query.getClientId() != null) {
            return clientId.toLowerCase().contains(query.getClientId().toLowerCase());
        }
        return true;
    }

    private boolean filterClientSubscriptionByTopicFilter(ClientSubscriptionQuery query, ClientSubscriptionInfoDto subscriptionInfoDto) {
        if (query.getTopicFilter() != null) {
            return subscriptionInfoDto.getSubscription().getTopicFilter().toLowerCase().contains(query.getTopicFilter().toLowerCase());
        }
        return true;
    }

    private boolean filterClientSubscriptionByQos(Set<Integer> qosSet, ClientSubscriptionInfoDto subscriptionInfoDto) {
        return CollectionUtils.isEmpty(qosSet) || qosSet.contains(subscriptionInfoDto.getSubscription().getQos().value());
    }

    private boolean filterClientSubscriptionBySubOption(List<Boolean> subOptionsList, boolean value) {
        return CollectionUtils.isEmpty(subOptionsList) || subOptionsList.size() != 1 || value == subOptionsList.get(0);
    }

    private boolean filterClientSubscriptionByRetainHandling(Set<Integer> retainHandlingSet, ClientSubscriptionInfoDto subscriptionInfoDto) {
        return CollectionUtils.isEmpty(retainHandlingSet) || retainHandlingSet.contains(subscriptionInfoDto.getSubscription().getOptions().getRetainHandling());
    }

    private boolean filterBySubscriptionId(ClientSubscriptionQuery query, ClientSubscriptionInfoDto subscriptionInfoDto) {
        return query.getSubscriptionId() == null || query.getSubscriptionId().equals(subscriptionInfoDto.getSubscription().getSubscriptionId());
    }

    private ClientSubscriptionInfoDto newClientSubscriptionInfoDto(String clientId, TopicSubscription topicSubscription) {
        return ClientSubscriptionInfoDto
                .builder()
                .clientId(clientId)
                .subscription(SubscriptionInfoDto.fromTopicSubscription(topicSubscription))
                .build();
    }

}
