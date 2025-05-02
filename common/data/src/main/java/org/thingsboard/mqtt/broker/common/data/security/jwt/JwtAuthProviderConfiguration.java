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
import org.thingsboard.mqtt.broker.common.data.security.MqttClientAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.validation.NoXss;
import org.thingsboard.mqtt.broker.exception.DataValidationException;

import java.util.Map;
import java.util.Objects;

@Data
public class JwtAuthProviderConfiguration implements MqttClientAuthProviderConfiguration {

    @NoXss
    private JwtVerifierType jwtVerifierType;
    @NoXss
    private ClientType defaultClientType;

    private JwtVerifierConfiguration jwtVerifierConfiguration;

    private Map<String, String> authClaims;
    // optional: will be used to manage creation of the non-default client type.
    private Map<String, String> clientTypeClaims;

    // omit for initial implementation
    // private boolean disconnectAfterExpiration;

    private boolean verifyJwtFirst;

    @Override
    public MqttClientAuthProviderType getType() {
        return MqttClientAuthProviderType.JWT;
    }

    @Override
    public void validate() {
        if (jwtVerifierType == null) {
            throw new DataValidationException("Jwt verifier type should be specified!");
        }
        if (jwtVerifierConfiguration == null) {
            throw new DataValidationException("Jwt verifier configuration should be specified!");
        }
        jwtVerifierConfiguration.validate();
    }

    // TODO: Application or Device by default?
    public ClientType getDefaultClientType() {
        return Objects.requireNonNullElse(defaultClientType, ClientType.APPLICATION);
    }

    public Map<String, String> getAuthClaims() {
        return CollectionUtils.isEmpty(authClaims) ? Map.of() : authClaims;
    }

    public Map<String, String> getClientTypeClaims() {
        return CollectionUtils.isEmpty(clientTypeClaims) ? Map.of() : clientTypeClaims;
    }

}
