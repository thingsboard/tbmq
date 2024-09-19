/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
                String basicAuthFailureReason = null;
                for (var authProviderType : AuthProviderType.values()) {
                    var authResponse = authenticate(authProviderType, authContext);

                    if (authProviderType.equals(AuthProviderType.BASIC)) {
                        basicAuthFailureReason = getBasicAuthFailureReason(authResponse);
                        if (authResponse != null && authResponse.isSuccess()) {
                            return authResponse;
                        }
                    } else {
                        return authenticateBySslAuthOnBothAuthStrategy(authResponse, basicAuthFailureReason);
                    }
                }
            } else {
                var authResponse = authenticateBySingleAuthProvider(authContext);
                if (authResponse != null) {
                    return authResponse;
                } else {
                    prepareAndThrowSingleAuthException(authContext);
                }
            }
        } catch (Exception e) {
            log.warn("[{}] Failed to authenticate client", authContext.getClientId(), e);
            String errorMsg = "Exception on client authentication: " + e.getMessage();
            throw new AuthenticationException(errorMsg);
        }
        throw new AuthenticationException("Failed to authenticate client");
    }

    private String getBasicAuthFailureReason(AuthResponse authResponse) {
        if (authResponse == null) {
            return "BASIC authentication is disabled";
        } else {
            if (authResponse.isSuccess()) {
                return null;
            } else {
                return authResponse.getReason();
            }
        }
    }

    private AuthResponse authenticateBySslAuthOnBothAuthStrategy(AuthResponse authResponse, String basicAuthFailureReason) throws AuthenticationException {
        if (authResponse == null) {
            String errorMsg = basicAuthFailureReason + ". X_509_CERTIFICATE_CHAIN authentication is disabled!";
            throw new AuthenticationException(errorMsg);
        } else {
            if (authResponse.isSuccess()) {
                return authResponse;
            } else {
                String errorMsg = basicAuthFailureReason + ". " + authResponse.getReason();
                return authResponse.toBuilder().reason(errorMsg).build();
            }
        }
    }

    private AuthResponse authenticateBySingleAuthProvider(AuthContext authContext) throws AuthenticationException {
        return authContext.isTlsEnabled() ?
                authenticate(AuthProviderType.X_509_CERTIFICATE_CHAIN, authContext) :
                authenticate(AuthProviderType.BASIC, authContext);
    }

    private void prepareAndThrowSingleAuthException(AuthContext authContext) throws AuthenticationException {
        String providerType = getAuthProviderType(authContext);
        String errorMsg = String.format("Failed to authenticate client, %s authentication is disabled!", providerType);
        throw new AuthenticationException(errorMsg);
    }

    private AuthResponse authenticate(AuthProviderType type, AuthContext authContext) throws AuthenticationException {
        var authProvider = authProviders.get(type);
        if (authProvider != null) {
            return authProvider.authenticate(authContext);
        }
        return null;
    }

    private String getAuthProviderType(AuthContext authContext) {
        return authContext.isTlsEnabled() ? AuthProviderType.X_509_CERTIFICATE_CHAIN.name() : AuthProviderType.BASIC.name();
    }
}
