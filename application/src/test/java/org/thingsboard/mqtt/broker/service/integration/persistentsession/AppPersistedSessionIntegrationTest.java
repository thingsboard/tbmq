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
package org.thingsboard.mqtt.broker.service.integration.persistentsession;

import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
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
import org.thingsboard.mqtt.MqttClient;
import org.thingsboard.mqtt.MqttClientConfig;
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.service.mqtt.ClientSession;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCtxService;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscriptionCache;
import org.thingsboard.mqtt.broker.service.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.service.test.util.TestUtils;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.thingsboard.mqtt.broker.service.test.util.TestUtils.getQoSLevels;
import static org.thingsboard.mqtt.broker.service.test.util.TestUtils.getTopicNames;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = AppPersistedSessionIntegrationTest.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "security.mqtt.basic.enabled=true"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class AppPersistedSessionIntegrationTest extends AbstractPubSubIntegrationTest {
    private static final String TEST_CLIENT_ID = "test-application-client";
    private static final List<TopicSubscription> TEST_TOPIC_SUBSCRIPTIONS = Arrays.asList(new TopicSubscription("A", 0),
            new TopicSubscription("A/1", 0), new TopicSubscription("A/2", 1), new TopicSubscription("B", 1));

    @Autowired
    private MqttClientCredentialsService credentialsService;
    @Autowired
    private ClientSessionCache clientSessionCache;
    @Autowired
    private ClientSessionCtxService clientSessionCtxService;
    @Autowired
    private ClientSubscriptionCache clientSubscriptionCache;

    private MqttClientCredentials applicationCredentials;
    private MqttClient persistedClient;

    @Before
    public void init() throws Exception {
        applicationCredentials = credentialsService.saveCredentials(TestUtils.createApplicationClientCredentials(TEST_CLIENT_ID, null));
    }

    @After
    public void clear() throws Exception {
        if (persistedClient.isConnected()) {
            persistedClient.disconnect();
        }
        MqttClientConfig config = new MqttClientConfig();
        config.setClientId(TEST_CLIENT_ID);
        config.setCleanSession(true);
        persistedClient = MqttClient.create(config, (s, byteBuf) -> {
        });
        persistedClient.connect("localhost", mqttPort).get();
        persistedClient.disconnect();
        credentialsService.deleteCredentials(applicationCredentials.getId());
    }

    @Test
    public void testSuccessPersistence_afterDisconnect() throws Exception {
        MqttClientConfig config = new MqttClientConfig();
        config.setClientId(TEST_CLIENT_ID);
        config.setCleanSession(false);
        persistedClient = MqttClient.create(config, (s, byteBuf) -> {
        });
        persistedClient.connect("localhost", mqttPort).get();

        String[] topicNames = getTopicNames(TEST_TOPIC_SUBSCRIPTIONS);
        int[] qoSLevels = getQoSLevels(TEST_TOPIC_SUBSCRIPTIONS);
        for (int i = 0; i < topicNames.length; i++) {
            persistedClient.on(topicNames[i], (s, byteBuf) -> {
            }, MqttQoS.valueOf(qoSLevels[i])).get();
        }
        persistedClient.disconnect();
        awaitUntilDisconnected();

        ClientSessionCtx clientSessionCtx = clientSessionCtxService.getClientSessionCtx(TEST_CLIENT_ID);
        Assert.assertNull(clientSessionCtx);
        ClientSession persistedClientSession = clientSessionCache.getClientSession(TEST_CLIENT_ID);
        Assert.assertNotNull(persistedClientSession);
        SessionInfo sessionInfo = persistedClientSession.getSessionInfo();
        Assert.assertFalse(persistedClientSession.isConnected());
        Assert.assertTrue(sessionInfo.isPersistent());
        Assert.assertEquals(new ClientInfo(TEST_CLIENT_ID, ClientType.APPLICATION), sessionInfo.getClientInfo());
        Set<TopicSubscription> persistedTopicSubscriptions = clientSubscriptionCache.getClientSubscriptions(TEST_CLIENT_ID);
        Assert.assertTrue(persistedTopicSubscriptions.size() == TEST_TOPIC_SUBSCRIPTIONS.size()
                && persistedTopicSubscriptions.containsAll(TEST_TOPIC_SUBSCRIPTIONS));
    }

    @Test
    public void testSuccessConnect() throws Exception {
        MqttClientConfig config = new MqttClientConfig();
        config.setClientId(TEST_CLIENT_ID);
        config.setCleanSession(false);
        persistedClient = MqttClient.create(config, (s, byteBuf) -> {
        });
        persistedClient.connect("localhost", mqttPort).get();
        ClientSessionCtx clientSessionCtx = clientSessionCtxService.getClientSessionCtx(TEST_CLIENT_ID);
        Assert.assertNotNull(clientSessionCtx);
        persistedClient.disconnect();
        awaitUntilDisconnected();
    }

    @Test
    public void testSuccessPersistence_afterReconnect() throws Exception {
        MqttClientConfig config = new MqttClientConfig();
        config.setClientId(TEST_CLIENT_ID);
        config.setCleanSession(false);
        persistedClient = MqttClient.create(config, (s, byteBuf) -> {
        });
        persistedClient.connect("localhost", mqttPort).get();

        String[] topicNames = getTopicNames(TEST_TOPIC_SUBSCRIPTIONS);
        int[] qoSLevels = getQoSLevels(TEST_TOPIC_SUBSCRIPTIONS);
        for (int i = 0; i < topicNames.length; i++) {
            persistedClient.on(topicNames[i], (s, byteBuf) -> {
            }, MqttQoS.valueOf(qoSLevels[i])).get();
        }
        persistedClient.disconnect();

        persistedClient = MqttClient.create(config, (s, byteBuf) -> {
        });
        persistedClient.connect("localhost", mqttPort).get();

        ClientSessionCtx clientSessionCtx = clientSessionCtxService.getClientSessionCtx(TEST_CLIENT_ID);
        Assert.assertNotNull(clientSessionCtx);
        ClientSession persistedClientSession = clientSessionCache.getClientSession(TEST_CLIENT_ID);
        Assert.assertNotNull(persistedClientSession);
        SessionInfo sessionInfo = persistedClientSession.getSessionInfo();
        Assert.assertTrue(persistedClientSession.isConnected());
        Assert.assertTrue(sessionInfo.isPersistent());
        Assert.assertEquals(new ClientInfo(TEST_CLIENT_ID, ClientType.APPLICATION), sessionInfo.getClientInfo());
        Set<TopicSubscription> persistedTopicSubscriptions = clientSubscriptionCache.getClientSubscriptions(TEST_CLIENT_ID);
        Assert.assertTrue(persistedTopicSubscriptions.size() == TEST_TOPIC_SUBSCRIPTIONS.size()
                && persistedTopicSubscriptions.containsAll(TEST_TOPIC_SUBSCRIPTIONS));
    }

    @Test
    public void testSuccessPersistence_afterReconnectAndChange() throws Exception {
        MqttClientConfig config = new MqttClientConfig();
        config.setClientId(TEST_CLIENT_ID);
        config.setCleanSession(false);
        persistedClient = MqttClient.create(config, (s, byteBuf) -> {
        });
        persistedClient.connect("localhost", mqttPort).get();

        String[] topicNames = getTopicNames(TEST_TOPIC_SUBSCRIPTIONS);
        int[] qoSLevels = getQoSLevels(TEST_TOPIC_SUBSCRIPTIONS);
        for (int i = 0; i < topicNames.length; i++) {
            persistedClient.on(topicNames[i], (s, byteBuf) -> {
            }, MqttQoS.valueOf(qoSLevels[i])).get();
        }
        persistedClient.disconnect();

        persistedClient = MqttClient.create(config, (s, byteBuf) -> {
        });
        persistedClient.connect("localhost", mqttPort).get();

        List<TopicSubscription> newTopicSubscriptions = Arrays.asList(new TopicSubscription("C/1", 1), new TopicSubscription("C/2", 0));
        String[] newTopicNames = getTopicNames(newTopicSubscriptions);
        int[] newQoSLevels = getQoSLevels(newTopicSubscriptions);
        for (int i = 0; i < newTopicNames.length; i++) {
            persistedClient.on(newTopicNames[i], (s, byteBuf) -> {
            }, MqttQoS.valueOf(newQoSLevels[i])).get();
        }

        Set<TopicSubscription> persistedTopicSubscriptions = clientSubscriptionCache.getClientSubscriptions(TEST_CLIENT_ID);
        Assert.assertTrue(persistedTopicSubscriptions.size() == TEST_TOPIC_SUBSCRIPTIONS.size() + newTopicSubscriptions.size()
                && persistedTopicSubscriptions.containsAll(TEST_TOPIC_SUBSCRIPTIONS) && persistedTopicSubscriptions.containsAll(newTopicSubscriptions));
    }

    @Test
    public void testSuccessPersistence_clearPersisted() throws Exception {
        MqttClientConfig config = new MqttClientConfig();
        config.setClientId(TEST_CLIENT_ID);
        config.setCleanSession(false);
        persistedClient = MqttClient.create(config, (s, byteBuf) -> {
        });
        persistedClient.connect("localhost", mqttPort).get();

        String[] topicNames = getTopicNames(TEST_TOPIC_SUBSCRIPTIONS);
        int[] qoSLevels = getQoSLevels(TEST_TOPIC_SUBSCRIPTIONS);
        for (int i = 0; i < topicNames.length; i++) {
            persistedClient.on(topicNames[i], (s, byteBuf) -> {
            }, MqttQoS.valueOf(qoSLevels[i])).get();
        }
        persistedClient.disconnect();

        config.setCleanSession(true);
        persistedClient = MqttClient.create(config, (s, byteBuf) -> {
        });
        persistedClient.connect("localhost", mqttPort).get();

        ClientSession persistedClientSession = clientSessionCache.getClientSession(TEST_CLIENT_ID);
        ClientSessionCtx clientSessionCtx = clientSessionCtxService.getClientSessionCtx(TEST_CLIENT_ID);
        Assert.assertNotNull(clientSessionCtx);
        Assert.assertTrue(persistedClientSession.isConnected());
        Assert.assertFalse(persistedClientSession.getSessionInfo().isPersistent());
        Set<TopicSubscription> persistedTopicSubscriptions = clientSubscriptionCache.getClientSubscriptions(TEST_CLIENT_ID);
        Assert.assertTrue(persistedTopicSubscriptions.isEmpty());
    }

    @Test
    public void testSuccessPersistence_clearPersistedAndDisconnect() throws Exception {
        MqttClientConfig config = new MqttClientConfig();
        config.setClientId(TEST_CLIENT_ID);
        config.setCleanSession(false);
        persistedClient = MqttClient.create(config, (s, byteBuf) -> {
        });
        persistedClient.connect("localhost", mqttPort).get();

        String[] topicNames = getTopicNames(TEST_TOPIC_SUBSCRIPTIONS);
        int[] qoSLevels = getQoSLevels(TEST_TOPIC_SUBSCRIPTIONS);
        for (int i = 0; i < topicNames.length; i++) {
            persistedClient.on(topicNames[i], (s, byteBuf) -> {
            }, MqttQoS.valueOf(qoSLevels[i])).get();
        }
        persistedClient.disconnect();

        config.setCleanSession(true);
        persistedClient = MqttClient.create(config, (s, byteBuf) -> {
        });
        persistedClient.connect("localhost", mqttPort).get();
        for (int i = 0; i < topicNames.length; i++) {
            persistedClient.on(topicNames[i], (s, byteBuf) -> {
            }, MqttQoS.valueOf(qoSLevels[i])).get();
        }
        persistedClient.disconnect();
        awaitUntilDisconnected();

        ClientSession persistedClientSession = clientSessionCache.getClientSession(TEST_CLIENT_ID);
        Assert.assertNull(persistedClientSession);
        ClientSessionCtx clientSessionCtx = clientSessionCtxService.getClientSessionCtx(TEST_CLIENT_ID);
        Assert.assertNull(clientSessionCtx);
        Set<TopicSubscription> persistedTopicSubscriptions = clientSubscriptionCache.getClientSubscriptions(TEST_CLIENT_ID);
        Assert.assertTrue(persistedTopicSubscriptions.isEmpty());
    }

    private void awaitUntilDisconnected() {
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> !persistedClient.isConnected());
    }
}
