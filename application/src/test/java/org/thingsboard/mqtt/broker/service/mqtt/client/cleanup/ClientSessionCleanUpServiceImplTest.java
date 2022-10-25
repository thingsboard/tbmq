/**
 * Copyright © 2016-2022 The Thingsboard Authors
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
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.service.mqtt.ClientSession;
import org.thingsboard.mqtt.broker.service.mqtt.client.disconnect.DisconnectClientCommandService;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventService;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionInfo;

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

    @SpyBean
    ClientSessionCleanUpServiceImpl clientSessionCleanUpService;

    @Test
    public void givenTwoSessions_whenWaitThreeSeconds_thenScheduledIsCalledAtLeastOneTime() {
        long currentTs = System.currentTimeMillis();
        long currentTsMinus10Secs = currentTs - TimeUnit.SECONDS.toMillis(10);

        int sessionExpiryInterval = 3;
        ClientSessionInfo clientSessionInfo1 = getClientSessionInfo(currentTs, true, sessionExpiryInterval);
        ClientSessionInfo clientSessionInfo2 = getClientSessionInfo(currentTsMinus10Secs, false, sessionExpiryInterval);

        when(clientSessionCache.getAllClientSessions()).thenReturn(Map.of(
                "client1", clientSessionInfo1,
                "client2", clientSessionInfo2
        ));

        await()
                .atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> verify(clientSessionCleanUpService, atLeast(1)).cleanUp());

        verify(clientSessionEventService, atMost(1)).requestSessionCleanup(any());
    }

    @Test
    public void givenSessions_whenCheckIfNotPersistent_thenReceiveExpectedResult() {
        Assert.assertFalse(clientSessionCleanUpService.isNotPersistent(
                getClientSessionInfo(System.currentTimeMillis(), true, 0)
        ));
        Assert.assertTrue(clientSessionCleanUpService.isNotPersistent(
                getClientSessionInfo(System.currentTimeMillis(), true, 100)
        ));
        Assert.assertTrue(clientSessionCleanUpService.isNotPersistent(
                getClientSessionInfo(System.currentTimeMillis(), false, 0)
        ));
        Assert.assertTrue(clientSessionCleanUpService.isNotPersistent(
                getClientSessionInfo(System.currentTimeMillis(), false, 100)
        ));
    }

    private ClientSessionInfo getClientSessionInfo(long lastUpdateTime, boolean persistent, int sessionExpiryInterval) {
        return ClientSessionInfo.builder()
                .lastUpdateTime(lastUpdateTime)
                .clientSession(ClientSession.builder()
                        .connected(false)
                        .sessionInfo(SessionInfo.builder()
                                .persistent(persistent)
                                .sessionExpiryInterval(sessionExpiryInterval)
                                .build())
                        .build())
                .build();
    }
}