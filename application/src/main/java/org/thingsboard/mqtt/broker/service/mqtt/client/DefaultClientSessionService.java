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
    private static final ClientSessionInfo EMPTY_CLIENT_SESSION_INFO = new ClientSessionInfo(null, 0);
    private static final QueueProtos.ClientSessionInfoProto EMPTY_CLIENT_SESSION_INFO_PROTO = QueueProtos.ClientSessionInfoProto.newBuilder().build();

    private Map<String, ClientSessionInfo> clientSessionMap;

    private final ClientSessionPersistenceService clientSessionPersistenceService;

    @PostConstruct
    public void init() {
        this.clientSessionMap = loadPersistedClientSessions();
    }

    @Override
    public Map<String, ClientSessionInfo> getPersistedClientSessionInfos() {
        return clientSessionMap.entrySet().stream()
                .filter(entry -> entry.getValue().getClientSession().getSessionInfo().isPersistent())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public ClientSession getClientSession(String clientId) {
        return clientSessionMap.getOrDefault(clientId, EMPTY_CLIENT_SESSION_INFO).getClientSession();
    }

    @Override
    public ClientSessionInfo getClientSessionInfo(String clientId) {
        return clientSessionMap.get(clientId);
    }

    @Override
    public void saveClientSession(String clientId, ClientSession clientSession) {
        if (!clientId.equals(clientSession.getSessionInfo().getClientInfo().getClientId())) {
            log.error("Error saving client session. " +
                    "Key clientId - {}, ClientSession's clientId - {}.", clientId, clientSession.getSessionInfo().getClientInfo().getClientId());
            throw new MqttException("Key clientId should be equals to ClientSession's clientId");
        }

        ClientSessionInfo clientSessionInfo = new ClientSessionInfo(clientSession, System.currentTimeMillis());
        clientSessionMap.put(clientId, clientSessionInfo);

        QueueProtos.ClientSessionInfoProto clientSessionInfoProto = ProtoConverter.convertToClientSessionInfoProto(clientSessionInfo);
        clientSessionPersistenceService.persistClientSessionInfo(clientId, clientSessionInfoProto);
    }

    @Override
    public void clearClientSession(String clientId) {
        ClientSessionInfo removedClientSessionInfo = clientSessionMap.remove(clientId);
        if (removedClientSessionInfo == null) {
            log.warn("[{}] No client session found while clearing session.", clientId);
        }
        clientSessionPersistenceService.persistClientSessionInfo(clientId, EMPTY_CLIENT_SESSION_INFO_PROTO);
    }

    private Map<String, ClientSessionInfo> loadPersistedClientSessions() {
        log.info("Load persisted client sessions.");
        Map<String, ClientSessionInfo> allClientSessions = clientSessionPersistenceService.loadAllClientSessionInfos();
        Map<String, ClientSessionInfo> persistedClientSessions = new ConcurrentHashMap<>();
        allClientSessions.forEach((clientId, clientSessionInfo) -> {
            if (clientSessionInfo.getClientSession().getSessionInfo().isPersistent()) {
                clientSessionInfo = markDisconnected(clientSessionInfo);
                persistedClientSessions.put(clientId, clientSessionInfo);
            } else {
                log.debug("[{}] Clearing not persistent client session.", clientId);
                clientSessionPersistenceService.persistClientSessionInfo(clientId, EMPTY_CLIENT_SESSION_INFO_PROTO);
            }
        });
        return persistedClientSessions;
    }

    private ClientSessionInfo markDisconnected(ClientSessionInfo clientSessionInfo) {
        return clientSessionInfo.toBuilder()
                .clientSession(
                        clientSessionInfo.getClientSession().toBuilder()
                                .connected(false)
                                .build()
                )
                .build();
    }
}
