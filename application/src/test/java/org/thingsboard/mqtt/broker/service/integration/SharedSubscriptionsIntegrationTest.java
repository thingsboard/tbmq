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
package org.thingsboard.mqtt.broker.service.integration;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.MqttClient;
import org.thingsboard.mqtt.MqttClientConfig;
import org.thingsboard.mqtt.MqttHandler;
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = SharedSubscriptionsIntegrationTest.class, loader = SpringBootContextLoader.class)
@DaoSqlTest
@RunWith(SpringRunner.class)
public class SharedSubscriptionsIntegrationTest extends AbstractPubSubIntegrationTest {

    static final int TOTAL_MSG_COUNT = 30;

    @After
    public void clear() {
    }

    // TODO: 09/08/2022 test persisted subscriptions, fix test below

//    @Test
//    public void test() throws Throwable {
//        CountDownLatch receivedResponses = new CountDownLatch(TOTAL_MSG_COUNT + TOTAL_MSG_COUNT / 2);
//
//        AtomicInteger shareSubClient1ReceivedMessages = new AtomicInteger();
//        AtomicInteger shareSubClient2ReceivedMessages = new AtomicInteger();
//
//        //sub
//        MqttClient shareSubClient1 = getMqttSubClient("test_share_client1", getHandler(shareSubClient1ReceivedMessages), "$share/g1/test/+");
//
//        MqttHandler handler = (s, byteBuf) -> {
//            shareSubClient2ReceivedMessages.incrementAndGet();
//            receivedResponses.countDown();
//        };
//        MqttClient shareSubClient2 = getMqttSubClient("test_share_client2", handler, "$share/g1/test/+");
//        shareSubClient2.on("+/topic", handler, MqttQoS.AT_LEAST_ONCE);
//
//        //pub
//        MqttClient pubClient = getMqttPubClient();
//        for (int i = 0; i < TOTAL_MSG_COUNT; i++) {
//            pubClient.publish("test/topic", Unpooled.wrappedBuffer(Integer.toString(i).getBytes(StandardCharsets.UTF_8)), MqttQoS.AT_MOST_ONCE);
//        }
//
//        boolean await = receivedResponses.await(10, TimeUnit.SECONDS);
//        log.error("The result of awaiting is: [{}]", await);
//
//        //asserts
//        assertEquals( TOTAL_MSG_COUNT / 2, shareSubClient1ReceivedMessages.get());
//        assertEquals(TOTAL_MSG_COUNT + TOTAL_MSG_COUNT / 2, shareSubClient2ReceivedMessages.get());
//
//        //disconnect clients
//        disconnectClient(pubClient);
//
//        disconnectClient(shareSubClient1);
//        disconnectClient(shareSubClient2);
//    }


    @Test
    public void given2SharedSubsGroupsWith2ClientsAndSameGroupButDifferentTopicFiltersAnd1NonSharedSub_whenPubMsgToSharedTopic_thenSuccess() throws Throwable {
        process("$share/g1/+/topic", "$share/g1/test/+");
    }

    @Test
    public void given2SharedSubsGroupsWith2ClientsAndSameTopicFilterAnd1NonSharedSub_whenPubMsgToSharedTopic_thenSuccess() throws Throwable {
        process("$share/g1/test/+", "$share/g2/test/+");
    }

    private void process(String group1TopicFilter, String group2TopicFilter) throws InterruptedException, ExecutionException {
        CountDownLatch receivedResponses = new CountDownLatch(TOTAL_MSG_COUNT);

        AtomicInteger shareSubClient1Group1ReceivedMessages = new AtomicInteger();
        AtomicInteger shareSubClient2Group1ReceivedMessages = new AtomicInteger();

        AtomicInteger shareSubClient1Group2ReceivedMessages = new AtomicInteger();
        AtomicInteger shareSubClient2Group2ReceivedMessages = new AtomicInteger();

        AtomicInteger subClientReceivedMessages = new AtomicInteger();

        //sub
        MqttClient shareSubClient1Group1 = getMqttSubClient("test_share_client1_g1", getHandler(shareSubClient1Group1ReceivedMessages), group1TopicFilter);
        MqttClient shareSubClient2Group1 = getMqttSubClient("test_share_client2_g1", getHandler(shareSubClient2Group1ReceivedMessages), group1TopicFilter);

        MqttClient shareSubClient1Group2 = getMqttSubClient("test_share_client1_g2", getHandler(shareSubClient1Group2ReceivedMessages), group2TopicFilter);
        MqttClient shareSubClient2Group2 = getMqttSubClient("test_share_client2_g2", getHandler(shareSubClient2Group2ReceivedMessages), group2TopicFilter);

        MqttClient shareSubClient3 = getMqttSubClient("test_sub_client", (s, byteBuf) -> {
            subClientReceivedMessages.incrementAndGet();
            receivedResponses.countDown();
        }, "test/+");

        //pub
        MqttClient pubClient = getMqttPubClient();
        for (int i = 0; i < TOTAL_MSG_COUNT; i++) {
            pubClient.publish("test/topic", Unpooled.wrappedBuffer(Integer.toString(i).getBytes(StandardCharsets.UTF_8)), MqttQoS.AT_MOST_ONCE);
        }

        boolean await = receivedResponses.await(10, TimeUnit.SECONDS);
        log.error("The result of awaiting is: [{}]", await);

        //asserts
        assertEquals(TOTAL_MSG_COUNT / 2, shareSubClient1Group1ReceivedMessages.get());
        assertEquals(TOTAL_MSG_COUNT / 2, shareSubClient2Group1ReceivedMessages.get());

        assertEquals(TOTAL_MSG_COUNT / 2, shareSubClient1Group2ReceivedMessages.get());
        assertEquals(TOTAL_MSG_COUNT / 2, shareSubClient2Group2ReceivedMessages.get());

        assertEquals(TOTAL_MSG_COUNT, subClientReceivedMessages.get());

        //disconnect clients
        disconnectClient(pubClient);

        disconnectClient(shareSubClient1Group1);
        disconnectClient(shareSubClient2Group1);

        disconnectClient(shareSubClient1Group2);
        disconnectClient(shareSubClient2Group2);

        disconnectClient(shareSubClient3);
    }

    private MqttClient getMqttPubClient() throws InterruptedException, ExecutionException {
        return getClient("test_pub_client", null);
    }

    private MqttClient getMqttSubClient(String clientId, MqttHandler handler, String topic) throws InterruptedException, ExecutionException {
        MqttClient client = getClient(clientId, handler);
        client.on(topic, handler, MqttQoS.AT_LEAST_ONCE);
        return client;
    }

    private MqttClient getClient(String clientId, MqttHandler handler) throws InterruptedException, ExecutionException {
        MqttClientConfig config = new MqttClientConfig();
        config.setClientId(clientId);
        MqttClient client = MqttClient.create(config, handler);
        client.connect("localhost", mqttPort).get();
        return client;
    }

    private MqttHandler getHandler(AtomicInteger integer) {
        return (s, byteBuf) -> integer.incrementAndGet();
    }

    private void disconnectClient(MqttClient client) {
        client.disconnect();
    }

}
