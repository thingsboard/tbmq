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
package org.thingsboard.mqtt.broker.service.integration;

import lombok.extern.slf4j.Slf4j;
import net.jodah.concurrentunit.Waiter;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = LastWillAndTestamentIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@DaoSqlTest
@RunWith(SpringRunner.class)
public class LastWillAndTestamentIntegrationTestCase extends AbstractPubSubIntegrationTest {

    private static final String TEST_TOPIC = "test";
    private static final String TEST_MESSAGE = "test_message";

    @Test(expected = TimeoutException.class)
    public void testNoLastWillOnDisconnect() throws Throwable {
        MqttClient subClient = new MqttClient(SERVER_URI + mqttPort, "test_sub_client" + UUID.randomUUID().toString().substring(0, 5));
        subClient.connect();
        Waiter waiter = new Waiter();
        subClient.subscribe(TEST_TOPIC, (topic, message) -> {
            waiter.assertNull(message);
            waiter.assertNull(topic);
        });

        MqttClient lastWillClient = new MqttClient(SERVER_URI + mqttPort, "test_last_will_client" + UUID.randomUUID().toString().substring(0, 5));
        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setWill(TEST_TOPIC, TEST_MESSAGE.getBytes(), 1, false);
        lastWillClient.connect(connectOptions);
        lastWillClient.disconnect();
        waiter.await(1, TimeUnit.SECONDS);

        lastWillClient.close();
        subClient.close();
    }

    @Test
    public void testLastWillOnKeepAliveFail() throws Throwable {
        MqttClient subClient = new MqttClient(SERVER_URI + mqttPort, "test_sub_client" + UUID.randomUUID().toString().substring(0, 5));
        subClient.connect();

        Waiter waiter = new Waiter();
        subClient.subscribe(TEST_TOPIC, (topic, message) -> {
            String receivedMsg = new String(message.getPayload(), StandardCharsets.UTF_8);
            waiter.assertEquals(TEST_MESSAGE, receivedMsg);
            waiter.assertEquals(1, message.getQos());
            waiter.resume();
        });

        MqttAsyncClient lastWillClient = new MqttAsyncClient(SERVER_URI + mqttPort, "test_last_will_client" + UUID.randomUUID().toString().substring(0, 5),
                null, DisabledMqttPingSender.DISABLED_MQTT_PING_SENDER);
        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setKeepAliveInterval(1);
        connectOptions.setWill(TEST_TOPIC, TEST_MESSAGE.getBytes(), 1, false);
        lastWillClient.connect(connectOptions).waitForCompletion();
        waiter.await();
        subClient.disconnect();
        subClient.close();
    }

    @Test
    public void testLastWillOnProtocolError() {
        // TODO implement own MqttClient with possibility to violate MQTT protocol and trigger LastWill msg
        Assert.assertEquals(1, 1);
    }
}
