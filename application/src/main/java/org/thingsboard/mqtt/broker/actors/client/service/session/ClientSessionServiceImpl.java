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
package org.thingsboard.mqtt.broker.actors.client.service.session;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.common.data.ClientSession;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.gen.queue.ClientSessionInfoProto;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionConsumer;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionPersistenceService;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.util.ClientSessionInfoFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import static org.thingsboard.mqtt.broker.queue.constants.QueueConstants.EMPTY_CLIENT_SESSION_INFO_PROTO;

/**
 * Not thread-safe for the same clientId
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientSessionServiceImpl implements ClientSessionService {

    private final ClientSessionPersistenceService clientSessionPersistenceService;
    private final ServiceInfoProvider serviceInfoProvider;
    private final StatsManager statsManager;

    private ConcurrentMap<String, ClientSessionInfo> clientSessionMap;
    private volatile boolean initialized = false;

    @Override
    public void init(Map<String, ClientSessionInfo> clientSessionInfos) {
        clientSessionMap = new ConcurrentHashMap<>(clientSessionInfos);
        statsManager.registerAllClientSessionsStats(clientSessionMap);
        initialized = true;
        log.info("Client sessions initialized. Total sessions: {}", clientSessionMap.size());
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public void startListening(ClientSessionConsumer clientSessionConsumer) {
        clientSessionConsumer.listen(this::processSessionUpdate);
    }

    @Override
    public void saveClientSession(ClientSessionInfo clientSessionInfo, BasicCallback callback) {
        var clientId = clientSessionInfo.getClientId();
        log.trace("[{}] Saving ClientSession {}", clientId, clientSessionInfo);

        clientSessionMap.put(clientId, clientSessionInfo);

        ClientSessionInfoProto clientSessionInfoProto = ProtoConverter.convertToClientSessionInfoProto(clientSessionInfo);
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
        clientSessionPersistenceService.persistClientSessionInfoAsync(clientId, EMPTY_CLIENT_SESSION_INFO_PROTO, callback);
    }

    @Override
    public Map<String, ClientSessionInfo> getPersistentClientSessionInfos() {
        return clientSessionMap.entrySet().stream()
                .filter(entry -> entry.getValue().isPersistent())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public ClientSession getClientSession(String clientId) {
        ClientSessionInfo clientSessionInfo = clientSessionMap.getOrDefault(clientId, null);
        if (clientSessionInfo != null) {
            SessionInfo sessionInfo = ClientSessionInfoFactory.clientSessionInfoToSessionInfo(clientSessionInfo);
            return new ClientSession(clientSessionInfo.isConnected(), sessionInfo);
        }
        return null;
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

    @Override
    public int getClientSessionsCount() {
        return clientSessionMap == null ? 0 : clientSessionMap.size();
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

}
