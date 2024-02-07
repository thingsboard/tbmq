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
package org.thingsboard.mqtt.broker.service.integration.persistentsession;

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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.service.test.util.TestUtils;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = DeviceMsgExpiryIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "security.mqtt.basic.enabled=true"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class DeviceMsgExpiryIntegrationTestCase extends AbstractPubSubIntegrationTest {

    private static final String DEV_MSG_EXPIRY_CLIENT = "devMsgExpiryClient";
    private static final String MSG_EXPIRY_USER_NAME = "msgExpiryUn";

    @Autowired
    private MqttClientCredentialsService credentialsService;

    private MqttClientCredentials deviceCredentials;
    private MqttClient persistedClient;

    @Before
    public void init() {
        deviceCredentials = credentialsService.saveCredentials(
                TestUtils.createDeviceClientCredentials(null, MSG_EXPIRY_USER_NAME)
        );
    }

    @After
    public void clear() throws Exception {
        log.warn("After test finish...");
        TestUtils.clearPersistedClient(persistedClient, getOptions(true, MSG_EXPIRY_USER_NAME));
        credentialsService.deleteCredentials(deviceCredentials.getId());
    }

    @Test
    public void givenDevPersistentClient_whenSendPubMsgWithLargeExpiryInterval_thenReceiveMsg() throws Throwable {
        CountDownLatch receivedResponses = new CountDownLatch(1);
        AtomicBoolean receivedExpirationMsg = new AtomicBoolean(false);

        persistedClient = new MqttClient(SERVER_URI + mqttPort, DEV_MSG_EXPIRY_CLIENT);
        persistedClient.connect(getOptions(false, MSG_EXPIRY_USER_NAME));
        IMqttMessageListener[] listeners = {(topic, msg) -> {
            log.error("[{}] Received msg with id: {}", topic, msg.getId());
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
        MqttMessage mqttMessage = new MqttMessage("test".getBytes(StandardCharsets.UTF_8), 1, false, properties);
        pubClient.publish("expiration/topic", mqttMessage);
        TestUtils.disconnectAndCloseClient(pubClient);

        persistedClient.connect(getOptions(false, MSG_EXPIRY_USER_NAME));

        boolean await = receivedResponses.await(1, TimeUnit.SECONDS);
        log.error("The result of awaiting is: [{}]", await);

        assertTrue(receivedExpirationMsg.get());
    }

    @Test
    public void givenDevPersistentClient_whenSendPubMsgWithSmallExpiryInterval_thenReceiveMsg() throws Throwable {
        CountDownLatch receivedResponses = new CountDownLatch(1);
        AtomicBoolean receivedExpirationMsg = new AtomicBoolean(false);

        persistedClient = new MqttClient(SERVER_URI + mqttPort, DEV_MSG_EXPIRY_CLIENT);
        persistedClient.connect(getOptions(false, MSG_EXPIRY_USER_NAME));
        IMqttMessageListener[] listeners = {(topic, msg) -> {
            log.error("[{}] Received msg with id: {}", topic, msg.getId());
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
        MqttMessage mqttMessage = new MqttMessage("test".getBytes(StandardCharsets.UTF_8), 1, false, properties);
        pubClient.publish("expiration/topic", mqttMessage);
        TestUtils.disconnectAndCloseClient(pubClient);

        Thread.sleep(1100);

        persistedClient.connect(getOptions(false, MSG_EXPIRY_USER_NAME));

        boolean await = receivedResponses.await(1, TimeUnit.SECONDS);
        log.error("The result of awaiting is: [{}]", await);

        assertFalse(receivedExpirationMsg.get());
    }

}
