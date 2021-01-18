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
package org.thingsboard.mqtt.broker.service.mqtt;

import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.ssl.SslHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.data.client.credentials.SslMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.dao.util.mapping.JacksonUtil;
import org.thingsboard.mqtt.broker.exception.AuthenticationException;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.service.auth.AuthService;
import org.thingsboard.mqtt.broker.service.mqtt.will.LastWillService;
import org.thingsboard.mqtt.broker.service.security.authorization.AuthorizationRule;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.SessionInfoCreator;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private final ClientManager clientManager;
    private final AuthService authService;


    public void process(ClientSessionCtx ctx, SslHandler sslHandler, MqttConnectMessage msg) throws MqttException {
        UUID sessionId = ctx.getSessionId();
        log.info("[{}] Processing connect msg for client: {}!", sessionId, msg.payload().clientIdentifier());

        validate(ctx, msg);

        String clientId = getClientId(msg);

        try {
            MqttClientCredentials clientCredentials = authService.authenticate(clientId, msg.payload().userName(), msg.payload().passwordInBytes(), sslHandler);
            if (clientCredentials != null && clientCredentials.getCredentialsType() == ClientCredentialsType.SSL) {
                SslMqttCredentials sslMqttCredentials = JacksonUtil.fromString(clientCredentials.getCredentialsValue(), SslMqttCredentials.class);
                if (sslMqttCredentials == null) {
                    throw new AuthenticationException("Cannot parse SslMqttCredentials.");
                }
                Pattern pattern = Pattern.compile(sslMqttCredentials.getPatternRegEx());
                Matcher commonNameMatcher = pattern.matcher(sslMqttCredentials.getCommonName());
                if (!commonNameMatcher.find()) {
                    throw new AuthenticationException("Cannot find string for pattern in common name [" + sslMqttCredentials.getCommonName() + "]");
                }
                String mappingKey = commonNameMatcher.group();
                String authorizationRulePatternRegEx = sslMqttCredentials.getAuthorizationRulesMapping().get(mappingKey);
                if (authorizationRulePatternRegEx == null) {
                    throw new AuthenticationException("Cannot find authorization rule pattern for key [" + mappingKey + "]");
                }

                AuthorizationRule authorizationRule = new AuthorizationRule(Pattern.compile(authorizationRulePatternRegEx));
                ctx.setAuthorizationRule(authorizationRule);
            }
        } catch (AuthenticationException e) {
            log.trace("[{}] Authentication failed. Reason - {}.", clientId, e.getMessage());
            ctx.getChannel().writeAndFlush(mqttMessageGenerator.createMqttConnAckMsg(CONNECTION_REFUSED_NOT_AUTHORIZED));
            throw new MqttException("Authentication failed for client [" + clientId + "].");
        }

        registerClient(ctx, clientId);

        ctx.setConnected();
        SessionInfo sessionInfo = SessionInfoCreator.create(sessionId, clientId, !msg.variableHeader().isCleanSession());
        ctx.setSessionInfo(sessionInfo);

        processLastWill(sessionInfo, msg);

        ctx.getChannel().writeAndFlush(mqttMessageGenerator.createMqttConnAckMsg(CONNECTION_ACCEPTED));
        log.info("[{}] Client connected!", sessionId);
    }

    private void registerClient(ClientSessionCtx ctx, String clientId) {
        boolean successfullyRegistered = clientManager.registerClient(clientId);
        if (!successfullyRegistered) {
            ctx.getChannel().writeAndFlush(mqttMessageGenerator.createMqttConnAckMsg(CONNECTION_REFUSED_IDENTIFIER_REJECTED));
            throw new MqttException("Client identifier is already registered in the system!");
        }
    }

    private void validate(ClientSessionCtx ctx, MqttConnectMessage msg) {
        if (!msg.variableHeader().isCleanSession() && StringUtils.isEmpty(msg.payload().clientIdentifier())) {
            ctx.getChannel().writeAndFlush(mqttMessageGenerator.createMqttConnAckMsg(CONNECTION_REFUSED_IDENTIFIER_REJECTED));
            throw new MqttException("Client identifier is empty and 'clean session' flag is set to 'false'!");
        }
    }

    private void processLastWill(SessionInfo sessionInfo, MqttConnectMessage msg) {
        if (msg.variableHeader().isWillFlag()) {

            PublishMsg publishMsg = PublishMsg.builder()
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
