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
import org.eclipse.paho.mqttv5.client.IMqttMessageListener;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
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
import org.thingsboard.mqtt.broker.actors.client.service.subscription.SubscriptionService;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.data.subscription.ClientTopicSubscription;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscriptionCache;
import org.thingsboard.mqtt.broker.service.subscription.EntitySubscription;
import org.thingsboard.mqtt.broker.service.subscription.ValueWithTopicFilter;
import org.thingsboard.mqtt.broker.service.test.util.TestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = Mqtt5PubSubIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "security.mqtt.basic.enabled=true"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class Mqtt5PubSubIntegrationTestCase extends AbstractPubSubIntegrationTest {

    static final String PUB_CLIENT_ID = "pubClientId";
    static final String SUB_CLIENT_ID = "subClientId";
    static final String MY_TOPIC = "my/topic";
    static final String TEST_TOPIC = "test/topic";
    static final String DUMMY_TOPIC = "dummy/topic";

    @Autowired
    private MqttClientCredentialsService credentialsService;
    @Autowired
    private ClientSubscriptionCache clientSubscriptionCache;
    @Autowired
    private SubscriptionService subscriptionService;

    private MqttClientCredentials pubCredentials;
    private MqttClientCredentials subCredentials;

    @Before
    public void init() throws Exception {
        pubCredentials = saveCredentials(PUB_CLIENT_ID, List.of(TEST_TOPIC));
        subCredentials = saveCredentials(SUB_CLIENT_ID, List.of(MY_TOPIC));
    }

    private MqttClientCredentials saveCredentials(String pubClientId, List<String> pubAuthRulePatterns) {
        return credentialsService.saveCredentials(TestUtils.createDeviceClientCredentialsWithAuth(pubClientId, pubAuthRulePatterns));
    }

    @After
    public void clear() {
        credentialsService.deleteCredentials(pubCredentials.getId());
        credentialsService.deleteCredentials(subCredentials.getId());
    }

    @Test
    public void givenPubClient_whenConnectWithoutUserNameAndWithPassword_thenSuccess() throws Throwable {
        String password = "password12345";
        String encodedPassword = passwordEncoder.encode(password);

        MqttClientCredentials credentials = TestUtils.createDeviceClientCredentialsWithPass("noUserNameWithPass", encodedPassword);
        MqttClientCredentials mqttClientCredentials = credentialsService.saveCredentials(credentials);
        Assert.assertNotNull(mqttClientCredentials);
        Assert.assertNotNull(mqttClientCredentials.getCredentialsId());

        MqttClient pubClient = new MqttClient(SERVER_URI + mqttPort, "noUserNameWithPass");
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setUserName(null);
        options.setPassword(password.getBytes(StandardCharsets.UTF_8));
        pubClient.connect(options);
        pubClient.publish(MY_TOPIC, BrokerConstants.DUMMY_PAYLOAD, 1, false);

        pubClient.disconnect();
        pubClient.close();

        credentialsService.deleteCredentials(mqttClientCredentials.getId());
    }

    @Test
    public void givenPubSubClients_whenPubMsgToNoAuthTopic_thenNotReceiveMsgBySubscriber() throws Throwable {
        CountDownLatch latch = new CountDownLatch(1);

        MqttClient subClient = new MqttClient(SERVER_URI + mqttPort, SUB_CLIENT_ID);
        subClient.connect();
        MqttSubscription[] subscriptions = {new MqttSubscription(MY_TOPIC, 1)};
        IMqttMessageListener[] listeners = {(topic, message) -> latch.countDown()};
        subClient.subscribe(subscriptions, listeners);

        MqttClient pubClient = new MqttClient(SERVER_URI + mqttPort, PUB_CLIENT_ID);
        pubClient.connect();
        pubClient.publish(MY_TOPIC, BrokerConstants.DUMMY_PAYLOAD, 1, false);

        boolean await = latch.await(1, TimeUnit.SECONDS);
        Assert.assertFalse(await);

        pubClient.disconnect();
        pubClient.close();

        subClient.disconnect();
        subClient.close();
    }

    @Test
    public void givenPubSubClients_whenSubToNoAuthTopic_thenNotReceiveMsgBySubscriber() throws Throwable {
        CountDownLatch latch = new CountDownLatch(1);

        MqttClient subClient = new MqttClient(SERVER_URI + mqttPort, SUB_CLIENT_ID);
        subClient.connect();
        MqttSubscription[] subscriptions = {new MqttSubscription(TEST_TOPIC, 1)};
        IMqttMessageListener[] listeners = {(topic, message) -> latch.countDown()};
        subClient.subscribe(subscriptions, listeners);

        MqttClient pubClient = new MqttClient(SERVER_URI + mqttPort, PUB_CLIENT_ID);
        pubClient.connect();
        pubClient.publish(TEST_TOPIC, BrokerConstants.DUMMY_PAYLOAD, 1, false);

        boolean await = latch.await(1, TimeUnit.SECONDS);
        Assert.assertFalse(await);

        MqttSubscription[] newSubscriptions = {new MqttSubscription(MY_TOPIC, 1)};
        subClient.subscribe(newSubscriptions, listeners);

        Set<TopicSubscription> clientSubscriptions = clientSubscriptionCache.getClientSubscriptions(SUB_CLIENT_ID);
        Assert.assertEquals(1, clientSubscriptions.size());
        Assert.assertTrue(clientSubscriptions.contains(new ClientTopicSubscription(MY_TOPIC, 1)));

        String[] unsubscribeTopics = {MY_TOPIC};
        subClient.unsubscribe(unsubscribeTopics);

        clientSubscriptions = clientSubscriptionCache.getClientSubscriptions(SUB_CLIENT_ID);
        Assert.assertTrue(clientSubscriptions.isEmpty());

        pubClient.disconnect();
        pubClient.close();

        subClient.disconnect();
        subClient.close();
    }

    @Test
    public void givenSubClient_whenSubscribeSeveralTopics_thenOnlyValidSubscriptionsPresent() throws Throwable {
        MqttClient subClient = new MqttClient(SERVER_URI + mqttPort, SUB_CLIENT_ID);
        subClient.connect();
        MqttSubscription[] subscriptions = {
                new MqttSubscription(TEST_TOPIC, 1),
                new MqttSubscription(MY_TOPIC, 2),
                new MqttSubscription(DUMMY_TOPIC, 0)
        };
        IMqttMessageListener[] listeners = {
                (topic, message) -> {
                },
                (topic, message) -> {
                },
                (topic, message) -> {
                }
        };
        subClient.subscribe(subscriptions, listeners);

        Set<TopicSubscription> clientSubscriptions = clientSubscriptionCache.getClientSubscriptions(SUB_CLIENT_ID);
        Assert.assertEquals(1, clientSubscriptions.size());
        Assert.assertTrue(clientSubscriptions.contains(new ClientTopicSubscription(MY_TOPIC, 2)));
        TopicSubscription topicSubscription = clientSubscriptions.stream().findFirst().get();
        Assert.assertEquals(2, topicSubscription.getQos());

        subClient.disconnect();
        subClient.close();
    }

    @Test
    public void givenSubClient_whenSubscribeTopicAndSubscribeSameTopicAgainWithDifferentQos_thenSuccess() throws Throwable {
        MqttClient subClient = new MqttClient(SERVER_URI + mqttPort, SUB_CLIENT_ID);
        subClient.connect();
        MqttSubscription[] subscriptions = {
                new MqttSubscription(MY_TOPIC, 1)
        };
        IMqttMessageListener[] listeners = {
                (topic, message) -> {
                }
        };
        subClient.subscribe(subscriptions, listeners);

        processAsserts(1);

        MqttSubscription[] subscriptionsUpdate = {
                new MqttSubscription(MY_TOPIC, 2)
        };
        subClient.subscribe(subscriptionsUpdate, listeners);

        processAsserts(2);

        subClient.disconnect();
        subClient.close();
    }

    private void processAsserts(int qos) {
        Set<TopicSubscription> clientSubscriptions = clientSubscriptionCache.getClientSubscriptions(SUB_CLIENT_ID);
        Collection<ValueWithTopicFilter<EntitySubscription>> clientSubscriptionsFromTrie = subscriptionService.getSubscriptions(MY_TOPIC);

        Assert.assertEquals(1, clientSubscriptions.size());
        Assert.assertTrue(clientSubscriptions.contains(new ClientTopicSubscription(MY_TOPIC, qos)));
        TopicSubscription topicSubscription = clientSubscriptions.stream().findFirst().get();
        Assert.assertEquals(qos, topicSubscription.getQos());
        Assert.assertEquals(1, clientSubscriptionsFromTrie.size());
        clientSubscriptionsFromTrie.forEach(clientSubscriptionValueWithTopicFilter -> {
            Assert.assertEquals(SUB_CLIENT_ID, clientSubscriptionValueWithTopicFilter.getValue().getClientId());
            Assert.assertEquals(qos, clientSubscriptionValueWithTopicFilter.getValue().getQos());
        });
    }
}
