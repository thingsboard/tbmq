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
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttReasonCodes;
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
import org.thingsboard.mqtt.broker.common.data.UnauthorizedClient;
import org.thingsboard.mqtt.broker.common.util.BrokerConstants;
import org.thingsboard.mqtt.broker.common.util.DonAsynchron;
import org.thingsboard.mqtt.broker.dao.client.unauthorized.UnauthorizedClientService;
import org.thingsboard.mqtt.broker.exception.AuthenticationException;
import org.thingsboard.mqtt.broker.service.auth.AuthenticationService;
import org.thingsboard.mqtt.broker.service.auth.EnhancedAuthenticationService;
import org.thingsboard.mqtt.broker.service.auth.enhanced.EnhancedAuthContext;
import org.thingsboard.mqtt.broker.service.auth.enhanced.EnhancedAuthContinueResponse;
import org.thingsboard.mqtt.broker.service.auth.enhanced.EnhancedAuthFailureReason;
import org.thingsboard.mqtt.broker.service.auth.enhanced.EnhancedAuthFinalResponse;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthContext;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthResponse;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.security.authorization.AuthRulePatterns;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;
import org.thingsboard.mqtt.broker.util.BytesUtil;
import org.thingsboard.mqtt.broker.util.MqttReasonCodeResolver;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_ACCEPTED;
import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_BAD_AUTHENTICATION_METHOD;
import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED;
import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED_5;
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
    private final UnauthorizedClientService unauthorizedClientService;

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
            persistClientUnauthorized(state, sessionInitMsg, authResponse);
            sendConnectionRefusedNotAuthorizedMsgAndCloseChannel(sessionCtx);
            return;
        }
        if (log.isDebugEnabled()) {
            log.debug("[{}] Connection is authenticated: {}", state.getClientId(), CONNECTION_ACCEPTED);
        }

        removeClientUnauthorized(state);
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
        EnhancedAuthContinueResponse authResponse = enhancedAuthenticationService.onClientConnectMsg(sessionCtx, authContext);
        if (!authResponse.success()) {
            persistClientUnauthorized(state, enhancedAuthInitMsg.getClientSessionCtx(), authResponse.enhancedAuthFailureReason());
            sendConnectionRefusedNotAuthorizedMsgAndCloseChannel(sessionCtx);
            return;
        }

        sendAuthChallengeToClient(sessionCtx, authContext.getAuthMethod(),
                authResponse.response(), MqttReasonCodes.Auth.CONTINUE_AUTHENTICATION);

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
            EnhancedAuthContext authContext = buildEnhancedAuthContext(state, authMsg);
            EnhancedAuthFinalResponse authResponse = enhancedAuthenticationService.onReAuthContinue(sessionCtx, authContext);
            if (!authResponse.success()) {
                clientMqttActorManager.disconnect(state.getClientId(), new MqttDisconnectMsg(sessionCtx.getSessionId(),
                        new DisconnectReason(DisconnectReasonType.NOT_AUTHORIZED)));
                return;
            }
            sendAuthChallengeToClient(sessionCtx, authContext.getAuthMethod(),
                    authResponse.response(), MqttReasonCodes.Auth.SUCCESS);
            finishEnhancedSessionAuth(state.getClientId(), sessionCtx, authResponse);
            sessionCtx.clearScramServer();
            return;
        }

        boolean auth = SessionState.ENHANCED_AUTH_STARTED.equals(state.getCurrentSessionState());
        if (auth) {
            EnhancedAuthContext authContext = buildEnhancedAuthContext(state, authMsg);
            EnhancedAuthFinalResponse authResponse = enhancedAuthenticationService.onAuthContinue(sessionCtx, authContext);
            if (!authResponse.success()) {
                updateClientActorState(state, SessionState.DISCONNECTED, sessionCtx);
                MqttConnectReturnCode returnCode = switch (authResponse.enhancedAuthFailureReason()) {
                    case AUTH_METHOD_MISMATCH -> CONNECTION_REFUSED_BAD_AUTHENTICATION_METHOD;
                    case CLIENT_FINAL_MESSAGE_EVALUATION_ERROR -> CONNECTION_REFUSED_NOT_AUTHORIZED_5;
                    default -> CONNECTION_REFUSED_UNSPECIFIED_ERROR;
                };
                persistClientUnauthorized(state, sessionCtx, authResponse.enhancedAuthFailureReason());
                sendConnectionRefusedMsgAndCloseChannel(sessionCtx, returnCode);
                return;
            }
            removeClientUnauthorized(state);
            finishEnhancedSessionAuth(state.getClientId(), sessionCtx, authResponse);
            updateClientActorState(state, SessionState.INITIALIZED, sessionCtx);
            clientMqttActorManager.connect(state.getClientId(),
                    NettyMqttConverter.createMqttConnectMsg(sessionCtx.getSessionId(), sessionCtx.getConnectMsgFromEnhancedAuth()));
            sessionCtx.clearScramServer();
            sessionCtx.clearConnectMsg();
            return;
        }

        if (log.isDebugEnabled()) {
            log.debug("[{}] Session was in {} state while Actor received enhanced auth continue message, prev sessionId - {}, new sessionId - {}.",
                    state.getClientId(), state.getCurrentSessionState(), state.getCurrentSessionId(), sessionCtx.getSessionId());
        }
        updateClientActorState(state, SessionState.DISCONNECTED, sessionCtx);
        sendConnectionRefusedNotAuthorizedMsgAndCloseChannel(sessionCtx);
        persistClientUnauthorized(state, sessionCtx, EnhancedAuthFailureReason.INVALID_CLIENT_STATE_FOR_AUTH_PACKET);
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
        EnhancedAuthContinueResponse authResponse = enhancedAuthenticationService.onReAuth(sessionCtx, authContext);
        if (!authResponse.success()) {
            clientMqttActorManager.disconnect(state.getClientId(), new MqttDisconnectMsg(sessionCtx.getSessionId(),
                    new DisconnectReason(DisconnectReasonType.NOT_AUTHORIZED, authResponse.enhancedAuthFailureReason().getReasonLog())));
            return;
        }

        sendAuthChallengeToClient(sessionCtx, authContext.getAuthMethod(),
                authResponse.response(), MqttReasonCodes.Auth.CONTINUE_AUTHENTICATION);
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

    private void finishEnhancedSessionAuth(String clientId, ClientSessionCtx sessionCtx, EnhancedAuthFinalResponse authResponse) {
        List<AuthRulePatterns> authRulePatterns = authResponse.authRulePatterns();
        if (!CollectionUtils.isEmpty(authRulePatterns)) {
            logAuthRules(clientId, authRulePatterns);
            sessionCtx.setAuthRulePatterns(authRulePatterns);
        }
        sessionCtx.setClientType(authResponse.clientType());
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
            return AuthResponse.failure(e.getMessage());
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

    private void sendAuthChallengeToClient(ClientSessionCtx ctx, String authMethod, byte[] response, MqttReasonCodes.Auth authReasonCode) {
        var properties = new MqttProperties();
        var methodProperty = new MqttProperties.StringProperty(BrokerConstants.AUTHENTICATION_METHOD_PROP_ID, authMethod);
        var dataProperty = new MqttProperties.BinaryProperty(BrokerConstants.AUTHENTICATION_DATA_PROP_ID, response);
        properties.add(methodProperty);
        properties.add(dataProperty);
        MqttMessage message = MqttMessageBuilders.auth()
                .properties(properties)
                .reasonCode(authReasonCode.byteValue())
                .build();
        ctx.getChannel().writeAndFlush(message);
    }


    private MqttDisconnectMsg newDisconnectMsg(UUID sessionId, DisconnectReason reason) {
        return new MqttDisconnectMsg(sessionId, reason);
    }

    private void persistClientUnauthorized(ClientActorState state, SessionInitMsg sessionInitMsg, AuthResponse authResponse) {
        UnauthorizedClient unauthorizedClient = UnauthorizedClient.builder()
                .clientId(state.getClientId())
                .ipAddress(BytesUtil.toHostAddress(sessionInitMsg.getClientSessionCtx().getAddressBytes()))
                .ts(System.currentTimeMillis())
                .username(sessionInitMsg.getUsername())
                .passwordProvided(sessionInitMsg.getPasswordBytes() != null)
                .tlsUsed(sessionInitMsg.getClientSessionCtx().getSslHandler() != null)
                .reason(authResponse.getReason())
                .build();
        DonAsynchron.withCallback(unauthorizedClientService.save(unauthorizedClient),
                v -> log.debug("[{}] Unauthorized Client saved successfully! {}", state.getClientId(), unauthorizedClient),
                throwable -> log.warn("[{}] Failed to persist unauthorized client! {}", state.getClientId(), unauthorizedClient, throwable));
    }

    private void persistClientUnauthorized(ClientActorState state, ClientSessionCtx sessionCtx, EnhancedAuthFailureReason reason) {
        UnauthorizedClient unauthorizedClient = UnauthorizedClient.builder()
                .clientId(state.getClientId())
                .ipAddress(BytesUtil.toHostAddress(sessionCtx.getAddressBytes()))
                .ts(System.currentTimeMillis())
                .passwordProvided(true)
                .tlsUsed(sessionCtx.getSslHandler() != null)
                .reason(reason.getReasonLog())
                .build();
        DonAsynchron.withCallback(unauthorizedClientService.save(unauthorizedClient),
                v -> log.debug("[{}] Unauthorized Client saved successfully! {}", state.getClientId(), unauthorizedClient),
                throwable -> log.warn("[{}] Failed to persist unauthorized client! {}", state.getClientId(), unauthorizedClient, throwable));
    }

    private void removeClientUnauthorized(ClientActorState state) {
        UnauthorizedClient unauthorizedClient = UnauthorizedClient.builder()
                .clientId(state.getClientId())
                .build();
        DonAsynchron.withCallback(unauthorizedClientService.remove(unauthorizedClient),
                v -> log.debug("[{}] Unauthorized Client removed successfully!", state.getClientId()),
                throwable -> log.warn("[{}] Failed to removed unauthorized client!", state.getClientId(), throwable));
    }
}
