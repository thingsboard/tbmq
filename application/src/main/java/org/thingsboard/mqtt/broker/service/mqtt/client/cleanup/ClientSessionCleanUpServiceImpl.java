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
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;

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
        clientSessionEventService.requestClientSessionCleanup(clientSessionInfo);
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
        UUID currentSessionId = clientSessionInfo.getSessionId();
        return !sessionId.equals(currentSessionId);
    }

    @Scheduled(cron = "${mqtt.client-session-expiry.cron}", zone = "${mqtt.client-session-expiry.zone}")
    public void cleanUp() {
        log.info("Starting cleaning up expired ClientSessions.");

        long now = System.currentTimeMillis();
        String myServiceId = serviceInfoProvider.getServiceId();

        Map<String, ClientSessionInfo> sessions = clientSessionCache.getAllClientSessions();

        int removedCount = 0;
        for (ClientSessionInfo info : sessions.values()) {
            if (!info.getServiceId().equals(myServiceId)) {
                continue;
            }
            if (info.isConnected()) {
                continue;
            }

            Long expiryMs = resolveSessionExpiryIntervalMs(info);
            if (expiryMs == null) {
                continue;
            }

            if (isExpired(info, expiryMs, now)) {
                removedCount++;
                clientSessionEventService.requestClientSessionCleanup(info);
            }
        }

        log.info("Cleaning up expired {} client sessions.", removedCount);
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
