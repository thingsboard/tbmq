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

import org.thingsboard.mqtt.broker.common.data.client.credentials.ScramAlgorithm;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Server side of the SCRAM-SHA-256 and SCRAM-SHA-512 SASL mechanisms (RFC 5802, RFC 7677) without channel binding.
 * <p>
 * The stored credential is obtained through the {@link CallbackHandler} with a {@link NameCallback} (its default name
 * is the decoded username) and a {@link ScramCredentialCallback}. Every failure is reported as a {@link SaslException}.
 */
public final class TbScramSaslServer implements SaslServer {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    // RFC 5802 section 7. A saslname may hold any UTF-8 except NUL, "," and a bare "="; an extension value any UTF-8
    // except NUL and ",". Repeated groups are possessive, because the backtracking form recurses once per repetition
    // and overflows the stack on long input. The lookahead keeps ",p=" out of the client-final extension loop.
    private static final String SASLNAME = "(?:[^,=\\u0000]|=2C|=3D)++";
    private static final String NONCE = "[\\x21-\\x2B\\x2D-\\x7E]+";
    private static final String FIRST_EXTENSIONS = "(?:,[A-Za-z]+=[^,\\u0000]+)*+";
    private static final String FINAL_EXTENSIONS = "(?:,[A-Za-z]+=[^,\\u0000]+(?=,))*+";
    private static final String BASE64 = "(?:[A-Za-z0-9+/]{4})*+(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?";
    private static final Pattern CLIENT_FIRST_MESSAGE = Pattern.compile(
            "n,(?:a=(" + SASLNAME + "))?,n=(" + SASLNAME + "),r=(" + NONCE + ")(" + FIRST_EXTENSIONS + ")");
    private static final Pattern CLIENT_FINAL_MESSAGE = Pattern.compile(
            "c=(" + BASE64 + "),r=(" + NONCE + ")" + FINAL_EXTENSIONS + ",p=(" + BASE64 + ")");

    private enum State {
        RECEIVE_CLIENT_FIRST_MESSAGE,
        RECEIVE_CLIENT_FINAL_MESSAGE,
        COMPLETE,
        FAILED
    }

    private final ScramAlgorithm algorithm;
    private final CallbackHandler callbackHandler;
    private final Supplier<String> serverNonceSupplier;

    private State state = State.RECEIVE_CLIENT_FIRST_MESSAGE;
    private String username;
    private String gs2Header;
    private String clientFirstMessageBare;
    private String serverFirstMessage;
    private String combinedNonce;
    // copies owned by this server, so that zero-filling them never affects arrays shared by the caller
    private byte[] salt;
    private byte[] storedKey;
    private byte[] serverKey;

    /**
     * @return empty unless the mechanism is "SCRAM-SHA-256" or "SCRAM-SHA-512"
     */
    public static Optional<TbScramSaslServer> create(String mechanism, CallbackHandler callbackHandler) {
        return ScramAlgorithm.fromMqttName(mechanism)
                .map(algorithm -> new TbScramSaslServer(algorithm, callbackHandler,
                        () -> new BigInteger(130, SECURE_RANDOM).toString(Character.MAX_RADIX)));
    }

    TbScramSaslServer(ScramAlgorithm algorithm, CallbackHandler callbackHandler, Supplier<String> serverNonceSupplier) {
        this.algorithm = algorithm;
        this.callbackHandler = callbackHandler;
        this.serverNonceSupplier = serverNonceSupplier;
    }

    @Override
    public String getMechanismName() {
        return algorithm.getMqttAlgorithmName();
    }

    @Override
    public byte[] evaluateResponse(byte[] response) throws SaslException {
        boolean succeeded = false;
        try {
            if (response == null || response.length == 0) {
                throw new SaslException("Empty SCRAM message");
            }
            String message = decodeUtf8(response);
            byte[] result = switch (state) {
                case RECEIVE_CLIENT_FIRST_MESSAGE -> processClientFirst(message);
                case RECEIVE_CLIENT_FINAL_MESSAGE -> processClientFinal(message);
                default -> throw new SaslException("Unexpected SCRAM message in state " + state);
            };
            succeeded = true;
            return result;
        } catch (SaslException e) {
            throw e;
        } catch (Exception e) {
            throw new SaslException("SCRAM authentication failed", e);
        } finally {
            if (!succeeded) {
                // fail closed on any abrupt exit, Errors included
                fail();
            }
        }
    }

