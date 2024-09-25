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

import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttReasonCodes;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.security.scram.internals.ScramSaslServer;
import org.apache.kafka.common.security.scram.internals.ScramSaslServerProvider;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.util.BrokerConstants;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.service.auth.enhanced.EnhancedAuthContext;
import org.thingsboard.mqtt.broker.service.auth.enhanced.EnhancedAuthFailureReason;
import org.thingsboard.mqtt.broker.service.auth.enhanced.EnhancedAuthResponse;
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
    public boolean onClientConnectMsg(ClientSessionCtx sessionCtx, EnhancedAuthContext authContext) {
        String clientId = authContext.getClientId();
        String authMethod = authContext.getAuthMethod();
        boolean initiated = initiateScramServerWithCallback(clientId, authMethod, sessionCtx);
        if (!initiated) {
            return false;
        }
        byte[] challenge;
        try {
            challenge = sessionCtx.getScramSaslServerWithCallback().evaluateResponse(authContext.getAuthData());
        } catch (SaslException e) {
            log.warn("[{}] Failed to evaluate client initial request due to: ", clientId, e);
            return false;
        }
        sendAuthChallengeToClient(sessionCtx, authMethod, challenge, MqttReasonCodes.Auth.CONTINUE_AUTHENTICATION);
        return true;
    }

    @Override
    public EnhancedAuthResponse onAuthContinue(ClientSessionCtx sessionCtx, EnhancedAuthContext authContext) {
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
            log.warn("[{}] {}", authContext.getClientId(), EnhancedAuthFailureReason.EVALUATION_ERROR.getReasonLog(), e);
            return EnhancedAuthResponse.failure(EnhancedAuthFailureReason.EVALUATION_ERROR);
        }
    }

    @Override
    public EnhancedAuthResponse onReAuthContinue(ClientSessionCtx sessionCtx, EnhancedAuthContext authContext) {
        try {
            var enhancedAuthResponse = processAuth(sessionCtx, authContext);
            if (enhancedAuthResponse.success()) {
                sendAuthChallengeToClient(sessionCtx, authContext.getAuthMethod(),
                        enhancedAuthResponse.response(), MqttReasonCodes.Auth.SUCCESS);
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
            log.warn("[{}] {}", authContext.getClientId(), EnhancedAuthFailureReason.EVALUATION_ERROR.getReasonLog(), e);
            return EnhancedAuthResponse.failure(EnhancedAuthFailureReason.EVALUATION_ERROR);
        }
    }

    @Override
    public boolean onReAuth(ClientSessionCtx sessionCtx, EnhancedAuthContext authContext) {
        String clientId = authContext.getClientId();
        String authMethodFromConnect = sessionCtx.getAuthMethod();
        String authMethod = authContext.getAuthMethod();

        if (authMethodFromConnect == null || !authMethodFromConnect.equals(authMethod)) {
            if (log.isDebugEnabled()) {
                log.debug("[{}] Received AUTH message while authentication method {} mismatch with value from the session ctx {}",
                        clientId, authMethod, authMethodFromConnect);
            }
            return false;
        }
        boolean initiated = initiateScramServerWithCallback(clientId, authMethod, sessionCtx);
        if (!initiated) {
            return false;
        }
        byte[] challenge;
        try {
            challenge = sessionCtx.getScramSaslServerWithCallback().evaluateResponse(authContext.getAuthData());
        } catch (SaslException e) {
            log.warn("[{}] Failed to evaluate client re-AUTH request due to: ", clientId, e);
            sessionCtx.clearScramServer();
            return false;
        }
        sendAuthChallengeToClient(sessionCtx, authMethod, challenge, MqttReasonCodes.Auth.CONTINUE_AUTHENTICATION);
        return true;
    }

    private EnhancedAuthResponse processAuth(ClientSessionCtx sessionCtx, EnhancedAuthContext authContext) throws SaslException {
        if (sessionCtx.getAuthMethod() == null) {
            return EnhancedAuthResponse.failure(EnhancedAuthFailureReason.MISSING_AUTH_METHOD);
        }
        if (!sessionCtx.getAuthMethod().equals(authContext.getAuthMethod())) {
            return EnhancedAuthResponse.failure(EnhancedAuthFailureReason.AUTH_METHOD_MISMATCH);
        }
        if (authContext.getAuthData() == null) {
            return EnhancedAuthResponse.failure(EnhancedAuthFailureReason.MISSING_AUTH_DATA);
        }
        if (sessionCtx.getScramSaslServerWithCallback() == null) {
            return EnhancedAuthResponse.failure(EnhancedAuthFailureReason.MISSING_SCRAM_SERVER);
        }
        var server = sessionCtx.getScramSaslServerWithCallback();
        byte[] response = server.evaluateResponse(authContext.getAuthData());
        if (!server.isComplete()) {
            return EnhancedAuthResponse.failure(EnhancedAuthFailureReason.AUTH_CHALLENGE_FAILED);
        }
        List<AuthRulePatterns> authRulePatterns = List.of(server.getAuthRulePatterns());
        ClientType clientType = server.getClientType();
        return EnhancedAuthResponse.success(clientType, authRulePatterns, response);
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

    private void sendAuthChallengeToClient(ClientSessionCtx ctx, String authMethod, byte[] response, MqttReasonCodes.Auth authReasonCode) {
        var properties = new MqttProperties();
        var methodProperty = new MqttProperties.StringProperty(BrokerConstants.AUTHENTICATION_METHOD_PROP_ID, authMethod);
        var dataProperty = new MqttProperties.BinaryProperty(BrokerConstants.AUTHENTICATION_DATA_PROP_ID, response);
        properties.add(methodProperty);
        properties.add(dataProperty);
        MqttMessage message = MqttMessageBuilders.auth()
                .properties(properties)
                .reasonCode(authReasonCode.byteValue())
                .build();
        ctx.getChannel().writeAndFlush(message);
    }

}
