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
package org.thingsboard.mqtt.broker.service.client;

import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.data.util.CallbackUtil;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.service.mqtt.ClientSession;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionPersistenceService;
import org.thingsboard.mqtt.broker.actors.client.service.session.ClientSessionService;
import org.thingsboard.mqtt.broker.actors.client.service.session.ClientSessionServiceImpl;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;


@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class ClientSessionServiceTestSuite {
    private static final String TEST_SERVICE_ID = "testServiceId";
    private static final String DEFAULT_CLIENT_ID = "test";
    private static final ClientType DEFAULT_CLIENT_TYPE = ClientType.DEVICE;
    private static final ClientSession DEFAULT_CLIENT_SESSION = ClientSession.builder()
            .connected(true)
            .sessionInfo(SessionInfo.builder()
                    .sessionId(UUID.randomUUID())
                    .persistent(true)
                    .clientInfo(new ClientInfo(DEFAULT_CLIENT_ID, DEFAULT_CLIENT_TYPE))
                    .build())
            .build()
            ;

    private ClientSessionService clientSessionService;

    @Before
    public void init() {
        ClientSessionPersistenceService clientSessionPersistenceServiceMock = Mockito.mock(ClientSessionPersistenceService.class);
        StatsManager statsManagerMock = Mockito.mock(StatsManager.class);
        ServiceInfoProvider serviceInfoProviderMock = Mockito.mock(ServiceInfoProvider.class);
        Mockito.when(serviceInfoProviderMock.getServiceId()).thenReturn(TEST_SERVICE_ID);
        this.clientSessionService = new ClientSessionServiceImpl(clientSessionPersistenceServiceMock, serviceInfoProviderMock, statsManagerMock);
        this.clientSessionService.init(Collections.emptyMap());
    }

    @Test
    public void testGetPersistedClients() {
        ClientSession persistentSession1 = DEFAULT_CLIENT_SESSION.toBuilder()
                .sessionInfo(DEFAULT_CLIENT_SESSION.getSessionInfo().toBuilder()
                        .clientInfo(new ClientInfo("persistent_1", ClientType.DEVICE))
                        .persistent(true)
                        .build())
                .build();
        ClientSession persistentSession2 = DEFAULT_CLIENT_SESSION.toBuilder()
                .sessionInfo(DEFAULT_CLIENT_SESSION.getSessionInfo().toBuilder()
                        .clientInfo(new ClientInfo("persistent_2", ClientType.DEVICE))
                        .persistent(true)
                        .build())
                .build();
        ClientSession notPersistentSession = DEFAULT_CLIENT_SESSION.toBuilder()
                .sessionInfo(DEFAULT_CLIENT_SESSION.getSessionInfo().toBuilder()
                        .clientInfo(new ClientInfo("not_persistent", ClientType.DEVICE))
                        .persistent(false)
                        .build())
                .build();
        clientSessionService.saveClientSession("persistent_1", persistentSession1, CallbackUtil.createCallback(() -> {}, t -> {}));
        clientSessionService.saveClientSession("persistent_2", persistentSession2, CallbackUtil.createCallback(() -> {}, t -> {}));
        clientSessionService.saveClientSession("not_persistent", notPersistentSession, CallbackUtil.createCallback(() -> {}, t -> {}));

        Set<String> persistedClients = clientSessionService.getPersistentClientSessionInfos().keySet();
        Assert.assertEquals(2, persistedClients.size());
        Assert.assertTrue(persistedClients.contains("persistent_1") && persistedClients.contains("persistent_2"));
    }

    @Test(expected = MqttException.class)
    public void testFail_differentClientIds() {
        ClientSession notValidClientSession = DEFAULT_CLIENT_SESSION.toBuilder()
                .sessionInfo(DEFAULT_CLIENT_SESSION.getSessionInfo().toBuilder()
                        .clientInfo(new ClientInfo(DEFAULT_CLIENT_ID + "_not_valid", ClientType.DEVICE))
                        .build())
                .build();
        clientSessionService.saveClientSession(DEFAULT_CLIENT_ID, notValidClientSession, CallbackUtil.createCallback(() -> {}, t -> {}));
    }
}
