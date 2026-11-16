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
package org.thingsboard.mqtt.broker.service.auth.enhanced;

import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.common.data.client.credentials.ScramAlgorithm;
import org.thingsboard.mqtt.broker.service.auth.enhanced.ScramTestClient.Mechanism;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScramTestClientTest {

    private static final String RFC_CLIENT_NONCE = "rOprNGfwEbeRWgbNEkqO";
    private static final String RFC_SERVER_FIRST =
            "r=rOprNGfwEbeRWgbNEkqO%hvYDpWUa2RaTCAfuxFIlj)hNlF$k0,s=W22ZaJ0SNY7soEsUEjb6gQ==,i=4096";

    @Test
    void rfc7677Section3Vector() {
        var client = new ScramTestClient("user", "pencil", Mechanism.SCRAM_SHA_256, RFC_CLIENT_NONCE);

        assertThat(utf8(client.clientFirstMessage())).isEqualTo("n,,n=user,r=rOprNGfwEbeRWgbNEkqO");
        assertThat(utf8(client.clientFinalMessage(utf8Bytes(RFC_SERVER_FIRST)))).isEqualTo(
                "c=biws,r=rOprNGfwEbeRWgbNEkqO%hvYDpWUa2RaTCAfuxFIlj)hNlF$k0,p=dHzbZapWIk4jUhN+Ute9ytag9zjfMHgsqmmiz7AndVQ=");
        client.verifyServerFinalMessage(utf8Bytes("v=6rriTRBi23WpRR/wtup+mMhUZUn/dB5nLTJRsjl95G4="));
    }

    @Test
    void aWrongServerSignatureIsRejected() {
        var client = new ScramTestClient("user", "pencil", Mechanism.SCRAM_SHA_256, RFC_CLIENT_NONCE);
        client.clientFirstMessage();
        client.clientFinalMessage(utf8Bytes(RFC_SERVER_FIRST));

        assertThatThrownBy(() -> client.verifyServerFinalMessage(
                utf8Bytes("v=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aServerErrorInsteadOfASignatureIsRejected() {
        var client = new ScramTestClient("user", "pencil", Mechanism.SCRAM_SHA_256, RFC_CLIENT_NONCE);
        client.clientFirstMessage();
        client.clientFinalMessage(utf8Bytes(RFC_SERVER_FIRST));

        assertThatThrownBy(() -> client.verifyServerFinalMessage(utf8Bytes("e=invalid-proof")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aServerNonceThatDoesNotStartWithTheClientNonceIsRejected() {
        var client = new ScramTestClient("user", "pencil", Mechanism.SCRAM_SHA_256, RFC_CLIENT_NONCE);
        client.clientFirstMessage();

        assertThatThrownBy(() -> client.clientFinalMessage(utf8Bytes(
                "r=someOtherNonce%hvYDpWUa2RaTCAfuxFIlj)hNlF$k0,s=W22ZaJ0SNY7soEsUEjb6gQ==,i=4096")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aUsernameWithCommaAndEqualsIsEscaped() {
        var client = new ScramTestClient("a,b=c", "pencil", Mechanism.SCRAM_SHA_512, RFC_CLIENT_NONCE);

        assertThat(utf8(client.clientFirstMessage())).isEqualTo("n,,n=a=2Cb=3Dc,r=" + RFC_CLIENT_NONCE);
    }

    @Test
    void eachClientGeneratesItsOwnNonce() {
        var first = utf8(new ScramTestClient("user", "pencil", Mechanism.SCRAM_SHA_256).clientFirstMessage());
        var second = utf8(new ScramTestClient("user", "pencil", Mechanism.SCRAM_SHA_256).clientFirstMessage());

        assertThat(first).startsWith("n,,n=user,r=").isNotEqualTo(second);
    }

    @Test
    void itAuthenticatesAgainstTbScramSaslServerForBothMechanisms() throws Exception {
        Map<Mechanism, ScramAlgorithm> algorithms = Map.of(
                Mechanism.SCRAM_SHA_256, ScramAlgorithm.SHA_256,
                Mechanism.SCRAM_SHA_512, ScramAlgorithm.SHA_512);

        for (var entry : algorithms.entrySet()) {
            var algorithm = entry.getValue();
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            var server = TbScramSaslServer.create(algorithm.getMqttAlgorithmName(),
                    handlerFor(credential(algorithm, "s3cret", salt))).orElseThrow();
            var client = new ScramTestClient("alice", "s3cret", entry.getKey());

            byte[] serverFirst = server.evaluateResponse(client.clientFirstMessage());
            byte[] serverFinal = server.evaluateResponse(client.clientFinalMessage(serverFirst));
            client.verifyServerFinalMessage(serverFinal);

            assertThat(server.isComplete()).as(algorithm.getMqttAlgorithmName()).isTrue();
            assertThat(server.getAuthorizationID()).isEqualTo("alice");
        }
    }

    @Test
    void aWrongPasswordIsRejectedByTbScramSaslServer() throws Exception {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        var server = TbScramSaslServer.create(ScramAlgorithm.SHA_256.getMqttAlgorithmName(),
                handlerFor(credential(ScramAlgorithm.SHA_256, "s3cret", salt))).orElseThrow();
        var client = new ScramTestClient("alice", "wrong", Mechanism.SCRAM_SHA_256);

        byte[] serverFirst = server.evaluateResponse(client.clientFirstMessage());

        assertThatThrownBy(() -> server.evaluateResponse(client.clientFinalMessage(serverFirst)))
                .isInstanceOf(javax.security.sasl.SaslException.class);
        assertThat(server.isComplete()).isFalse();
    }

    private static CallbackHandler handlerFor(ScramCredential credential) {
        return callbacks -> {
            for (Callback callback : callbacks) {
                if (callback instanceof NameCallback) {
                    continue;
                }
                if (callback instanceof ScramCredentialCallback credentialCallback) {
                    credentialCallback.setScramCredential(credential);
                }
            }
        };
    }

    private static ScramCredential credential(ScramAlgorithm algorithm, String password, byte[] salt) throws Exception {
        int iterations = 4096;
        var spec = new PBEKeySpec(password.toCharArray(), salt, iterations, algorithm.getHashByteSize() * 8);
        byte[] saltedPassword = SecretKeyFactory.getInstance(algorithm.getPbkdf2Algorithm()).generateSecret(spec).getEncoded();
        byte[] clientKey = hmac(algorithm, saltedPassword, "Client Key");
        byte[] storedKey = MessageDigest.getInstance(algorithm.getHashAlgorithm()).digest(clientKey);
        byte[] serverKey = hmac(algorithm, saltedPassword, "Server Key");
        return new ScramCredential(salt, storedKey, serverKey, iterations);
    }

    private static byte[] hmac(ScramAlgorithm algorithm, byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance(algorithm.getHmacAlgorithm());
        mac.init(new SecretKeySpec(key, algorithm.getHmacAlgorithm()));
        return mac.doFinal(data.getBytes(UTF_8));
    }

    private static String utf8(byte[] bytes) {
        return new String(bytes, UTF_8);
    }

    private static byte[] utf8Bytes(String value) {
        return value.getBytes(UTF_8);
    }

}
