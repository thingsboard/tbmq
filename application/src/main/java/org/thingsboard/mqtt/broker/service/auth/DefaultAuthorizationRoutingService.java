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

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.common.data.SysAdminSettingType;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsService;
import org.thingsboard.mqtt.broker.gen.queue.MqttAuthSettingsProto;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthContext;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthResponse;
import org.thingsboard.mqtt.broker.service.auth.providers.basic.BasicMqttClientAuthProvider;
import org.thingsboard.mqtt.broker.service.auth.providers.jwt.JwtMqttClientAuthProvider;
import org.thingsboard.mqtt.broker.service.auth.providers.ssl.SslMqttClientAuthProvider;
import org.thingsboard.mqtt.broker.service.install.data.MqttAuthSettings;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultAuthorizationRoutingService implements AuthorizationRoutingService {

    private final BasicMqttClientAuthProvider basicMqttClientAuthProvider;
    private final SslMqttClientAuthProvider sslMqttClientAuthProvider;
    private final JwtMqttClientAuthProvider jwtMqttClientAuthProvider;

    private final AdminSettingsService adminSettingsService;

    private volatile List<MqttAuthProviderType> priorities;

    @PostConstruct
    public void init() {
        AdminSettings mqttAuthorization = adminSettingsService.findAdminSettingsByKey(SysAdminSettingType.MQTT_AUTHORIZATION.getKey());
        if (mqttAuthorization == null) {
            priorities = MqttAuthProviderType.defaultPriorityList;
            log.warn("Failed to find MQTT authorization settings. Going to use default authentication execution order {}", priorities);
            return;
        }
        MqttAuthSettings mqttAuthSettings = MqttAuthSettings.fromJsonValue(mqttAuthorization.getJsonValue());
        priorities = getPriorities(mqttAuthSettings.getPriorities());
    }

    @Override
    public void onMqttAuthSettingsUpdate(MqttAuthSettingsProto mqttAuthSettingsProto) {
        priorities = getPriorities(ProtoConverter.fromMqttAuthPriorities(mqttAuthSettingsProto.getPrioritiesList()));
    }

    @Override
    public AuthResponse executeAuthFlow(AuthContext authContext) {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Authenticating client", authContext.getClientId());
        }

        if (!defaultProvidersEnabled()) {
            return AuthResponse.defaultAuthResponse();
        }

        List<String> failureReasons = new ArrayList<>(priorities.size());

        for (MqttAuthProviderType providerType : priorities) {
            AuthResponse response = switch (providerType) {
                case JWT -> jwtMqttClientAuthProvider.authenticate(authContext);
                case MQTT_BASIC -> basicMqttClientAuthProvider.authenticate(authContext);
                case X_509 -> sslMqttClientAuthProvider.authenticate(authContext);
                default -> throw new IllegalStateException("Unexpected provider type: " + providerType);
            };
            if (response.isSuccess()) {
                return response;
            }
            addFailureReason(authContext, response, providerType.getDisplayName(), failureReasons);
        }
        return getFinalFailureAuthResponse(authContext, failureReasons);
    }

    private boolean defaultProvidersEnabled() {
        return basicMqttClientAuthProvider.isEnabled() ||
               sslMqttClientAuthProvider.isEnabled() ||
               jwtMqttClientAuthProvider.isEnabled();
    }

    private void addFailureReason(AuthContext authContext, AuthResponse response, String authType, List<String> failureReasons) {
        String reason = response.getReason();
        if (log.isDebugEnabled()) {
            log.debug("[{}] {} authentication failed: {}", authContext.getClientId(), authType, reason);
        }
        failureReasons.add(reason);
    }

    private AuthResponse getFinalFailureAuthResponse(AuthContext authContext, List<String> failureReasons) {
        String fullReason = String.join(" | ", failureReasons);
        log.warn("[{}] Failed to authenticate client: {}", authContext.getClientId(), fullReason);
        return AuthResponse.failure(fullReason);
    }

    private List<MqttAuthProviderType> getPriorities(List<MqttAuthProviderType> mqttAuthPriorities) {
        return mqttAuthPriorities.stream()
                .filter(providerType -> providerType != MqttAuthProviderType.SCRAM)
                .toList();
    }

}
