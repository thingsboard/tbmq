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
package org.thingsboard.mqtt.broker.service.auth.providers;

import io.netty.handler.ssl.SslHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.cache.CacheConstants;
import org.thingsboard.mqtt.broker.common.data.client.credentials.ClientTypeSslMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.client.credentials.SslMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.dao.client.credentials.SslCredentialsCacheValue;
import org.thingsboard.mqtt.broker.dao.util.protocol.ProtocolUtil;
import org.thingsboard.mqtt.broker.exception.AuthenticationException;
import org.thingsboard.mqtt.broker.service.auth.AuthorizationRuleService;
import org.thingsboard.mqtt.broker.service.security.authorization.AuthRulePatterns;
import org.thingsboard.mqtt.broker.util.SslUtil;

import javax.net.ssl.SSLPeerUnverifiedException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class SslMqttClientAuthProvider implements MqttClientAuthProvider {

    private final MqttClientCredentialsService clientCredentialsService;
    private final AuthorizationRuleService authorizationRuleService;
    private final CacheManager cacheManager;

    @Value("${security.mqtt.ssl.skip_validity_check_for_client_cert:false}")
    private boolean skipValidityCheckForClientCert;

    @Override
    public AuthResponse authenticate(AuthContext authContext) throws AuthenticationException {
        if (authContext.getSslHandler() == null) {
            log.error("[{}] Could not authenticate client with SSL credentials since SSL listener is not enabled!", authContext);
            return new AuthResponse(false, null, null);
        }
        if (log.isTraceEnabled()) {
            log.trace("[{}] Authenticating client with SSL credentials", authContext.getClientId());
        }
        ClientTypeSslMqttCredentials clientTypeSslMqttCredentials = authWithSSLCredentials(authContext.getClientId(), authContext.getSslHandler());
        if (clientTypeSslMqttCredentials == null) {
            log.error("Failed to authenticate client with SSL credentials! No SSL credentials were found!");
            return new AuthResponse(false, null, null);
        }
        if (log.isTraceEnabled()) {
            log.trace("[{}] Successfully authenticated with SSL credentials", authContext.getClientId());
        }
        String clientCommonName = getClientCertificateCommonName(authContext.getSslHandler());
        List<AuthRulePatterns> authRulePatterns = authorizationRuleService.parseSslAuthorizationRule(clientTypeSslMqttCredentials.getSslMqttCredentials(), clientCommonName);
        return new AuthResponse(true, clientTypeSslMqttCredentials.getType(), authRulePatterns);
    }

    private ClientTypeSslMqttCredentials authWithSSLCredentials(String clientId, SslHandler sslHandler) throws AuthenticationException {
        X509Certificate[] certificates;
        try {
            certificates = (X509Certificate[]) sslHandler.engine().getSession().getPeerCertificates();
        } catch (SSLPeerUnverifiedException e) {
            if (log.isDebugEnabled()) {
                log.debug("Client SSL certification is disabled.", e);
            }
            return null;
        }
        if (certificates.length == 0) {
            log.warn("There are no certificates in the chain.");
            return null;
        }


        SslCredentialsCacheValue sslCredentialsCacheValue = getSslRegexBasedCredentials();
        if (sslCredentialsCacheValue == null) {
            return null;
        }


        for (X509Certificate certificate : certificates) {
            checkCertValidity(clientId, certificate);

            String commonName;
            try {
                commonName = SslUtil.parseCommonName(certificate);
            } catch (CertificateEncodingException e) {
                throw new AuthenticationException("Couldn't get Common Name from certificate.", e);
            }
            if (log.isTraceEnabled()) {
                log.trace("[{}] Trying to authorize client with common name - {}.", clientId, commonName);
            }


            if (sslCredentialsCacheValue.getCredentials().isEmpty()) {
                // need continue the default search
            } else {
                for (var creds : sslCredentialsCacheValue.getCredentials()) {
                    String certCommonNameRegex = creds.getSslMqttCredentials().getCertCnPattern();
                    Pattern pattern = Pattern.compile(certCommonNameRegex);
                    Matcher commonNameMatcher = pattern.matcher(commonName);
                    boolean result = commonNameMatcher.find();
                    if (result) {
                        return creds;
                    }
                }
            }


            String sslCredentialsId = ProtocolUtil.sslCredentialsId(commonName);
            List<MqttClientCredentials> matchingCredentials = clientCredentialsService.findMatchingCredentials(Collections.singletonList(sslCredentialsId));
            if (!matchingCredentials.isEmpty()) {
                MqttClientCredentials mqttClientCredentials = matchingCredentials.get(0);

                SslMqttCredentials sslMqttCredentials = JacksonUtil.fromString(mqttClientCredentials.getCredentialsValue(), SslMqttCredentials.class);

                return new ClientTypeSslMqttCredentials(mqttClientCredentials.getClientType(), sslMqttCredentials);
            }
        }
        return null;
    }

    private SslCredentialsCacheValue getSslRegexBasedCredentials() {

        Cache cache = cacheManager.getCache(CacheConstants.SSL_REGEX_BASED_CREDENTIALS_CACHE);
        String s = cache.get(ClientCredentialsType.SSL, String.class);
        SslCredentialsCacheValue sslCredentialsCacheValue = JacksonUtil.fromString(s, SslCredentialsCacheValue.class);

        if (sslCredentialsCacheValue == null) {
            log.debug("sslRegexBasedCredentials cache is empty");
            List<MqttClientCredentials> sslCredentials = clientCredentialsService.findByCredentialsType(ClientCredentialsType.SSL);
            if (sslCredentials.isEmpty()) {
                log.debug("SSL credentials are not found in DB");
                return null;
            } else {
                List<ClientTypeSslMqttCredentials> clientTypeSslMqttCredentialsList = new ArrayList<>();
                for (MqttClientCredentials sslCredential : sslCredentials) {
                    SslMqttCredentials sslMqttCredentials = JacksonUtil.fromString(sslCredential.getCredentialsValue(), SslMqttCredentials.class);
                    if (sslMqttCredentials != null && sslMqttCredentials.isCertCnIsRegex()) {
                        clientTypeSslMqttCredentialsList.add(new ClientTypeSslMqttCredentials(sslCredential.getClientType(), sslMqttCredentials));
                    }
                }
                sslCredentialsCacheValue = new SslCredentialsCacheValue(clientTypeSslMqttCredentialsList);

                cache.put(ClientCredentialsType.SSL, JacksonUtil.toString(sslCredentialsCacheValue));

                return sslCredentialsCacheValue;
            }
        } else {
            if (sslCredentialsCacheValue.getCredentials().isEmpty()) {
                log.debug("Got empty SSL regex based credentials list from cache");
            }
            return sslCredentialsCacheValue;
        }
    }

    private void checkCertValidity(String clientId, X509Certificate certificate) throws AuthenticationException {
        try {
            if (!skipValidityCheckForClientCert) {
                certificate.checkValidity();
            }
        } catch (Exception e) {
            if (log.isTraceEnabled()) {
                log.trace("[{}] X509 auth failure.", clientId, e);
            }
            throw new AuthenticationException("X509 auth failure.", e);
        }
    }

    private String getClientCertificateCommonName(SslHandler sslHandler) throws AuthenticationException {
        X509Certificate[] certificates;
        try {
            certificates = (X509Certificate[]) sslHandler.engine().getSession().getPeerCertificates();
            return SslUtil.parseCommonName(certificates[0]);
        } catch (Exception e) {
            log.error("Failed to get client's certificate common name", e);
            throw new AuthenticationException("Failed to get client's certificate common name.", e);
        }

    }
}
