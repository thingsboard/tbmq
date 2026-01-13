/**
 * Copyright © 2016-2026 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.auth.providers.jwt;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.security.jwt.AlgorithmBasedVerifierConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.jwt.HmacBasedAlgorithmConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.jwt.JwtMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.jwt.PemKeyAlgorithmConfiguration;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttAuthProviderService;
import org.thingsboard.mqtt.broker.service.auth.AuthorizationRuleService;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthContext;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthResponse;
import org.thingsboard.mqtt.broker.service.security.authorization.AuthRulePatterns;
import org.thingsboard.mqtt.broker.service.test.util.TestUtils;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class JwtMqttClientAuthProviderTest {

    @Mock
    private MqttAuthProviderService mqttAuthProviderService;
    @Mock
    private AuthorizationRuleService authorizationRuleService;
    @Mock
    private JwtVerificationStrategy verificationStrategy;
    @Mock
    private AuthRulePatterns authRulePatterns;
    @Mock
    private AuthContext authContext;

    private JwtMqttClientAuthProvider provider;

    @Before
    public void setUp() {
        provider = new JwtMqttClientAuthProvider(mqttAuthProviderService, authorizationRuleService);
    }

    @Test
    public void testInitFailsIfNoProvider() {
        when(mqttAuthProviderService.getAuthProviderByType(MqttAuthProviderType.JWT)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> provider.init())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to initialize JWT authentication provider! Provider is missing in the DB!");
    }

    @Test
    public void testInitSucceedsWithEnabledHmacProvider() throws Exception {
        HmacBasedAlgorithmConfiguration hmacConfig = mock(HmacBasedAlgorithmConfiguration.class);
        when(hmacConfig.getSecret()).thenReturn(TestUtils.generateHmac32Secret());

        AlgorithmBasedVerifierConfiguration algConfig = mock(AlgorithmBasedVerifierConfiguration.class);
        when(algConfig.getJwtSignAlgorithmConfiguration()).thenReturn(hmacConfig);


        JwtMqttAuthProviderConfiguration config = mock(JwtMqttAuthProviderConfiguration.class);
        when(config.getJwtVerifierConfiguration()).thenReturn(algConfig);

        MqttAuthProvider authProvider = mock(MqttAuthProvider.class);
        when(authProvider.getConfiguration()).thenReturn(config);
        when(authProvider.isEnabled()).thenReturn(true);

        when(mqttAuthProviderService.getAuthProviderByType(MqttAuthProviderType.JWT)).thenReturn(Optional.of(authProvider));
        when(authorizationRuleService.parseAuthorizationRule(config)).thenReturn(authRulePatterns);

        provider.init();

        assertThat(provider.isEnabled()).isTrue();

        JwtVerificationStrategy strategyField = (JwtVerificationStrategy) ReflectionTestUtils.getField(provider, "verificationStrategy");
        assertThat(strategyField).isNotNull().isInstanceOf(HmacJwtVerificationStrategy.class);
    }

    @Test
    public void testInitSucceedsWithEnabledPubKeyProvider() throws Exception {
        PemKeyAlgorithmConfiguration pemBasedConfig = mock(PemKeyAlgorithmConfiguration.class);

        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();

        when(pemBasedConfig.getPublicPemKey()).thenReturn(TestUtils.toPemString(publicKey));

        AlgorithmBasedVerifierConfiguration algConfig = mock(AlgorithmBasedVerifierConfiguration.class);
        when(algConfig.getJwtSignAlgorithmConfiguration()).thenReturn(pemBasedConfig);

        JwtMqttAuthProviderConfiguration config = mock(JwtMqttAuthProviderConfiguration.class);
        when(config.getJwtVerifierConfiguration()).thenReturn(algConfig);

        MqttAuthProvider authProvider = mock(MqttAuthProvider.class);
        when(authProvider.getConfiguration()).thenReturn(config);
        when(authProvider.isEnabled()).thenReturn(true);

        when(mqttAuthProviderService.getAuthProviderByType(MqttAuthProviderType.JWT)).thenReturn(Optional.of(authProvider));
        when(authorizationRuleService.parseAuthorizationRule(config)).thenReturn(authRulePatterns);

        provider.init();

        assertThat(provider.isEnabled()).isTrue();

        JwtVerificationStrategy strategyField = (JwtVerificationStrategy) ReflectionTestUtils.getField(provider, "verificationStrategy");
        assertThat(strategyField).isNotNull().isInstanceOf(PemKeyJwtVerificationStrategy.class);
    }


    @Test
    public void testInitSucceedsWithDisabledProvider() throws Exception {
        JwtMqttAuthProviderConfiguration config = mock(JwtMqttAuthProviderConfiguration.class);

        MqttAuthProvider authProvider = mock(MqttAuthProvider.class);
        when(authProvider.getConfiguration()).thenReturn(config);
        when(authProvider.isEnabled()).thenReturn(false);

        when(mqttAuthProviderService.getAuthProviderByType(MqttAuthProviderType.JWT)).thenReturn(Optional.of(authProvider));
        when(authorizationRuleService.parseAuthorizationRule(config)).thenReturn(authRulePatterns);

        provider.init();

        assertThat(provider.isEnabled()).isFalse();

        JwtVerificationStrategy strategyField = (JwtVerificationStrategy) ReflectionTestUtils.getField(provider, "verificationStrategy");
        assertThat(strategyField).isNull();
    }

    @Test
    public void testDisableAndEnableLifecycle() throws Exception {
        HmacBasedAlgorithmConfiguration hmacConfig = mock(HmacBasedAlgorithmConfiguration.class);
        when(hmacConfig.getSecret()).thenReturn(TestUtils.generateHmac32Secret());

        AlgorithmBasedVerifierConfiguration algConfig = mock(AlgorithmBasedVerifierConfiguration.class);
        when(algConfig.getJwtSignAlgorithmConfiguration()).thenReturn(hmacConfig);

        JwtMqttAuthProviderConfiguration config = mock(JwtMqttAuthProviderConfiguration.class);
        when(config.getJwtVerifierConfiguration()).thenReturn(algConfig);

        MqttAuthProvider authProvider = mock(MqttAuthProvider.class);
        when(authProvider.getConfiguration()).thenReturn(config);
        when(authProvider.isEnabled()).thenReturn(true);

        when(mqttAuthProviderService.getAuthProviderByType(MqttAuthProviderType.JWT)).thenReturn(Optional.of(authProvider));
        when(authorizationRuleService.parseAuthorizationRule(config)).thenReturn(authRulePatterns);

        // Initialize
        provider.init();
        assertThat(provider.isEnabled()).isTrue();

        // Disable
        provider.disable();
        assertThat(provider.isEnabled()).isFalse();
        JwtVerificationStrategy strategyField = (JwtVerificationStrategy) ReflectionTestUtils.getField(provider, "verificationStrategy");
        assertThat(strategyField).isNull();

        // Re-enable
        provider.enable();
        assertThat(provider.isEnabled()).isTrue();
        JwtVerificationStrategy strategyFieldAfterEnable = (JwtVerificationStrategy) ReflectionTestUtils.getField(provider, "verificationStrategy");
        assertThat(strategyFieldAfterEnable).isNotNull().isInstanceOf(HmacJwtVerificationStrategy.class);
    }

    @Test
    public void testEnableThrowsWhenConfigOrRulesMissing() {
        // Case 1: Both configuration and authRulePatterns are null
        verifyExceptionThrowsForEnableWhenConfigOrRulesMissing();

        // Case 2: Only configuration is null
        ReflectionTestUtils.setField(provider, "configuration", null);
        ReflectionTestUtils.setField(provider, "authRulePatterns", mock(AuthRulePatterns.class));

        verifyExceptionThrowsForEnableWhenConfigOrRulesMissing();

        // Case 3: Only authRulePatterns is null
        ReflectionTestUtils.setField(provider, "configuration", mock(JwtMqttAuthProviderConfiguration.class));
        ReflectionTestUtils.setField(provider, "authRulePatterns", null);

        verifyExceptionThrowsForEnableWhenConfigOrRulesMissing();
    }

    private void verifyExceptionThrowsForEnableWhenConfigOrRulesMissing() {
        assertThatThrownBy(() -> provider.enable())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot enable JWT provider! Provider configuration or authorization rules are missing!");
    }

    @Test
    public void testEnableDoesNothingWhenAlreadyEnabled() {
        ReflectionTestUtils.setField(provider, "verificationStrategy", verificationStrategy);
        assertThatCode(() -> provider.enable()).doesNotThrowAnyException();
        JwtVerificationStrategy actualVerificationStrategy = (JwtVerificationStrategy) ReflectionTestUtils.getField(provider, "verificationStrategy");
        assertThat(actualVerificationStrategy).isSameAs(verificationStrategy);
    }

    @Test
    public void testOnProviderUpdateDisablesProvider() throws Exception {
        JwtMqttAuthProviderConfiguration config = mock(JwtMqttAuthProviderConfiguration.class);

        when(authorizationRuleService.parseAuthorizationRule(config)).thenReturn(authRulePatterns);

        // Call onProviderUpdate with enabled=false
        provider.onProviderUpdate(false, config);

        // The Provider remains disabled, but config and authRulePatterns are set
        assertThat(provider.isEnabled()).isFalse();

        JwtVerificationStrategy strategyField = (JwtVerificationStrategy) ReflectionTestUtils.getField(provider, "verificationStrategy");
        assertThat(strategyField).isNull();

        JwtMqttAuthProviderConfiguration configField = (JwtMqttAuthProviderConfiguration) ReflectionTestUtils.getField(provider, "configuration");
        AuthRulePatterns patternsField = (AuthRulePatterns) ReflectionTestUtils.getField(provider, "authRulePatterns");

        assertThat(configField).isSameAs(config);
        assertThat(patternsField).isSameAs(authRulePatterns);
    }

    @Test
    public void testOnProviderUpdateEnablesProvider() throws Exception {
        assertThat(provider.isEnabled()).isFalse();

        // Internal config and rules are null
        JwtMqttAuthProviderConfiguration configField = (JwtMqttAuthProviderConfiguration) ReflectionTestUtils.getField(provider, "configuration");
        AuthRulePatterns patternsField = (AuthRulePatterns) ReflectionTestUtils.getField(provider, "authRulePatterns");

        assertThat(configField).isNull();
        assertThat(patternsField).isNull();

        HmacBasedAlgorithmConfiguration hmacConfig = mock(HmacBasedAlgorithmConfiguration.class);
        when(hmacConfig.getSecret()).thenReturn(TestUtils.generateHmac32Secret());

        AlgorithmBasedVerifierConfiguration algConfig = mock(AlgorithmBasedVerifierConfiguration.class);
        when(algConfig.getJwtSignAlgorithmConfiguration()).thenReturn(hmacConfig);

        JwtMqttAuthProviderConfiguration config = mock(JwtMqttAuthProviderConfiguration.class);
        when(config.getJwtVerifierConfiguration()).thenReturn(algConfig);

        when(authorizationRuleService.parseAuthorizationRule(config)).thenReturn(authRulePatterns);

        // Call onProviderUpdate with enabled=true
        provider.onProviderUpdate(true, config);

        // Provider is enabled and strategy is set
        assertThat(provider.isEnabled()).isTrue();

        JwtVerificationStrategy strategyField = (JwtVerificationStrategy) ReflectionTestUtils.getField(provider, "verificationStrategy");
        assertThat(strategyField).isNotNull().isInstanceOf(HmacJwtVerificationStrategy.class);

        // Internal config and rules updated as well
        configField = (JwtMqttAuthProviderConfiguration) ReflectionTestUtils.getField(provider, "configuration");
        patternsField = (AuthRulePatterns) ReflectionTestUtils.getField(provider, "authRulePatterns");

        assertThat(configField).isSameAs(config);
        assertThat(patternsField).isSameAs(authRulePatterns);
    }

    @Test
    public void testAuthenticateReturnsProviderDisabledIfStrategyIsNull() {
        AuthResponse response = provider.authenticate(authContext);

        assertThat(response.isFailure()).isTrue();
        assertThat(response.getReason()).isEqualTo(MqttAuthProviderType.JWT + " authentication is disabled!");
    }

    @Test
    public void testAuthenticateReturnsFailureIfPasswordIsNull() throws Exception {
        ReflectionTestUtils.setField(provider, "verificationStrategy", verificationStrategy);

        when(authContext.getPasswordBytes()).thenReturn(null);

        AuthResponse response = provider.authenticate(authContext);

        assertThat(response.isFailure()).isTrue();
        assertThat(response.getReason()).isEqualTo("Failed to fetch JWT authentication token from password.");
        verify(verificationStrategy, never()).authenticateJwt(any(), any());
    }

    @Test
    public void testAuthenticateCallsStrategyAndReturnsResult() throws Exception {
        ReflectionTestUtils.setField(provider, "verificationStrategy", verificationStrategy);

        byte[] passwordBytes = "jwtToken".getBytes(StandardCharsets.UTF_8);
        when(authContext.getPasswordBytes()).thenReturn(passwordBytes);

        AuthResponse expected = AuthResponse.success(null, null, MqttAuthProviderType.JWT.name());
        when(verificationStrategy.authenticateJwt(any(), any())).thenReturn(expected);

        AuthResponse response = provider.authenticate(authContext);

        assertThat(response).isEqualTo(expected);
        verify(verificationStrategy).authenticateJwt(authContext, "jwtToken");
    }

}
