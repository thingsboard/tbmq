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
package org.thingsboard.mqtt.broker.actors.client.service;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.actors.client.messages.SessionInitMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttDisconnectMsg;
import org.thingsboard.mqtt.broker.actors.client.service.disconnect.DisconnectService;
import org.thingsboard.mqtt.broker.actors.client.state.ClientActorState;
import org.thingsboard.mqtt.broker.actors.client.state.DefaultClientActorState;
import org.thingsboard.mqtt.broker.actors.client.state.SessionState;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.exception.AuthenticationException;
import org.thingsboard.mqtt.broker.service.auth.AuthenticationService;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthResponse;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.security.authorization.AuthRulePatterns;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class ActorProcessorImplTest {

    ActorProcessorImpl actorProcessor;
    DisconnectService disconnectService;
    AuthenticationService authenticationService;
    MqttMessageGenerator mqttMessageGenerator;

    ClientActorState clientActorState;

    @Before
    public void setUp() {
        disconnectService = mock(DisconnectService.class);
        authenticationService = mock(AuthenticationService.class);
        mqttMessageGenerator = mock(MqttMessageGenerator.class);
        actorProcessor = spy(new ActorProcessorImpl(disconnectService, authenticationService, mqttMessageGenerator));

        clientActorState = new DefaultClientActorState("clientId", false, 0);
    }

    @Test
    public void givenConnectedSession_whenOnDisconnect_thenOk() {
        updateSessionState(SessionState.CONNECTED);

        doNothing().when(disconnectService).disconnect(any(), any());
        actorProcessor.onDisconnect(clientActorState, getDisconnectMsg());

        assertEquals(SessionState.DISCONNECTED, clientActorState.getCurrentSessionState());
        verify(disconnectService, times(1)).disconnect(any(), any());
    }

    @Test
    public void givenDisconnectedSession_whenOnDisconnect_thenOk() {
        updateSessionState(SessionState.DISCONNECTED);

        actorProcessor.onDisconnect(clientActorState, getDisconnectMsg());

        assertEquals(SessionState.DISCONNECTED, clientActorState.getCurrentSessionState());
        verify(disconnectService, never()).disconnect(any(), any());
    }

    @Test
    public void givenDisconnectedSession_whenOnInit_thenOk() throws AuthenticationException {
        updateSessionState(SessionState.DISCONNECTED);

        AuthResponse authResponse = getAuthResponse(true);
        doReturn(authResponse).when(authenticationService).authenticate(any());

        SessionInitMsg sessionInitMsg = getSessionInitMsg(getClientSessionCtx());
        actorProcessor.onInit(clientActorState, sessionInitMsg);

        assertEquals(SessionState.INITIALIZED, clientActorState.getCurrentSessionState());
        assertEquals(sessionInitMsg.getClientSessionCtx(), clientActorState.getCurrentSessionCtx());
        assertEquals(1, clientActorState.getCurrentSessionCtx().getAuthRulePatterns().size());
        assertEquals(1, clientActorState.getCurrentSessionCtx().getAuthRulePatterns().get(0).getPubPatterns().size());
        assertEquals(1, clientActorState.getCurrentSessionCtx().getAuthRulePatterns().get(0).getSubPatterns().size());
        assertEquals("test", clientActorState.getCurrentSessionCtx().getAuthRulePatterns().get(0).getPubPatterns().get(0).pattern());
        assertEquals("test", clientActorState.getCurrentSessionCtx().getAuthRulePatterns().get(0).getSubPatterns().get(0).pattern());
        assertEquals(ClientType.APPLICATION, clientActorState.getCurrentSessionCtx().getClientType());
    }

    @Test
    public void givenSameSession_whenOnInit_thenDisconnect() throws AuthenticationException {
        updateSessionState(SessionState.CONNECTED);

        ClientSessionCtx clientSessionCtx = getClientSessionCtx();
        clientActorState.setClientSessionCtx(clientSessionCtx);

        SessionInitMsg sessionInitMsg = getSessionInitMsg(clientSessionCtx);
        actorProcessor.onInit(clientActorState, sessionInitMsg);

        assertEquals(SessionState.DISCONNECTING, clientActorState.getCurrentSessionState());
        verify(disconnectService, times(1)).disconnect(any(), any());
        verify(authenticationService, never()).authenticate(any());
    }

    @Test
    public void givenDisconnectedSession_whenOnInitAndAuthenticateFailed_thenClose() throws AuthenticationException {
        updateSessionState(SessionState.DISCONNECTED);

        AuthResponse authResponse = getAuthResponse(false);
        doReturn(authResponse).when(authenticationService).authenticate(any());

        doNothing().when(actorProcessor).sendConnectionRefusedMsgAndCloseChannel(any());

        SessionInitMsg sessionInitMsg = getSessionInitMsg(getClientSessionCtx());
        actorProcessor.onInit(clientActorState, sessionInitMsg);

        assertEquals(SessionState.DISCONNECTED, clientActorState.getCurrentSessionState());
        verify(actorProcessor, never()).updateClientActorState(any(), any());
        verify(actorProcessor, times(1)).sendConnectionRefusedMsgAndCloseChannel(any());
    }

    private AuthResponse getAuthResponse(boolean success) {
        return new AuthResponse(success, ClientType.APPLICATION, getAuthorizationRules());
    }

    private List<AuthRulePatterns> getAuthorizationRules() {
        return List.of(AuthRulePatterns.newInstance(List.of(Pattern.compile("test"))));
    }

    private MqttDisconnectMsg getDisconnectMsg() {
        return new MqttDisconnectMsg(UUID.randomUUID(), new DisconnectReason(DisconnectReasonType.ON_DISCONNECT_MSG));
    }

    private SessionInitMsg getSessionInitMsg(ClientSessionCtx clientSessionCtx) {
        return new SessionInitMsg(clientSessionCtx, "userName", "password".getBytes(StandardCharsets.UTF_8));
    }

    private ClientSessionCtx getClientSessionCtx() {
        return new ClientSessionCtx(UUID.randomUUID(), null, 0);
    }

    private void updateSessionState(SessionState state) {
        clientActorState.updateSessionState(state);
    }
}