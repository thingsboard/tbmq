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

    public static SessionInfo getSessionInfo(String serviceId, ClientInfo clientInfo, ConnectionInfo connectionInfo) {
        return getSessionInfo(false, serviceId, clientInfo, connectionInfo);
    }

    public static SessionInfo getSessionInfo(boolean persistent, String serviceId,
                                             ClientInfo clientInfo, ConnectionInfo connectionInfo) {
        return getSessionInfo(UUID.randomUUID(), persistent, serviceId, clientInfo, connectionInfo);
    }

    public static SessionInfo getSessionInfo(UUID sessionId, boolean persistent, String serviceId,
                                             ClientInfo clientInfo, ConnectionInfo connectionInfo) {
        return SessionInfo.builder()
                .sessionId(sessionId)
                .persistent(persistent)
                .serviceId(serviceId)
                .clientInfo(clientInfo)
                .connectionInfo(connectionInfo)
                .build();
    }

    public static ConnectionInfo getConnectionInfo() {
        return getConnectionInfo(100000);
    }

    public static ConnectionInfo getConnectionInfo(int keepAlive) {
        return getConnectionInfo(keepAlive, System.currentTimeMillis());
    }

    public static ConnectionInfo getConnectionInfo(int keepAlive, long connectedAt) {
        return ConnectionInfo.builder()
                .connectedAt(connectedAt)
                .keepAlive(keepAlive)
                .build();
    }

    public static ClientInfo getClientInfo(String clientId) {
        return getClientInfo(clientId, ClientType.DEVICE);
    }

    public static ClientInfo getClientInfo(String clientId, ClientType clientType) {
        return ClientInfo.builder()
                .clientId(clientId)
                .type(clientType)
                .build();
    }
}
