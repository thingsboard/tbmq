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
package org.thingsboard.mqtt.broker.service.testing.integration;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.common.data.client.credentials.PubSubAuthorizationRules;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.security.jwt.HmacBasedAlgorithmConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.jwt.JwtMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = JwtHmacAuthorizationIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@DaoSqlTest
@RunWith(SpringRunner.class)
public class JwtHmacAuthorizationIntegrationTestCase extends AbstractPubSubIntegrationTest {

    private static final String CLIENT_USERNAME = "test-hmac-mqtt-jwt-username";
    private static final String CLIENT_ID = "hmacAuthClientId";
    private static final String MY_TOPIC = "my/topic";
    private static final String TEST_TOPIC = "test/topic";

    @Before
    public void beforeTest() throws Exception {
        super.beforeTest();
        MqttAuthProvider provider = getMqttAuthProvider(MqttAuthProviderType.JWT);
        var configuration = (JwtMqttAuthProviderConfiguration) provider.getConfiguration();
        PubSubAuthorizationRules pubSubAuthorizationRules = new PubSubAuthorizationRules(
                List.of("test/.*"),
                List.of("my/.*")
        );
        provider.setEnabled(true);
        configuration.setAuthRules(pubSubAuthorizationRules);
        provider.setConfiguration(configuration);
        mqttAuthProviderManagerService.saveAuthProvider(provider);
    }

    @Test
    public void givenClient_whenConnectWithoutPassword_thenConnectionRefusedNotAuthorized() throws Throwable {
        MqttClient client = getMqttClient();
        Throwable thrown = catchThrowable(client::connect);
        assertThat(thrown).isInstanceOf(MqttException.class);
        MqttException me = (MqttException) thrown;
        assertThat(me.getReasonCode()).isEqualTo(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED.byteValue());
        client.close();
    }

    @Test
    public void givenClient_whenConnectWithValidJwtPassword_thenConnectionAccepted() throws Throwable {
        String jwt = generateValidSecretSignedJwtToken(CLIENT_USERNAME);
        MqttClient client = getMqttClient();
        client.connect(getMqttConnectOptions(jwt));
        assertThat(client.isConnected()).isTrue();
        client.disconnect();
        client.close();
    }

    @Test
    public void givenClient_whenPublishToCorrectTopic_thenSuccess() throws Throwable {
        String jwt = generateValidSecretSignedJwtToken(CLIENT_USERNAME);
        MqttClient client = getMqttClient();
        client.connect(getMqttConnectOptions(jwt));
        assertThat(client.isConnected()).isTrue();
        client.publish(TEST_TOPIC, "msg".getBytes(StandardCharsets.UTF_8), 1, false);
        client.disconnect();
        client.close();
    }

    @Test
    public void givenClient_whenSubscribeToCorrectTopic_thenSuccess() throws Throwable {
        String jwt = generateValidSecretSignedJwtToken(CLIENT_USERNAME);
        MqttClient client = getMqttClient();
        client.connect(getMqttConnectOptions(jwt));
        assertThat(client.isConnected()).isTrue();
        client.subscribe(MY_TOPIC);
        client.disconnect();
        client.close();
    }

    @Test(expected = MqttException.class)
    public void givenClient_whenPublishToWrongTopic_thenCloseChannelWithoutDisconnect() throws Throwable {
        String jwt = generateValidSecretSignedJwtToken(CLIENT_USERNAME);
        MqttClient client = getMqttClient();
        client.connect(getMqttConnectOptions(jwt));
        assertThat(client.isConnected()).isTrue();
        client.publish(MY_TOPIC, "msg".getBytes(StandardCharsets.UTF_8), 1, false);
        client.close();
    }

    @Test(expected = MqttException.class)
    public void givenClient_whenSubscribeToWrongTopic_thenCloseChannelWithoutDisconnect() throws Throwable {
        String jwt = generateValidSecretSignedJwtToken(CLIENT_USERNAME);
        MqttClient client = getMqttClient();
        client.connect(getMqttConnectOptions(jwt));
        assertThat(client.isConnected()).isTrue();
        client.subscribe(TEST_TOPIC);
        client.close();
    }

    private MqttClient getMqttClient() throws MqttException {
        return new MqttClient(SERVER_URI + mqttPort, CLIENT_ID);
    }

    private MqttConnectOptions getMqttConnectOptions(String jwt) {
        var options = new MqttConnectOptions();
        options.setUserName(CLIENT_USERNAME);
        options.setPassword(jwt.toCharArray());
        return options;
    }

    public String generateValidSecretSignedJwtToken(String username) throws Exception {
        JWSSigner signer = new MACSigner(HmacBasedAlgorithmConfiguration.DEFAULT_JWT_SECRET.getBytes());
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(username)
                .expirationTime(new Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(1)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(signer);
        return jwt.serialize();
    }

}
