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
package org.thingsboard.mqtt.broker.service.testing.integration;

import com.hivemq.client.mqtt.datatypes.MqttUtf8String;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.Mqtt5ClientConfig;
import com.hivemq.client.mqtt.mqtt5.auth.Mqtt5EnhancedAuthMechanism;
import com.hivemq.client.mqtt.mqtt5.exceptions.Mqtt5ConnAckException;
import com.hivemq.client.mqtt.mqtt5.exceptions.Mqtt5DisconnectException;
import com.hivemq.client.mqtt.mqtt5.message.auth.Mqtt5Auth;
import com.hivemq.client.mqtt.mqtt5.message.auth.Mqtt5AuthBuilder;
import com.hivemq.client.mqtt.mqtt5.message.auth.Mqtt5AuthReasonCode;
import com.hivemq.client.mqtt.mqtt5.message.auth.Mqtt5EnhancedAuth;
import com.hivemq.client.mqtt.mqtt5.message.auth.Mqtt5EnhancedAuthBuilder;
import com.hivemq.client.mqtt.mqtt5.message.connect.Mqtt5Connect;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAck;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAckReasonCode;
import com.hivemq.client.mqtt.mqtt5.message.disconnect.Mqtt5Disconnect;
import com.hivemq.client.mqtt.mqtt5.message.disconnect.Mqtt5DisconnectReasonCode;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.actors.TbActorSystem;
import org.thingsboard.mqtt.broker.actors.TbTypeActorId;
import org.thingsboard.mqtt.broker.common.data.UnauthorizedClient;
import org.thingsboard.mqtt.broker.common.data.client.credentials.ScramAlgorithm;
import org.thingsboard.mqtt.broker.common.data.id.ActorType;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.dao.client.unauthorized.UnauthorizedClientService;
import org.thingsboard.mqtt.broker.service.auth.enhanced.ScramTestClient;
import org.thingsboard.mqtt.broker.service.auth.enhanced.ScramTestClient.Mechanism;
import org.thingsboard.mqtt.broker.service.test.util.TestUtils;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.SCRAM_ONLY_AUTH_ENABLED_MSG;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = Mqtt5AuthIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@RunWith(SpringRunner.class)
public class Mqtt5AuthIntegrationTestCase extends AbstractPubSubIntegrationTest {

    private static final String TEST_CLIENT_ID = "test_client_id";
    private static final String TEST_CLIENT_USERNAME = "test_username";
    private static final String TEST_CLIENT_PASSWORD = "test_client_password";

    @Autowired
    private TbActorSystem actorSystem;

    @Autowired
    private MqttClientCredentialsService credentialsService;

    @Autowired
    private UnauthorizedClientService unauthorizedClientService;

    private MqttClientCredentials clientCredentials;

    @Before
    public void beforeTest() throws Exception {
        super.beforeTest();
        clientCredentials = saveCredentials();
        enabledScramProvider();
    }

    @After
    public void after() {
        if (clientCredentials != null) {
            credentialsService.deleteCredentials(clientCredentials.getId());
        }
        // SCRAM-only refuses non-SCRAM clients; don't leave it on for test classes that skip the provider reset
        mqttAuthProviderManagerService.disableAuthProvider(getMqttAuthProvider(MqttAuthProviderType.SCRAM).getId());
    }

