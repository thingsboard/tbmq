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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = UserPropertiesIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@DaoSqlTest
@RunWith(SpringRunner.class)
public class UserPropertiesIntegrationTestCase extends AbstractPubSubIntegrationTest {

    static final String PUB_CLIENT_ID_APP = "pubClientIdApp";
    static final String SUB_CLIENT_ID_APP = "subClientIdApp";
    static final String PUB_CLIENT_ID_DEV = "pubClientIdDev";
    static final String SUB_CLIENT_ID_DEV = "subClientIdDev";
    static final String DEFAULT_USER_NAME = "defaultUserName";
    static final String MY_TOPIC = "my/topic";

    @Autowired
    private MqttClientCredentialsService credentialsService;

    private MqttClientCredentials appPubCredentials;
    private MqttClientCredentials appSubCredentials;
    private MqttClientCredentials devPubCredentials;
    private MqttClientCredentials devSubCredentials;
    private MqttClientCredentials defaultCredentials;

    private MqttClient appSubClient;
    private MqttClient devSubClient;

    @Before
    public void beforeTest() throws Exception {
        appPubCredentials = credentialsService.saveCredentials(TestUtils.createApplicationClientCredentials(PUB_CLIENT_ID_APP, null));
        appSubCredentials = credentialsService.saveCredentials(TestUtils.createApplicationClientCredentials(SUB_CLIENT_ID_APP, null));
        devPubCredentials = credentialsService.saveCredentials(TestUtils.createDeviceClientCredentials(PUB_CLIENT_ID_DEV, null));
        devSubCredentials = credentialsService.saveCredentials(TestUtils.createDeviceClientCredentials(SUB_CLIENT_ID_DEV, null));
        defaultCredentials = credentialsService.saveCredentials(TestUtils.createDeviceClientCredentials(null, DEFAULT_USER_NAME));
        enableBasicProvider();
    }

    @After
    public void clear() throws Exception {
        clearAndDisconnect(appSubClient);
        clearAndDisconnect(devSubClient);

        credentialsService.deleteCredentials(appPubCredentials.getId());
        credentialsService.deleteCredentials(appSubCredentials.getId());
        credentialsService.deleteCredentials(devPubCredentials.getId());
        credentialsService.deleteCredentials(devSubCredentials.getId());
        credentialsService.deleteCredentials(defaultCredentials.getId());
    }

    private void clearAndDisconnect(MqttClient client) throws Exception {
        if (client != null) {
            if (client.isConnected()) {
                client.disconnect();
            }
            MqttConnectionOptions options = new MqttConnectionOptions();
            options.setCleanStart(true);
            client.connect(options);
            client.disconnect();
            client.close();
        }
    }

    @Test
    public void testUserPropertiesCleanSession() throws Throwable {
        devSubClient = processTest(SUB_CLIENT_ID_DEV, PUB_CLIENT_ID_DEV, true, 1);
    }

    @Test
    public void testUserPropertiesDevPersistedSession() throws Throwable {
        devSubClient = processTest(SUB_CLIENT_ID_DEV, PUB_CLIENT_ID_DEV, false, 2);
    }

    @Test
    public void testUserPropertiesAppPersistedSession() throws Throwable {
        appSubClient = processTest(SUB_CLIENT_ID_APP, PUB_CLIENT_ID_APP, false, 2);
    }

    private MqttClient processTest(String subClientId, String pubClientId, boolean cleanStart, int qos) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        MqttClient subClient = new MqttClient(SERVER_URI + mqttPort, subClientId);
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(cleanStart);
        subClient.connect(options);

        IMqttMessageListener[] listeners = {(topic, message) -> {
            log.error("[{}] Received msg: {}", topic, message.getProperties());
            assertUserProperties(message.getProperties().getUserProperties());
            latch.countDown();
        }};

        MqttSubscription[] subscriptions = {new MqttSubscription(MY_TOPIC, qos)};
        subClient.subscribe(subscriptions, listeners);

        MqttClient pubClient = new MqttClient(SERVER_URI + mqttPort, pubClientId);
        pubClient.connect();

