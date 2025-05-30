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

import io.netty.handler.codec.mqtt.MqttVersion;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.junit.After;
import org.junit.Assert;
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
import org.thingsboard.mqtt.broker.actors.client.service.session.ClientSessionService;
import org.thingsboard.mqtt.broker.cache.CacheConstants;
import org.thingsboard.mqtt.broker.cache.CacheNameResolver;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;

import java.util.concurrent.TimeUnit;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = ClientMqttVersionIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@DaoSqlTest
@RunWith(SpringRunner.class)
public class ClientMqttVersionIntegrationTestCase extends AbstractPubSubIntegrationTest {

    private final String MQTT_5_VERSION_NAME = MqttVersion.MQTT_5.name();
    private final String MQTT_3_VERSION_NAME = MqttVersion.MQTT_3_1_1.name();
    private final String USER_NAME = "clientMqttVersionUn";

    @Autowired
    public ClientSessionService clientSessionService;
    @Autowired
    public CacheNameResolver cacheNameResolver;

    @Before
    public void init() {
    }

    @After
    public void clear() throws Exception {
    }

    @Test
    public void givenConnectingMqtt3Client_whenCheckClientMqttVersion_thenReturnExpectedResult() throws Throwable {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(USER_NAME);

        String clientId = "clientMqtt3Version";
        org.eclipse.paho.client.mqttv3.MqttClient client = new org.eclipse.paho.client.mqttv3.MqttClient(SERVER_URI + mqttPort, clientId);
        client.connect(options);

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .until(client::isConnected);

        String mqttVersion = cacheNameResolver.getCache(CacheConstants.CLIENT_MQTT_VERSION_CACHE).get(clientId, String.class);
        Assert.assertEquals(MQTT_3_VERSION_NAME, mqttVersion);

        client.disconnect();
        client.close();

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .until(() -> clientSessionService.getClientSessionInfo(client.getClientId()) == null);

        mqttVersion = cacheNameResolver.getCache(CacheConstants.CLIENT_MQTT_VERSION_CACHE).get(clientId, String.class);
        Assert.assertNull(mqttVersion);
    }

    @Test
    public void givenConnectingClient_whenCheckClientMqttVersion_thenReturnExpectedResult() throws Throwable {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setUserName(USER_NAME);

        String clientId = "clientMqttVersion";
        MqttClient client = new MqttClient(SERVER_URI + mqttPort, clientId);
        client.connect(options);

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .until(client::isConnected);

        String mqttVersion = cacheNameResolver.getCache(CacheConstants.CLIENT_MQTT_VERSION_CACHE).get(clientId, String.class);
        Assert.assertEquals(MQTT_5_VERSION_NAME, mqttVersion);

        client.disconnect();
        client.close();

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .until(() -> clientSessionService.getClientSessionInfo(client.getClientId()) == null);

        mqttVersion = cacheNameResolver.getCache(CacheConstants.CLIENT_MQTT_VERSION_CACHE).get(clientId, String.class);
        Assert.assertNull(mqttVersion);
    }

    @Test
    public void givenPersistentConnectingClient_whenCheckClientMqttVersion_thenReturnExpectedResult() throws Throwable {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setUserName(USER_NAME);
        options.setCleanStart(false);

        String clientId = "clientMqttVersionPersistent";
        MqttClient client = new MqttClient(SERVER_URI + mqttPort, clientId);
        client.connect(options);

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .until(client::isConnected);

        String mqttVersion = cacheNameResolver.getCache(CacheConstants.CLIENT_MQTT_VERSION_CACHE).get(clientId, String.class);
        Assert.assertEquals(MQTT_5_VERSION_NAME, mqttVersion);

        client.disconnect();

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .until(() -> clientSessionService.getClientSessionInfo(client.getClientId()).isDisconnected());

        mqttVersion = cacheNameResolver.getCache(CacheConstants.CLIENT_MQTT_VERSION_CACHE).get(clientId, String.class);
        Assert.assertEquals(MQTT_5_VERSION_NAME, mqttVersion);

        options.setCleanStart(true);
        client.connect(options);
        client.disconnect();
        client.close();

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .until(() -> clientSessionService.getClientSessionInfo(client.getClientId()) == null);

        mqttVersion = cacheNameResolver.getCache(CacheConstants.CLIENT_MQTT_VERSION_CACHE).get(clientId, String.class);
        Assert.assertNull(mqttVersion);
    }

}
