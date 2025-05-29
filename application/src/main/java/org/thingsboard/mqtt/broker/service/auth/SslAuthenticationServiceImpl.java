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
package org.thingsboard.mqtt.broker.service.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthContext;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthResponse;
import org.thingsboard.mqtt.broker.service.auth.providers.MqttClientAuthProviderManager;

@Service
@RequiredArgsConstructor
public class SslAuthenticationServiceImpl implements SslAuthenticationService {

    private final MqttClientAuthProviderManager mqttClientAuthProviderManager;

    @Override
    public AuthResponse authenticate(AuthContext authContext) {
        if (!mqttClientAuthProviderManager.isSslEnabled()) {
            return AuthResponse.providerDisabled(MqttAuthProviderType.X_509);
        }
        try {
            return mqttClientAuthProviderManager.getSslProvider().authenticate(authContext);
        } catch (Exception e) {
            return AuthResponse.failure(e.getMessage());
        }
    }

}