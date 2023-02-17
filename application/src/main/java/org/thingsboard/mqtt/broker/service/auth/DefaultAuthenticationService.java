/**
 * Copyright © 2016-2023 The Thingsboard Authors
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

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.exception.AuthenticationException;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthContext;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthProviderType;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthResponse;
import org.thingsboard.mqtt.broker.service.auth.providers.MqttClientAuthProvider;
import org.thingsboard.mqtt.broker.service.auth.providers.MqttClientAuthProviderManager;

import java.util.Map;

@Slf4j
@Service
public class DefaultAuthenticationService implements AuthenticationService {

    private final Map<AuthProviderType, MqttClientAuthProvider> authProviders;

    public DefaultAuthenticationService(MqttClientAuthProviderManager authProviderManager) {
        this.authProviders = authProviderManager.getActiveAuthProviders();
    }

    @Setter
    @Value("${security.mqtt.auth_strategy:BOTH}")
    private AuthStrategy authStrategy;

    @Override
    public AuthResponse authenticate(AuthContext authContext) throws AuthenticationException {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Authenticating client", authContext.getClientId());
        }
        if (authProviders.isEmpty()) {
            return new AuthResponse(true, ClientType.DEVICE, null);
        }
        try {
            if (AuthStrategy.BOTH == authStrategy) {
                for (var authProviderType : AuthProviderType.values()) {
                    var authResponse = authenticate(authProviderType, authContext);
                    if (authResponse != null) {
                        return authResponse;
                    }
                }
            } else {
                var authResponse = authContext.getSslHandler() != null ?
                        authenticate(AuthProviderType.X_509_CERTIFICATE_CHAIN, authContext) :
                        authenticate(AuthProviderType.BASIC, authContext);
                if (authResponse != null) {
                    return authResponse;
                }
            }
        } catch (Exception e) {
            log.warn("[{}] Failed to authenticate client.", authContext.getClientId(), e);
            throw new AuthenticationException("Exception on client authentication");
        }
        throw new AuthenticationException("Failed to authenticate client");
    }

    private AuthResponse authenticate(AuthProviderType type, AuthContext authContext) throws AuthenticationException {
        var authProvider = authProviders.get(type);
        if (authProvider != null) {
            var authResponse = authProvider.authenticate(authContext);
            if (authResponse.isSuccess()) {
                return authResponse;
            }
        }
        return null;
    }
}
