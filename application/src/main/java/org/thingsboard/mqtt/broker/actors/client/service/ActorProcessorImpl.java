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
package org.thingsboard.mqtt.broker.actors.client.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.actors.client.messages.DisconnectMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.SessionInitMsg;
import org.thingsboard.mqtt.broker.actors.client.service.disconnect.DisconnectService;
import org.thingsboard.mqtt.broker.actors.client.state.ClientActorState;
import org.thingsboard.mqtt.broker.actors.client.state.SessionState;
import org.thingsboard.mqtt.broker.actors.client.util.ClientActorUtil;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.exception.AuthenticationException;
import org.thingsboard.mqtt.broker.service.auth.AuthenticationService;
import org.thingsboard.mqtt.broker.service.auth.AuthorizationRuleService;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.security.authorization.AuthorizationRule;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;

import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActorProcessorImpl implements ActorProcessor {

    private final DisconnectService disconnectService;
    private final AuthenticationService authenticationService;
    private final AuthorizationRuleService authorizationRuleService;
    private final MqttMessageGenerator mqttMessageGenerator;

    @Override
    public void onInit(ClientActorState state, SessionInitMsg sessionInitMsg) {
        ClientSessionCtx clientSessionCtx = sessionInitMsg.getClientSessionCtx();

        if (clientSessionCtx.getSessionId().equals(state.getCurrentSessionId())) {
            log.warn("[{}][{}] Trying to initialize the same session.", state.getClientId(), clientSessionCtx.getSessionId());
            if (state.getCurrentSessionState() != SessionState.DISCONNECTED) {
                state.updateSessionState(SessionState.DISCONNECTING);
                disconnectService.disconnect(state, new DisconnectReason(DisconnectReasonType.ON_ERROR, "Trying to init the same active session"));
            }
            return;
        }

        boolean clientAuthenticated = authenticateClient(clientSessionCtx, sessionInitMsg.getUsername(), sessionInitMsg.getPasswordBytes(), state.getClientId());
        if (!clientAuthenticated) {
            clientSessionCtx.getChannel().writeAndFlush(mqttMessageGenerator.createMqttConnAckMsg(CONNECTION_REFUSED_NOT_AUTHORIZED, false));
            clientSessionCtx.getChannel().close();
            return;
        }

        SessionState sessionState = state.getCurrentSessionState();
        if (sessionState != SessionState.DISCONNECTED) {
            log.debug("[{}] Session was in {} state while Actor received INIT message, prev sessionId - {}, new sessionId - {}.",
                    state.getClientId(), sessionState, state.getCurrentSessionId(), clientSessionCtx.getSessionId());
            state.updateSessionState(SessionState.DISCONNECTING);
            disconnectService.disconnect(state, new DisconnectReason(DisconnectReasonType.ON_CONFLICTING_SESSIONS));
        }

        state.updateSessionState(SessionState.INITIALIZED);
        state.setClientSessionCtx(clientSessionCtx);
        state.clearStopActorCommandId();
    }

    @Override
    public void onDisconnect(ClientActorState state, DisconnectMsg disconnectMsg) {
        if (state.getCurrentSessionState() == SessionState.DISCONNECTED) {
            log.debug("[{}][{}] Session is already disconnected.", state.getClientId(), state.getCurrentSessionId());
            return;
        }

        if (state.getCurrentSessionState() == SessionState.DISCONNECTING) {
            log.warn("[{}][{}] Session is in {} state. Will try to disconnect again.", state.getClientId(), state.getCurrentSessionId(), SessionState.DISCONNECTING);
        }

        state.updateSessionState(SessionState.DISCONNECTING);
        disconnectService.disconnect(state, disconnectMsg.getReason());
        state.updateSessionState(SessionState.DISCONNECTED);
    }

    private boolean authenticateClient(ClientSessionCtx ctx, String username, byte[] passwordBytes, String clientId) {
        try {
            // TODO: make it with Plugin architecture (to be able to use LDAP, OAuth etc)
            MqttClientCredentials clientCredentials = authenticationService.authenticate(clientId, username, passwordBytes, ctx.getSslHandler());
            configureAuthorizationRule(ctx, clientId, clientCredentials);
            return true;
        } catch (AuthenticationException e) {
            log.debug("[{}] Authentication failed. Reason - {}.", clientId, e.getMessage());
            return false;
        }
    }

    private void configureAuthorizationRule(ClientSessionCtx ctx, String clientId, MqttClientCredentials clientCredentials) throws AuthenticationException {
        if (clientCredentials == null) {
            return;
        }
        AuthorizationRule authorizationRule = null;
        if (clientCredentials.getCredentialsType() == ClientCredentialsType.SSL) {
            String clientCommonName = authenticationService.getClientCertificateCommonName(ctx.getSslHandler());
            authorizationRule = authorizationRuleService.parseSslAuthorizationRule(clientCredentials.getCredentialsValue(), clientCommonName);
        } else if (clientCredentials.getCredentialsType() == ClientCredentialsType.MQTT_BASIC) {
            authorizationRule = authorizationRuleService.parseBasicAuthorizationRule(clientCredentials.getCredentialsValue());
        }
        if (authorizationRule != null) {
            log.debug("[{}] Authorization rule for client - {}.", clientId, authorizationRule.getPattern().toString());
        }
        ctx.setAuthorizationRule(authorizationRule);
    }

}