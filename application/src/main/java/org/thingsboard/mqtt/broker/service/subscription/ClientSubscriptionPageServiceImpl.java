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
package org.thingsboard.mqtt.broker.service.subscription;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.subscription.ClientSubscriptionQuery;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.dto.ClientSubscriptionInfoDto;
import org.thingsboard.mqtt.broker.dto.SubscriptionInfoDto;

import java.util.ArrayList;
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

        PageLink pageLink = query.getPageLink();
        Set<Integer> qosSet = query.getQosList();
        List<Boolean> noLocalList = query.getNoLocalList();
        List<Boolean> retainAsPublishList = query.getRetainAsPublishList();
        Set<Integer> retainHandlingSet = query.getRetainHandlingList();
        Integer subscriptionId = query.getSubscriptionId();

        List<ClientSubscriptionInfoDto> filteredSubscriptions = new ArrayList<>(allClientSubscriptions.size());

        List<ClientSubscriptionInfoDto> filteredByClientId = new ArrayList<>();
        for (var subscritionsEntry : allClientSubscriptions.entrySet()) {
            if (!filterClientSubscriptionsByTextSearch(pageLink.getTextSearch(), subscritionsEntry.getKey())) {
                continue;
            }
            if (!filterClientSubscriptionsByClientId(query, subscritionsEntry.getKey())) {
                continue;
            }
            filteredByClientId.addAll(convertToClientSubscriptionInfoDtoList(subscritionsEntry));
        }

        for (var subscriptionInfoDto : filteredByClientId) {
            if (!filterClientSubscriptionByTopicFilter(query, subscriptionInfoDto)) {
                continue;
            }
            if (!CollectionUtils.isEmpty(qosSet)) {
                if (!qosSet.contains(subscriptionInfoDto.getSubscription().getQos().value())) {
                    continue;
                }
            }
            if (!CollectionUtils.isEmpty(noLocalList) && noLocalList.size() == 1) {
                if (subscriptionInfoDto.getSubscription().getOptions().isNoLocal() != noLocalList.get(0)) {
                    continue;
                }
            }
            if (!CollectionUtils.isEmpty(retainAsPublishList) && retainAsPublishList.size() == 1) {
                if (subscriptionInfoDto.getSubscription().getOptions().isRetainAsPublish() != retainAsPublishList.get(0)) {
                    continue;
                }
            }
            if (!CollectionUtils.isEmpty(retainHandlingSet)) {
                if (!retainHandlingSet.contains(subscriptionInfoDto.getSubscription().getOptions().getRetainHandling())) {
                    continue;
                }
            }
            if (subscriptionId != null) {
                if (!subscriptionId.equals(subscriptionInfoDto.getSubscription().getSubscriptionId())) {
                    continue;
                }
            }

            filteredSubscriptions.add(subscriptionInfoDto);
        }

        return mapToPageDataResponse(filteredSubscriptions, pageLink);
    }

    private PageData<ClientSubscriptionInfoDto> mapToPageDataResponse(List<ClientSubscriptionInfoDto> filteredClientSubscriptions, PageLink pageLink) {
        List<ClientSubscriptionInfoDto> data = filteredClientSubscriptions.stream()
                .sorted(sorted(pageLink))
                .skip((long) pageLink.getPage() * pageLink.getPageSize())
                .limit(pageLink.getPageSize())
                .collect(Collectors.toList());

        int totalPages = (int) Math.ceil((double) filteredClientSubscriptions.size() / pageLink.getPageSize());
        return new PageData<>(data,
                totalPages,
                filteredClientSubscriptions.size(),
                pageLink.getPage() < totalPages - 1);
    }

    private Comparator<? super ClientSubscriptionInfoDto> sorted(PageLink pageLink) {
        return pageLink.getSortOrder() == null ? (o1, o2) -> 0 :
                Comparator.nullsLast(ClientSubscriptionInfoDto.getComparator(pageLink.getSortOrder()));
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

    public List<ClientSubscriptionInfoDto> convertToClientSubscriptionInfoDtoList(Map.Entry<String, Set<TopicSubscription>> entry) {
        List<ClientSubscriptionInfoDto> result = new ArrayList<>();
        for (TopicSubscription topicSubscription : entry.getValue()) {
            result.add(newClientSubscriptionInfoDto(entry.getKey(), topicSubscription));
        }
        return result;
    }

    private ClientSubscriptionInfoDto newClientSubscriptionInfoDto(String clientId, TopicSubscription topicSubscription) {
        return ClientSubscriptionInfoDto
                .builder()
                .clientId(clientId)
                .subscription(SubscriptionInfoDto.fromTopicSubscription(topicSubscription))
                .build();
    }

}
