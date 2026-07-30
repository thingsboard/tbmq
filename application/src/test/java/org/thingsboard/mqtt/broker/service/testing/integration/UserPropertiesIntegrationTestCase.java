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

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.mqttv5.client.IMqttMessageListener;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.eclipse.paho.mqttv5.common.packet.UserProperty;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.service.test.util.TestUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = UserPropertiesIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@DaoSqlTest
@RunWith(SpringRunner.class)
public class UserPropertiesIntegrationTestCase extends AbstractPubSubIntegrationTest {

    static final String PUB_CLIENT_ID_APP = "pubClientIdApp";
    static final String SUB_CLIENT_ID_APP = "subClientIdApp";
    static final String PUB_CLIENT_ID_DEV = "pubClientIdDev";
    static final String SUB_CLIENT_ID_DEV = "subClientIdDev";
    static final String PUB_CLIENT_ID_DEV_CLEAN = "pubClientIdDevCleanStart";
    static final String SUB_CLIENT_ID_DEV_CLEAN = "subClientIdDevCleanStart";
    static final String DEFAULT_USER_NAME = "defaultUserName";
    static final String MY_TOPIC = "my/topic";

    // Delivery to a persisted session goes through Kafka, which can take considerably longer than
    // the in-memory path on a loaded CI agent.
    private static final int DELIVERY_TIMEOUT_SEC = 30;

    @Autowired
    private MqttClientCredentialsService credentialsService;

    private final List<MqttClientCredentials> credentials = new ArrayList<>();

    // Clients are registered here as soon as they are created, so that @After closes them even when a
    // test fails before its own disconnect. A client left connected keeps both its session on the
    // broker and its Paho file persistence directory open, and since that directory is derived from
    // the client id and the server URI, the next client created with the same client id shares it:
    // whichever of the two is closed first deletes the directory and the other one then fails with
    // "Untranslated MqttException - RC: 0" on its receiver thread.
    private final List<MqttClient> clients = new ArrayList<>();
    private final List<MqttAsyncClient> asyncClients = new ArrayList<>();

    // Sub client of a persisted session test. Its session outlives the connection, so it has to be
    // dropped in @After to not leave a subscription behind for the other test classes.
    private MqttClient persistedSubClient;

    @Before
    public void beforeTest() {
        saveCredentials(TestUtils.createApplicationClientCredentials(PUB_CLIENT_ID_APP, null));
        saveCredentials(TestUtils.createApplicationClientCredentials(SUB_CLIENT_ID_APP, null));
        saveCredentials(TestUtils.createDeviceClientCredentials(PUB_CLIENT_ID_DEV, null));
        saveCredentials(TestUtils.createDeviceClientCredentials(SUB_CLIENT_ID_DEV, null));
        saveCredentials(TestUtils.createDeviceClientCredentials(PUB_CLIENT_ID_DEV_CLEAN, null));
        saveCredentials(TestUtils.createDeviceClientCredentials(SUB_CLIENT_ID_DEV_CLEAN, null));
        saveCredentials(TestUtils.createDeviceClientCredentials(null, DEFAULT_USER_NAME));
        enableBasicProvider();
    }

    @After
    public void clear() {
        clearSession(persistedSubClient);
        clients.forEach(this::disconnectAndClose);
        asyncClients.forEach(this::disconnectAndClose);
        credentials.forEach(c -> credentialsService.deleteCredentials(c.getId()));
    }

    private void saveCredentials(MqttClientCredentials credentials) {
        this.credentials.add(credentialsService.saveCredentials(credentials));
    }

    private MqttClient newClient(String clientId) throws MqttException {
        MqttClient client = new MqttClient(SERVER_URI + mqttPort, clientId);
        clients.add(client);
        return client;
    }

    private MqttAsyncClient newAsyncClient(String clientId) throws MqttException {
        MqttAsyncClient client = new MqttAsyncClient(SERVER_URI + mqttPort, clientId,
                null, DisabledMqtt5PingSender.DISABLED_MQTT_PING_SENDER, null);
        asyncClients.add(client);
        return client;
    }

