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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientSessionPageReaderImpl implements ClientSessionPageReader {
    private final ClientSessionReader clientSessionReader;

    @Override
    public PageData<ShortClientSessionInfoDto> getClientSessionInfos(PageLink pageLink) {
        // TODO: add some sorting
        Map<String, ClientSessionInfo> allClientSessions = clientSessionReader.getAllClientSessions();
        List<ShortClientSessionInfoDto> data = allClientSessions.values().stream()
                .skip(pageLink.getPage() * pageLink.getPageSize())
                .limit(pageLink.getPageSize())
                .map(ClientSessionInfo::getClientSession)
                .map(clientSession -> ShortClientSessionInfoDto.builder()
                        .id(clientSession.getSessionInfo().getSessionId())
                        .clientId(clientSession.getSessionInfo().getClientInfo().getClientId())
                        .clientType(clientSession.getSessionInfo().getClientInfo().getType())
                        .connectionState(clientSession.isConnected() ? ConnectionState.CONNECTED : ConnectionState.DISCONNECTED)
                        .nodeId(clientSession.getSessionInfo().getServiceId())
                        .build())
                .collect(Collectors.toList());
        return new PageData<>(data, allClientSessions.size() / pageLink.getPageSize(),
                allClientSessions.size(),
                pageLink.getPageSize() + pageLink.getPage() * pageLink.getPageSize() < allClientSessions.size());
    }
}
