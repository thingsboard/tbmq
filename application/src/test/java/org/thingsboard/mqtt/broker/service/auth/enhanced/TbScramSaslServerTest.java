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

import org.apache.kafka.common.security.scram.internals.ScramSaslClientProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.common.data.client.credentials.ScramAlgorithm;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.thingsboard.mqtt.broker.service.auth.DefaultEnhancedAuthenticationService.SCRAM_SASL_PROPS;
import static org.thingsboard.mqtt.broker.service.auth.DefaultEnhancedAuthenticationService.SCRAM_SASL_PROTOCOL;

class TbScramSaslServerTest {

    private static final String PASSWORD = "pencil";
    private static final String RFC_SALT = "W22ZaJ0SNY7soEsUEjb6gQ==";
    private static final String RFC_SERVER_NONCE = "%hvYDpWUa2RaTCAfuxFIlj)hNlF$k0";
    private static final String RFC_CLIENT_NONCE = "rOprNGfwEbeRWgbNEkqO";
    private static final String RFC_COMBINED_NONCE = RFC_CLIENT_NONCE + RFC_SERVER_NONCE;
    private static final String RFC_PROOF = "dHzbZapWIk4jUhN+Ute9ytag9zjfMHgsqmmiz7AndVQ=";
    private static final int ITERATIONS = 4096;

