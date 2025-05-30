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

import io.netty.handler.codec.mqtt.MqttReasonCodes;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.eclipse.paho.mqttv5.client.IMqttMessageListener;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = Mqtt5IntegrationTestCase.class, loader = SpringBootContextLoader.class)
@DaoSqlTest
@RunWith(SpringRunner.class)
public class Mqtt5IntegrationTestCase extends AbstractPubSubIntegrationTest {

    static final String MY_TOPIC = "my/topic";

    @Before
    public void init() throws Exception {
    }

    @After
    public void clear() {
    }

    @Test
    public void testSendLastWillOnDisconnectMsgWithSpecialReasonCode() throws Throwable {
        CountDownLatch latch = new CountDownLatch(1);

        MqttClient subClient = new MqttClient(SERVER_URI + mqttPort, "subClientLastWill");
        subClient.connect();

        IMqttMessageListener[] listeners = {(topic, message) -> {
            log.error("[{}] Received msg: {}", topic, message.getProperties());
            Assert.assertEquals("will", new String(message.getPayload()));

            latch.countDown();
        }};

        MqttSubscription[] subscriptions = {new MqttSubscription(MY_TOPIC, 2)};
        subClient.subscribe(subscriptions, listeners);

        MqttAsyncClient pubClient = new MqttAsyncClient(SERVER_URI + mqttPort, "pubClientLastWill");

        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setWill(MY_TOPIC, new MqttMessage("will".getBytes(StandardCharsets.UTF_8), 2, false, MQTT_PROPERTIES));
        options.setWillMessageProperties(MQTT_PROPERTIES);
        pubClient.connect(options).waitForCompletion();

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .until(pubClient::isConnected);

        pubClient.disconnect(0, null, null, MqttReasonCodes.Disconnect.DISCONNECT_WITH_WILL_MESSAGE.byteValue(), new MqttProperties()).waitForCompletion();
        pubClient.close();

        boolean await = latch.await(10, TimeUnit.SECONDS);
        Assert.assertTrue(await);

        subClient.disconnect();
        subClient.close();
    }

    @Test
    public void testNoLastWillSentWhenReconnectSessionBeforeWillDelayElapsed() throws Throwable {
        AtomicBoolean lastWillReceived = new AtomicBoolean(false);

        MqttClient subClient = new MqttClient(SERVER_URI + mqttPort, "subClientLastWill");
        subClient.connect();

        IMqttMessageListener[] listeners = {(topic, message) -> {
            log.error("[{}] Received msg: {}", topic, message.getProperties());
            Assert.assertEquals("will", new String(message.getPayload()));
            lastWillReceived.set(true);
        }};

        MqttSubscription[] subscriptions = {new MqttSubscription(MY_TOPIC, 2)};
        subClient.subscribe(subscriptions, listeners);

        final MqttAsyncClient pubClient = new MqttAsyncClient(SERVER_URI + mqttPort, "pubClientLastWill",
                null, DisabledMqtt5PingSender.DISABLED_MQTT_PING_SENDER, null);

        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setSessionExpiryInterval(100L);
        MqttProperties mqttProperties = getMqttPropertiesWithWillDelay(3L);
        options.setWill(MY_TOPIC, new MqttMessage("will".getBytes(StandardCharsets.UTF_8), 1, false, mqttProperties));
        options.setWillMessageProperties(mqttProperties);
        options.setKeepAliveInterval(1);
        pubClient.connect(options).waitForCompletion();

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> !pubClient.isConnected());

        Thread.sleep(1000); // needed to wait a bit for will delay

        MqttAsyncClient pubClientReconnect = new MqttAsyncClient(SERVER_URI + mqttPort, "pubClientLastWill");
        pubClientReconnect.connect().waitForCompletion();

        Thread.sleep(3000); // needed to wait a bit for will delay

        Assert.assertFalse(lastWillReceived.get());

