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
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = UserPropertiesIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@RunWith(SpringRunner.class)
public class UserPropertiesIntegrationTestCase extends AbstractUserPropertiesIntegrationTest {

    @Test
    public void testUserPropertiesCleanSession() throws Throwable {
        processTest(SUB_CLIENT_ID_DEV_CLEAN, PUB_CLIENT_ID_DEV_CLEAN, true, 1);
    }

    @Test
    public void testUserPropertiesDevPersistedSession() throws Throwable {
        processTest(SUB_CLIENT_ID_DEV, PUB_CLIENT_ID_DEV, false, 2);
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
        pubClient.connect(options).waitForCompletion(BLOCKING_CALL_TIMEOUT_MS);

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
        pubClient.connect(options).waitForCompletion(BLOCKING_CALL_TIMEOUT_MS);

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

}
