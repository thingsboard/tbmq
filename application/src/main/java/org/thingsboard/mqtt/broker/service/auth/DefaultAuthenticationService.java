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

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.exception.AuthenticationException;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthContext;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthResponse;
import org.thingsboard.mqtt.broker.service.auth.providers.MqttClientAuthProviderManager;
import org.thingsboard.mqtt.broker.service.auth.providers.MqttClientAuthProvider;

import java.util.Map;

@Slf4j
@Service
public class DefaultAuthenticationService implements AuthenticationService {

    private final Map<MqttAuthProviderType, MqttClientAuthProvider> authProviders;

    public DefaultAuthenticationService(MqttClientAuthProviderManager authProviderManager) {
        this.authProviders = authProviderManager.getActiveAuthProviders();
    }

    @Override
    public AuthResponse authenticate(AuthContext authContext, boolean useListenerBasedProviderOnly) {
        if (authProviders.isEmpty()) {
            return AuthResponse.defaultAuthResponse();
        }

        try {
            return useListenerBasedProviderOnly ?
                    authenticateWithSingleStrategy(authContext) :
                    authenticateWithBothStrategies(authContext);
        } catch (Exception e) {
            return AuthResponse.failure(e.getMessage());
        }
    }

    private AuthResponse authenticateWithBothStrategies(AuthContext authContext) throws AuthenticationException {
        var basicAuthResponse = authenticate(MqttAuthProviderType.BASIC, authContext);
        if (basicAuthResponse.isSuccess()) {
            return basicAuthResponse;
        }
        if (authContext.isNotSecurePortUsed()) {
            return basicAuthResponse;
        }
        var sslAuthResponse = authenticate(MqttAuthProviderType.X_509, authContext);
        if (sslAuthResponse.isSuccess()) {
            return sslAuthResponse;
        }
        return AuthResponse.failure(basicAuthResponse.getReason() + " | " + sslAuthResponse.getReason());
    }

    private AuthResponse authenticateWithSingleStrategy(AuthContext authContext) throws AuthenticationException {
        MqttAuthProviderType providerType = getAuthProviderType(authContext);
        return authenticate(providerType, authContext);
    }

    private AuthResponse authenticate(MqttAuthProviderType type, AuthContext authContext) throws AuthenticationException {
        var authProvider = authProviders.get(type);
        return authProvider != null ?
                authProvider.authenticate(authContext) :
                AuthResponse.failure(type + " authentication is disabled!");
    }

    private MqttAuthProviderType getAuthProviderType(AuthContext authContext) {
        return authContext.isSecurePortUsed() ? MqttAuthProviderType.X_509 : MqttAuthProviderType.BASIC;
    }

}