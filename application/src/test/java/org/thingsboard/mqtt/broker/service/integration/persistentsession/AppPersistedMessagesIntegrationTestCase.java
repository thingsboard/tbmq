/**
 * Copyright © 2016-2022 The Thingsboard Authors
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
import org.awaitility.Awaitility;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.junit.After;
import org.junit.Assert;
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
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.service.test.util.TestUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = AppPersistedMessagesIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "security.mqtt.basic.enabled=true"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class AppPersistedMessagesIntegrationTestCase extends AbstractPubSubIntegrationTest {

    private static final String TEST_APPLICATION_CLIENT = "test-application-client";
    private static final String PUBLISHING_CLIENT = "publishing_client";
    public static final String APP_USERNAME = "app";
    public static final String DEV_USERNAME = "dev";

    @Autowired
    private MqttClientCredentialsService credentialsService;

    private MqttClientCredentials applicationCredentials;
    private MqttClientCredentials deviceCredentials;
    private MqttClient persistedClient;

    @Before
    public void init() {
        applicationCredentials = credentialsService.saveCredentials(
                TestUtils.createApplicationClientCredentials(TEST_APPLICATION_CLIENT, APP_USERNAME)
        );
        deviceCredentials = credentialsService.saveCredentials(
                TestUtils.createDeviceClientCredentials(PUBLISHING_CLIENT, DEV_USERNAME)
        );
    }

    @After
    public void clear() throws Exception {
        log.warn("After test finish...");
        if (persistedClient.isConnected()) {
            persistedClient.disconnect();
        }
        MqttConnectOptions connectOptions = getConnectOptions(true, APP_USERNAME);
        persistedClient.connect(connectOptions);
        log.warn("After test finish... Persisted client connected: {}", isConnected());
        persistedClient.disconnect();
        persistedClient.close();

        credentialsService.deleteCredentials(applicationCredentials.getId());
        credentialsService.deleteCredentials(deviceCredentials.getId());
    }

    @Test
    public void testSuccessPersistence_afterReconnect() throws Throwable {
        CountDownLatch responses = new CountDownLatch(1);

        MqttConnectOptions persistedConnectOptions = getConnectOptions(false, APP_USERNAME);

        persistedClient = new MqttClient("tcp://localhost:" + mqttPort, TEST_APPLICATION_CLIENT);
        persistedClient.connect(persistedConnectOptions);
        log.warn("Persisted client connected: {}", isConnected());
        persistedClient.subscribe("test", 1, (topic, msg) -> responses.countDown());
        persistedClient.disconnect();

        MqttClient publishingClient = new MqttClient("tcp://localhost:" + mqttPort, PUBLISHING_CLIENT);
        publishingClient.connect(getConnectOptions(true, DEV_USERNAME));
        publishingClient.publish("test", "test_message".getBytes(), 1, false);
        publishingClient.disconnect();
        publishingClient.close();

        persistedClient.connect(persistedConnectOptions);

        log.warn("Persisted client connected: {}", isConnected());
        Assert.assertTrue(responses.await(30, TimeUnit.SECONDS));
    }

    @Test
    public void testSuccessPersistence_duringAndAfterDisconnect() throws Throwable {
        CountDownLatch responses = new CountDownLatch(2);

        MqttConnectOptions persistedConnectOptions = getConnectOptions(false, APP_USERNAME);

        persistedClient = new MqttClient("tcp://localhost:" + mqttPort, TEST_APPLICATION_CLIENT);
        persistedClient.connect(persistedConnectOptions);
        log.warn("Persisted client connected: {}", isConnected());
        persistedClient.subscribe("test", 1, (topic, msg) -> responses.countDown());

        MqttClient publishingClient = new MqttClient("tcp://localhost:" + mqttPort, PUBLISHING_CLIENT);
        publishingClient.connect(getConnectOptions(true, DEV_USERNAME));
        publishingClient.publish("test", "test_message_1".getBytes(), 1, false);

        persistedClient.disconnect();

        publishingClient.publish("test", "test_message_2".getBytes(), 1, false);

        publishingClient.disconnect();
        publishingClient.close();

        persistedClient.connect(persistedConnectOptions);

        log.warn("Persisted client connected: {}", isConnected());
        Assert.assertTrue(responses.await(30, TimeUnit.SECONDS));
    }

    private boolean isConnected() {
        return Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> persistedClient.isConnected(), Boolean::booleanValue);
    }

    private MqttConnectOptions getConnectOptions(boolean cleanSession, String username) {
        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setCleanSession(cleanSession);
        connectOptions.setUserName(username);
        return connectOptions;
    }
}