    // Reconnecting with clean start is what makes the broker drop the persisted session. The client
    // authenticates by client id, so no credentials have to be set on the connection options.
    private void clearSession(MqttClient client) {
        if (client == null) {
            return;
        }
        try {
            if (client.isConnected()) {
                client.disconnect();
            }
            MqttConnectionOptions options = new MqttConnectionOptions();
            options.setCleanStart(true);
            client.connect(options);
            client.disconnect();
        } catch (Exception e) {
            log.warn("[{}] Failed to clear the session", client.getClientId(), e);
        }
    }

    // Cleanup must never throw: it would mask the failure of the test itself and, worse, skip the
    // cleanup of the clients that come after this one.
    private void disconnectAndClose(MqttClient client) {
        try {
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
        } catch (Exception e) {
            log.warn("[{}] Failed to close the client", client.getClientId(), e);
        }
    }

    private void disconnectAndClose(MqttAsyncClient client) {
        try {
            if (client.isConnected()) {
                // The last will clients are killed by the broker on keep alive expiry, so the client
                // side can still see the connection as alive here. Wait with a timeout to never hang.
                client.disconnect().waitForCompletion(TimeUnit.SECONDS.toMillis(5));
            }
            client.close();
        } catch (Exception e) {
            log.warn("[{}] Failed to close the client", client.getClientId(), e);
        }
    }

    @Test
    public void testUserPropertiesCleanSession() throws Throwable {
        processTest(SUB_CLIENT_ID_DEV_CLEAN, PUB_CLIENT_ID_DEV_CLEAN, true, 1);
    }

    @Test
    public void testUserPropertiesDevPersistedSession() throws Throwable {
        processTest(SUB_CLIENT_ID_DEV, PUB_CLIENT_ID_DEV, false, 2);
    }

    @Test
    public void testUserPropertiesAppPersistedSession() throws Throwable {
        processTest(SUB_CLIENT_ID_APP, PUB_CLIENT_ID_APP, false, 2);
    }

    private void processTest(String subClientId, String pubClientId, boolean cleanStart, int qos) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        MqttClient subClient = newClient(subClientId);
        if (!cleanStart) {
            persistedSubClient = subClient;
        }
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(cleanStart);
        subClient.connect(options);

        IMqttMessageListener[] listeners = {(topic, message) -> {
            log.debug("[{}] Received msg: {}", topic, message.getProperties());
            assertUserProperties(message.getProperties().getUserProperties());
            latch.countDown();
        }};

        MqttSubscription[] subscriptions = {new MqttSubscription(MY_TOPIC, qos)};
        subClient.subscribe(subscriptions, listeners);

        MqttClient pubClient = newClient(pubClientId);
        pubClient.connect();

        MqttMessage message = new MqttMessage(BrokerConstants.DUMMY_PAYLOAD, qos, false, MQTT_PROPERTIES);
        pubClient.publish(MY_TOPIC, message);

