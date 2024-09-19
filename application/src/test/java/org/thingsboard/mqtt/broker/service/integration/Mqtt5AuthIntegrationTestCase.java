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
package org.thingsboard.mqtt.broker.service.integration;

import com.hivemq.client.mqtt.datatypes.MqttUtf8String;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.Mqtt5ClientConfig;
import com.hivemq.client.mqtt.mqtt5.auth.Mqtt5EnhancedAuthMechanism;
import com.hivemq.client.mqtt.mqtt5.message.auth.Mqtt5Auth;
import com.hivemq.client.mqtt.mqtt5.message.auth.Mqtt5AuthBuilder;
import com.hivemq.client.mqtt.mqtt5.message.auth.Mqtt5AuthReasonCode;
import com.hivemq.client.mqtt.mqtt5.message.auth.Mqtt5EnhancedAuthBuilder;
import com.hivemq.client.mqtt.mqtt5.message.connect.Mqtt5Connect;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAck;
import com.hivemq.client.mqtt.mqtt5.message.disconnect.Mqtt5Disconnect;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.security.scram.internals.ScramSaslClientProvider;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.common.data.client.credentials.ScramAlgorithm;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.service.test.util.TestUtils;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import static org.thingsboard.mqtt.broker.service.auth.DefaultEnhancedAuthenticationService.SCRAM_SASL_PROPS;
import static org.thingsboard.mqtt.broker.service.auth.DefaultEnhancedAuthenticationService.SCRAM_SASL_PROTOCOL;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = Mqtt5AuthIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "security.mqtt.basic.enabled=true"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class Mqtt5AuthIntegrationTestCase extends AbstractPubSubIntegrationTest {

    private static final String TEST_CLIENT_ID = "test_client_id";
    private static final String TEST_CLIENT_USERNAME = "test_username";
    private static final String TEST_CLIENT_PASSWORD = "test_client_password";
    private static final ScramAlgorithm TEST_SCRAM_ALGORITHM = ScramAlgorithm.SHA_512;

    @Autowired
    private MqttClientCredentialsService credentialsService;

    private MqttClientCredentials clientCredentials;

    @Before
    public void before() throws Exception {
        clientCredentials = saveCredentials();
    }

    @After
    public void after() {
        if (clientCredentials != null) {
            credentialsService.deleteCredentials(clientCredentials.getId());
        }
    }

    @Test
    public void testConnectWithAuthenticationMethodAndDataProvided() throws Throwable {
        var saslClient = createScramSaslClient(TEST_CLIENT_USERNAME, TEST_CLIENT_PASSWORD, TEST_SCRAM_ALGORITHM);
        var connectedLatch = new CountDownLatch(1);
        var authCallback = new TestMqtt5EnhancedAuthMechanism(saslClient);

        Mqtt5AsyncClient client = Mqtt5Client.builder()
                .identifier(TEST_CLIENT_ID)
                .serverAddress(new InetSocketAddress(LOCALHOST, mqttPort))
                .enhancedAuth(authCallback).build().toAsync();

        client.connect().whenComplete((mqtt5ConnAck, throwable) -> {
            if (throwable != null) {
                log.error("Failed to connect client due to: ", throwable);
            } else {
                log.warn("Client connected!");
            }
            connectedLatch.countDown();
        });
        connectedLatch.await();

        String updatedUsername = TEST_CLIENT_USERNAME + "updated";
        String updatedPassword = TEST_CLIENT_PASSWORD + "updated";
        clientCredentials = updateCredentials(clientCredentials, updatedUsername, updatedPassword);

        saslClient = createScramSaslClient(updatedUsername, updatedPassword, TEST_SCRAM_ALGORITHM);
        authCallback.setSaslClient(saslClient);

        var reAuthLatch = new CountDownLatch(1);

        client.reauth().whenComplete((__, throwable) -> {
            if (throwable != null) {
                log.error("Failed to re-auth client due to: ", throwable);
            } else {
                log.warn("Client re-authenticated!");
            }
            reAuthLatch.countDown();
        });
        reAuthLatch.await();

        client.disconnect();
    }

    private SaslClient createScramSaslClient(String username, String password, ScramAlgorithm algorithm) {
        ScramSaslClientProvider.initialize();
        try {
            return Sasl.createSaslClient(
                    new String[]{algorithm.getMqttAlgorithmName()},
                    null,
                    SCRAM_SASL_PROTOCOL,
                    null,
                    SCRAM_SASL_PROPS,
                    new ScramSaslClientCallbackHandler(username, password)
            );
        } catch (SaslException e) {
            throw new RuntimeException("Failed to initialize Sasl client", e);
        }
    }

    private static class ScramSaslClientCallbackHandler implements CallbackHandler {

        private final String username;
        private final char[] password;

        public ScramSaslClientCallbackHandler(String username, String password) {
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

    @Setter
    private static class TestMqtt5EnhancedAuthMechanism implements Mqtt5EnhancedAuthMechanism {

        private SaslClient saslClient;

        public TestMqtt5EnhancedAuthMechanism(SaslClient saslClient) {
            this.saslClient = saslClient;
        }

        @Override
        public @NotNull MqttUtf8String getMethod() {
            return MqttUtf8String.of(TEST_SCRAM_ALGORITHM.getMqttAlgorithmName());
        }

        @Override
        public int getTimeout() {
            return 60;
        }

        @Override
        public @NotNull CompletableFuture<Void> onAuth(@NotNull Mqtt5ClientConfig mqtt5ClientConfig, @NotNull Mqtt5Connect mqtt5Connect, @NotNull Mqtt5EnhancedAuthBuilder mqtt5EnhancedAuthBuilder) {
            try {
                byte[] initialRequest = saslClient.evaluateChallenge(new byte[0]);
                mqtt5EnhancedAuthBuilder.data(initialRequest);
            } catch (SaslException e) {
                throw new RuntimeException(e);
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public @NotNull CompletableFuture<Void> onReAuth(@NotNull Mqtt5ClientConfig mqtt5ClientConfig, @NotNull Mqtt5AuthBuilder mqtt5AuthBuilder) {
            try {
                byte[] initialRequest = saslClient.evaluateChallenge(new byte[0]);
                mqtt5AuthBuilder.data(initialRequest);
            } catch (SaslException e) {
                throw new RuntimeException(e);
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public @NotNull CompletableFuture<Boolean> onContinue(@NotNull Mqtt5ClientConfig mqtt5ClientConfig, @NotNull Mqtt5Auth mqtt5Auth, @NotNull Mqtt5AuthBuilder mqtt5AuthBuilder) {
            Mqtt5AuthReasonCode reasonCode = mqtt5Auth.getReasonCode();
            if (reasonCode == Mqtt5AuthReasonCode.CONTINUE_AUTHENTICATION) {
                mqtt5Auth.getData().ifPresentOrElse(authDataBuffer -> {
                    byte[] authDataAsByteArray = getBytes(authDataBuffer);
                    try {
                        byte[] response = saslClient.evaluateChallenge(authDataAsByteArray);
                        mqtt5AuthBuilder.data(response);
                    } catch (SaslException e) {
                        throw new RuntimeException(e);
                    }
                }, () -> log.error("Received empty auth response from server"));
                return CompletableFuture.completedFuture(true);
            }
            return CompletableFuture.completedFuture(false);
        }

        @Override
        public @NotNull CompletableFuture<Boolean> onAuthSuccess(@NotNull Mqtt5ClientConfig mqtt5ClientConfig, @NotNull Mqtt5ConnAck mqtt5ConnAck) {
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public @NotNull CompletableFuture<Boolean> onReAuthSuccess(@NotNull Mqtt5ClientConfig mqtt5ClientConfig, @NotNull Mqtt5Auth mqtt5Auth) {
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public void onAuthRejected(@NotNull Mqtt5ClientConfig mqtt5ClientConfig, @NotNull Mqtt5ConnAck mqtt5ConnAck) {
            log.warn("onAuthRejected: {}", mqtt5ConnAck);
        }

        @Override
        public void onReAuthRejected(@NotNull Mqtt5ClientConfig mqtt5ClientConfig, @NotNull Mqtt5Disconnect mqtt5Disconnect) {
            log.warn("onReAuthRejected: {}", mqtt5Disconnect);
        }

        @Override
        public void onAuthError(@NotNull Mqtt5ClientConfig mqtt5ClientConfig, @NotNull Throwable throwable) {
            log.error("onAuthError: ", throwable);
        }

        @Override
        public void onReAuthError(@NotNull Mqtt5ClientConfig mqtt5ClientConfig, @NotNull Throwable throwable) {
            log.error("onReAuthError: ", throwable);
        }

        private static byte[] getBytes(ByteBuffer authDataBuffer) {
            if (authDataBuffer.hasArray()) {
                return authDataBuffer.array();
            }
            byte[] authDataAsByteArray = new byte[authDataBuffer.remaining()];
            authDataBuffer.get(authDataAsByteArray);
            return authDataAsByteArray;
        }

    }

    private MqttClientCredentials saveCredentials() throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException {
        return credentialsService.saveCredentials(TestUtils.createScramDeviceClientCredentials(TEST_CLIENT_USERNAME, TEST_CLIENT_PASSWORD, TEST_SCRAM_ALGORITHM));
    }

    private MqttClientCredentials updateCredentials(MqttClientCredentials mqttClientCredentials, String username, String password) throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException {
        return credentialsService.saveCredentials(TestUtils.updateScramDeviceClientCredentials(mqttClientCredentials, username, password, TEST_SCRAM_ALGORITHM));
    }

}