    @Test
    public void testConnectWithAuthenticationMethodAndDataProvided() throws Throwable {
        var scramClient = createScramClient(TEST_CLIENT_USERNAME, TEST_CLIENT_PASSWORD);
        var authCallback = new TestMqtt5EnhancedAuthMechanism(scramClient);

        Mqtt5AsyncClient client = Mqtt5Client.builder()
                .identifier(TEST_CLIENT_ID)
                .serverAddress(new InetSocketAddress(LOCALHOST, mqttPort))
                .enhancedAuth(authCallback).build().toAsync();

        // The auth mechanism verifies the SCRAM server-final message carried in the CONNACK Authentication Data,
        // so a CONNACK without it (or with a wrong server signature) fails the connect.
        client.connect().get(30, TimeUnit.SECONDS);
        assertThat(authCallback.isServerVerified()).isTrue();

        String updatedUsername = TEST_CLIENT_USERNAME + "updated";
        String updatedPassword = TEST_CLIENT_PASSWORD + "updated";
        clientCredentials = updateCredentials(clientCredentials, updatedUsername, updatedPassword);

        scramClient = createScramClient(updatedUsername, updatedPassword);
        authCallback.setScramClient(scramClient);

        // Same for re-authentication: the server-final message must arrive in the AUTH (Success) packet.
        client.reauth().get(30, TimeUnit.SECONDS);
        assertThat(authCallback.isServerVerified()).isTrue();

        client.disconnect();
    }

    @Test
    public void testReAuthWithNewAuthMethod() throws Throwable {
        var scramClient = createScramClient(TEST_CLIENT_USERNAME, TEST_CLIENT_PASSWORD);
        var authCallback = new TestMqtt5EnhancedAuthMechanism(scramClient);

        Mqtt5AsyncClient client = Mqtt5Client.builder()
                .identifier(TEST_CLIENT_ID)
                .serverAddress(new InetSocketAddress(LOCALHOST, mqttPort))
                .enhancedAuth(authCallback).build().toAsync();

        var connectedLatch = new CountDownLatch(1);

        AtomicBoolean connected = new AtomicBoolean(false);

        client.connect().whenComplete((mqtt5ConnAck, throwable) -> {
            if (throwable == null) {
                connected.set(true);
            } else {
                log.error("Failed to connect client: ", throwable);
            }
            connectedLatch.countDown();
        });
        connectedLatch.await();

        assertThat(connected.get()).isTrue();

        scramClient = createScramClient(TEST_CLIENT_ID, TEST_CLIENT_PASSWORD, ScramAlgorithm.SHA_256);
        authCallback.setScramClient(scramClient);
        authCallback.setAuthMethod(ScramAlgorithm.SHA_256.getMqttAlgorithmName());

        var reAuthLatch = new CountDownLatch(1);
        var actualException = new AtomicReference<Throwable>();

        client.reauth().whenComplete((__, throwable) -> {
            if (throwable != null) {
                actualException.set(throwable);
                log.error("Failed to re-auth client due to: ", throwable);
            } else {
                log.error("Re-auth should fail due to auth method mismatch!");
            }
            reAuthLatch.countDown();
        });

        reAuthLatch.await();

        assertThat(actualException.get())
                .isNotNull()
                .isInstanceOf(Mqtt5DisconnectException.class)
                .extracting(e -> ((Mqtt5DisconnectException) e).getMqttMessage().getReasonCode())
                .isEqualTo(Mqtt5DisconnectReasonCode.NOT_AUTHORIZED);
    }