        boolean await = latch.await(DELIVERY_TIMEOUT_SEC, TimeUnit.SECONDS);
        Assert.assertTrue("[" + subClientId + "] Did not receive the msg in " + DELIVERY_TIMEOUT_SEC + " seconds", await);
    }

    @Test
    public void testUserPropertiesOnLastWillMsg() throws Throwable {
        CountDownLatch latch = new CountDownLatch(1);

        MqttClient subClient = newClient("subClientLastWill");
        MqttConnectionOptions subCliOptions = new MqttConnectionOptions();
        subCliOptions.setUserName(DEFAULT_USER_NAME);
        subClient.connect(subCliOptions);

        IMqttMessageListener[] listeners = {(topic, message) -> {
            log.debug("[{}] Received msg: {}", topic, message.getProperties());
            Assert.assertFalse(message.isRetained());
            Assert.assertEquals("will", new String(message.getPayload()));

            assertUserProperties(message.getProperties().getUserProperties());
            latch.countDown();
        }};

        MqttSubscription[] subscriptions = {new MqttSubscription(MY_TOPIC, 2)};
        subClient.subscribe(subscriptions, listeners);

        MqttAsyncClient pubClient = newAsyncClient("pubClientLastWill");

        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setWill(MY_TOPIC, new MqttMessage("will".getBytes(StandardCharsets.UTF_8), 2, false, MQTT_PROPERTIES));
        options.setWillMessageProperties(MQTT_PROPERTIES);
        options.setKeepAliveInterval(1);
        options.setUserName(DEFAULT_USER_NAME);
        pubClient.connect(options).waitForCompletion();

        boolean await = latch.await(10, TimeUnit.SECONDS);
        Assert.assertTrue(await);
    }

    @Test
    public void testUserPropertiesOnLastWillRetainedMsg() throws Throwable {
        CountDownLatch latch = new CountDownLatch(1);

        MqttAsyncClient pubClient = newAsyncClient("pubClientLastWillRetained");

        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setWill(MY_TOPIC, new MqttMessage("willRetained".getBytes(StandardCharsets.UTF_8), 2, true, MQTT_PROPERTIES));
        options.setWillMessageProperties(MQTT_PROPERTIES);
        options.setKeepAliveInterval(1);
        options.setUserName(DEFAULT_USER_NAME);
        pubClient.connect(options).waitForCompletion();

        boolean await = latch.await(3, TimeUnit.SECONDS);
        Assert.assertFalse(await);

        MqttClient subClient = newClient("subClientLastWillRetained");
        MqttConnectionOptions subCliOptions = new MqttConnectionOptions();
        subCliOptions.setUserName(DEFAULT_USER_NAME);
        subClient.connect(subCliOptions);

        CountDownLatch retainedWillLatch = new CountDownLatch(1);

        IMqttMessageListener[] listeners = {(topic, message) -> {
            log.debug("[{}] Received msg: {}", topic, message.getProperties());

            Assert.assertTrue(message.isRetained());
            Assert.assertEquals("willRetained", new String(message.getPayload()));

            assertUserProperties(message.getProperties().getUserProperties());
            retainedWillLatch.countDown();
        }};

        MqttSubscription[] subscriptions = {new MqttSubscription(MY_TOPIC, 2)};
        subClient.subscribe(subscriptions, listeners);

        boolean retainedAwait = retainedWillLatch.await(10, TimeUnit.SECONDS);
        Assert.assertTrue(retainedAwait);

        clearRetainedMsg();
    }

    private void clearRetainedMsg() throws MqttException {
        MqttClient pubClientClearRetained = new MqttClient(SERVER_URI + mqttPort, "pubClearRetained");
        MqttConnectionOptions clearRetainedOptions = new MqttConnectionOptions();
        clearRetainedOptions.setUserName(DEFAULT_USER_NAME);

        pubClientClearRetained.connect(clearRetainedOptions);
        pubClientClearRetained.publish(MY_TOPIC, new MqttMessage("".getBytes(StandardCharsets.UTF_8), 0, true, MQTT_PROPERTIES));
        pubClientClearRetained.disconnect();
        pubClientClearRetained.close();
    }

    private static void assertUserProperties(List<UserProperty> userProperties) {
        Assert.assertNotNull(userProperties);
        Assert.assertFalse(userProperties.isEmpty());

        Assert.assertEquals("myUserPropertyKey", userProperties.get(0).getKey());
        Assert.assertEquals("myUserPropertyValue", userProperties.get(0).getValue());
        Assert.assertEquals("region", userProperties.get(1).getKey());
        Assert.assertEquals("UA", userProperties.get(1).getValue());
        Assert.assertEquals("type", userProperties.get(2).getKey());
        Assert.assertEquals("JSON", userProperties.get(2).getValue());
    }
}
