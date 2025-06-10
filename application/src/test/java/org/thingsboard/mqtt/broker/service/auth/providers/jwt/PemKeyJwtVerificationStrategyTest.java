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

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.Ed25519Signer;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.common.data.util.SslUtil;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthContext;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthResponse;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.EdECPrivateKey;
import java.security.interfaces.EdECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@RunWith(MockitoJUnitRunner.class)
public class PemKeyJwtVerificationStrategyTest {

    @Mock
    JwtClaimsValidator claimsValidatorMock;

    private PemKeyJwtVerificationStrategy strategy;

    @Test
    public void givenEd448Key_whenConstructStrategy_thenThrows() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("Ed448");
        KeyPair keyPair = keyGen.generateKeyPair();
        PublicKey ed448PublicKey = keyPair.getPublic();
        String pem = toPemString(ed448PublicKey);

        // WHEN-THEN
        assertThatThrownBy(() -> new PemKeyJwtVerificationStrategy(pem, claimsValidatorMock))
                .isInstanceOf(JOSEException.class)
                .hasMessage("Ed25519Verifier only supports OctetKeyPairs with crv=Ed25519");
    }

    @Test
    public void givenDsaKey_whenConstructStrategy_thenThrowsUnsupportedType() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("DSA");
        keyGen.initialize(1024);
        KeyPair keyPair = keyGen.generateKeyPair();

        PublicKey dsaPublicKey = keyPair.getPublic();
        String pem = toPemString(dsaPublicKey);

        // WHEN-THEN
        assertThatThrownBy(() -> new PemKeyJwtVerificationStrategy(pem, claimsValidatorMock))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported public key type for PEM: DSA");
    }


    @Test
    public void givenRSASignedJwt_whenAuthJwt_thenSuccess() throws Exception {
        // GIVEN
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();

        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        String publicPemKeyStr = toPemString(publicKey);

        strategy = new PemKeyJwtVerificationStrategy(publicPemKeyStr, claimsValidatorMock);

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder().subject("test").build();

        String signedRSAJwt = createSignedRSAJwt(claimsSet, privateKey);
        byte[] passwordBytes = signedRSAJwt.getBytes(StandardCharsets.UTF_8);
        AuthContext authContext = new AuthContext("testClientId", "testUsername",
                passwordBytes, null);

        AuthResponse mockResponse = AuthResponse.defaultAuthResponse();
        given(claimsValidatorMock.validateAll(any(), any())).willReturn(mockResponse);

        // WHEN-THEN
        AuthResponse authResponse = strategy.authenticateJwt(authContext, signedRSAJwt);
        assertThat(authResponse).isEqualTo(mockResponse);
        then(claimsValidatorMock).should().validateAll(authContext, claimsSet);
    }

    @Test
    public void givenRsaSignedJwtWithWrongPublicKey_whenAuthJwt_thenFailure() throws Exception {
        // GIVEN
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);

        KeyPair correctKeyPair = keyGen.generateKeyPair();
        KeyPair wrongKeyPair = keyGen.generateKeyPair();

        RSAPrivateKey signingKey = (RSAPrivateKey) correctKeyPair.getPrivate();
        RSAPublicKey wrongPublicKey = (RSAPublicKey) wrongKeyPair.getPublic();

        String wrongPublicPem = toPemString(wrongPublicKey);

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder().subject("test").build();

        String signedRSAJwt = createSignedRSAJwt(claimsSet, signingKey);
        byte[] passwordBytes = signedRSAJwt.getBytes(StandardCharsets.UTF_8);
        AuthContext authContext = new AuthContext("clientId", "username", passwordBytes, null);

        // WHEN: create verifier with a wrong key and validate
        PemKeyJwtVerificationStrategy strategy = new PemKeyJwtVerificationStrategy(wrongPublicPem, claimsValidatorMock);

        AuthResponse response = strategy.authenticateJwt(authContext, signedRSAJwt);

        // THEN: verification should fail
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getReason()).isEqualTo("JWT signature validation failed.");
    }

    @Test
    public void givenECSignedJwt_whenAuthJwt_thenSuccess() throws Exception {
        // GIVEN
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
        keyGen.initialize(256); // for ES256 (P-256)
        KeyPair keyPair = keyGen.generateKeyPair();

        ECPublicKey publicKey = (ECPublicKey) keyPair.getPublic();
        ECPrivateKey privateKey = (ECPrivateKey) keyPair.getPrivate();
        String publicPemKeyStr = toPemString(publicKey);

        strategy = new PemKeyJwtVerificationStrategy(publicPemKeyStr, claimsValidatorMock);

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder().subject("test").build();

        String signedECJwt = createSignedECJwt(claimsSet, privateKey);
        byte[] passwordBytes = signedECJwt.getBytes(StandardCharsets.UTF_8);
        AuthContext authContext = new AuthContext("testClientId", "testUsername",
                passwordBytes, null);

        AuthResponse mockResponse = AuthResponse.defaultAuthResponse();
        given(claimsValidatorMock.validateAll(any(), any())).willReturn(mockResponse);

        // WHEN-THEN
        AuthResponse authResponse = strategy.authenticateJwt(authContext, signedECJwt);
        assertThat(authResponse).isEqualTo(mockResponse);
        then(claimsValidatorMock).should().validateAll(authContext, claimsSet);
    }

    @Test
    public void givenEd25519SignedJwt_whenAuthJwt_thenSuccess() throws Exception {
        // GIVEN
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("Ed25519");
        KeyPair keyPair = keyGen.generateKeyPair();

        EdECPublicKey publicKey = (EdECPublicKey) keyPair.getPublic();
        EdECPrivateKey privateKey = (EdECPrivateKey) keyPair.getPrivate();
        String publicPemKeyStr = toPemString(publicKey);

        strategy = new PemKeyJwtVerificationStrategy(publicPemKeyStr, claimsValidatorMock);

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder().subject("test").build();

        String signedEdJwt = createSignedEd25519Jwt(claimsSet, publicKey, privateKey);
        byte[] passwordBytes = signedEdJwt.getBytes(StandardCharsets.UTF_8);
        AuthContext authContext = new AuthContext("testClientId", "testUsername", passwordBytes, null);

        AuthResponse mockResponse = AuthResponse.defaultAuthResponse();
        given(claimsValidatorMock.validateAll(any(), any())).willReturn(mockResponse);

        // WHEN-THEN
        AuthResponse authResponse = strategy.authenticateJwt(authContext, signedEdJwt);
        assertThat(authResponse).isEqualTo(mockResponse);
        then(claimsValidatorMock).should().validateAll(authContext, claimsSet);
    }

    private String createSignedRSAJwt(JWTClaimsSet claimsSet, RSAPrivateKey rsaPrivateKey) throws JOSEException {
        JWSSigner signer = new RSASSASigner(rsaPrivateKey);
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claimsSet);
        jwt.sign(signer);
        return jwt.serialize();
    }

    private String createSignedECJwt(JWTClaimsSet claimsSet, ECPrivateKey ecPrivateKey) throws JOSEException {
        JWSSigner signer = new ECDSASigner(ecPrivateKey);
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.ES256), claimsSet);
        jwt.sign(signer);
        return jwt.serialize();
    }

    private String createSignedEd25519Jwt(JWTClaimsSet claimsSet, EdECPublicKey edPublicKey, EdECPrivateKey edPrivateKey) throws JOSEException {
        JWSSigner signer = new Ed25519Signer(SslUtil.edEcPrivateKeyToOctetKeyPair(edPublicKey, edPrivateKey));
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.EdDSA), claimsSet);
        jwt.sign(signer);
        return jwt.serialize();
    }

    private static String toPemString(PublicKey publicKey) throws IOException {
        StringWriter str = new StringWriter();
        PemWriter pemWriter = new PemWriter(str);
        pemWriter.writeObject(new PemObject("PUBLIC KEY", publicKey.getEncoded()));
        pemWriter.flush();
        pemWriter.close();
        return str.toString();
    }

}
