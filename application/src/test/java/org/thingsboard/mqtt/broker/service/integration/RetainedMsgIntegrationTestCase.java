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

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.eclipse.paho.mqttv5.client.IMqttMessageListener;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = RetainedMsgIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@DaoSqlTest
@RunWith(SpringRunner.class)
public class RetainedMsgIntegrationTestCase extends AbstractPubSubIntegrationTest {

    public static final String TEST_RETAIN_TOPIC = "test/retain";

    @After
    public void clear() throws MqttException {
        log.warn("Removing retained msg after test finished...");
        createPubClientPublishRetainedMsgAndClose(new byte[0]);
    }

    @Test
    public void givenNewSubscriber_whenRetainedMsgWasAlreadyPublished_thenReceiveRetainedMsg() throws Throwable {
        CountDownLatch receivedResponses = new CountDownLatch(1);
        AtomicBoolean receivedRetainedMsg = new AtomicBoolean(false);

        createPubClientPublishRetainedMsgAndClose("online".getBytes());

        MqttClient subClient = createSubClientSubscribeToRetainedMsgTopicAndCheckMsg("test/+", receivedRetainedMsg, receivedResponses);

        boolean await = receivedResponses.await(3, TimeUnit.SECONDS);
        log.error("The result of awaiting is: [{}]", await);

        disconnectAndCloseClient(subClient);

        assertTrue(receivedRetainedMsg.get());
    }

    @Test
    public void givenOnlineSubscriber_whenRetainedMsgWasPublished_thenReceiveRegularMsg() throws Throwable {
        CountDownLatch receivedResponses = new CountDownLatch(1);
        AtomicBoolean receivedRetainedMsg = new AtomicBoolean(false);

        MqttClient subClient = createSubClientSubscribeToRetainedMsgTopicAndCheckMsg("#", receivedRetainedMsg, receivedResponses);

        createPubClientPublishRetainedMsgAndClose("online".getBytes());

        boolean await = receivedResponses.await(3, TimeUnit.SECONDS);
        log.error("The result of awaiting is: [{}]", await);

        disconnectAndCloseClient(subClient);

        assertFalse(receivedRetainedMsg.get());
    }

    @Test
    public void givenNewSubscriber_whenRetainedMsgWasAlreadyPublishedWithExpiration_thenReceiveRetainedMsg() throws Throwable {
        CountDownLatch receivedResponses = new CountDownLatch(1);
        AtomicBoolean receivedRetainedMsg = new AtomicBoolean(false);

        MqttClient pubClient = new MqttClient(SERVER_URI + mqttPort, RandomStringUtils.randomAlphabetic(10));
        pubClient.connect();

        MqttProperties properties = new MqttProperties();
        properties.setMessageExpiryInterval(100L);
        MqttMessage message = new MqttMessage("test".getBytes(StandardCharsets.UTF_8), 1, true, properties);
        pubClient.publish("expiration/retain", message);
        disconnectAndCloseClient(pubClient);

        MqttClient subClient = createSubClientSubscribeToRetainedMsgTopicAndCheckMsg("expiration/retain", receivedRetainedMsg, receivedResponses);

        boolean await = receivedResponses.await(3, TimeUnit.SECONDS);
        log.error("The result of awaiting is: [{}]", await);

        disconnectAndCloseClient(subClient);

        assertTrue(receivedRetainedMsg.get());
    }

    @Test
    public void givenNewSubscriber_whenRetainedMsgWasAlreadyPublishedWithExpirationAndSleep_thenNotReceiveRetainedMsg() throws Throwable {
        CountDownLatch receivedResponses = new CountDownLatch(1);
        AtomicBoolean receivedRetainedMsg = new AtomicBoolean(false);

        MqttClient pubClient = new MqttClient(SERVER_URI + mqttPort, RandomStringUtils.randomAlphabetic(10));
        pubClient.connect();

        MqttProperties properties = new MqttProperties();
        properties.setMessageExpiryInterval(1L);
        MqttMessage message = new MqttMessage("test".getBytes(StandardCharsets.UTF_8), 1, true, properties);
        pubClient.publish("expiration/retain", message);
        disconnectAndCloseClient(pubClient);

        Thread.sleep(1000);

        MqttClient subClient = createSubClientSubscribeToRetainedMsgTopicAndCheckMsg("expiration/retain", receivedRetainedMsg, receivedResponses);

        boolean await = receivedResponses.await(1, TimeUnit.SECONDS);
        log.error("The result of awaiting is: [{}]", await);

        disconnectAndCloseClient(subClient);

        assertFalse(receivedRetainedMsg.get());
    }

    private MqttClient createSubClientSubscribeToRetainedMsgTopicAndCheckMsg(String topicFilter, AtomicBoolean receivedRetainedMsg,
                                                                             CountDownLatch receivedResponses) throws MqttException {
        MqttClient subClient = new MqttClient(SERVER_URI + mqttPort, "test_sub_client");
        subClient.connect();
        IMqttMessageListener[] listeners = {(topic, message) -> {
            log.error("[{}] Received msg with id: {}, isRetained: {}", topic, message.getId(), message.isRetained());
            if (message.isRetained()) {
                receivedRetainedMsg.set(true);
            }
            receivedResponses.countDown();
        }};
        MqttSubscription[] subscriptions = {new MqttSubscription(topicFilter, 1)};
        subClient.subscribe(subscriptions, listeners);
        return subClient;
    }

    private void createPubClientPublishRetainedMsgAndClose(byte[] payload) throws MqttException {
        MqttClient pubClient = new MqttClient(SERVER_URI + mqttPort, "test_pub_client");
        pubClient.connect();
        pubClient.publish(TEST_RETAIN_TOPIC, payload, 1, true);
        disconnectAndCloseClient(pubClient);
    }

    private void disconnectAndCloseClient(MqttClient client) throws MqttException {
        client.disconnect();
        client.close();
    }
}
