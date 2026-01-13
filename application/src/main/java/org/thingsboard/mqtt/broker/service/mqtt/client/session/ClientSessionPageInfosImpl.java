/**
 * Copyright © 2016-2026 The Thingsboard Authors
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
import org.thingsboard.mqtt.broker.common.data.client.session.SubscriptionOperation;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.page.TimePageLink;
import org.thingsboard.mqtt.broker.common.data.util.BytesUtil;
import org.thingsboard.mqtt.broker.common.data.util.ComparableUtil;
import org.thingsboard.mqtt.broker.dto.ClientSessionStatsInfoDto;
import org.thingsboard.mqtt.broker.dto.ShortClientSessionInfoDto;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscriptionCache;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    @Override
    public PageData<ShortClientSessionInfoDto> getClientSessionInfos(ClientSessionQuery query) {
        var allClientSessions = clientSessionCache.getAllClientSessions();

        TimePageLink pageLink = query.getPageLink();
        Long startTime = pageLink.getStartTime();
        Long endTime = pageLink.getEndTime();
        List<ConnectionState> connectedStatusList = query.getConnectedStatusList();
        List<ClientType> clientTypeList = query.getClientTypeList();
        List<Boolean> cleanStartList = query.getCleanStartList();
        Set<String> nodeIdSet = query.getNodeIdSet();
        Integer subscriptions = query.getSubscriptions();
        SubscriptionOperation operation = query.getOperation();
        String ipAddress = query.getClientIpAddress();

        List<ClientSessionInfo> filteredClientSessionInfos = allClientSessions
                .values()
                .parallelStream()
                .filter(clientSessionInfo -> filterClientSessionByTextSearch(pageLink.getTextSearch(), clientSessionInfo))
                .filter(clientSessionInfo -> filterByConnectedStatus(connectedStatusList, clientSessionInfo))
                .filter(clientSessionInfo -> filterByClientType(clientTypeList, clientSessionInfo))
                .filter(clientSessionInfo -> filterByCleanStart(cleanStartList, clientSessionInfo))
                .filter(clientSessionInfo -> filterBySubscriptions(subscriptions, operation, clientSessionInfo))
                .filter(clientSessionInfo -> filterByNodeId(nodeIdSet, clientSessionInfo))
                .filter(clientSessionInfo -> filterByClientIpAddress(ipAddress, clientSessionInfo))
                .filter(clientSessionInfo -> filterByTimeRange(startTime, endTime, clientSessionInfo))
                .toList();

        return mapToPageDataResponse(filteredClientSessionInfos, pageLink);
    }

    private PageData<ShortClientSessionInfoDto> mapToPageDataResponse(List<ClientSessionInfo> filteredClientSessionInfos, PageLink pageLink) {
        List<ShortClientSessionInfoDto> data = filteredClientSessionInfos.stream()
                .map(this::toShortSessionInfo)
                .sorted(sorted(pageLink))
                .skip((long) pageLink.getPage() * pageLink.getPageSize())
                .limit(pageLink.getPageSize())
                .collect(Collectors.toList());

        return PageData.of(data, filteredClientSessionInfos.size(), pageLink);
    }

    private static boolean isInTimeRange(long ts, long startTime, long endTime) {
        return ts >= startTime && ts <= endTime;
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
        return ComparableUtil.sorted(pageLink, ShortClientSessionInfoDto::getComparator);
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

    private boolean filterByConnectedStatus(List<ConnectionState> connectedStatusList, ClientSessionInfo clientSessionInfo) {
        return CollectionUtils.isEmpty(connectedStatusList) || connectedStatusList.size() != 1 || getSessionConnectionState(clientSessionInfo) == connectedStatusList.get(0);
    }

    private boolean filterByClientType(List<ClientType> clientTypeList, ClientSessionInfo clientSessionInfo) {
        return CollectionUtils.isEmpty(clientTypeList) || clientTypeList.size() != 1 || clientSessionInfo.getType() == clientTypeList.get(0);
    }

    private boolean filterByCleanStart(List<Boolean> cleanStartList, ClientSessionInfo clientSessionInfo) {
        return CollectionUtils.isEmpty(cleanStartList) || cleanStartList.size() != 1 || clientSessionInfo.isCleanStart() == cleanStartList.get(0);
    }

    private boolean filterBySubscriptions(Integer subscriptionsValue, SubscriptionOperation operation, ClientSessionInfo clientSessionInfo) {
        if (subscriptionsValue == null) {
            return true;
        }
        int sessionSubscriptionsCount = getSubscriptionsCount(clientSessionInfo);
        return switch (operation) {
            case EQUAL -> sessionSubscriptionsCount == subscriptionsValue;
            case NOT_EQUAL -> sessionSubscriptionsCount != subscriptionsValue;
            case GREATER -> sessionSubscriptionsCount > subscriptionsValue;
            case LESS -> sessionSubscriptionsCount < subscriptionsValue;
            case GREATER_OR_EQUAL -> sessionSubscriptionsCount >= subscriptionsValue;
            case LESS_OR_EQUAL -> sessionSubscriptionsCount <= subscriptionsValue;
        };
    }

    private boolean filterByNodeId(Set<String> nodeIdSet, ClientSessionInfo clientSessionInfo) {
        return CollectionUtils.isEmpty(nodeIdSet) || nodeIdSet.contains(clientSessionInfo.getServiceId());
    }

    private boolean filterByClientIpAddress(String ipAddress, ClientSessionInfo clientSessionInfo) {
        if (ipAddress == null) {
            return true;
        }
        String hostAddress = BytesUtil.toHostAddress(clientSessionInfo.getClientIpAdr());
        return hostAddress.toLowerCase().contains(ipAddress.toLowerCase());
    }

    private boolean filterByTimeRange(Long startTime, Long endTime, ClientSessionInfo clientSessionInfo) {
        if (startTime != null && endTime != null) {
            return clientSessionInfo.isConnected() ?
                    isInTimeRange(clientSessionInfo.getConnectedAt(), startTime, endTime) :
                    isInTimeRange(clientSessionInfo.getDisconnectedAt(), startTime, endTime);
        }
        return true;
    }
}
