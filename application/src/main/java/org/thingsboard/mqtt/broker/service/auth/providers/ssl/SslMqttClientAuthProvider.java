/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.auth.providers.ssl;

import io.netty.handler.ssl.SslHandler;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.cache.CacheConstants;
import org.thingsboard.mqtt.broker.cache.CacheNameResolver;
import org.thingsboard.mqtt.broker.common.data.client.credentials.ClientTypeSslMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.client.credentials.SslMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.data.security.ssl.SslMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.dao.client.credentials.SslCredentialsCacheValue;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttAuthProviderService;
import org.thingsboard.mqtt.broker.dao.util.protocol.ProtocolUtil;
import org.thingsboard.mqtt.broker.exception.AuthenticationException;
import org.thingsboard.mqtt.broker.server.MqttHandlerCtx;
import org.thingsboard.mqtt.broker.service.auth.AuthorizationRuleService;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthContext;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthResponse;
import org.thingsboard.mqtt.broker.service.auth.providers.MqttClientAuthProvider;
import org.thingsboard.mqtt.broker.service.security.authorization.AuthRulePatterns;
import org.thingsboard.mqtt.broker.util.SslUtil;

import javax.net.ssl.SSLPeerUnverifiedException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import static org.thingsboard.mqtt.broker.service.auth.providers.ssl.SslAuthFailure.FAILED_TO_GET_CERT_CN;
import static org.thingsboard.mqtt.broker.service.auth.providers.ssl.SslAuthFailure.FAILED_TO_GET_CLIENT_CERT_CN;
import static org.thingsboard.mqtt.broker.service.auth.providers.ssl.SslAuthFailure.NO_CERTS_IN_CHAIN;
import static org.thingsboard.mqtt.broker.service.auth.providers.ssl.SslAuthFailure.NO_X_509_CREDS_FOUND;
import static org.thingsboard.mqtt.broker.service.auth.providers.ssl.SslAuthFailure.PEER_IDENTITY_NOT_VERIFIED;
import static org.thingsboard.mqtt.broker.service.auth.providers.ssl.SslAuthFailure.SSL_HANDLER_NOT_CONSTRUCTED;
import static org.thingsboard.mqtt.broker.service.auth.providers.ssl.SslAuthFailure.X_509_AUTH_FAILURE;

@Slf4j
@Service
@RequiredArgsConstructor
public class SslMqttClientAuthProvider implements MqttClientAuthProvider<SslMqttAuthProviderConfiguration> {

    private final MqttClientCredentialsService clientCredentialsService;
    private final AuthorizationRuleService authorizationRuleService;
    private final CacheNameResolver cacheNameResolver;
    private final MqttAuthProviderService mqttAuthProviderService;
    private final MqttHandlerCtx mqttHandlerCtx;

    private volatile boolean enabled;
    private volatile SslMqttAuthProviderConfiguration configuration;

    @PostConstruct
    public void init() {
        MqttAuthProvider sslAuthProvider = mqttAuthProviderService.getAuthProviderByType(MqttAuthProviderType.X_509)
                .orElseThrow(() -> new IllegalStateException("Failed to initialize X_509 Certificate chain authentication provider! Provider is missing in the DB!"));
        this.enabled = sslAuthProvider.isEnabled();
        this.configuration = (SslMqttAuthProviderConfiguration) sslAuthProvider.getConfiguration();
        setClientAuthType();
    }

    private void setClientAuthType() {
        this.mqttHandlerCtx.setClientAuthType(configuration.getClientAuthType());
    }

