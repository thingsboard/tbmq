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
import org.thingsboard.mqtt.broker.common.data.security.jwt.JwtMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.service.test.util.JwtTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = JwtHmacAuthorizationIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@DaoSqlTest
@RunWith(SpringRunner.class)
public class JwtHmacAuthorizationIntegrationTestCase extends AbstractPubSubIntegrationTest {

    static final String CLIENT_ID = "authClientId";
    static final String MY_TOPIC = "my/topic";
    static final String TEST_TOPIC = "test/topic";

    @Before
    public void beforeTest() throws Exception {
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

    @After
    public void afterTest() {
        disableJwtProvider();
    }

    @Test
    public void givenClient_whenConnectWithoutPassword_thenConnectionRefusedNotAuthorized() throws Throwable {
        MqttClient client = new MqttClient(SERVER_URI + mqttPort, CLIENT_ID);
        Throwable thrown = catchThrowable(client::connect);
        assertThat(thrown).isInstanceOf(MqttException.class);
        MqttException me = (MqttException) thrown;
        assertThat(me.getReasonCode()).isEqualTo(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED.byteValue());
    }

    @Test
    public void givenClient_whenConnectWithValidJwtPassword_thenConnectionAccepted() throws Throwable {
        String jwt = JwtTestUtils.generateValidJwtTokenWithDefaultSecret("test-mqtt-jwt-username");

        MqttClient client = new MqttClient(SERVER_URI + mqttPort, CLIENT_ID);
        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName("test-mqtt-jwt-username");
        options.setPassword(jwt.toCharArray());

        client.connect(options);

        assertThat(client.isConnected()).isTrue();
        client.disconnect();
    }

}
