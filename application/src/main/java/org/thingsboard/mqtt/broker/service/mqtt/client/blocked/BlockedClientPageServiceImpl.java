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
package org.thingsboard.mqtt.broker.service.mqtt.client.blocked;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.page.TimePageLink;
import org.thingsboard.mqtt.broker.common.data.util.ComparableUtil;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.dto.BlockedClientDto;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.BlockedClient;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.BlockedClientQuery;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.BlockedClientType;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.RegexMatchTarget;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class BlockedClientPageServiceImpl implements BlockedClientPageService {

    private final BlockedClientService blockedClientService;

    @Override
    public PageData<BlockedClientDto> getBlockedClients(PageLink pageLink) {
        List<BlockedClient> filteredBlockedClients = blockedClientService.getBlockedClients()
                .values().stream()
                .flatMap(typeMap -> typeMap.values().stream())
                .filter(blockedClient -> filterByTextSearch(pageLink.getTextSearch(), blockedClient))
                .toList();
        return mapToPageDataResponse(filteredBlockedClients, pageLink);
    }

    private boolean filterByTextSearch(String textSearch, BlockedClient blockedClient) {
        if (textSearch != null) {
            return blockedClient.getValue().toLowerCase().contains(textSearch.toLowerCase());
        }
        return true;
    }

    private PageData<BlockedClientDto> mapToPageDataResponse(List<BlockedClient> filteredBlockedClients, PageLink pageLink) {
        List<BlockedClientDto> data = filteredBlockedClients.stream()
                .map(BlockedClientDto::newInstance)
                .sorted(sorted(pageLink))
                .skip((long) pageLink.getPage() * pageLink.getPageSize())
                .limit(pageLink.getPageSize())
                .collect(Collectors.toList());

        return PageData.of(data, filteredBlockedClients.size(), pageLink);
    }

    private Comparator<? super BlockedClientDto> sorted(PageLink pageLink) {
        return ComparableUtil.sorted(pageLink, BlockedClientDto::getComparator);
    }


    @Override
    public PageData<BlockedClientDto> getBlockedClients(BlockedClientQuery query) {
        Map<BlockedClientType, Map<String, BlockedClient>> blockedClients = blockedClientService.getBlockedClients();

        TimePageLink pageLink = query.getPageLink();
        String textSearch = pageLink.getTextSearch();
        Long startTime = pageLink.getStartTime();
        Long endTime = pageLink.getEndTime();
        Set<BlockedClientType> types = query.getTypes();
        String value = query.getValue();
        List<RegexMatchTarget> regexMatchTargets = query.getRegexMatchTargets();

        Predicate<BlockedClient> textSearchFilter = bc -> StringUtils.isEmpty(textSearch) || bc.getValue().toLowerCase().contains(textSearch.toLowerCase());
        Predicate<BlockedClient> timeFilter = bc -> (startTime == null || bc.getExpirationTime() >= startTime) && (endTime == null || bc.getExpirationTime() <= endTime);
        Predicate<BlockedClient> typeFilter = bc -> CollectionUtils.isEmpty(types) || types.contains(bc.getType());
        Predicate<BlockedClient> valueFilter = bc -> StringUtils.isEmpty(value) || bc.getValue().toLowerCase().contains(value.toLowerCase());
        Predicate<BlockedClient> regexMatchTargetFilter = bc -> CollectionUtils.isEmpty(regexMatchTargets) || bc.getRegexMatchTarget() != null && regexMatchTargets.contains(bc.getRegexMatchTarget());

        List<BlockedClient> filteredBlockedClients = blockedClients
                .values().stream()
                .flatMap(typeMap -> typeMap.values().stream())
                .filter(textSearchFilter.and(typeFilter).and(valueFilter).and(timeFilter).and(regexMatchTargetFilter))
                .toList();

        return mapToPageDataResponse(filteredBlockedClients, pageLink);
    }

}
