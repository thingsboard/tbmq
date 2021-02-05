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
package org.thingsboard.mqtt.broker.service.mqtt.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.service.mqtt.ClientSession;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.PersistenceSessionClearer;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class StandaloneClientSessionManager implements ClientSessionManager {
    private final Map<String, ClientSessionCtx> clientContextMap = new ConcurrentHashMap<>();

    private final AtomicInteger connectedClientsCounter;
    private final ClientSessionService clientSessionService;
    private final PersistenceSessionClearer persistenceSessionClearer;

    public StandaloneClientSessionManager(StatsManager statsManager, ClientSessionService clientSessionService, PersistenceSessionClearer persistenceSessionClearer) {
        this.connectedClientsCounter = statsManager.createConnectedClientsCounter();
        this.clientSessionService = clientSessionService;
        this.persistenceSessionClearer = persistenceSessionClearer;
    }

    @Override
    public void registerClient(SessionInfo sessionInfo, ClientSessionCtx clientSessionCtx) throws MqttException {
        String clientId = sessionInfo.getClientInfo().getClientId();
        ClientSession prevClientSession = clientSessionService.getClientSession(clientId);
        if (prevClientSession != null && prevClientSession.isConnected()) {
            throw new MqttException("Client with such clientId is already connected.");
        }
        ClientSession clientSession = ClientSession.builder()
                .connected(true)
                .clientInfo(sessionInfo.getClientInfo())
                .persistent(sessionInfo.isPersistent())
                .topicSubscriptions(prevClientSession != null ? prevClientSession.getTopicSubscriptions() : new HashSet<>())
                .build();
        clientSessionService.replaceClientSession(clientId, prevClientSession, clientSession);

        clientContextMap.put(clientId, clientSessionCtx);
        connectedClientsCounter.incrementAndGet();

        if (prevClientSession != null && prevClientSession.isPersistent() && !sessionInfo.isPersistent()) {
            persistenceSessionClearer.clearPersistedSession(sessionInfo.getClientInfo());
        }
    }

    @Override
    public void unregisterClient(String clientId) {
        ClientSession clientSession = clientSessionService.getClientSession(clientId);
        if (clientSession == null) {
            log.warn("[{}] Cannot find client session.", clientId);
            return;
        }

        log.trace("[{}] Unregistering client, persistent session - {}", clientId, clientSession.isPersistent());
        clientContextMap.remove(clientId);
        connectedClientsCounter.decrementAndGet();

        ClientSession newClientSession = clientSession.toBuilder()
                .connected(false)
                .topicSubscriptions(clientSession.isPersistent() ? clientSession.getTopicSubscriptions() : new HashSet<>())
                .build();
        clientSessionService.replaceClientSession(clientId, clientSession, newClientSession);
    }

    @Override
    public PersistedClientSession getPersistedClientInfo(String clientId) {
        return new PersistedClientSession(clientSessionService.getClientSession(clientId), clientContextMap.get(clientId));
    }
}
