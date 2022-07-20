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
package org.thingsboard.mqtt.broker.service.integration.restart;

import lombok.extern.slf4j.Slf4j;
import net.jodah.concurrentunit.Waiter;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.service.mqtt.ClientSession;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscriptionCache;
import org.thingsboard.mqtt.broker.service.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.service.test.util.RestartingSpringJUnit4ClassRunner;
import org.thingsboard.mqtt.broker.service.test.util.SpringRestarter;
import org.thingsboard.mqtt.broker.service.test.util.TestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.thingsboard.mqtt.broker.service.test.util.TestUtils.clearPersistedClient;
import static org.thingsboard.mqtt.broker.service.test.util.TestUtils.getQoSLevels;
import static org.thingsboard.mqtt.broker.service.test.util.TestUtils.getTopicNames;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = RestartIntegrationTest.class, loader = SpringBootContextLoader.class)
@DaoSqlTest
@RunWith(RestartingSpringJUnit4ClassRunner.class)
// Fails if separated on different classes
public class RestartIntegrationTest extends AbstractPubSubIntegrationTest {
    private static final String TEST_CLIENT_ID = "test-application-client";
    private static final int NUMBER_OF_MSGS_IN_SEQUENCE = 50;
    private static final String TEST_TOPIC = "test";
    private static final List<TopicSubscription> TEST_TOPIC_SUBSCRIPTIONS = Arrays.asList(new TopicSubscription("A", 0),
            new TopicSubscription("A/1", 0), new TopicSubscription("A/2", 1), new TopicSubscription("B", 1));

    @Autowired
    private MqttClientCredentialsService credentialsService;
    @Autowired
    private ClientSessionCache clientSessionCache;
    @Autowired
    private ClientSubscriptionCache clientSubscriptionCache;

    private MqttClientCredentials applicationCredentials;
    private MqttClient persistedClient;


    @Before
    public void init() throws Exception {
        applicationCredentials = credentialsService.saveCredentials(TestUtils.createDeviceClientCredentials(TEST_CLIENT_ID, null));
        persistedClient = initClient();
    }

    @After
    public void clear() throws Exception {
        clearPersistedClient(persistedClient, initClient());
        credentialsService.deleteCredentials(applicationCredentials.getId());
    }

    @Test
    public void testBrokerRestart_simple() throws Throwable {
        AtomicReference<AbstractPubSubIntegrationTest.TestPublishMsg> previousMsg = new AtomicReference<>();

        testPubSub(0, previousMsg);

        SpringRestarter.getInstance().restart();

        testPubSub(NUMBER_OF_MSGS_IN_SEQUENCE, previousMsg);
    }

    @Test
    public void tesPersistedSession_interruptedConnection() throws Throwable {
        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setCleanSession(false);
        persistedClient.connect(connectOptions);

        persistedClient.subscribe(getTopicNames(TEST_TOPIC_SUBSCRIPTIONS), getQoSLevels(TEST_TOPIC_SUBSCRIPTIONS));

        SpringRestarter.getInstance().restart();

        ClientSession persistedClientSession = clientSessionCache.getClientSession(TEST_CLIENT_ID);
        Assert.assertNotNull(persistedClientSession);
        Assert.assertFalse(persistedClientSession.isConnected());
        SessionInfo sessionInfo = persistedClientSession.getSessionInfo();
        Assert.assertTrue(sessionInfo.isPersistent());
        Assert.assertEquals(new ClientInfo(TEST_CLIENT_ID, ClientType.DEVICE), sessionInfo.getClientInfo());
        Set<TopicSubscription> persistedTopicSubscriptions = clientSubscriptionCache.getClientSubscriptions(TEST_CLIENT_ID);
        Assert.assertTrue(persistedTopicSubscriptions.size() == TEST_TOPIC_SUBSCRIPTIONS.size()
                && persistedTopicSubscriptions.containsAll(TEST_TOPIC_SUBSCRIPTIONS));
    }

    @Test
    public void tesPersistedSession_afterDisconnectedClient() throws Throwable {
        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setCleanSession(false);
        persistedClient.connect(connectOptions);
        persistedClient.subscribe(getTopicNames(TEST_TOPIC_SUBSCRIPTIONS), getQoSLevels(TEST_TOPIC_SUBSCRIPTIONS));
        persistedClient.disconnect();

        SpringRestarter.getInstance().restart();

        ClientSession persistedClientSession = clientSessionCache.getClientSession(TEST_CLIENT_ID);
        Assert.assertNotNull(persistedClientSession);
        Assert.assertFalse(persistedClientSession.isConnected());
        SessionInfo sessionInfo = persistedClientSession.getSessionInfo();
        Assert.assertTrue(sessionInfo.isPersistent());
        Assert.assertEquals(new ClientInfo(TEST_CLIENT_ID, ClientType.DEVICE), sessionInfo.getClientInfo());
        Set<TopicSubscription> persistedTopicSubscriptions = clientSubscriptionCache.getClientSubscriptions(TEST_CLIENT_ID);
        Assert.assertTrue(persistedTopicSubscriptions.size() == TEST_TOPIC_SUBSCRIPTIONS.size()
                && persistedTopicSubscriptions.containsAll(TEST_TOPIC_SUBSCRIPTIONS));
    }

    private MqttClient initClient() throws MqttException {
        return new MqttClient("tcp://localhost:" + mqttPort, TEST_CLIENT_ID);
    }

    private void testPubSub(int startSequence, AtomicReference<AbstractPubSubIntegrationTest.TestPublishMsg> previousMsg) throws Throwable {
        MqttClient pubClient = new MqttClient("tcp://localhost:" + mqttPort, "app_restart_test_pub");
        pubClient.connect();

        MqttClient subClient = new MqttClient("tcp://localhost:" + mqttPort, "app_restart_test_sub");
        subClient.connect();

        Waiter waiter = new Waiter();

        subClient.subscribe(TEST_TOPIC, 0, (topic, message) -> {
            AbstractPubSubIntegrationTest.TestPublishMsg currentMsg = mapper.readValue(message.getPayload(), AbstractPubSubIntegrationTest.TestPublishMsg.class);
            if (previousMsg.get() != null) {
                waiter.assertEquals(previousMsg.get().sequenceId + 1, currentMsg.sequenceId);
            }
            if (currentMsg.sequenceId == startSequence + NUMBER_OF_MSGS_IN_SEQUENCE - 1) {
                waiter.resume();
            }
            previousMsg.getAndSet(currentMsg);
        });
        for (int j = startSequence; j < startSequence + NUMBER_OF_MSGS_IN_SEQUENCE; j++) {
            MqttMessage msg = new MqttMessage();
            AbstractPubSubIntegrationTest.TestPublishMsg payload = new AbstractPubSubIntegrationTest.TestPublishMsg(0, j, false);
            msg.setPayload(mapper.writeValueAsBytes(payload));
            msg.setQos(0);
            pubClient.publish(TEST_TOPIC, msg);
        }
        waiter.await(1, TimeUnit.SECONDS);

        pubClient.disconnect();
        pubClient.close();

        subClient.disconnect();
        subClient.close();
    }
}
