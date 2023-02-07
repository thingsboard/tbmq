/**
 * Copyright © 2016-2023 The Thingsboard Authors
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

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
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
import org.thingsboard.mqtt.broker.common.data.client.credentials.PubSubAuthorizationRules;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.service.test.util.TestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = AuthorizationIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "security.mqtt.basic.enabled=true"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class AuthorizationIntegrationTestCase extends AbstractPubSubIntegrationTest {

    static final String CLIENT_ID = "authClientId";
    static final String MY_TOPIC = "my/topic";
    static final String TEST_TOPIC = "test/topic";

    @Autowired
    private MqttClientCredentialsService credentialsService;

    private MqttClientCredentials credentials;

    @Before
    public void init() throws Exception {
        credentials = saveCredentials();
    }

    private MqttClientCredentials saveCredentials() {
        PubSubAuthorizationRules pubSubAuthorizationRules = new PubSubAuthorizationRules(
                List.of("test/.*"),
                List.of("my/.*")
        );
        return credentialsService.saveCredentials(
                TestUtils.createDeviceClientCredentialsWithPubSubAuth(CLIENT_ID, null, null, pubSubAuthorizationRules)
        );
    }

    @After
    public void clear() {
        credentialsService.deleteCredentials(credentials.getId());
    }

    @Test(expected = MqttException.class)
    public void givenClient_whenPublishToWrongTopic_thenDisconnect() throws Throwable {
        MqttClient client = new MqttClient("tcp://localhost:" + mqttPort, CLIENT_ID);
        client.connect();

        client.publish(MY_TOPIC, "msg".getBytes(StandardCharsets.UTF_8), 1, false);
        client.close();
    }

    @Test
    public void givenClient_whenPublishToCorrectTopic_thenSuccess() throws Throwable {
        MqttClient client = new MqttClient("tcp://localhost:" + mqttPort, CLIENT_ID);
        client.connect();

        client.publish(TEST_TOPIC, "msg".getBytes(StandardCharsets.UTF_8), 1, false);
        client.disconnect();
        client.close();
    }

    @Test(expected = MqttException.class)
    public void givenClient_whenSubscribeToWrongTopic_thenDisconnect() throws Throwable {
        MqttClient client = new MqttClient("tcp://localhost:" + mqttPort, CLIENT_ID);
        client.connect();

        client.subscribe(TEST_TOPIC);
        client.close();
    }

    @Test
    public void givenClient_whenSubscribeToCorrectTopic_thenSuccess() throws Throwable {
        MqttClient client = new MqttClient("tcp://localhost:" + mqttPort, CLIENT_ID);
        client.connect();

        client.subscribe(MY_TOPIC);
        client.disconnect();
        client.close();
    }
}
