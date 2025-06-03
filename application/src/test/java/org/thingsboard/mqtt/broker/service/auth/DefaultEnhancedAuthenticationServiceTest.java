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

import org.apache.kafka.common.security.scram.internals.ScramSaslServer;
import org.apache.kafka.common.security.scram.internals.ScramSaslServerProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.client.credentials.ScramAlgorithm;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttAuthProviderService;
import org.thingsboard.mqtt.broker.service.auth.enhanced.EnhancedAuthContext;
import org.thingsboard.mqtt.broker.service.auth.enhanced.EnhancedAuthFailure;
import org.thingsboard.mqtt.broker.service.auth.enhanced.EnhancedAuthFinalResponse;
import org.thingsboard.mqtt.broker.service.auth.enhanced.ScramServerWithCallbackHandler;
import org.thingsboard.mqtt.broker.service.security.authorization.AuthRulePatterns;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import java.nio.charset.StandardCharsets;
import java.security.Provider;
import java.security.Security;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DefaultEnhancedAuthenticationServiceTest {

    private static final String CLIENT_ID = "clientId";

    DefaultEnhancedAuthenticationService enhancedAuthenticationService;

    @Before
    public void setUp() {
        MqttClientCredentialsService credentialsServiceMock = mock(MqttClientCredentialsService.class);
        AuthorizationRuleService authorizationRuleServiceMock = mock(AuthorizationRuleService.class);
        MqttAuthProviderService mqttAuthProviderService = mock(MqttAuthProviderService.class);
        enhancedAuthenticationService = spy(new DefaultEnhancedAuthenticationService(credentialsServiceMock, authorizationRuleServiceMock, mqttAuthProviderService));
        ReflectionTestUtils.setField(enhancedAuthenticationService, "enabled", true);
    }

    @Test
    public void verifyScramSaslServerProviderInitiated() {
        // GIVEN-WHEN
        enhancedAuthenticationService.init();

        // THEN
        Provider[] providers = Security.getProviders();
        boolean providerFound = Arrays.stream(providers)
                .anyMatch(provider -> provider.getClass().equals(ScramSaslServerProvider.class));
        assertThat(providerFound).isTrue();
    }

    @Test
    public void givenScramServerInitiated_whenOnClientConnectMsgEvalSuccess_thenVerifyInvocations() throws Exception {
        // GIVEN
        var enhancedAuthContext = getEnhancedAuthContext();
        enhancedAuthenticationService.init();

        var scramSaslServer = mock(ScramSaslServer.class);
        var scramSaslServerWithCallbackMock = mock(ScramServerWithCallbackHandler.class);
        var clientSessionCtxMock = mock(ClientSessionCtx.class);

        when(clientSessionCtxMock.getScramServerWithCallbackHandler()).thenReturn(scramSaslServerWithCallbackMock);
        doReturn(scramSaslServer).when(enhancedAuthenticationService).createSaslServer(any(), any());

        // WHEN
        var enhancedAuthContinueResponse = enhancedAuthenticationService.onClientConnectMsg(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(enhancedAuthContinueResponse.success()).isTrue();
        verify(clientSessionCtxMock).setScramServerWithCallbackHandler(any());
        verify(clientSessionCtxMock).getScramServerWithCallbackHandler();
        verifyNoMoreInteractions(clientSessionCtxMock);
        verify(scramSaslServerWithCallbackMock).evaluateResponse(enhancedAuthContext.getAuthData());
    }

    @Test
    public void givenScramServerInitiated_whenOnClientConnectMsgEvalFailure_thenVerifyInvocations() throws Exception {
        // GIVEN
        var enhancedAuthContext = getEnhancedAuthContext();
        enhancedAuthenticationService.init();

        var scramSaslServer = mock(ScramSaslServer.class);
        var scramSaslServerWithCallbackMock = mock(ScramServerWithCallbackHandler.class);
        var clientSessionCtxMock = mock(ClientSessionCtx.class);

        when(clientSessionCtxMock.getScramServerWithCallbackHandler()).thenReturn(scramSaslServerWithCallbackMock);
        doReturn(scramSaslServer).when(enhancedAuthenticationService).createSaslServer(any(), any());
        doThrow(SaslException.class).when(scramSaslServerWithCallbackMock).evaluateResponse(any());

        // WHEN
        var enhancedAuthContinueResponse = enhancedAuthenticationService.onClientConnectMsg(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(enhancedAuthContinueResponse.success()).isFalse();
        verify(scramSaslServerWithCallbackMock).evaluateResponse(enhancedAuthContext.getAuthData());
        verify(clientSessionCtxMock).setScramServerWithCallbackHandler(any());
        verify(clientSessionCtxMock).getScramServerWithCallbackHandler();
        verifyNoMoreInteractions(clientSessionCtxMock);
    }

    @Test
    public void givenNullScramServer_whenOnClientConnectMsg_thenVerifyScramServerWithCallbackIsNotInitiated() throws Exception {
        // GIVEN
        var enhancedAuthContext = getEnhancedAuthContext();
        var clientSessionCtxMock = mock(ClientSessionCtx.class);
        enhancedAuthenticationService.init();

        doReturn(null).when(enhancedAuthenticationService).createSaslServer(any(), any());

        // WHEN
        var enhancedAuthContinueResponse = enhancedAuthenticationService.onClientConnectMsg(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(enhancedAuthContinueResponse.success()).isFalse();
        verifyNoInteractions(clientSessionCtxMock);
    }

    @Test
    public void givenNotScramServer_whenOnClientConnectMsg_thenVerifyScramServerWithCallbackIsNotInitiated() throws Exception {
        // GIVEN
        var enhancedAuthContext = getEnhancedAuthContext();
        var saslServer = mock(SaslServer.class);
        var clientSessionCtxMock = mock(ClientSessionCtx.class);
        enhancedAuthenticationService.init();

        doReturn(saslServer).when(enhancedAuthenticationService).createSaslServer(any(), any());

        // WHEN
        var enhancedAuthContinueResponse = enhancedAuthenticationService.onClientConnectMsg(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(enhancedAuthContinueResponse.success()).isFalse();
        verifyNoInteractions(clientSessionCtxMock);
    }

    @Test
    public void givenSaslException_whenOnClientConnectMsg_thenVerifyScramServerWithCallbackIsNotInitiated() throws Exception {
        // GIVEN
        var enhancedAuthContext = getEnhancedAuthContext();
        var clientSessionCtxMock = mock(ClientSessionCtx.class);
        enhancedAuthenticationService.init();

        doThrow(SaslException.class).when(enhancedAuthenticationService).createSaslServer(any(), any());

        // WHEN
        var enhancedAuthContinueResponse = enhancedAuthenticationService.onClientConnectMsg(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(enhancedAuthContinueResponse.success()).isFalse();
        verifyNoInteractions(clientSessionCtxMock);
    }

    @Test
    public void givenSuccessEnhancedAuthResponse_whenOnAuthContinue_thenVerifyInvocations() throws SaslException {
        // GIVEN
        var enhancedAuthContext = getEnhancedAuthContext();
        var clientSessionCtxMock = mock(ClientSessionCtx.class);
        var scramSaslServerWithCallbackMock = mock(ScramServerWithCallbackHandler.class);

        when(clientSessionCtxMock.getAuthMethod()).thenReturn(ScramAlgorithm.SHA_256.getMqttAlgorithmName());
        when(clientSessionCtxMock.getScramServerWithCallbackHandler()).thenReturn(scramSaslServerWithCallbackMock);
        byte[] response = "server-final-data".getBytes(StandardCharsets.UTF_8);
        when(scramSaslServerWithCallbackMock.evaluateResponse(any())).thenReturn(response);
        when(scramSaslServerWithCallbackMock.isComplete()).thenReturn(true);
        var authRulePatterns = getAuthRulePatterns();
        when(scramSaslServerWithCallbackMock.getAuthRulePatterns()).thenReturn(authRulePatterns);
        when(scramSaslServerWithCallbackMock.getClientType()).thenReturn(ClientType.DEVICE);

        // WHEN
        EnhancedAuthFinalResponse enhancedAuthFinalResponse = enhancedAuthenticationService.onAuthContinue(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(enhancedAuthFinalResponse).isNotNull();
        assertThat(enhancedAuthFinalResponse.enhancedAuthFailure()).isNull();
        assertThat(enhancedAuthFinalResponse.authRulePatterns()).isEqualTo(List.of(authRulePatterns));
        assertThat(enhancedAuthFinalResponse.clientType()).isEqualTo(ClientType.DEVICE);
        assertThat(enhancedAuthFinalResponse.success()).isTrue();
        assertThat(enhancedAuthFinalResponse.response()).isEqualTo(response);
    }

    @Test
    public void givenMissingAuthMethod_whenOnAuthContinue_thenVerifyFailure() {
        // GIVEN
        var enhancedAuthContext = getEnhancedAuthContext();
        var clientSessionCtxMock = mock(ClientSessionCtx.class);
        var scramSaslServerWithCallbackMock = mock(ScramServerWithCallbackHandler.class);

        when(clientSessionCtxMock.getScramServerWithCallbackHandler()).thenReturn(scramSaslServerWithCallbackMock);
        when(clientSessionCtxMock.getAuthMethod()).thenReturn(null);

        // WHEN
        EnhancedAuthFinalResponse enhancedAuthFinalResponse = enhancedAuthenticationService.onAuthContinue(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(enhancedAuthFinalResponse).isNotNull();
        assertThat(enhancedAuthFinalResponse.success()).isFalse();
        assertThat(enhancedAuthFinalResponse.enhancedAuthFailure()).isEqualTo(EnhancedAuthFailure.MISSING_AUTH_METHOD);
    }

    @Test
    public void givenAuthMethodMismatch_whenOnAuthContinue_thenVerifyFailure() {
        // GIVEN
        var enhancedAuthContext = getEnhancedAuthContextWithSha512();
        var clientSessionCtxMock = mock(ClientSessionCtx.class);
        var scramSaslServerWithCallbackMock = mock(ScramServerWithCallbackHandler.class);

        when(clientSessionCtxMock.getScramServerWithCallbackHandler()).thenReturn(scramSaslServerWithCallbackMock);
        when(clientSessionCtxMock.getAuthMethod()).thenReturn(ScramAlgorithm.SHA_256.getMqttAlgorithmName());

        // WHEN
        EnhancedAuthFinalResponse enhancedAuthFinalResponse = enhancedAuthenticationService.onAuthContinue(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(enhancedAuthFinalResponse).isNotNull();
        assertThat(enhancedAuthFinalResponse.success()).isFalse();
        assertThat(enhancedAuthFinalResponse.enhancedAuthFailure()).isEqualTo(EnhancedAuthFailure.AUTH_METHOD_MISMATCH);
    }

    @Test
    public void givenMissingAuthData_whenOnAuthContinue_thenVerifyFailure() {
        // GIVEN
        var enhancedAuthContext = getEnhancedAuthContext(ScramAlgorithm.SHA_256, null);
        var clientSessionCtxMock = mock(ClientSessionCtx.class);
        var scramSaslServerWithCallbackMock = mock(ScramServerWithCallbackHandler.class);

        when(clientSessionCtxMock.getScramServerWithCallbackHandler()).thenReturn(scramSaslServerWithCallbackMock);
        when(clientSessionCtxMock.getAuthMethod()).thenReturn(ScramAlgorithm.SHA_256.getMqttAlgorithmName());

        // WHEN
        EnhancedAuthFinalResponse enhancedAuthFinalResponse = enhancedAuthenticationService.onAuthContinue(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(enhancedAuthFinalResponse).isNotNull();
        assertThat(enhancedAuthFinalResponse.success()).isFalse();
        assertThat(enhancedAuthFinalResponse.enhancedAuthFailure()).isEqualTo(EnhancedAuthFailure.MISSING_AUTH_DATA);
    }

    @Test
    public void givenMissingScramServer_whenOnAuthContinue_thenVerifyFailure() {
        // GIVEN
        var enhancedAuthContext = getEnhancedAuthContext();
        var clientSessionCtxMock = mock(ClientSessionCtx.class);

        when(clientSessionCtxMock.getScramServerWithCallbackHandler()).thenReturn(null);

        // WHEN
        EnhancedAuthFinalResponse enhancedAuthFinalResponse = enhancedAuthenticationService.onAuthContinue(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(enhancedAuthFinalResponse).isNotNull();
        assertThat(enhancedAuthFinalResponse.success()).isFalse();
        assertThat(enhancedAuthFinalResponse.enhancedAuthFailure()).isEqualTo(EnhancedAuthFailure.MISSING_SCRAM_SERVER);
    }

    @Test
    public void givenSaslException_whenOnAuthContinue_thenVerifyFailure() throws SaslException {
        // GIVEN
        var enhancedAuthContext = getEnhancedAuthContext();
        var clientSessionCtxMock = mock(ClientSessionCtx.class);
        var scramSaslServerWithCallbackMock = mock(ScramServerWithCallbackHandler.class);

        when(clientSessionCtxMock.getAuthMethod()).thenReturn(ScramAlgorithm.SHA_256.getMqttAlgorithmName());
        when(clientSessionCtxMock.getScramServerWithCallbackHandler()).thenReturn(scramSaslServerWithCallbackMock);
        when(scramSaslServerWithCallbackMock.evaluateResponse(any())).thenThrow(new SaslException());

        // WHEN
        EnhancedAuthFinalResponse enhancedAuthFinalResponse = enhancedAuthenticationService.onAuthContinue(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(enhancedAuthFinalResponse).isNotNull();
        assertThat(enhancedAuthFinalResponse.success()).isFalse();
        assertThat(enhancedAuthFinalResponse.enhancedAuthFailure()).isEqualTo(EnhancedAuthFailure.CLIENT_FINAL_MESSAGE_EVALUATION_ERROR);
    }

    @Test
    public void givenFailedAuthChallenge_whenOnAuthContinue_thenVerifyFailure() {
        // GIVEN
        var enhancedAuthContext = getEnhancedAuthContext();
        var clientSessionCtxMock = mock(ClientSessionCtx.class);
        var scramSaslServerWithCallbackMock = mock(ScramServerWithCallbackHandler.class);

        when(clientSessionCtxMock.getAuthMethod()).thenReturn(ScramAlgorithm.SHA_256.getMqttAlgorithmName());
        when(clientSessionCtxMock.getScramServerWithCallbackHandler()).thenReturn(scramSaslServerWithCallbackMock);
        when(scramSaslServerWithCallbackMock.isComplete()).thenReturn(false);

        // WHEN
        EnhancedAuthFinalResponse enhancedAuthFinalResponse = enhancedAuthenticationService.onAuthContinue(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(enhancedAuthFinalResponse).isNotNull();
        assertThat(enhancedAuthFinalResponse.success()).isFalse();
        assertThat(enhancedAuthFinalResponse.enhancedAuthFailure()).isEqualTo(EnhancedAuthFailure.AUTH_CHALLENGE_FAILED);
    }

    @Test
    public void givenSuccessEnhancedAuthResponse_whenOnReAuthContinue_thenVerifySuccess() throws SaslException {
        // GIVEN
        var enhancedAuthContext = getEnhancedAuthContext();
        var clientSessionCtxMock = mock(ClientSessionCtx.class);
        var scramSaslServerWithCallbackMock = mock(ScramServerWithCallbackHandler.class);

        when(clientSessionCtxMock.getAuthMethod()).thenReturn(ScramAlgorithm.SHA_256.getMqttAlgorithmName());
        when(clientSessionCtxMock.getScramServerWithCallbackHandler()).thenReturn(scramSaslServerWithCallbackMock);
        byte[] response = "server-final-data".getBytes(StandardCharsets.UTF_8);
        when(scramSaslServerWithCallbackMock.evaluateResponse(any())).thenReturn(response);
        when(scramSaslServerWithCallbackMock.isComplete()).thenReturn(true);
        var authRulePatterns = getAuthRulePatterns();
        when(scramSaslServerWithCallbackMock.getAuthRulePatterns()).thenReturn(authRulePatterns);
        when(scramSaslServerWithCallbackMock.getClientType()).thenReturn(ClientType.DEVICE);

        // WHEN
        EnhancedAuthFinalResponse enhancedAuthFinalResponse = enhancedAuthenticationService.onReAuthContinue(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(enhancedAuthFinalResponse).isNotNull();
        assertThat(enhancedAuthFinalResponse.enhancedAuthFailure()).isNull();
        assertThat(enhancedAuthFinalResponse.authRulePatterns()).isEqualTo(List.of(authRulePatterns));
        assertThat(enhancedAuthFinalResponse.clientType()).isEqualTo(ClientType.DEVICE);
        assertThat(enhancedAuthFinalResponse.success()).isTrue();
        assertThat(enhancedAuthFinalResponse.response()).isEqualTo(response);
    }

    @Test
    public void givenMissingAuthMethod_whenOnReAuthContinue_thenVerifyFailure() {
        // GIVEN
        var enhancedAuthContext = getEnhancedAuthContext();
        var clientSessionCtxMock = mock(ClientSessionCtx.class);
        var scramSaslServerWithCallbackMock = mock(ScramServerWithCallbackHandler.class);

        when(clientSessionCtxMock.getScramServerWithCallbackHandler()).thenReturn(scramSaslServerWithCallbackMock);
        when(clientSessionCtxMock.getAuthMethod()).thenReturn(null);

        // WHEN
        EnhancedAuthFinalResponse enhancedAuthFinalResponse = enhancedAuthenticationService.onReAuthContinue(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(enhancedAuthFinalResponse).isNotNull();
        assertThat(enhancedAuthFinalResponse.success()).isFalse();
        assertThat(enhancedAuthFinalResponse.enhancedAuthFailure()).isEqualTo(EnhancedAuthFailure.MISSING_AUTH_METHOD);
    }

    @Test
    public void givenAuthMethodMismatch_whenOnReAuthContinue_thenVerifyFailure() {
        // GIVEN
        var enhancedAuthContext = getEnhancedAuthContextWithSha512();
        var clientSessionCtxMock = mock(ClientSessionCtx.class);
        var scramSaslServerWithCallbackMock = mock(ScramServerWithCallbackHandler.class);

        when(clientSessionCtxMock.getScramServerWithCallbackHandler()).thenReturn(scramSaslServerWithCallbackMock);
        when(clientSessionCtxMock.getAuthMethod()).thenReturn(ScramAlgorithm.SHA_256.getMqttAlgorithmName());

        // WHEN
        EnhancedAuthFinalResponse enhancedAuthFinalResponse = enhancedAuthenticationService.onReAuthContinue(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(enhancedAuthFinalResponse).isNotNull();
        assertThat(enhancedAuthFinalResponse.success()).isFalse();
        assertThat(enhancedAuthFinalResponse.enhancedAuthFailure()).isEqualTo(EnhancedAuthFailure.AUTH_METHOD_MISMATCH);
    }

    @Test
    public void givenMissingAuthData_whenOnReAuthContinue_thenVerifyFailure() {
        // GIVEN
        var enhancedAuthContext = getEnhancedAuthContext(ScramAlgorithm.SHA_256, null);
        var clientSessionCtxMock = mock(ClientSessionCtx.class);
        var scramSaslServerWithCallbackMock = mock(ScramServerWithCallbackHandler.class);

        when(clientSessionCtxMock.getScramServerWithCallbackHandler()).thenReturn(scramSaslServerWithCallbackMock);
        when(clientSessionCtxMock.getAuthMethod()).thenReturn(ScramAlgorithm.SHA_256.getMqttAlgorithmName());

        // WHEN
        EnhancedAuthFinalResponse enhancedAuthFinalResponse = enhancedAuthenticationService.onReAuthContinue(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(enhancedAuthFinalResponse).isNotNull();
        assertThat(enhancedAuthFinalResponse.success()).isFalse();
        assertThat(enhancedAuthFinalResponse.enhancedAuthFailure()).isEqualTo(EnhancedAuthFailure.MISSING_AUTH_DATA);
    }

    @Test
    public void givenMissingScramServer_whenOnReAuthContinue_thenVerifyFailure() {
        // GIVEN
        var enhancedAuthContext = getEnhancedAuthContext();
        var clientSessionCtxMock = mock(ClientSessionCtx.class);

        when(clientSessionCtxMock.getScramServerWithCallbackHandler()).thenReturn(null);

        // WHEN
        EnhancedAuthFinalResponse enhancedAuthFinalResponse = enhancedAuthenticationService.onReAuthContinue(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(enhancedAuthFinalResponse).isNotNull();
        assertThat(enhancedAuthFinalResponse.success()).isFalse();
        assertThat(enhancedAuthFinalResponse.enhancedAuthFailure()).isEqualTo(EnhancedAuthFailure.MISSING_SCRAM_SERVER);
    }

    @Test
    public void givenSaslException_whenOnReAuthContinue_thenVerifyFailure() throws SaslException {
        // GIVEN
        var enhancedAuthContext = getEnhancedAuthContext();
        var clientSessionCtxMock = mock(ClientSessionCtx.class);
        var scramSaslServerWithCallbackMock = mock(ScramServerWithCallbackHandler.class);

        when(clientSessionCtxMock.getAuthMethod()).thenReturn(ScramAlgorithm.SHA_256.getMqttAlgorithmName());
        when(clientSessionCtxMock.getScramServerWithCallbackHandler()).thenReturn(scramSaslServerWithCallbackMock);
        when(scramSaslServerWithCallbackMock.evaluateResponse(any())).thenThrow(new SaslException());

        // WHEN
        EnhancedAuthFinalResponse enhancedAuthFinalResponse = enhancedAuthenticationService.onReAuthContinue(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(enhancedAuthFinalResponse).isNotNull();
        assertThat(enhancedAuthFinalResponse.success()).isFalse();
        assertThat(enhancedAuthFinalResponse.enhancedAuthFailure()).isEqualTo(EnhancedAuthFailure.CLIENT_FINAL_MESSAGE_EVALUATION_ERROR);
    }

    @Test
    public void givenFailedAuthChallenge_whenOnReAuthContinue_thenVerifyFailure() {
        // GIVEN
        var enhancedAuthContext = getEnhancedAuthContext();
        var clientSessionCtxMock = mock(ClientSessionCtx.class);
        var scramSaslServerWithCallbackMock = mock(ScramServerWithCallbackHandler.class);

        when(clientSessionCtxMock.getAuthMethod()).thenReturn(ScramAlgorithm.SHA_256.getMqttAlgorithmName());
        when(clientSessionCtxMock.getScramServerWithCallbackHandler()).thenReturn(scramSaslServerWithCallbackMock);
        when(scramSaslServerWithCallbackMock.isComplete()).thenReturn(false);

        // WHEN
        EnhancedAuthFinalResponse enhancedAuthFinalResponse = enhancedAuthenticationService.onReAuthContinue(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(enhancedAuthFinalResponse).isNotNull();
        assertThat(enhancedAuthFinalResponse.success()).isFalse();
        assertThat(enhancedAuthFinalResponse.enhancedAuthFailure()).isEqualTo(EnhancedAuthFailure.AUTH_CHALLENGE_FAILED);
    }

    @Test
    public void givenAuthMethodMismatch_whenOnReAuth_thenReturnFalse() {
        // GIVEN
        var enhancedAuthContext = getEnhancedAuthContext();
        var clientSessionCtxMock = mock(ClientSessionCtx.class);
        String authMethodFromConnect = ScramAlgorithm.SHA_512.getMqttAlgorithmName();

        when(clientSessionCtxMock.getAuthMethod()).thenReturn(authMethodFromConnect);

        // WHEN
        var enhancedAuthContinueResponse = enhancedAuthenticationService.onReAuth(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(enhancedAuthContinueResponse.success()).isFalse();
        verify(clientSessionCtxMock).getAuthMethod();
        verifyNoMoreInteractions(clientSessionCtxMock);
    }

    @Test
    public void givenFailedScramServerInitiation_whenOnReAuth_thenReturnFalse() throws SaslException {
        // GIVEN
        var enhancedAuthContext = getEnhancedAuthContext();
        var clientSessionCtxMock = mock(ClientSessionCtx.class);

        when(clientSessionCtxMock.getAuthMethod()).thenReturn(ScramAlgorithm.SHA_256.getMqttAlgorithmName());
        doReturn(null).when(enhancedAuthenticationService).createSaslServer(any(), any());

        // WHEN
        var enhancedAuthContinueResponse = enhancedAuthenticationService.onReAuth(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(enhancedAuthContinueResponse.success()).isFalse();
        verify(clientSessionCtxMock).getAuthMethod();
        verifyNoMoreInteractions(clientSessionCtxMock);
    }

    @Test
    public void givenSaslExceptionDuringEvaluation_whenOnReAuth_thenReturnFalse() throws SaslException {
        // GIVEN
        var enhancedAuthContext = getEnhancedAuthContext();
        var scramSaslServer = mock(ScramSaslServer.class);
        var clientSessionCtxMock = mock(ClientSessionCtx.class);
        var scramSaslServerWithCallbackMock = mock(ScramServerWithCallbackHandler.class);

        when(clientSessionCtxMock.getAuthMethod()).thenReturn(ScramAlgorithm.SHA_256.getMqttAlgorithmName());
        doReturn(scramSaslServer).when(enhancedAuthenticationService).createSaslServer(any(), any());
        when(clientSessionCtxMock.getScramServerWithCallbackHandler()).thenReturn(scramSaslServerWithCallbackMock);
        when(scramSaslServerWithCallbackMock.evaluateResponse(any())).thenThrow(new SaslException("Evaluation failed"));

        // WHEN
        var enhancedAuthContinueResponse = enhancedAuthenticationService.onReAuth(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(enhancedAuthContinueResponse.success()).isFalse();
        verify(clientSessionCtxMock).getAuthMethod();
        verify(clientSessionCtxMock).setScramServerWithCallbackHandler(any());
        verify(clientSessionCtxMock).getScramServerWithCallbackHandler();
        verifyNoMoreInteractions(clientSessionCtxMock);
    }

    @Test
    public void givenSuccessfulReAuth_whenOnReAuth_thenReturnTrue() throws SaslException {
        // GIVEN
        var enhancedAuthContext = getEnhancedAuthContext();
        var scramSaslServer = mock(ScramSaslServer.class);
        var clientSessionCtxMock = mock(ClientSessionCtx.class);
        var scramSaslServerWithCallbackMock = mock(ScramServerWithCallbackHandler.class);

        byte[] challenge = "server-initial-request".getBytes(StandardCharsets.UTF_8);

        when(clientSessionCtxMock.getAuthMethod()).thenReturn(ScramAlgorithm.SHA_256.getMqttAlgorithmName());
        doReturn(scramSaslServer).when(enhancedAuthenticationService).createSaslServer(any(), any());
        when(clientSessionCtxMock.getScramServerWithCallbackHandler()).thenReturn(scramSaslServerWithCallbackMock);
        when(scramSaslServerWithCallbackMock.evaluateResponse(any())).thenReturn(challenge);

        // WHEN
        var enhancedAuthContinueResponse = enhancedAuthenticationService.onReAuth(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(enhancedAuthContinueResponse.success()).isTrue();
        verify(clientSessionCtxMock).getAuthMethod();
        verify(clientSessionCtxMock).setScramServerWithCallbackHandler(any());
        verify(clientSessionCtxMock).getScramServerWithCallbackHandler();
        verifyNoMoreInteractions(clientSessionCtxMock);
        verify(scramSaslServerWithCallbackMock).evaluateResponse(enhancedAuthContext.getAuthData());
    }

    private AuthRulePatterns getAuthRulePatterns() {
        return AuthRulePatterns.newInstance(List.of(Pattern.compile("test")));
    }

    private EnhancedAuthContext getEnhancedAuthContextWithSha512() {
        return getEnhancedAuthContext(ScramAlgorithm.SHA_512, "client-first-data".getBytes(StandardCharsets.UTF_8));
    }

    private EnhancedAuthContext getEnhancedAuthContext() {
        return getEnhancedAuthContext(ScramAlgorithm.SHA_256, "client-first-data".getBytes(StandardCharsets.UTF_8));
    }

    private EnhancedAuthContext getEnhancedAuthContext(ScramAlgorithm algorithm, byte[] authData) {
        return EnhancedAuthContext.builder()
                .clientId(CLIENT_ID)
                .authData(authData)
                .authMethod(algorithm.getMqttAlgorithmName())
                .build();
    }

}
