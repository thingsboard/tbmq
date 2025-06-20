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
package org.thingsboard.mqtt.broker.service.testing.integration;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.eclipse.paho.mqttv5.client.IMqttMessageListener;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgListenerService;
import org.thingsboard.mqtt.broker.service.test.util.TestUtils;
import org.thingsboard.mqtt.broker.service.testing.integration.parent.AbstractRequestResponseIntegrationTestCase;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = RequestResponseIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@DaoSqlTest
@RunWith(SpringRunner.class)
public class RequestResponseIntegrationTestCase extends AbstractRequestResponseIntegrationTestCase {

    @Autowired
    protected RetainedMsgListenerService retainedMsgListenerService;

    @Test
    public void givenRetainedMsgWithResponseTopicAndCorrelationData_whenSubscribeClient_thenReceiveMsgWithSpecifiedProperties() throws Throwable {
        CountDownLatch receivedResponses = new CountDownLatch(1);
        AtomicBoolean receivedMsg = new AtomicBoolean(false);

        connectPubClientSendMsgAndClose(CORRELATION_DATA, RESPONSE_TOPIC, true);

        MqttClient subClient = connectClientAndSubscribe(new MqttConnectionOptions(), receivedMsg, receivedResponses);

        boolean await = receivedResponses.await(5, TimeUnit.SECONDS);
        log.error("The result of awaiting of message receiving is: [{}]", await);

        TestUtils.disconnectAndCloseClient(subClient);

        retainedMsgListenerService.clearRetainedMsgAndPersist(TOPIC);

        assertTrue(receivedMsg.get());
    }

    @Test
    public void givenSubscribedClient_whenPubMsgWithResponseTopicAndCorrelationData_thenReceiveMsgWithSpecifiedProperties() throws Throwable {
        CountDownLatch receivedResponses = new CountDownLatch(1);
        AtomicBoolean receivedMsg = new AtomicBoolean(false);

        MqttClient subClient = connectClientAndSubscribe(new MqttConnectionOptions(), receivedMsg, receivedResponses);

        connectPubClientSendMsgAndClose(CORRELATION_DATA, RESPONSE_TOPIC, false);

        boolean await = receivedResponses.await(5, TimeUnit.SECONDS);
        log.error("The result of awaiting of message receiving is: [{}]", await);

        TestUtils.disconnectAndCloseClient(subClient);

        assertTrue(receivedMsg.get());
    }

    @Test
    public void givenSubscribedClient_whenPubMsgWithResponseTopic_thenReceiveMsgWithSpecifiedProperty() throws Throwable {
        CountDownLatch receivedResponses = new CountDownLatch(1);
        AtomicBoolean receivedMsg = new AtomicBoolean(false);

        MqttClient subClient = new MqttClient(SERVER_URI + mqttPort, RandomStringUtils.randomAlphabetic(10));
        subClient.connect();
        IMqttMessageListener[] listeners = {(topic, msg) -> {
            log.error("[{}] Received msg with id: {}", topic, msg.getId());
            assertEquals(RESPONSE_TOPIC, msg.getProperties().getResponseTopic());
            assertNull(msg.getProperties().getCorrelationData());
            receivedMsg.set(true);
            receivedResponses.countDown();
        }};
        MqttSubscription[] subscriptions = {new MqttSubscription(TOPIC, 1)};
        subClient.subscribe(subscriptions, listeners);

        connectPubClientSendMsgAndClose(null, RESPONSE_TOPIC, false);

        boolean await = receivedResponses.await(5, TimeUnit.SECONDS);
        log.error("The result of awaiting of message receiving is: [{}]", await);

        TestUtils.disconnectAndCloseClient(subClient);

        assertTrue(receivedMsg.get());
    }

    @Test
    public void givenSubscribedClient_whenPubMsgWithCorrelationData_thenReceiveMsgWithSpecifiedProperty() throws Throwable {
        CountDownLatch receivedResponses = new CountDownLatch(1);
        AtomicBoolean receivedMsg = new AtomicBoolean(false);

        MqttClient subClient = new MqttClient(SERVER_URI + mqttPort, RandomStringUtils.randomAlphabetic(10));
        subClient.connect();
        IMqttMessageListener[] listeners = {(topic, msg) -> {
            log.error("[{}] Received msg with id: {}", topic, msg.getId());
            assertNull(msg.getProperties().getResponseTopic());
            assertEquals(new String(CORRELATION_DATA, StandardCharsets.UTF_8), new String(msg.getProperties().getCorrelationData(), StandardCharsets.UTF_8));
            receivedMsg.set(true);
            receivedResponses.countDown();
        }};
        MqttSubscription[] subscriptions = {new MqttSubscription(TOPIC, 1)};
        subClient.subscribe(subscriptions, listeners);

        connectPubClientSendMsgAndClose(CORRELATION_DATA, null, false);

        boolean await = receivedResponses.await(5, TimeUnit.SECONDS);
        log.error("The result of awaiting of message receiving is: [{}]", await);

        TestUtils.disconnectAndCloseClient(subClient);

        assertTrue(receivedMsg.get());
    }

}
