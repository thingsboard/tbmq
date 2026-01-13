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
package org.thingsboard.mqtt.broker.service.testing.integration.parent;

import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.eclipse.paho.mqttv5.client.IMqttMessageListener;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.jetbrains.annotations.NotNull;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.springframework.beans.factory.annotation.Autowired;
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.service.test.util.TestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public abstract class AbstractFlowControlIntegrationTestCase extends AbstractPubSubIntegrationTest {

    protected static final int QOS = 1;

    protected static final String FLOW_CONTROL_USER_NAME = "flowControlUn";
    protected static final String PUB_CLIENT = "pubClient";
    protected static final String RECEIVE_MAX_TOPIC = "receive/max/topic";

    protected static ExecutorService service;

    @Autowired
    private MqttClientCredentialsService credentialsService;

    private MqttClientCredentials clientCredentials;
    private MqttClient persistedClient;

    @BeforeClass
    public static void beforeClass() throws Exception {
        service = Executors.newSingleThreadExecutor();
    }

    @AfterClass
    public static void afterClass() throws Exception {
        service.shutdownNow();
    }

    protected void init(MqttClientCredentials rawClientCredentials) {
        clientCredentials = credentialsService.saveCredentials(rawClientCredentials);
    }

    protected void clear() throws Exception {
        log.warn("After test finish...");
        TestUtils.clearPersistedClient(persistedClient, getOptions(true, FLOW_CONTROL_USER_NAME));
        credentialsService.deleteCredentials(clientCredentials.getId());
    }

    protected void givenReceiveMaximumSetTo5_whenSend10MessagesWithoutAcks_thenReceive5Messages(String client) throws Throwable {
        CountDownLatch receivedResponses = new CountDownLatch(10);
        AtomicBoolean wasDisconnected = new AtomicBoolean(false);

        MqttConnectionOptions options = getOptions(false, FLOW_CONTROL_USER_NAME);
        options.setReceiveMaximum(5);

        persistedClient = new MqttClient(SERVER_URI + mqttPort, client);
        persistedClient.setManualAcks(true);

        persistedClient.connectWithResult(options);

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .until(persistedClient::isConnected);

        IMqttMessageListener[] listeners = {(topic, message) -> {
            int messageId = message.getId();
            log.warn("[{}] Received message for receiveMax subscriber", messageId);

            service.submit(() -> {
                try {
                    if (wasDisconnected.get()) {
                        persistedClient.messageArrivedComplete(messageId, QOS);
                        receivedResponses.countDown();
                    }
                } catch (Exception e) {
                    log.error("Failure", e);
                }
            });
        }};
        MqttSubscription[] subscriptions = {new MqttSubscription(RECEIVE_MAX_TOPIC, QOS)};
        persistedClient.subscribe(subscriptions, listeners);

        MqttClient publisher = new MqttClient(SERVER_URI + mqttPort, PUB_CLIENT);
        publisher.connectWithResult(getOptions(true, FLOW_CONTROL_USER_NAME));

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .until(publisher::isConnected);

        for (int i = 0; i < 10; i++) {
            publisher.publish(RECEIVE_MAX_TOPIC, PAYLOAD, QOS, false);
        }

        boolean await = receivedResponses.await(2, TimeUnit.SECONDS);
        Assert.assertFalse(await);

        Assert.assertEquals(10, receivedResponses.getCount());

        persistedClient.disconnect();
        wasDisconnected.set(true);

        persistedClient.connect(options);

        await = receivedResponses.await(5, TimeUnit.SECONDS);
        Assert.assertTrue(await);

        Assert.assertEquals(0, receivedResponses.getCount());

        publisher.disconnect();
        publisher.close();
    }

    protected MqttClient givenReceiveMaximumSetTo1_whenSend2Messages_thenReceiveMessagesWithDelay(String client, boolean subCleanStart) throws Throwable {
        List<Long> timestampsOfReceivedMessages = processTest(client, subCleanStart, 1);

        Assert.assertTrue(timestampsOfReceivedMessages.get(1) - timestampsOfReceivedMessages.get(0) > TimeUnit.SECONDS.toNanos(2));

        return persistedClient;
    }

    protected MqttClient givenReceiveMaximumSetTo2_whenSend2Messages_thenReceiveMessagesWithoutDelay(String client, boolean subCleanStart) throws Throwable {
        List<Long> timestampsOfReceivedMessages = processTest(client, subCleanStart, 2);

        Assert.assertTrue(timestampsOfReceivedMessages.get(1) - timestampsOfReceivedMessages.get(0) < TimeUnit.SECONDS.toNanos(2));

        return persistedClient;
    }

    @NotNull
    private List<Long> processTest(String client, boolean subCleanStart, int receiveMaximum) throws MqttException, InterruptedException {
        List<Long> timestampsOfReceivedMessages = new ArrayList<>(2);
        CountDownLatch receivedResponses = new CountDownLatch(2);

        MqttConnectionOptions options = getOptions(subCleanStart, FLOW_CONTROL_USER_NAME);
        options.setReceiveMaximum(receiveMaximum);

        persistedClient = new MqttClient(SERVER_URI + mqttPort, client);
        persistedClient.setManualAcks(true);
        persistedClient.connectWithResult(options);

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .until(persistedClient::isConnected);

        IMqttMessageListener[] listeners = {(topic, message) -> {
            int messageId = message.getId();
            log.warn("[{}] Received message for receiveMax subscriber", messageId);
            timestampsOfReceivedMessages.add(System.nanoTime());

            service.submit(() -> {
                try {
                    if (messageId == 1) {
                        Thread.sleep(2000);
                        persistedClient.messageArrivedComplete(messageId, QOS);
                    } else {
                        persistedClient.messageArrivedComplete(messageId, QOS);
                    }
                    receivedResponses.countDown();
                } catch (Exception e) {
                    log.error("Failure", e);
                }
            });
        }};
        MqttSubscription[] subscriptions = {new MqttSubscription(RECEIVE_MAX_TOPIC, QOS)};
        persistedClient.subscribe(subscriptions, listeners);

        MqttClient publisher = new MqttClient(SERVER_URI + mqttPort, PUB_CLIENT);
        publisher.connectWithResult(getOptions(true, FLOW_CONTROL_USER_NAME));

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .until(publisher::isConnected);

        for (int i = 0; i < 2; i++) {
            publisher.publish(RECEIVE_MAX_TOPIC, PAYLOAD, QOS, false);
        }

        boolean await = receivedResponses.await(5, TimeUnit.SECONDS);
        Assert.assertTrue(await);

        publisher.disconnect();
        publisher.close();
        return timestampsOfReceivedMessages;
    }

}
