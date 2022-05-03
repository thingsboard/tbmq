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
package org.thingsboard.mqtt.broker.service.mqtt.client.session;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.ConnectionState;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.dto.ShortClientSessionInfoDto;
import org.thingsboard.mqtt.broker.service.mqtt.ClientSession;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientSessionPageInfosImpl implements ClientSessionPageInfos {

    private final ClientSessionCache clientSessionCache;

    @Override
    public PageData<ShortClientSessionInfoDto> getClientSessionInfos(PageLink pageLink) {
        Map<String, ClientSessionInfo> allClientSessions = clientSessionCache.getAllClientSessions();

        List<ClientSessionInfo> filteredByTextSearch = filterClientSessionInfos(allClientSessions, pageLink);

        List<ShortClientSessionInfoDto> data = filteredByTextSearch.stream()
                .skip((long) pageLink.getPage() * pageLink.getPageSize())
                .limit(pageLink.getPageSize())
                .map(ClientSessionInfo::getClientSession)
                .map(clientSession -> ShortClientSessionInfoDto.builder()
                        .id(clientSession.getSessionInfo().getClientInfo().getClientId())
                        .clientId(clientSession.getSessionInfo().getClientInfo().getClientId())
                        .clientType(clientSession.getSessionInfo().getClientInfo().getType())
                        .connectionState(clientSession.isConnected() ? ConnectionState.CONNECTED : ConnectionState.DISCONNECTED)
                        .nodeId(clientSession.getSessionInfo().getServiceId())
                        .build())
                .sorted(sorted(pageLink))
                .collect(Collectors.toList());

        return new PageData<>(data,
                filteredByTextSearch.size() / pageLink.getPageSize(),
                filteredByTextSearch.size(),
                pageLink.getPageSize() + pageLink.getPage() * pageLink.getPageSize() < filteredByTextSearch.size());
    }

    private Comparator<? super ShortClientSessionInfoDto> sorted(PageLink pageLink) {
        return pageLink.getSortOrder() == null ? (o1, o2) -> 0 :
                Comparator.nullsLast(ShortClientSessionInfoDto.getComparator(pageLink.getSortOrder()));
    }

    private List<ClientSessionInfo> filterClientSessionInfos(Map<String, ClientSessionInfo> allClientSessions, PageLink pageLink) {
        return allClientSessions.values().stream()
                .filter(clientSessionInfo -> filter(pageLink, clientSessionInfo.getClientSession()))
                .collect(Collectors.toList());
    }

    private boolean filter(PageLink pageLink, ClientSession clientSession) {
        if (pageLink.getTextSearch() != null) {
            return clientSession.getSessionInfo().getClientInfo().getClientId().contains(pageLink.getTextSearch());
        }
        return true;
    }
}
