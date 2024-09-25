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

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import org.apache.kafka.common.security.scram.internals.ScramSaslServer;
import org.apache.kafka.common.security.scram.internals.ScramSaslServerProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.client.credentials.ScramAlgorithm;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.service.auth.enhanced.EnhancedAuthContext;
import org.thingsboard.mqtt.broker.service.auth.enhanced.EnhancedAuthFailureReason;
import org.thingsboard.mqtt.broker.service.auth.enhanced.EnhancedAuthResponse;
import org.thingsboard.mqtt.broker.service.auth.enhanced.ScramSaslServerWithCallback;
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
        enhancedAuthenticationService = spy(new DefaultEnhancedAuthenticationService(credentialsServiceMock, authorizationRuleServiceMock));
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

        var scramSaslServer = mock(ScramSaslServer.class);
        var scramSaslServerWithCallbackMock = mock(ScramSaslServerWithCallback.class);
        var channelHandlerContextMock = mock(ChannelHandlerContext.class);
        var clientSessionCtxMock = mock(ClientSessionCtx.class);

        when(clientSessionCtxMock.getScramSaslServerWithCallback()).thenReturn(scramSaslServerWithCallbackMock);
        when(clientSessionCtxMock.getChannel()).thenReturn(channelHandlerContextMock);
        doReturn(scramSaslServer).when(enhancedAuthenticationService).createSaslServer(any(), any());

        // WHEN
        boolean result = enhancedAuthenticationService.onClientConnectMsg(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(result).isTrue();
        verify(clientSessionCtxMock).setScramSaslServerWithCallback(any());
        verify(clientSessionCtxMock).getScramSaslServerWithCallback();
        verify(clientSessionCtxMock).getChannel();
        verifyNoMoreInteractions(clientSessionCtxMock);
        verify(scramSaslServerWithCallbackMock).evaluateResponse(enhancedAuthContext.getAuthData());

        var mqttMessageCaptor = ArgumentCaptor.forClass(MqttMessage.class);
        verify(channelHandlerContextMock).writeAndFlush(mqttMessageCaptor.capture());
        var mqttMessage = mqttMessageCaptor.getValue();
        assertThat(mqttMessage).isNotNull();
        assertThat(mqttMessage.fixedHeader().messageType()).isEqualTo(MqttMessageType.AUTH);
    }

    @Test
    public void givenScramServerInitiated_whenOnClientConnectMsgEvalFailure_thenVerifyInvocations() throws Exception {
        // GIVEN
        var enhancedAuthContext = getEnhancedAuthContext();

        var scramSaslServer = mock(ScramSaslServer.class);
        var scramSaslServerWithCallbackMock = mock(ScramSaslServerWithCallback.class);
        var clientSessionCtxMock = mock(ClientSessionCtx.class);

        when(clientSessionCtxMock.getScramSaslServerWithCallback()).thenReturn(scramSaslServerWithCallbackMock);
        doReturn(scramSaslServer).when(enhancedAuthenticationService).createSaslServer(any(), any());
        doThrow(SaslException.class).when(scramSaslServerWithCallbackMock).evaluateResponse(any());

        // WHEN
        boolean result = enhancedAuthenticationService.onClientConnectMsg(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(result).isFalse();
        verify(scramSaslServerWithCallbackMock).evaluateResponse(enhancedAuthContext.getAuthData());
        verify(clientSessionCtxMock).setScramSaslServerWithCallback(any());
        verify(clientSessionCtxMock).getScramSaslServerWithCallback();
        verifyNoMoreInteractions(clientSessionCtxMock);
    }

    @Test
    public void givenNullScramServer_whenOnClientConnectMsg_thenVerifyScramServerWithCallbackIsNotInitiated() throws Exception {
        // GIVEN
        var enhancedAuthContext = getEnhancedAuthContext();
        var clientSessionCtxMock = mock(ClientSessionCtx.class);

        doReturn(null).when(enhancedAuthenticationService).createSaslServer(any(), any());

        // WHEN
        boolean result = enhancedAuthenticationService.onClientConnectMsg(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(result).isFalse();
        verifyNoInteractions(clientSessionCtxMock);
    }

    @Test
    public void givenNotScramServer_whenOnClientConnectMsg_thenVerifyScramServerWithCallbackIsNotInitiated() throws Exception {
        // GIVEN
        var enhancedAuthContext = getEnhancedAuthContext();
        var saslServer = mock(SaslServer.class);
        var clientSessionCtxMock = mock(ClientSessionCtx.class);

        doReturn(saslServer).when(enhancedAuthenticationService).createSaslServer(any(), any());

        // WHEN
        boolean result = enhancedAuthenticationService.onClientConnectMsg(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(result).isFalse();
        verifyNoInteractions(clientSessionCtxMock);
    }

    @Test
    public void givenSaslException_whenOnClientConnectMsg_thenVerifyScramServerWithCallbackIsNotInitiated() throws Exception {
        // GIVEN
        var enhancedAuthContext = getEnhancedAuthContext();
        var clientSessionCtxMock = mock(ClientSessionCtx.class);

        doThrow(SaslException.class).when(enhancedAuthenticationService).createSaslServer(any(), any());

        // WHEN
        boolean result = enhancedAuthenticationService.onClientConnectMsg(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(result).isFalse();
        verifyNoInteractions(clientSessionCtxMock);
    }

    @Test
    public void givenSuccessEnhancedAuthResponse_whenOnAuthContinue_thenVerifyInvocations() throws SaslException {
        // GIVEN
        var enhancedAuthContext = getEnhancedAuthContext();
        var clientSessionCtxMock = mock(ClientSessionCtx.class);
        var scramSaslServerWithCallbackMock = mock(ScramSaslServerWithCallback.class);

        when(clientSessionCtxMock.getAuthMethod()).thenReturn(ScramAlgorithm.SHA_256.getMqttAlgorithmName());
        when(clientSessionCtxMock.getScramSaslServerWithCallback()).thenReturn(scramSaslServerWithCallbackMock);
        byte[] response = "server-final-data".getBytes(StandardCharsets.UTF_8);
        when(scramSaslServerWithCallbackMock.evaluateResponse(any())).thenReturn(response);
        when(scramSaslServerWithCallbackMock.isComplete()).thenReturn(true);
        var authRulePatterns = getAuthRulePatterns();
        when(scramSaslServerWithCallbackMock.getAuthRulePatterns()).thenReturn(authRulePatterns);
        when(scramSaslServerWithCallbackMock.getClientType()).thenReturn(ClientType.DEVICE);

        // WHEN
        EnhancedAuthResponse enhancedAuthResponse = enhancedAuthenticationService.onAuthContinue(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(enhancedAuthResponse).isNotNull();
        assertThat(enhancedAuthResponse.enhancedAuthFailureReason()).isNull();
        assertThat(enhancedAuthResponse.authRulePatterns()).isEqualTo(List.of(authRulePatterns));
        assertThat(enhancedAuthResponse.clientType()).isEqualTo(ClientType.DEVICE);
        assertThat(enhancedAuthResponse.success()).isTrue();
        assertThat(enhancedAuthResponse.response()).isEqualTo(response);
    }

    @Test
    public void givenMissingAuthMethod_whenOnAuthContinue_thenVerifyFailure() {
        // GIVEN
        var enhancedAuthContext = getEnhancedAuthContext();
        var clientSessionCtxMock = mock(ClientSessionCtx.class);

        when(clientSessionCtxMock.getAuthMethod()).thenReturn(null);

        // WHEN
        EnhancedAuthResponse enhancedAuthResponse = enhancedAuthenticationService.onAuthContinue(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(enhancedAuthResponse).isNotNull();
        assertThat(enhancedAuthResponse.success()).isFalse();
        assertThat(enhancedAuthResponse.enhancedAuthFailureReason()).isEqualTo(EnhancedAuthFailureReason.MISSING_AUTH_METHOD);
    }

    @Test
    public void givenAuthMethodMismatch_whenOnAuthContinue_thenVerifyFailure() {
        // GIVEN
        var enhancedAuthContext = getEnhancedAuthContext(ScramAlgorithm.SHA_512);
        var clientSessionCtxMock = mock(ClientSessionCtx.class);

        when(clientSessionCtxMock.getAuthMethod()).thenReturn(ScramAlgorithm.SHA_256.getMqttAlgorithmName());

        // WHEN
        EnhancedAuthResponse enhancedAuthResponse = enhancedAuthenticationService.onAuthContinue(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(enhancedAuthResponse).isNotNull();
        assertThat(enhancedAuthResponse.success()).isFalse();
        assertThat(enhancedAuthResponse.enhancedAuthFailureReason()).isEqualTo(EnhancedAuthFailureReason.AUTH_METHOD_MISMATCH);
    }

    @Test
    public void givenMissingAuthData_whenOnAuthContinue_thenVerifyFailure() {
        // GIVEN
        var enhancedAuthContext = getEnhancedAuthContext(ScramAlgorithm.SHA_256, null);
        var clientSessionCtxMock = mock(ClientSessionCtx.class);

        when(clientSessionCtxMock.getAuthMethod()).thenReturn(ScramAlgorithm.SHA_256.getMqttAlgorithmName());

        // WHEN
        EnhancedAuthResponse enhancedAuthResponse = enhancedAuthenticationService.onAuthContinue(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(enhancedAuthResponse).isNotNull();
        assertThat(enhancedAuthResponse.success()).isFalse();
        assertThat(enhancedAuthResponse.enhancedAuthFailureReason()).isEqualTo(EnhancedAuthFailureReason.MISSING_AUTH_DATA);
    }

    @Test
    public void givenMissingScramServer_whenOnAuthContinue_thenVerifyFailure() {
        // GIVEN
        var enhancedAuthContext = getEnhancedAuthContext();
        var clientSessionCtxMock = mock(ClientSessionCtx.class);

        when(clientSessionCtxMock.getAuthMethod()).thenReturn(ScramAlgorithm.SHA_256.getMqttAlgorithmName());
        when(clientSessionCtxMock.getScramSaslServerWithCallback()).thenReturn(null);

        // WHEN
        EnhancedAuthResponse enhancedAuthResponse = enhancedAuthenticationService.onAuthContinue(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(enhancedAuthResponse).isNotNull();
        assertThat(enhancedAuthResponse.success()).isFalse();
        assertThat(enhancedAuthResponse.enhancedAuthFailureReason()).isEqualTo(EnhancedAuthFailureReason.MISSING_SCRAM_SERVER);
    }

    @Test
    public void givenSaslException_whenOnAuthContinue_thenVerifyFailure() throws SaslException {
        // GIVEN
        var enhancedAuthContext = getEnhancedAuthContext();
        var clientSessionCtxMock = mock(ClientSessionCtx.class);
        var scramSaslServerWithCallbackMock = mock(ScramSaslServerWithCallback.class);

        when(clientSessionCtxMock.getAuthMethod()).thenReturn(ScramAlgorithm.SHA_256.getMqttAlgorithmName());
        when(clientSessionCtxMock.getScramSaslServerWithCallback()).thenReturn(scramSaslServerWithCallbackMock);
        when(scramSaslServerWithCallbackMock.evaluateResponse(any())).thenThrow(new SaslException());

        // WHEN
        EnhancedAuthResponse enhancedAuthResponse = enhancedAuthenticationService.onAuthContinue(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(enhancedAuthResponse).isNotNull();
        assertThat(enhancedAuthResponse.success()).isFalse();
        assertThat(enhancedAuthResponse.enhancedAuthFailureReason()).isEqualTo(EnhancedAuthFailureReason.EVALUATION_ERROR);
    }

    @Test
    public void givenFailedAuthChallenge_whenOnAuthContinue_thenVerifyFailure() {
        // GIVEN
        var enhancedAuthContext = getEnhancedAuthContext();
        var clientSessionCtxMock = mock(ClientSessionCtx.class);
        var scramSaslServerWithCallbackMock = mock(ScramSaslServerWithCallback.class);

        when(clientSessionCtxMock.getAuthMethod()).thenReturn(ScramAlgorithm.SHA_256.getMqttAlgorithmName());
        when(clientSessionCtxMock.getScramSaslServerWithCallback()).thenReturn(scramSaslServerWithCallbackMock);
        when(scramSaslServerWithCallbackMock.isComplete()).thenReturn(false);

        // WHEN
        EnhancedAuthResponse enhancedAuthResponse = enhancedAuthenticationService.onAuthContinue(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(enhancedAuthResponse).isNotNull();
        assertThat(enhancedAuthResponse.success()).isFalse();
        assertThat(enhancedAuthResponse.enhancedAuthFailureReason()).isEqualTo(EnhancedAuthFailureReason.AUTH_CHALLENGE_FAILED);
    }

    @Test
    public void givenSuccessEnhancedAuthResponse_whenOnReAuthContinue_thenVerifySuccess() throws SaslException {
        // GIVEN
        var enhancedAuthContext = getEnhancedAuthContext();
        var clientSessionCtxMock = mock(ClientSessionCtx.class);
        var channelHandlerCtxMock = mock(ChannelHandlerContext.class);
        var scramSaslServerWithCallbackMock = mock(ScramSaslServerWithCallback.class);

        when(clientSessionCtxMock.getAuthMethod()).thenReturn(ScramAlgorithm.SHA_256.getMqttAlgorithmName());
        when(clientSessionCtxMock.getScramSaslServerWithCallback()).thenReturn(scramSaslServerWithCallbackMock);
        when(clientSessionCtxMock.getChannel()).thenReturn(channelHandlerCtxMock);
        byte[] response = "server-final-data".getBytes(StandardCharsets.UTF_8);
        when(scramSaslServerWithCallbackMock.evaluateResponse(any())).thenReturn(response);
        when(scramSaslServerWithCallbackMock.isComplete()).thenReturn(true);
        var authRulePatterns = getAuthRulePatterns();
        when(scramSaslServerWithCallbackMock.getAuthRulePatterns()).thenReturn(authRulePatterns);
        when(scramSaslServerWithCallbackMock.getClientType()).thenReturn(ClientType.DEVICE);

        // WHEN
        EnhancedAuthResponse enhancedAuthResponse = enhancedAuthenticationService.onReAuthContinue(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(enhancedAuthResponse).isNotNull();
        assertThat(enhancedAuthResponse.enhancedAuthFailureReason()).isNull();
        assertThat(enhancedAuthResponse.authRulePatterns()).isEqualTo(List.of(authRulePatterns));
        assertThat(enhancedAuthResponse.clientType()).isEqualTo(ClientType.DEVICE);
        assertThat(enhancedAuthResponse.success()).isTrue();
        assertThat(enhancedAuthResponse.response()).isEqualTo(response);

        verify(clientSessionCtxMock).getChannel();

        var mqttMessageCaptor = ArgumentCaptor.forClass(MqttMessage.class);
        verify(channelHandlerCtxMock).writeAndFlush(mqttMessageCaptor.capture());
        var mqttMessage = mqttMessageCaptor.getValue();
        assertThat(mqttMessage).isNotNull();
        assertThat(mqttMessage.fixedHeader().messageType()).isEqualTo(MqttMessageType.AUTH);
    }

    @Test
    public void givenMissingAuthMethod_whenOnReAuthContinue_thenVerifyFailure() {
        // GIVEN
        var enhancedAuthContext = getEnhancedAuthContext();
        var clientSessionCtxMock = mock(ClientSessionCtx.class);

        when(clientSessionCtxMock.getAuthMethod()).thenReturn(null);

        // WHEN
        EnhancedAuthResponse enhancedAuthResponse = enhancedAuthenticationService.onReAuthContinue(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(enhancedAuthResponse).isNotNull();
        assertThat(enhancedAuthResponse.success()).isFalse();
        assertThat(enhancedAuthResponse.enhancedAuthFailureReason()).isEqualTo(EnhancedAuthFailureReason.MISSING_AUTH_METHOD);
    }

    @Test
    public void givenAuthMethodMismatch_whenOnReAuthContinue_thenVerifyFailure() {
        // GIVEN
        var enhancedAuthContext = getEnhancedAuthContext(ScramAlgorithm.SHA_512);
        var clientSessionCtxMock = mock(ClientSessionCtx.class);

        when(clientSessionCtxMock.getAuthMethod()).thenReturn(ScramAlgorithm.SHA_256.getMqttAlgorithmName());

        // WHEN
        EnhancedAuthResponse enhancedAuthResponse = enhancedAuthenticationService.onReAuthContinue(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(enhancedAuthResponse).isNotNull();
        assertThat(enhancedAuthResponse.success()).isFalse();
        assertThat(enhancedAuthResponse.enhancedAuthFailureReason()).isEqualTo(EnhancedAuthFailureReason.AUTH_METHOD_MISMATCH);
    }

    @Test
    public void givenMissingAuthData_whenOnReAuthContinue_thenVerifyFailure() {
        // GIVEN
        var enhancedAuthContext = getEnhancedAuthContext(ScramAlgorithm.SHA_256, null);
        var clientSessionCtxMock = mock(ClientSessionCtx.class);

        when(clientSessionCtxMock.getAuthMethod()).thenReturn(ScramAlgorithm.SHA_256.getMqttAlgorithmName());

        // WHEN
        EnhancedAuthResponse enhancedAuthResponse = enhancedAuthenticationService.onReAuthContinue(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(enhancedAuthResponse).isNotNull();
        assertThat(enhancedAuthResponse.success()).isFalse();
        assertThat(enhancedAuthResponse.enhancedAuthFailureReason()).isEqualTo(EnhancedAuthFailureReason.MISSING_AUTH_DATA);
    }

    @Test
    public void givenMissingScramServer_whenOnReAuthContinue_thenVerifyFailure() {
        // GIVEN
        var enhancedAuthContext = getEnhancedAuthContext();
        var clientSessionCtxMock = mock(ClientSessionCtx.class);

        when(clientSessionCtxMock.getAuthMethod()).thenReturn(ScramAlgorithm.SHA_256.getMqttAlgorithmName());
        when(clientSessionCtxMock.getScramSaslServerWithCallback()).thenReturn(null);

        // WHEN
        EnhancedAuthResponse enhancedAuthResponse = enhancedAuthenticationService.onReAuthContinue(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(enhancedAuthResponse).isNotNull();
        assertThat(enhancedAuthResponse.success()).isFalse();
        assertThat(enhancedAuthResponse.enhancedAuthFailureReason()).isEqualTo(EnhancedAuthFailureReason.MISSING_SCRAM_SERVER);
    }

    @Test
    public void givenSaslException_whenOnReAuthContinue_thenVerifyFailure() throws SaslException {
        // GIVEN
        var enhancedAuthContext = getEnhancedAuthContext();
        var clientSessionCtxMock = mock(ClientSessionCtx.class);
        var scramSaslServerWithCallbackMock = mock(ScramSaslServerWithCallback.class);

        when(clientSessionCtxMock.getAuthMethod()).thenReturn(ScramAlgorithm.SHA_256.getMqttAlgorithmName());
        when(clientSessionCtxMock.getScramSaslServerWithCallback()).thenReturn(scramSaslServerWithCallbackMock);
        when(scramSaslServerWithCallbackMock.evaluateResponse(any())).thenThrow(new SaslException());

        // WHEN
        EnhancedAuthResponse enhancedAuthResponse = enhancedAuthenticationService.onReAuthContinue(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(enhancedAuthResponse).isNotNull();
        assertThat(enhancedAuthResponse.success()).isFalse();
        assertThat(enhancedAuthResponse.enhancedAuthFailureReason()).isEqualTo(EnhancedAuthFailureReason.EVALUATION_ERROR);
    }

    @Test
    public void givenFailedAuthChallenge_whenOnReAuthContinue_thenVerifyFailure() {
        // GIVEN
        var enhancedAuthContext = getEnhancedAuthContext();
        var clientSessionCtxMock = mock(ClientSessionCtx.class);
        var scramSaslServerWithCallbackMock = mock(ScramSaslServerWithCallback.class);

        when(clientSessionCtxMock.getAuthMethod()).thenReturn(ScramAlgorithm.SHA_256.getMqttAlgorithmName());
        when(clientSessionCtxMock.getScramSaslServerWithCallback()).thenReturn(scramSaslServerWithCallbackMock);
        when(scramSaslServerWithCallbackMock.isComplete()).thenReturn(false);

        // WHEN
        EnhancedAuthResponse enhancedAuthResponse = enhancedAuthenticationService.onReAuthContinue(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(enhancedAuthResponse).isNotNull();
        assertThat(enhancedAuthResponse.success()).isFalse();
        assertThat(enhancedAuthResponse.enhancedAuthFailureReason()).isEqualTo(EnhancedAuthFailureReason.AUTH_CHALLENGE_FAILED);
    }

    @Test
    public void givenAuthMethodMismatch_whenOnReAuth_thenReturnFalse() {
        // GIVEN
        var enhancedAuthContext = getEnhancedAuthContext();
        var clientSessionCtxMock = mock(ClientSessionCtx.class);
        String authMethodFromConnect = ScramAlgorithm.SHA_512.getMqttAlgorithmName();

        when(clientSessionCtxMock.getAuthMethod()).thenReturn(authMethodFromConnect);

        // WHEN
        boolean result = enhancedAuthenticationService.onReAuth(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(result).isFalse();
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
        boolean result = enhancedAuthenticationService.onReAuth(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(result).isFalse();
        verify(clientSessionCtxMock).getAuthMethod();
        verifyNoMoreInteractions(clientSessionCtxMock);
    }

    @Test
    public void givenSaslExceptionDuringEvaluation_whenOnReAuth_thenReturnFalse() throws SaslException {
        // GIVEN
        var enhancedAuthContext = getEnhancedAuthContext();
        var scramSaslServer = mock(ScramSaslServer.class);
        var clientSessionCtxMock = mock(ClientSessionCtx.class);
        var scramSaslServerWithCallbackMock = mock(ScramSaslServerWithCallback.class);

        when(clientSessionCtxMock.getAuthMethod()).thenReturn(ScramAlgorithm.SHA_256.getMqttAlgorithmName());
        doReturn(scramSaslServer).when(enhancedAuthenticationService).createSaslServer(any(), any());
        when(clientSessionCtxMock.getScramSaslServerWithCallback()).thenReturn(scramSaslServerWithCallbackMock);
        when(scramSaslServerWithCallbackMock.evaluateResponse(any())).thenThrow(new SaslException("Evaluation failed"));

        // WHEN
        boolean result = enhancedAuthenticationService.onReAuth(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(result).isFalse();
        verify(clientSessionCtxMock).getAuthMethod();
        verify(clientSessionCtxMock).setScramSaslServerWithCallback(any());
        verify(clientSessionCtxMock).getScramSaslServerWithCallback();
        verify(clientSessionCtxMock).clearScramServer();
        verifyNoMoreInteractions(clientSessionCtxMock);
    }

    @Test
    public void givenSuccessfulReAuth_whenOnReAuth_thenReturnTrue() throws SaslException {
        // GIVEN
        var enhancedAuthContext = getEnhancedAuthContext();
        var scramSaslServer = mock(ScramSaslServer.class);
        var clientSessionCtxMock = mock(ClientSessionCtx.class);
        var channelHandlerCtxMock = mock(ChannelHandlerContext.class);
        var scramSaslServerWithCallbackMock = mock(ScramSaslServerWithCallback.class);

        byte[] challenge = "server-initial-request".getBytes(StandardCharsets.UTF_8);

        when(clientSessionCtxMock.getAuthMethod()).thenReturn(ScramAlgorithm.SHA_256.getMqttAlgorithmName());
        doReturn(scramSaslServer).when(enhancedAuthenticationService).createSaslServer(any(), any());
        when(clientSessionCtxMock.getScramSaslServerWithCallback()).thenReturn(scramSaslServerWithCallbackMock);
        when(clientSessionCtxMock.getChannel()).thenReturn(channelHandlerCtxMock);
        when(scramSaslServerWithCallbackMock.evaluateResponse(any())).thenReturn(challenge);

        // WHEN
        boolean result = enhancedAuthenticationService.onReAuth(clientSessionCtxMock, enhancedAuthContext);

        // THEN
        assertThat(result).isTrue();
        verify(clientSessionCtxMock).getAuthMethod();
        verify(clientSessionCtxMock).setScramSaslServerWithCallback(any());
        verify(clientSessionCtxMock).getScramSaslServerWithCallback();
        verify(clientSessionCtxMock).getChannel();
        verifyNoMoreInteractions(clientSessionCtxMock);
        verify(scramSaslServerWithCallbackMock).evaluateResponse(enhancedAuthContext.getAuthData());

        var mqttMessageCaptor = ArgumentCaptor.forClass(MqttMessage.class);
        verify(channelHandlerCtxMock).writeAndFlush(mqttMessageCaptor.capture());
        var mqttMessage = mqttMessageCaptor.getValue();
        assertThat(mqttMessage).isNotNull();
        assertThat(mqttMessage.fixedHeader().messageType()).isEqualTo(MqttMessageType.AUTH);
    }

    private AuthRulePatterns getAuthRulePatterns() {
        return AuthRulePatterns.newInstance(List.of(Pattern.compile("test")));
    }

    private EnhancedAuthContext getEnhancedAuthContext(ScramAlgorithm algorithm) {
        return getEnhancedAuthContext(algorithm, "client-first-data".getBytes(StandardCharsets.UTF_8));
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
