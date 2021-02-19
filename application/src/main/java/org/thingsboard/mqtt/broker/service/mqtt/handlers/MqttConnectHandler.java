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
package org.thingsboard.mqtt.broker.service.mqtt.handlers;

import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.ssl.SslHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.dao.client.MqttClientService;
import org.thingsboard.mqtt.broker.exception.AuthenticationException;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.service.auth.AuthenticationService;
import org.thingsboard.mqtt.broker.service.auth.AuthorizationRuleService;
import org.thingsboard.mqtt.broker.service.mqtt.client.ClientSessionManager;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.mqtt.will.LastWillService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.MsgPersistenceManager;
import org.thingsboard.mqtt.broker.service.security.authorization.AuthorizationRule;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.UUID;

import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_ACCEPTED;
import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED;
import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED;

@Service
@RequiredArgsConstructor
@Slf4j
// TODO maybe it's better to create handlers for each session and not for the whole system (this way we could always use SessionContext)
public class MqttConnectHandler {

    private final MqttMessageGenerator mqttMessageGenerator;
    private final LastWillService lastWillService;
    private final ClientSessionManager clientSessionManager;
    private final AuthenticationService authenticationService;
    private final AuthorizationRuleService authorizationRuleService;
    private final MqttClientService mqttClientService;
    private final MsgPersistenceManager msgPersistenceManager;


    public void process(ClientSessionCtx ctx, SslHandler sslHandler, MqttConnectMessage msg) throws MqttException {
        UUID sessionId = ctx.getSessionId();
        log.debug("[{}] Processing connect msg for client: {}!", sessionId, msg.payload().clientIdentifier());

        validate(ctx, msg);

        String clientId = getClientId(msg);

        authenticateClient(ctx, sslHandler, msg, clientId);

        SessionInfo sessionInfo = getSessionInfo(msg, sessionId, clientId);
        boolean isSessionPresent = tryRegisterClient(sessionInfo, ctx);

        processLastWill(ctx.getSessionInfo(), msg);

        ctx.getChannel().writeAndFlush(mqttMessageGenerator.createMqttConnAckMsg(CONNECTION_ACCEPTED, isSessionPresent));
        ctx.setConnected();

        if (sessionInfo.isPersistent()) {
            msgPersistenceManager.processPersistedMessages(ctx);
        }

        log.info("[{}] [{}] Client connected!", clientId, sessionId);
    }

    private void authenticateClient(ClientSessionCtx ctx, SslHandler sslHandler, MqttConnectMessage msg, String clientId) {
        try {
            MqttClientCredentials clientCredentials = authenticationService.authenticate(clientId, msg.payload().userName(), msg.payload().passwordInBytes(), sslHandler);
            if (clientCredentials != null) {
                AuthorizationRule authorizationRule = null;
                if (clientCredentials.getCredentialsType() == ClientCredentialsType.SSL) {
                    String clientCommonName = authenticationService.getClientCertificateCommonName(sslHandler);
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

    private boolean tryRegisterClient(SessionInfo sessionInfo, ClientSessionCtx ctx) {
        try {
            boolean isSessionPresent = clientSessionManager.registerClient(sessionInfo, ctx);
            ctx.setSessionInfo(sessionInfo);
            return isSessionPresent;
        } catch (MqttException e) {
            ctx.getChannel().writeAndFlush(mqttMessageGenerator.createMqttConnAckMsg(CONNECTION_REFUSED_IDENTIFIER_REJECTED, false));
            throw e;
        }
    }

    private void validate(ClientSessionCtx ctx, MqttConnectMessage msg) {
        if (!msg.variableHeader().isCleanSession() && StringUtils.isEmpty(msg.payload().clientIdentifier())) {
            ctx.getChannel().writeAndFlush(mqttMessageGenerator.createMqttConnAckMsg(CONNECTION_REFUSED_IDENTIFIER_REJECTED, false));
            throw new MqttException("Client identifier is empty and 'clean session' flag is set to 'false'!");
        }
    }

    private void processLastWill(SessionInfo sessionInfo, MqttConnectMessage msg) {
        if (msg.variableHeader().isWillFlag()) {

            PublishMsg publishMsg = PublishMsg.builder()
                    .packetId(-1)
                    .topicName(msg.payload().willTopic())
                    .payload(msg.payload().willMessageInBytes())
                    .isRetained(msg.variableHeader().isWillRetain())
                    .qosLevel(msg.variableHeader().willQos())
                    .build();

            lastWillService.saveLastWillMsg(sessionInfo, publishMsg);
        }
    }

    private String getClientId(MqttConnectMessage msg) {
        String clientId = msg.payload().clientIdentifier();
        if (StringUtils.isEmpty(clientId)) {
            clientId = UUID.randomUUID().toString();
        }
        return clientId;
    }
}
