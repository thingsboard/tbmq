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

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.security.scram.internals.ScramSaslServer;
import org.apache.kafka.common.security.scram.internals.ScramSaslServerProvider;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.service.auth.enhanced.EnhancedAuthContext;
import org.thingsboard.mqtt.broker.service.auth.enhanced.EnhancedAuthContinueResponse;
import org.thingsboard.mqtt.broker.service.auth.enhanced.EnhancedAuthFailureReason;
import org.thingsboard.mqtt.broker.service.auth.enhanced.EnhancedAuthFinalResponse;
import org.thingsboard.mqtt.broker.service.auth.enhanced.ScramAuthCallbackHandler;
import org.thingsboard.mqtt.broker.service.auth.enhanced.ScramSaslServerWithCallback;
import org.thingsboard.mqtt.broker.service.security.authorization.AuthRulePatterns;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultEnhancedAuthenticationService implements EnhancedAuthenticationService {

    public static final String SCRAM_SASL_PROTOCOL = "mqtt";
    public static final Map<String, String> SCRAM_SASL_PROPS = Map.of(Sasl.QOP, "auth");

    private final MqttClientCredentialsService credentialsService;
    private final AuthorizationRuleService authorizationRuleService;

    @PostConstruct
    public void init() {
        ScramSaslServerProvider.initialize();
    }

    @Override
    public EnhancedAuthContinueResponse onClientConnectMsg(ClientSessionCtx sessionCtx, EnhancedAuthContext authContext) {
        String clientId = authContext.getClientId();
        String authMethod = authContext.getAuthMethod();
        boolean initiated = initiateScramServerWithCallback(clientId, authMethod, sessionCtx);
        if (!initiated) {
            return EnhancedAuthContinueResponse.failure(EnhancedAuthFailureReason.FAILED_TO_INIT_SCRAM_SERVER);
        }
        try {
            byte[] challenge = sessionCtx.getScramSaslServerWithCallback().evaluateResponse(authContext.getAuthData());
            return EnhancedAuthContinueResponse.success(challenge);
        } catch (SaslException e) {
            log.warn("[{}] Failed to evaluate client initial request due to: ", clientId, e);
            return EnhancedAuthContinueResponse.failure(EnhancedAuthFailureReason.CLIENT_FIRST_MESSAGE_EVALUATION_ERROR);
        }
    }

    @Override
    public EnhancedAuthFinalResponse onAuthContinue(ClientSessionCtx sessionCtx, EnhancedAuthContext authContext) {
        try {
            var enhancedAuthResponse = processAuth(sessionCtx, authContext);
            if (enhancedAuthResponse.success()) {
                if (log.isDebugEnabled()) {
                    log.debug("[{}] Enhanced auth completed successfully!", authContext.getClientId());
                }
                return enhancedAuthResponse;
            }
            var enhancedAuthFailureReason = enhancedAuthResponse.enhancedAuthFailureReason();
            if (log.isDebugEnabled()) {
                log.debug("[{}] {}", authContext.getClientId(), enhancedAuthFailureReason.getReasonLog());
            }
            return enhancedAuthResponse;
        } catch (SaslException e) {
            log.warn("[{}] {}", authContext.getClientId(), EnhancedAuthFailureReason.CLIENT_FINAL_MESSAGE_EVALUATION_ERROR.getReasonLog(), e);
            return EnhancedAuthFinalResponse.failure(EnhancedAuthFailureReason.CLIENT_FINAL_MESSAGE_EVALUATION_ERROR);
        }
    }

    @Override
    public EnhancedAuthFinalResponse onReAuthContinue(ClientSessionCtx sessionCtx, EnhancedAuthContext authContext) {
        try {
            var enhancedAuthResponse = processAuth(sessionCtx, authContext);
            if (enhancedAuthResponse.success()) {
                if (log.isDebugEnabled()) {
                    log.debug("[{}] Enhanced re-auth completed successfully!", authContext.getClientId());
                }
                return enhancedAuthResponse;
            }
            var enhancedAuthFailureReason = enhancedAuthResponse.enhancedAuthFailureReason();
            if (log.isDebugEnabled()) {
                log.debug("[{}] {}", authContext.getClientId(), enhancedAuthFailureReason.getReasonLog());
            }
            return enhancedAuthResponse;
        } catch (SaslException e) {
            log.warn("[{}] {}", authContext.getClientId(), EnhancedAuthFailureReason.CLIENT_FINAL_MESSAGE_EVALUATION_ERROR.getReasonLog(), e);
            return EnhancedAuthFinalResponse.failure(EnhancedAuthFailureReason.CLIENT_FINAL_MESSAGE_EVALUATION_ERROR);
        }
    }

    @Override
    public EnhancedAuthContinueResponse onReAuth(ClientSessionCtx sessionCtx, EnhancedAuthContext authContext) {
        String clientId = authContext.getClientId();
        String authMethodFromConnect = sessionCtx.getAuthMethod();
        String authMethod = authContext.getAuthMethod();

        if (authMethodFromConnect == null || !authMethodFromConnect.equals(authMethod)) {
            if (log.isDebugEnabled()) {
                log.debug("[{}] Received AUTH message while authentication method {} mismatch with value from the session ctx {}",
                        clientId, authMethod, authMethodFromConnect);
            }
            return EnhancedAuthContinueResponse.failure(EnhancedAuthFailureReason.AUTH_METHOD_MISMATCH);
        }
        boolean initiated = initiateScramServerWithCallback(clientId, authMethod, sessionCtx);
        if (!initiated) {
            return EnhancedAuthContinueResponse.failure(EnhancedAuthFailureReason.FAILED_TO_INIT_SCRAM_SERVER);
        }
        try {
            byte[] challenge = sessionCtx.getScramSaslServerWithCallback().evaluateResponse(authContext.getAuthData());
            return EnhancedAuthContinueResponse.success(challenge);
        } catch (SaslException e) {
            log.warn("[{}] Failed to evaluate client re-AUTH request due to: ", clientId, e);
            sessionCtx.clearScramServer();
            return EnhancedAuthContinueResponse.failure(EnhancedAuthFailureReason.CLIENT_RE_AUTH_MESSAGE_EVALUATION_ERROR);
        }
    }

    private EnhancedAuthFinalResponse processAuth(ClientSessionCtx sessionCtx, EnhancedAuthContext authContext) throws SaslException {
        if (sessionCtx.getAuthMethod() == null) {
            return EnhancedAuthFinalResponse.failure(EnhancedAuthFailureReason.MISSING_AUTH_METHOD);
        }
        if (!sessionCtx.getAuthMethod().equals(authContext.getAuthMethod())) {
            return EnhancedAuthFinalResponse.failure(EnhancedAuthFailureReason.AUTH_METHOD_MISMATCH);
        }
        if (authContext.getAuthData() == null) {
            return EnhancedAuthFinalResponse.failure(EnhancedAuthFailureReason.MISSING_AUTH_DATA);
        }
        if (sessionCtx.getScramSaslServerWithCallback() == null) {
            return EnhancedAuthFinalResponse.failure(EnhancedAuthFailureReason.MISSING_SCRAM_SERVER);
        }
        var server = sessionCtx.getScramSaslServerWithCallback();
        byte[] response = server.evaluateResponse(authContext.getAuthData());
        if (!server.isComplete()) {
            return EnhancedAuthFinalResponse.failure(EnhancedAuthFailureReason.AUTH_CHALLENGE_FAILED);
        }
        List<AuthRulePatterns> authRulePatterns = List.of(server.getAuthRulePatterns());
        ClientType clientType = server.getClientType();
        return EnhancedAuthFinalResponse.success(clientType, authRulePatterns, response);
    }

    private boolean initiateScramServerWithCallback(String clientId, String authMethod, ClientSessionCtx sessionCtx) {
        var callbackHandler = new ScramAuthCallbackHandler(credentialsService, authorizationRuleService);
        SaslServer saslServer;
        try {
            saslServer = createSaslServer(authMethod, callbackHandler);
            if (saslServer == null) {
                if (log.isDebugEnabled()) {
                    log.debug("[{}] SASL server is null!", clientId);
                }
                return false;
            }
            if (!(saslServer instanceof ScramSaslServer)) {
                if (log.isDebugEnabled()) {
                    log.debug("[{}] {} SASL server does not supported! Only ScramSaslServer is supported!", clientId, saslServer.getClass().getName());
                }
                return false;
            }
        } catch (SaslException e) {
            log.warn("[{}] Failed to initialize SASL server due to: ", clientId, e);
            return false;
        }
        sessionCtx.setScramSaslServerWithCallback(new ScramSaslServerWithCallback(saslServer, callbackHandler));
        return true;
    }

    SaslServer createSaslServer(String authMethod, ScramAuthCallbackHandler callbackHandler) throws SaslException {
        return Sasl.createSaslServer(authMethod, SCRAM_SASL_PROTOCOL, null, SCRAM_SASL_PROPS, callbackHandler);
    }

}
