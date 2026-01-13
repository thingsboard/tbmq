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
package org.thingsboard.mqtt.broker.service.testing.integration.persistentsession;

import com.google.common.util.concurrent.Futures;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttVersion;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
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
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.MqttClient;
import org.thingsboard.mqtt.MqttClientConfig;
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientSession;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.data.subscription.ClientTopicSubscription;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCtxService;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscriptionCache;
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
@ContextConfiguration(classes = AppPersistedSessionIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@DaoSqlTest
@RunWith(SpringRunner.class)
public class AppPersistedSessionIntegrationTestCase extends AbstractPubSubIntegrationTest {

    private static final List<TopicSubscription> TEST_TOPIC_SUBSCRIPTIONS = Arrays.asList(new ClientTopicSubscription("A", 0),
            new ClientTopicSubscription("A/1", 0), new ClientTopicSubscription("A/2", 1), new ClientTopicSubscription("B", 1));

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
    private String TEST_CLIENT_ID;

    @Before
    public void beforeTest() throws Exception {
        TEST_CLIENT_ID = RandomStringUtils.randomAlphabetic(15);
        applicationCredentials = credentialsService.saveCredentials(TestUtils.createApplicationClientCredentials(TEST_CLIENT_ID, null));
        enableBasicProvider();
    }

    @After
    public void clear() throws Exception {
        if (persistedClient.isConnected()) {
            persistedClient.disconnect();
            awaitUntilDisconnected();
        }
        MqttClientConfig config = new MqttClientConfig();
        config.setProtocolVersion(MqttVersion.MQTT_3_1_1);
        config.setClientId(TEST_CLIENT_ID);
        config.setCleanSession(true);
        persistedClient = createMqttClient(config);
        persistedClient.connect("localhost", mqttPort).get();
        persistedClient.disconnect();
        credentialsService.deleteCredentials(applicationCredentials.getId());
    }

    @Test
    public void testSuccessPersistence_afterDisconnect() throws Exception {
        MqttClientConfig config = new MqttClientConfig();
        config.setProtocolVersion(MqttVersion.MQTT_3_1_1);
        config.setClientId(TEST_CLIENT_ID);
        config.setCleanSession(false);
        persistedClient = createMqttClient(config);
        persistedClient.connect("localhost", mqttPort).get();

        String[] topicNames = getTopicNames(TEST_TOPIC_SUBSCRIPTIONS);
        int[] qoSLevels = getQoSLevels(TEST_TOPIC_SUBSCRIPTIONS);
        for (int i = 0; i < topicNames.length; i++) {
            persistedClient.on(topicNames[i], (s, byteBuf) -> Futures.immediateVoidFuture(), MqttQoS.valueOf(qoSLevels[i])).get();
        }
        persistedClient.disconnect();
        awaitUntilDisconnected();
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> clientSessionCtxService.getClientSessionCtx(TEST_CLIENT_ID) == null);
        ClientSessionCtx clientSessionCtx = clientSessionCtxService.getClientSessionCtx(TEST_CLIENT_ID);
        Assert.assertNull(clientSessionCtx);
        ClientSession persistedClientSession = clientSessionCache.getClientSession(TEST_CLIENT_ID);
        Assert.assertNotNull(persistedClientSession);
        SessionInfo sessionInfo = persistedClientSession.getSessionInfo();
        Assert.assertFalse(persistedClientSession.isConnected());
        Assert.assertTrue(sessionInfo.isNotCleanSession());
        Assert.assertEquals(new ClientInfo(TEST_CLIENT_ID, ClientType.APPLICATION), sessionInfo.getClientInfo());
        Set<TopicSubscription> persistedTopicSubscriptions = clientSubscriptionCache.getClientSubscriptions(TEST_CLIENT_ID);
        Assert.assertTrue(persistedTopicSubscriptions.size() == TEST_TOPIC_SUBSCRIPTIONS.size()
                && persistedTopicSubscriptions.containsAll(TEST_TOPIC_SUBSCRIPTIONS));
    }

    @Test
    public void testSuccessConnect() throws Exception {
        MqttClientConfig config = new MqttClientConfig();
        config.setProtocolVersion(MqttVersion.MQTT_3_1_1);
        config.setClientId(TEST_CLIENT_ID);
        config.setCleanSession(false);
        persistedClient = createMqttClient(config);
        persistedClient.connect("localhost", mqttPort).get(30, TimeUnit.SECONDS);
        Awaitility
                .await()
                .atMost(10, TimeUnit.SECONDS)
                .until(() -> clientSessionCtxService.getClientSessionCtx(TEST_CLIENT_ID) != null);
        persistedClient.disconnect();
        awaitUntilDisconnected();
    }

    @Test
    public void testSuccessPersistence_afterReconnect() throws Exception {
        MqttClientConfig config = new MqttClientConfig();
        config.setProtocolVersion(MqttVersion.MQTT_3_1_1);
        config.setClientId(TEST_CLIENT_ID);
        config.setCleanSession(false);
        persistedClient = createMqttClient(config);
        persistedClient.connect("localhost", mqttPort).get();

        String[] topicNames = getTopicNames(TEST_TOPIC_SUBSCRIPTIONS);
        int[] qoSLevels = getQoSLevels(TEST_TOPIC_SUBSCRIPTIONS);
        for (int i = 0; i < topicNames.length; i++) {
            persistedClient.on(topicNames[i], (s, byteBuf) -> Futures.immediateVoidFuture(), MqttQoS.valueOf(qoSLevels[i])).get();
        }
        persistedClient.disconnect();

        persistedClient = createMqttClient(config);
        persistedClient.connect("localhost", mqttPort).get();

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> {
            ClientSessionCtx clientSessionCtx = clientSessionCtxService.getClientSessionCtx(TEST_CLIENT_ID);
            return clientSessionCtx != null;
        });

        ClientSession persistedClientSession = clientSessionCache.getClientSession(TEST_CLIENT_ID);
        Assert.assertNotNull(persistedClientSession);
        SessionInfo sessionInfo = persistedClientSession.getSessionInfo();
        Assert.assertTrue(persistedClientSession.isConnected());
        Assert.assertTrue(sessionInfo.isNotCleanSession());
        Assert.assertEquals(new ClientInfo(TEST_CLIENT_ID, ClientType.APPLICATION), sessionInfo.getClientInfo());
        Set<TopicSubscription> persistedTopicSubscriptions = clientSubscriptionCache.getClientSubscriptions(TEST_CLIENT_ID);
        Assert.assertTrue(persistedTopicSubscriptions.size() == TEST_TOPIC_SUBSCRIPTIONS.size()
                && persistedTopicSubscriptions.containsAll(TEST_TOPIC_SUBSCRIPTIONS));
    }

    @Test
    public void testSuccessPersistence_afterReconnectAndChange() throws Exception {
        MqttClientConfig config = new MqttClientConfig();
        config.setProtocolVersion(MqttVersion.MQTT_3_1_1);
        config.setClientId(TEST_CLIENT_ID);
        config.setCleanSession(false);
        persistedClient = createMqttClient(config);
        persistedClient.connect("localhost", mqttPort).get();

        String[] topicNames = getTopicNames(TEST_TOPIC_SUBSCRIPTIONS);
        int[] qoSLevels = getQoSLevels(TEST_TOPIC_SUBSCRIPTIONS);
        for (int i = 0; i < topicNames.length; i++) {
            persistedClient.on(topicNames[i], (s, byteBuf) -> Futures.immediateVoidFuture(), MqttQoS.valueOf(qoSLevels[i])).get();
        }
        persistedClient.disconnect();

        persistedClient = createMqttClient(config);
        persistedClient.connect("localhost", mqttPort).get();

        List<TopicSubscription> newTopicSubscriptions = Arrays.asList(new ClientTopicSubscription("C/1", 1), new ClientTopicSubscription("C/2", 0));
        String[] newTopicNames = getTopicNames(newTopicSubscriptions);
        int[] newQoSLevels = getQoSLevels(newTopicSubscriptions);
        for (int i = 0; i < newTopicNames.length; i++) {
            persistedClient.on(newTopicNames[i], (s, byteBuf) -> Futures.immediateVoidFuture(), MqttQoS.valueOf(newQoSLevels[i])).get();
        }

        Set<TopicSubscription> persistedTopicSubscriptions = clientSubscriptionCache.getClientSubscriptions(TEST_CLIENT_ID);
        Assert.assertTrue(persistedTopicSubscriptions.size() == TEST_TOPIC_SUBSCRIPTIONS.size() + newTopicSubscriptions.size()
                && persistedTopicSubscriptions.containsAll(TEST_TOPIC_SUBSCRIPTIONS) && persistedTopicSubscriptions.containsAll(newTopicSubscriptions));
    }

    @Test
    public void testSuccessPersistence_clearPersisted() throws Exception {
        MqttClientConfig config = new MqttClientConfig();
        config.setProtocolVersion(MqttVersion.MQTT_3_1_1);
        config.setClientId(TEST_CLIENT_ID);
        config.setCleanSession(false);
        persistedClient = createMqttClient(config);
        persistedClient.connect("localhost", mqttPort).get();

        String[] topicNames = getTopicNames(TEST_TOPIC_SUBSCRIPTIONS);
        int[] qoSLevels = getQoSLevels(TEST_TOPIC_SUBSCRIPTIONS);
        for (int i = 0; i < topicNames.length; i++) {
            persistedClient.on(topicNames[i], (s, byteBuf) -> Futures.immediateVoidFuture(), MqttQoS.valueOf(qoSLevels[i])).get();
        }
        persistedClient.disconnect();

        config.setCleanSession(true);
        persistedClient = createMqttClient(config);
        persistedClient.connect("localhost", mqttPort).get();

        ClientSession persistedClientSession = clientSessionCache.getClientSession(TEST_CLIENT_ID);

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> {
            ClientSessionCtx clientSessionCtx = clientSessionCtxService.getClientSessionCtx(TEST_CLIENT_ID);
            return clientSessionCtx != null;
        });

        Assert.assertTrue(persistedClientSession.isConnected());
        Assert.assertTrue(persistedClientSession.getSessionInfo().isCleanSession());
        Set<TopicSubscription> persistedTopicSubscriptions = clientSubscriptionCache.getClientSubscriptions(TEST_CLIENT_ID);
        Assert.assertTrue(persistedTopicSubscriptions.isEmpty());
    }

    @Test
    public void testSuccessPersistence_clearPersistedAndDisconnect() throws Exception {
        MqttClientConfig config = new MqttClientConfig();
        config.setProtocolVersion(MqttVersion.MQTT_3_1_1);
        config.setClientId(TEST_CLIENT_ID);
        config.setCleanSession(false);
        persistedClient = createMqttClient(config);
        persistedClient.connect("localhost", mqttPort).get();

        String[] topicNames = getTopicNames(TEST_TOPIC_SUBSCRIPTIONS);
        int[] qoSLevels = getQoSLevels(TEST_TOPIC_SUBSCRIPTIONS);
        for (int i = 0; i < topicNames.length; i++) {
            persistedClient.on(topicNames[i], (s, byteBuf) -> Futures.immediateVoidFuture(), MqttQoS.valueOf(qoSLevels[i])).get();
        }
        persistedClient.disconnect();

        config.setCleanSession(true);
        persistedClient = createMqttClient(config);
        persistedClient.connect("localhost", mqttPort).get();
        for (int i = 0; i < topicNames.length; i++) {
            persistedClient.on(topicNames[i], (s, byteBuf) -> Futures.immediateVoidFuture(), MqttQoS.valueOf(qoSLevels[i])).get();
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

    private MqttClient createMqttClient(MqttClientConfig config) {
        return MqttClient.create(config, (s, byteBuf) -> Futures.immediateVoidFuture(), externalExecutorService);
    }

    private void awaitUntilDisconnected() {
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> {
                    ClientSession session = clientSessionCache.getClientSession(TEST_CLIENT_ID);
                    return session == null || !session.isConnected();
                });
    }
}
