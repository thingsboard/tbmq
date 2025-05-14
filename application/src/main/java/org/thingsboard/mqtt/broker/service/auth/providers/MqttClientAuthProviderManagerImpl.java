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
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderDto;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.security.basic.BasicAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.jwt.JwtAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.ssl.SslAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttClientAuthProviderService;
import org.thingsboard.mqtt.broker.gen.queue.MqttAuthProviderProto;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MqttClientAuthProviderManagerImpl implements MqttClientAuthProviderManager {

    // TODO: we have only 3 possible providers at the current stage. No need to try fetch more
    private static final PageLink DEFAULT_PAGE_LINK = new PageLink(3, 0);

    private final MqttClientAuthProviderService mqttClientAuthProviderService;
    private final MqttClientAuthProviderFactory mqttClientAuthProviderFactory;

    private volatile BasicMqttClientAuthProvider basicProvider;
    private volatile SslMqttClientAuthProvider sslProvider;
    private volatile JwtMqttClientAuthProvider jwtProvider;

    @PostConstruct
    public void init() {
        PageData<MqttAuthProviderDto> enabledAuthProvidersDto = mqttClientAuthProviderService.getEnabledAuthProviders(DEFAULT_PAGE_LINK);
        if (enabledAuthProvidersDto == null || enabledAuthProvidersDto.getData().isEmpty()) {
            log.info("MQTT Authentication options are disabled!");
            return;
        }
        for (var dto : enabledAuthProvidersDto.getData()) {
            try {
                switch (dto.getType()) {
                    case BASIC -> basicProvider = mqttClientAuthProviderFactory.createBasicProvider((BasicAuthProviderConfiguration) dto.getConfiguration());
                    case SSL -> sslProvider = mqttClientAuthProviderFactory.createSslProvider((SslAuthProviderConfiguration) dto.getConfiguration());
                    case JWT -> jwtProvider = mqttClientAuthProviderFactory.createJwtProvider((JwtAuthProviderConfiguration) dto.getConfiguration());
                    default -> throw new IllegalStateException("Unexpected MQTT client auth provider type: " + dto.getType());
                }
            } catch (Exception ex) {
                // TODO: should we throw an exception here?
                log.error("[{}][{}] Failed to initialize provider: ", dto.getId(), dto.getType(), ex);
            }
        }
    }

    @Override
    public boolean isAuthEnabled() {
        return isBasicEnabled() || isSslEnabled() || isSslEnabled();
    }

    @Override
    public boolean isJwtEnabled() {
        return jwtProvider != null && jwtProvider.isEnabled();
    }

    @Override
    public boolean isBasicEnabled() {
        return basicProvider != null && basicProvider.isEnabled();
    }

    @Override
    public boolean isSslEnabled() {
        return sslProvider != null && sslProvider.isEnabled();
    }

    @Override
    public boolean isVerifyJwtFirst() {
        return isJwtEnabled() && jwtProvider.getConfiguration().isVerifyJwtFirst();
    }

    @Override
    public JwtMqttClientAuthProvider getJwtMqttClientAuthProvider() {
        return jwtProvider;
    }

    @Override
    public BasicMqttClientAuthProvider getBasicMqttClientAuthProvider() {
        return basicProvider;
    }

    @Override
    public SslMqttClientAuthProvider getSslMqttClientAuthProvider() {
        return sslProvider;
    }

    @Override
    public void handleProviderNotification(MqttAuthProviderProto providerNotificationProto) {
        UUID uuid = toUUID(providerNotificationProto);
        log.trace("Received MQTT Auth provider notification: {}, {}", uuid, providerNotificationProto.getEventType());

        var providerType = MqttClientAuthProviderType.fromProtoNumber(providerNotificationProto.getProviderType().ordinal());

        switch (providerNotificationProto.getEventType()) {

            case PROVIDER_CREATED -> {
                String configStr = providerNotificationProto.getConfiguration();
                boolean enabled = providerNotificationProto.getEnabled();
                switch (providerType) {
                    case BASIC -> basicProvider = mqttClientAuthProviderFactory.createBasicProvider(toBasicAuthProviderConfiguration(configStr), enabled);
                    case SSL -> sslProvider = mqttClientAuthProviderFactory.createSslProvider(toSslAuthProviderConfiguration(configStr), enabled);
                    case JWT -> jwtProvider = mqttClientAuthProviderFactory.createJwtProvider(toJwtAuthProviderConfiguration(configStr), enabled);
                }
            }

            case PROVIDER_UPDATED -> {
                String configStr = providerNotificationProto.getConfiguration();
                switch (providerType) {
                    case BASIC -> basicProvider.updateConfiguration(toBasicAuthProviderConfiguration(configStr));
                    case SSL -> sslProvider.updateConfiguration(toSslAuthProviderConfiguration(configStr));
                    case JWT -> jwtProvider.updateConfiguration(toJwtAuthProviderConfiguration(configStr));
                }
            }

            case PROVIDER_ENABLED -> {
                switch (providerType) {
                    // TODO: handle via ID. Since providerType is uknown on enable.
                    case BASIC -> basicProvider.enable();
                    case SSL -> sslProvider.enable();
                    case JWT -> jwtProvider.enable();
                }
            }

            case PROVIDER_DISABLED -> {
                switch (providerType) {
                    // TODO: handle via ID. Since providerType is uknown on disable.
                    case BASIC -> basicProvider.disable();
                    case SSL -> sslProvider.disable();
                    case JWT -> jwtProvider.disable();
                }
            }
            case PROVIDER_DELETED -> {
                switch (providerType) {
                    // TODO: handle via ID. Since providerType is uknown on delete.
                    case BASIC -> basicProvider = null;
                    case SSL -> sslProvider = null;
                    case JWT -> jwtProvider = null;
                }
            }
        }
    }

    private static BasicAuthProviderConfiguration toBasicAuthProviderConfiguration(String configuration) {
        return JacksonUtil.fromString(configuration, BasicAuthProviderConfiguration.class);
    }

    private static SslAuthProviderConfiguration toSslAuthProviderConfiguration(String configuration) {
        return JacksonUtil.fromString(configuration, SslAuthProviderConfiguration.class);
    }

    private static JwtAuthProviderConfiguration toJwtAuthProviderConfiguration(String configuration) {
        return JacksonUtil.fromString(configuration, JwtAuthProviderConfiguration.class);
    }

    private UUID toUUID(MqttAuthProviderProto notification) {
        return new UUID(notification.getMqttClientAuthProviderIdMSB(), notification.getMqttClientAuthProviderIdLSB());
    }



}
