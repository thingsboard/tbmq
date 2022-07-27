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
package org.thingsboard.mqtt.broker.service.integration;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = RetainedMsgIntegrationTest.class, loader = SpringBootContextLoader.class)
@DaoSqlTest
@RunWith(SpringRunner.class)
public class RetainedMsgIntegrationTest extends AbstractPubSubIntegrationTest {

    public static final String TEST_RETAIN_TOPIC = "test/retain";

    @After
    public void clear() throws MqttException {
        log.warn("Removing retained msg after test finished...");
        MqttClient pubClient = new MqttClient("tcp://localhost:" + mqttPort, "test_pub_client");
        pubClient.connect();
        pubClient.publish(TEST_RETAIN_TOPIC, new byte[0], 1, true);
        pubClient.disconnect();
        pubClient.close();
    }

    @Test
    public void givenNewSubscriber_whenRetainedMsgWasAlreadyPublished_thenReceiveRetainedMsg() throws Throwable {
        CountDownLatch receivedResponses = new CountDownLatch(1);
        AtomicBoolean receivedRetainedMsg = new AtomicBoolean(false);

        MqttClient pubClient = new MqttClient("tcp://localhost:" + mqttPort, "test_pub_client");
        pubClient.connect();
        pubClient.publish(TEST_RETAIN_TOPIC, "online".getBytes(), 1, true);
        pubClient.disconnect();
        pubClient.close();

        MqttClient subClient = new MqttClient("tcp://localhost:" + mqttPort, "test_sub_client");
        subClient.connect();
        subClient.subscribe("test/+", 1, (topic, message) -> {
            log.error("[{}] Received msg with id: {}, isRetained: {}", topic, message.getId(), message.isRetained());
            if (message.isRetained()) {
                receivedRetainedMsg.set(true);
            }
            receivedResponses.countDown();
        });

        boolean await = receivedResponses.await(3, TimeUnit.SECONDS);
        log.error("The result of awaiting is: [{}]", await);

        subClient.disconnect();
        subClient.close();

        assertTrue(receivedRetainedMsg.get());
    }

    @Test
    public void givenOnlineSubscriber_whenRetainedMsgWasPublished_thenReceiveRegularMsg() throws Throwable {
        CountDownLatch receivedResponses = new CountDownLatch(1);
        AtomicBoolean receivedRetainedMsg = new AtomicBoolean(false);

        MqttClient subClient = new MqttClient("tcp://localhost:" + mqttPort, "test_sub_client");
        subClient.connect();
        subClient.subscribe("#", 1, (topic, message) -> {
            log.error("[{}] Received msg with id: {}, isRetained: {}", topic, message.getId(), message.isRetained());
            if (message.isRetained()) {
                receivedRetainedMsg.set(true);
            }
            receivedResponses.countDown();
        });

        MqttClient pubClient = new MqttClient("tcp://localhost:" + mqttPort, "test_pub_client");
        pubClient.connect();
        pubClient.publish(TEST_RETAIN_TOPIC, "online".getBytes(), 1, true);
        pubClient.disconnect();
        pubClient.close();

        boolean await = receivedResponses.await(3, TimeUnit.SECONDS);
        log.error("The result of awaiting is: [{}]", await);

        subClient.disconnect();
        subClient.close();

        assertFalse(receivedRetainedMsg.get());
    }
}
