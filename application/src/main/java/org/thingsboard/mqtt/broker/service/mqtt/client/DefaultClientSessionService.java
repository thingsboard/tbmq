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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.service.mqtt.ClientSession;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/*
    Not thread-safe for the same clientId
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultClientSessionService implements ClientSessionService {
    private static final QueueProtos.ClientSessionProto EMPTY_CLIENT_SESSION_PROTO = QueueProtos.ClientSessionProto.newBuilder().build();

    private final Map<String, ClientSession> clientSessionMap = new ConcurrentHashMap<>();

    private final ClientSessionPersistenceService clientSessionPersistenceService;

    @PostConstruct
    public void init() {
        loadPersistedClientSessions();
    }

    @Override
    public Map<String, ClientSession> getPersistedClientSessions() {
        return clientSessionMap.entrySet().stream()
                .filter(entry -> entry.getValue().getSessionInfo().isPersistent())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public ClientSession getClientSession(String clientId) {
        return clientSessionMap.get(clientId);
    }

    @Override
    public void saveClientSession(String clientId, ClientSession clientSession) {
        if (!clientId.equals(clientSession.getSessionInfo().getClientInfo().getClientId())) {
            log.error("Error saving client session. " +
                    "Key clientId - {}, ClientSession's clientId - {}.", clientId, clientSession.getSessionInfo().getClientInfo().getClientId());
            throw new MqttException("Key clientId should be equals to ClientSession's clientId");
        }

        clientSessionMap.put(clientId, clientSession);

        QueueProtos.ClientSessionProto clientSessionProto = ProtoConverter.convertToClientSessionProto(clientSession);
        clientSessionPersistenceService.persistClientSession(clientId, clientSessionProto);
    }

    @Override
    public void clearClientSession(String clientId) {
        ClientSession removedClientSession = clientSessionMap.remove(clientId);
        if (removedClientSession == null) {
            log.warn("[{}] No client session found.", clientId);
        }
        clientSessionPersistenceService.persistClientSession(clientId, EMPTY_CLIENT_SESSION_PROTO);
    }

    private void loadPersistedClientSessions() {
        log.info("Load persisted client sessions.");
        Map<String, ClientSession> allClientSessions = clientSessionPersistenceService.loadAllClientSessions();
        allClientSessions.forEach((clientId, clientSession) -> {
            if (clientSession.getSessionInfo().isPersistent()) {
                this.clientSessionMap.put(clientId, clientSession);
            } else {
                log.debug("[{}] Clearing not persistent client session.", clientId);
                clientSessionPersistenceService.persistClientSession(clientId, EMPTY_CLIENT_SESSION_PROTO);
            }
        });
    }
}
