/**
 * Copyright © 2016-2024 The Thingsboard Authors
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

import io.netty.handler.codec.mqtt.MqttConnAckMessage;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.actors.client.messages.EnhancedAuthInitMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.SessionInitMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.EnhancedAuthMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttAuthMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttDisconnectMsg;
import org.thingsboard.mqtt.broker.actors.client.service.disconnect.DisconnectService;
import org.thingsboard.mqtt.broker.actors.client.state.ClientActorState;
import org.thingsboard.mqtt.broker.actors.client.state.SessionState;
import org.thingsboard.mqtt.broker.adaptor.NettyMqttConverter;
import org.thingsboard.mqtt.broker.exception.AuthenticationException;
import org.thingsboard.mqtt.broker.service.auth.AuthenticationService;
import org.thingsboard.mqtt.broker.service.auth.EnhancedAuthenticationService;
import org.thingsboard.mqtt.broker.service.auth.enhanced.EnhancedAuthContext;
import org.thingsboard.mqtt.broker.service.auth.enhanced.EnhancedAuthResponse;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthContext;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthResponse;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.security.authorization.AuthRulePatterns;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;
import org.thingsboard.mqtt.broker.util.MqttReasonCodeResolver;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED;
import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_UNSPECIFIED_ERROR;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActorProcessorImpl implements ActorProcessor {

    private final DisconnectService disconnectService;
    private final AuthenticationService authenticationService;
    private final EnhancedAuthenticationService enhancedAuthenticationService;
    private final MqttMessageGenerator mqttMessageGenerator;
    private final ClientMqttActorManager clientMqttActorManager;

    @Override
    public void onInit(ClientActorState state, SessionInitMsg sessionInitMsg) {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Processing SESSION_INIT_MSG onInit {}", state.getClientId(), sessionInitMsg);
        }
        ClientSessionCtx sessionCtx = sessionInitMsg.getClientSessionCtx();

        if (sessionCtx.getSessionId().equals(state.getCurrentSessionId())) {
            tryDisconnectSameSession(state, sessionCtx);
            return;
        }

        AuthContext authContext = buildAuthContext(state, sessionInitMsg);
        AuthResponse authResponse = authenticateClient(authContext);

        if (!authResponse.isSuccess()) {
            log.warn("[{}] Connection is not established due to: {}", state.getClientId(), CONNECTION_REFUSED_NOT_AUTHORIZED);
            sendConnectionRefusedNotAuthorizedMsgAndCloseChannel(sessionCtx);
            return;
        }

        finishDefaultSessionAuth(state.getClientId(), sessionCtx, authResponse);

        if (state.getCurrentSessionState() != SessionState.DISCONNECTED) {
            disconnectCurrentSession(state, sessionCtx);
        }

        updateClientActorState(state, SessionState.INITIALIZED, sessionCtx);
    }

    @Override
    public void onEnhancedAuthInit(ClientActorState state, EnhancedAuthInitMsg enhancedAuthInitMsg) {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Processing ENHANCED_AUTH_INIT_MSG onEnhancedAuthInit {}", state.getClientId(), enhancedAuthInitMsg);
        }
        var sessionCtx = enhancedAuthInitMsg.getClientSessionCtx();

        if (sessionCtx.getSessionId().equals(state.getCurrentSessionId())) {
            tryDisconnectSameSession(state, sessionCtx);
            return;
        }

        EnhancedAuthContext authContext = buildEnhancedAuthContext(state, enhancedAuthInitMsg);
        boolean challengeStarted = enhancedAuthenticationService.onClientConnectMsg(sessionCtx, authContext);
        if (!challengeStarted) {
            sendConnectionRefusedUnspecifiedErrorAndCloseChannel(sessionCtx);
            return;
        }

        if (state.getCurrentSessionState() != SessionState.DISCONNECTED) {
            disconnectCurrentSession(state, sessionCtx);
        }

        updateClientActorState(state, SessionState.ENHANCED_AUTH_STARTED, sessionCtx);
    }

    @Override
    public void onEnhancedAuthContinue(ClientActorState state, MqttAuthMsg authMsg) {
        if (log.isTraceEnabled()) {
            log.trace("[{}][{}] Processing MQTT_AUTH_MSG onEnhancedAuthContinue {}",
                    state.getClientId(), state.getCurrentSessionState(), authMsg);
        }
        var sessionCtx = state.getCurrentSessionCtx();

        boolean reAuth = SessionState.CONNECTED.equals(state.getCurrentSessionState());
        if (reAuth) {
            onEnhancedReAuth(state, authMsg, sessionCtx);
            return;
        }

        boolean auth = SessionState.ENHANCED_AUTH_STARTED.equals(state.getCurrentSessionState());
        if (auth) {
            onEnhancedAuth(state, authMsg, sessionCtx);
            return;
        }

        if (log.isDebugEnabled()) {
            log.debug("[{}] Session was in {} state while Actor received enhanced auth continue message, prev sessionId - {}, new sessionId - {}.",
                    state.getClientId(), state.getCurrentSessionState(), state.getCurrentSessionId(), sessionCtx.getSessionId());
        }
        updateClientActorState(state, SessionState.DISCONNECTED, sessionCtx);
        sendConnectionRefusedUnspecifiedErrorAndCloseChannel(sessionCtx);
    }

    private void onEnhancedAuth(ClientActorState state, MqttAuthMsg authMsg, ClientSessionCtx sessionCtx) {
        EnhancedAuthContext authContext = buildEnhancedAuthContext(state, authMsg);
        EnhancedAuthResponse authResponse = enhancedAuthenticationService.onAuthContinue(sessionCtx, authContext, false);
        if (!authResponse.isSuccess()) {
            updateClientActorState(state, SessionState.DISCONNECTED, sessionCtx);
            sendConnectionRefusedMsgAndCloseChannel(sessionCtx, authResponse.getFailureReasonCode());
            return;
        }

        finishEnhancedSessionAuth(state.getClientId(), sessionCtx, authResponse);

        updateClientActorState(state, SessionState.INITIALIZED, sessionCtx);
        clientMqttActorManager.connect(state.getClientId(),
                NettyMqttConverter.createMqttConnectMsg(sessionCtx.getSessionId(), sessionCtx.getConnectMsgFromEnhancedAuth()));
        sessionCtx.clearScramServer();
        sessionCtx.clearConnectMsg();
    }

    private void onEnhancedReAuth(ClientActorState state, MqttAuthMsg authMsg, ClientSessionCtx sessionCtx) {
        EnhancedAuthContext authContext = buildEnhancedAuthContext(state, authMsg);
        EnhancedAuthResponse authResponse = enhancedAuthenticationService.onAuthContinue(sessionCtx, authContext, true);
        if (!authResponse.isSuccess()) {
            clientMqttActorManager.disconnect(state.getClientId(), new MqttDisconnectMsg(sessionCtx.getSessionId(),
                    new DisconnectReason(DisconnectReasonType.NOT_AUTHORIZED)));
            return;
        }
        finishEnhancedSessionAuth(state.getClientId(), sessionCtx, authResponse);
        sessionCtx.clearScramServer();
    }

    @Override
    public void onEnhancedReAuth(ClientActorState state, MqttAuthMsg authMsg) {
        if (log.isTraceEnabled()) {
            log.trace("[{}][{}] Processing MqttAuthMsg onEnhancedReAuth {}",
                    state.getClientId(), state.getCurrentSessionState(), authMsg);
        }

        var sessionCtx = state.getCurrentSessionCtx();

        var reAuth = SessionState.CONNECTED.equals(state.getCurrentSessionState());
        if (!reAuth) {
            if (log.isDebugEnabled()) {
                log.debug("[{}] Session was in {} state while Actor received enhanced re-auth message, prev sessionId - {}, new sessionId - {}.",
                        state.getClientId(), state.getCurrentSessionState(), state.getCurrentSessionId(), sessionCtx.getSessionId());
            }
            clientMqttActorManager.disconnect(state.getClientId(), new MqttDisconnectMsg(sessionCtx.getSessionId(),
                    new DisconnectReason(DisconnectReasonType.ON_PROTOCOL_ERROR)));
            return;
        }
        EnhancedAuthContext authContext = buildEnhancedAuthContext(state, authMsg);
        boolean success = enhancedAuthenticationService.onReAuth(sessionCtx, authContext);
        if (!success) {
            clientMqttActorManager.disconnect(state.getClientId(), new MqttDisconnectMsg(sessionCtx.getSessionId(),
                    new DisconnectReason(DisconnectReasonType.NOT_AUTHORIZED)));
        }
    }

    private void tryDisconnectSameSession(ClientActorState state, ClientSessionCtx sessionCtx) {
        log.warn("[{}][{}] Trying to initialize the same session.", state.getClientId(), sessionCtx.getSessionId());
        if (state.getCurrentSessionState() != SessionState.DISCONNECTED) {
            state.updateSessionState(SessionState.DISCONNECTING);
            DisconnectReason reason = new DisconnectReason(DisconnectReasonType.ON_CONFLICTING_SESSIONS, "Trying to init the same active session");
            disconnect(state, newDisconnectMsg(state.getCurrentSessionId(), reason));
        }
    }

    void sendConnectionRefusedNotAuthorizedMsgAndCloseChannel(ClientSessionCtx sessionCtx) {
        sendConnectionRefusedMsgAndCloseChannel(sessionCtx,
                MqttReasonCodeResolver.connectionRefusedNotAuthorized(sessionCtx));
    }

    private void sendConnectionRefusedUnspecifiedErrorAndCloseChannel(ClientSessionCtx sessionCtx) {
        sendConnectionRefusedMsgAndCloseChannel(sessionCtx, CONNECTION_REFUSED_UNSPECIFIED_ERROR);
    }

    private void sendConnectionRefusedMsgAndCloseChannel(ClientSessionCtx sessionCtx, MqttConnectReturnCode returnCode) {
        MqttConnAckMessage msg = mqttMessageGenerator.createMqttConnAckMsg(returnCode);
        sessionCtx.getChannel().writeAndFlush(msg);
        sessionCtx.closeChannel();
    }

    private void disconnectCurrentSession(ClientActorState state, ClientSessionCtx sessionCtx) {
        if (log.isDebugEnabled()) {
            log.debug("[{}] Session was in {} state while Actor received INIT message, prev sessionId - {}, new sessionId - {}.",
                    state.getClientId(), state.getCurrentSessionState(), state.getCurrentSessionId(), sessionCtx.getSessionId());
        }
        state.updateSessionState(SessionState.DISCONNECTING);
        DisconnectReason reason = new DisconnectReason(DisconnectReasonType.ON_CONFLICTING_SESSIONS);
        disconnect(state, newDisconnectMsg(state.getCurrentSessionId(), reason));
    }

    void updateClientActorState(ClientActorState state, SessionState stateStatus, ClientSessionCtx sessionCtx) {
        state.updateSessionState(stateStatus);
        state.setClientSessionCtx(sessionCtx);
        state.clearStopActorCommandId();
    }

    private void finishDefaultSessionAuth(String clientId, ClientSessionCtx sessionCtx, AuthResponse authResponse) {
        List<AuthRulePatterns> authRulePatterns = authResponse.getAuthRulePatterns();
        if (!CollectionUtils.isEmpty(authRulePatterns)) {
            logAuthRules(clientId, authRulePatterns);
            sessionCtx.setAuthRulePatterns(authRulePatterns);
        }
        sessionCtx.setClientType(authResponse.getClientType());
    }

    private void finishEnhancedSessionAuth(String clientId, ClientSessionCtx sessionCtx, EnhancedAuthResponse authResponse) {
        List<AuthRulePatterns> authRulePatterns = authResponse.getAuthRulePatterns();
        if (!CollectionUtils.isEmpty(authRulePatterns)) {
            logAuthRules(clientId, authRulePatterns);
            sessionCtx.setAuthRulePatterns(authRulePatterns);
        }
        sessionCtx.setClientType(authResponse.getClientType());
    }

    private void logAuthRules(String clientId, List<AuthRulePatterns> authRulePatterns) {
        if (log.isDebugEnabled()) {
            log.debug("[{}] Authorization rules for client - pub: {}, sub: {}.",
                    clientId, toPubSet(authRulePatterns), toSubSet(authRulePatterns));
        }
    }

    private Set<String> toPubSet(List<AuthRulePatterns> authRulePatterns) {
        Stream<List<Pattern>> pubPatterns = authRulePatterns.stream().map(AuthRulePatterns::getPubPatterns);
        return toSet(pubPatterns);
    }

    private Set<String> toSubSet(List<AuthRulePatterns> authRulePatterns) {
        Stream<List<Pattern>> subPatterns = authRulePatterns.stream().map(AuthRulePatterns::getSubPatterns);
        return toSet(subPatterns);
    }

    private Set<String> toSet(Stream<List<Pattern>> stream) {
        return stream.flatMap(List::stream)
                .map(Pattern::toString)
                .collect(Collectors.toSet());
    }

    @Override
    public void onDisconnect(ClientActorState state, MqttDisconnectMsg disconnectMsg) {
        if (state.getCurrentSessionState() == SessionState.DISCONNECTED) {
            if (log.isDebugEnabled()) {
                log.debug("[{}][{}] Session is already disconnected.", state.getClientId(), state.getCurrentSessionId());
            }
            return;
        }

        if (state.getCurrentSessionState() == SessionState.DISCONNECTING) {
            log.warn("[{}][{}] Session is in {} state. Skipping this msg", state.getClientId(), state.getCurrentSessionId(), SessionState.DISCONNECTING);
            return;
        }

        state.updateSessionState(SessionState.DISCONNECTING);
        disconnect(state, disconnectMsg);
        state.updateSessionState(SessionState.DISCONNECTED);
    }

    private void disconnect(ClientActorState state, MqttDisconnectMsg disconnectMsg) {
        disconnectService.disconnect(state, disconnectMsg);
    }

    private AuthResponse authenticateClient(AuthContext authContext) {
        try {
            // TODO: make it with Plugin architecture (to be able to use LDAP, OAuth etc.)
            return authenticationService.authenticate(authContext);
        } catch (AuthenticationException e) {
            if (log.isDebugEnabled()) {
                log.debug("[{}] Authentication failed.", authContext.getClientId(), e);
            }
            return AuthResponse.failure();
        }
    }

    private AuthContext buildAuthContext(ClientActorState state, SessionInitMsg sessionInitMsg) {
        return AuthContext.builder()
                .clientId(state.getClientId())
                .username(sessionInitMsg.getUsername())
                .passwordBytes(sessionInitMsg.getPasswordBytes())
                .sslHandler(sessionInitMsg.getClientSessionCtx().getSslHandler())
                .build();
    }

    private EnhancedAuthContext buildEnhancedAuthContext(ClientActorState state, EnhancedAuthMsg enhancedAuthMsg) {
        return EnhancedAuthContext.builder()
                .clientId(state.getClientId())
                .authMethod(enhancedAuthMsg.getAuthMethod())
                .authData(enhancedAuthMsg.getAuthData())
                .build();
    }

    private MqttDisconnectMsg newDisconnectMsg(UUID sessionId, DisconnectReason reason) {
        return new MqttDisconnectMsg(sessionId, reason);
    }
}
