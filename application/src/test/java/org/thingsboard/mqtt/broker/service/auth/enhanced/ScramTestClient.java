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

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Client side of SCRAM (RFC 5802, with the SHA-256 profile of RFC 7677 and its SHA-512 sibling), written from the
 * RFCs and built on the JDK only, so tests can drive the broker's SCRAM server without Kafka on the classpath.
 * <p>
 * No channel binding ({@code n,,}), no authorization identity, no SASLprep (test passwords are ASCII).
 * An instance runs one exchange: {@link #clientFirstMessage()}, {@link #clientFinalMessage(byte[])},
 * {@link #verifyServerFinalMessage(byte[])}.
 */
public final class ScramTestClient {

    public enum Mechanism {
        SCRAM_SHA_256("HmacSHA256", "PBKDF2WithHmacSHA256", "SHA-256", 32),
        SCRAM_SHA_512("HmacSHA512", "PBKDF2WithHmacSHA512", "SHA-512", 64);

        private final String hmac;
        private final String pbkdf2;
        private final String digest;
        private final int hashLength;

        Mechanism(String hmac, String pbkdf2, String digest, int hashLength) {
            this.hmac = hmac;
            this.pbkdf2 = pbkdf2;
            this.digest = digest;
            this.hashLength = hashLength;
        }
    }

    // base64("n,,"): the GS2 header without channel binding and without an authzid
    private static final String GS2_HEADER = "n,,";
    private static final String CHANNEL_BINDING = "c=" + Base64.getEncoder().encodeToString(GS2_HEADER.getBytes(UTF_8));
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final String username;
    private final String password;
    private final Mechanism mechanism;
    private final String clientNonce;

    private String clientFirstMessageBare;
    private byte[] expectedServerSignature;

    public ScramTestClient(String username, String password, Mechanism mechanism) {
        this(username, password, mechanism, randomNonce());
    }

    ScramTestClient(String username, String password, Mechanism mechanism, String clientNonce) {
        this.username = username;
        this.password = password;
        this.mechanism = mechanism;
        this.clientNonce = clientNonce;
    }

    public byte[] clientFirstMessage() {
        clientFirstMessageBare = "n=" + saslName(username) + ",r=" + clientNonce;
        return (GS2_HEADER + clientFirstMessageBare).getBytes(UTF_8);
    }

    public byte[] clientFinalMessage(byte[] serverFirstMessage) {
        if (clientFirstMessageBare == null) {
            throw new IllegalStateException("The client-first message has not been produced yet");
        }
        String serverFirst = new String(serverFirstMessage, UTF_8);
        String[] attributes = serverFirst.split(",");
        if (attributes.length < 3 || !attributes[0].startsWith("r=") || !attributes[1].startsWith("s=")
            || !attributes[2].startsWith("i=")) {
            throw new IllegalStateException("Malformed server-first message: " + serverFirst);
        }
        String nonce = attributes[0].substring(2);
        if (!nonce.startsWith(clientNonce) || nonce.length() == clientNonce.length()) {
            throw new IllegalStateException("The server nonce does not extend the client nonce");
        }
        byte[] salt = Base64.getDecoder().decode(attributes[1].substring(2));
        int iterations = Integer.parseInt(attributes[2].substring(2));
        if (iterations <= 0) {
            throw new IllegalStateException("Invalid iteration count: " + iterations);
        }

        try {
            byte[] saltedPassword = hi(salt, iterations);
            byte[] clientKey = hmac(saltedPassword, "Client Key");
            byte[] storedKey = MessageDigest.getInstance(mechanism.digest).digest(clientKey);
            byte[] serverKey = hmac(saltedPassword, "Server Key");

            String clientFinalWithoutProof = CHANNEL_BINDING + ",r=" + nonce;
            String authMessage = clientFirstMessageBare + "," + serverFirst + "," + clientFinalWithoutProof;

            byte[] clientSignature = hmac(storedKey, authMessage);
            byte[] clientProof = new byte[clientKey.length];
            for (int i = 0; i < clientProof.length; i++) {
                clientProof[i] = (byte) (clientKey[i] ^ clientSignature[i]);
            }
            expectedServerSignature = hmac(serverKey, authMessage);
            return (clientFinalWithoutProof + ",p=" + Base64.getEncoder().encodeToString(clientProof)).getBytes(UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SCRAM computation failed", e);
        }
    }

    public void verifyServerFinalMessage(byte[] serverFinalMessage) {
        if (expectedServerSignature == null) {
            throw new IllegalStateException("The client-final message has not been produced yet");
        }
        String serverFinal = new String(serverFinalMessage, UTF_8);
        if (serverFinal.startsWith("e=")) {
            throw new IllegalStateException("The server rejected the authentication: " + serverFinal);
        }
        if (!serverFinal.startsWith("v=")) {
            throw new IllegalStateException("Malformed server-final message: " + serverFinal);
        }
        byte[] signature;
        try {
            signature = Base64.getDecoder().decode(serverFinal.substring(2));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("The server signature is not valid base64", e);
        }
        if (!MessageDigest.isEqual(expectedServerSignature, signature)) {
            throw new IllegalStateException("The server signature does not match");
        }
    }

    // RFC 5802 section 2.2: Hi(str, salt, i) is PBKDF2 with HMAC as the PRF and a hash-sized output
    private byte[] hi(byte[] salt, int iterations) throws GeneralSecurityException {
        var spec = new PBEKeySpec(password.toCharArray(), salt, iterations, mechanism.hashLength * 8);
        return SecretKeyFactory.getInstance(mechanism.pbkdf2).generateSecret(spec).getEncoded();
    }

    private byte[] hmac(byte[] key, String message) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(mechanism.hmac);
        mac.init(new SecretKeySpec(key, mechanism.hmac));
        return mac.doFinal(message.getBytes(UTF_8));
    }

    // RFC 5802 section 5.1: saslname escapes "=" as "=3D" and "," as "=2C"
    private static String saslName(String name) {
        return name.replace("=", "=3D").replace(",", "=2C");
    }

    // printable ASCII without ","; base64 of random bytes qualifies
    private static String randomNonce() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

}
