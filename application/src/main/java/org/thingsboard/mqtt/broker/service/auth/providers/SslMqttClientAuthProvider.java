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
package org.thingsboard.mqtt.broker.service.auth.providers;

import io.netty.handler.ssl.SslHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.client.credentials.SslMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.dao.util.protocol.ProtocolUtil;
import org.thingsboard.mqtt.broker.exception.AuthenticationException;
import org.thingsboard.mqtt.broker.service.auth.AuthorizationRuleService;
import org.thingsboard.mqtt.broker.service.security.authorization.AuthorizationRule;
import org.thingsboard.mqtt.broker.util.SslUtil;

import javax.net.ssl.SSLPeerUnverifiedException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SslMqttClientAuthProvider implements MqttClientAuthProvider {

    private final MqttClientCredentialsService clientCredentialsService;
    private final AuthorizationRuleService authorizationRuleService;

    @Override
    public AuthResponse authorize(AuthContext authContext) throws AuthenticationException {
        if (authContext.getSslHandler() == null) {
            return new AuthResponse(false, null, null);
        }
        log.trace("[{}] Authenticating client with SSL credentials", authContext.getClientId());
        MqttClientCredentials sslCredentials = authWithSSLCredentials(authContext.getClientId(), authContext.getSslHandler());
        if (sslCredentials == null) {
            return new AuthResponse(false, null, null);
        }
        log.trace("[{}] Successfully authenticated with SSL credentials", authContext.getClientId());
        String clientCommonName = getClientCertificateCommonName(authContext.getSslHandler());
        SslMqttCredentials credentials = JacksonUtil.fromString(sslCredentials.getCredentialsValue(), SslMqttCredentials.class);
        List<AuthorizationRule> authorizationRules = authorizationRuleService.parseSslAuthorizationRule(credentials, clientCommonName);
        return new AuthResponse(true, sslCredentials.getClientType(), authorizationRules);
    }

    private MqttClientCredentials authWithSSLCredentials(String clientId, SslHandler sslHandler) throws AuthenticationException {
        X509Certificate[] certificates;
        try {
            certificates = (X509Certificate[]) sslHandler.engine().getSession().getPeerCertificates();
        } catch (SSLPeerUnverifiedException e) {
            log.debug("Client SSL certification is disabled.");
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
            log.trace("[{}] Trying to authorize client with common name - {}.", clientId, commonName);
            String sslCredentialsId = ProtocolUtil.sslCredentialsId(commonName);
            List<MqttClientCredentials> matchingCredentials = clientCredentialsService.findMatchingCredentials(Collections.singletonList(sslCredentialsId));
            if (!matchingCredentials.isEmpty()) {
                return matchingCredentials.get(0);
            }
        }
        return null;
    }

    private String getClientCertificateCommonName(SslHandler sslHandler) throws AuthenticationException {
        X509Certificate[] certificates;
        try {
            certificates = (X509Certificate[]) sslHandler.engine().getSession().getPeerCertificates();
            return SslUtil.parseCommonName(certificates[0]);
        } catch (Exception e) {
            log.error("Failed to get client's certificate common name. Exception - {}, reason - {}.", e.getClass().getSimpleName(), e.getMessage());
            throw new AuthenticationException("Failed to get client's certificate common name.", e);
        }

    }
}
