/**
 * Copyright © 2016-2020 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.integration.persistentsession;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.awaitility.Awaitility;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
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
import org.thingsboard.mqtt.broker.common.data.client.credentials.BasicMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.service.test.util.TestUtils;

import java.util.concurrent.TimeUnit;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = AppPersistedMessagesIntegrationTest.class, loader = SpringBootContextLoader.class)
@DaoSqlTest
@RunWith(SpringRunner.class)
public class AppPersistedMessagesIntegrationTest extends AbstractPubSubIntegrationTest {
    private static final String TEST_CLIENT_ID = "test-application-client";
    @Autowired
    private MqttClientCredentialsService credentialsService;

    private MqttClientCredentials applicationCredentials;
    private BasicMqttCredentials basicMqttCredentials;
    private MqttClient persistedClient;

    @Before
    public void init() {
        applicationCredentials = credentialsService.saveCredentials(TestUtils.createApplicationClientCredentials(TEST_CLIENT_ID));
        basicMqttCredentials = new BasicMqttCredentials(RandomStringUtils.randomAlphabetic(5), "", null, null);
    }

    @After
    public void clear() throws Exception {
        log.warn("After test finish...");
        if (persistedClient.isConnected()) {
            persistedClient.disconnect();
        }
        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setCleanSession(true);
        persistedClient.connect(connectOptions);
        log.warn("After test finish... Persisted client connected: {}", isConnected());
        persistedClient.disconnect();
        persistedClient.close();
        credentialsService.deleteCredentials(applicationCredentials.getId());
    }

    @Test
    public void testSuccessPersistence_afterReconnect() throws Throwable {
        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setCleanSession(false);
        persistedClient = new MqttClient("tcp://localhost:" + mqttPort, basicMqttCredentials.getClientId());
        persistedClient.connect(connectOptions);
        log.warn("Persisted client connected: {}", isConnected());
        persistedClient.subscribe("test", 1, (topic, msg) -> {
        });
        persistedClient.disconnect();

        MqttClient publishingClient = new MqttClient("tcp://localhost:" + mqttPort, "publishing_client");
        publishingClient.connect();
        publishingClient.publish("test", "test_message".getBytes(), 1, false);
        publishingClient.disconnect();
        publishingClient.close();

        persistedClient.connect(connectOptions);

        log.warn("Persisted client connected: {}", isConnected());
    }

    @Test
    public void testSuccessPersistence_duringAndAfterDisconnect() throws Throwable {
        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setCleanSession(false);
        persistedClient = new MqttClient("tcp://localhost:" + mqttPort, basicMqttCredentials.getClientId());
        persistedClient.connect(connectOptions);
        log.warn("Persisted client connected: {}", isConnected());
        persistedClient.subscribe("test", 1, (topic, msg) -> {
        });

        MqttClient publishingClient = new MqttClient("tcp://localhost:" + mqttPort, "publishing_client");
        publishingClient.connect();
        publishingClient.publish("test", "test_message_1".getBytes(), 1, false);

        persistedClient.disconnect();

        publishingClient.publish("test", "test_message_2".getBytes(), 1, false);

        publishingClient.disconnect();
        publishingClient.close();

        persistedClient.connect(connectOptions);

        log.warn("Persisted client connected: {}", isConnected());
    }

    private boolean isConnected() {
        return Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> persistedClient.isConnected(), Boolean::booleanValue);
    }
}
