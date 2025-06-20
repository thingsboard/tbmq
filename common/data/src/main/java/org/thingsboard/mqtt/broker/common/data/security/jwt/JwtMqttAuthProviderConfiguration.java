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
package org.thingsboard.mqtt.broker.common.data.security.jwt;

import lombok.Data;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.client.credentials.PubSubAuthorizationRules;
import org.thingsboard.mqtt.broker.common.data.client.credentials.SinglePubSubAuthRulesAware;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.util.AuthRulesUtil;
import org.thingsboard.mqtt.broker.common.data.validation.NoXss;
import org.thingsboard.mqtt.broker.exception.DataValidationException;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Data
public class JwtMqttAuthProviderConfiguration implements MqttAuthProviderConfiguration, SinglePubSubAuthRulesAware {

    @NoXss
    private JwtVerifierType jwtVerifierType;
    @NoXss
    private ClientType defaultClientType;

    private JwtVerifierConfiguration jwtVerifierConfiguration;

    private Map<String, String> authClaims;
    // optional: will be used to manage creation of the non-default client type.
    private Map<String, String> clientTypeClaims;

    private PubSubAuthorizationRules authRules;

    // omit for initial implementation
    // private boolean disconnectAfterExpiration;

    @Override
    public MqttAuthProviderType getType() {
        return MqttAuthProviderType.JWT;
    }

    @Override
    public void validate() {
        if (ClientType.INTEGRATION.equals(defaultClientType)) {
            throw new DataValidationException("INTEGRATION client type is not supported!");
        }
        AuthRulesUtil.validateAndCompileAuthRules(authRules);
        if (jwtVerifierType == null) {
            throw new DataValidationException("Jwt verifier type should be specified!");
        }
        if (jwtVerifierConfiguration == null) {
            throw new DataValidationException("Jwt verifier configuration should be specified!");
        }
        jwtVerifierConfiguration.validate();
    }

    public ClientType getDefaultClientType() {
        return Objects.requireNonNullElse(defaultClientType, ClientType.DEVICE);
    }

    public Map<String, String> getAuthClaims() {
        return CollectionUtils.isEmpty(authClaims) ? Map.of() : authClaims;
    }

    public Map<String, String> getClientTypeClaims() {
        return CollectionUtils.isEmpty(clientTypeClaims) ? Map.of() : clientTypeClaims;
    }

    public static JwtMqttAuthProviderConfiguration defaultConfiguration() {
        var jwtConfig = new JwtMqttAuthProviderConfiguration();
        jwtConfig.setAuthRules(PubSubAuthorizationRules.newInstance(List.of(".*")));
        jwtConfig.setDefaultClientType(ClientType.DEVICE);
        jwtConfig.setJwtVerifierType(JwtVerifierType.ALGORITHM_BASED);
        jwtConfig.setJwtVerifierConfiguration(AlgorithmBasedVerifierConfiguration.defaultConfiguration());
        return jwtConfig;
    }

}