        MqttMessage message = new MqttMessage(BrokerConstants.DUMMY_PAYLOAD, qos, false, MQTT_PROPERTIES);
        pubClient.publish(MY_TOPIC, message);

        boolean await = latch.await(10, TimeUnit.SECONDS);
        Assert.assertTrue(await);

        pubClient.disconnect();
        pubClient.close();
        subClient.disconnect();

        return subClient;
    }

    @Test
    public void testUserPropertiesOnLastWillMsg() throws Throwable {
        CountDownLatch latch = new CountDownLatch(1);

        MqttClient subClient = new MqttClient(SERVER_URI + mqttPort, "subClientLastWill");
        MqttConnectionOptions subCliOptions = new MqttConnectionOptions();
        subCliOptions.setUserName(DEFAULT_USER_NAME);
        subClient.connect(subCliOptions);

        IMqttMessageListener[] listeners = {(topic, message) -> {
            log.error("[{}] Received msg: {}", topic, message.getProperties());
            Assert.assertFalse(message.isRetained());
            Assert.assertEquals("will", new String(message.getPayload()));

            assertUserProperties(message.getProperties().getUserProperties());
            latch.countDown();
        }};

        MqttSubscription[] subscriptions = {new MqttSubscription(MY_TOPIC, 2)};
        subClient.subscribe(subscriptions, listeners);

        MqttAsyncClient pubClient = new MqttAsyncClient(SERVER_URI + mqttPort, "pubClientLastWill",
                null, DisabledMqtt5PingSender.DISABLED_MQTT_PING_SENDER, null);

        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setWill(MY_TOPIC, new MqttMessage("will".getBytes(StandardCharsets.UTF_8), 2, false, MQTT_PROPERTIES));
        options.setWillMessageProperties(MQTT_PROPERTIES);
        options.setKeepAliveInterval(1);
        options.setUserName(DEFAULT_USER_NAME);
        pubClient.connect(options).waitForCompletion();

        boolean await = latch.await(10, TimeUnit.SECONDS);
        Assert.assertTrue(await);

        subClient.disconnect();
        subClient.close();
    }

    @Test
    public void testUserPropertiesOnLastWillRetainedMsg() throws Throwable {
        CountDownLatch latch = new CountDownLatch(1);

        MqttAsyncClient pubClient = new MqttAsyncClient(SERVER_URI + mqttPort, "pubClientLastWillRetained",
                null, DisabledMqtt5PingSender.DISABLED_MQTT_PING_SENDER, null);

        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setWill(MY_TOPIC, new MqttMessage("willRetained".getBytes(StandardCharsets.UTF_8), 2, true, MQTT_PROPERTIES));
        options.setWillMessageProperties(MQTT_PROPERTIES);
        options.setKeepAliveInterval(1);
        options.setUserName(DEFAULT_USER_NAME);
        pubClient.connect(options).waitForCompletion();

        boolean await = latch.await(3, TimeUnit.SECONDS);
        Assert.assertFalse(await);

        MqttClient subClient = new MqttClient(SERVER_URI + mqttPort, "subClientLastWillRetained");
        MqttConnectionOptions subCliOptions = new MqttConnectionOptions();
        subCliOptions.setUserName(DEFAULT_USER_NAME);
        subClient.connect(subCliOptions);

        CountDownLatch retainedWillLatch = new CountDownLatch(1);

        IMqttMessageListener[] listeners = {(topic, message) -> {
            log.error("[{}] Received msg: {}", topic, message.getProperties());

            Assert.assertTrue(message.isRetained());
            Assert.assertEquals("willRetained", new String(message.getPayload()));

            assertUserProperties(message.getProperties().getUserProperties());
            retainedWillLatch.countDown();
        }};

        MqttSubscription[] subscriptions = {new MqttSubscription(MY_TOPIC, 2)};
        subClient.subscribe(subscriptions, listeners);

        boolean retainedAwait = retainedWillLatch.await(10, TimeUnit.SECONDS);
        Assert.assertTrue(retainedAwait);

        subClient.disconnect();
        subClient.close();

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
