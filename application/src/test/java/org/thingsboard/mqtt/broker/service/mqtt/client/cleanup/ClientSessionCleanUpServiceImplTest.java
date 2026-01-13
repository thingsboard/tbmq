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
package org.thingsboard.mqtt.broker.service.mqtt.client.cleanup;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.service.mqtt.client.disconnect.DisconnectClientCommandService;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventService;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.data.ClientCleanupInfo;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCtxService;
import org.thingsboard.mqtt.broker.util.ClientSessionInfoFactory;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.thingsboard.mqtt.broker.session.DisconnectReasonType.ON_ADMINISTRATIVE_ACTION;

@RunWith(SpringRunner.class)
@EnableScheduling
@ContextConfiguration(classes = ClientSessionCleanUpServiceImpl.class)
@TestPropertySource(properties = {
        "mqtt.client-session-expiry.cron=* * * * * *",
        "mqtt.client-session-expiry.zone=UTC"
})
public class ClientSessionCleanUpServiceImplTest {

    private final String SERVICE_ID = "tb-broker";

    @MockitoBean
    ClientSessionCache clientSessionCache;
    @MockitoBean
    ClientSessionCtxService clientSessionCtxService;
    @MockitoBean
    ClientSessionEventService clientSessionEventService;
    @MockitoBean
    DisconnectClientCommandService disconnectClientCommandService;
    @MockitoBean
    ServiceInfoProvider serviceInfoProvider;

    @MockitoSpyBean
    ClientSessionCleanUpServiceImpl clientSessionCleanUpService;

    @Test
    public void givenDisconnectedSessionsAndOneOutdated_whenRunCleanup_thenSessionRemoved() {
        long currentTs = System.currentTimeMillis();
        long currentTsMinus10Secs = currentTs - TimeUnit.SECONDS.toMillis(10);

        int sessionExpiryInterval = 3;
        ClientSessionInfo clientSessionInfo1 = getClientSessionInfo(currentTs, false, sessionExpiryInterval);
        ClientSessionInfo clientSessionInfo2 = getClientSessionInfo(currentTsMinus10Secs, true, sessionExpiryInterval);
        ClientSessionInfo clientSessionInfo3 = getClientSessionInfo(currentTsMinus10Secs, false, 0);

        when(serviceInfoProvider.getServiceId()).thenReturn(SERVICE_ID);
        when(clientSessionCache.getAllClientSessions()).thenReturn(Map.of(
                "client1", clientSessionInfo1,
                "client2", clientSessionInfo2,
                "client3", clientSessionInfo3
        ));

        await()
                .atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> verify(clientSessionCleanUpService, atLeast(1)).cleanUp());

        verify(clientSessionEventService).requestClientSessionCleanup(eq(clientSessionInfo2), eq(ClientCleanupInfo.GRACEFUL));
    }

    @Test
    public void givenConnectedGhostSession_whenRunCleanup_thenSessionDisconnected() {
        ClientSessionInfo clientSessionInfo = getClientSessionInfo(false, 10)
                .toBuilder().connected(true).clientId("ghostClient").build();
        SessionInfo sessionInfo = ClientSessionInfoFactory.clientSessionInfoToSessionInfo(clientSessionInfo);

        when(serviceInfoProvider.getServiceId()).thenReturn(SERVICE_ID);
        when(clientSessionCache.getAllClientSessions()).thenReturn(Map.of("ghostClient", clientSessionInfo));
        when(clientSessionCtxService.hasSession(eq("ghostClient"))).thenReturn(false);

        clientSessionCleanUpService.cleanUp();

        verify(clientSessionEventService).notifyClientDisconnected(eq(sessionInfo), eq(ON_ADMINISTRATIVE_ACTION), eq(null));
    }

    @Test
    public void givenConnectedSession_whenRunCleanup_thenSessionIsNotRemoved() {
        ClientSessionInfo clientSessionInfo = getClientSessionInfo(false, 10)
                .toBuilder().connected(true).clientId("client").build();

        when(serviceInfoProvider.getServiceId()).thenReturn(SERVICE_ID);
        when(clientSessionCache.getAllClientSessions()).thenReturn(Map.of("client", clientSessionInfo));
        when(clientSessionCtxService.hasSession(eq("client"))).thenReturn(true);

        clientSessionCleanUpService.cleanUp();

        verify(clientSessionEventService, never()).requestClientSessionCleanup(any(), any());
    }

    @Test
    public void givenSessions_whenCheckIfNotCleanSession_thenReceiveExpectedResult() {
        Assert.assertTrue(clientSessionCleanUpService.isNotCleanSession(
                getClientSessionInfo(false, 0)
        ));
        Assert.assertFalse(clientSessionCleanUpService.isNotCleanSession(
                getClientSessionInfo(false, 100)
        ));
        Assert.assertFalse(clientSessionCleanUpService.isNotCleanSession(
                getClientSessionInfo(true, 0)
        ));
        Assert.assertFalse(clientSessionCleanUpService.isNotCleanSession(
                getClientSessionInfo(true, 100)
        ));
    }

    private ClientSessionInfo getClientSessionInfo(boolean cleanStart, int sessionExpiryInterval) {
        return getClientSessionInfo(0L, cleanStart, sessionExpiryInterval);
    }

    private ClientSessionInfo getClientSessionInfo(long disconnectedAt, boolean cleanStart, int sessionExpiryInterval) {
        return ClientSessionInfo.builder()
                .disconnectedAt(disconnectedAt)
                .connected(false)
                .cleanStart(cleanStart)
                .sessionExpiryInterval(sessionExpiryInterval)
                .serviceId(SERVICE_ID)
                .build();
    }
}
