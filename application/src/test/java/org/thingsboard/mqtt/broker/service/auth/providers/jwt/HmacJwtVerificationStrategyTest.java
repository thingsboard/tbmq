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

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthContext;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthResponse;
import org.thingsboard.mqtt.broker.service.test.util.TestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HmacJwtVerificationStrategyTest {

    private static final String SECRET = TestUtils.generateHmac32Secret();

    private JwtClaimsValidator claimsValidator;
    private HmacJwtVerificationStrategy strategy;
    private AuthContext dummyContext;

    @Before
    public void setup() throws Exception {
        claimsValidator = Mockito.mock(JwtClaimsValidator.class);
        strategy = new HmacJwtVerificationStrategy(SECRET, claimsValidator);
        dummyContext = Mockito.mock(AuthContext.class); // Use a real or dummy context as needed
    }

    private String createSignedJwt(JWTClaimsSet claimsSet) throws Exception {
        JWSSigner signer = new MACSigner(SECRET);
        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader(JWSAlgorithm.HS256),
                claimsSet
        );
        signedJWT.sign(signer);
        return signedJWT.serialize();
    }

    @Test
    public void testValidJwtReturnsValidatorResult() throws Exception {
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder().subject("alice").build();
        String jwt = createSignedJwt(claimsSet);

        AuthResponse expectedResponse = AuthResponse.success(ClientType.DEVICE, null, MqttAuthProviderType.JWT.name());
        when(claimsValidator.validateAll(any(), any())).thenReturn(expectedResponse);

        AuthResponse actual = strategy.authenticateJwt(dummyContext, jwt);

        assertThat(actual).isEqualTo(expectedResponse);
        verify(claimsValidator).validateAll(any(), any());
    }

    @Test
    public void testInvalidSignatureReturnsFailure() throws Exception {
        JWSSigner otherSigner = new MACSigner(TestUtils.generateHmac32Secret());
        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader(JWSAlgorithm.HS256),
                new JWTClaimsSet.Builder().subject("bob").build()
        );
        signedJWT.sign(otherSigner);
        String jwt = signedJWT.serialize();

        AuthResponse result = strategy.authenticateJwt(dummyContext, jwt);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getReason()).isEqualTo("JWT signature validation failed.");
        verify(claimsValidator, never()).validateAll(any(), any());
    }

    @Test
    public void testExceptionFromValidatorIsPropagated() throws Exception {
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder().subject("alice").build();
        String jwt = createSignedJwt(claimsSet);

        RuntimeException runtimeException = new RuntimeException("Validation failed!");
        when(claimsValidator.validateAll(any(), any()))
                .thenThrow(runtimeException);

        assertThatThrownBy(() -> strategy.authenticateJwt(dummyContext, jwt)).isEqualTo(runtimeException);
    }

}
