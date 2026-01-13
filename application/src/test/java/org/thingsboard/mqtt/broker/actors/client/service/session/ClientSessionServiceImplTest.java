/**
 * Copyright © 2016-2026 The Thingsboard Authors
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
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.util.CallbackUtil;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionPersistenceService;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.util.ClientSessionInfoFactory;

import java.util.Collections;
import java.util.Set;

import static org.mockito.Mockito.spy;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class ClientSessionServiceImplTest {

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
        ClientSessionInfo persistentSession1 = prepareSession("persistent_1", 1, true);
        ClientSessionInfo persistentSession2 = prepareSession("persistent_2", 2, false);
        ClientSessionInfo notPersistentSession = prepareSession("not_persistent", 0, true);

        saveClientSession(persistentSession1);
        saveClientSession(persistentSession2);
        saveClientSession(notPersistentSession);

        Set<String> persistedClients = getPersistedClients();
        Assert.assertEquals(2, persistedClients.size());
        Assert.assertTrue(persistedClients.contains("persistent_1") && persistedClients.contains("persistent_2"));
    }

    @Test
    public void givenSession_whenSaveAndClearSession_thenReturnResult() {
        ClientSessionInfo persistentSession = prepareSession("persistent", 1, false);

        saveClientSession(persistentSession);

        Set<String> persistedClients = getPersistedClients();
        Assert.assertEquals(1, persistedClients.size());
        Assert.assertTrue(persistedClients.contains("persistent"));

        clearClientSession();
        persistedClients = getPersistedClients();
        Assert.assertTrue(persistedClients.isEmpty());
    }

    private ClientSessionInfo prepareSession(String clientId, int sessionExpiryInterval, boolean cleanStart) {
        return ClientSessionInfoFactory.getClientSessionInfo()
                .toBuilder()
                .clientId(clientId)
                .sessionExpiryInterval(sessionExpiryInterval)
                .cleanStart(cleanStart)
                .build();
    }

    private Set<String> getPersistedClients() {
        return clientSessionService.getPersistentClientSessionInfos().keySet();
    }

    private void saveClientSession(ClientSessionInfo clientSessionInfo) {
        clientSessionService.saveClientSession(clientSessionInfo, CallbackUtil.createCallback(() -> {
        }, t -> {
        }));
    }

    private void clearClientSession() {
        clientSessionService.clearClientSession("persistent", CallbackUtil.createCallback(() -> {
        }, t -> {
        }));
    }

}
