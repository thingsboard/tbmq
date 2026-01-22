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
package org.thingsboard.mqtt.broker.service.auth.providers.jwt;

import com.nimbusds.jose.JOSEException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.security.jwt.AlgorithmBasedVerifierConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.jwt.HmacBasedAlgorithmConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.jwt.JwksVerifierConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.jwt.JwtMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.jwt.PemKeyAlgorithmConfiguration;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttAuthProviderService;
import org.thingsboard.mqtt.broker.service.auth.AuthorizationRuleService;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthContext;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthResponse;
import org.thingsboard.mqtt.broker.service.auth.providers.MqttClientAuthProvider;
import org.thingsboard.mqtt.broker.service.security.authorization.AuthRulePatterns;

import java.nio.charset.StandardCharsets;


@Slf4j
@Service
@RequiredArgsConstructor
public class JwtMqttClientAuthProvider implements MqttClientAuthProvider<JwtMqttAuthProviderConfiguration> {

    private final MqttAuthProviderService mqttAuthProviderService;
    private final AuthorizationRuleService authorizationRuleService;

    private volatile JwtMqttAuthProviderConfiguration configuration;
    private volatile AuthRulePatterns authRulePatterns;
    private volatile JwtVerificationStrategy verificationStrategy;

    @SneakyThrows
    @PostConstruct
    public void init() {
        MqttAuthProvider jwtAuthProvider = mqttAuthProviderService.getAuthProviderByType(MqttAuthProviderType.JWT)
                .orElseThrow(() -> new IllegalStateException("Failed to initialize JWT authentication provider! Provider is missing in the DB!"));
        this.configuration = (JwtMqttAuthProviderConfiguration) jwtAuthProvider.getConfiguration();
        this.authRulePatterns = authorizationRuleService.parseAuthorizationRule(this.configuration);
        this.verificationStrategy = jwtAuthProvider.isEnabled() ? createStrategy() : null;
    }

    @PreDestroy
    public void destroy() {
        disable();
    }

    private JwtVerificationStrategy createStrategy() throws JOSEException {
        var jwtVerifierConfig = configuration.getJwtVerifierConfiguration();
        var validator = new JwtClaimsValidator(configuration, authRulePatterns);

        if (jwtVerifierConfig instanceof AlgorithmBasedVerifierConfiguration algConfiguration) {
            var algoConfig = algConfiguration.getJwtSignAlgorithmConfiguration();
            if (algoConfig instanceof HmacBasedAlgorithmConfiguration hmacConfig) {
                return new HmacJwtVerificationStrategy(hmacConfig.getSecret(), validator);
            }
            if (algoConfig instanceof PemKeyAlgorithmConfiguration pemConfig) {
                return new PemKeyJwtVerificationStrategy(pemConfig.getPublicPemKey(), validator);
            }
            throw new IllegalArgumentException("Unsupported AlgorithmBasedVerifierConfiguration: " + algoConfig.getClass().getSimpleName());
        }

        if (jwtVerifierConfig instanceof JwksVerifierConfiguration jwksConfig) {
            return new JwksVerificationStrategy(jwksConfig, validator);
        }

        throw new IllegalArgumentException("Unsupported JwtVerifierConfiguration: " + jwtVerifierConfig.getClass().getSimpleName());
    }

    @Override
    public AuthResponse authenticate(AuthContext authContext) {
        JwtVerificationStrategy verificationStrategy = this.verificationStrategy;
        if (verificationStrategy == null) {
            return AuthResponse.providerDisabled(MqttAuthProviderType.JWT);
        }
        log.trace("[{}] Authenticating client using JWT provider...", authContext.getClientId());
        byte[] passwordBytes = authContext.getPasswordBytes();
        if (passwordBytes == null) {
            return AuthResponse.skip("Failed to fetch JWT authentication token from password.");
        }
        try {
            return verificationStrategy.authenticateJwt(authContext, new String(passwordBytes, StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.debug("[{}] Authentication failed", authContext.getClientId(), e);
            return AuthResponse.skip(e.getMessage());
        }
    }

    @SneakyThrows
    @Override
    public void onProviderUpdate(boolean enabled, JwtMqttAuthProviderConfiguration configuration) {
        if (this.verificationStrategy != null) {
            this.verificationStrategy.destroy();
        }
        this.configuration = configuration;
        this.authRulePatterns = authorizationRuleService.parseAuthorizationRule(configuration);
        this.verificationStrategy = enabled ? createStrategy() : null;
    }

    @SneakyThrows
    @Override
    public void enable() {
        if (verificationStrategy != null) {
            return;
        }
        if (configuration == null || authRulePatterns == null) {
            throw new IllegalStateException("Cannot enable JWT provider! Provider configuration or authorization rules are missing!");
        }
        this.verificationStrategy = createStrategy();
    }

    @Override
    public void disable() {
        if (this.verificationStrategy != null) {
            this.verificationStrategy.destroy();
            this.verificationStrategy = null;
        }
    }

    @Override
    public boolean isEnabled() {
        return verificationStrategy != null;
    }
}
