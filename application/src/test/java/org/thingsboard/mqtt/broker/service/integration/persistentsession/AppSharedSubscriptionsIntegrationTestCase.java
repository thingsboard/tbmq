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
package org.thingsboard.mqtt.broker.service.integration.persistentsession;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.junit.After;
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
import org.thingsboard.mqtt.MqttHandler;
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.actors.client.service.session.ClientSessionService;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.ClientSubscriptionService;
import org.thingsboard.mqtt.broker.common.data.ApplicationSharedSubscription;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.dao.client.application.ApplicationSharedSubscriptionService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.topic.ApplicationTopicService;
import org.thingsboard.mqtt.broker.service.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.service.test.util.TestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = AppSharedSubscriptionsIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "security.mqtt.basic.enabled=true"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class AppSharedSubscriptionsIntegrationTestCase extends AbstractPubSubIntegrationTest {

    static final int TOTAL_MSG_COUNT = 100;

    @Autowired
    private MqttClientCredentialsService credentialsService;
    @Autowired
    private ApplicationTopicService applicationTopicService;
    @Autowired
    private ApplicationSharedSubscriptionService applicationSharedSubscriptionService;
    @Autowired
    private ClientSubscriptionService clientSubscriptionService;
    @Autowired
    private ClientSessionService clientSessionService;

    MqttClientCredentials applicationCredentials1;
    MqttClientCredentials applicationCredentials2;
    MqttClientCredentials applicationCredentials3;
    MqttClientCredentials applicationCredentials4;
    MqttClientCredentials deviceCredentials;
    ApplicationSharedSubscription applicationSharedSubscription;

    @Before
    public void init() {
        log.warn("Before test start...");
        applicationCredentials1 = credentialsService.saveCredentials(
                TestUtils.createApplicationClientCredentials("test_sub_client1", null)
        );
        applicationCredentials2 = credentialsService.saveCredentials(
                TestUtils.createApplicationClientCredentials("test_sub_client2", null)
        );
        applicationCredentials3 = credentialsService.saveCredentials(
                TestUtils.createApplicationClientCredentials("test_sub_client3", null)
        );
        applicationCredentials4 = credentialsService.saveCredentials(
                TestUtils.createApplicationClientCredentials("test_sub_client4", null)
        );
        deviceCredentials = credentialsService.saveCredentials(
                TestUtils.createDeviceClientCredentials("test_pub_client", null)
        );

        applicationSharedSubscription = applicationSharedSubscriptionService.saveSharedSubscription(getSubscription());
        applicationTopicService.createSharedTopic(applicationSharedSubscription);
    }

    private ApplicationSharedSubscription getSubscription() {
        ApplicationSharedSubscription subscription = new ApplicationSharedSubscription();
        subscription.setName("test");
        subscription.setTopicFilter("test/+");
        subscription.setPartitions(10);
        return subscription;
    }

    @After
    public void clear() {
        log.warn("After test finish...");
        credentialsService.deleteCredentials(applicationCredentials1.getId());
        credentialsService.deleteCredentials(applicationCredentials2.getId());
        credentialsService.deleteCredentials(applicationCredentials3.getId());
        credentialsService.deleteCredentials(applicationCredentials4.getId());
        credentialsService.deleteCredentials(deviceCredentials.getId());

        applicationTopicService.deleteSharedTopic(applicationSharedSubscription);
        applicationSharedSubscriptionService.deleteSharedSubscription(applicationSharedSubscription.getId());
    }

    private void disconnectWithCleanSession(MqttClient client) throws Exception {
        if (client != null) {
            MqttClientConfig config = new MqttClientConfig();
            config.setCleanSession(true);
            config.setClientId(client.getClientConfig().getClientId());
            if (client.isConnected()) {
                client.disconnect();
                Thread.sleep(50);
            }
            client = MqttClient.create(config, null);
            client.connect("localhost", mqttPort).get(30, TimeUnit.SECONDS);
            client.disconnect();
        }
    }

    @Test
    public void givenSharedSubsGroupWith2ClientsAndQos0_whenPubMsgToSharedTopic_thenReceiveAllMessages() throws Throwable {
        process(MqttQoS.AT_MOST_ONCE, MqttQoS.AT_MOST_ONCE);
    }

    @Test
    public void givenSharedSubsGroupWith2ClientsAndQos1_whenPubMsgToSharedTopic_thenReceiveAllMessages() throws Throwable {
        process(MqttQoS.AT_LEAST_ONCE, MqttQoS.AT_LEAST_ONCE);
    }

    @Test
    public void givenSharedSubsGroupWith2ClientsAndQos2_whenPubMsgToSharedTopic_thenReceiveAllMessages() throws Throwable {
        process(MqttQoS.EXACTLY_ONCE, MqttQoS.EXACTLY_ONCE);
    }

    @Test
    public void givenSharedSubsGroupWith2ClientsAndQos0PubAndQos1Sub_whenPubMsgToSharedTopic_thenReceiveAllMessages() throws Throwable {
        process(MqttQoS.AT_LEAST_ONCE, MqttQoS.AT_MOST_ONCE);
    }

    @Test
    public void givenSharedSubsGroupWith2ClientsAndQos1PubAndQos0Sub_whenPubMsgToSharedTopic_thenReceiveAllMessages() throws Throwable {
        process(MqttQoS.AT_MOST_ONCE, MqttQoS.AT_LEAST_ONCE);
    }

    @Test
    public void givenSharedSubsGroupWith2ClientsAndQos0PubAndQos2Sub_whenPubMsgToSharedTopic_thenReceiveAllMessages() throws Throwable {
        process(MqttQoS.EXACTLY_ONCE, MqttQoS.AT_MOST_ONCE);
    }

    @Test
    public void givenSharedSubsGroupWith2ClientsAndQos2PubAndQos0Sub_whenPubMsgToSharedTopic_thenReceiveAllMessages() throws Throwable {
        process(MqttQoS.AT_MOST_ONCE, MqttQoS.EXACTLY_ONCE);
    }

    private void process(MqttQoS subQos, MqttQoS pubQos) throws Exception {
        CountDownLatch receivedResponses = new CountDownLatch(TOTAL_MSG_COUNT);

        AtomicInteger shareSubClient1ReceivedMessages = new AtomicInteger();
        AtomicInteger shareSubClient2ReceivedMessages = new AtomicInteger();

        //sub
        MqttClient shareSubClient1 = getClient("test_sub_client1", getHandler(receivedResponses, shareSubClient1ReceivedMessages), false);
        MqttClient shareSubClient2 = getClient("test_sub_client2", getHandler(receivedResponses, shareSubClient2ReceivedMessages), false);

        shareSubClient1.on("$share/g1/test/+", getHandler(receivedResponses, shareSubClient1ReceivedMessages), subQos).get(5, TimeUnit.SECONDS);
        shareSubClient2.on("$share/g1/test/+", getHandler(receivedResponses, shareSubClient2ReceivedMessages), subQos).get(5, TimeUnit.SECONDS);

        //pub
        MqttClient pubClient = getPubClient();
        sendPublishPackets(pubClient, pubQos);

        boolean await = receivedResponses.await(10, TimeUnit.SECONDS);
        log.error("The result of awaiting is: [{}]", await);

        //asserts
        assertEquals(TOTAL_MSG_COUNT, shareSubClient1ReceivedMessages.get() + shareSubClient2ReceivedMessages.get());

        //disconnect clients
        disconnectClient(pubClient);

        disconnectWithCleanSession(shareSubClient1);
        disconnectWithCleanSession(shareSubClient2);
    }

    @Test
    public void givenSharedSubsGroupWith1PersistedClient_whenDisconnectAndConnect_thenReceiveAllMessagesWithoutAdditionalSubscribe() throws Throwable {
        CountDownLatch receivedResponses = new CountDownLatch(TOTAL_MSG_COUNT);
        AtomicInteger shareSubClientReceivedMessages = new AtomicInteger();

        //sub
        MqttClient shareSubClient = getClient("test_sub_client1", getHandler(receivedResponses, shareSubClientReceivedMessages), false);
        shareSubClient.on("$share/g1/test/+", getHandler(receivedResponses, shareSubClientReceivedMessages), MqttQoS.AT_LEAST_ONCE).get(5, TimeUnit.SECONDS);
        Awaitility
                .await()
                .atMost(5, TimeUnit.SECONDS)
                .until(() -> clientSubscriptionService.getClientSubscriptionsCount() == 1);

        //disconnect
        shareSubClient.disconnect();
        Awaitility
                .await()
                .atMost(5, TimeUnit.SECONDS)
                .until(() -> clientSessionService.getClientSessionInfo(shareSubClient.getClientConfig().getClientId()).isDisconnected());

        shareSubClient.connect("localhost", mqttPort).get(30, TimeUnit.SECONDS);
        Awaitility
                .await()
                .atMost(5, TimeUnit.SECONDS)
                .until(() -> clientSessionService.getClientSessionInfo(shareSubClient.getClientConfig().getClientId()).isConnected());

        //pub
        MqttClient pubClient = getPubClient();
        sendPublishPackets(pubClient);

        boolean await = receivedResponses.await(10, TimeUnit.SECONDS);
        log.error("The result of awaiting is: [{}]", await);

        //asserts
        assertEquals(TOTAL_MSG_COUNT, shareSubClientReceivedMessages.get());

        //disconnect clients
        disconnectClient(pubClient);

        disconnectWithCleanSession(shareSubClient);
    }

    @Test
    public void givenSharedSubsGroupWith2PersistedClients_whenPubMsgToSharedTopic_thenReceiveAllMessages() throws Throwable {
        CountDownLatch receivedResponses = new CountDownLatch(TOTAL_MSG_COUNT);

        AtomicInteger shareSubClient1ReceivedMessages = new AtomicInteger();
        AtomicInteger shareSubClient2ReceivedMessages = new AtomicInteger();

        //sub
        MqttClient shareSubClient1 = getClient("test_sub_client1", getHandler(receivedResponses, shareSubClient1ReceivedMessages), false);
        MqttClient shareSubClient2 = getClient("test_sub_client2", getHandler(receivedResponses, shareSubClient2ReceivedMessages), false);

        shareSubClient1.on("$share/g1/test/+", getHandler(receivedResponses, shareSubClient1ReceivedMessages), MqttQoS.AT_LEAST_ONCE).get(5, TimeUnit.SECONDS);
        shareSubClient2.on("$share/g1/test/+", getHandler(receivedResponses, shareSubClient2ReceivedMessages), MqttQoS.AT_LEAST_ONCE).get(5, TimeUnit.SECONDS);

        //pub
        MqttClient pubClient = getPubClient();
        sendPublishPackets(pubClient);

        boolean await = receivedResponses.await(10, TimeUnit.SECONDS);
        log.error("The result of awaiting is: [{}]", await);

        //asserts
        assertEquals(TOTAL_MSG_COUNT, shareSubClient1ReceivedMessages.get() + shareSubClient2ReceivedMessages.get());

        //disconnect clients
        disconnectClient(pubClient);

        disconnectWithCleanSession(shareSubClient1);
        disconnectWithCleanSession(shareSubClient2);
    }

    @Test
    public void givenSharedSubsGroupWith2PersistedClients_whenPubMsgToSharedTopicAndUnsubscribeOneClient_thenReceiveAllMessages() throws Throwable {
        CountDownLatch receivedResponses = new CountDownLatch(TOTAL_MSG_COUNT);

        AtomicInteger shareSubClient1ReceivedMessages = new AtomicInteger();
        AtomicInteger shareSubClient2ReceivedMessages = new AtomicInteger();

        //sub
        MqttHandlerImpl client1Handler = getHandler(receivedResponses, shareSubClient1ReceivedMessages);
        MqttHandlerImpl client2Handler = getHandler(receivedResponses, shareSubClient2ReceivedMessages);

        MqttClient shareSubClient1 = getClient("test_sub_client3", client1Handler, false);
        MqttClient shareSubClient2 = getClient("test_sub_client4", client2Handler, false);

        shareSubClient1.on("$share/g1/test/+", client1Handler, MqttQoS.EXACTLY_ONCE).get(5, TimeUnit.SECONDS);
        shareSubClient2.on("$share/g1/test/+", client2Handler, MqttQoS.EXACTLY_ONCE).get(5, TimeUnit.SECONDS);

        //pub
        MqttClient pubClient = getPubClient();
        sendPublishPackets(pubClient);

        boolean await = receivedResponses.await(10, TimeUnit.SECONDS);
        log.error("The result of awaiting is: [{}]", await);

        //asserts
        assertEquals(TOTAL_MSG_COUNT, shareSubClient1ReceivedMessages.get() + shareSubClient2ReceivedMessages.get());

        //unsub
        shareSubClient2.off("$share/g1/test/+").get(5, TimeUnit.SECONDS);

        Set<TopicSubscription> client1Subscriptions = clientSubscriptionService.getClientSubscriptions("test_sub_client3");
        assertEquals(1, client1Subscriptions.size());
        Set<TopicSubscription> client2Subscriptions = clientSubscriptionService.getClientSubscriptions("test_sub_client4");
        assertTrue(client2Subscriptions.isEmpty());

        receivedResponses = new CountDownLatch(TOTAL_MSG_COUNT);
        client1Handler.updateLatch(receivedResponses);

        //pub
        sendPublishPackets(pubClient);

        await = receivedResponses.await(10, TimeUnit.SECONDS);
        log.error("The result of awaiting is: [{}]", await);

        //asserts
        assertEquals(TOTAL_MSG_COUNT * 2, shareSubClient1ReceivedMessages.get() + shareSubClient2ReceivedMessages.get());

        //disconnect clients
        disconnectClient(pubClient);

        disconnectWithCleanSession(shareSubClient1);
        disconnectWithCleanSession(shareSubClient2);
    }

    private void sendPublishPackets(MqttClient pubClient) throws Exception {
        sendPublishPackets(pubClient, MqttQoS.EXACTLY_ONCE);
    }

    private void sendPublishPackets(MqttClient pubClient, MqttQoS qos) throws Exception {
        for (int i = 0; i < TOTAL_MSG_COUNT; i++) {
            pubClient.publish(
                            "test/topic",
                            Unpooled.wrappedBuffer(Integer.toString(i).getBytes(StandardCharsets.UTF_8)),
                            qos)
                    .get(5, TimeUnit.SECONDS);
            Thread.sleep(50);
        }
    }

    private MqttClient getPubClient() throws Exception {
        return getClient("test_pub_client", null, true);
    }

    private MqttClient getClient(String clientId, MqttHandler handler, boolean cleanSession) throws Exception {
        MqttClientConfig config = new MqttClientConfig();
        config.setCleanSession(cleanSession);
        config.setClientId(clientId);
        MqttClient client = MqttClient.create(config, handler);
        client.connect("localhost", mqttPort).get(5, TimeUnit.SECONDS);
        return client;
    }

    private MqttHandlerImpl getHandler(CountDownLatch latch, AtomicInteger integer) {
        return new MqttHandlerImpl(latch, integer);
    }

    private void disconnectClient(MqttClient client) {
        client.disconnect();
    }

    @Data
    @AllArgsConstructor
    private static class MqttHandlerImpl implements MqttHandler {

        private CountDownLatch latch;
        private AtomicInteger ai;

        public void updateLatch(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void onMessage(String s, ByteBuf byteBuf) {
            ai.incrementAndGet();
            latch.countDown();
        }
    }
}
