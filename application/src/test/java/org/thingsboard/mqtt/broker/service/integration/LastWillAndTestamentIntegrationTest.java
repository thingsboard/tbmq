/**
 * Copyright © 2016-2020 The Thingsboard Authors
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
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPingSender;
import org.eclipse.paho.client.mqttv3.internal.ClientComms;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = LastWillAndTestamentIntegrationTest.class, loader = SpringBootContextLoader.class)
@DaoSqlTest
@RunWith(SpringRunner.class)
public class LastWillAndTestamentIntegrationTest extends AbstractPubSubIntegrationTest {
    private static final String TEST_TOPIC = "test";
    private static final String TEST_MESSAGE = "test_message";

    @Test(expected = TimeoutException.class)
    public void testNoLastWillOnDisconnect() throws Throwable {
        MqttClient subClient = new MqttClient("tcp://" + mqttAddress + ":" + mqttPort, "test_sub_client");
        subClient.connect();
        Waiter waiter = new Waiter();
        subClient.subscribe(TEST_TOPIC, (topic, message) -> {
            waiter.assertNull(message);
            waiter.assertNull(topic);
        });

        MqttClient lastWillClient = new MqttClient("tcp://" + mqttAddress + ":" + mqttPort, "test_last_will_client");
        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setWill(TEST_TOPIC, TEST_MESSAGE.getBytes(), 1, false);
        lastWillClient.connect(connectOptions);
        lastWillClient.disconnect();
        lastWillClient.close();
        try {
            waiter.await(1, TimeUnit.SECONDS);
        } finally {
            subClient.disconnect();
            subClient.close();
        }
    }

    @Test
    public void testLastWillOnClientClose() throws Throwable {
        MqttClient subClient = new MqttClient("tcp://" + mqttAddress + ":" + mqttPort, "test_sub_client");
        subClient.connect();

        Waiter waiter = new Waiter();
        subClient.subscribe(TEST_TOPIC, (topic, message) -> {
            waiter.assertEquals(TEST_MESSAGE.getBytes(), message.getPayload());
            waiter.assertEquals(1, message.getQos());
            waiter.resume();
        });

        MqttClient lastWillClient = new MqttClient("tcp://" + mqttAddress + ":" + mqttPort, "test_last_will_client");
        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setWill(TEST_TOPIC, TEST_MESSAGE.getBytes(), 1, false);
        lastWillClient.connect(connectOptions);
        lastWillClient.close();
        waiter.await(1, TimeUnit.SECONDS);
        subClient.disconnect();
        subClient.close();
    }

    @Test
    public void testLastWillOnKeepAliveFail() throws Throwable {
        MqttClient subClient = new MqttClient("tcp://" + mqttAddress + ":" + mqttPort, "test_sub_client");
        subClient.connect();

        Waiter waiter = new Waiter();
        subClient.subscribe(TEST_TOPIC, (topic, message) -> {
            waiter.assertEquals(TEST_MESSAGE.getBytes(), message.getPayload());
            waiter.assertEquals(1, message.getQos());
            waiter.resume();
        });

        MqttAsyncClient lastWillClient = new MqttAsyncClient("tcp://" + mqttAddress + ":" + mqttPort, "test_last_will_client",
                null, DisabledMqttPingSender.DISABLED_MQTT_PING_SENDER);
        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setKeepAliveInterval(1);
        connectOptions.setWill(TEST_TOPIC, TEST_MESSAGE.getBytes(), 1, false);
        lastWillClient.connect(connectOptions);
        waiter.await(2, TimeUnit.SECONDS);
        subClient.disconnect();
        subClient.close();
    }

    @Test
    public void testLastWillOnProtocolError() throws Throwable {
        MqttClient subClient = new MqttClient("tcp://" + mqttAddress + ":" + mqttPort, "test_sub_client");
        subClient.connect();

        Waiter waiter = new Waiter();
        subClient.subscribe(TEST_TOPIC, (topic, message) -> {
            waiter.assertEquals(TEST_MESSAGE.getBytes(), message.getPayload());
            waiter.assertEquals(1, message.getQos());
            waiter.resume();
        });

        MqttClient lastWillClient = new MqttClient("tcp://" + mqttAddress + ":" + mqttPort, "test_last_will_client");
        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setWill(TEST_TOPIC, TEST_MESSAGE.getBytes(), 1, false);
        lastWillClient.connect(connectOptions);
        lastWillClient.connect();
        waiter.await(1, TimeUnit.SECONDS);
        subClient.disconnect();
        subClient.close();
    }
}
