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
package org.thingsboard.mqtt.broker.standalone.event;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thingsboard.mqtt.broker.actors.client.messages.ConnectionRequestInfo;
import org.thingsboard.mqtt.broker.actors.client.messages.cluster.ClearSessionMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.cluster.ConnectionRequestMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.cluster.SessionClusterManagementMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.cluster.SessionDisconnectedMsg;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionCallbackMsgFactoryImpl;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventActorManager;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventFactoryImpl;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.data.ClientCleanupInfo;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.data.ClientConnectInfo;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class StandaloneClientSessionEventServiceTest {

    @Mock
    ClientSessionEventActorManager actorManager;
    @Mock
    TbQueueCallback queueCallback;

    PendingConnectionRequests pending;
    StandaloneClientSessionEventService service;
    LocalClientSessionEventResponseSender sender;

    @BeforeEach
    void setUp() {
        pending = new PendingConnectionRequests(1, 100, 20);
        pending.init();
        service = new StandaloneClientSessionEventService(new ClientSessionEventFactoryImpl(),
                new ClientSessionCallbackMsgFactoryImpl(), actorManager, pending);
        sender = new LocalClientSessionEventResponseSender(pending);
    }

    @AfterEach
    void tearDown() {
        pending.destroy();
    }

    private ClientConnectInfo connectInfo() {
        return new ClientConnectInfo("MQTT_5_0", "auth", false);
    }

    private SessionClusterManagementMsg sentMsg(String clientId) {
        var msg = ArgumentCaptor.forClass(SessionClusterManagementMsg.class);
        verify(actorManager).sendSessionClusterManagementMsg(eq(clientId), msg.capture());
        return msg.getValue();
    }

    @Test
    void aConnectionRequestReachesTheClientActorAndCompletesFromTheLocalSender() {
        long before = System.currentTimeMillis();
        var future = service.requestConnection(PendingConnectionRequestsTest.sessionInfo("c1"), connectInfo());

        var request = (ConnectionRequestMsg) sentMsg("c1");
        assertThat(request.getRequestInfo().getResponseTopic()).isEqualTo("local");
        assertThat(request.getRequestInfo().getRequestTime()).isBetween(before, System.currentTimeMillis());
        assertThat(request.getConnectInfo()).isEqualTo(connectInfo());
        assertThat(future).isNotDone();

        sender.send("c1", request.getRequestInfo(), ProtoConverter.toSuccessConnectionResponseProto(true));

        assertThat(future).succeedsWithin(Duration.ofSeconds(1)).satisfies(r -> assertThat(r.isSessionPresent()).isTrue());
    }

    @Test
    void aRequestNobodyAnswersTimesOut() {
        var future = service.requestConnection(PendingConnectionRequestsTest.sessionInfo("c1"), connectInfo());

        assertThatThrownBy(() -> future.get(2, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(TimeoutException.class);
    }

    @Test
    void aFullPendingMapFailsAtOnce() {
        service.requestConnection(PendingConnectionRequestsTest.sessionInfo("c1"), connectInfo());

        var second = service.requestConnection(PendingConnectionRequestsTest.sessionInfo("c2"), connectInfo());

        assertThat(second).isDone();
        assertThatThrownBy(second::get).hasMessageContaining("Pending request map is full");
        verify(actorManager).sendSessionClusterManagementMsg(eq("c1"), any());
        verifyNoMoreInteractions(actorManager);
    }

    @Test
    void aConnectionRequestTheActorCannotTakeFailsTheFuture() {
        doThrow(new IllegalStateException("no actor")).when(actorManager).sendSessionClusterManagementMsg(eq("c1"), any());

        var future = service.requestConnection(PendingConnectionRequestsTest.sessionInfo("c1"), connectInfo());

        assertThat(future).isDone();
        assertThatThrownBy(future::get).hasRootCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void aConnectionRequestTheActorFailsFailsTheFuture() {
        var future = service.requestConnection(PendingConnectionRequestsTest.sessionInfo("c1"), connectInfo());

        ((ConnectionRequestMsg) sentMsg("c1")).getCallback().onFailure(new IllegalStateException("actor stopped"));

        assertThatThrownBy(future::get).hasRootCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void aDisconnectNotificationInvokesTheQueueCallback() {
        service.notifyClientDisconnected(PendingConnectionRequestsTest.sessionInfo("c1"),
                DisconnectReasonType.ON_DISCONNECT_MSG, queueCallback);

        var msg = (SessionDisconnectedMsg) sentMsg("c1");
        assertThat(msg.getReasonType()).isEqualTo(DisconnectReasonType.ON_DISCONNECT_MSG);
        verifyNoInteractions(queueCallback);

        msg.getCallback().onSuccess();

        verify(queueCallback).onSuccess(isNull());
    }

    @Test
    void aFailedDisconnectNotificationFailsTheQueueCallback() {
        service.notifyClientDisconnected(PendingConnectionRequestsTest.sessionInfo("c1"),
                DisconnectReasonType.ON_ERROR, queueCallback);
        var failure = new IllegalStateException("boom");

        ((SessionDisconnectedMsg) sentMsg("c1")).getCallback().onFailure(failure);

        verify(queueCallback).onFailure(failure);
    }

    @Test
    void aDisconnectNotificationTheActorCannotTakeFailsTheQueueCallback() {
        var failure = new IllegalStateException("no actor");
        doThrow(failure).when(actorManager).sendSessionClusterManagementMsg(eq("c1"), any());

        service.notifyClientDisconnected(PendingConnectionRequestsTest.sessionInfo("c1"),
                DisconnectReasonType.ON_ERROR, queueCallback);

        verify(queueCallback).onFailure(failure);
    }

    @Test
    void aCleanupRequestSendsAClearSessionMsg() {
        var sessionInfo = ClientSessionInfo.builder()
                .serviceId("lite").sessionId(UUID.randomUUID()).clientId("c1").type(ClientType.DEVICE)
                .clientIpAdr(new byte[]{127, 0, 0, 1}).build();

        service.requestClientSessionCleanup(sessionInfo, ClientCleanupInfo.FORCEFUL);

        var msg = (ClearSessionMsg) sentMsg("c1");
        assertThat(msg.getSessionId()).isEqualTo(sessionInfo.getSessionId());
        assertThat(msg.getCleanupInfo()).isEqualTo(ClientCleanupInfo.FORCEFUL);
    }

    @Test
    void anApplicationTopicRemovalIsNotSentAnywhere() {
        service.requestApplicationTopicRemoved("c1");

        verifyNoInteractions(actorManager);
    }

    @Test
    void theLocalSenderIgnoresAResponseNobodyWaitsFor() {
        var info = new ConnectionRequestInfo(UUID.randomUUID(), 1L, "local");

        sender.send("c1", info, ProtoConverter.toSuccessConnectionResponseProto(false));

        verifyNoInteractions(actorManager);
    }

    @Test
    void theNoOpConsumerDoesNothing() {
        new NoOpClientSessionEventConsumer().startConsuming();
    }
}
