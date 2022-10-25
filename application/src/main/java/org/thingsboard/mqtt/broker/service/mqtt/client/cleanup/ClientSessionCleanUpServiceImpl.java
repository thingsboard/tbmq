/**
 * Copyright © 2016-2022 The Thingsboard Authors
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.service.mqtt.ClientSession;
import org.thingsboard.mqtt.broker.service.mqtt.client.disconnect.DisconnectClientCommandService;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventService;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionInfo;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientSessionCleanUpServiceImpl implements ClientSessionCleanUpService {

    private final ClientSessionCache clientSessionCache;
    private final ClientSessionEventService clientSessionEventService;
    private final DisconnectClientCommandService disconnectClientCommandService;

    @Override
    public void removeClientSession(String clientId, UUID sessionId) throws ThingsboardException {
        log.trace("[{}] Removing ClientSession.", clientId);
        ClientSessionInfo clientSessionInfo = clientSessionCache.getClientSessionInfo(clientId);
        if (clientSessionInfo == null || !sameSession(sessionId, clientSessionInfo)) {
            throw new ThingsboardException("No such client session", ThingsboardErrorCode.ITEM_NOT_FOUND);
        }
        if (clientSessionInfo.getClientSession().isConnected()) {
            throw new ThingsboardException("Client is currently connected", ThingsboardErrorCode.GENERAL);
        }
        log.debug("[{}] Cleaning up client session.", clientId);
        clientSessionEventService.requestSessionCleanup(clientSessionInfo.getClientSession().getSessionInfo());
    }

    @Override
    public void disconnectClientSession(String clientId, UUID sessionId) throws ThingsboardException {
        log.trace("[{}][{}] Disconnecting ClientSession.", clientId, sessionId);
        ClientSessionInfo clientSessionInfo = clientSessionCache.getClientSessionInfo(clientId);
        if (clientSessionInfo == null || !sameSession(sessionId, clientSessionInfo)) {
            throw new ThingsboardException("No such client session", ThingsboardErrorCode.ITEM_NOT_FOUND);
        }
        if (!clientSessionInfo.getClientSession().isConnected()) {
            return;
        }
        String serviceId = clientSessionInfo.getClientSession().getSessionInfo().getServiceId();
        disconnectClientCommandService.disconnectSession(serviceId, clientId, sessionId, false);
    }

    private boolean sameSession(UUID sessionId, ClientSessionInfo clientSessionInfo) {
        UUID currentSessionId = clientSessionInfo.getClientSession().getSessionInfo().getSessionId();
        return sessionId.equals(currentSessionId);
    }

    @Scheduled(cron = "${mqtt.client-session-expiry.cron}", zone = "${mqtt.client-session-expiry.zone}")
    public void cleanUp() {
        log.info("Starting cleaning up expired ClientSessions.");

        long currentTs = System.currentTimeMillis();
        Map<String, ClientSessionInfo> clientSessionInfos = clientSessionCache.getAllClientSessions();

        List<SessionInfo> clientSessionsToRemove = clientSessionInfos.values().stream()
                .filter(this::isNotPersistent)
                .filter(clientSessionInfo -> needsToBeRemoved(currentTs, clientSessionInfo))
                .map(ClientSessionInfo::getClientSession)
                .map(ClientSession::getSessionInfo)
                .collect(Collectors.toList());

        log.info("Cleaning up expired {} client sessions.", clientSessionsToRemove.size());
        for (SessionInfo sessionInfo : clientSessionsToRemove) {
            clientSessionEventService.requestSessionCleanup(sessionInfo);
        }
    }

    boolean isNotPersistent(ClientSessionInfo clientSessionInfo) {
        return !clientSessionInfo.getClientSession().getSessionInfo().isPersistent() ||
                clientSessionInfo.getClientSession().getSessionInfo().getSessionExpiryInterval() != 0;
    }

    private boolean needsToBeRemoved(long currentTs, ClientSessionInfo clientSessionInfo) {
        long sessionExpiryIntervalMs = TimeUnit.SECONDS.toMillis(clientSessionInfo.getClientSession().getSessionInfo().getSessionExpiryInterval());
        return clientSessionInfo.getLastUpdateTime() + sessionExpiryIntervalMs < currentTs
                && !clientSessionInfo.getClientSession().isConnected();
    }
}