    @Override
    public AuthResponse authenticate(AuthContext authContext) {
        if (!enabled) {
            return AuthResponse.providerDisabled(MqttAuthProviderType.X_509);
        }
        if (!authContext.isSecurePortUsed()) {
            String errorMsg = SSL_HANDLER_NOT_CONSTRUCTED.getErrorMsg();
            String logErrorMsg = "[{}] " + errorMsg;
            log.error(logErrorMsg, authContext);
            return AuthResponse.failure(errorMsg);
        }
        if (log.isTraceEnabled()) {
            log.trace("[{}] Authenticating client with SSL credentials", authContext.getClientId());
        }
        try {
            ClientTypeSslMqttCredentials clientTypeSslMqttCredentials = authWithSSLCredentials(authContext.getClientId(), authContext.getSslHandler());
            if (clientTypeSslMqttCredentials == null) {
                String errorMsg = NO_X_509_CREDS_FOUND.getErrorMsg();
                log.warn(errorMsg);
                return AuthResponse.failure(errorMsg);
            }
            putIntoClientSessionCredsCache(authContext, clientTypeSslMqttCredentials);
            if (log.isDebugEnabled()) {
                String protocol = authContext.getSslHandler().engine().getSession().getProtocol();
                log.debug("[{}] Successfully authenticated with SSL credentials as {}. Version {}",
                        authContext.getClientId(), clientTypeSslMqttCredentials.getType(), protocol);
            }
            String clientCommonName = getClientCertificateCommonName(authContext.getSslHandler());
            List<AuthRulePatterns> authRulePatterns = authorizationRuleService.parseSslAuthorizationRule(clientTypeSslMqttCredentials, clientCommonName);
            return AuthResponse.success(clientTypeSslMqttCredentials.getType(), authRulePatterns);
        } catch (Exception e) {
            return AuthResponse.failure(e.getMessage());
        }
    }

    @Override
    public void onProviderUpdate(boolean enabled, SslMqttAuthProviderConfiguration configuration) {
        this.enabled = enabled;
        this.configuration = configuration;
        setClientAuthType();
    }

    @Override
    public void enable() {
        this.enabled = true;
    }

    @Override
    public void disable() {
        this.enabled = false;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    private ClientTypeSslMqttCredentials authWithSSLCredentials(String clientId, SslHandler sslHandler) throws AuthenticationException {
        X509Certificate[] certificates;
        try {
            certificates = (X509Certificate[]) sslHandler.engine().getSession().getPeerCertificates();
        } catch (SSLPeerUnverifiedException e) {
            if (log.isDebugEnabled()) {
                log.debug(PEER_IDENTITY_NOT_VERIFIED.getErrorMsg(), e);
            }
            throw new AuthenticationException(PEER_IDENTITY_NOT_VERIFIED.getErrorMsg());
        }
        if (certificates.length == 0) {
            log.warn(NO_CERTS_IN_CHAIN.getErrorMsg());
            throw new AuthenticationException(NO_CERTS_IN_CHAIN.getErrorMsg());
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
                throw new AuthenticationException(FAILED_TO_GET_CERT_CN.getErrorMsg(), e);
            }
            if (log.isTraceEnabled()) {
                log.trace("[{}] Trying to authorize client with common name - {}.", clientId, commonName);
            }

            if (!sslCredentialsCacheValue.getCredentials().isEmpty()) {
                for (var creds : sslCredentialsCacheValue.getCredentials()) {
                    Pattern pattern = Pattern.compile(creds.getSslMqttCredentials().getCertCnPattern());
                    boolean found = pattern.matcher(commonName).find();
                    if (found) {
                        return creds;
                    }
                }
            }

            String sslCredentialsId = ProtocolUtil.sslCredentialsId(commonName);
            List<MqttClientCredentials> matchingCredentials = clientCredentialsService.findMatchingCredentials(Collections.singletonList(sslCredentialsId));
            if (!matchingCredentials.isEmpty()) {
                MqttClientCredentials mqttClientCredentials = matchingCredentials.get(0);
                SslMqttCredentials sslMqttCredentials = JacksonUtil.fromString(mqttClientCredentials.getCredentialsValue(), SslMqttCredentials.class);
                return new ClientTypeSslMqttCredentials(mqttClientCredentials.getClientType(), sslMqttCredentials, mqttClientCredentials.getName());
            }
        }
        return null;
    }