    private final AtomicReference<String> lookedUpName = new AtomicReference<>();
    private TbScramSaslServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = newServer(ScramAlgorithm.SHA_256, credential(ScramAlgorithm.SHA_256, PASSWORD, b64(RFC_SALT)), RFC_SERVER_NONCE);
    }

    @Test
    void rfc7677Sha256Exchange() throws Exception {
        byte[] serverFirst = server.evaluateResponse(utf8("n,,n=user,r=rOprNGfwEbeRWgbNEkqO"));
        assertThat(new String(serverFirst, UTF_8)).isEqualTo(
                "r=rOprNGfwEbeRWgbNEkqO%hvYDpWUa2RaTCAfuxFIlj)hNlF$k0,s=W22ZaJ0SNY7soEsUEjb6gQ==,i=4096");
        byte[] serverFinal = server.evaluateResponse(utf8(
                "c=biws,r=rOprNGfwEbeRWgbNEkqO%hvYDpWUa2RaTCAfuxFIlj)hNlF$k0,p=dHzbZapWIk4jUhN+Ute9ytag9zjfMHgsqmmiz7AndVQ="));
        assertThat(new String(serverFinal, UTF_8)).isEqualTo("v=6rriTRBi23WpRR/wtup+mMhUZUn/dB5nLTJRsjl95G4=");
        assertThat(server.isComplete()).isTrue();
        assertThat(server.getAuthorizationID()).isEqualTo("user");
        assertThat(server.getNegotiatedProperty("anything")).isNull();
    }

    @Test
    void sha256AndSha512RoundTripWithKafkaScramClient() throws Exception {
        for (ScramAlgorithm algorithm : ScramAlgorithm.values()) {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            TbScramSaslServer scramServer = TbScramSaslServer.create(algorithm.getMqttAlgorithmName(),
                    handlerFor(credential(algorithm, "s3cret", salt))).orElseThrow();
            SaslClient client = createScramSaslClient("alice", "s3cret", algorithm);

            byte[] clientFirst = client.evaluateChallenge(new byte[0]);
            byte[] serverFirst = scramServer.evaluateResponse(clientFirst);
            byte[] clientFinal = client.evaluateChallenge(serverFirst);
            byte[] serverFinal = scramServer.evaluateResponse(clientFinal);
            client.evaluateChallenge(serverFinal);

            assertThat(scramServer.isComplete()).as(algorithm.getMqttAlgorithmName()).isTrue();
            assertThat(client.isComplete()).as(algorithm.getMqttAlgorithmName()).isTrue();
            assertThat(scramServer.getAuthorizationID()).isEqualTo("alice");
            assertThat(scramServer.getMechanismName()).isEqualTo(algorithm.getMqttAlgorithmName());
        }
    }

    @Test
    void escapedUsernameIsDecodedBeforeLookup() throws Exception {
        server.evaluateResponse(utf8("n,,n=a=2Cb=3Dc,r=x"));
        assertThat(lookedUpName.get()).isEqualTo("a,b=c");
    }

    @Test
    void authzidEqualToUsernameIsAccepted() throws Exception {
        byte[] serverFirst = server.evaluateResponse(utf8("n,a=user,n=user,r=x"));
        String clientFinal = clientFinal("n=user,r=x", new String(serverFirst, UTF_8),
                b64Str("n,a=user,"), "x" + RFC_SERVER_NONCE);
        server.evaluateResponse(utf8(clientFinal));
        assertThat(server.isComplete()).isTrue();
        assertThat(server.getAuthorizationID()).isEqualTo("user");
    }

    @Test
    void authzidDifferentFromUsernameFails() {
        assertThatThrownBy(() -> server.evaluateResponse(utf8("n,a=other,n=user,r=x"))).isInstanceOf(SaslException.class);
    }

    @Test
    void channelBindingFlagOtherThanNFails() throws Exception {
        assertThatThrownBy(() -> server.evaluateResponse(utf8("y,,n=user,r=x"))).isInstanceOf(SaslException.class);
        TbScramSaslServer other = freshServer();
        assertThatThrownBy(() -> other.evaluateResponse(utf8("p=tls-unique,,n=user,r=x"))).isInstanceOf(SaslException.class);
    }

    @Test
    void mandatoryExtensionFails() {
        assertThatThrownBy(() -> server.evaluateResponse(utf8("n,,m=ext,n=user,r=x"))).isInstanceOf(SaslException.class);
    }

    @Test
    void tokenauthExtensionFails() {
        assertThatThrownBy(() -> server.evaluateResponse(utf8("n,,n=user,r=x,tokenauth=true"))).isInstanceOf(SaslException.class);
    }

    @Test
    void unknownExtensionIsIgnored() throws Exception {
        byte[] serverFirst = server.evaluateResponse(utf8("n,,n=user,r=x,foo=bar"));
        assertThat(new String(serverFirst, UTF_8)).startsWith("r=x" + RFC_SERVER_NONCE + ",");
    }

    @Test
    void wrongProofFails() throws Exception {
        server.evaluateResponse(utf8("n,,n=user,r=" + RFC_CLIENT_NONCE));
        byte[] proof = Base64.getDecoder().decode(RFC_PROOF);
        proof[0] ^= 1;
        String clientFinal = "c=biws,r=" + RFC_COMBINED_NONCE + ",p=" + Base64.getEncoder().encodeToString(proof);

        assertThatThrownBy(() -> server.evaluateResponse(utf8(clientFinal))).isInstanceOf(SaslException.class);
        assertThat(server.isComplete()).isFalse();
    }

    @Test
    void wrongNonceFails() throws Exception {
        byte[] serverFirst = server.evaluateResponse(utf8("n,,n=user,r=x"));
        // the nonce ends with the combined nonce (all Kafka checks) but is not equal to it; the proof is valid for this very message
        String clientFinal = clientFinal("n=user,r=x", new String(serverFirst, UTF_8), "biws", "a" + "x" + RFC_SERVER_NONCE);

        assertThatThrownBy(() -> server.evaluateResponse(utf8(clientFinal))).isInstanceOf(SaslException.class)
                .hasMessageContaining("nonce");
        assertThat(server.isComplete()).isFalse();
    }

    @Test
    void wrongChannelBindingInFinalFails() throws Exception {
        byte[] serverFirst = server.evaluateResponse(utf8("n,,n=user,r=x"));
        String clientFinal = clientFinal("n=user,r=x", new String(serverFirst, UTF_8), b64Str("y,,"), "x" + RFC_SERVER_NONCE);

        assertThatThrownBy(() -> server.evaluateResponse(utf8(clientFinal))).isInstanceOf(SaslException.class)
                .hasMessageContaining("channel binding");
        assertThat(server.isComplete()).isFalse();
    }

    @Test
    void credentialForOtherAlgorithmFails() throws Exception {
        TbScramSaslServer sha256Server = newServer(ScramAlgorithm.SHA_256,
                credential(ScramAlgorithm.SHA_512, PASSWORD, b64(RFC_SALT)), RFC_SERVER_NONCE);
        assertThatThrownBy(() -> sha256Server.evaluateResponse(utf8("n,,n=user,r=x"))).isInstanceOf(SaslException.class);
    }

    @Test
    void lookupFailureBecomesSaslException() {
        RuntimeException failure = new RuntimeException("unknown user");
        CallbackHandler failing = callbacks -> {
            throw failure;
        };
        TbScramSaslServer failingServer = new TbScramSaslServer(ScramAlgorithm.SHA_256, failing, () -> "srv");

        assertThatThrownBy(() -> failingServer.evaluateResponse(utf8("n,,n=user,r=x")))
                .isInstanceOf(SaslException.class)
                .hasCause(failure);
    }

    @Test
    void callsAfterCompletionOrFailureThrowSaslException() throws Exception {
        server.evaluateResponse(utf8("n,,n=user,r=" + RFC_CLIENT_NONCE));
        server.evaluateResponse(utf8("c=biws,r=" + RFC_COMBINED_NONCE + ",p=" + RFC_PROOF));
        assertThat(server.isComplete()).isTrue();
        assertThatThrownBy(() -> server.evaluateResponse(utf8("n,,n=user,r=x"))).isInstanceOf(SaslException.class);

        TbScramSaslServer failed = freshServer();
        assertThatThrownBy(() -> failed.evaluateResponse(utf8("y,,n=user,r=x"))).isInstanceOf(SaslException.class);
        assertThatThrownBy(() -> failed.evaluateResponse(utf8("n,,n=user,r=x"))).isInstanceOf(SaslException.class);
        assertThat(failed.isComplete()).isFalse();
    }

    @Test
    void malformedMessagesFail() throws Exception {
        String[] clientFirsts = {
                "n,,r=x",                  // no username
                "n,,n=user",               // no nonce
                "n,,n=user,r=",            // empty nonce
                "n,,n=user,x=1,r=x",       // nonce not in its place
                "n,,n=a=2Xb,r=x",          // invalid saslname escape
                "n,,n=a=,r=x",             // dangling escape
                "n,,n=user,r=x,junk",      // extension without a value
                "n,user,n=user,r=x",       // malformed authzid
                "n",                       // truncated
        };
        for (String clientFirst : clientFirsts) {
            assertSaslFailure(freshServer(), clientFirst);
        }

        assertSaslFailure(freshServer(), (byte[]) null);
        assertSaslFailure(freshServer(), new byte[0]);

        String valid = "c=biws,r=" + RFC_COMBINED_NONCE;
        String[] clientFinals = {
                "c=biws,r=" + RFC_COMBINED_NONCE,                                   // no proof
                "c=biws,p=" + RFC_PROOF,                                            // no nonce
                "r=" + RFC_COMBINED_NONCE + ",c=biws,p=" + RFC_PROOF,               // out of order
                "r=" + RFC_COMBINED_NONCE + ",p=" + RFC_PROOF,                      // no channel binding
                valid + ",p=!!!not-base64!!!",                                      // proof not base64
                valid + ",p=" + Base64.getEncoder().encodeToString(new byte[5]),    // proof of a wrong length
                valid + ",p=",                                                      // empty proof
        };
        for (String clientFinal : clientFinals) {
            TbScramSaslServer s = freshServer();
            s.evaluateResponse(utf8("n,,n=user,r=" + RFC_CLIENT_NONCE));
            assertSaslFailure(s, clientFinal);
            assertThat(s.isComplete()).isFalse();
        }
    }

    @Test
    void createIsEmptyForUnsupportedMechanisms() {
        CallbackHandler handler = callbacks -> {
        };
        assertThat(TbScramSaslServer.create("SCRAM-SHA-1", handler)).isEmpty();
        assertThat(TbScramSaslServer.create("PLAIN", handler)).isEmpty();
        assertThat(TbScramSaslServer.create(null, handler)).isEmpty();
        assertThat(TbScramSaslServer.create("SCRAM-SHA-256", handler)).isPresent();
        assertThat(TbScramSaslServer.create("SCRAM-SHA-512", handler)).isPresent();
    }

    @Test
    void sharedCredentialArraysAreNeverModified() throws Exception {
        ScramCredential shared = credential(ScramAlgorithm.SHA_256, PASSWORD, b64(RFC_SALT));
        ScramCredential snapshot = new ScramCredential(shared.salt().clone(), shared.storedKey().clone(),
                shared.serverKey().clone(), shared.iterations());
        TbScramSaslServer s = newServer(ScramAlgorithm.SHA_256, shared, RFC_SERVER_NONCE);
        s.evaluateResponse(utf8("n,,n=user,r=" + RFC_CLIENT_NONCE));
        s.evaluateResponse(utf8("c=biws,r=" + RFC_COMBINED_NONCE + ",p=" + RFC_PROOF));
        s.dispose();

        assertThat(shared.salt()).isEqualTo(snapshot.salt());
        assertThat(shared.storedKey()).isEqualTo(snapshot.storedKey());
        assertThat(shared.serverKey()).isEqualTo(snapshot.serverKey());
    }

    @Test
    void wrapAndUnwrapAreNotSupported() {
        assertThatThrownBy(() -> server.wrap(new byte[1], 0, 1)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> server.unwrap(new byte[1], 0, 1)).isInstanceOf(IllegalStateException.class);
    }

    // ---- helpers ----

    private void assertSaslFailure(TbScramSaslServer s, String message) {
        assertSaslFailure(s, utf8(message));
    }

    private void assertSaslFailure(TbScramSaslServer s, byte[] message) {
        assertThatThrownBy(() -> s.evaluateResponse(message)).isInstanceOf(SaslException.class);
    }

    private TbScramSaslServer freshServer() throws Exception {
        return newServer(ScramAlgorithm.SHA_256, credential(ScramAlgorithm.SHA_256, PASSWORD, b64(RFC_SALT)), RFC_SERVER_NONCE);
    }

    private TbScramSaslServer newServer(ScramAlgorithm algorithm, ScramCredential credential, String serverNonce) {
        return new TbScramSaslServer(algorithm, handlerFor(credential), () -> serverNonce);
    }

    private CallbackHandler handlerFor(ScramCredential credential) {
        return callbacks -> {
            for (Callback callback : callbacks) {
                if (callback instanceof NameCallback nameCallback) {
                    lookedUpName.set(nameCallback.getDefaultName());
                } else if (callback instanceof ScramCredentialCallback credentialCallback) {
                    credentialCallback.setScramCredential(credential);
                }
            }
        };
    }

    /**
     * Builds a client-final message with a valid proof computed over the AuthMessage that contains exactly the given
     * channel binding and nonce, so only the checks on those values can reject it.
     */
    private static String clientFinal(String clientFirstBare, String serverFirst, String channelBinding, String nonce)
            throws Exception {
        String withoutProof = "c=" + channelBinding + ",r=" + nonce;
        String authMessage = clientFirstBare + "," + serverFirst + "," + withoutProof;
        return withoutProof + ",p=" + Base64.getEncoder().encodeToString(
                clientProof(ScramAlgorithm.SHA_256, PASSWORD, b64(RFC_SALT), authMessage));
    }

    private static byte[] clientProof(ScramAlgorithm algorithm, String password, byte[] salt, String authMessage) throws Exception {
        byte[] clientKey = hmac(algorithm, saltedPassword(algorithm, password, salt), "Client Key".getBytes(UTF_8));
        byte[] storedKey = MessageDigest.getInstance(algorithm.getHashAlgorithm()).digest(clientKey);
        byte[] signature = hmac(algorithm, storedKey, authMessage.getBytes(UTF_8));
        byte[] proof = new byte[clientKey.length];
        for (int i = 0; i < proof.length; i++) {
            proof[i] = (byte) (clientKey[i] ^ signature[i]);
        }
        return proof;
    }

    private static ScramCredential credential(ScramAlgorithm algorithm, String password, byte[] salt) throws Exception {
        byte[] saltedPassword = saltedPassword(algorithm, password, salt);
        byte[] clientKey = hmac(algorithm, saltedPassword, "Client Key".getBytes(UTF_8));
        byte[] storedKey = MessageDigest.getInstance(algorithm.getHashAlgorithm()).digest(clientKey);
        byte[] serverKey = hmac(algorithm, saltedPassword, "Server Key".getBytes(UTF_8));
        return new ScramCredential(salt, storedKey, serverKey, ITERATIONS);
    }

    private static byte[] saltedPassword(ScramAlgorithm algorithm, String password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, algorithm.getHashByteSize() * 8);
        return SecretKeyFactory.getInstance(algorithm.getPbkdf2Algorithm()).generateSecret(spec).getEncoded();
    }

    private static byte[] hmac(ScramAlgorithm algorithm, byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance(algorithm.getHmacAlgorithm());
        mac.init(new SecretKeySpec(key, algorithm.getHmacAlgorithm()));
        return mac.doFinal(data);
    }

    private static byte[] utf8(String value) {
        return value.getBytes(UTF_8);
    }

    private static byte[] b64(String value) {
        return Base64.getDecoder().decode(value);
    }

    private static String b64Str(String value) {
        return Base64.getEncoder().encodeToString(utf8(value));
    }

    private static SaslClient createScramSaslClient(String username, String password, ScramAlgorithm algorithm) throws SaslException {
        ScramSaslClientProvider.initialize();
        return Sasl.createSaslClient(
                new String[]{algorithm.getMqttAlgorithmName()},
                null,
                SCRAM_SASL_PROTOCOL,
                null,
                SCRAM_SASL_PROPS,
                new ScramClientCallbackHandler(username, password));
    }

    private static class ScramClientCallbackHandler implements CallbackHandler {

        private final String username;
        private final char[] password;

        ScramClientCallbackHandler(String username, String password) {
            this.username = username;
            this.password = password.toCharArray();
        }

        @Override
        public void handle(Callback[] callbacks) throws UnsupportedCallbackException {
            for (Callback callback : callbacks) {
                if (callback instanceof NameCallback nameCallback) {
                    nameCallback.setName(username);
                } else if (callback instanceof PasswordCallback passwordCallback) {
                    passwordCallback.setPassword(password);
                } else {
                    throw new UnsupportedCallbackException(callback);
                }
            }
        }
    }

}
