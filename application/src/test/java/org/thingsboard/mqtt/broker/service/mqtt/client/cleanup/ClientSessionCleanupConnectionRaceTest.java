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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.thingsboard.mqtt.broker.actors.ActorSystemContext;
import org.thingsboard.mqtt.broker.actors.ClientActorContext;
import org.thingsboard.mqtt.broker.actors.TbActorCtx;
import org.thingsboard.mqtt.broker.actors.TbActorId;
import org.thingsboard.mqtt.broker.actors.TbActorRef;
import org.thingsboard.mqtt.broker.actors.TbActorSystem;
import org.thingsboard.mqtt.broker.actors.client.ClientActor;
import org.thingsboard.mqtt.broker.actors.client.ClientActorConfiguration;
import org.mockito.ArgumentCaptor;
import org.junit.Assert;
import org.thingsboard.mqtt.broker.actors.client.state.ClientActorState;
import org.thingsboard.mqtt.broker.actors.client.state.SessionState;
import org.thingsboard.mqtt.broker.actors.msg.TbActorMsg;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.service.mqtt.client.disconnect.DisconnectClientCommandService;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventService;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCtxService;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManagerImpl;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.util.ClientSessionInfoFactory;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.thingsboard.mqtt.broker.session.DisconnectReasonType.ON_ADMINISTRATIVE_ACTION;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {ClientSessionCleanUpServiceImpl.class, ClientMqttActorManagerImpl.class})
public class ClientSessionCleanupConnectionRaceTest {
    private static final String CLIENT = "connecting-client";
    private static final String SERVICE = "broker";
    @MockitoBean ClientSessionCache cache;
    @MockitoBean ClientSessionCtxService contexts;
    @MockitoBean ClientSessionEventService events;
    @MockitoBean DisconnectClientCommandService disconnect;
    @MockitoBean ServiceInfoProvider services;
    @MockitoBean ActorSystemContext systemContext;
    @MockitoBean TbActorSystem actorSystem;
    @Autowired ClientSessionCleanUpServiceImpl cleanup;
    private ClientActorState state;
    private ClientSessionInfo session;
    private TbActorRef actorRef;
    private ClientActor actor;

    @Before
    public void setup() throws Exception {
        when(services.getServiceId()).thenReturn(SERVICE);
        session = ClientSessionInfoFactory.getClientSessionInfo(CLIENT, SERVICE).toBuilder()
                .sessionId(UUID.randomUUID()).connectedAt(System.currentTimeMillis()).build();
        when(cache.getAllClientSessions()).thenReturn(Map.of(CLIENT, session));
        when(contexts.hasSession(CLIENT)).thenReturn(false);
        when(systemContext.getClientActorContext()).thenReturn(mock(ClientActorContext.class, RETURNS_DEEP_STUBS));
        when(systemContext.getClientActorConfiguration()).thenReturn(mock(ClientActorConfiguration.class));
        actor = new ClientActor(systemContext, CLIENT, false);
        actor.init(mock(TbActorCtx.class));
        state = (ClientActorState) ReflectionTestUtils.getField(actor, "state");
        var physical = mock(ClientSessionCtx.class);
        when(physical.getSessionId()).thenReturn(session.getSessionId());
        state.setClientSessionCtx(physical);
        state.updateSessionState(SessionState.CONNECTING);
        actorRef = mock(TbActorRef.class);
        when(actorSystem.getActor(any(TbActorId.class))).thenReturn(actorRef);
        doAnswer(i -> { actor.process(i.getArgument(0, TbActorMsg.class)); return null; })
                .when(actorRef).tell(any(TbActorMsg.class));
    }

    @Test
    public void clusterConnectedBeforeLocalRegistrationMustNotBecomeGhost() {
        cleanup.cleanUp();
        verify(events, never()).notifyClientDisconnected(any(), any(), any());
    }

    @Test
    public void establishedActorMustNotBecomeGhostFromStaleContextRead() {
        state.updateSessionState(SessionState.CONNECTED);
        cleanup.cleanUp();
        verify(events, never()).notifyClientDisconnected(any(), any(), any());
    }

    @Test
    public void disconnectedActorIsStillCleaned() {
        state.updateSessionState(SessionState.DISCONNECTED);
        cleanup.cleanUp();
        verify(events).notifyClientDisconnected(
                eq(ClientSessionInfoFactory.clientSessionInfoToSessionInfo(session)),
                eq(ON_ADMINISTRATIVE_ACTION), eq(null));
    }

    @Test
    public void replacementSessionKeepsItsOwnIdentityAndState() {
        var replacement = mock(ClientSessionCtx.class);
        UUID replacementId = UUID.randomUUID();
        when(replacement.getSessionId()).thenReturn(replacementId);
        state.setClientSessionCtx(replacement);
        state.updateSessionState(SessionState.CONNECTED);
        cleanup.cleanUp();
        verify(events).notifyClientDisconnected(
                eq(ClientSessionInfoFactory.clientSessionInfoToSessionInfo(session)),
                eq(ON_ADMINISTRATIVE_ACTION), eq(null));
        Assert.assertEquals(replacementId, state.getCurrentSessionId());
        Assert.assertEquals(SessionState.CONNECTED, state.getCurrentSessionState());
    }

    @Test
    public void checkUsesStateWhenActorProcessesIt() {
        doNothing().when(actorRef).tell(any(TbActorMsg.class));
        cleanup.cleanUp();
        verify(events, never()).notifyClientDisconnected(any(), any(), any());
        var queued = ArgumentCaptor.forClass(TbActorMsg.class);
        verify(actorRef).tell(queued.capture());
        state.updateSessionState(SessionState.CONNECTED);
        actor.process(queued.getValue());
        verify(events, never()).notifyClientDisconnected(any(), any(), any());
    }

    @Test
    public void nonWritableConnectionIsNotAGhost() {
        state.updateSessionState(SessionState.CHANNEL_NON_WRITABLE);
        cleanup.cleanUp();
        verify(events, never()).notifyClientDisconnected(any(), any(), any());
    }

    @Test
    public void genuinelyMissingActorIsStillCleaned() {
        when(actorSystem.getActor(any(TbActorId.class))).thenReturn(null);
        cleanup.cleanUp();
        verify(events).notifyClientDisconnected(
                eq(ClientSessionInfoFactory.clientSessionInfoToSessionInfo(session)),
                eq(ON_ADMINISTRATIVE_ACTION), eq(null));
    }
}
