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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.gen.queue.MqttAuthSettingsProto;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthContext;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthResponse;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultAuthorizationRoutingServiceImpl implements AuthorizationRoutingService {

    private volatile List<MqttAuthProviderType> priorities;
    private volatile boolean useListenerBasedProviderOnly;

    private final AuthenticationService defaultAuthenticationService;
    private final JwtAuthenticationService jwtAuthenticationService;

    @Override
    public void onMqttAuthSettingsUpdate(MqttAuthSettingsProto mqttAuthSettingsProto) {
        priorities = ProtoConverter.fromMqttAuthPriorities(mqttAuthSettingsProto.getPrioritiesList());
        useListenerBasedProviderOnly = mqttAuthSettingsProto.getUseListenerBasedProviderOnly();
    }

    @Override
    public AuthResponse executeAuthFlow(AuthContext authContext) {
        logTraceAuthenticationAttempt(authContext);

        List<String> failureReasons = new ArrayList<>(priorities.size());

        for (MqttAuthProviderType providerType : priorities) {
            AuthResponse response;
            String authType = providerType.getDisplayName();

            switch (providerType) {
                case JWT -> {
                    response = jwtAuthenticationService.authenticate(authContext);
                    authType = providerType.getDisplayName();
                }
                case BASIC, X_509 -> {
                    response = defaultAuthenticationService.authenticate(authContext, useListenerBasedProviderOnly);
                    authType = useListenerBasedProviderOnly ? getBasicOrX509AuthType(authContext) :
                            "Both: " + MqttAuthProviderType.X_509.getDisplayName() + " + " + MqttAuthProviderType.BASIC.getDisplayName();
                }
                default ->
                        response = AuthResponse.failure("[" + authContext.getClientId() + "] " + "Unsupported auth type: " + providerType);
            }
            if (response.isSuccess()) {
                return response;
            }
            addFailureReason(authContext, response, authType, failureReasons);
        }
        return getFinalFailureAuthResponse(authContext, failureReasons);
    }

    private String getBasicOrX509AuthType(AuthContext authContext) {
        return authContext.isSecurePortUsed() ? MqttAuthProviderType.X_509.getDisplayName() : MqttAuthProviderType.BASIC.getDisplayName();
    }

    private void logTraceAuthenticationAttempt(AuthContext authContext) {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Authenticating client", authContext.getClientId());
        }
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
        var re = new RuntimeException(fullReason);
        log.warn("[{}] Failed to authenticate client", authContext.getClientId(), re);
        return AuthResponse.failure(String.join(" | ", failureReasons));
    }

}
