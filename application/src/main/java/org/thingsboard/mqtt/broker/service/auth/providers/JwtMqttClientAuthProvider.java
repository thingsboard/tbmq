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

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.security.jwt.JwtMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttAuthProviderService;
import org.thingsboard.mqtt.broker.exception.AuthenticationException;

import java.util.Optional;


@Slf4j
@Service
public class JwtMqttClientAuthProvider implements MqttClientAuthProvider<JwtMqttAuthProviderConfiguration> {

    private final MqttAuthProviderService mqttAuthProviderService;

    private volatile boolean enabled;
    private volatile JwtMqttAuthProviderConfiguration configuration;

    public JwtMqttClientAuthProvider(MqttAuthProviderService mqttAuthProviderService) {
        this.mqttAuthProviderService = mqttAuthProviderService;
    }

    @PostConstruct
    public void init() {
        Optional<MqttAuthProvider> jwtAuthProviderOpt = mqttAuthProviderService.getAuthProviderByType(MqttAuthProviderType.JWT);
        if (jwtAuthProviderOpt.isEmpty()) {
            log.warn("JWT authentication provider does not exist! JWT authentication is disabled!");
            return;
        }
        MqttAuthProvider jwtAuthProvider = jwtAuthProviderOpt.get();
        this.enabled = jwtAuthProvider.isEnabled();
        this.configuration = (JwtMqttAuthProviderConfiguration) jwtAuthProvider.getConfiguration();
    }

    @Override
    public AuthResponse authenticate(AuthContext authContext) throws AuthenticationException {
        return AuthResponse.defaultAuthResponse();
    }

    @Override
    public void onProviderUpdate(boolean enabled, JwtMqttAuthProviderConfiguration configuration) {
        this.enabled = enabled;
        this.configuration = configuration;
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

}
