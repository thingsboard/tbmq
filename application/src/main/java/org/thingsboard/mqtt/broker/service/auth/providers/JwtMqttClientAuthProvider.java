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

import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.security.jwt.JwtAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.exception.AuthenticationException;


// TODO: add implementation
@Slf4j
public class JwtMqttClientAuthProvider implements MqttClientAuthProvider<JwtAuthProviderConfiguration> {

    private boolean enabled;
    private JwtAuthProviderConfiguration configuration;

    public JwtMqttClientAuthProvider(boolean enabled, JwtAuthProviderConfiguration configuration) {
        this.enabled = enabled;
        this.configuration = configuration;
    }

    @Override
    public MqttClientAuthProviderType getType() {
        return MqttClientAuthProviderType.JWT;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
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
    public JwtAuthProviderConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public void updateConfiguration(JwtAuthProviderConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public AuthResponse authenticate(AuthContext authContext) throws AuthenticationException {
        return AuthResponse.defaultAuthResponse();
    }

}