    private static String decodeUtf8(byte[] bytes) throws SaslException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new SaslException("SCRAM message is not valid UTF-8", e);
        }
    }

    private byte[] processClientFirst(String message) throws SaslException {
        Matcher matcher = CLIENT_FIRST_MESSAGE.matcher(message);
        if (!matcher.matches()) {
            throw new SaslException("Malformed client-first message");
        }
        String authzid = matcher.group(1) == null ? null : unescapeSaslName(matcher.group(1));
        String clientUsername = unescapeSaslName(matcher.group(2));
        String clientNonce = matcher.group(3);
        boolean tokenAuth = false;
        for (String extension : matcher.group(4).split(",")) {
            int eq = extension.indexOf('=');
            if (eq > 0 && extension.substring(0, eq).equalsIgnoreCase("tokenauth") && Boolean.parseBoolean(extension.substring(eq + 1))) {
                tokenAuth = true;
            }
        }

        NameCallback nameCallback = new NameCallback("username", clientUsername);
        ScramCredentialCallback credentialCallback = new ScramCredentialCallback();
        try {
            callbackHandler.handle(new Callback[]{nameCallback, credentialCallback});
        } catch (Exception e) {
            throw new SaslException("Authentication failed: credentials could not be obtained", e);
        }
        // rejected only after the callback, so that the handler has seen the username of a rejected client
        // RFC 5802 section 5.1: both authzid and username are saslnames, compared after unescaping
        if (authzid != null && !authzid.equals(clientUsername)) {
            throw new SaslException("Authorization id differs from username");
        }
        if (tokenAuth) {
            throw new SaslException("Token authentication is not supported");
        }
        ScramCredential credential = credentialCallback.getScramCredential();
        if (credential == null || credential.salt() == null || credential.storedKey() == null
                || credential.serverKey() == null || credential.iterations() <= 0) {
            throw new SaslException("Authentication failed: invalid user credentials");
        }
        if (credential.storedKey().length != algorithm.getHashByteSize()
                || credential.serverKey().length != algorithm.getHashByteSize()) {
            throw new SaslException("Authentication failed: credential does not match " + algorithm.getMqttAlgorithmName());
        }
        this.salt = credential.salt().clone();
        this.storedKey = credential.storedKey().clone();
        this.serverKey = credential.serverKey().clone();

        this.username = clientUsername;
        this.gs2Header = message.substring(0, matcher.start(2) - 2);
        this.clientFirstMessageBare = message.substring(gs2Header.length());
        this.combinedNonce = clientNonce + serverNonceSupplier.get();
        this.serverFirstMessage = "r=" + combinedNonce
                + ",s=" + Base64.getEncoder().encodeToString(salt)
                + ",i=" + credential.iterations();
        this.state = State.RECEIVE_CLIENT_FINAL_MESSAGE;
        return serverFirstMessage.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] processClientFinal(String message) throws SaslException, GeneralSecurityException {
        Matcher matcher = CLIENT_FINAL_MESSAGE.matcher(message);
        if (!matcher.matches()) {
            throw new SaslException("Malformed client-final message");
        }
        String withoutProof = message.substring(0, matcher.start(3) - 3);
        String expectedChannelBinding = Base64.getEncoder().encodeToString(gs2Header.getBytes(StandardCharsets.UTF_8));
        if (!expectedChannelBinding.equals(matcher.group(1))) {
            throw new SaslException("Invalid channel binding in client-final message");
        }
        if (!combinedNonce.equals(matcher.group(2))) {
            throw new SaslException("Invalid nonce in client-final message");
        }
        byte[] clientProof = Base64.getDecoder().decode(matcher.group(3));
        if (clientProof.length != algorithm.getHashByteSize()) {
            throw new SaslException("Invalid proof length in client-final message");
        }

        byte[] authMessage = (clientFirstMessageBare + "," + serverFirstMessage + "," + withoutProof)
                .getBytes(StandardCharsets.UTF_8);
        byte[] clientSignature = hmac(storedKey, authMessage);
        byte[] clientKey = new byte[clientProof.length];
        for (int i = 0; i < clientKey.length; i++) {
            clientKey[i] = (byte) (clientProof[i] ^ clientSignature[i]);
        }
        byte[] candidateStoredKey = MessageDigest.getInstance(algorithm.getHashAlgorithm()).digest(clientKey);
        if (!MessageDigest.isEqual(candidateStoredKey, storedKey)) {
            throw new SaslException("Authentication failed: invalid client proof");
        }
        byte[] serverSignature = hmac(serverKey, authMessage);
        clearCredentials();
        state = State.COMPLETE;
        return ("v=" + Base64.getEncoder().encodeToString(serverSignature)).getBytes(StandardCharsets.UTF_8);
    }

    private byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(algorithm.getHmacAlgorithm());
        mac.init(new SecretKeySpec(key, algorithm.getHmacAlgorithm()));
        return mac.doFinal(data);
    }

    private static String unescapeSaslName(String saslName) throws SaslException {
        StringBuilder result = new StringBuilder(saslName.length());
        for (int i = 0; i < saslName.length(); i++) {
            char c = saslName.charAt(i);
            if (c != '=') {
                result.append(c);
                continue;
            }
            if (saslName.startsWith("2C", i + 1)) {
                result.append(',');
            } else if (saslName.startsWith("3D", i + 1)) {
                result.append('=');
            } else {
                throw new SaslException("Invalid escape sequence in SCRAM user name");
            }
            i += 2;
        }
        return result.toString();
    }

    private void fail() {
        clearCredentials();
        state = State.FAILED;
    }

    private void clearCredentials() {
        zeroFill(salt);
        zeroFill(storedKey);
        zeroFill(serverKey);
    }

    private static void zeroFill(byte[] array) {
        if (array != null) {
            Arrays.fill(array, (byte) 0);
        }
    }

    @Override
    public boolean isComplete() {
        return state == State.COMPLETE;
    }

    @Override
    public String getAuthorizationID() {
        checkComplete();
        return username;
    }

    @Override
    public byte[] unwrap(byte[] incoming, int offset, int len) {
        throw new IllegalStateException("SCRAM has no security layer");
    }

    @Override
    public byte[] wrap(byte[] outgoing, int offset, int len) {
        throw new IllegalStateException("SCRAM has no security layer");
    }

    @Override
    public Object getNegotiatedProperty(String propName) {
        checkComplete();
        return null;
    }

    @Override
    public void dispose() {
        clearCredentials();
        if (state != State.COMPLETE) {
            state = State.FAILED;
        }
    }

    private void checkComplete() {
        if (!isComplete()) {
            throw new IllegalStateException("Authentication exchange has not completed");
        }
    }

}
