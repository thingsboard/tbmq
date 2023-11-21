/**
 * Copyright © 2016-2023 The Thingsboard Authors
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
import org.thingsboard.mqtt.broker.common.util.BrokerConstants;

import java.util.UUID;

public class ClientSessionInfoFactory {

    public static ClientSessionInfo getClientSessionInfo(String clientId) {
        return getClientSessionInfo(clientId, BrokerConstants.SERVICE_ID_HEADER);
    }

    public static ClientSessionInfo getClientSessionInfo(String clientId, String serviceId) {
        return getClientSessionInfo(clientId, serviceId, true);
    }

    public static ClientSessionInfo getClientSessionInfo(String clientId, String serviceId, boolean connected) {
        ClientInfo clientInfo = getClientInfo(clientId);
        ConnectionInfo connectionInfo = getConnectionInfo();
        SessionInfo sessionInfo = getSessionInfo(serviceId, clientInfo, connectionInfo);
        ClientSession clientSession = getClientSession(connected, sessionInfo);
        return clientSessionToClientSessionInfo(clientSession);
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

    public static ClientSessionInfo getClientSessionInfo(boolean connected, String serviceId, boolean cleanStart,
                                                         ClientType clientType, long connectedAt, long disconnectedAt) {
        return getClientSessionInfo(null, connected, serviceId, cleanStart, clientType, connectedAt, disconnectedAt);
    }

    public static ClientSessionInfo getClientSessionInfo(String clientId, boolean connected, String serviceId, boolean cleanStart,
                                                         ClientType clientType, long connectedAt, long disconnectedAt) {
        return ClientSessionInfo.builder()
                .clientId(clientId)
                .connected(connected)
                .serviceId(serviceId)
                .cleanStart(cleanStart)
                .type(clientType)
                .connectedAt(connectedAt)
                .disconnectedAt(disconnectedAt)
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
                .clientInfo(getClientInfo(clientSessionInfo.getClientId(), clientSessionInfo.getType(), clientSessionInfo.getClientIpAdr()))
                .connectionInfo(
                        getConnectionInfo(
                                clientSessionInfo.getKeepAlive(),
                                clientSessionInfo.getConnectedAt(),
                                clientSessionInfo.getDisconnectedAt()))
                .build();
    }

    public static SessionInfo getSessionInfo(String serviceId, ClientInfo clientInfo, ConnectionInfo connectionInfo) {
        return getSessionInfo(true, serviceId, clientInfo, connectionInfo);
    }

    public static SessionInfo getSessionInfo(boolean cleanStart, String serviceId,
                                             ClientInfo clientInfo, ConnectionInfo connectionInfo) {
        return getSessionInfo(UUID.randomUUID(), cleanStart, serviceId, clientInfo, connectionInfo, 0);
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
        return getConnectionInfo(100000);
    }

    public static ConnectionInfo getConnectionInfo(int keepAlive) {
        return getConnectionInfo(keepAlive, System.currentTimeMillis());
    }

    public static ConnectionInfo getConnectionInfo(int keepAlive, long connectedAt) {
        return getConnectionInfo(keepAlive, connectedAt, 0);
    }

    public static ConnectionInfo getConnectionInfo(int keepAlive, long connectedAt, long disconnectedAt) {
        return ConnectionInfo.builder()
                .connectedAt(connectedAt)
                .disconnectedAt(disconnectedAt)
                .keepAlive(keepAlive)
                .build();
    }

    public static ClientInfo getClientInfo(String clientId) {
        return getClientInfo(clientId, ClientType.DEVICE);
    }

    public static ClientInfo getClientInfo(String clientId, ClientType clientType) {
        return getClientInfo(clientId, clientType, BrokerConstants.LOCAL_ADR);
    }

    public static ClientInfo getClientInfo(String clientId, ClientType clientType, byte[] clientIpAdr) {
        return ClientInfo.builder()
                .clientId(clientId)
                .type(clientType)
                .clientIpAdr(clientIpAdr)
                .build();
    }
}
