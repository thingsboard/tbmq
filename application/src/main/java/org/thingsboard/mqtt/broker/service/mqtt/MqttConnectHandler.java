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

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.ssl.SslHandler;
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
import org.thingsboard.mqtt.broker.service.mqtt.will.LastWillService;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.SessionInfoCreator;

import javax.net.ssl.SSLPeerUnverifiedException;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_ACCEPTED;
import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD;
import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED;
import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED;

@Service
@RequiredArgsConstructor
@Slf4j
// TODO maybe it's better to create handlers for each session and not for the whole system (this way we could always use SessionContext)
public class MqttConnectHandler {

    @Value("${security.mqtt.enabled}")
    private Boolean mqttSecurityEnabled;

    @Value("${server.mqtt.ssl.enabled}")
    private Boolean sslEnabled;

    private final BCryptPasswordEncoder passwordEncoder;
    private final MqttMessageGenerator mqttMessageGenerator;
    private final MqttClientCredentialsService clientCredentialsService;
    private final LastWillService lastWillService;
    private final ClientManager clientManager;


    public void process(ClientSessionCtx ctx, SslHandler sslHandler, MqttConnectMessage msg) throws MqttException {
        UUID sessionId = ctx.getSessionId();
        log.info("[{}] Processing connect msg for client: {}!", sessionId, msg.payload().clientIdentifier());

        validate(ctx, msg);

        String clientId = getClientId(msg);

        if (mqttSecurityEnabled && !isAuthenticated(msg.payload().userName(), clientId, msg.payload().passwordInBytes())) {
            ctx.getChannel().writeAndFlush(mqttMessageGenerator.createMqttConnAckMsg(CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD));
            throw new MqttException("Authentication failed for client [clientId: "+
                    clientId + ", userName: " + msg.payload().userName() + "].");
        }
        if (sslEnabled) {
            X509Certificate certificate = getCertificate(sslHandler);
            if (certificate != null) {
                processX509CertConnect(ctx, certificate);
            }
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

    private boolean isAuthenticated(String userName, String clientId, byte[] passwordBytes) {
        List<String> credentialIds = new ArrayList<>();
        if (!StringUtils.isEmpty(userName)) {
            credentialIds.add(ProtocolUtil.usernameCredentialsId(userName));
        }
        if (!StringUtils.isEmpty(clientId)) {
            credentialIds.add(ProtocolUtil.clientIdCredentialsId(clientId));
        }
        if (!StringUtils.isEmpty(userName) && !StringUtils.isEmpty(clientId)) {
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

    private void processX509CertConnect(ClientSessionCtx ctx, X509Certificate cert) {
        try {
            cert.checkValidity();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private X509Certificate getCertificate(SslHandler sslHandler) {
        try {
            X509Certificate[] certificates = (X509Certificate[]) sslHandler.engine().getSession().getPeerCertificates();
            if (certificates.length > 0) {
                return certificates[0];
            }
        } catch (SSLPeerUnverifiedException e) {
            log.warn("Failed to get SSL Certificate. Reason - {}.", e.getMessage());
        }
        return null;
    }
}
