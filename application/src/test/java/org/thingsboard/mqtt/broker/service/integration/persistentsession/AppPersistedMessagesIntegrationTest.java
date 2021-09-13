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
package org.thingsboard.mqtt.broker.service.integration.persistentsession;

import lombok.extern.slf4j.Slf4j;
import net.jodah.concurrentunit.Waiter;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.client.MqttClientService;
import org.thingsboard.mqtt.broker.service.integration.AbstractPubSubIntegrationTest;

import java.util.concurrent.TimeUnit;

import static org.thingsboard.mqtt.broker.service.test.util.TestUtils.createApplicationClient;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = AppPersistedMessagesIntegrationTest.class, loader = SpringBootContextLoader.class)
@DaoSqlTest
@RunWith(SpringRunner.class)
public class AppPersistedMessagesIntegrationTest extends AbstractPubSubIntegrationTest {

    @Autowired
    private MqttClientService mqttClientService;

    private org.thingsboard.mqtt.broker.common.data.MqttClient applicationClient;
    private MqttClient persistedClient;

    @Before
    public void init() {
        applicationClient = mqttClientService.saveMqttClient(createApplicationClient());
    }

    @After
    public void clear() throws Exception {
        if (persistedClient.isConnected()) {
            persistedClient.disconnect();
        }
        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setCleanSession(true);
        persistedClient.connect(connectOptions);
        persistedClient.disconnect();
        persistedClient.close();
        mqttClientService.deleteMqttClient(applicationClient.getId());
    }

    @Test
    public void testSuccessPersistence_afterReconnect() throws Throwable {
        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setCleanSession(false);
        persistedClient = new MqttClient("tcp://localhost:" + mqttPort, applicationClient.getClientId());
        persistedClient.connect(connectOptions);
        Waiter waiter = new Waiter();
        persistedClient.subscribe("test", 1, (topic, msg) -> {
            waiter.resume();
        });
        persistedClient.disconnect();

        MqttClient publishingClient = new MqttClient("tcp://localhost:" + mqttPort, "publishing_client");
        publishingClient.connect();
        publishingClient.publish("test", "test_message".getBytes(), 1, false);
        publishingClient.disconnect();
        publishingClient.close();

        persistedClient.connect(connectOptions);

        waiter.await(200, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testSuccessPersistence_duringAndAfterDisconnect() throws Throwable {
        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setCleanSession(false);
        persistedClient = new MqttClient("tcp://localhost:" + mqttPort, applicationClient.getClientId());
        persistedClient.connect(connectOptions);
        Waiter waiter = new Waiter();
        persistedClient.subscribe("test", 1, (topic, msg) -> {
            waiter.resume();
        });

        MqttClient publishingClient = new MqttClient("tcp://localhost:" + mqttPort, "publishing_client");
        publishingClient.connect();
        publishingClient.publish("test", "test_message_1".getBytes(), 1, false);

        persistedClient.disconnect();

        publishingClient.publish("test", "test_message_2".getBytes(), 1, false);

        publishingClient.disconnect();
        publishingClient.close();

        persistedClient.connect(connectOptions);

        waiter.await(200, TimeUnit.MILLISECONDS, 2);
    }
}
