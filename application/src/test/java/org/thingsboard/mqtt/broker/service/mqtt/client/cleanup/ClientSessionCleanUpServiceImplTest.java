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
package org.thingsboard.mqtt.broker.service.mqtt.client.cleanup;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.service.mqtt.client.disconnect.DisconnectClientCommandService;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventService;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@EnableScheduling
@ContextConfiguration(classes = ClientSessionCleanUpServiceImpl.class)
@TestPropertySource(properties = {
        "mqtt.client-session-expiry.cron=* * * * * *",
        "mqtt.client-session-expiry.zone=UTC"
})
public class ClientSessionCleanUpServiceImplTest {

    @MockBean
    ClientSessionCache clientSessionCache;
    @MockBean
    ClientSessionEventService clientSessionEventService;
    @MockBean
    DisconnectClientCommandService disconnectClientCommandService;
    @MockBean
    ServiceInfoProvider serviceInfoProvider;

    @SpyBean
    ClientSessionCleanUpServiceImpl clientSessionCleanUpService;

    @Test
    public void givenTwoSessions_whenWaitThreeSeconds_thenScheduledIsCalledAtLeastOneTime() {
        long currentTs = System.currentTimeMillis();
        long currentTsMinus10Secs = currentTs - TimeUnit.SECONDS.toMillis(10);

        int sessionExpiryInterval = 3;
        ClientSessionInfo clientSessionInfo1 = getClientSessionInfo(currentTs, false, sessionExpiryInterval);
        ClientSessionInfo clientSessionInfo2 = getClientSessionInfo(currentTsMinus10Secs, true, sessionExpiryInterval);
        ClientSessionInfo clientSessionInfo3 = getClientSessionInfo(currentTsMinus10Secs, false, 0);

        when(clientSessionCache.getAllClientSessions()).thenReturn(Map.of(
                "client1", clientSessionInfo1,
                "client2", clientSessionInfo2,
                "client3", clientSessionInfo3
        ));

        await()
                .atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> verify(clientSessionCleanUpService, atLeast(1)).cleanUp());

        verify(clientSessionEventService, atMost(1)).requestSessionCleanup(any());
    }

    @Test
    public void givenSessions_whenCheckIfNotPersistent_thenReceiveExpectedResult() {
        Assert.assertTrue(clientSessionCleanUpService.isNotCleanSession(
                getSessionInfo(false, 0)
        ));
        Assert.assertFalse(clientSessionCleanUpService.isNotCleanSession(
                getSessionInfo(false, 100)
        ));
        Assert.assertFalse(clientSessionCleanUpService.isNotCleanSession(
                getSessionInfo(true, 0)
        ));
        Assert.assertFalse(clientSessionCleanUpService.isNotCleanSession(
                getSessionInfo(true, 100)
        ));
    }

    private SessionInfo getSessionInfo(boolean cleanStart, int sessionExpiryInterval) {
        return SessionInfo.builder()
                .cleanStart(cleanStart)
                .sessionExpiryInterval(sessionExpiryInterval)
                .build();
    }

    private ClientSessionInfo getClientSessionInfo(long disconnectedAt, boolean cleanStart, int sessionExpiryInterval) {
        return ClientSessionInfo.builder()
                .disconnectedAt(disconnectedAt)
                .connected(false)
                .cleanStart(cleanStart)
                .sessionExpiryInterval(sessionExpiryInterval)
                .serviceId("tb-broker")
                .build();
    }
}