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

    public static ClientSessionInfo getClientSessionInfo(String clientId) {
        return ClientSessionInfo.builder()
                .keepAlive(100000)
                .disconnectedAt(0)
                .connectedAt(System.currentTimeMillis())
                .type(DEVICE)
                .clientIpAdr(LOCAL_ADR)
                .clientId(clientId)
                .sessionExpiryInterval(0)
                .cleanStart(true)
                .sessionId(UUID.randomUUID())
                .serviceId(SERVICE_ID_HEADER)
                .connected(true)
                .build();
    }

    public static ClientSessionInfo getClientSessionInfo(String clientId, String serviceId) {
        return ClientSessionInfo.builder()
                .keepAlive(100000)
                .disconnectedAt(0)
                .connectedAt(System.currentTimeMillis())
                .type(DEVICE)
                .clientIpAdr(LOCAL_ADR)
                .clientId(clientId)
                .sessionExpiryInterval(0)
                .cleanStart(true)
                .sessionId(UUID.randomUUID())
                .serviceId(serviceId)
                .connected(true)
                .build();
    }

    public static ClientSessionInfo getClientSessionInfo(String clientId, String serviceId, boolean connected) {
        return ClientSessionInfo.builder()
                .keepAlive(100000)
                .disconnectedAt(0)
                .connectedAt(System.currentTimeMillis())
                .type(DEVICE)
                .clientIpAdr(LOCAL_ADR)
                .clientId(clientId)
                .sessionExpiryInterval(0)
                .cleanStart(true)
                .sessionId(UUID.randomUUID())
                .serviceId(serviceId)
                .connected(connected)
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

    public static ClientSessionInfo getClientSessionInfo(boolean connected, String serviceId, boolean cleanStart,
                                                         ClientType clientType, long connectedAt, long disconnectedAt) {
        return ClientSessionInfo.builder()
                .clientId(null)
                .connected(connected)
                .serviceId(serviceId)
                .cleanStart(cleanStart)
                .type(clientType)
                .connectedAt(connectedAt)
                .disconnectedAt(disconnectedAt)
                .clientIpAdr(LOCAL_ADR)
                .sessionId(UUID.randomUUID())
                .sessionExpiryInterval(0)
                .keepAlive(60)
                .build();
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
                .clientIpAdr(LOCAL_ADR)
                .sessionId(UUID.randomUUID())
                .sessionExpiryInterval(0)
                .keepAlive(60)
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

    public static SessionInfo getSessionInfo(String serviceId, ClientInfo clientInfo, ConnectionInfo connectionInfo) {
        return SessionInfo.builder()
                .sessionId(UUID.randomUUID())
                .cleanStart(true)
                .serviceId(serviceId)
                .clientInfo(clientInfo)
                .connectionInfo(connectionInfo)
                .sessionExpiryInterval(0)
                .build();
    }

    public static SessionInfo getSessionInfo(boolean cleanStart, String serviceId,
                                             ClientInfo clientInfo, ConnectionInfo connectionInfo) {
        return SessionInfo.builder()
                .sessionId(UUID.randomUUID())
                .cleanStart(cleanStart)
                .serviceId(serviceId)
                .clientInfo(clientInfo)
                .connectionInfo(connectionInfo)
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
        return ConnectionInfo.builder()
                .connectedAt(System.currentTimeMillis())
                .disconnectedAt(0)
                .keepAlive(100000)
                .build();
    }

    public static ConnectionInfo getConnectionInfo(int keepAlive) {
        return ConnectionInfo.builder()
                .connectedAt(System.currentTimeMillis())
                .disconnectedAt(0)
                .keepAlive(keepAlive)
                .build();
    }

    public static ConnectionInfo getConnectionInfo(int keepAlive, long connectedAt) {
        return ConnectionInfo.builder()
                .connectedAt(connectedAt)
                .disconnectedAt(0)
                .keepAlive(keepAlive)
                .build();
    }

    public static ConnectionInfo getConnectionInfo(int keepAlive, long connectedAt, long disconnectedAt) {
        return ConnectionInfo.builder()
                .connectedAt(connectedAt)
                .disconnectedAt(disconnectedAt)
                .keepAlive(keepAlive)
                .build();
    }

    public static ClientInfo getClientInfo(String clientId) {
        return ClientInfo.builder()
                .clientId(clientId)
                .type(DEVICE)
                .clientIpAdr(LOCAL_ADR)
                .build();
    }

    public static ClientInfo getClientInfo(String clientId, ClientType clientType) {
        return ClientInfo.builder()
                .clientId(clientId)
                .type(clientType)
                .clientIpAdr(LOCAL_ADR)
                .build();
    }

    public static ClientInfo getClientInfo(String clientId, ClientType clientType, byte[] clientIpAdr) {
        return ClientInfo.builder()
                .clientId(clientId)
                .type(clientType)
                .clientIpAdr(clientIpAdr)
                .build();
    }
}
