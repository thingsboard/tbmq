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
import org.thingsboard.mqtt.broker.exception.AuthenticationException;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthContext;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthResponse;
import org.thingsboard.mqtt.broker.service.auth.providers.MqttClientAuthProviderManager;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultAuthenticationService implements AuthenticationService {

    private final MqttClientAuthProviderManager authProviderManager;

    // TODO: check for backward compatibility, when Auth strategy: SINGLE, BOTH were used.
    // TODO: Uncomment test: DefaultAuthenticationServiceTest
    @Override
    public AuthResponse authenticate(AuthContext authContext) throws AuthenticationException {
        logAuthenticationAttempt(authContext);

        if (!authProviderManager.isAuthEnabled()) {
            return AuthResponse.defaultAuthResponse();
        }

        List<String> failureReasons = new ArrayList<>(3);

        // JWT first
        if (authProviderManager.isJwtEnabled() && authProviderManager.isVerifyJwtFirst()) {
            AuthResponse jwtResponse = authProviderManager.getJwtMqttClientAuthProvider().authenticate(authContext);
            if (jwtResponse.isSuccess()) {
                return jwtResponse;
            }
            failureReasons.add("JWT: " + jwtResponse.getReason());
        }

        // BASIC
        if (authProviderManager.isBasicEnabled()) {
            AuthResponse basicResponse = authProviderManager.getBasicMqttClientAuthProvider().authenticate(authContext);
            if (basicResponse.isSuccess()) {
                return basicResponse;
            }
            failureReasons.add("BASIC: " + basicResponse.getReason());
        }

        // SSL
        if (authProviderManager.isSslEnabled()) {
            AuthResponse sslResponse = authProviderManager.getSslMqttClientAuthProvider().authenticate(authContext);
            if (sslResponse.isSuccess()) {
                return sslResponse;
            }
            failureReasons.add("SSL: " + sslResponse.getReason());
        }

        // JWT last
        if (authProviderManager.isJwtEnabled() && !authProviderManager.isVerifyJwtFirst()) {
            AuthResponse jwtResponse = authProviderManager.getJwtMqttClientAuthProvider().authenticate(authContext);
            if (jwtResponse.isSuccess()) {
                return jwtResponse;
            }
            failureReasons.add("JWT: " + jwtResponse.getReason());
        }

        // Everything failed
        throw onAuthFailure(authContext, failureReasons);
    }

    private void logAuthenticationAttempt(AuthContext authContext) {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Authenticating client", authContext.getClientId());
        }
    }

    private AuthenticationException onAuthFailure(AuthContext authContext, List<String> failureReasons) {
        String fullReason = String.join(" | ", failureReasons);
        var re = new RuntimeException("Authentication failed: " + fullReason);
        log.warn("[{}] Failed to authenticate client", authContext.getClientId(), re);
        return new AuthenticationException("Exception on client authentication: " + re.getMessage());
    }

}
