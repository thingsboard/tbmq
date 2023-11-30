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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.service.integration.parent.AbstractPayloadFormatAndContentTypesIntegrationTestCase;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgListenerService;
import org.thingsboard.mqtt.broker.service.test.util.TestUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = PayloadFormatAndContentTypesIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "security.mqtt.basic.enabled=false"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class PayloadFormatAndContentTypesIntegrationTestCase extends AbstractPayloadFormatAndContentTypesIntegrationTestCase {

    @Autowired
    protected RetainedMsgListenerService retainedMsgListenerService;

    @Test
    public void givenRetainedMsgWithPayloadFormatAndContentType_whenSubscribeClient_thenReceiveMsgWithSpecifiedProperties() throws Throwable {
        CountDownLatch receivedResponses = new CountDownLatch(1);
        AtomicBoolean receivedMsg = new AtomicBoolean(false);

        connectPubClientSendMsgAndClose(true, CONTENT_TYPE, true);

        MqttClient subClient = connectClientAndSubscribe(new MqttConnectionOptions(), receivedMsg, receivedResponses);

        boolean await = receivedResponses.await(5, TimeUnit.SECONDS);
        log.error("The result of awaiting of message receiving is: [{}]", await);

        TestUtils.disconnectAndCloseClient(subClient);

        retainedMsgListenerService.clearRetainedMsgAndPersist(TOPIC);

        assertTrue(receivedMsg.get());
    }

    @Test
    public void givenSubscribedClient_whenPubMsgWithPayloadFormatAndContentType_thenReceiveMsgWithSpecifiedProperties() throws Throwable {
        CountDownLatch receivedResponses = new CountDownLatch(1);
        AtomicBoolean receivedMsg = new AtomicBoolean(false);

        MqttClient subClient = connectClientAndSubscribe(new MqttConnectionOptions(), receivedMsg, receivedResponses);

        connectPubClientSendMsgAndClose(true, CONTENT_TYPE, false);

        boolean await = receivedResponses.await(5, TimeUnit.SECONDS);
        log.error("The result of awaiting of message receiving is: [{}]", await);

        TestUtils.disconnectAndCloseClient(subClient);

        assertTrue(receivedMsg.get());
    }

    @Test
    public void givenSubscribedClient_whenPubMsgWithContentType_thenReceiveMsgWithSpecifiedProperty() throws Throwable {
        CountDownLatch receivedResponses = new CountDownLatch(1);
        AtomicBoolean receivedMsg = new AtomicBoolean(false);

        MqttClient subClient = new MqttClient(SERVER_URI + mqttPort, RandomStringUtils.randomAlphabetic(10));
        subClient.connect();
        IMqttMessageListener[] listeners = {(topic, msg) -> {
            log.error("[{}] Received msg with id: {}", topic, msg.getId());
            assertEquals(CONTENT_TYPE, msg.getProperties().getContentType());
            assertFalse(msg.getProperties().getPayloadFormat());
            receivedMsg.set(true);
            receivedResponses.countDown();
        }};
        MqttSubscription[] subscriptions = {new MqttSubscription(TOPIC, 1)};
        subClient.subscribe(subscriptions, listeners);

        connectPubClientSendMsgAndClose(false, CONTENT_TYPE, false);

        boolean await = receivedResponses.await(5, TimeUnit.SECONDS);
        log.error("The result of awaiting of message receiving is: [{}]", await);

        TestUtils.disconnectAndCloseClient(subClient);

        assertTrue(receivedMsg.get());
    }

    @Test
    public void givenSubscribedClient_whenPubMsgWithPayloadFormat_thenReceiveMsgWithSpecifiedProperty() throws Throwable {
        CountDownLatch receivedResponses = new CountDownLatch(1);
        AtomicBoolean receivedMsg = new AtomicBoolean(false);

        MqttClient subClient = new MqttClient(SERVER_URI + mqttPort, RandomStringUtils.randomAlphabetic(10));
        subClient.connect();
        IMqttMessageListener[] listeners = {(topic, msg) -> {
            log.error("[{}] Received msg with id: {}", topic, msg.getId());
            assertNull(msg.getProperties().getContentType());
            assertTrue(msg.getProperties().getPayloadFormat());
            receivedMsg.set(true);
            receivedResponses.countDown();
        }};
        MqttSubscription[] subscriptions = {new MqttSubscription(TOPIC, 1)};
        subClient.subscribe(subscriptions, listeners);

        connectPubClientSendMsgAndClose(true, null, false);

        boolean await = receivedResponses.await(5, TimeUnit.SECONDS);
        log.error("The result of awaiting of message receiving is: [{}]", await);

        TestUtils.disconnectAndCloseClient(subClient);

        assertTrue(receivedMsg.get());
    }

}
