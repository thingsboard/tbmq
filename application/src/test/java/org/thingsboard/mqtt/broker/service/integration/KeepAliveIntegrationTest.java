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
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttPingSender;
import org.eclipse.paho.client.mqttv3.internal.ClientComms;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;

import java.util.concurrent.TimeUnit;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = KeepAliveIntegrationTest.class, loader = SpringBootContextLoader.class)
@DaoSqlTest
@RunWith(SpringRunner.class)
public class KeepAliveIntegrationTest extends AbstractPubSubIntegrationTest {

    @Test
    public void testKeepAlive() throws Throwable {
        MqttAsyncClient testAsyncClient = new MqttAsyncClient("tcp://localhost:" + mqttPort, "test_client",
                null, DisabledMqttPingSender.DISABLED_MQTT_PING_SENDER);
        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setKeepAliveInterval(1);
        Waiter waiter = new Waiter();
        testAsyncClient.connect(connectOptions, null, new IMqttActionListener() {
            @Override
            public void onSuccess(IMqttToken asyncActionToken) {
                try {
                    waiter.assertTrue(testAsyncClient.isConnected());
                    Thread.sleep(1400);
                    waiter.assertTrue(testAsyncClient.isConnected());
                    Thread.sleep(300);
                    waiter.assertFalse(testAsyncClient.isConnected());
                } catch (Exception e){
                    waiter.assertNull(e);
                } finally {
                    waiter.resume();
                }
            }

            @Override
            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                waiter.assertNull(exception);
                waiter.resume();
            }
        });

        waiter.await(3, TimeUnit.SECONDS);
    }
}
