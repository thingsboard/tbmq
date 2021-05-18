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
package org.thingsboard.mqtt.broker.service.mqtt.client.cleanup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.service.mqtt.ClientSession;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionInfo;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionService;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventService;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientSessionCleanUpServiceImpl implements ClientSessionCleanUpService {
    private final ClientSessionService clientSessionService;
    private final ClientSessionEventService clientSessionEventService;

    @Value("${mqtt.client-session-cleanup.inactive-session-ttl}")
    private int ttl;

    @Override
    public void removeClientSession(String clientId) {
        ClientSessionInfo clientSessionInfo = clientSessionService.getClientSessionInfo(clientId);
        if (clientSessionInfo == null) {
            throw new RuntimeException("Client Session doesn't exist for provided clientId.");
        }
        if (clientSessionInfo.getClientSession().isConnected()) {
            throw new RuntimeException("Client is currently connected!");
        }
        log.debug("[{}] Cleaning up client session.", clientId);
        clientSessionEventService.tryClear(clientSessionInfo.getClientSession().getSessionInfo());
    }

    @Scheduled(cron = "${mqtt.client-session-cleanup.cron}", zone = "${mqtt.client-session-cleanup.zone}")
    public void cleanUp() {
        log.info("Starting cleaning up stale ClientSessions.");

        long oldestAllowedTime = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(ttl);
        Map<String, ClientSessionInfo> persistedClientSessionInfos = clientSessionService.getPersistedClientSessionInfos();


        List<SessionInfo> clientSessionToRemove = persistedClientSessionInfos.values().stream()
                .filter(clientSessionInfo -> needsToBeRemoved(oldestAllowedTime, clientSessionInfo))
                .map(ClientSessionInfo::getClientSession)
                .map(ClientSession::getSessionInfo)
                .collect(Collectors.toList());

        log.info("Cleaning up stale {} client sessions.", clientSessionToRemove.size());
        for (SessionInfo sessionInfo : clientSessionToRemove) {
            clientSessionEventService.tryClear(sessionInfo);
        }
    }

    private boolean needsToBeRemoved(long oldestAllowedTime, ClientSessionInfo clientSessionInfo) {
        return clientSessionInfo.getLastUpdateTime() < oldestAllowedTime && !clientSessionInfo.getClientSession().isConnected();
    }
}
