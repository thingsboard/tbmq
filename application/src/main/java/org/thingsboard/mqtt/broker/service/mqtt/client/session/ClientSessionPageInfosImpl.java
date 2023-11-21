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
package org.thingsboard.mqtt.broker.service.mqtt.client.session;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.ClientSessionQuery;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.ConnectionState;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.page.TimePageLink;
import org.thingsboard.mqtt.broker.dto.ClientSessionStatsInfoDto;
import org.thingsboard.mqtt.broker.dto.ShortClientSessionInfoDto;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscriptionCache;
import org.thingsboard.mqtt.broker.util.BytesUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientSessionPageInfosImpl implements ClientSessionPageInfos {

    private final ClientSessionCache clientSessionCache;
    private final ClientSubscriptionCache clientSubscriptionCache;

    @Override
    public PageData<ShortClientSessionInfoDto> getClientSessionInfos(PageLink pageLink) {
        Map<String, ClientSessionInfo> allClientSessions = clientSessionCache.getAllClientSessions();
        List<ClientSessionInfo> filteredClientSessionInfos = filterClientSessionInfos(allClientSessions, pageLink.getTextSearch());
        return mapToPageDataResponse(filteredClientSessionInfos, pageLink);
    }

    // TODO: 26.09.23 improve the performance of this two methods (do mapping toShortSessionInfo at the end, mb use parallel processing)

    @Override
    public PageData<ShortClientSessionInfoDto> getClientSessionInfos(ClientSessionQuery query) {
        var allClientSessions = clientSessionCache.getAllClientSessions();

        TimePageLink pageLink = query.getPageLink();
        Long startTime = pageLink.getStartTime();
        Long endTime = pageLink.getEndTime();
        List<ConnectionState> connectedStatusList = query.getConnectedStatusList();
        List<ClientType> clientTypeList = query.getClientTypeList();
        List<Boolean> cleanStartList = query.getCleanStartList();
        List<String> nodeIdList = query.getNodeIdList();
        Integer subscriptions = query.getSubscriptions();

        List<ClientSessionInfo> filteredClientSessionInfos = new ArrayList<>(allClientSessions.size());
        for (ClientSessionInfo clientSessionInfo : allClientSessions.values()) {
            if (!filterClientSessionByTextSearch(pageLink.getTextSearch(), clientSessionInfo)) {
                continue;
            }
            if (!CollectionUtils.isEmpty(connectedStatusList) && connectedStatusList.size() == 1) {
                if (getSessionConnectionState(clientSessionInfo) != connectedStatusList.get(0)) {
                    continue;
                }
            }
            if (!CollectionUtils.isEmpty(clientTypeList) && clientTypeList.size() == 1) {
                if (clientSessionInfo.getType() != clientTypeList.get(0)) {
                    continue;
                }
            }
            if (!CollectionUtils.isEmpty(cleanStartList) && cleanStartList.size() == 1) {
                if (clientSessionInfo.isCleanStart() != cleanStartList.get(0)) {
                    continue;
                }
            }
            if (subscriptions != null) {
                if (subscriptions != getSubscriptionsCount(clientSessionInfo)) {
                    continue;
                }
            }
            if (!CollectionUtils.isEmpty(nodeIdList)) {
                if (!nodeIdList.contains(clientSessionInfo.getServiceId())) {
                    continue;
                }
            }
            if (startTime != null && endTime != null) {
                if (clientSessionInfo.isConnected()) {
                    if (isOutOfTimeRange(clientSessionInfo.getConnectedAt(), startTime, endTime)) continue;
                } else {
                    if (isOutOfTimeRange(clientSessionInfo.getDisconnectedAt(), startTime, endTime)) continue;
                }
            }
            filteredClientSessionInfos.add(clientSessionInfo);
        }
        return mapToPageDataResponse(filteredClientSessionInfos, pageLink);
    }

    private PageData<ShortClientSessionInfoDto> mapToPageDataResponse(List<ClientSessionInfo> filteredClientSessionInfos, PageLink pageLink) {
        List<ShortClientSessionInfoDto> data = filteredClientSessionInfos.stream()
                .map(this::toShortSessionInfo)
                .sorted(sorted(pageLink))
                .skip((long) pageLink.getPage() * pageLink.getPageSize())
                .limit(pageLink.getPageSize())
                .collect(Collectors.toList());

        int totalPages = (int) Math.ceil((double) filteredClientSessionInfos.size() / pageLink.getPageSize());
        return new PageData<>(data,
                totalPages,
                filteredClientSessionInfos.size(),
                pageLink.getPage() < totalPages - 1);
    }

    private static boolean isOutOfTimeRange(long ts, long startTime, long endTime) {
        return ts < startTime || ts > endTime;
    }

    @Override
    public ClientSessionStatsInfoDto getClientSessionStatsInfo() {
        var allClientSessions = clientSessionCache.getAllClientSessions();
        int totalCount = allClientSessions.size();
        long connectedCount = allClientSessions.values().stream().filter(ClientSessionInfo::isConnected).count();
        long disconnectedCount = totalCount - connectedCount;
        return new ClientSessionStatsInfoDto(connectedCount, disconnectedCount, totalCount);
    }

    private ShortClientSessionInfoDto toShortSessionInfo(ClientSessionInfo clientSessionInfo) {
        return ShortClientSessionInfoDto.builder()
                .id(clientSessionInfo.getClientId())
                .clientId(clientSessionInfo.getClientId())
                .clientType(clientSessionInfo.getType())
                .connectionState(getSessionConnectionState(clientSessionInfo))
                .nodeId(clientSessionInfo.getServiceId())
                .sessionId(clientSessionInfo.getSessionId())
                .subscriptionsCount(getSubscriptionsCount(clientSessionInfo))
                .connectedAt(clientSessionInfo.getConnectedAt())
                .disconnectedAt(clientSessionInfo.getDisconnectedAt())
                .clientIpAdr(BytesUtil.toHostAddress(clientSessionInfo.getClientIpAdr()))
                .cleanStart(clientSessionInfo.isCleanStart())
                .build();
    }

    private ConnectionState getSessionConnectionState(ClientSessionInfo clientSessionInfo) {
        return clientSessionInfo.isConnected() ? ConnectionState.CONNECTED : ConnectionState.DISCONNECTED;
    }

    private int getSubscriptionsCount(ClientSessionInfo clientSessionInfo) {
        return clientSubscriptionCache.getClientSubscriptions(clientSessionInfo.getClientId()).size();
    }

    private Comparator<? super ShortClientSessionInfoDto> sorted(PageLink pageLink) {
        return pageLink.getSortOrder() == null ? (o1, o2) -> 0 :
                Comparator.nullsLast(ShortClientSessionInfoDto.getComparator(pageLink.getSortOrder()));
    }

    private List<ClientSessionInfo> filterClientSessionInfos(Map<String, ClientSessionInfo> allClientSessions, String textSearch) {
        return allClientSessions.values().stream()
                .filter(clientSessionInfo -> filterClientSessionByTextSearch(textSearch, clientSessionInfo))
                .collect(Collectors.toList());
    }

    private boolean filterClientSessionByTextSearch(String textSearch, ClientSessionInfo clientSessionInfo) {
        if (textSearch != null) {
            return clientSessionInfo.getClientId().toLowerCase().contains(textSearch.toLowerCase());
        }
        return true;
    }
}
