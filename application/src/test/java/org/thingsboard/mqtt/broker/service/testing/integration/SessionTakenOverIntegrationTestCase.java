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
package org.thingsboard.mqtt.broker.service.testing.integration;

import com.google.common.util.concurrent.Futures;
import io.micrometer.core.instrument.MeterRegistry;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttVersion;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.MqttClient;
import org.thingsboard.mqtt.MqttClientConfig;
import org.thingsboard.mqtt.MqttHandler;
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.common.data.ClientSession;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.common.stats.StatsType;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscriptionCache;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = SessionTakenOverIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@DaoSqlTest
@RunWith(SpringRunner.class)
public class SessionTakenOverIntegrationTestCase extends AbstractPubSubIntegrationTest {

    static final String MY_TOPIC = "my/topic";

    @Autowired
    private ClientSessionCache clientSessionCache;
    @Autowired
    private ClientSubscriptionCache clientSubscriptionCache;
    @Autowired
    private MeterRegistry meterRegistry;

    // Every client created here is registered so that @After disconnects it. A client left connected
    // keeps its session on the broker and, with the reconnect that MqttClientConfig enables by default,
    // keeps dialing back once a second for the rest of the surefire fork.
    private final List<MqttClient> clients = new ArrayList<>();

    @After
    public void clear() {
        clients.forEach(MqttClient::disconnect);
    }

    @Test
    public void givenCleanSessionClient_whenCleanSessionTakenOver_thenNoSubscriptionsPresent() throws Throwable {
        String clientId = RandomStringUtils.randomAlphabetic(10);
        MqttClientConfig config = getConfig(clientId, true);

        MqttClient client = newClient(config);
        connectClient(client);
        subscribeClient(client);

        sessionAndSubsPresent(clientId);

        MqttClient clientTakenOver = newClient(config);
        connectClient(clientTakenOver);

        Set<TopicSubscription> clientSubscriptions = clientSubscriptionCache.getClientSubscriptions(clientId);
        Assert.assertEquals(0, clientSubscriptions.size());
    }

    @Test
    public void givenPersistentSessionClient_whenPersistentSessionTakenOver_thenSubscriptionsPresent() throws Throwable {
        String clientId = RandomStringUtils.randomAlphabetic(10);
        MqttClientConfig config = getConfig(clientId, false);

        MqttClient client = newClient(config);
        connectClient(client);
        subscribeClient(client);

        sessionAndSubsPresent(clientId);

        MqttClient clientTakenOver = newClient(config);
        connectClient(clientTakenOver);

        Set<TopicSubscription> clientSubscriptions = clientSubscriptionCache.getClientSubscriptions(clientId);
        Assert.assertEquals(1, clientSubscriptions.size());
    }

    @Test
    public void givenPersistentSessionClient_whenCleanSessionTakenOver_thenNoSubscriptionsPresent() throws Throwable {
        String clientId = RandomStringUtils.randomAlphabetic(10);
        MqttClientConfig config = getConfig(clientId, false);

        MqttClient client = newClient(config);
        connectClient(client);
        subscribeClient(client);

        sessionAndSubsPresent(clientId);

        config = getConfig(clientId, true);
        MqttClient clientTakenOver = newClient(config);
        connectClient(clientTakenOver);

        Set<TopicSubscription> clientSubscriptions = clientSubscriptionCache.getClientSubscriptions(clientId);
        Assert.assertEquals(0, clientSubscriptions.size());
    }

    @Test
    public void givenCleanSessionClient_whenPersistentSessionTakenOver_thenNoSubscriptionsPresent() throws Throwable {
        String clientId = RandomStringUtils.randomAlphabetic(10);
        MqttClientConfig config = getConfig(clientId, true);

        MqttClient client = newClient(config);
        connectClient(client);
        subscribeClient(client);

        sessionAndSubsPresent(clientId);

        config = getConfig(clientId, false);
        MqttClient clientTakenOver = newClient(config);
        connectClient(clientTakenOver);

        Set<TopicSubscription> clientSubscriptions = clientSubscriptionCache.getClientSubscriptions(clientId);
        Assert.assertEquals(0, clientSubscriptions.size());
    }

    @Test
    public void givenConnectedClient_whenSessionTakenOver_thenClientDisconnectsCounterIncremented() throws Throwable {
        String clientId = RandomStringUtils.randomAlphabetic(10);
        MqttClientConfig config = getConfig(clientId, true);

        MqttClient client = newClient(config);
        connectClient(client);

        // Cumulative Micrometer counter (never reset), so assert the delta caused by the takeover disconnect —
        // immune to disconnects produced by the other test methods sharing this context.
        double before = clientDisconnectsCount();

        // Reconnecting with the same clientId forces the broker to disconnect the original session with
        // ON_CLUSTER_CONFLICTING_SESSIONS: a broker-generated disconnect that must bump clientDisconnects.
        MqttClient clientTakenOver = newClient(config);
        connectClient(clientTakenOver);

        // The takeover disconnect is processed asynchronously on the client actor thread, so poll the counter.
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> clientDisconnectsCount() - before >= 1);
    }

    private double clientDisconnectsCount() {
        return meterRegistry.get(StatsType.CLIENT_DISCONNECTS.getPrintName()).counter().count();
    }

    private MqttClient newClient(MqttClientConfig config) {
        MqttClient client = MqttClient.create(config, null, externalExecutorService);
        clients.add(client);
        return client;
    }

    private void connectClient(MqttClient client) throws InterruptedException, ExecutionException, TimeoutException {
        client.connect("localhost", mqttPort).get(30, TimeUnit.SECONDS);
    }

    private MqttClientConfig getConfig(String clientId, boolean cleanSession) {
        MqttClientConfig config = new MqttClientConfig();
        config.setClientId(clientId);
        config.setCleanSession(cleanSession);
        config.setProtocolVersion(MqttVersion.MQTT_3_1_1);
        // The takeover disconnects the client whose session was stolen. With the default reconnect it
        // would come back a second later and steal the session right back, racing the assertions below.
        config.setReconnect(false);
        return config;
    }

    private void subscribeClient(MqttClient client) throws InterruptedException, ExecutionException, TimeoutException {
        client.on(MY_TOPIC, getHandler(), MqttQoS.AT_LEAST_ONCE).get(30, TimeUnit.SECONDS);
    }

    private MqttHandler getHandler() {
        return (s, byteBuf) -> Futures.immediateVoidFuture();
    }

    private void sessionAndSubsPresent(String clientId) {
        ClientSession clientSession = clientSessionCache.getClientSession(clientId);
        Assert.assertNotNull(clientSession);
        Set<TopicSubscription> clientSubscriptions = clientSubscriptionCache.getClientSubscriptions(clientId);
        Assert.assertEquals(1, clientSubscriptions.size());
    }
}
