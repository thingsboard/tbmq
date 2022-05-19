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
package org.thingsboard.mqtt.broker.util;

import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.ConnectionInfo;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.service.mqtt.ClientSession;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionInfo;

import java.util.UUID;

public class ClientSessionInfoFactory {

    public static ClientSessionInfo getClientSessionInfo(String clientId) {
        return getClientSessionInfo(clientId, "serviceId");
    }

    public static ClientSessionInfo getClientSessionInfo(String clientId, String serviceId) {
        return getClientSessionInfo(clientId, serviceId, true);
    }

    public static ClientSessionInfo getClientSessionInfo(String clientId, String serviceId, boolean connected) {
        ClientInfo clientInfo = getClientInfo(clientId);
        ConnectionInfo connectionInfo = getConnectionInfo();
        SessionInfo sessionInfo = getSessionInfo(serviceId, clientInfo, connectionInfo);
        ClientSession clientSession = getClientSession(connected, sessionInfo);
        return getClientSessionInfo(clientSession);
    }

    private static ClientSessionInfo getClientSessionInfo(ClientSession clientSession) {
        return ClientSessionInfo.builder()
                .lastUpdateTime(System.currentTimeMillis())
                .clientSession(clientSession)
                .build();
    }

    private static ClientSession getClientSession(boolean connected, SessionInfo sessionInfo) {
        return ClientSession.builder()
                .connected(connected)
                .sessionInfo(sessionInfo)
                .build();
    }

    private static SessionInfo getSessionInfo(String serviceId, ClientInfo clientInfo, ConnectionInfo connectionInfo) {
        return SessionInfo.builder()
                .sessionId(UUID.randomUUID())
                .persistent(false)
                .serviceId(serviceId)
                .clientInfo(clientInfo)
                .connectionInfo(connectionInfo)
                .build();
    }

    private static ConnectionInfo getConnectionInfo() {
        return ConnectionInfo.builder()
                .connectedAt(System.currentTimeMillis())
                .keepAlive(100000)
                .build();
    }

    private static ClientInfo getClientInfo(String clientId) {
        return ClientInfo.builder()
                .clientId(clientId)
                .type(ClientType.DEVICE)
                .build();
    }
}