        subClient.disconnect();
        subClient.close();
    }

    @Test
    public void testLastWillSentWhenSessionExpires() throws Throwable {
        CountDownLatch latch = new CountDownLatch(1);

        MqttClient subClient = new MqttClient(SERVER_URI + mqttPort, "subClientLastWill");
        subClient.connect();

        IMqttMessageListener[] listeners = {(topic, message) -> {
            log.error("[{}] Received msg: {}", topic, message.getProperties());
            Assert.assertEquals("will", new String(message.getPayload()));
            latch.countDown();
        }};

        MqttSubscription[] subscriptions = {new MqttSubscription(MY_TOPIC, 2)};
        subClient.subscribe(subscriptions, listeners);

        final MqttAsyncClient pubClient = new MqttAsyncClient(SERVER_URI + mqttPort, "pubClientLastWill",
                null, DisabledMqtt5PingSender.DISABLED_MQTT_PING_SENDER, null);

        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setSessionExpiryInterval(5L);
        MqttProperties mqttProperties = getMqttPropertiesWithWillDelay(30L);
        options.setWill(MY_TOPIC, new MqttMessage("will".getBytes(StandardCharsets.UTF_8), 1, false, mqttProperties));
        options.setWillMessageProperties(mqttProperties);
        options.setKeepAliveInterval(1);
        pubClient.connect(options).waitForCompletion();

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> !pubClient.isConnected());

        boolean await = latch.await(10, TimeUnit.SECONDS);
        Assert.assertTrue(await);

        subClient.disconnect();
        subClient.close();
    }

    @Test
    public void testLastWillSentWhenSessionExpiresWithValueFromDisconnect() throws Throwable {
        CountDownLatch latch = new CountDownLatch(1);

        MqttClient subClient = new MqttClient(SERVER_URI + mqttPort, "subClientLastWill");
        subClient.connect();

        IMqttMessageListener[] listeners = {(topic, message) -> {
            log.error("[{}] Received msg: {}", topic, message.getProperties());
            Assert.assertEquals("will", new String(message.getPayload()));
            latch.countDown();
        }};

        MqttSubscription[] subscriptions = {new MqttSubscription(MY_TOPIC, 2)};
        subClient.subscribe(subscriptions, listeners);

        final MqttAsyncClient pubClient = new MqttAsyncClient(SERVER_URI + mqttPort, "pubClientLastWill",
                null, DisabledMqtt5PingSender.DISABLED_MQTT_PING_SENDER, null);

        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setSessionExpiryInterval(15L);
        MqttProperties mqttProperties = getMqttPropertiesWithWillDelay(30L);
        options.setWill(MY_TOPIC, new MqttMessage("will".getBytes(StandardCharsets.UTF_8), 1, false, mqttProperties));
        options.setWillMessageProperties(mqttProperties);
        options.setKeepAliveInterval(10);
        pubClient.connect(options).waitForCompletion();

        MqttProperties disconnectProperties = new MqttProperties();
        disconnectProperties.setSessionExpiryInterval(5L);
        pubClient.disconnect(0, null, null, MqttReasonCodes.Disconnect.DISCONNECT_WITH_WILL_MESSAGE.byteValue(), disconnectProperties).waitForCompletion();

        boolean await = latch.await(10, TimeUnit.SECONDS);
        Assert.assertTrue(await);

        subClient.disconnect();
        subClient.close();
    }

    private MqttProperties getMqttPropertiesWithWillDelay(long willDelayInterval) {
        MqttProperties mqttProperties = new MqttProperties();
        mqttProperties.setWillDelayInterval(willDelayInterval);
        return mqttProperties;
    }

    @Test
    public void testSendMsgWithPropertiesForMqtt3AndMqtt5Clients() throws Throwable {
        CountDownLatch latch = new CountDownLatch(2);

        MqttClient subClientMqtt5 = new MqttClient(SERVER_URI + mqttPort, "subClientMqtt5");
        subClientMqtt5.connect();

        IMqttMessageListener[] listeners = {(topic, message) -> {
            log.error("[{}] Received msg: {}", topic, message.getProperties());
            Assert.assertEquals("test123", new String(message.getPayload()));
            Assert.assertEquals(2, message.getQos());
            Assert.assertNotNull(message.getProperties());

            latch.countDown();
        }};

        MqttSubscription[] subscriptions = {new MqttSubscription(MY_TOPIC, 2)};
        subClientMqtt5.subscribe(subscriptions, listeners);

        org.eclipse.paho.client.mqttv3.MqttClient subClientMqtt3 =
                new org.eclipse.paho.client.mqttv3.MqttClient(SERVER_URI + mqttPort, "subClientMqtt3");
        subClientMqtt3.connect();
        subClientMqtt3.subscribe(MY_TOPIC, 2, (topic, message) -> {
            log.error("[{}] Received msg: {}", topic, message);
            Assert.assertEquals("test123", new String(message.getPayload()));
            Assert.assertEquals(2, message.getQos());

            latch.countDown();
        });

        MqttClient pubClient = new MqttClient(SERVER_URI + mqttPort, "pubClient");
        pubClient.connect();

        MqttMessage message = new MqttMessage("test123".getBytes(StandardCharsets.UTF_8), 2, false, MQTT_PROPERTIES);
        pubClient.publish(MY_TOPIC, message);

        pubClient.disconnect();
        pubClient.close();

        boolean await = latch.await(10, TimeUnit.SECONDS);
        Assert.assertTrue(await);

        subClientMqtt3.disconnect();
        subClientMqtt3.close();

        subClientMqtt5.disconnect();
        subClientMqtt5.close();
    }

    @Test
    public void testSendMsgAndReceiveForSameClient() throws Throwable {
        CountDownLatch latch = new CountDownLatch(1);

        MqttClient client = new MqttClient(SERVER_URI + mqttPort, "sameClient");
        client.connect();

        IMqttMessageListener[] listeners = {(topic, message) -> {
            log.error("[{}] Received msg: {}", topic, message.getProperties());
            Assert.assertEquals("myMsg", new String(message.getPayload()));

            latch.countDown();
        }};

        MqttSubscription[] subscriptions = {new MqttSubscription(MY_TOPIC, 2)};
        client.subscribe(subscriptions, listeners);

        client.publish(MY_TOPIC, "myMsg".getBytes(StandardCharsets.UTF_8), 2, false);

        boolean await = latch.await(3, TimeUnit.SECONDS);
        Assert.assertTrue(await);

        client.disconnect();
        client.close();
    }

    @Test
    public void testSendMsgAndNotReceiveForSameClientWhenNoLocal() throws Throwable {
        CountDownLatch latch = new CountDownLatch(1);

        MqttClient client = new MqttClient(SERVER_URI + mqttPort, "sameClientNoLocal");
        client.connect();

        IMqttMessageListener[] listeners = {(topic, message) -> {
            log.error("[{}] Received msg: {}", topic, message.getProperties());
            Assert.assertEquals("myMsg", new String(message.getPayload()));

            latch.countDown();
        }};

        MqttSubscription mqttSubscription = new MqttSubscription(MY_TOPIC, 2);
        mqttSubscription.setNoLocal(true);
        MqttSubscription[] subscriptions = {mqttSubscription};
        client.subscribe(subscriptions, listeners);

        client.publish(MY_TOPIC, "myMsg".getBytes(StandardCharsets.UTF_8), 2, false);

        boolean await = latch.await(2, TimeUnit.SECONDS);
        Assert.assertFalse(await);

        client.disconnect();
        client.close();
    }
}