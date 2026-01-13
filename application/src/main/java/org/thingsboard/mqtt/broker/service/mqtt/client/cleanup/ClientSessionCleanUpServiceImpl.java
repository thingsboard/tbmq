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
package org.thingsboard.mqtt.broker.service.mqtt.client.cleanup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.service.mqtt.client.disconnect.DisconnectClientCommandService;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventService;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.data.ClientCleanupInfo;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCtxService;
import org.thingsboard.mqtt.broker.util.ClientSessionInfoFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.thingsboard.mqtt.broker.session.DisconnectReasonType.ON_ADMINISTRATIVE_ACTION;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientSessionCleanUpServiceImpl implements ClientSessionCleanUpService {

    private final ClientSessionCache clientSessionCache;
    private final ClientSessionCtxService clientSessionCtxService;
    private final ClientSessionEventService clientSessionEventService;
    private final DisconnectClientCommandService disconnectClientCommandService;
    private final ServiceInfoProvider serviceInfoProvider;

    @Value("${mqtt.client-session-expiry.ttl:0}")
    private int ttl;

    @Override
    public void removeClientSession(String clientId, UUID sessionId) throws ThingsboardException {
        log.trace("[{}] Removing ClientSession.", clientId);
        ClientSessionInfo clientSessionInfo = clientSessionCache.getClientSessionInfo(clientId);
        if (clientSessionInfo == null || differentSession(sessionId, clientSessionInfo)) {
            throw new ThingsboardException("No such client session", ThingsboardErrorCode.ITEM_NOT_FOUND);
        }
        if (clientSessionInfo.isConnected()) {
            throw new ThingsboardException("Client is currently connected", ThingsboardErrorCode.GENERAL);
        }
        log.debug("[{}] Cleaning up client session.", clientId);
        clientSessionEventService.requestClientSessionCleanup(clientSessionInfo, ClientCleanupInfo.GRACEFUL);
    }

    @Override
    public void disconnectClientSession(String clientId, UUID sessionId) throws ThingsboardException {
        log.trace("[{}][{}] Disconnecting ClientSession.", clientId, sessionId);
        ClientSessionInfo clientSessionInfo = clientSessionCache.getClientSessionInfo(clientId);
        if (clientSessionInfo == null || differentSession(sessionId, clientSessionInfo)) {
            throw new ThingsboardException("No such client session", ThingsboardErrorCode.ITEM_NOT_FOUND);
        }
        if (!clientSessionInfo.isConnected()) {
            return;
        }
        String serviceId = clientSessionInfo.getServiceId();
        disconnectClientCommandService.disconnectSessionOnAdminAction(serviceId, clientId, sessionId, true);
    }

    @Override
    public void disconnectClientSession(String clientId) {
        log.trace("[{}] Disconnecting ClientSession", clientId);
        ClientSessionInfo clientSessionInfo = clientSessionCache.getClientSessionInfo(clientId);
        if (clientSessionInfo == null || !clientSessionInfo.isConnected()) {
            return;
        }
        disconnectClientCommandService.disconnectSessionOnAdminAction(
                clientSessionInfo.getServiceId(),
                clientId,
                clientSessionInfo.getSessionId(),
                true);
    }

    private boolean differentSession(UUID sessionId, ClientSessionInfo clientSessionInfo) {
        return !sessionId.equals(clientSessionInfo.getSessionId());
    }

    @Scheduled(cron = "${mqtt.client-session-expiry.cron}", zone = "${mqtt.client-session-expiry.zone}")
    public void cleanUp() {
        log.info("Starting cleaning up expired ClientSessions.");

        long now = System.currentTimeMillis();
        String myServiceId = serviceInfoProvider.getServiceId();

        Map<String, ClientSessionInfo> sessions = clientSessionCache.getAllClientSessions();

        int removedCount = 0;
        int fixedGhostCount = 0;

        for (ClientSessionInfo info : sessions.values()) {
            if (!info.getServiceId().equals(myServiceId)) {
                continue;
            }

            if (info.isConnected()) {
                boolean stateFixed = tryFixGhostSessionState(info);
                if (stateFixed) {
                    fixedGhostCount++;
                }
            } else {
                Long expiryMs = resolveSessionExpiryIntervalMs(info);
                if (expiryMs == null) {
                    continue;
                }

                boolean removed = tryCleanupDisconnectedSession(info, expiryMs, now);
                if (removed) {
                    removedCount++;
                }
            }
        }

        if (removedCount > 0 || fixedGhostCount > 0) {
            log.info("Session Cleanup Report: Removed [{}] expired, Fixed [{}] ghost sessions.", removedCount, fixedGhostCount);
        } else {
            log.info("No expired or ghost client sessions found.");
        }
    }

    private boolean tryFixGhostSessionState(ClientSessionInfo info) {
        if (clientSessionCtxService.hasSession(info.getClientId())) {
            return false;
        }

        log.info("[{}][{}] Ghost session detected (No physical connection). Marking as Disconnected.",
                info.getClientId(), info.getSessionId());
        clientSessionEventService.notifyClientDisconnected(
                ClientSessionInfoFactory.clientSessionInfoToSessionInfo(info),
                ON_ADMINISTRATIVE_ACTION,
                null
        );

        return true;
    }

    private boolean tryCleanupDisconnectedSession(ClientSessionInfo info, long expiryMs, long now) {
        if (isExpired(info, expiryMs, now)) {
            clientSessionEventService.requestClientSessionCleanup(info, ClientCleanupInfo.GRACEFUL);
            return true;
        }
        return false;
    }

    boolean isNotCleanSession(ClientSessionInfo sessionInfo) {
        return sessionInfo.isNotCleanSession();
    }

    private Long resolveSessionExpiryIntervalMs(ClientSessionInfo session) {
        if (isNotCleanSession(session)) {
            return ttl > 0 ? toMillis(ttl) : null;
        }
        return toMillis(session.safeGetSessionExpiryInterval());
    }

    private boolean isExpired(ClientSessionInfo info, long expiryIntervalMs, long now) {
        return info.getDisconnectedAt() + expiryIntervalMs < now;
    }

    private long toMillis(int seconds) {
        return TimeUnit.SECONDS.toMillis(seconds);
    }
}