    @Test
    public void testReAuthWithInvalidCredentials() throws Throwable {
        var scramClient = createScramClient(TEST_CLIENT_USERNAME, TEST_CLIENT_PASSWORD);
        var authCallback = new TestMqtt5EnhancedAuthMechanism(scramClient);

        Mqtt5AsyncClient client = Mqtt5Client.builder()
                .identifier(TEST_CLIENT_ID)
                .serverAddress(new InetSocketAddress(LOCALHOST, mqttPort))
                .enhancedAuth(authCallback).build().toAsync();

        var connectedLatch = new CountDownLatch(1);

        AtomicBoolean connected = new AtomicBoolean(false);

        client.connect().whenComplete((mqtt5ConnAck, throwable) -> {
            if (throwable == null) {
                connected.set(true);
            } else {
                log.error("Failed to connect client: ", throwable);
            }
            connectedLatch.countDown();
        });
        connectedLatch.await();

        assertThat(connected.get()).isTrue();

        String updatedPassword = TEST_CLIENT_PASSWORD + "_updated";
        scramClient = createScramClient(TEST_CLIENT_USERNAME, updatedPassword);
        authCallback.setScramClient(scramClient);

        var reAuthLatch = new CountDownLatch(1);
        var actualException = new AtomicReference<Throwable>();

        client.reauth().whenComplete((__, throwable) -> {
            if (throwable != null) {
                actualException.set(throwable);
                log.error("Failed to re-auth client due to: ", throwable);
            } else {
                log.error("Re-auth should fail due to credentials mismatch!");
            }
            reAuthLatch.countDown();
        });

        reAuthLatch.await();

        assertThat(actualException.get())
                .isNotNull()
                .isInstanceOf(Mqtt5DisconnectException.class)
                .extracting(e -> ((Mqtt5DisconnectException) e).getMqttMessage().getReasonCode())
                .isEqualTo(Mqtt5DisconnectReasonCode.NOT_AUTHORIZED);
    }

    @Test
    public void testConnectWithUnsupportedAuthMethod() throws Throwable {
        var scramClient = createScramClient(TEST_CLIENT_USERNAME, TEST_CLIENT_PASSWORD);
        var authCallback = new TestMqtt5EnhancedAuthMechanism(scramClient) {
            @Override
            public @NotNull MqttUtf8String getMethod() {
                return MqttUtf8String.of("UNSUPPORTED_AUTH_METHOD");
            }
        };

        Mqtt5AsyncClient client = Mqtt5Client.builder()
                .identifier(TEST_CLIENT_ID)
                .serverAddress(new InetSocketAddress(LOCALHOST, mqttPort))
                .enhancedAuth(authCallback).build().toAsync();

        var connectedLatch = new CountDownLatch(1);
        var actualException = new AtomicReference<Throwable>();

        client.connect().whenComplete((mqtt5ConnAck, throwable) -> {
            if (throwable != null) {
                actualException.set(throwable);
                log.warn("Expected failure due to invalid auth method: ", throwable);
            } else {
                log.error("Connection should fail with bad auth method reason code!");
            }
            connectedLatch.countDown();
        });
        connectedLatch.await();

        assertThat(actualException.get())
                .isNotNull()
                .isInstanceOf(Mqtt5ConnAckException.class)
                .extracting(e -> ((Mqtt5ConnAckException) e).getMqttMessage().getReasonCode())
                .isEqualTo(Mqtt5ConnAckReasonCode.BAD_AUTHENTICATION_METHOD);
    }

    @Test
    public void testConnectWithMissingAuthData() throws Throwable {
        var scramClient = createScramClient(TEST_CLIENT_USERNAME, TEST_CLIENT_PASSWORD);
        var authCallback = new TestMqtt5EnhancedAuthMechanism(scramClient) {
            @Override
            public @NotNull CompletableFuture<Void> onAuth(@NotNull Mqtt5ClientConfig mqtt5ClientConfig, @NotNull Mqtt5Connect mqtt5Connect, @NotNull Mqtt5EnhancedAuthBuilder mqtt5EnhancedAuthBuilder) {
                mqtt5EnhancedAuthBuilder.data((byte[]) null);
                return CompletableFuture.completedFuture(null);
            }
        };

        Mqtt5AsyncClient client = Mqtt5Client.builder()
                .identifier(TEST_CLIENT_ID)
                .serverAddress(new InetSocketAddress(LOCALHOST, mqttPort))
                .enhancedAuth(authCallback).build().toAsync();

        var connectedLatch = new CountDownLatch(1);
        var actualException = new AtomicReference<Throwable>();

        client.connect().whenComplete((mqtt5ConnAck, throwable) -> {
            if (throwable != null) {
                actualException.set(throwable);
                log.warn("Expected failure due to missing auth data: ", throwable);
            } else {
                log.error("Connection should fail with unspecified reason code due to missing auth data!");
            }
            connectedLatch.countDown();
        });

        connectedLatch.await();

        assertThat(actualException.get())
                .isNotNull()
                .isInstanceOf(Mqtt5ConnAckException.class)
                .extracting(e -> ((Mqtt5ConnAckException) e).getMqttMessage().getReasonCode())
                .isEqualTo(Mqtt5ConnAckReasonCode.UNSPECIFIED_ERROR);
        assertThat(actorSystem.getActor(new TbTypeActorId(ActorType.CLIENT, TEST_CLIENT_ID))).isNull();
    }

