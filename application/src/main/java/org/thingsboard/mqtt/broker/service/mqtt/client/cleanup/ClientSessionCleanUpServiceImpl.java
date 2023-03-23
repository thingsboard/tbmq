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
package org.thingsboard.mqtt.broker.service.mqtt.client.cleanup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.service.mqtt.client.disconnect.DisconnectClientCommandService;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventService;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.util.ClientSessionInfoFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientSessionCleanUpServiceImpl implements ClientSessionCleanUpService {

    private final ClientSessionCache clientSessionCache;
    private final ClientSessionEventService clientSessionEventService;
    private final DisconnectClientCommandService disconnectClientCommandService;

    @Override
    public void removeClientSession(String clientId, UUID sessionId) throws ThingsboardException {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Removing ClientSession.", clientId);
        }
        ClientSessionInfo clientSessionInfo = clientSessionCache.getClientSessionInfo(clientId);
        if (clientSessionInfo == null || differentSession(sessionId, clientSessionInfo)) {
            throw new ThingsboardException("No such client session", ThingsboardErrorCode.ITEM_NOT_FOUND);
        }
        if (clientSessionInfo.isConnected()) {
            throw new ThingsboardException("Client is currently connected", ThingsboardErrorCode.GENERAL);
        }
        if (log.isDebugEnabled()) {
            log.debug("[{}] Cleaning up client session.", clientId);
        }
        clientSessionEventService.requestSessionCleanup(ClientSessionInfoFactory.clientSessionInfoToSessionInfo(clientSessionInfo));
    }

    @Override
    public void disconnectClientSession(String clientId, UUID sessionId) throws ThingsboardException {
        if (log.isTraceEnabled()) {
            log.trace("[{}][{}] Disconnecting ClientSession.", clientId, sessionId);
        }
        ClientSessionInfo clientSessionInfo = clientSessionCache.getClientSessionInfo(clientId);
        if (clientSessionInfo == null || differentSession(sessionId, clientSessionInfo)) {
            throw new ThingsboardException("No such client session", ThingsboardErrorCode.ITEM_NOT_FOUND);
        }
        if (!clientSessionInfo.isConnected()) {
            return;
        }
        String serviceId = clientSessionInfo.getServiceId();
        disconnectClientCommandService.disconnectSession(serviceId, clientId, sessionId, true);
    }

    private boolean differentSession(UUID sessionId, ClientSessionInfo clientSessionInfo) {
        UUID currentSessionId = clientSessionInfo.getSessionId();
        return !sessionId.equals(currentSessionId);
    }

    @Scheduled(cron = "${mqtt.client-session-expiry.cron}", zone = "${mqtt.client-session-expiry.zone}")
    public void cleanUp() {
        log.info("Starting cleaning up expired ClientSessions.");

        long currentTs = System.currentTimeMillis();
        Map<String, ClientSessionInfo> clientSessionInfos = clientSessionCache.getAllClientSessions();

        List<SessionInfo> clientSessionsToRemove = new ArrayList<>();
        for (ClientSessionInfo clientSessionInfo : clientSessionInfos.values()) {
            SessionInfo sessionInfo = ClientSessionInfoFactory.clientSessionInfoToSessionInfo(clientSessionInfo);
            if (!isNotCleanSession(sessionInfo) && needsToBeRemoved(currentTs, clientSessionInfo, sessionInfo)) {
                clientSessionsToRemove.add(sessionInfo);
            }
        }

        log.info("Cleaning up expired {} client sessions.", clientSessionsToRemove.size());
        for (SessionInfo sessionInfo : clientSessionsToRemove) {
            clientSessionEventService.requestSessionCleanup(sessionInfo);
        }
    }

    boolean isNotCleanSession(SessionInfo sessionInfo) {
        return sessionInfo.isNotCleanSession();
    }

    private boolean needsToBeRemoved(long currentTs, ClientSessionInfo clientSessionInfo, SessionInfo sessionInfo) {
        long sessionExpiryIntervalMs = getSessionExpiryIntervalMs(sessionInfo);
        return isExpired(clientSessionInfo, sessionExpiryIntervalMs, currentTs)
                && !clientSessionInfo.isConnected();
    }

    private boolean isExpired(ClientSessionInfo clientSessionInfo, long sessionExpiryIntervalMs, long currentTs) {
        return clientSessionInfo.getDisconnectedAt() + sessionExpiryIntervalMs < currentTs;
    }

    private long getSessionExpiryIntervalMs(SessionInfo sessionInfo) {
        return TimeUnit.SECONDS.toMillis(sessionInfo.safeGetSessionExpiryInterval());
    }
}
