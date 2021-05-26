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

import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.MqttClient;
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
import org.thingsboard.mqtt.MqttClientConfig;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.client.MqttClientService;
import org.thingsboard.mqtt.broker.service.integration.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.service.mqtt.ClientSession;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscriptionService;
import org.thingsboard.mqtt.broker.service.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCtxService;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionService;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

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
    @Autowired
    private ClientSessionCtxService clientSessionCtxService;
    @Autowired
    private ClientSubscriptionService clientSubscriptionService;

    private org.thingsboard.mqtt.broker.common.data.MqttClient applicationClient;
    private MqttClient persistedClient;

    @Before
    public void init() throws Exception {
        applicationClient = mqttClientService.saveMqttClient(createApplicationClient());
    }

    @After
    public void clear() throws Exception {
        if (persistedClient.isConnected()) {
            persistedClient.disconnect();
        }
        MqttClientConfig config = new MqttClientConfig();
        config.setClientId(applicationClient.getClientId());
        config.setCleanSession(true);
        persistedClient = MqttClient.create(config, (s, byteBuf) -> {});
        persistedClient.connect("localhost", mqttPort).get();
        persistedClient.disconnect();
        mqttClientService.deleteMqttClient(applicationClient.getId());
    }

    @Test
    public void testSuccessPersistence_afterDisconnect() throws Exception {
        MqttClientConfig config = new MqttClientConfig();
        config.setClientId(applicationClient.getClientId());
        config.setCleanSession(false);
        persistedClient = MqttClient.create(config, (s, byteBuf) -> {});
        persistedClient.connect("localhost", mqttPort).get();

        String[] topicNames = getTopicNames(TEST_TOPIC_SUBSCRIPTIONS);
        int[] qoSLevels = getQoSLevels(TEST_TOPIC_SUBSCRIPTIONS);
        for (int i = 0; i < topicNames.length; i++) {
            persistedClient.on(topicNames[i], (s, byteBuf) -> {}, MqttQoS.valueOf(qoSLevels[i])).get();
        }
        persistedClient.disconnect();
        // need to wait till client is actually stopped
        Thread.sleep(200);

        ClientSessionCtx clientSessionCtx = clientSessionCtxService.getClientSessionCtx(applicationClient.getClientId());
        Assert.assertNull(clientSessionCtx);
        ClientSession persistedClientSession = clientSessionService.getClientSession(applicationClient.getClientId());
        Assert.assertNotNull(persistedClientSession);
        SessionInfo sessionInfo = persistedClientSession.getSessionInfo();
        Assert.assertFalse(persistedClientSession.isConnected());
        Assert.assertTrue(sessionInfo.isPersistent());
        Assert.assertEquals(new ClientInfo(applicationClient.getClientId(), ClientType.APPLICATION), sessionInfo.getClientInfo());
        Set<TopicSubscription> persistedTopicSubscriptions = clientSubscriptionService.getClientSubscriptions(applicationClient.getClientId());
        Assert.assertTrue(persistedTopicSubscriptions.size() == TEST_TOPIC_SUBSCRIPTIONS.size()
                && persistedTopicSubscriptions.containsAll(TEST_TOPIC_SUBSCRIPTIONS));
    }

    @Test
    public void testSuccessConnect() throws Exception {
        MqttClientConfig config = new MqttClientConfig();
        config.setClientId(applicationClient.getClientId());
        config.setCleanSession(false);
        persistedClient = MqttClient.create(config, (s, byteBuf) -> {});
        persistedClient.connect("localhost", mqttPort).get();
        ClientSessionCtx clientSessionCtx = clientSessionCtxService.getClientSessionCtx(applicationClient.getClientId());
        Assert.assertNotNull(clientSessionCtx);
        persistedClient.disconnect();
        // need to wait till client is actually stopped
        Thread.sleep(200);
    }

    @Test
    public void testSuccessPersistence_afterReconnect() throws Exception {
        MqttClientConfig config = new MqttClientConfig();
        config.setClientId(applicationClient.getClientId());
        config.setCleanSession(false);
        persistedClient = MqttClient.create(config, (s, byteBuf) -> {});
        persistedClient.connect("localhost", mqttPort).get();

        String[] topicNames = getTopicNames(TEST_TOPIC_SUBSCRIPTIONS);
        int[] qoSLevels = getQoSLevels(TEST_TOPIC_SUBSCRIPTIONS);
        for (int i = 0; i < topicNames.length; i++) {
            persistedClient.on(topicNames[i], (s, byteBuf) -> {}, MqttQoS.valueOf(qoSLevels[i])).get();
        }
        persistedClient.disconnect();

        persistedClient = MqttClient.create(config, (s, byteBuf) -> {});
        persistedClient.connect("localhost", mqttPort).get();

        ClientSessionCtx clientSessionCtx = clientSessionCtxService.getClientSessionCtx(applicationClient.getClientId());
        Assert.assertNotNull(clientSessionCtx);
        ClientSession persistedClientSession = clientSessionService.getClientSession(applicationClient.getClientId());
        Assert.assertNotNull(persistedClientSession);
        SessionInfo sessionInfo = persistedClientSession.getSessionInfo();
        Assert.assertTrue(persistedClientSession.isConnected());
        Assert.assertTrue(sessionInfo.isPersistent());
        Assert.assertEquals(new ClientInfo(applicationClient.getClientId(), ClientType.APPLICATION), sessionInfo.getClientInfo());
        Set<TopicSubscription> persistedTopicSubscriptions = clientSubscriptionService.getClientSubscriptions(applicationClient.getClientId());
        Assert.assertTrue(persistedTopicSubscriptions.size() == TEST_TOPIC_SUBSCRIPTIONS.size()
                && persistedTopicSubscriptions.containsAll(TEST_TOPIC_SUBSCRIPTIONS));
    }

    @Test
    public void testSuccessPersistence_afterReconnectAndChange() throws Exception {
        MqttClientConfig config = new MqttClientConfig();
        config.setClientId(applicationClient.getClientId());
        config.setCleanSession(false);
        persistedClient = MqttClient.create(config, (s, byteBuf) -> {});
        persistedClient.connect("localhost", mqttPort).get();

        String[] topicNames = getTopicNames(TEST_TOPIC_SUBSCRIPTIONS);
        int[] qoSLevels = getQoSLevels(TEST_TOPIC_SUBSCRIPTIONS);
        for (int i = 0; i < topicNames.length; i++) {
            persistedClient.on(topicNames[i], (s, byteBuf) -> {}, MqttQoS.valueOf(qoSLevels[i])).get();
        }
        persistedClient.disconnect();

        persistedClient = MqttClient.create(config, (s, byteBuf) -> {});
        persistedClient.connect("localhost", mqttPort).get();

        List<TopicSubscription> newTopicSubscriptions = Arrays.asList(new TopicSubscription("C/1", 1), new TopicSubscription("C/2", 0));
        String[] newTopicNames = getTopicNames(newTopicSubscriptions);
        int[] newQoSLevels = getQoSLevels(newTopicSubscriptions);
        for (int i = 0; i < newTopicNames.length; i++) {
            persistedClient.on(newTopicNames[i], (s, byteBuf) -> {}, MqttQoS.valueOf(newQoSLevels[i])).get();
        }

        Set<TopicSubscription> persistedTopicSubscriptions = clientSubscriptionService.getClientSubscriptions(applicationClient.getClientId());
        Assert.assertTrue(persistedTopicSubscriptions.size() == TEST_TOPIC_SUBSCRIPTIONS.size() + newTopicSubscriptions.size()
                && persistedTopicSubscriptions.containsAll(TEST_TOPIC_SUBSCRIPTIONS) && persistedTopicSubscriptions.containsAll(newTopicSubscriptions));
    }

    @Test
    public void testSuccessPersistence_clearPersisted() throws Exception {
        MqttClientConfig config = new MqttClientConfig();
        config.setClientId(applicationClient.getClientId());
        config.setCleanSession(false);
        persistedClient = MqttClient.create(config, (s, byteBuf) -> {});
        persistedClient.connect("localhost", mqttPort).get();

        String[] topicNames = getTopicNames(TEST_TOPIC_SUBSCRIPTIONS);
        int[] qoSLevels = getQoSLevels(TEST_TOPIC_SUBSCRIPTIONS);
        for (int i = 0; i < topicNames.length; i++) {
            persistedClient.on(topicNames[i], (s, byteBuf) -> {}, MqttQoS.valueOf(qoSLevels[i])).get();
        }
        persistedClient.disconnect();

        config.setCleanSession(true);
        persistedClient = MqttClient.create(config, (s, byteBuf) -> {});
        persistedClient.connect("localhost", mqttPort).get();

        ClientSession persistedClientSession = clientSessionService.getClientSession(applicationClient.getClientId());
        ClientSessionCtx clientSessionCtx = clientSessionCtxService.getClientSessionCtx(applicationClient.getClientId());
        Assert.assertNotNull(clientSessionCtx);
        Assert.assertTrue(persistedClientSession.isConnected());
        Assert.assertFalse(persistedClientSession.getSessionInfo().isPersistent());
        Set<TopicSubscription> persistedTopicSubscriptions = clientSubscriptionService.getClientSubscriptions(applicationClient.getClientId());
        Assert.assertTrue(persistedTopicSubscriptions.isEmpty());
    }


    @Test
    public void testSuccessPersistence_clearPersistedAndDisconnect() throws Exception {
        MqttClientConfig config = new MqttClientConfig();
        config.setClientId(applicationClient.getClientId());
        config.setCleanSession(false);
        persistedClient = MqttClient.create(config, (s, byteBuf) -> {});
        persistedClient.connect("localhost", mqttPort).get();

        String[] topicNames = getTopicNames(TEST_TOPIC_SUBSCRIPTIONS);
        int[] qoSLevels = getQoSLevels(TEST_TOPIC_SUBSCRIPTIONS);
        for (int i = 0; i < topicNames.length; i++) {
            persistedClient.on(topicNames[i], (s, byteBuf) -> {}, MqttQoS.valueOf(qoSLevels[i])).get();
        }
        persistedClient.disconnect();

        config.setCleanSession(true);
        persistedClient = MqttClient.create(config, (s, byteBuf) -> {});
        persistedClient.connect("localhost", mqttPort).get();
        for (int i = 0; i < topicNames.length; i++) {
            persistedClient.on(topicNames[i], (s, byteBuf) -> {}, MqttQoS.valueOf(qoSLevels[i])).get();
        }
        persistedClient.disconnect();
        // need to wait till client is actually stopped
        Thread.sleep(200);

        ClientSession persistedClientSession = clientSessionService.getClientSession(applicationClient.getClientId());
        Assert.assertNull(persistedClientSession);
        ClientSessionCtx clientSessionCtx = clientSessionCtxService.getClientSessionCtx(applicationClient.getClientId());
        Assert.assertNull(clientSessionCtx);
        Set<TopicSubscription> persistedTopicSubscriptions = clientSubscriptionService.getClientSubscriptions(applicationClient.getClientId());
        Assert.assertTrue(persistedTopicSubscriptions.isEmpty());
    }
}
