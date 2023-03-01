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
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.ConnectionState;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.dto.ShortClientSessionInfoDto;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscriptionCache;

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

        List<ClientSessionInfo> filteredByTextSearch = filterClientSessionInfos(allClientSessions, pageLink);

        List<ShortClientSessionInfoDto> data = filteredByTextSearch.stream()
                .skip((long) pageLink.getPage() * pageLink.getPageSize())
                .limit(pageLink.getPageSize())
                .map(this::toShortSessionInfo)
                .sorted(sorted(pageLink))
                .collect(Collectors.toList());

        return new PageData<>(data,
                filteredByTextSearch.size() / pageLink.getPageSize(),
                filteredByTextSearch.size(),
                pageLink.getPageSize() + pageLink.getPage() * pageLink.getPageSize() < filteredByTextSearch.size());
    }

    private ShortClientSessionInfoDto toShortSessionInfo(ClientSessionInfo clientSessionInfo) {
        return ShortClientSessionInfoDto.builder()
                .id(clientSessionInfo.getClientId())
                .clientId(clientSessionInfo.getClientId())
                .clientType(clientSessionInfo.getType())
                .connectionState(clientSessionInfo.isConnected() ? ConnectionState.CONNECTED : ConnectionState.DISCONNECTED)
                .nodeId(clientSessionInfo.getServiceId())
                .sessionId(clientSessionInfo.getSessionId())
                .subscriptionsCount(clientSubscriptionCache.getClientSubscriptions(clientSessionInfo.getClientId()).size())
                .connectedAt(clientSessionInfo.getConnectedAt())
                .disconnectedAt(clientSessionInfo.getDisconnectedAt())
                .clientIpAdr(clientSessionInfo.getClientIpAdr())
                .cleanStart(clientSessionInfo.isCleanStart())
                .build();
    }

    private Comparator<? super ShortClientSessionInfoDto> sorted(PageLink pageLink) {
        return pageLink.getSortOrder() == null ? (o1, o2) -> 0 :
                Comparator.nullsLast(ShortClientSessionInfoDto.getComparator(pageLink.getSortOrder()));
    }

    private List<ClientSessionInfo> filterClientSessionInfos(Map<String, ClientSessionInfo> allClientSessions, PageLink pageLink) {
        return allClientSessions.values().stream()
                .filter(clientSessionInfo -> filter(pageLink, clientSessionInfo))
                .collect(Collectors.toList());
    }

    private boolean filter(PageLink pageLink, ClientSessionInfo clientSessionInfo) {
        if (pageLink.getTextSearch() != null) {
            return clientSessionInfo.getClientId().contains(pageLink.getTextSearch());
        }
        return true;
    }
}
