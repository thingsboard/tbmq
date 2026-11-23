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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.dao.clock.CleanupClockGuard;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.service.mqtt.client.disconnect.DisconnectClientCommandService;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventService;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.data.ClientCleanupInfo;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCtxService;
import org.thingsboard.mqtt.broker.util.ClientSessionInfoFactory;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.thingsboard.mqtt.broker.session.DisconnectReasonType.ON_ADMINISTRATIVE_ACTION;

@RunWith(SpringRunner.class)
@EnableScheduling
@ContextConfiguration(classes = ClientSessionCleanUpServiceImpl.class)
@TestPropertySource(properties = {
        "mqtt.client-session-expiry.cron=* * * * * *",
        "mqtt.client-session-expiry.zone=UTC",
        "mqtt.client-session-expiry.ttl=60"
})
public class ClientSessionCleanUpServiceImplTest {

    @Value("${mqtt.client-session-expiry.ttl}")
    private int ttlSeconds;

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
    @MockitoBean
    CleanupClockGuard cleanupClockGuard;

    @MockitoSpyBean
    ClientSessionCleanUpServiceImpl clientSessionCleanUpService;

    @Before
    public void allowCleanups() {
        when(cleanupClockGuard.cleanupAllowed()).thenReturn(true);
    }

    @Test
    public void givenAPostponingClockGuard_whenRunCleanup_thenGhostSessionIsRepairedButExpiredSessionIsNotCleaned() {
        // a service with mocks of its own: the context's bean runs every second on the same mocks, and a run that
        // started while the guard still said yes would clean the expired session
        ClientSessionCache cache = mock(ClientSessionCache.class);
        ClientSessionCtxService ctxService = mock(ClientSessionCtxService.class);
        ClientSessionEventService eventService = mock(ClientSessionEventService.class);
        ServiceInfoProvider infoProvider = mock(ServiceInfoProvider.class);
        CleanupClockGuard guard = mock(CleanupClockGuard.class);
        ClientSessionCleanUpServiceImpl service = new ClientSessionCleanUpServiceImpl(cache, ctxService, eventService,
                mock(DisconnectClientCommandService.class), infoProvider, guard);
        ReflectionTestUtils.setField(service, "ttl", ttlSeconds);

        long now = System.currentTimeMillis();
        ClientSessionInfo ghost = getClientSessionInfo(false, 10).toBuilder().connected(true).clientId("ghostClient").build();
        ClientSessionInfo pastTtl = getClientSessionInfo(now - TimeUnit.SECONDS.toMillis(ttlSeconds + 5), false, 0);
        assertThat(pastTtl.isExpired(now, ttlSeconds)).isTrue();
        when(guard.cleanupAllowed()).thenReturn(false);
        when(infoProvider.getServiceId()).thenReturn(SERVICE_ID);
        when(cache.getAllClientSessions()).thenReturn(Map.of("ghostClient", ghost, "pastTtl", pastTtl));
        when(ctxService.hasSession(eq("ghostClient"))).thenReturn(false);

        service.cleanUp();

        verify(eventService).notifyClientDisconnected(
                eq(ClientSessionInfoFactory.clientSessionInfoToSessionInfo(ghost)), eq(ON_ADMINISTRATIVE_ACTION), eq(null));
        verify(eventService, never()).requestClientSessionCleanup(any(), any());
    }

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

        verify(clientSessionEventService, atLeast(1)).requestClientSessionCleanup(eq(clientSessionInfo2), eq(ClientCleanupInfo.GRACEFUL));
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

        // at least once: the context's bean also runs every second on the same mocks
        verify(clientSessionEventService, atLeast(1)).notifyClientDisconnected(eq(sessionInfo), eq(ON_ADMINISTRATIVE_ACTION), eq(null));
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
    public void givenMqtt3NotCleanSessions_whenRunCleanup_thenOnlyOnePastTtlRemoved() {
        long now = System.currentTimeMillis();
        ClientSessionInfo pastTtl = getClientSessionInfo(now - TimeUnit.SECONDS.toMillis(ttlSeconds + 5), false, 0);
        ClientSessionInfo insideTtl = getClientSessionInfo(now - TimeUnit.SECONDS.toMillis(1), false, 0);

        when(serviceInfoProvider.getServiceId()).thenReturn(SERVICE_ID);
        when(clientSessionCache.getAllClientSessions()).thenReturn(Map.of("pastTtl", pastTtl, "insideTtl", insideTtl));

        clientSessionCleanUpService.cleanUp();

        verify(clientSessionEventService, atLeast(1)).requestClientSessionCleanup(eq(pastTtl), eq(ClientCleanupInfo.GRACEFUL));
        verify(clientSessionEventService, never()).requestClientSessionCleanup(eq(insideTtl), any());
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
