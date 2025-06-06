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
package org.thingsboard.mqtt.broker.service.auth.providers.jwt;

import com.nimbusds.jose.JOSEException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.security.jwt.AlgorithmBasedVerifierConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.jwt.HmacBasedAlgorithmConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.jwt.JwtMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.jwt.JwtSignAlgorithm;
import org.thingsboard.mqtt.broker.common.data.security.jwt.JwtVerifierType;
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

    private JwtVerificationStrategy createStrategy() throws JOSEException {
        if (JwtVerifierType.ALGORITHM_BASED.equals(configuration.getJwtVerifierType())) {
            var conf = (AlgorithmBasedVerifierConfiguration) configuration.getJwtVerifierConfiguration();
            if (JwtSignAlgorithm.HMAC_BASED.equals(conf.getAlgorithm())) {
                String rawSecret = ((HmacBasedAlgorithmConfiguration) conf.getJwtSignAlgorithmConfiguration()).getSecret();
                return new HmacJwtVerificationStrategy(rawSecret, new JwtClaimsValidator(configuration, authRulePatterns));
            }
        }
        // TODO: add other strategies
        throw new UnsupportedOperationException("No suitable verification strategy configured!");
    }

    @Override
    public AuthResponse authenticate(AuthContext authContext) {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Trying to authenticate client using JWT provider...", authContext.getClientId());
        }
        JwtVerificationStrategy verificationStrategy = this.verificationStrategy;
        if (verificationStrategy == null) {
            return AuthResponse.providerDisabled(MqttAuthProviderType.JWT);
        }
        byte[] passwordBytes = authContext.getPasswordBytes();
        if (passwordBytes == null) {
            return AuthResponse.failure("Failed to fetch JWT authentication token from password.");
        }
        try {
            return verificationStrategy.authenticateJwt(authContext, new String(passwordBytes, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return AuthResponse.failure(e.getMessage());
        }
    }

    @SneakyThrows
    @Override
    public void onProviderUpdate(boolean enabled, JwtMqttAuthProviderConfiguration configuration) {
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
        this.verificationStrategy = null;
    }

    @Override
    public boolean isEnabled() {
        return verificationStrategy != null;
    }
}
