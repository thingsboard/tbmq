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

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.cache.CacheNameResolver;
import org.thingsboard.mqtt.broker.common.data.security.basic.BasicMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.jwt.JwtMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.ssl.SslMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.service.auth.AuthorizationRuleService;

@Component
@RequiredArgsConstructor
public class MqttAuthProviderFactory {

    private final AuthorizationRuleService authorizationRuleService;
    private final MqttClientCredentialsService credentialsService;
    private final CacheNameResolver cacheNameResolver;
    private final @Lazy BCryptPasswordEncoder passwordEncoder;

    public BasicMqttClientAuthProvider createBasicProvider(BasicMqttAuthProviderConfiguration configuration) {
        return createBasicProvider(configuration, true);
    }

    public BasicMqttClientAuthProvider createBasicProvider(BasicMqttAuthProviderConfiguration configuration, boolean enabled) {
        return new BasicMqttClientAuthProvider(authorizationRuleService, credentialsService, cacheNameResolver, passwordEncoder, enabled, configuration);
    }

    public SslMqttClientAuthProvider createSslProvider(SslMqttAuthProviderConfiguration configuration) {
        return createSslProvider(configuration, true);
    }

    public SslMqttClientAuthProvider createSslProvider(SslMqttAuthProviderConfiguration configuration, boolean enabled) {
        return new SslMqttClientAuthProvider(authorizationRuleService, credentialsService, cacheNameResolver, enabled, configuration);
    }


    public JwtMqttClientAuthProvider createJwtProvider(JwtMqttAuthProviderConfiguration configuration) {
        return createJwtProvider(configuration, true);
    }

    public JwtMqttClientAuthProvider createJwtProvider(JwtMqttAuthProviderConfiguration configuration, boolean enabled) {
        return new JwtMqttClientAuthProvider(enabled, configuration);
    }

}