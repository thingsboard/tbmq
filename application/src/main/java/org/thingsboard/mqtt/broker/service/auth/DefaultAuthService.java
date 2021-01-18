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
package org.thingsboard.mqtt.broker.service.auth;

import io.netty.handler.ssl.SslHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thingsboard.mqtt.broker.common.data.client.credentials.BasicMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.dao.util.mapping.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.util.protocol.ProtocolUtil;
import org.thingsboard.mqtt.broker.exception.AuthenticationException;
import org.thingsboard.mqtt.broker.util.SslUtil;

import javax.net.ssl.SSLPeerUnverifiedException;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultAuthService implements AuthService {

    @Value("${security.mqtt.basic.enabled}")
    private Boolean basicSecurityEnabled;
    @Value("${security.mqtt.ssl.enabled}")
    private Boolean sslEnabled;

    private final MqttClientCredentialsService clientCredentialsService;
    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    public MqttClientCredentials authenticate(String clientId, String username, byte[] passwordBytes, SslHandler sslHandler) throws AuthenticationException {
        if (!sslEnabled && !basicSecurityEnabled) {
            return null;
        }
        log.trace("[{}] Authorizing client", clientId);
        if (basicSecurityEnabled) {
            MqttClientCredentials basicCredentials = authWithBasicCredentials(clientId, username, passwordBytes);
            if (basicCredentials != null) {
                log.trace("[{}] Authenticated with username {}", clientId, username);
                return basicCredentials;
            }
        }
        // TODO: decide in what order and with what priority to authenticate clients with both BASIC and SSL auth
        if (sslEnabled) {
            return authWithSSLCredentials(sslHandler);
        }
        throw new AuthenticationException("Could not find basic or ssl credentials!");
    }

    private MqttClientCredentials authWithSSLCredentials(SslHandler sslHandler) throws AuthenticationException {
        X509Certificate[] certificates;
        try {
            certificates = (X509Certificate[]) sslHandler.engine().getSession().getPeerCertificates();
        } catch (SSLPeerUnverifiedException e) {
            log.warn("Failed to get SSL Certificate. Reason - {}.", e.getMessage());
            return null;
        }
        if (certificates.length == 0) {
            log.warn("There are no certificates in the chain.");
            return null;
        }
        for (X509Certificate certificate : certificates) {
            String commonName = null;
            try {
                commonName = SslUtil.parseCommonName(certificate);
            } catch (CertificateEncodingException e) {
                throw new AuthenticationException("Couldn't get Common Name from certificate.", e);
            }
            String sslCredentialsId = ProtocolUtil.sslCredentialsId(commonName);
            List<MqttClientCredentials> matchingCredentials = clientCredentialsService.findMatchingCredentials(Collections.singletonList(sslCredentialsId));
            if (!matchingCredentials.isEmpty()) {
                return matchingCredentials.get(0);
            }
        }
        return null;
    }

    private MqttClientCredentials authWithBasicCredentials(String clientId, String username, byte[] passwordBytes) {
        List<String> credentialIds = new ArrayList<>();
        if (!StringUtils.isEmpty(username)) {
            credentialIds.add(ProtocolUtil.usernameCredentialsId(username));
        }
        if (!StringUtils.isEmpty(clientId)) {
            credentialIds.add(ProtocolUtil.clientIdCredentialsId(clientId));
        }
        if (!StringUtils.isEmpty(username) && !StringUtils.isEmpty(clientId)) {
            credentialIds.add(ProtocolUtil.mixedCredentialsId(username, clientId));
        }
        List<MqttClientCredentials> matchingCredentials = clientCredentialsService.findMatchingCredentials(credentialIds);
        String password = passwordBytes != null ?
                new String(passwordBytes, StandardCharsets.UTF_8) : null;

        for (MqttClientCredentials matchingCredential : matchingCredentials) {
            BasicMqttCredentials basicMqttCredentials = JacksonUtil.fromString(matchingCredential.getCredentialsValue(), BasicMqttCredentials.class);
            if (basicMqttCredentials != null && isMatchingPassword(password, basicMqttCredentials)) {
                return matchingCredential;
            }
        }
        return null;
    }

    private boolean isMatchingPassword(String password, BasicMqttCredentials basicMqttCredentials) {
        return basicMqttCredentials.getPassword() == null
                || (password != null && passwordEncoder.matches(password, basicMqttCredentials.getPassword()));
    }
}
