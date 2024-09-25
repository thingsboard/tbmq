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
package org.thingsboard.mqtt.broker.common.data.client.credentials;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.thingsboard.mqtt.broker.common.data.validation.NoXss;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.List;

@Data
public class ScramMqttCredentials implements HasSinglePubSubAutorizationRules {

    public static final int ITERATIONS_COUNT = 4096;

    private static final int SALT_LENGTH = 16;

    @NoXss
    private String userName;
    private byte[] salt;
    private byte[] serverKey;
    private byte[] storedKey;
    private ScramAlgorithm algorithm;
    private PubSubAuthorizationRules authRules;

    @JsonCreator
    public ScramMqttCredentials(
            @JsonProperty("userName") String userName,
            @JsonProperty("password") String password,
            @JsonProperty("salt") String salt,
            @JsonProperty("serverKey") String serverKey,
            @JsonProperty("storedKey") String storedKey,
            @JsonProperty("algorithm") ScramAlgorithm algorithm,
            @JsonProperty("authRules") PubSubAuthorizationRules authRules) throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException {
        this.userName = userName;
        this.authRules = authRules;
        this.algorithm = algorithm;

        if (password != null) {
            this.salt = generateRandomSalt();
            byte[] saltedPassword = pbkdf2(password, this.salt, algorithm);
            byte[] clientKey = hmac(saltedPassword, "Client Key", algorithm);
            this.serverKey = hmac(saltedPassword, "Server Key", algorithm);
            this.storedKey = hash(clientKey, algorithm);
        } else {
            this.salt = Base64.getDecoder().decode(salt);
            this.serverKey = Base64.getDecoder().decode(serverKey);
            this.storedKey = Base64.getDecoder().decode(storedKey);
        }
    }

    public static ScramMqttCredentials newInstance(
            String username,
            String password,
            ScramAlgorithm algorithm,
            List<String> authorizationRulePatterns) throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException {
        var authRules = PubSubAuthorizationRules.newInstance(authorizationRulePatterns);
        return new ScramMqttCredentials(username, password, null, null, null, algorithm, authRules);
    }

    @JsonGetter("salt")
    public String getSaltStr() {
        return Base64.getEncoder().encodeToString(this.salt);
    }

    @JsonGetter("serverKey")
    public String getServerKeyStr() {
        return Base64.getEncoder().encodeToString(this.serverKey);
    }

    @JsonGetter("storedKey")
    public String getStoredKeyStr() {
        return Base64.getEncoder().encodeToString(this.storedKey);
    }

    private static byte[] generateRandomSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        SecureRandom random = new SecureRandom();
        random.nextBytes(salt);
        return salt;
    }

    private static byte[] pbkdf2(String password, byte[] salt, ScramAlgorithm algorithm) throws NoSuchAlgorithmException, InvalidKeySpecException {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS_COUNT, algorithm.getHashByteSize() * 8);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(algorithm.getPbkdf2Algorithm());
        return factory.generateSecret(spec).getEncoded();
    }

    private static byte[] hmac(byte[] key, String data, ScramAlgorithm algorithm) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance(algorithm.getHmacAlgorithm());
        SecretKeySpec keySpec = new SecretKeySpec(key, algorithm.getHmacAlgorithm());
        mac.init(keySpec);
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] hash(byte[] data, ScramAlgorithm algorithm) throws NoSuchAlgorithmException {
        return MessageDigest.getInstance(algorithm.getHashAlgorithm()).digest(data);
    }
}