    @Test
    public void testConnectWithMissingAuthChallengeResponse() throws Throwable {
        var scramClient = createScramClient(TEST_CLIENT_USERNAME, TEST_CLIENT_PASSWORD);
        var authCallback = new TestMqtt5EnhancedAuthMechanism(scramClient) {
            @Override
            public @NotNull CompletableFuture<Boolean> onContinue(@NotNull Mqtt5ClientConfig mqtt5ClientConfig, @NotNull Mqtt5Auth mqtt5Auth, @NotNull Mqtt5AuthBuilder mqtt5AuthBuilder) {
                mqtt5Auth.getData().ifPresent(authDataBuffer -> {
                    try {
                        mqtt5AuthBuilder.data((byte[]) null);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                return CompletableFuture.completedFuture(true);
            }
        };

        Mqtt5AsyncClient client = Mqtt5Client.builder()
                .identifier(TEST_CLIENT_ID)
                .serverAddress(new InetSocketAddress(LOCALHOST, mqttPort))
                .enhancedAuth(authCallback).build().toAsync();

        var connectedLatch = new CountDownLatch(1);
        var actualException = new AtomicReference<Throwable>();

        client.connect().whenComplete((mqtt5ConnAck, throwable) -> {
            if (throwable != null) {
                actualException.set(throwable);
                log.error("Expected failure due to missing auth challenge response: ", throwable);
            } else {
                log.error("Connection should fail since auth challenge response is null!");
            }
            connectedLatch.countDown();
        });

        connectedLatch.await();
        assertThat(actualException.get())
                .isNotNull()
                .isInstanceOf(Mqtt5ConnAckException.class)
                .extracting(e -> ((Mqtt5ConnAckException) e).getMqttMessage().getReasonCode())
                .isEqualTo(Mqtt5ConnAckReasonCode.UNSPECIFIED_ERROR);
    }

    @Test
    public void testConnectWithInvalidAuthChallengeResponse() throws Throwable {
        var scramClient = createScramClient(TEST_CLIENT_USERNAME, TEST_CLIENT_PASSWORD);
        var authCallback = new TestMqtt5EnhancedAuthMechanism(scramClient) {
            @Override
            public @NotNull CompletableFuture<Boolean> onContinue(@NotNull Mqtt5ClientConfig mqtt5ClientConfig, @NotNull Mqtt5Auth mqtt5Auth, @NotNull Mqtt5AuthBuilder mqtt5AuthBuilder) {
                mqtt5Auth.getData().ifPresent(authDataBuffer -> {
                    try {
                        mqtt5AuthBuilder.data(new byte[0]);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                return CompletableFuture.completedFuture(true);
            }
        };

        Mqtt5AsyncClient client = Mqtt5Client.builder()
                .identifier(TEST_CLIENT_ID)
                .serverAddress(new InetSocketAddress(LOCALHOST, mqttPort))
                .enhancedAuth(authCallback).build().toAsync();

        var connectedLatch = new CountDownLatch(1);
        var actualException = new AtomicReference<Throwable>();

        client.connect().whenComplete((mqtt5ConnAck, throwable) -> {
            if (throwable != null) {
                actualException.set(throwable);
                log.error("Expected failure due to auth challenge evaluation failure: ", throwable);
            } else {
                log.error("Connection should fail with auth challenge evaluation failure!");
            }
            connectedLatch.countDown();
        });

        connectedLatch.await();
        assertThat(actualException.get())
                .isNotNull()
                .isInstanceOf(Mqtt5ConnAckException.class)
                .extracting(e -> ((Mqtt5ConnAckException) e).getMqttMessage().getReasonCode())
                .isEqualTo(Mqtt5ConnAckReasonCode.NOT_AUTHORIZED);
    }

    @Test
    public void givenOnlyScramEnabled_whenMqtt5ClientConnectsWithoutAuthMethod_thenNotAuthorized() {
        String clientId = "scram_only_mqtt5_no_auth_method";
        var client = Mqtt5Client.builder()
                .identifier(clientId)
                .serverAddress(new InetSocketAddress(LOCALHOST, mqttPort))
                .buildBlocking();

        Throwable thrown = catchThrowable(client::connect);

        assertThat(thrown)
                .isInstanceOf(Mqtt5ConnAckException.class)
                .extracting(e -> ((Mqtt5ConnAckException) e).getMqttMessage().getReasonCode())
                .isEqualTo(Mqtt5ConnAckReasonCode.NOT_AUTHORIZED);
        awaitUnauthorizedClientWithScramOnlyReason(clientId);
    }

    @Test
    public void givenOnlyScramEnabled_whenMqtt311ClientConnects_thenNotAuthorized() throws Throwable {
        String clientId = "scram_only_mqtt311_client";
        var client = new MqttClient(SERVER_URI + mqttPort, clientId);
        var options = new MqttConnectOptions();
        options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
        options.setUserName(TEST_CLIENT_USERNAME);
        options.setPassword(TEST_CLIENT_PASSWORD.toCharArray());

        Throwable thrown = catchThrowable(() -> client.connect(options));

        assertThat(thrown).isInstanceOf(MqttException.class);
        assertThat(((MqttException) thrown).getReasonCode())
                .isEqualTo(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED.byteValue());
        client.close();
        awaitUnauthorizedClientWithScramOnlyReason(clientId);
    }

    private void awaitUnauthorizedClientWithScramOnlyReason(String clientId) {
        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(unauthorizedClientService.findUnauthorizedClient(clientId))
                        .get()
                        .extracting(UnauthorizedClient::getReason)
                        .isEqualTo(SCRAM_ONLY_AUTH_ENABLED_MSG));
        unauthorizedClientService.deleteUnauthorizedClient(clientId);
    }

    private ScramTestClient createScramClient(String username, String password) {
        return createScramClient(username, password, ScramAlgorithm.SHA_512);
    }

    private ScramTestClient createScramClient(String username, String password, ScramAlgorithm algorithm) {
        return new ScramTestClient(username, password, algorithm == ScramAlgorithm.SHA_256 ? Mechanism.SCRAM_SHA_256 : Mechanism.SCRAM_SHA_512);
    }

    @Setter
    private static class TestMqtt5EnhancedAuthMechanism implements Mqtt5EnhancedAuthMechanism {

        private ScramTestClient scramClient;
        private String authMethod;
        @Getter
        private volatile boolean serverVerified;

        public TestMqtt5EnhancedAuthMechanism(ScramTestClient scramClient) {
            this(scramClient, ScramAlgorithm.SHA_512.getMqttAlgorithmName());
        }

        public TestMqtt5EnhancedAuthMechanism(ScramTestClient scramClient, String authMethod) {
            this.scramClient = scramClient;
            this.authMethod = authMethod;
        }

        @Override
        public @NotNull MqttUtf8String getMethod() {
            return MqttUtf8String.of(authMethod);
        }

        @Override
        public int getTimeout() {
            return 60;
        }

        @Override
        public @NotNull CompletableFuture<Void> onAuth(@NotNull Mqtt5ClientConfig mqtt5ClientConfig, @NotNull Mqtt5Connect mqtt5Connect, @NotNull Mqtt5EnhancedAuthBuilder mqtt5EnhancedAuthBuilder) {
            serverVerified = false;
            mqtt5EnhancedAuthBuilder.data(scramClient.clientFirstMessage());
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public @NotNull CompletableFuture<Void> onReAuth(@NotNull Mqtt5ClientConfig mqtt5ClientConfig, @NotNull Mqtt5AuthBuilder mqtt5AuthBuilder) {
            serverVerified = false;
            mqtt5AuthBuilder.data(scramClient.clientFirstMessage());
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public @NotNull CompletableFuture<Boolean> onContinue(@NotNull Mqtt5ClientConfig mqtt5ClientConfig, @NotNull Mqtt5Auth mqtt5Auth, @NotNull Mqtt5AuthBuilder mqtt5AuthBuilder) {
            Mqtt5AuthReasonCode reasonCode = mqtt5Auth.getReasonCode();
            if (reasonCode == Mqtt5AuthReasonCode.CONTINUE_AUTHENTICATION) {
                mqtt5Auth.getData().ifPresentOrElse(authDataBuffer -> {
                    byte[] authDataAsByteArray = getBytes(authDataBuffer);
                    mqtt5AuthBuilder.data(scramClient.clientFinalMessage(authDataAsByteArray));
                }, () -> log.error("Received empty auth response from server"));
                return CompletableFuture.completedFuture(true);
            }
            return CompletableFuture.completedFuture(false);
        }

        @Override
        public @NotNull CompletableFuture<Boolean> onAuthSuccess(@NotNull Mqtt5ClientConfig mqtt5ClientConfig, @NotNull Mqtt5ConnAck mqtt5ConnAck) {
            return CompletableFuture.completedFuture(verifyServerFinalMessage(mqtt5ConnAck.getEnhancedAuth().flatMap(Mqtt5EnhancedAuth::getData)));
        }

        @Override
        public @NotNull CompletableFuture<Boolean> onReAuthSuccess(@NotNull Mqtt5ClientConfig mqtt5ClientConfig, @NotNull Mqtt5Auth mqtt5Auth) {
            return CompletableFuture.completedFuture(verifyServerFinalMessage(mqtt5Auth.getData()));
        }

        // Completes SCRAM mutual authentication (RFC 5802): the client accepts the server only if the
        // server-final message is present and its server signature verifies against the client's password.
        private boolean verifyServerFinalMessage(Optional<ByteBuffer> serverFinalMessage) {
            if (serverFinalMessage.isEmpty()) {
                log.error("Server-final message is missing from the successful authentication response");
                return false;
            }
            try {
                scramClient.verifyServerFinalMessage(getBytes(serverFinalMessage.get()));
                serverVerified = true;
                return true;
            } catch (IllegalStateException e) {
                log.error("Failed to verify server-final message", e);
                return false;
            }
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

        static byte[] getBytes(ByteBuffer authDataBuffer) {
            if (authDataBuffer.hasArray()) {
                return authDataBuffer.array();
            }
            byte[] authDataAsByteArray = new byte[authDataBuffer.remaining()];
            authDataBuffer.get(authDataAsByteArray);
            return authDataAsByteArray;
        }

    }

    private MqttClientCredentials saveCredentials() throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException {
        return credentialsService.saveCredentials(TestUtils.createScramDeviceClientCredentials(TEST_CLIENT_USERNAME, TEST_CLIENT_PASSWORD, ScramAlgorithm.SHA_512));
    }

    private MqttClientCredentials updateCredentials(MqttClientCredentials mqttClientCredentials, String username, String password) throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException {
        return credentialsService.saveCredentials(TestUtils.updateScramDeviceClientCredentials(mqttClientCredentials, username, password, ScramAlgorithm.SHA_512));
    }

}
