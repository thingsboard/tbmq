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
package org.thingsboard.mqtt.broker.service.auth.providers.jwt;

import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.security.jwt.JwtMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthContext;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthResponse;
import org.thingsboard.mqtt.broker.service.security.authorization.AuthRulePatterns;

import java.util.Collections;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class JwtClaimsValidatorTest {

    private JwtMqttAuthProviderConfiguration configuration;
    private AuthRulePatterns authRulePatterns;
    private AuthContext authContext;

    @Before
    public void setUp() {
        configuration = mock(JwtMqttAuthProviderConfiguration.class);
        authRulePatterns = mock(AuthRulePatterns.class);
        authContext = mock(AuthContext.class);
    }

    @Test
    public void testExpiredTokenReturnsFailure() throws Exception {
        Date past = new Date(System.currentTimeMillis() - 10000);
        JWTClaimsSet claims = new JWTClaimsSet.Builder().expirationTime(past).build();

        JwtClaimsValidator validator = new JwtClaimsValidator(configuration, authRulePatterns);
        AuthResponse response = validator.validateAll(authContext, claims);

        assertThat(response.isFailure()).isTrue();
        assertThat(response.getReason()).isEqualTo("JWT token is expired.");
    }

    @Test
    public void testNotYetValidTokenReturnsFailure() throws Exception {
        Date future = new Date(System.currentTimeMillis() + 10000);
        JWTClaimsSet claims = new JWTClaimsSet.Builder().notBeforeTime(future).build();

        JwtClaimsValidator validator = new JwtClaimsValidator(configuration, authRulePatterns);
        AuthResponse response = validator.validateAll(authContext, claims);

        assertThat(response.isFailure()).isTrue();
        assertThat(response.getReason()).isEqualTo("JWT token not valid yet.");
    }

    @Test
    public void testInvalidUsernameInAuthClaimsReturnsFailure() throws Exception {
        // Auth claims: claim 'sub' must be 'expectedUser'
        Map<String, String> authClaims = Map.of("sub", "${username}");
        when(configuration.getAuthClaims()).thenReturn(authClaims);
        when(authContext.getUsername()).thenReturn("expectedUser");

        // JWT claim does not match username
        JWTClaimsSet claims = new JWTClaimsSet.Builder().claim("sub", "otherUser").build();

        JwtClaimsValidator validator = new JwtClaimsValidator(configuration, authRulePatterns);
        AuthResponse response = validator.validateAll(authContext, claims);

        assertThat(response.isFailure()).isTrue();
        assertThat(response.getReason()).contains("Failed to validate JWT auth claims");
    }

    @Test
    public void testInvalidClientIdInAuthClaimsReturnsFailure() throws Exception {
        // Auth claims: claim 'sub' must be 'expectedClientId'
        Map<String, String> authClaims = Map.of("sub", "${clientId}");
        when(configuration.getAuthClaims()).thenReturn(authClaims);
        when(authContext.getClientId()).thenReturn("expectedClientId");

        // JWT claim does not match username
        JWTClaimsSet claims = new JWTClaimsSet.Builder().claim("sub", "otherClientId").build();

        JwtClaimsValidator validator = new JwtClaimsValidator(configuration, authRulePatterns);
        AuthResponse response = validator.validateAll(authContext, claims);

        assertThat(response.isFailure()).isTrue();
        assertThat(response.getReason()).contains("Failed to validate JWT auth claims");
    }

    @Test
    public void testClientTypeResolution() throws Exception {
        // Default: DEVICE, flip to APPLICATION if all clientTypeClaims match
        Map<String, String> clientTypeClaims = Map.of("custom", "abc");
        when(configuration.getAuthClaims()).thenReturn(Collections.emptyMap());
        when(configuration.getClientTypeClaims()).thenReturn(clientTypeClaims);
        when(configuration.getDefaultClientType()).thenReturn(ClientType.DEVICE);

        JWTClaimsSet claims = new JWTClaimsSet.Builder().claim("custom", "abc").build();

        JwtClaimsValidator validator = new JwtClaimsValidator(configuration, authRulePatterns);
        AuthResponse response = validator.validateAll(authContext, claims);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getClientType()).isEqualTo(ClientType.APPLICATION);
    }

    @Test
    public void testClientTypeResolutionReturnsDefaultIfClaimMismatch() throws Exception {
        Map<String, String> clientTypeClaims = Map.of("custom", "abc", "type", "expected");
        when(configuration.getAuthClaims()).thenReturn(Collections.emptyMap());
        when(configuration.getClientTypeClaims()).thenReturn(clientTypeClaims);
        when(configuration.getDefaultClientType()).thenReturn(ClientType.DEVICE);

        // Only "custom" claim matches, "type" does not
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .claim("custom", "abc")
                .claim("type", "not-expected")
                .build();

        JwtClaimsValidator validator = new JwtClaimsValidator(configuration, authRulePatterns);
        AuthResponse response = validator.validateAll(authContext, claims);

        // Should NOT flip type: should return DEVICE
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getClientType()).isEqualTo(ClientType.DEVICE);
    }

    @Test
    public void testValidTokenReturnsSuccess() throws Exception {
        when(configuration.getAuthClaims()).thenReturn(Collections.emptyMap());
        when(configuration.getClientTypeClaims()).thenReturn(Collections.emptyMap());
        when(configuration.getDefaultClientType()).thenReturn(ClientType.APPLICATION);

        JWTClaimsSet claims = new JWTClaimsSet.Builder().build();

        JwtClaimsValidator validator = new JwtClaimsValidator(configuration, authRulePatterns);
        AuthResponse response = validator.validateAll(authContext, claims);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getClientType()).isEqualTo(ClientType.APPLICATION);
        assertThat(response.getAuthRulePatterns()).containsExactly(authRulePatterns);
    }

}
