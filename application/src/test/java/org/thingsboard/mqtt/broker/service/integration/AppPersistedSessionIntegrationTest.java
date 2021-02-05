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
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.client.MqttClientService;
import org.thingsboard.mqtt.broker.service.mqtt.ClientSession;
import org.thingsboard.mqtt.broker.service.mqtt.TopicSubscription;
import org.thingsboard.mqtt.broker.service.mqtt.client.ClientSessionService;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.thingsboard.mqtt.broker.service.test.util.TestUtils.clearPersistedClient;
import static org.thingsboard.mqtt.broker.service.test.util.TestUtils.createApplicationClient;
import static org.thingsboard.mqtt.broker.service.test.util.TestUtils.getQoSLevels;
import static org.thingsboard.mqtt.broker.service.test.util.TestUtils.getTopicNames;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = AppPersistedSessionIntegrationTest.class, loader = SpringBootContextLoader.class)
@DaoSqlTest
@RunWith(SpringRunner.class)
public class AppPersistedSessionIntegrationTest extends AbstractPubSubIntegrationTest {

    private static final List<TopicSubscription> TEST_TOPIC_SUBSCRIPTIONS = Arrays.asList(new TopicSubscription("A", 0),
            new TopicSubscription("A/1", 0), new TopicSubscription("A/2", 1), new TopicSubscription("B", 1));

    @Autowired
    private MqttClientService mqttClientService;
    @Autowired
    private ClientSessionService clientSessionService;

    private org.thingsboard.mqtt.broker.common.data.MqttClient applicationClient;
    private MqttClient persistedClient;

    @Before
    public void init() throws Exception {
        applicationClient = mqttClientService.saveMqttClient(createApplicationClient());
        persistedClient = new MqttClient("tcp://localhost:" + mqttPort, applicationClient.getClientId());
    }

    @After
    public void clear() throws Exception {
        clearPersistedClient(persistedClient);
        mqttClientService.deleteMqttClient(applicationClient.getId());
    }

    @Test
    public void testSuccessPersistence_afterDisconnect() throws Exception {
        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setCleanSession(false);
        persistedClient.connect(connectOptions);

        persistedClient.subscribe(getTopicNames(TEST_TOPIC_SUBSCRIPTIONS), getQoSLevels(TEST_TOPIC_SUBSCRIPTIONS));
        persistedClient.disconnect();

        ClientSession persistedClientSession = clientSessionService.getClientSession(applicationClient.getClientId());
        Assert.assertNotNull(persistedClientSession);
        Assert.assertFalse(persistedClientSession.isConnected());
        Assert.assertTrue(persistedClientSession.isPersistent());
        Assert.assertEquals(new ClientInfo(applicationClient.getClientId(), ClientType.APPLICATION), persistedClientSession.getClientInfo());
        Set<TopicSubscription> persistedTopicSubscriptions = persistedClientSession.getTopicSubscriptions();
        Assert.assertTrue(persistedTopicSubscriptions.size() == TEST_TOPIC_SUBSCRIPTIONS.size()
                && persistedTopicSubscriptions.containsAll(TEST_TOPIC_SUBSCRIPTIONS));
    }

    @Test
    public void testSuccessPersistence_afterReconnect() throws Exception {
        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setCleanSession(false);
        persistedClient.connect(connectOptions);

        persistedClient.subscribe(getTopicNames(TEST_TOPIC_SUBSCRIPTIONS), getQoSLevels(TEST_TOPIC_SUBSCRIPTIONS));
        persistedClient.disconnect();
        persistedClient.connect(connectOptions);

        ClientSession persistedClientSession = clientSessionService.getClientSession(applicationClient.getClientId());
        Assert.assertNotNull(persistedClientSession);
        Assert.assertTrue(persistedClientSession.isConnected());
        Assert.assertTrue(persistedClientSession.isPersistent());
        Assert.assertEquals(new ClientInfo(applicationClient.getClientId(), ClientType.APPLICATION), persistedClientSession.getClientInfo());
        Set<TopicSubscription> persistedTopicSubscriptions = persistedClientSession.getTopicSubscriptions();
        Assert.assertTrue(persistedTopicSubscriptions.size() == TEST_TOPIC_SUBSCRIPTIONS.size()
                && persistedTopicSubscriptions.containsAll(TEST_TOPIC_SUBSCRIPTIONS));
    }

    @Test
    public void testSuccessPersistence_afterReconnectAndChange() throws Exception {
        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setCleanSession(false);
        persistedClient.connect(connectOptions);

        persistedClient.subscribe(getTopicNames(TEST_TOPIC_SUBSCRIPTIONS), getQoSLevels(TEST_TOPIC_SUBSCRIPTIONS));
        persistedClient.disconnect();
        persistedClient.connect(connectOptions);
        List<TopicSubscription> newTopicSubscriptions = Arrays.asList(new TopicSubscription("C/1", 1), new TopicSubscription("C/2", 0));
        persistedClient.subscribe(getTopicNames(newTopicSubscriptions), getQoSLevels(newTopicSubscriptions));

        ClientSession persistedClientSession = clientSessionService.getClientSession(applicationClient.getClientId());
        Set<TopicSubscription> persistedTopicSubscriptions = persistedClientSession.getTopicSubscriptions();
        Assert.assertTrue(persistedTopicSubscriptions.size() == TEST_TOPIC_SUBSCRIPTIONS.size() + newTopicSubscriptions.size()
                && persistedTopicSubscriptions.containsAll(TEST_TOPIC_SUBSCRIPTIONS) && persistedTopicSubscriptions.containsAll(newTopicSubscriptions));
    }

    @Test
    public void testSuccessPersistence_clearPersisted() throws Exception {
        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setCleanSession(false);
        persistedClient.connect(connectOptions);

        persistedClient.subscribe(getTopicNames(TEST_TOPIC_SUBSCRIPTIONS), getQoSLevels(TEST_TOPIC_SUBSCRIPTIONS));
        persistedClient.disconnect();
        connectOptions.setCleanSession(true);
        persistedClient.connect(connectOptions);

        ClientSession persistedClientSession = clientSessionService.getClientSession(applicationClient.getClientId());
        Assert.assertTrue(persistedClientSession.isConnected());
        Assert.assertFalse(persistedClientSession.isPersistent());
        Assert.assertTrue(persistedClientSession.getTopicSubscriptions().isEmpty());
    }


    @Test
    public void testSuccessPersistence_clearPersistedAndDisconnect() throws Exception {
        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setCleanSession(false);
        persistedClient.connect(connectOptions);

        persistedClient.subscribe(getTopicNames(TEST_TOPIC_SUBSCRIPTIONS), getQoSLevels(TEST_TOPIC_SUBSCRIPTIONS));
        persistedClient.disconnect();
        connectOptions.setCleanSession(true);
        persistedClient.connect(connectOptions);
        persistedClient.subscribe(getTopicNames(TEST_TOPIC_SUBSCRIPTIONS), getQoSLevels(TEST_TOPIC_SUBSCRIPTIONS));
        persistedClient.disconnect();

        ClientSession persistedClientSession = clientSessionService.getClientSession(applicationClient.getClientId());
        Assert.assertFalse(persistedClientSession.isConnected());
        Assert.assertFalse(persistedClientSession.isPersistent());
        Assert.assertTrue(persistedClientSession.getTopicSubscriptions().isEmpty());
    }
}
