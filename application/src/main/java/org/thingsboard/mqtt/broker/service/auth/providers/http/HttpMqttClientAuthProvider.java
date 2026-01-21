/**
 * Copyright © 2016-2026 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.auth.providers.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.handler.ssl.SslHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.client.credentials.PubSubAuthorizationRules;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.security.http.HttpAuthCallback;
import org.thingsboard.mqtt.broker.common.data.security.http.HttpMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.util.CallbackUtil;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttAuthProviderService;
import org.thingsboard.mqtt.broker.service.auth.AuthorizationRuleService;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthContext;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthResponse;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthStatus;
import org.thingsboard.mqtt.broker.service.auth.providers.MqttClientAuthProvider;
import org.thingsboard.mqtt.broker.service.security.authorization.AuthRulePatterns;
import org.thingsboard.mqtt.broker.util.SslUtil;

import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.EMPTY_STR;
import static org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType.HTTP;

@Slf4j
@Service
@RequiredArgsConstructor
public class HttpMqttClientAuthProvider implements MqttClientAuthProvider<HttpMqttAuthProviderConfiguration> {

    private final MqttAuthProviderService mqttAuthProviderService;
    private final AuthorizationRuleService authorizationRuleService;

    private volatile HttpMqttAuthProviderConfiguration configuration;
    private volatile AuthRulePatterns authRulePatterns;
    private volatile HttpAuthClient client;

    @SneakyThrows
    @PostConstruct
    public void init() {
        MqttAuthProvider httpAuthProvider = mqttAuthProviderService.getAuthProviderByType(MqttAuthProviderType.HTTP)
                .orElseThrow(() -> new IllegalStateException("Failed to initialize HTTP service authentication provider! Provider is missing in the DB!"));
        configuration = (HttpMqttAuthProviderConfiguration) httpAuthProvider.getConfiguration();
        authRulePatterns = authorizationRuleService.parseAuthorizationRule(configuration);
        client = httpAuthProvider.isEnabled() ? initClient(configuration) : null;
    }

    @PreDestroy
    public void destroy() {
        disable();
    }

    private HttpAuthClient initClient(HttpMqttAuthProviderConfiguration config) {
        return new HttpAuthClient(config);
    }

    @Override
    public AuthResponse authenticate(AuthContext authContext) {
        if (client == null) {
            return AuthResponse.providerDisabled(MqttAuthProviderType.HTTP);
        }
        log.trace("[{}] Authenticating client using HTTP service provider...", authContext.getClientId());
        try {
            CountDownLatch latch = new CountDownLatch(1);

            AtomicReference<AuthResponse> response = new AtomicReference<>();
            HttpAuthCallback callback = CallbackUtil.createHttpCallback(result -> {

                response.set(getAuthResponse(authContext.getClientId(), result));
                latch.countDown();

            }, throwable -> {
                response.set(AuthResponse.skip(throwable.getMessage()));
                latch.countDown();
            });

            String requestBody = getRequestBody(authContext);
            if (client != null) {
                client.processMessage(authContext.getClientId(), requestBody, callback);
            } else {
                throw new RuntimeException("HTTP client is destroyed. Please check your configuration!");
            }
            latch.await();
            return response.get();
        } catch (Exception e) {
            log.debug("[{}] Authentication failed", authContext.getClientId(), e);
            return AuthResponse.skip(e.getMessage());
        }
    }

    private String getRequestBody(AuthContext authContext) {
        String requestBody = configuration.getRequestBody();

        String commonName = getClientCertificateCommonName(authContext.getSslHandler());
        String clientId = authContext.getClientId();
        String username = authContext.getUsername() != null ? authContext.getUsername() : EMPTY_STR;
        String password = authContext.getPasswordBytes() != null ?
                new String(authContext.getPasswordBytes(), StandardCharsets.UTF_8) : EMPTY_STR;

        return requestBody
                .replace("${commonName}", commonName)
                .replace("${clientId}", clientId)
                .replace("${username}", username)
                .replace("${password}", password);
    }

    @SneakyThrows
    @Override
    public void onProviderUpdate(boolean enabled, HttpMqttAuthProviderConfiguration config) {
        if (client != null) {
            client.destroy();
        }
        configuration = config;
        authRulePatterns = authorizationRuleService.parseAuthorizationRule(config);
        client = enabled ? initClient(configuration) : null;
    }

    @Override
    public void enable() {
        if (client != null) {
            return;
        }
        if (configuration == null || authRulePatterns == null) {
            throw new IllegalStateException("Cannot enable HTTP provider! Provider configuration or authorization rules are missing!");
        }
        client = initClient(configuration);
    }

    @Override
    public void disable() {
        if (client != null) {
            client.destroy();
            client = null;
        }
    }

    @Override
    public boolean isEnabled() {
        return client != null;
    }

    public void checkConnection(MqttAuthProvider authProvider) throws Exception {
        HttpAuthClient client = null;
        try {
            SettableFuture<Void> result = SettableFuture.create();

            client = initClient((HttpMqttAuthProviderConfiguration) authProvider.getConfiguration());
            client.checkConnection(CallbackUtil.createHttpCallback(v -> result.set(null), result::setException));

            result.get(15, TimeUnit.SECONDS);
        } finally {
            if (client != null) {
                client.destroy();
            }
        }
    }

    private String getClientCertificateCommonName(SslHandler sslHandler) {
        if (sslHandler == null) {
            return EMPTY_STR;
        }
        try {
            X509Certificate[] certificates = (X509Certificate[]) sslHandler.engine().getSession().getPeerCertificates();
            return SslUtil.parseCommonName(certificates[0]);
        } catch (Exception e) {
            log.warn("Failed to get client certificate CN from SSL handler.", e);
            return EMPTY_STR;
        }
    }


    private AuthResponse getAuthResponse(String clientId, ResponseEntity<JsonNode> responseEntity) {
        JsonNode responseBody = responseEntity.getBody();

        if (responseBody == null || responseBody.isNull()) {
            log.debug("[{}] Empty response body received. Using default configuration.", clientId);
            return success(configuration.getDefaultClientType(), authRulePatterns);
        }

        try {
            HttpAuthResponseDto authResponseDto = JacksonUtil.convertValue(responseBody, HttpAuthResponseDto.class);

            AuthStatus status = parseStatus(clientId, authResponseDto.getResult());

            if (status == AuthStatus.FAILURE) {
                return AuthResponse.failure("Authentication explicitly denied by the external HTTP service.");
            }

            if (status == AuthStatus.SKIPPED) {
                return AuthResponse.skip("HTTP service skipped authentication; attempting next available provider.");
            }

            ClientType finalClientType = parseClientType(clientId, authResponseDto.getClientType());
            AuthRulePatterns authRulePatterns = parseAuthRules(clientId, authResponseDto.getAuthRules());

            return success(finalClientType, authRulePatterns);

        } catch (Exception e) {
            log.warn("[{}] Failed to parse HTTP auth response body: {}", clientId, responseBody, e);
            return AuthResponse.skip(e.getMessage());
        }
    }

    private AuthResponse success(ClientType type, AuthRulePatterns authRules) {
        return AuthResponse.success(type, Collections.singletonList(authRules), HTTP.name());
    }

    private AuthStatus parseStatus(String clientId, String result) {
        if (result == null) {
            return AuthStatus.SUCCESS;
        }
        try {
            return AuthStatus.valueOf(result.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("[{}] Unknown auth result status: {}. Defaulting to SKIPPED.", clientId, result);
            return AuthStatus.SKIPPED;
        }
    }

    private ClientType parseClientType(String clientId, String type) {
        if (StringUtils.isBlank(type)) {
            return configuration.getDefaultClientType();
        }
        try {
            return ClientType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("[{}] Unknown client type in response: {}. Falling back to default.", clientId, type);
            return configuration.getDefaultClientType();
        }
    }

    private AuthRulePatterns parseAuthRules(String clientId, PubSubAuthorizationRules authRules) {
        if (authRules != null) {
            try {
                return authorizationRuleService.parsePubSubAuthorizationRule(authRules);
            } catch (Exception e) {
                log.warn("[{}] Failed to parse auth rules from response: {}", clientId, authRules, e);
            }
        }
        return authRulePatterns;

    }

}
