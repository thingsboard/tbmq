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
package org.thingsboard.mqtt.broker.actors.client.service.session;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.constant.BrokerConstants;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.service.mqtt.ClientSession;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionConsumer;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionInfo;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionPersistenceService;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/*
    Not thread-safe for the same clientId
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientSessionServiceImpl implements ClientSessionService {

    private static final ClientSessionInfo EMPTY_CLIENT_SESSION_INFO = new ClientSessionInfo(null, 0);

    private final ClientSessionPersistenceService clientSessionPersistenceService;
    private final ServiceInfoProvider serviceInfoProvider;
    private final StatsManager statsManager;

    private ConcurrentMap<String, ClientSessionInfo> clientSessionMap;

    @Override
    public void init(Map<String, ClientSessionInfo> clientSessionInfos) {
        this.clientSessionMap = new ConcurrentHashMap<>(clientSessionInfos);
        statsManager.registerAllClientSessionsStats(clientSessionMap);
    }

    @Override
    public void startListening(ClientSessionConsumer clientSessionConsumer) {
        clientSessionConsumer.listen(this::processSessionUpdate);
    }

    @Override
    public void saveClientSession(String clientId, ClientSession clientSession, BasicCallback callback) {
        if (!clientId.equals(clientSession.getSessionInfo().getClientInfo().getClientId())) {
            log.error("Error saving client session. " +
                    "Key clientId - {}, ClientSession's clientId - {}.", clientId, clientSession.getSessionInfo().getClientInfo().getClientId());
            throw new MqttException("Key clientId should be equals to ClientSession's clientId");
        }
        log.trace("[{}] Saving ClientSession.", clientId);

        ClientSessionInfo clientSessionInfo = new ClientSessionInfo(clientSession, System.currentTimeMillis());
        clientSessionMap.put(clientId, clientSessionInfo);

        QueueProtos.ClientSessionInfoProto clientSessionInfoProto = ProtoConverter.convertToClientSessionInfoProto(clientSessionInfo);
        clientSessionPersistenceService.persistClientSessionInfoAsync(clientId, clientSessionInfoProto, callback);
    }

    // TODO: if client disconnects from Node A and connects to Node B it's possible that changes to ClientSession are not delivered on B yet. Solutions:
    //          - ask other nodes before connecting client
    //          - force wait if client was previously connected to other node

    @Override
    public void clearClientSession(String clientId, BasicCallback callback) {
        log.trace("[{}] Clearing ClientSession.", clientId);
        ClientSessionInfo removedClientSessionInfo = clientSessionMap.remove(clientId);
        if (removedClientSessionInfo == null) {
            log.warn("[{}] No client session found while clearing session.", clientId);
        }
        clientSessionPersistenceService.persistClientSessionInfoAsync(clientId, BrokerConstants.EMPTY_CLIENT_SESSION_INFO_PROTO, callback);
    }

    @Override
    public Map<String, ClientSessionInfo> getPersistentClientSessionInfos() {
        return clientSessionMap.entrySet().stream()
                .filter(entry -> isPersistent(entry.getValue()))
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
    public Map<String, ClientSessionInfo> getAllClientSessions() {
        Map<String, ClientSessionInfo> allClientSessionsMap = clientSessionMap == null ? Map.of() : clientSessionMap;
        return new HashMap<>(allClientSessionsMap);
    }

    private void processSessionUpdate(String clientId, String serviceId, ClientSessionInfo clientSessionInfo) {
        if (serviceInfoProvider.getServiceId().equals(serviceId)) {
            log.trace("[{}] Msg was already processed.", clientId);
            return;
        }
        if (clientSessionInfo == null) {
            log.trace("[{}][{}] Clearing remote ClientSession.", serviceId, clientId);
            clientSessionMap.remove(clientId);
        } else {
            log.trace("[{}][{}] Saving remote ClientSession.", serviceId, clientId);
            clientSessionMap.put(clientId, clientSessionInfo);
        }
    }

    private boolean isPersistent(ClientSessionInfo clientSessionInfo) {
        return clientSessionInfo.getClientSession().getSessionInfo().isPersistent();
    }
}
