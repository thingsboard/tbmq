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
import org.thingsboard.mqtt.broker.gen.queue.MqttAuthSettingsProto;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthContext;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthResponse;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultAuthorizationRoutingServiceImpl implements AuthorizationRoutingService {

    private volatile boolean jwtFirst;
    private volatile boolean useListenerBasedProviderOnly;

    private final AuthenticationService defaultAuthenticationService;
    private final JwtAuthenticationService jwtAuthenticationService;


    @Override
    public void onMqttAuthSettingsUpdate(MqttAuthSettingsProto mqttAuthSettingsProto) {
        jwtFirst = mqttAuthSettingsProto.getJwtFirst();
        useListenerBasedProviderOnly = mqttAuthSettingsProto.getUseListenerBasedProviderOnly();
    }

    @Override
    public AuthResponse executeAuthFlow(AuthContext authContext) {
        logTraceAuthenticationAttempt(authContext);

        List<String> failureReasons = new ArrayList<>(3);

        if (jwtFirst) {
            AuthResponse jwtAuthResponse = jwtAuthenticationService.authenticate(authContext);
            if (jwtAuthResponse.isSuccess()) {
                return jwtAuthResponse;
            }
            addFailureReason(authContext, jwtAuthResponse, "JWT", failureReasons);
        }

        AuthResponse basicOrSslAuthResponse = defaultAuthenticationService.authenticate(authContext, useListenerBasedProviderOnly);
        if (basicOrSslAuthResponse.isSuccess()) {
            return basicOrSslAuthResponse;
        }
        String listenerBased = authContext.isSecurePortUsed() ? "X.509 Certificate chain" : "Basic";
        String authType = useListenerBasedProviderOnly ? listenerBased : "Both: X.509 Certificate chain + Basic";
        addFailureReason(authContext, basicOrSslAuthResponse, authType, failureReasons);

        if (!jwtFirst) {
            AuthResponse jwtAuthResponse = jwtAuthenticationService.authenticate(authContext);
            if (jwtAuthResponse.isSuccess()) {
                return jwtAuthResponse;
            }
            addFailureReason(authContext, jwtAuthResponse, "JWT", failureReasons);
        }
        return getFinalFailureAuthResponse(authContext, failureReasons);
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
