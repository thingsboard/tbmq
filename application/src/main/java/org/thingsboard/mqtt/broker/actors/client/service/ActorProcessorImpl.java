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
import org.thingsboard.mqtt.broker.actors.client.messages.SessionInitMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttDisconnectMsg;
import org.thingsboard.mqtt.broker.actors.client.service.disconnect.DisconnectService;
import org.thingsboard.mqtt.broker.actors.client.state.ClientActorState;
import org.thingsboard.mqtt.broker.actors.client.state.SessionState;
import org.thingsboard.mqtt.broker.common.data.UnauthorizedClient;
import org.thingsboard.mqtt.broker.common.util.DonAsynchron;
import org.thingsboard.mqtt.broker.dao.client.unauthorized.UnauthorizedClientService;
import org.thingsboard.mqtt.broker.exception.AuthenticationException;
import org.thingsboard.mqtt.broker.service.auth.AuthenticationService;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthContext;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthResponse;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.security.authorization.AuthRulePatterns;
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

import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActorProcessorImpl implements ActorProcessor {

    private final DisconnectService disconnectService;
    private final AuthenticationService authenticationService;
    private final MqttMessageGenerator mqttMessageGenerator;
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
            sendConnectionRefusedMsgAndCloseChannel(sessionCtx);
            return;
        }

        removeClientUnauthorized(state);
        finishSessionAuth(state.getClientId(), sessionCtx, authResponse);

        if (state.getCurrentSessionState() != SessionState.DISCONNECTED) {
            disconnectCurrentSession(state, sessionCtx);
        }

        updateClientActorState(state, sessionCtx);
    }

    private void tryDisconnectSameSession(ClientActorState state, ClientSessionCtx sessionCtx) {
        log.warn("[{}][{}] Trying to initialize the same session.", state.getClientId(), sessionCtx.getSessionId());
        if (state.getCurrentSessionState() != SessionState.DISCONNECTED) {
            state.updateSessionState(SessionState.DISCONNECTING);
            DisconnectReason reason = new DisconnectReason(DisconnectReasonType.ON_CONFLICTING_SESSIONS, "Trying to init the same active session");
            disconnect(state, newDisconnectMsg(state.getCurrentSessionId(), reason));
        }
    }

    void sendConnectionRefusedMsgAndCloseChannel(ClientSessionCtx sessionCtx) {
        MqttConnectReturnCode code = MqttReasonCodeResolver.connectionRefusedNotAuthorized(sessionCtx);
        MqttConnAckMessage msg = mqttMessageGenerator.createMqttConnAckMsg(code);
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

    void updateClientActorState(ClientActorState state, ClientSessionCtx sessionCtx) {
        state.updateSessionState(SessionState.INITIALIZED);
        state.setClientSessionCtx(sessionCtx);
        state.clearStopActorCommandId();
    }

    private void finishSessionAuth(String clientId, ClientSessionCtx sessionCtx, AuthResponse authResponse) {
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
            return AuthResponse.builder().success(false).reason("something else bad").build();
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

    private void removeClientUnauthorized(ClientActorState state) {
        UnauthorizedClient unauthorizedClient = UnauthorizedClient.builder()
                .clientId(state.getClientId())
                .build();
        DonAsynchron.withCallback(unauthorizedClientService.remove(unauthorizedClient),
                v -> log.debug("[{}] Unauthorized Client removed successfully!", state.getClientId()),
                throwable -> log.warn("[{}] Failed to removed unauthorized client!", state.getClientId(), throwable));
    }
}
