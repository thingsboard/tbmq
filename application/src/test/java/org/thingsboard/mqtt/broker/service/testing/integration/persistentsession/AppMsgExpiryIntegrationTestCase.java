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
package org.thingsboard.mqtt.broker.service.testing.integration.persistentsession;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.eclipse.paho.mqttv5.client.IMqttMessageListener;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
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
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.service.test.util.TestUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = AppMsgExpiryIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@RunWith(SpringRunner.class)
public class AppMsgExpiryIntegrationTestCase extends AbstractPubSubIntegrationTest {

    private static final String APP_MSG_EXPIRY_CLIENT = "appMsgExpiryClient";
    private static final String MSG_EXPIRY_USER_NAME = "msgExpiryUn";

    @Autowired
    private MqttClientCredentialsService credentialsService;

    private MqttClientCredentials applicationCredentials;
    private MqttClient persistedClient;

    @Before
    public void beforeTest() throws Exception {
        applicationCredentials = credentialsService.saveCredentials(
                TestUtils.createApplicationClientCredentials(null, MSG_EXPIRY_USER_NAME)
        );
        enableBasicProvider();
    }

    @After
    public void clear() throws Exception {
        log.warn("After test finish...");
        TestUtils.clearPersistedClient(persistedClient, getOptions(true, MSG_EXPIRY_USER_NAME));
        credentialsService.deleteCredentials(applicationCredentials.getId());
    }

    @Test
    public void givenAppPersistentClient_whenSendPubMsgWithLargeExpiryInterval_thenReceiveMsg() throws Throwable {
        CountDownLatch receivedResponses = new CountDownLatch(1);
        AtomicBoolean receivedExpirationMsg = new AtomicBoolean(false);

        persistedClient = new MqttClient(SERVER_URI + mqttPort, APP_MSG_EXPIRY_CLIENT);
        persistedClient.connect(getOptions(false, MSG_EXPIRY_USER_NAME));
        IMqttMessageListener[] listeners = {(topic, msg) -> {
            log.debug("[{}] Received msg with id: {}", topic, msg.getId());
            receivedExpirationMsg.set(true);
            receivedResponses.countDown();
        }};
        MqttSubscription[] subscriptions = {new MqttSubscription("expiration/topic", 1)};
        persistedClient.subscribe(subscriptions, listeners);
        persistedClient.disconnect();

        MqttClient pubClient = new MqttClient(SERVER_URI + mqttPort, RandomStringUtils.randomAlphabetic(10));
        pubClient.connect(getOptions(true, MSG_EXPIRY_USER_NAME));

        MqttProperties properties = new MqttProperties();
        properties.setMessageExpiryInterval(100L);
        MqttMessage mqttMessage = new MqttMessage(BrokerConstants.DUMMY_PAYLOAD, 1, false, properties);
        pubClient.publish("expiration/topic", mqttMessage);
        TestUtils.disconnectAndCloseClient(pubClient);

        persistedClient.connect(getOptions(false, MSG_EXPIRY_USER_NAME));

        boolean await = receivedResponses.await(1, TimeUnit.SECONDS);
        log.debug("The result of awaiting is: [{}]", await);
        assertTrue(await);

        assertTrue(receivedExpirationMsg.get());
    }