    private SslCredentialsCacheValue getSslRegexBasedCredentials() {
        SslCredentialsCacheValue sslCredentialsCacheValue = getFromSslRegexCache();
        if (sslCredentialsCacheValue == null) {
            log.debug("sslRegexBasedCredentials cache is empty");
            List<MqttClientCredentials> sslCredentials = clientCredentialsService.findByCredentialsType(ClientCredentialsType.X_509);
            if (sslCredentials.isEmpty()) {
                log.debug("X_509 credentials are not found in DB");
                return null;
            } else {
                sslCredentialsCacheValue = prepareSslRegexCredentialsWithValuesFromDb(sslCredentials);
                putInSslRegexCache(sslCredentialsCacheValue);
                return sslCredentialsCacheValue;
            }
        } else {
            if (sslCredentialsCacheValue.getCredentials().isEmpty()) {
                log.debug("Got empty X_509 regex based credentials list from cache");
            }
            return sslCredentialsCacheValue;
        }
    }

    private void checkCertValidity(String clientId, X509Certificate certificate) throws AuthenticationException {
        try {
            if (!configuration.isSkipValidityCheckForClientCert()) {
                certificate.checkValidity();
            }
        } catch (Exception e) {
            if (log.isTraceEnabled()) {
                log.trace("[{}] X509 auth failure.", clientId, e);
            }
            String errorMsg = String.format(X_509_AUTH_FAILURE.getErrorMsg(), e.getMessage());
            throw new AuthenticationException(errorMsg, e);
        }
    }

    private String getClientCertificateCommonName(SslHandler sslHandler) throws AuthenticationException {
        X509Certificate[] certificates;
        try {
            certificates = (X509Certificate[]) sslHandler.engine().getSession().getPeerCertificates();
            return SslUtil.parseCommonName(certificates[0]);
        } catch (Exception e) {
            log.error(FAILED_TO_GET_CLIENT_CERT_CN.getErrorMsg(), e);
            throw new AuthenticationException(FAILED_TO_GET_CLIENT_CERT_CN.getErrorMsg(), e);
        }
    }

    private SslCredentialsCacheValue prepareSslRegexCredentialsWithValuesFromDb(List<MqttClientCredentials> sslCredentials) {
        List<ClientTypeSslMqttCredentials> clientTypeSslMqttCredentialsList = new ArrayList<>();
        for (MqttClientCredentials sslCredential : sslCredentials) {
            SslMqttCredentials sslMqttCredentials = JacksonUtil.fromString(sslCredential.getCredentialsValue(), SslMqttCredentials.class);
            if (sslMqttCredentials != null && sslMqttCredentials.isCertCnIsRegex()) {
                clientTypeSslMqttCredentialsList.add(new ClientTypeSslMqttCredentials(sslCredential.getClientType(), sslMqttCredentials, sslCredential.getName()));
            }
        }
        return new SslCredentialsCacheValue(clientTypeSslMqttCredentialsList);
    }

    private SslCredentialsCacheValue getFromSslRegexCache() {
        Cache cache = getSslRegexCredentialsCache();
        return JacksonUtil.fromString(cache.get(ClientCredentialsType.X_509, String.class), SslCredentialsCacheValue.class);
    }

    private void putInSslRegexCache(SslCredentialsCacheValue sslCredentialsCacheValue) {
        Cache cache = getSslRegexCredentialsCache();
        cache.put(ClientCredentialsType.X_509, JacksonUtil.toString(sslCredentialsCacheValue));
    }

    private Cache getSslRegexCredentialsCache() {
        return cacheNameResolver.getCache(CacheConstants.SSL_REGEX_BASED_CREDENTIALS_CACHE);
    }

    private void putIntoClientSessionCredsCache(AuthContext authContext, ClientTypeSslMqttCredentials credentials) {
        getClientSessionCredentialsCache().put(authContext.getClientId(), credentials.getName());
    }

    private Cache getClientSessionCredentialsCache() {
        return cacheNameResolver.getCache(CacheConstants.CLIENT_SESSION_CREDENTIALS_CACHE);
    }
}
