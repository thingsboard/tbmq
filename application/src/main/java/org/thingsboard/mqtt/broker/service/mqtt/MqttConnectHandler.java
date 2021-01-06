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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.data.client.credentials.BasicMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.dao.util.mapping.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.util.protocol.ProtocolUtil;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.SessionInfoCreator;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_ACCEPTED;
import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD;

@Service
@RequiredArgsConstructor
@Slf4j
public class MqttConnectHandler {

    @Value("${security.mqtt.enabled}")
    private Boolean mqttSecurityEnabled;

    private final BCryptPasswordEncoder passwordEncoder;
    private final MqttMessageGenerator mqttMessageGenerator;
    private final MqttClientCredentialsService clientCredentialsService;

    public void process(ClientSessionCtx ctx, MqttConnectMessage msg) throws MqttException {
        UUID sessionId = ctx.getSessionId();
        log.info("[{}] Processing connect msg for client: {}!", sessionId, msg.payload().clientIdentifier());

        String clientId = msg.payload().clientIdentifier();
        if (StringUtils.isEmpty(clientId)) {
            clientId = UUID.randomUUID().toString();
        }

        if (mqttSecurityEnabled && !isAuthenticated(msg.payload().userName(), clientId, msg.payload().passwordInBytes())) {
            ctx.getChannel().writeAndFlush(mqttMessageGenerator.createMqttConnAckMsg(CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD));
            throw new MqttException("Authentication failed for client [clientId: "+
                    clientId + ", userName: " + msg.payload().userName() + "].");
        }

        ctx.setConnected();
        SessionInfo sessionInfo = SessionInfoCreator.create(sessionId, clientId, !msg.variableHeader().isCleanSession());
        ctx.setSessionInfo(sessionInfo);
        ctx.setSessionInfoProto(SessionInfoCreator.createProto(sessionInfo));
        ctx.getChannel().writeAndFlush(mqttMessageGenerator.createMqttConnAckMsg(CONNECTION_ACCEPTED));
        log.info("[{}] Client connected!", sessionId);
    }

    private boolean isAuthenticated(String userName, String clientId, byte[] passwordBytes) {
        List<String> credentialIds = new ArrayList<>();
        if (!StringUtils.isEmpty(userName)) {
            credentialIds.add(ProtocolUtil.usernameCredentialsId(userName));
        }
        if (!StringUtils.isEmpty(clientId)) {
            credentialIds.add(ProtocolUtil.clientIdCredentialsId(userName));
        }
        if (!StringUtils.isEmpty(clientId) && !StringUtils.isEmpty(userName)) {
            credentialIds.add(ProtocolUtil.mixedCredentialsId(userName, clientId));
        }
        List<MqttClientCredentials> matchingCredentials = clientCredentialsService.findMatchingCredentials(credentialIds);
        String password = passwordBytes != null ?
                new String(passwordBytes, StandardCharsets.UTF_8) : null;
        return matchingCredentials.stream()
                .map(MqttClientCredentials::getCredentialsValue)
                .map(credentialsValue -> JacksonUtil.fromString(credentialsValue, BasicMqttCredentials.class))
                .filter(Objects::nonNull)
                .anyMatch(basicMqttCredentials -> basicMqttCredentials.getPassword() == null
                        || (password != null && passwordEncoder.matches(password, basicMqttCredentials.getPassword())));
    }
}
