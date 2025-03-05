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

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
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

    @Setter
    @Value("${security.mqtt.auth_strategy:BOTH}")
    private AuthStrategy authStrategy;

    public DefaultAuthenticationService(MqttClientAuthProviderManager authProviderManager) {
        this.authProviders = authProviderManager.getActiveAuthProviders();
    }

    @Override
    public AuthResponse authenticate(AuthContext authContext) throws AuthenticationException {
        logAuthenticationAttempt(authContext);

        if (authProviders.isEmpty()) {
            return AuthResponse.defaultAuthResponse();
        }

        try {
            return AuthStrategy.BOTH == authStrategy ?
                    authenticateWithBothStrategies(authContext) :
                    authenticateWithSingleStrategy(authContext);
        } catch (Exception e) {
            throw newAuthenticationException(authContext, e);
        }
    }

    private void logAuthenticationAttempt(AuthContext authContext) {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Authenticating client", authContext.getClientId());
        }
    }

    private AuthResponse authenticateWithBothStrategies(AuthContext authContext) throws AuthenticationException {
        var basicAuthResponse = authenticate(AuthProviderType.BASIC, authContext);
        if (isAuthSuccessful(basicAuthResponse)) {
            return basicAuthResponse;
        }

        String basicAuthFailureReason = getBasicAuthFailureReason(basicAuthResponse);
        if (authContext.isTlsDisabled()) {
            return AuthResponse.failure(basicAuthFailureReason);
        }

        var sslAuthResponse = authenticate(AuthProviderType.X_509_CERTIFICATE_CHAIN, authContext);
        return processSslAuthResponse(sslAuthResponse, basicAuthFailureReason);
    }

    private AuthResponse authenticateWithSingleStrategy(AuthContext authContext) throws AuthenticationException {
        var authResponse = authenticateBySingleAuthProvider(authContext);

        if (authResponse == null) {
            throwAuthenticationExceptionForSingleStrategy(authContext);
        }

        return authResponse;
    }

    private AuthResponse authenticateBySingleAuthProvider(AuthContext authContext) throws AuthenticationException {
        AuthProviderType providerType = getAuthProviderType(authContext);
        return authenticate(providerType, authContext);
    }

    private void throwAuthenticationExceptionForSingleStrategy(AuthContext authContext) throws AuthenticationException {
        String providerType = getAuthProviderType(authContext).name();
        String errorMsg = String.format("Failed to authenticate client, %s authentication is disabled!", providerType);
        throw new AuthenticationException(errorMsg);
    }

    private AuthResponse processSslAuthResponse(AuthResponse authResponse, String basicAuthFailureReason) throws AuthenticationException {
        if (authResponse == null) {
            throw new AuthenticationException(basicAuthFailureReason + ". X_509_CERTIFICATE_CHAIN authentication is disabled!");
        }

        if (authResponse.isFailure()) {
            String errorMsg = basicAuthFailureReason + ". " + authResponse.getReason();
            return authResponse.toBuilder().reason(errorMsg).build();
        }

        return authResponse;
    }

    private String getBasicAuthFailureReason(AuthResponse authResponse) {
        return authResponse == null ? "BASIC authentication is disabled" : authResponse.getReason();
    }

    private AuthResponse authenticate(AuthProviderType type, AuthContext authContext) throws AuthenticationException {
        var authProvider = authProviders.get(type);
        return authProvider != null ? authProvider.authenticate(authContext) : null;
    }

    private AuthProviderType getAuthProviderType(AuthContext authContext) {
        return authContext.isTlsEnabled() ? AuthProviderType.X_509_CERTIFICATE_CHAIN : AuthProviderType.BASIC;
    }

    private boolean isAuthSuccessful(AuthResponse authResponse) {
        return authResponse != null && authResponse.isSuccess();
    }

    private AuthenticationException newAuthenticationException(AuthContext authContext, Exception e) {
        log.warn("[{}] Failed to authenticate client", authContext.getClientId(), e);
        return new AuthenticationException("Exception on client authentication: " + e.getMessage());
    }

}
