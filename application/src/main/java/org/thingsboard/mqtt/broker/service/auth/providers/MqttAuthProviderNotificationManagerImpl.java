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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.security.basic.BasicMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.jwt.JwtMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.scram.ScramMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.ssl.SslMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.gen.queue.MqttAuthProviderProto;
import org.thingsboard.mqtt.broker.service.auth.EnhancedAuthenticationService;
import org.thingsboard.mqtt.broker.service.auth.providers.basic.BasicMqttClientAuthProvider;
import org.thingsboard.mqtt.broker.service.auth.providers.jwt.JwtMqttClientAuthProvider;
import org.thingsboard.mqtt.broker.service.auth.providers.ssl.SslMqttClientAuthProvider;

@Slf4j
@Service
@RequiredArgsConstructor
public class MqttAuthProviderNotificationManagerImpl implements MqttAuthProviderNotificationManager {

    private final BasicMqttClientAuthProvider basicMqttClientAuthProvider;
    private final SslMqttClientAuthProvider sslMqttClientAuthProvider;
    private final JwtMqttClientAuthProvider jwtMqttClientAuthProvider;
    private final EnhancedAuthenticationService enhancedAuthenticationService;

    @Override
    public void handleProviderNotification(MqttAuthProviderProto notification) {
        log.trace("Received MQTT authentication provider notification: {}", notification);
        MqttAuthProviderType type = MqttAuthProviderType.fromProtoNumber(notification.getProviderType().getNumber());
        switch (notification.getEventType()) {
            case PROVIDER_UPDATED -> {
                final boolean enabled = notification.getEnabled();
                switch (type) {
                    case BASIC -> basicMqttClientAuthProvider.onProviderUpdate(enabled,
                            JacksonUtil.fromString(notification.getConfiguration(), BasicMqttAuthProviderConfiguration.class));
                    case X_509 -> sslMqttClientAuthProvider.onProviderUpdate(enabled,
                            JacksonUtil.fromString(notification.getConfiguration(), SslMqttAuthProviderConfiguration.class));
                    case JWT -> jwtMqttClientAuthProvider.onProviderUpdate(enabled,
                            JacksonUtil.fromString(notification.getConfiguration(), JwtMqttAuthProviderConfiguration.class));
                    case SCRAM -> enhancedAuthenticationService.onProviderUpdate(enabled,
                            JacksonUtil.fromString(notification.getConfiguration(), ScramMqttAuthProviderConfiguration.class));
                }

            }
            case PROVIDER_DISABLED -> {
                switch (type) {
                    case BASIC -> basicMqttClientAuthProvider.disable();
                    case X_509 -> sslMqttClientAuthProvider.disable();
                    case JWT -> jwtMqttClientAuthProvider.disable();
                    case SCRAM -> enhancedAuthenticationService.disable();
                }
            }
            case PROVIDER_ENABLED -> {
                switch (type) {
                    case BASIC -> basicMqttClientAuthProvider.enable();
                    case X_509 -> sslMqttClientAuthProvider.enable();
                    case JWT -> jwtMqttClientAuthProvider.enable();
                    case SCRAM -> enhancedAuthenticationService.enable();
                }
            }
        }
    }

    @Override
    public boolean defaultProvidersEnabled() {
        return basicMqttClientAuthProvider.isEnabled() ||
               sslMqttClientAuthProvider.isEnabled() ||
               jwtMqttClientAuthProvider.isEnabled();
    }

}
