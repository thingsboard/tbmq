/**
 * Copyright © 2016-2024 The Thingsboard Authors
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

import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientSession;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.ConnectionInfo;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.data.util.CallbackUtil;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionPersistenceService;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.spy;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class ClientSessionServiceImplTest {

    private final String TEST_SERVICE_ID = "testServiceId";
    private final String DEFAULT_CLIENT_ID = "test";
    private final ClientType DEFAULT_CLIENT_TYPE = ClientType.DEVICE;
    private final ClientSession DEFAULT_CLIENT_SESSION = ClientSession.builder()
            .connected(true)
            .sessionInfo(SessionInfo.builder()
                    .sessionId(UUID.randomUUID())
                    .cleanStart(false)
                    .clientInfo(new ClientInfo(DEFAULT_CLIENT_ID, DEFAULT_CLIENT_TYPE))
                    .serviceId(TEST_SERVICE_ID)
                    .connectionInfo(ConnectionInfo.builder()
                            .connectedAt(1)
                            .disconnectedAt(2)
                            .keepAlive(3)
                            .build())
                    .build())
            .build();

    private ClientSessionService clientSessionService;

    @Before
    public void init() {
        ClientSessionPersistenceService clientSessionPersistenceServiceMock = Mockito.mock(ClientSessionPersistenceService.class);
        StatsManager statsManagerMock = Mockito.mock(StatsManager.class);
        ServiceInfoProvider serviceInfoProviderMock = Mockito.mock(ServiceInfoProvider.class);
        this.clientSessionService = spy(new ClientSessionServiceImpl(clientSessionPersistenceServiceMock, serviceInfoProviderMock, statsManagerMock));
        this.clientSessionService.init(Collections.emptyMap());
    }

    @Test
    public void givenSessions_whenSaveAndGetPersistentSessions_thenReturnResult() {
        ClientSession persistentSession1 = prepareSession("persistent_1", 1, true);
        ClientSession persistentSession2 = prepareSession("persistent_2", 2, false);
        ClientSession notPersistentSession = prepareSession("not_persistent", 0, true);

        saveClientSession("persistent_1", persistentSession1);
        saveClientSession("persistent_2", persistentSession2);
        saveClientSession("not_persistent", notPersistentSession);

        Set<String> persistedClients = getPersistedClients();
        Assert.assertEquals(2, persistedClients.size());
        Assert.assertTrue(persistedClients.contains("persistent_1") && persistedClients.contains("persistent_2"));
    }

    @Test
    public void givenSession_whenSaveAndClearSession_thenReturnResult() {
        ClientSession persistentSession = prepareSession("persistent", 1, false);

        saveClientSession("persistent", persistentSession);

        Set<String> persistedClients = getPersistedClients();
        Assert.assertEquals(1, persistedClients.size());
        Assert.assertTrue(persistedClients.contains("persistent"));

        clearClientSession();
        persistedClients = getPersistedClients();
        Assert.assertTrue(persistedClients.isEmpty());
    }

    @Test(expected = MqttException.class)
    public void givenSession_whenSaveWithDifferentClientIds_thenThrowException() {
        ClientSession notValidClientSession = prepareSession(DEFAULT_CLIENT_ID + "_not_valid", 1, true);
        saveClientSession(DEFAULT_CLIENT_ID, notValidClientSession);
    }

    private ClientSession prepareSession(String clientId, int sessionExpiryInterval, boolean cleanStart) {
        return DEFAULT_CLIENT_SESSION.toBuilder()
                .sessionInfo(DEFAULT_CLIENT_SESSION.getSessionInfo().toBuilder()
                        .clientInfo(new ClientInfo(clientId, ClientType.DEVICE))
                        .sessionExpiryInterval(sessionExpiryInterval)
                        .cleanStart(cleanStart)
                        .build())
                .build();
    }

    private Set<String> getPersistedClients() {
        return clientSessionService.getPersistentClientSessionInfos().keySet();
    }

    private void saveClientSession(String clientId, ClientSession clientSession) {
        clientSessionService.saveClientSession(clientId, clientSession, CallbackUtil.createCallback(() -> {
        }, t -> {
        }));
    }

    private void clearClientSession() {
        clientSessionService.clearClientSession("persistent", CallbackUtil.createCallback(() -> {
        }, t -> {
        }));
    }

}