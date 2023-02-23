/**
 * Copyright © 2016-2023 The Thingsboard Authors
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
import org.awaitility.Awaitility;
import org.eclipse.paho.mqttv5.client.IMqttMessageListener;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
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
import org.thingsboard.mqtt.broker.common.data.ClientSession;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscriptionCache;
import org.thingsboard.mqtt.broker.service.subscription.TopicSubscription;

import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = SessionExpiryIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "security.mqtt.basic.enabled=false",
        "mqtt.client-session-expiry.cron=* * * ? * *" // every second
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class SessionExpiryIntegrationTestCase extends AbstractPubSubIntegrationTest {

    static final String MY_TOPIC = "my/topic";
    static final String CLIENT_ID = "expiryCleanClient";

    @Autowired
    private ClientSessionCache clientSessionCache;
    @Autowired
    private ClientSubscriptionCache clientSubscriptionCache;

    @Before
    public void init() throws Exception {
    }

    @After
    public void clear() {
    }

    @Test
    public void givenSessionWithCleanStartAndSessionExpiryInterval_whenDisconnect_thenSessionIsClearedAfterDelay() throws Throwable {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(true);
        options.setSessionExpiryInterval(5L);

        MqttClient client = new MqttClient("tcp://localhost:" + mqttPort, CLIENT_ID);
        client.connect(options);

        IMqttMessageListener[] listeners = {(topic, message) -> {
        }};
        MqttSubscription[] subscriptions = {new MqttSubscription(MY_TOPIC, 2)};
        client.subscribe(subscriptions, listeners);

        client.disconnect();
        client.close();

        ClientSession clientSession = clientSessionCache.getClientSession(CLIENT_ID);
        Assert.assertNotNull(clientSession);
        Set<TopicSubscription> clientSubscriptions = clientSubscriptionCache.getClientSubscriptions(CLIENT_ID);
        Assert.assertNotNull(clientSubscriptions);
        Assert.assertEquals(1, clientSubscriptions.size());

        Awaitility.await()
                .atMost(7, TimeUnit.SECONDS)
                .until(this::clientSessionCleared);
    }

    @Test
    public void givenSessionWithCleanStart_whenDisconnect_thenSessionIsCleared() throws Throwable {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(true);

        MqttClient client = new MqttClient("tcp://localhost:" + mqttPort, CLIENT_ID);
        client.connect(options);

        IMqttMessageListener[] listeners = {(topic, message) -> {
        }};
        MqttSubscription[] subscriptions = {new MqttSubscription(MY_TOPIC, 2)};
        client.subscribe(subscriptions, listeners);

        client.disconnect();
        client.close();

        ClientSession clientSession = clientSessionCache.getClientSession(CLIENT_ID);
        Assert.assertNull(clientSession);
        Set<TopicSubscription> clientSubscriptions = clientSubscriptionCache.getClientSubscriptions(CLIENT_ID);
        Assert.assertTrue(clientSubscriptions.isEmpty());
    }

    @Test
    public void givenSessionWithCleanStartAndSessionExpiryInterval_whenDisconnectWithExpiryInterval_thenSessionIsClearedAfterDelay() throws Throwable {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(true);
        options.setSessionExpiryInterval(1000L);

        MqttAsyncClient client = new MqttAsyncClient("tcp://localhost:" + mqttPort, CLIENT_ID);
        client.connect(options).waitForCompletion();

        MqttProperties disconnectProperties = new MqttProperties();
        disconnectProperties.setSessionExpiryInterval(2L);
        client.disconnect(0, null, null, 0, disconnectProperties).waitForCompletion();
        client.close();

        ClientSession clientSession = clientSessionCache.getClientSession(CLIENT_ID);
        Assert.assertNotNull(clientSession);

        Awaitility.await()
                .atMost(4, TimeUnit.SECONDS)
                .until(() -> clientSessionCache.getClientSession(CLIENT_ID) == null);
    }

    @Test
    public void givenSessionWithoutCleanStartAndWithSessionExpiryInterval_whenDisconnect_thenSessionIsClearedAfterDelay() throws Throwable {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(false);
        options.setSessionExpiryInterval(5L);

        ClientSession clientSession = clientSessionCache.getClientSession(CLIENT_ID);
        Assert.assertNull(clientSession);

        MqttClient client = new MqttClient("tcp://localhost:" + mqttPort, CLIENT_ID);
        client.connect(options);

        IMqttMessageListener[] listeners = {(topic, message) -> {
        }};
        MqttSubscription[] subscriptions = {new MqttSubscription(MY_TOPIC, 2)};
        client.subscribe(subscriptions, listeners);

        client.disconnect();
        client.close();

        clientSession = clientSessionCache.getClientSession(CLIENT_ID);
        Assert.assertNotNull(clientSession);
        Assert.assertEquals(5, clientSession.getSessionInfo().getSessionExpiryInterval().intValue());
        Set<TopicSubscription> clientSubscriptions = clientSubscriptionCache.getClientSubscriptions(CLIENT_ID);
        Assert.assertNotNull(clientSubscriptions);
        Assert.assertEquals(1, clientSubscriptions.size());

        Awaitility.await()
                .atMost(7, TimeUnit.SECONDS)
                .until(this::clientSessionCleared);
    }

    @Test
    public void givenSessionWithoutCleanStart_whenDisconnect_thenSessionIsNotCleared() throws Throwable {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(false);

        ClientSession clientSession = clientSessionCache.getClientSession(CLIENT_ID);
        Assert.assertNull(clientSession);

        MqttClient client = new MqttClient("tcp://localhost:" + mqttPort, CLIENT_ID);
        client.connect(options);

        IMqttMessageListener[] listeners = {(topic, message) -> {
        }};
        MqttSubscription[] subscriptions = {new MqttSubscription(MY_TOPIC, 2)};
        client.subscribe(subscriptions, listeners);

        client.disconnect();
        client.close();

        clientSession = clientSessionCache.getClientSession(CLIENT_ID);
        Assert.assertNotNull(clientSession);
        Set<TopicSubscription> clientSubscriptions = clientSubscriptionCache.getClientSubscriptions(CLIENT_ID);
        Assert.assertNotNull(clientSubscriptions);
        Assert.assertEquals(1, clientSubscriptions.size());

        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .until(this::clientSessionNotCleared);

        client = new MqttClient("tcp://localhost:" + mqttPort, CLIENT_ID);
        options.setCleanStart(true);
        options.setSessionExpiryInterval(0L);
        client.connect(options);
        client.disconnect();
        client.close();
    }

    @Test
    public void givenSessionWithCleanStartAndSessionExpiryInterval_whenDisconnectAndReconnectWithoutCleanStart_thenReconnectSuccessful() throws Throwable {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(true);
        options.setSessionExpiryInterval(5L);

        MqttClient client = new MqttClient("tcp://localhost:" + mqttPort, CLIENT_ID);
        client.connect(options);

        IMqttMessageListener[] listeners = {(topic, message) -> {
        }};
        MqttSubscription[] subscriptions = {new MqttSubscription(MY_TOPIC, 2)};
        client.subscribe(subscriptions, listeners);

        client.disconnect();
        client.close();

        ClientSession clientSession = clientSessionCache.getClientSession(CLIENT_ID);
        Assert.assertNotNull(clientSession);
        Set<TopicSubscription> clientSubscriptions = clientSubscriptionCache.getClientSubscriptions(CLIENT_ID);
        Assert.assertNotNull(clientSubscriptions);
        Assert.assertEquals(1, clientSubscriptions.size());

        client = new MqttClient("tcp://localhost:" + mqttPort, CLIENT_ID);
        options.setCleanStart(false);
        client.connect(options);

        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .until(() -> clientSessionCache.getClientSession(CLIENT_ID) != null && clientSubscriptionCache.getClientSubscriptions(CLIENT_ID).size() == 1);

        client.disconnect();
        client.close();

        client = new MqttClient("tcp://localhost:" + mqttPort, CLIENT_ID);
        options.setCleanStart(true);
        options.setSessionExpiryInterval(0L);
        client.connect(options);
        client.disconnect();
        client.close();
    }

    private boolean clientSessionCleared() {
        return clientSessionCache.getClientSession(CLIENT_ID) == null && clientSubscriptionCache.getClientSubscriptions(CLIENT_ID).isEmpty();
    }

    private boolean clientSessionNotCleared() {
        return clientSessionCache.getClientSession(CLIENT_ID) != null && !clientSubscriptionCache.getClientSubscriptions(CLIENT_ID).isEmpty();
    }
}