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
package org.thingsboard.mqtt.broker.service.auth.providers;

import lombok.Builder;
import lombok.Data;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.service.security.authorization.AuthRulePatterns;

import java.util.List;

@Data
@Builder(toBuilder = true)
public class AuthResponse {

    private final boolean success;
    private final ClientType clientType;
    private final List<AuthRulePatterns> authRulePatterns;
    private final String reason;

    public static AuthResponse failure(String reason) {
        return AuthResponse.builder().success(false).reason(reason).build();
    }

    public static AuthResponse providerDisabled(MqttAuthProviderType providerType) {
        return AuthResponse.builder().success(false).reason(providerType.getDisplayName() + " authentication is disabled!").build();
    }

    public static AuthResponse defaultAuthResponse() {
        return AuthResponse.builder().success(true).clientType(ClientType.DEVICE).authRulePatterns(null).build();
    }

    public static AuthResponse success(ClientType clientType, List<AuthRulePatterns> authRulePatterns) {
        return AuthResponse.builder().success(true).clientType(clientType).authRulePatterns(authRulePatterns).build();
    }

    public boolean isFailure() {
        return !success;
    }
}
