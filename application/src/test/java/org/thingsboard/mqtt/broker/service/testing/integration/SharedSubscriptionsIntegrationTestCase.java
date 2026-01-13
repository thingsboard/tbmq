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
import io.netty.buffer.Unpooled;
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
import org.thingsboard.mqtt.broker.actors.client.service.session.ClientSessionService;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.ClientSubscriptionService;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = SharedSubscriptionsIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@DaoSqlTest
@RunWith(SpringRunner.class)
public class SharedSubscriptionsIntegrationTestCase extends AbstractPubSubIntegrationTest {

    static final int TOTAL_MSG_COUNT = 10;

    @Autowired
    private ClientSessionService clientSessionService;
    @Autowired
    private ClientSubscriptionService clientSubscriptionService;

    private MqttClient shareSubClient1;
    private MqttClient shareSubClient2;

    @After
    public void clear() throws Exception {
        MqttClientConfig config = new MqttClientConfig();
        config.setCleanSession(true);
        config.setProtocolVersion(MqttVersion.MQTT_3_1_1);
        disconnectWithCleanSession(shareSubClient1, config);
        disconnectWithCleanSession(shareSubClient2, config);
    }

    private void disconnectWithCleanSession(MqttClient client, MqttClientConfig config) throws Exception {
        if (client != null) {
            if (client.isConnected()) {
                client.disconnect();
                Thread.sleep(50);
            }
            client = MqttClient.create(config, null, externalExecutorService);
            client.connect("localhost", mqttPort).get(30, TimeUnit.SECONDS);
            client.disconnect();
        }
    }

    @Test
    public void givenSharedSubsGroupWith2ClientsAndQos0_whenPubMsgToSharedTopic_thenReceiveAllMessages() throws Throwable {
        process(true, MqttQoS.AT_MOST_ONCE, MqttQoS.AT_MOST_ONCE);
    }

    @Test
    public void givenSharedSubsGroupWith2ClientsAndQos1_whenPubMsgToSharedTopic_thenReceiveAllMessages() throws Throwable {
        process(true, MqttQoS.AT_LEAST_ONCE, MqttQoS.AT_LEAST_ONCE);
    }

    @Test
    public void givenSharedSubsGroupWith2ClientsAndQos2_whenPubMsgToSharedTopic_thenReceiveAllMessages() throws Throwable {
        process(true, MqttQoS.EXACTLY_ONCE, MqttQoS.EXACTLY_ONCE);
    }

    @Test
    public void givenSharedSubsGroupWith2PersistentClientsAndQos2SubAndQos0Pub_whenPubMsgToSharedTopic_thenReceiveAllMessages() throws Throwable {
        process(false, MqttQoS.EXACTLY_ONCE, MqttQoS.AT_MOST_ONCE);
    }

    private void process(boolean cleanSession, MqttQoS subQos, MqttQoS pubQos) throws Exception {
        String random = RandomStringUtils.randomAlphabetic(5);
        CountDownLatch receivedResponses = new CountDownLatch(TOTAL_MSG_COUNT);

        AtomicInteger shareSubClient1ReceivedMessages = new AtomicInteger();
        AtomicInteger shareSubClient2ReceivedMessages = new AtomicInteger();

        //sub
        shareSubClient1 = getClient(getHandler(receivedResponses, shareSubClient1ReceivedMessages), cleanSession);
        shareSubClient2 = getClient(getHandler(receivedResponses, shareSubClient2ReceivedMessages), cleanSession);

        shareSubClient1.on("$share/g1/test/+/" + random, getHandler(receivedResponses, shareSubClient1ReceivedMessages), subQos).get(30, TimeUnit.SECONDS);
        shareSubClient2.on("$share/g1/test/+/" + random, getHandler(receivedResponses, shareSubClient2ReceivedMessages), subQos).get(30, TimeUnit.SECONDS);

        //pub
        MqttClient pubClient = getMqttPubClient();
        for (int i = 0; i < TOTAL_MSG_COUNT; i++) {
            pubClient.publish("test/topic/" + random, Unpooled.wrappedBuffer(Integer.toString(i).getBytes(StandardCharsets.UTF_8)), pubQos).get(30, TimeUnit.SECONDS);
            Thread.sleep(500);
        }

        boolean await = receivedResponses.await(10, TimeUnit.SECONDS);
        log.error("The result of awaiting is: [{}]", await);

        //asserts
        assertEquals(TOTAL_MSG_COUNT, shareSubClient1ReceivedMessages.get() + shareSubClient2ReceivedMessages.get());

        //disconnect clients
        disconnectClient(pubClient);

        disconnectClient(shareSubClient1);
        disconnectClient(shareSubClient2);
    }

