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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttAuthProviderService;
import org.thingsboard.mqtt.broker.gen.queue.MqttAuthProviderEventProto;
import org.thingsboard.mqtt.broker.gen.queue.MqttAuthProviderProto;

import java.util.List;

@Slf4j
@Service
@Profile("!install")
@RequiredArgsConstructor
public class MqttClientClientAuthProviderManagerImpl implements MqttClientAuthProviderManager {

    private volatile boolean basicAuthEnabled;
    private volatile boolean sslAuthEnabled;
    private volatile boolean jwtAuthEnabled;
    private volatile boolean enhancedAuthEnabled;

    private final BasicMqttClientAuthProvider basicMqttClientAuthProvider;
    private final SslMqttClientAuthProvider sslMqttClientAuthProvider;
    private final JwtMqttClientAuthProvider jwtMqttClientAuthProvider;
    private final MqttAuthProviderService mqttAuthProviderService;

    @PostConstruct
    public void init() {
        List<MqttAuthProvider> providers = mqttAuthProviderService.getAuthProviders(new PageLink(10)).getData();
        if (providers.isEmpty()) {
            return;
        }
        for (MqttAuthProvider provider : providers) {
            switch (provider.getType()) {
                case BASIC -> basicAuthEnabled = provider.isEnabled();
                case X_509 -> sslAuthEnabled = provider.isEnabled();
                case JWT -> jwtAuthEnabled = provider.isEnabled();
                case SCRAM -> enhancedAuthEnabled = provider.isEnabled();
            }
        }
        log.info("Initialized auth provider states: BASIC={}, X_509={}, JWT={}", basicAuthEnabled, sslAuthEnabled, jwtAuthEnabled);
    }

    @Override
    public boolean isBasicEnabled() {
        return basicAuthEnabled;
    }

    @Override
    public BasicMqttClientAuthProvider getBasicProvider() {
        return basicMqttClientAuthProvider;
    }

    @Override
    public boolean isSslEnabled() {
        return sslAuthEnabled;
    }

    @Override
    public SslMqttClientAuthProvider getSslProvider() {
        return sslMqttClientAuthProvider;
    }

    @Override
    public boolean isJwtEnabled() {
        return jwtAuthEnabled;
    }

    @Override
    public JwtMqttClientAuthProvider getJwtProvider() {
        return jwtMqttClientAuthProvider;
    }

    @Override
    public boolean isEnhancedAuthEnabled() {
        return enhancedAuthEnabled;
    }

    @Override
    public void handleProviderNotification(MqttAuthProviderProto notification) {
        log.trace("Received MQTT authentication provider notification: {}", notification);
        MqttAuthProviderType type = MqttAuthProviderType.fromProtoNumber(notification.getProviderType().getNumber());
        switch (notification.getEventType()) {
            case PROVIDER_UPDATED -> {
                switch (type) {
                    case BASIC -> {
                        basicAuthEnabled = notification.getEnabled();
                        basicMqttClientAuthProvider.onConfigurationUpdate(notification.getConfiguration());
                    }
                    case X_509 -> {
                        sslAuthEnabled = notification.getEnabled();
                        sslMqttClientAuthProvider.onConfigurationUpdate(notification.getConfiguration());
                    }
                    case JWT -> {
                        jwtAuthEnabled = notification.getEnabled();
                        jwtMqttClientAuthProvider.onConfigurationUpdate(notification.getConfiguration());
                    }
                    case SCRAM -> enhancedAuthEnabled = notification.getEnabled();
                }

            }
            case PROVIDER_ENABLED, PROVIDER_DISABLED -> {
                boolean enabled = notification.getEventType() == MqttAuthProviderEventProto.PROVIDER_ENABLED;
                switch (type) {
                    case BASIC -> basicAuthEnabled = enabled;
                    case X_509 -> sslAuthEnabled = enabled;
                    case JWT -> jwtAuthEnabled = enabled;
                    case SCRAM -> enhancedAuthEnabled = enabled;
                }
            }
        }

    }
}