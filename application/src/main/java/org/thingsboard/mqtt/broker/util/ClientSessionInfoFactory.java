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
package org.thingsboard.mqtt.broker.util;

import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientSession;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.ConnectionInfo;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;

import java.util.UUID;

import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.LOCAL_ADR;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.SERVICE_ID_HEADER;
import static org.thingsboard.mqtt.broker.common.data.ClientType.DEVICE;

public class ClientSessionInfoFactory {

    private static final ClientSessionInfo defaultClientSessionInfo = ClientSessionInfo.builder()
            .keepAlive(60)
            .disconnectedAt(0)
            .connectedAt(System.currentTimeMillis())
            .type(DEVICE)
            .clientIpAdr(LOCAL_ADR)
            .clientId(null)
            .sessionExpiryInterval(0)
            .cleanStart(true)
            .sessionId(UUID.randomUUID())
            .serviceId(SERVICE_ID_HEADER)
            .connected(true)
            .build();

    private static final ConnectionInfo defaultConnectionInfo = ConnectionInfo.builder()
            .connectedAt(System.currentTimeMillis())
            .disconnectedAt(0)
            .keepAlive(60)
            .build();

    private static final ClientInfo defaultClientInfo = ClientInfo.builder()
            .clientId(null)
            .type(DEVICE)
            .clientIpAdr(LOCAL_ADR)
            .build();


    public static ClientSessionInfo getClientSessionInfo(String clientId) {
        return defaultClientSessionInfo.toBuilder().clientId(clientId).build();
    }

    public static ClientSessionInfo getClientSessionInfo(String clientId, String serviceId) {
        return defaultClientSessionInfo.toBuilder().clientId(clientId).serviceId(serviceId).build();
    }

    public static ClientSessionInfo getClientSessionInfo(String clientId, String serviceId, boolean connected) {
        return defaultClientSessionInfo
                .toBuilder()
                .clientId(clientId)
                .serviceId(serviceId)
                .connected(connected)
                .build();
    }

    public static ClientSessionInfo getClientSessionInfo(boolean connected, String serviceId, boolean cleanStart,
                                                         ClientType clientType, long connectedAt, long disconnectedAt) {
        return defaultClientSessionInfo
                .toBuilder()
                .connected(connected)
                .serviceId(serviceId)
                .cleanStart(cleanStart)
                .type(clientType)
                .connectedAt(connectedAt)
                .disconnectedAt(disconnectedAt)
                .build();
    }

    public static ClientSessionInfo getClientSessionInfo(String clientId, boolean connected, String serviceId, boolean cleanStart,
                                                         ClientType clientType, long connectedAt, long disconnectedAt) {
        return defaultClientSessionInfo
                .toBuilder()
                .clientId(clientId)
                .connected(connected)
                .serviceId(serviceId)
                .cleanStart(cleanStart)
                .type(clientType)
                .connectedAt(connectedAt)
                .disconnectedAt(disconnectedAt)
                .build();
    }

    public static ClientSessionInfo clientSessionToClientSessionInfo(ClientSession clientSession) {
        return ClientSessionInfo.builder()
                .keepAlive(clientSession.getSessionInfo().getConnectionInfo().getKeepAlive())
                .disconnectedAt(clientSession.getSessionInfo().getConnectionInfo().getDisconnectedAt())
                .connectedAt(clientSession.getSessionInfo().getConnectionInfo().getConnectedAt())
                .type(clientSession.getClientType())
                .clientIpAdr(clientSession.getSessionInfo().getClientInfo().getClientIpAdr())
                .clientId(clientSession.getClientId())
                .sessionExpiryInterval(clientSession.getSessionInfo().getSessionExpiryInterval())
                .cleanStart(clientSession.getSessionInfo().isCleanStart())
                .sessionId(clientSession.getSessionInfo().getSessionId())
                .serviceId(clientSession.getSessionInfo().getServiceId())
                .connected(clientSession.isConnected())
                .build();
    }

    public static ClientSession getClientSession(boolean connected, SessionInfo sessionInfo) {
        return ClientSession.builder()
                .connected(connected)
                .sessionInfo(sessionInfo)
                .build();
    }

    public static SessionInfo clientSessionInfoToSessionInfo(ClientSessionInfo clientSessionInfo) {
        return SessionInfo.builder()
                .serviceId(clientSessionInfo.getServiceId())
                .sessionId(clientSessionInfo.getSessionId())
                .cleanStart(clientSessionInfo.isCleanStart())
                .sessionExpiryInterval(clientSessionInfo.getSessionExpiryInterval())
                .clientInfo(
                        getClientInfo(
                                clientSessionInfo.getClientId(),
                                clientSessionInfo.getType(),
                                clientSessionInfo.getClientIpAdr()))
                .connectionInfo(
                        getConnectionInfo(
                                clientSessionInfo.getKeepAlive(),
                                clientSessionInfo.getConnectedAt(),
                                clientSessionInfo.getDisconnectedAt()))
                .build();
    }

    public static SessionInfo getSessionInfo(String serviceId, String clientId, ClientType clientType) {
        return SessionInfo.builder()
                .sessionId(UUID.randomUUID())
                .cleanStart(true)
                .serviceId(serviceId)
                .clientInfo(getClientInfo(clientId, clientType))
                .connectionInfo(defaultConnectionInfo)
                .sessionExpiryInterval(0)
                .build();
    }

    public static SessionInfo getSessionInfo(boolean cleanStart, String serviceId,
                                             ClientInfo clientInfo) {
        return SessionInfo.builder()
                .sessionId(UUID.randomUUID())
                .cleanStart(cleanStart)
                .serviceId(serviceId)
                .clientInfo(clientInfo)
                .connectionInfo(defaultConnectionInfo)
                .sessionExpiryInterval(0)
                .build();
    }

    public static SessionInfo getSessionInfo(UUID sessionId, boolean cleanStart, String serviceId,
                                             ClientInfo clientInfo, ConnectionInfo connectionInfo, int sessionExpiryInterval) {
        return SessionInfo.builder()
                .sessionId(sessionId)
                .cleanStart(cleanStart)
                .serviceId(serviceId)
                .clientInfo(clientInfo)
                .connectionInfo(connectionInfo)
                .sessionExpiryInterval(sessionExpiryInterval)
                .build();
    }

    public static ConnectionInfo getConnectionInfo() {
        return defaultConnectionInfo;
    }

    public static ConnectionInfo getConnectionInfo(int keepAlive) {
        return defaultConnectionInfo.toBuilder().keepAlive(keepAlive).build();
    }

    public static ConnectionInfo getConnectionInfo(int keepAlive, long connectedAt) {
        return defaultConnectionInfo.toBuilder().keepAlive(keepAlive).connectedAt(connectedAt).build();
    }

    public static ConnectionInfo getConnectionInfo(int keepAlive, long connectedAt, long disconnectedAt) {
        return ConnectionInfo.builder()
                .connectedAt(connectedAt)
                .disconnectedAt(disconnectedAt)
                .keepAlive(keepAlive)
                .build();
    }

    public static ClientInfo getClientInfo(String clientId) {
        return defaultClientInfo.toBuilder().clientId(clientId).build();
    }

    public static ClientInfo getClientInfo(String clientId, ClientType clientType) {
        return defaultClientInfo.toBuilder().clientId(clientId).type(clientType).build();
    }

    public static ClientInfo getClientInfo(String clientId, ClientType clientType, byte[] clientIpAdr) {
        return ClientInfo.builder()
                .clientId(clientId)
                .type(clientType)
                .clientIpAdr(clientIpAdr)
                .build();
    }
}
