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
package org.thingsboard.mqtt.broker.service.mqtt.client.connect;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thingsboard.mqtt.broker.adaptor.MqttConverter;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.dao.client.MqttClientService;
import org.thingsboard.mqtt.broker.exception.AuthenticationException;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.service.auth.AuthenticationService;
import org.thingsboard.mqtt.broker.service.auth.AuthorizationRuleService;
import org.thingsboard.mqtt.broker.service.mqtt.ClientSession;
import org.thingsboard.mqtt.broker.service.mqtt.client.ClientSessionCtxService;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventService;
import org.thingsboard.mqtt.broker.service.mqtt.client.ClientSessionService;
import org.thingsboard.mqtt.broker.service.mqtt.client.disconnect.DisconnectService;
import org.thingsboard.mqtt.broker.service.mqtt.keepalive.KeepAliveService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.PersistenceSessionClearer;
import org.thingsboard.mqtt.broker.service.mqtt.will.LastWillService;
import org.thingsboard.mqtt.broker.service.security.authorization.AuthorizationRule;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.SessionState;

import javax.annotation.PreDestroy;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_ACCEPTED;
import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED;
import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConnectServiceImpl implements ConnectService {
    private final ExecutorService connectHandlerExecutor = Executors.newCachedThreadPool(ThingsBoardThreadFactory.forName("connect-handler-executor"));

    private final MqttMessageGenerator mqttMessageGenerator;
    private final LastWillService lastWillService;
    private final ClientSessionCtxService clientSessionCtxService;
    private final AuthenticationService authenticationService;
    private final AuthorizationRuleService authorizationRuleService;
    private final MqttClientService mqttClientService;
    private final ClientSessionEventService clientSessionEventService;
    private final ClientSessionService clientSessionService;
    private final PersistenceSessionClearer persistenceSessionClearer;
    private final KeepAliveService keepAliveService;
    private final DisconnectService disconnectService;
    private final PostConnectService postConnectService;

    @Override
    public void connect(ClientSessionCtx ctx, MqttConnectMessage msg) throws MqttException {
        UUID sessionId = ctx.getSessionId();
        log.debug("[{}] Processing connect msg for client: {}!", sessionId, msg.payload().clientIdentifier());

        validate(ctx, msg);

        String clientId = getClientId(msg);

        authenticateClient(ctx, msg, clientId);

        ctx.updateSessionState(SessionState.CONNECTING);
        ctx.setSessionInfo(getSessionInfo(msg, sessionId, clientId));

        keepAliveService.registerSession(sessionId, msg.variableHeader().keepAliveTimeSeconds(), () -> disconnectService.disconnect(ctx, DisconnectReason.ON_ERROR));

        ClientSession prevSession = clientSessionService.getClientSession(clientId);
        boolean isPrevSessionPersistent = prevSession != null && prevSession.getSessionInfo().isPersistent();

        ListenableFuture<Boolean> connectFuture = clientSessionEventService.connect(ctx.getSessionInfo());
        Futures.addCallback(connectFuture, new FutureCallback<>() {
            @Override
            public void onSuccess(@Nullable Boolean successfulConnection) {
                onConnectFinish(msg, ctx, successfulConnection, isPrevSessionPersistent);
            }

            @Override
            public void onFailure(Throwable t) {
                onConnectFailure(ctx, t);
            }
        }, connectHandlerExecutor);
    }

    private void onConnectFailure(ClientSessionCtx ctx, Throwable t) {
        log.debug("[{}][{}] Couldn't connect the client. Reason - {}.", ctx.getClientId(), ctx.getSessionId(), t.getMessage());
        log.trace("Detailed error: ", t);
        try {
            ctx.getChannel().writeAndFlush(mqttMessageGenerator.createMqttConnAckMsg(CONNECTION_REFUSED_IDENTIFIER_REJECTED, false));
        } finally {
            disconnectService.disconnect(ctx, DisconnectReason.ON_ERROR);
        }
    }

    private void onConnectFinish(MqttConnectMessage msg, ClientSessionCtx ctx, Boolean wasConnectionSuccessful, boolean isPrevSessionPersistent) {
        UUID sessionId = ctx.getSessionId();
        SessionInfo sessionInfo = ctx.getSessionInfo();
        String clientId = ctx.getClientId();
        if (!wasConnectionSuccessful) {
            log.debug("[{}][{}] Client wasn't connected.", clientId, sessionId);
            try {
                ctx.getChannel().writeAndFlush(mqttMessageGenerator.createMqttConnAckMsg(CONNECTION_REFUSED_IDENTIFIER_REJECTED, false));
            } catch (Exception e) {
                log.trace("[{}][{}] Failed to send CONN_ACK response.", clientId, sessionId);
            } finally {
                disconnectService.disconnect(ctx, DisconnectReason.ON_ERROR);
            }
            return;
        }
        try {
            clientSessionCtxService.registerSession(ctx);
            if (msg.variableHeader().isWillFlag()) {
                lastWillService.saveLastWillMsg(sessionInfo, MqttConverter.convertLastWillToPublishMsg(msg));
            }

            boolean isCurrentSessionPersistent = sessionInfo.isPersistent();
            if (isPrevSessionPersistent && !isCurrentSessionPersistent) {
                persistenceSessionClearer.clearPersistedSession(sessionInfo.getClientInfo());
            }
            ctx.getChannel().writeAndFlush(mqttMessageGenerator.createMqttConnAckMsg(CONNECTION_ACCEPTED, isPrevSessionPersistent && isCurrentSessionPersistent));
            ctx.updateSessionState(SessionState.CONNECTED);
            log.info("[{}] [{}] Client connected!", clientId, sessionId);

            postConnectService.process(ctx);
        } catch (Exception e) {
            log.warn("[{}][{}] Failed to finish client connection. Exception - {}, reason - {}.",
                    ctx.getClientId(), ctx.getSessionId(), e.getClass().getSimpleName(), e.getMessage());
            log.trace("Detailed error: ", e);
            disconnectService.disconnect(ctx, DisconnectReason.ON_ERROR);
        }
    }

    private void authenticateClient(ClientSessionCtx ctx, MqttConnectMessage msg, String clientId) {
        try {
            MqttClientCredentials clientCredentials = authenticationService.authenticate(clientId, msg.payload().userName(), msg.payload().passwordInBytes(), ctx.getSslHandler());
            if (clientCredentials != null) {
                AuthorizationRule authorizationRule = null;
                if (clientCredentials.getCredentialsType() == ClientCredentialsType.SSL) {
                    String clientCommonName = authenticationService.getClientCertificateCommonName(ctx.getSslHandler());
                    authorizationRule = authorizationRuleService.parseSslAuthorizationRule(clientCredentials.getCredentialsValue(), clientCommonName);
                } else if (clientCredentials.getCredentialsType() == ClientCredentialsType.MQTT_BASIC) {
                    authorizationRule = authorizationRuleService.parseBasicAuthorizationRule(clientCredentials.getCredentialsValue());
                }
                ctx.setAuthorizationRule(authorizationRule);
            }
        } catch (AuthenticationException e) {
            log.debug("[{}] Authentication failed. Reason - {}.", clientId, e.getMessage());
            ctx.getChannel().writeAndFlush(mqttMessageGenerator.createMqttConnAckMsg(CONNECTION_REFUSED_NOT_AUTHORIZED, false));
            throw new MqttException("Authentication failed for client [" + clientId + "].");
        }
    }

    private SessionInfo getSessionInfo(MqttConnectMessage msg, UUID sessionId, String clientId) {
        ClientInfo clientInfo = mqttClientService.getMqttClient(clientId)
                .map(mqttClient -> new ClientInfo(mqttClient.getClientId(), mqttClient.getType()))
                .orElse(new ClientInfo(clientId, ClientType.DEVICE));
        boolean isPersistentSession = !msg.variableHeader().isCleanSession();
        return SessionInfo.builder().sessionId(sessionId).persistent(isPersistentSession).clientInfo(clientInfo).build();
    }

    private void validate(ClientSessionCtx ctx, MqttConnectMessage msg) {
        if (!msg.variableHeader().isCleanSession() && StringUtils.isEmpty(msg.payload().clientIdentifier())) {
            ctx.getChannel().writeAndFlush(mqttMessageGenerator.createMqttConnAckMsg(CONNECTION_REFUSED_IDENTIFIER_REJECTED, false));
            throw new MqttException("Client identifier is empty and 'clean session' flag is set to 'false'!");
        }
    }

    private String getClientId(MqttConnectMessage msg) {
        String clientId = msg.payload().clientIdentifier();
        if (StringUtils.isEmpty(clientId)) {
            clientId = UUID.randomUUID().toString();
        }
        return clientId;
    }

    @PreDestroy
    public void destroy() {
        log.debug("Shutting down executors");
        connectHandlerExecutor.shutdownNow();
    }
}
