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
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
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
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventService;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionReader;
import org.thingsboard.mqtt.broker.service.mqtt.keepalive.KeepAliveService;
import org.thingsboard.mqtt.broker.service.security.authorization.AuthorizationRule;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;

import javax.annotation.PreDestroy;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED;
import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConnectServiceImpl implements ConnectService {
    private final ExecutorService connectHandlerExecutor = Executors.newCachedThreadPool(ThingsBoardThreadFactory.forName("connect-handler-executor"));

    private final ClientMqttActorManager clientMqttActorManager;
    private final MqttMessageGenerator mqttMessageGenerator;
    private final AuthenticationService authenticationService;
    private final AuthorizationRuleService authorizationRuleService;
    private final MqttClientService mqttClientService;
    private final ClientSessionEventService clientSessionEventService;
    private final ClientSessionReader clientSessionReader;
    private final KeepAliveService keepAliveService;
    private final ServiceInfoProvider serviceInfoProvider;

    @Override
    public void startConnection(String clientId, ClientSessionCtx sessionCtx, MqttConnectMessage msg) throws MqttException {
        UUID sessionId = sessionCtx.getSessionId();

        log.trace("[{}][{}] Processing connect msg.", clientId, sessionId);

        validate(sessionCtx, msg);

        // TODO: move auth to INIT phase (reject client before it disconnects existing session)
        authenticateClient(sessionCtx, msg, clientId);

        sessionCtx.setSessionInfo(getSessionInfo(msg, sessionId, clientId));

        keepAliveService.registerSession(sessionId, msg.variableHeader().keepAliveTimeSeconds(),
                () -> clientMqttActorManager.disconnect(clientId, sessionId, new DisconnectReason(DisconnectReasonType.ON_ERROR, "Client was inactive too long")));

        ClientSession prevSession = clientSessionReader.getClientSession(clientId);
        boolean isPrevSessionPersistent = prevSession != null && prevSession.getSessionInfo().isPersistent();

        ListenableFuture<Boolean> connectFuture = clientSessionEventService.connect(sessionCtx.getSessionInfo());
        Futures.addCallback(connectFuture, new FutureCallback<>() {
            @Override
            public void onSuccess(@Nullable Boolean successfulConnection) {
                onConnectFinish(msg.variableHeader().isWillFlag() ? MqttConverter.convertLastWillToPublishMsg(msg) : null,
                        sessionCtx, successfulConnection, isPrevSessionPersistent);
            }

            @Override
            public void onFailure(Throwable t) {
                onConnectFailure(sessionCtx, t);
            }
        }, connectHandlerExecutor);
    }

    private void onConnectFailure(ClientSessionCtx sessionCtx, Throwable t) {
        log.debug("[{}][{}] Couldn't connect the client. Exception - {}, reason - {}.", sessionCtx.getClientId(), sessionCtx.getSessionId(), t.getClass().getSimpleName(), t.getMessage());
        log.trace("Detailed error: ", t);
        try {
            sessionCtx.getChannel().writeAndFlush(mqttMessageGenerator.createMqttConnAckMsg(CONNECTION_REFUSED_IDENTIFIER_REJECTED, false));
        } finally {
            clientMqttActorManager.disconnect(sessionCtx.getClientId(), sessionCtx.getSessionId(),
                    new DisconnectReason(DisconnectReasonType.ON_ERROR, "Connection failed. Message - " + t.getMessage()));
        }
    }

    private void onConnectFinish(PublishMsg lastWillMsg, ClientSessionCtx ctx, Boolean wasConnectionSuccessful, boolean isPrevSessionPersistent) {
        UUID sessionId = ctx.getSessionId();
        String clientId = ctx.getClientId();

        if (wasConnectionSuccessful) {
            clientMqttActorManager.processConnectionAccepted(clientId, sessionId, isPrevSessionPersistent, lastWillMsg);
        } else {
            log.debug("[{}][{}] Client wasn't connected.", clientId, sessionId);
            try {
                ctx.getChannel().writeAndFlush(mqttMessageGenerator.createMqttConnAckMsg(CONNECTION_REFUSED_IDENTIFIER_REJECTED, false));
            } catch (Exception e) {
                log.trace("[{}][{}] Failed to send CONN_ACK response.", clientId, sessionId);
            } finally {
                clientMqttActorManager.disconnect(clientId, sessionId,
                        new DisconnectReason(DisconnectReasonType.ON_ERROR, "Client wasn't allowed to connect"));
            }
        }
    }

    private void authenticateClient(ClientSessionCtx ctx, MqttConnectMessage msg, String clientId) {
        try {
            // TODO: make it with Plugin architecture (to be able to use LDAP, OAuth etc)
            MqttClientCredentials clientCredentials = authenticationService.authenticate(clientId, msg.payload().userName(), msg.payload().passwordInBytes(), ctx.getSslHandler());
            if (clientCredentials != null) {
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
        } catch (AuthenticationException e) {
            log.debug("[{}] Authentication failed. Reason - {}.", clientId, e.getMessage());
            ctx.getChannel().writeAndFlush(mqttMessageGenerator.createMqttConnAckMsg(CONNECTION_REFUSED_NOT_AUTHORIZED, false));
            throw new MqttException("Authentication failed . Reason - " + e.getMessage());
        }
    }

    private SessionInfo getSessionInfo(MqttConnectMessage msg, UUID sessionId, String clientId) {
        ClientInfo clientInfo = mqttClientService.getMqttClient(clientId)
                .map(mqttClient -> new ClientInfo(mqttClient.getClientId(), mqttClient.getType()))
                .orElse(new ClientInfo(clientId, ClientType.DEVICE));
        boolean isPersistentSession = !msg.variableHeader().isCleanSession();
        return SessionInfo.builder()
                .serviceId(serviceInfoProvider.getServiceId())
                .sessionId(sessionId).persistent(isPersistentSession).clientInfo(clientInfo).build();
    }

    private void validate(ClientSessionCtx ctx, MqttConnectMessage msg) {
        if (!msg.variableHeader().isCleanSession() && StringUtils.isEmpty(msg.payload().clientIdentifier())) {
            ctx.getChannel().writeAndFlush(mqttMessageGenerator.createMqttConnAckMsg(CONNECTION_REFUSED_IDENTIFIER_REJECTED, false));
            throw new MqttException("Client identifier is empty and 'clean session' flag is set to 'false'!");
        }
    }

    @PreDestroy
    public void destroy() {
        log.debug("Shutting down executors");
        connectHandlerExecutor.shutdownNow();
    }
}