    @Test
    public void givenSharedSubsGroupWith2PersistentClientsAndQos2SubAndQos0Pub_whenClientsDisconnectedAndPubMsgToSharedTopic_thenConnectClientAndReceiveNoMessages() throws Throwable {
        CountDownLatch receivedResponses = new CountDownLatch(TOTAL_MSG_COUNT);

        AtomicInteger shareSubClient1ReceivedMessages = new AtomicInteger();
        AtomicInteger shareSubClient2ReceivedMessages = new AtomicInteger();

        //sub
        shareSubClient1 = getClient(getHandler(receivedResponses, shareSubClient1ReceivedMessages), false);
        shareSubClient2 = getClient(getHandler(receivedResponses, shareSubClient2ReceivedMessages), false);

        shareSubClient1.on("$share/g1/test/+/e", getHandler(receivedResponses, shareSubClient1ReceivedMessages), MqttQoS.EXACTLY_ONCE).get(30, TimeUnit.SECONDS);
        shareSubClient2.on("$share/g1/test/+/e", getHandler(receivedResponses, shareSubClient2ReceivedMessages), MqttQoS.EXACTLY_ONCE).get(30, TimeUnit.SECONDS);

        shareSubClient1.disconnect();
        shareSubClient2.disconnect();

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> {
            ClientSessionInfo clientSessionInfo1 = clientSessionService.getClientSessionInfo(shareSubClient1.getClientConfig().getClientId());
            ClientSessionInfo clientSessionInfo2 = clientSessionService.getClientSessionInfo(shareSubClient2.getClientConfig().getClientId());
            return !clientSessionInfo1.isConnected() && !clientSessionInfo2.isConnected();
        });

        //pub
        MqttClient pubClient = getMqttPubClient();
        for (int i = 0; i < TOTAL_MSG_COUNT; i++) {
            pubClient.publish("test/topic/e", Unpooled.wrappedBuffer(Integer.toString(i).getBytes(StandardCharsets.UTF_8)), MqttQoS.AT_MOST_ONCE).get(30, TimeUnit.SECONDS);
            Thread.sleep(500);
        }

        shareSubClient1.connect("localhost", mqttPort).get(30, TimeUnit.SECONDS);

        boolean await = receivedResponses.await(2, TimeUnit.SECONDS);
        log.error("The result of awaiting should be [false], actual is: [{}]", await);
        Assert.assertFalse(await);

        //asserts
        assertEquals(0, shareSubClient1ReceivedMessages.get() + shareSubClient2ReceivedMessages.get());

        //disconnect clients
        disconnectClient(pubClient);