    @Test
    public void givenAppPersistentClient_whenSendPubMsgWithSmallExpiryInterval_thenReceiveMsg() throws Throwable {
        CountDownLatch receivedResponses = new CountDownLatch(1);
        AtomicBoolean receivedExpirationMsg = new AtomicBoolean(false);

        persistedClient = new MqttClient(SERVER_URI + mqttPort, APP_MSG_EXPIRY_CLIENT);
        persistedClient.connect(getOptions(false, MSG_EXPIRY_USER_NAME));
        IMqttMessageListener[] listeners = {(topic, msg) -> {
            log.debug("[{}] Received msg with id: {}", topic, msg.getId());
            receivedExpirationMsg.set(true);
            receivedResponses.countDown();
        }};
        MqttSubscription[] subscriptions = {new MqttSubscription("expiration/topic", 1)};
        persistedClient.subscribe(subscriptions, listeners);
        persistedClient.disconnect();

        MqttClient pubClient = new MqttClient(SERVER_URI + mqttPort, RandomStringUtils.randomAlphabetic(10));
        pubClient.connect(getOptions(true, MSG_EXPIRY_USER_NAME));

        MqttProperties properties = new MqttProperties();
        properties.setMessageExpiryInterval(1L);
        MqttMessage mqttMessage = new MqttMessage(BrokerConstants.DUMMY_PAYLOAD, 1, false, properties);
        pubClient.publish("expiration/topic", mqttMessage);
        TestUtils.disconnectAndCloseClient(pubClient);

        Thread.sleep(1100);

        persistedClient.connect(getOptions(false, MSG_EXPIRY_USER_NAME));

        boolean await = receivedResponses.await(1, TimeUnit.SECONDS);
        log.debug("The result of awaiting is: [{}]", await);

        assertFalse(receivedExpirationMsg.get());
    }

    @Test
    public void givenAppPersistentClient_whenSendPubMsgWithZeroExpiryInterval_thenReceiveMsg() throws Throwable {
        // TBMQ never expires a message with a Message Expiry Interval of 0
        assertMsgWithExpiryIntervalReceivedOnReconnect(0L);
    }

    @Test
    public void givenAppPersistentClient_whenSendPubMsgWithMaxUnsignedExpiryInterval_thenReceiveMsg() throws Throwable {
        // MQTT-5 1.5.3: the interval is unsigned; netty reads 0xFFFFFFFF into an int as -1
        assertMsgWithExpiryIntervalReceivedOnReconnect(0xFFFFFFFFL);
    }

    private void assertMsgWithExpiryIntervalReceivedOnReconnect(long messageExpiryInterval) throws Throwable {
        CountDownLatch receivedResponses = new CountDownLatch(1);
        AtomicReference<Long> receivedExpiryInterval = new AtomicReference<>();

        persistedClient = new MqttClient(SERVER_URI + mqttPort, APP_MSG_EXPIRY_CLIENT);
        persistedClient.connect(getOptions(false, MSG_EXPIRY_USER_NAME));
        IMqttMessageListener[] listeners = {(topic, msg) -> {
            log.debug("[{}] Received msg with id: {}", topic, msg.getId());
            receivedExpiryInterval.set(msg.getProperties().getMessageExpiryInterval());
            receivedResponses.countDown();
        }};
        MqttSubscription[] subscriptions = {new MqttSubscription("expiration/topic", 1)};
        persistedClient.subscribe(subscriptions, listeners);
        persistedClient.disconnect();

        MqttClient pubClient = new MqttClient(SERVER_URI + mqttPort, RandomStringUtils.randomAlphabetic(10));
        pubClient.connect(getOptions(true, MSG_EXPIRY_USER_NAME));

        MqttProperties properties = new MqttProperties();
        properties.setMessageExpiryInterval(messageExpiryInterval);
        MqttMessage mqttMessage = new MqttMessage(BrokerConstants.DUMMY_PAYLOAD, 1, false, properties);
        pubClient.publish("expiration/topic", mqttMessage);
        TestUtils.disconnectAndCloseClient(pubClient);

        persistedClient.connect(getOptions(false, MSG_EXPIRY_USER_NAME));

        assertTrue(receivedResponses.await(2, TimeUnit.SECONDS));

        // the interval forwarded to the subscriber is the received one minus the wait, read unsigned
        Long forwardedInterval = receivedExpiryInterval.get();
        assertNotNull(forwardedInterval);
        if (messageExpiryInterval == 0) {
            assertEquals(0L, forwardedInterval.longValue());
        } else {
            assertTrue(forwardedInterval > messageExpiryInterval - 60 && forwardedInterval <= messageExpiryInterval);
        }
    }

}
