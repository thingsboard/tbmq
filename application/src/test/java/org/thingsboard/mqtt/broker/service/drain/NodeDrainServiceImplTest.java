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
package org.thingsboard.mqtt.broker.service.drain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttDisconnectMsg;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCtxService;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeDrainServiceImplTest {

    @Mock
    private ClientSessionCtxService sessionCtxService;
    @Mock
    private ClientMqttActorManager clientMqttActorManager;
    private NodeDrainServiceImpl service;
    private Collection<ClientSessionCtx> sessions;

    @BeforeEach
    void setUp() {
        NodeDrainSettings settings = new NodeDrainSettings();
        settings.setLoadBalancerWaitMs(0);
        settings.setBatchSize(2);
        settings.setBatchIntervalMs(500);
        settings.setTimeoutMs(2_000);

        sessions = new CopyOnWriteArrayList<>();
        lenient().when(sessionCtxService.getAllClientSessionCtx()).thenAnswer(invocation -> new ArrayList<>(sessions));
        lenient().when(sessionCtxService.getSessionsCount()).thenAnswer(invocation -> sessions.size());
        service = new NodeDrainServiceImpl(sessionCtxService, clientMqttActorManager, settings);
    }

    @AfterEach
    void tearDown() {
        service.destroy();
    }

    @Test
    void givenActiveNode_whenDrainStarts_thenImmediatelyReportsDraining() {
        sessions.add(session("client-1"));

        NodeDrainStatus status = service.startDrain();

        assertThat(status.getState()).isEqualTo(NodeDrainState.DRAINING);
        assertThat(status.getInitialSessions()).isEqualTo(1);
        assertThat(service.isDraining()).isTrue();
    }

    @Test
    void givenThreeSessions_whenDraining_thenDisconnectsInConfiguredBatchesAndCompletes() {
        sessions.add(session("client-1"));
        sessions.add(session("client-2"));
        sessions.add(session("client-3"));

        service.startDrain();

        await().atMost(Duration.ofSeconds(1)).untilAsserted(() ->
                assertThat(service.getStatus().getDisconnectRequests()).isEqualTo(2));
        assertThat(service.getStatus().getDisconnectRequests()).isEqualTo(2);

        await().atMost(Duration.ofSeconds(1)).untilAsserted(() ->
                assertThat(service.getStatus().getDisconnectRequests()).isEqualTo(3));

        sessions.clear();
        await().atMost(Duration.ofSeconds(1)).untilAsserted(() ->
                assertThat(service.getStatus().getState()).isEqualTo(NodeDrainState.DRAINED));

        ArgumentCaptor<MqttDisconnectMsg> disconnectCaptor = ArgumentCaptor.forClass(MqttDisconnectMsg.class);
        verify(clientMqttActorManager, times(3)).disconnect(org.mockito.ArgumentMatchers.anyString(), disconnectCaptor.capture());
        assertThat(disconnectCaptor.getAllValues())
                .allMatch(msg -> msg.getReason().getType() == DisconnectReasonType.ON_SERVER_SHUTTING_DOWN);
    }

    @Test
    void givenDrainAlreadyStarted_whenStartedAgain_thenReturnsSameOperation() {
        NodeDrainStatus first = service.startDrain();
        NodeDrainStatus second = service.startDrain();

        assertThat(second.getStartedAt()).isEqualTo(first.getStartedAt());
        assertThat(second.getState()).isNotEqualTo(NodeDrainState.ACTIVE);
    }

    @Test
    void givenSessionDoesNotDisconnectBeforeTimeout_whenDraining_thenReportsTimedOut() {
        NodeDrainSettings settings = new NodeDrainSettings();
        settings.setLoadBalancerWaitMs(0);
        settings.setBatchSize(1);
        settings.setBatchIntervalMs(5);
        settings.setTimeoutMs(25);
        service.destroy();
        service = new NodeDrainServiceImpl(sessionCtxService, clientMqttActorManager, settings);
        sessions.add(session("stuck-client"));

        service.startDrain();

        await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> {
            NodeDrainStatus status = service.getStatus();
            assertThat(status.getState()).isEqualTo(NodeDrainState.TIMED_OUT);
            assertThat(status.getRemainingSessions()).isEqualTo(1);
        });
    }

    private ClientSessionCtx session(String clientId) {
        ClientSessionCtx session = mock(ClientSessionCtx.class);
        lenient().when(session.getClientId()).thenReturn(clientId);
        lenient().when(session.getSessionId()).thenReturn(UUID.randomUUID());
        return session;
    }

}