        disconnectClient(shareSubClient1);
        disconnectClient(shareSubClient2);
    }

    @Test
    public void givenSharedSubsGroupWith2PersistentClientsAndQos0SubAndQos2Pub_whenClientsDisconnectedAndPubMsgToSharedTopic_thenConnectClientAndReceiveMessagesForPubQosNot0() throws Throwable {
        CountDownLatch receivedResponses = new CountDownLatch(TOTAL_MSG_COUNT);

        AtomicInteger shareSubClient1ReceivedMessages = new AtomicInteger();
        AtomicInteger shareSubClient2ReceivedMessages = new AtomicInteger();

        //sub
        shareSubClient1 = getClient(getHandler(receivedResponses, shareSubClient1ReceivedMessages), false);
        shareSubClient2 = getClient(getHandler(receivedResponses, shareSubClient2ReceivedMessages), false);

        shareSubClient1.on("$share/g1/test/+/d", getHandler(receivedResponses, shareSubClient1ReceivedMessages), MqttQoS.AT_MOST_ONCE).get(30, TimeUnit.SECONDS);
        shareSubClient2.on("$share/g1/test/+/d", getHandler(receivedResponses, shareSubClient2ReceivedMessages), MqttQoS.AT_MOST_ONCE).get(30, TimeUnit.SECONDS);

        shareSubClient1.disconnect();
        shareSubClient2.disconnect();

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> {
            ClientSessionInfo clientSessionInfo1 = clientSessionService.getClientSessionInfo(shareSubClient1.getClientConfig().getClientId());
            ClientSessionInfo clientSessionInfo2 = clientSessionService.getClientSessionInfo(shareSubClient2.getClientConfig().getClientId());
            return !clientSessionInfo1.isConnected() && !clientSessionInfo2.isConnected();
        });

        //pub
        MqttClient pubClient = getMqttPubClient();
        for (int i = 0; i < TOTAL_MSG_COUNT; i++) {
            pubClient.publish("test/topic/d", Unpooled.wrappedBuffer(Integer.toString(i).getBytes(StandardCharsets.UTF_8)), MqttQoS.EXACTLY_ONCE).get(30, TimeUnit.SECONDS);
            Thread.sleep(500);
        }

        shareSubClient1.connect("localhost", mqttPort).get(30, TimeUnit.SECONDS);

        boolean await = receivedResponses.await(2, TimeUnit.SECONDS);
        log.error("The result of awaiting should be [false], actual is: [{}]", await);

        //asserts
        assertEquals(0, shareSubClient1ReceivedMessages.get() + shareSubClient2ReceivedMessages.get());

        shareSubClient2.connect("localhost", mqttPort).get(30, TimeUnit.SECONDS);
        shareSubClient2.on("$share/g1/test/+/d", getHandler(receivedResponses, shareSubClient1ReceivedMessages), MqttQoS.AT_LEAST_ONCE).get(30, TimeUnit.SECONDS);

        receivedResponses = new CountDownLatch(TOTAL_MSG_COUNT);
        receivedResponses.await(5, TimeUnit.SECONDS);

        //asserts
        assertEquals(TOTAL_MSG_COUNT, shareSubClient1ReceivedMessages.get() + shareSubClient2ReceivedMessages.get());

        //disconnect clients
        disconnectClient(pubClient);

        disconnectClient(shareSubClient1);
        disconnectClient(shareSubClient2);
    }

    @Test
    public void givenSharedSubsGroupWith2PersistedClients_whenPubMsgToSharedTopic_thenReceiveAllMessages() throws Throwable {
        CountDownLatch receivedResponses = new CountDownLatch(TOTAL_MSG_COUNT);

        AtomicInteger shareSubClient1ReceivedMessages = new AtomicInteger();
        AtomicInteger shareSubClient2ReceivedMessages = new AtomicInteger();

        //sub
        shareSubClient1 = getClient(getHandler(receivedResponses, shareSubClient1ReceivedMessages), false);
        shareSubClient2 = getClient(getHandler(receivedResponses, shareSubClient2ReceivedMessages), false);

        shareSubClient1.on("$share/g1/test/+/c", getHandler(receivedResponses, shareSubClient1ReceivedMessages), MqttQoS.AT_LEAST_ONCE).get(30, TimeUnit.SECONDS);
        shareSubClient2.on("$share/g1/test/+/c", getHandler(receivedResponses, shareSubClient2ReceivedMessages), MqttQoS.AT_LEAST_ONCE).get(30, TimeUnit.SECONDS);

        //pub
        MqttClient pubClient = getMqttPubClient();
        for (int i = 0; i < TOTAL_MSG_COUNT; i++) {
            pubClient.publish("test/topic/c", Unpooled.wrappedBuffer(Integer.toString(i).getBytes(StandardCharsets.UTF_8)), MqttQoS.AT_LEAST_ONCE).get(30, TimeUnit.SECONDS);
            Thread.sleep(500);
        }

        boolean await = receivedResponses.await(10, TimeUnit.SECONDS);
        log.error("The result of awaiting is: [{}]", await);

        //asserts
        assertEquals(TOTAL_MSG_COUNT, shareSubClient1ReceivedMessages.get() + shareSubClient2ReceivedMessages.get());

        //disconnect clients
        disconnectClient(pubClient);

        disconnectClient(shareSubClient1);
        disconnectClient(shareSubClient2);
    }

    @Test
    public void givenSharedSubsGroupWith2ClientsAnd1NonSharedSubClientFromSameGroup_whenPubMsgToTopic_thenReceiveCorrectNumberOfMessages() throws Throwable {
        CountDownLatch receivedResponses = new CountDownLatch(TOTAL_MSG_COUNT + TOTAL_MSG_COUNT / 2);

        AtomicInteger shareSubClient1ReceivedMessages = new AtomicInteger();
        AtomicInteger shareSubClient2ReceivedMessages = new AtomicInteger();

        //sub
        MqttClient shareSubClient1 = getMqttSubClient(getHandler(shareSubClient1ReceivedMessages), "$share/g1/test/+/b");

        MqttHandler handler = getHandler(receivedResponses, shareSubClient2ReceivedMessages);
        MqttClient shareSubClient2 = getMqttSubClient(handler, "$share/g1/test/+/b");
        shareSubClient2.on("+/topic/b", handler, MqttQoS.AT_LEAST_ONCE).get(30, TimeUnit.SECONDS);

        //pub
        MqttClient pubClient = getMqttPubClient();
        for (int i = 0; i < TOTAL_MSG_COUNT; i++) {
            pubClient.publish("test/topic/b", Unpooled.wrappedBuffer(Integer.toString(i).getBytes(StandardCharsets.UTF_8)), MqttQoS.AT_LEAST_ONCE).get(30, TimeUnit.SECONDS);
            Thread.sleep(500);
        }

        boolean await = receivedResponses.await(10, TimeUnit.SECONDS);
        log.error("The result of awaiting is: [{}]", await);

        //asserts
        assertEquals(TOTAL_MSG_COUNT / 2, shareSubClient1ReceivedMessages.get());
        assertEquals(TOTAL_MSG_COUNT + TOTAL_MSG_COUNT / 2, shareSubClient2ReceivedMessages.get());

        //disconnect clients
        disconnectClient(pubClient);

        disconnectClient(shareSubClient1);
        disconnectClient(shareSubClient2);
    }


    @Test
    public void given2SharedSubsGroupsWith2ClientsAndSameGroupButDifferentTopicFiltersAnd1NonSharedSub_whenPubMsgToSharedTopic_thenReceiveCorrectNumberOfMessages() throws Throwable {
        process("$share/g1/+/topic/a", "$share/g1/test/+/a");
    }

    @Test
    public void given2SharedSubsGroupsWith2ClientsAndSameTopicFilterAnd1NonSharedSub_whenPubMsgToSharedTopic_thenReceiveCorrectNumberOfMessages() throws Throwable {
        process("$share/g1/test/+/a", "$share/g2/test/+/a");
    }

    @Test
    public void givenTwoPersistentClients_whenBothGotDisconnectedAndMessagesSent_thenBothConnectedAndFirstOneReceiveAllMessages() throws Throwable {
        CountDownLatch receivedResponses = new CountDownLatch(TOTAL_MSG_COUNT);

        AtomicInteger shareSubClient1ReceivedMessages = new AtomicInteger();
        AtomicInteger shareSubClient2ReceivedMessages = new AtomicInteger();

        //sub
        shareSubClient1 = getClient(getHandler(receivedResponses, shareSubClient1ReceivedMessages), false);
        shareSubClient2 = getClient(getHandler(receivedResponses, shareSubClient2ReceivedMessages), false);

        shareSubClient1.on("$share/g1/my/test/data", getHandler(receivedResponses, shareSubClient1ReceivedMessages), MqttQoS.AT_LEAST_ONCE).get(30, TimeUnit.SECONDS);
        shareSubClient2.on("$share/g1/my/test/data", getHandler(receivedResponses, shareSubClient2ReceivedMessages), MqttQoS.AT_LEAST_ONCE).get(30, TimeUnit.SECONDS);

        Awaitility
                .await()
                .atMost(5, TimeUnit.SECONDS)
                .until(() -> clientSubscriptionService.getClientSubscriptionsCount() == 2);

        //disconnect
        shareSubClient1.disconnect();
        shareSubClient2.disconnect();

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> {
            ClientSessionInfo clientSessionInfo1 = clientSessionService.getClientSessionInfo(shareSubClient1.getClientConfig().getClientId());
            ClientSessionInfo clientSessionInfo2 = clientSessionService.getClientSessionInfo(shareSubClient2.getClientConfig().getClientId());
            return !clientSessionInfo1.isConnected() && !clientSessionInfo2.isConnected();
        });

        //pub
        MqttClient pubClient = getMqttPubClient();
        for (int i = 0; i < TOTAL_MSG_COUNT; i++) {
            pubClient.publish("my/test/data", Unpooled.wrappedBuffer(Integer.toString(i).getBytes(StandardCharsets.UTF_8)), MqttQoS.AT_LEAST_ONCE).get(30, TimeUnit.SECONDS);
            Thread.sleep(500);
        }

        shareSubClient1.connect("localhost", mqttPort).get(30, TimeUnit.SECONDS);
        Thread.sleep(100);
        shareSubClient2.connect("localhost", mqttPort).get(30, TimeUnit.SECONDS);

        boolean await = receivedResponses.await(10, TimeUnit.SECONDS);
        log.error("The result of awaiting is: [{}]", await);

        //asserts
        assertEquals(TOTAL_MSG_COUNT, shareSubClient1ReceivedMessages.get());
        assertEquals(0, shareSubClient2ReceivedMessages.get());

        //disconnect clients
        disconnectClient(pubClient);

        disconnectClient(shareSubClient1);
        disconnectClient(shareSubClient2);
    }

    private void process(String group1TopicFilter, String group2TopicFilter) throws Exception {
        CountDownLatch receivedResponses = new CountDownLatch(TOTAL_MSG_COUNT * 3);

        AtomicInteger shareSubClient1Group1ReceivedMessages = new AtomicInteger();
        AtomicInteger shareSubClient2Group1ReceivedMessages = new AtomicInteger();

        AtomicInteger shareSubClient1Group2ReceivedMessages = new AtomicInteger();
        AtomicInteger shareSubClient2Group2ReceivedMessages = new AtomicInteger();

        AtomicInteger subClientReceivedMessages = new AtomicInteger();

        //sub
        MqttClient shareSubClient1Group1 = getMqttSubClient(getHandler(receivedResponses, shareSubClient1Group1ReceivedMessages), group1TopicFilter);
        MqttClient shareSubClient2Group1 = getMqttSubClient(getHandler(receivedResponses, shareSubClient2Group1ReceivedMessages), group1TopicFilter);

        MqttClient shareSubClient1Group2 = getMqttSubClient(getHandler(receivedResponses, shareSubClient1Group2ReceivedMessages), group2TopicFilter);
        MqttClient shareSubClient2Group2 = getMqttSubClient(getHandler(receivedResponses, shareSubClient2Group2ReceivedMessages), group2TopicFilter);

        MqttClient shareSubClient3 = getMqttSubClient(getHandler(receivedResponses, subClientReceivedMessages), "test/+/a");

        //pub
        MqttClient pubClient = getMqttPubClient();
        for (int i = 0; i < TOTAL_MSG_COUNT; i++) {
            pubClient.publish("test/topic/a", Unpooled.wrappedBuffer(Integer.toString(i).getBytes(StandardCharsets.UTF_8)), MqttQoS.AT_LEAST_ONCE).get(30, TimeUnit.SECONDS);
            Thread.sleep(500);
        }

        boolean await = receivedResponses.await(10, TimeUnit.SECONDS);
        log.error("The result of awaiting is: [{}]", await);

        //asserts
        assertEquals(TOTAL_MSG_COUNT, shareSubClient1Group1ReceivedMessages.get() + shareSubClient2Group1ReceivedMessages.get());
        assertEquals(TOTAL_MSG_COUNT, shareSubClient1Group2ReceivedMessages.get() + shareSubClient2Group2ReceivedMessages.get());
        assertEquals(TOTAL_MSG_COUNT, subClientReceivedMessages.get());

        //disconnect clients
        disconnectClient(pubClient);

        disconnectClient(shareSubClient1Group1);
        disconnectClient(shareSubClient2Group1);

        disconnectClient(shareSubClient1Group2);
        disconnectClient(shareSubClient2Group2);

        disconnectClient(shareSubClient3);
    }

    private MqttClient getMqttPubClient() throws Exception {
        return getClient(null);
    }

    private MqttClient getMqttSubClient(MqttHandler handler, String topic) throws Exception {
        MqttClient client = getClient(handler);
        client.on(topic, handler, MqttQoS.AT_LEAST_ONCE).get(30, TimeUnit.SECONDS);
        return client;
    }

    private MqttClient getClient(MqttHandler handler) throws Exception {
        return getClient(handler, true);
    }

    private MqttClient getClient(MqttHandler handler, boolean cleanSession) throws Exception {
        MqttClientConfig config = new MqttClientConfig();
        config.setCleanSession(cleanSession);
        config.setProtocolVersion(MqttVersion.MQTT_3_1_1);
        MqttClient client = MqttClient.create(config, handler, externalExecutorService);
        client.connect("localhost", mqttPort).get(30, TimeUnit.SECONDS);
        return client;
    }

    private MqttHandler getHandler(CountDownLatch latch, AtomicInteger integer) {
        return (s, byteBuf) -> {
            integer.incrementAndGet();
            latch.countDown();
            return Futures.immediateVoidFuture();
        };
    }

    private MqttHandler getHandler(AtomicInteger integer) {
        return (s, byteBuf) -> {
            integer.incrementAndGet();
            return Futures.immediateVoidFuture();
        };
    }

    private void disconnectClient(MqttClient client) {
        client.disconnect();
    }

}
