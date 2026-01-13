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

import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.eclipse.paho.mqttv5.client.IMqttMessageListener;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.service.testing.integration.parent.AbstractFlowControlIntegrationTestCase;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = FlowControlIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "mqtt.max-in-flight-msgs=500",
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class FlowControlIntegrationTestCase extends AbstractFlowControlIntegrationTestCase {

    private static final String SUB_CLIENT = "receiveMaxSubClient";

    @Test
    public void givenConnectingClient_whenConnected_thenReceiveMaxFromServerReceived() throws Throwable {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setReceiveMaximum(100);

        MqttClient client = new MqttClient(SERVER_URI + mqttPort, SUB_CLIENT);
        IMqttToken iMqttToken = client.connectWithResult(options);

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .until(client::isConnected);

        MqttProperties responseProperties = iMqttToken.getResponseProperties();
        Assert.assertEquals(500, responseProperties.getReceiveMaximum().intValue());

        client.disconnect();
        client.close();
    }

    @Test
    public void givenReceiveMaximumSetTo5_whenSend10MessagesWithoutAcks_thenReceive5Messages() throws Throwable {
        CountDownLatch receivedResponses = new CountDownLatch(10);

        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setReceiveMaximum(5);

        MqttClient subscriber = new MqttClient(SERVER_URI + mqttPort, SUB_CLIENT);
        subscriber.setManualAcks(true);
        subscriber.connectWithResult(options);

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .until(subscriber::isConnected);

        IMqttMessageListener[] listeners = {(topic, message) -> {
            int messageId = message.getId();
            log.warn("[{}] Received message for receiveMax subscriber", messageId);

            service.submit(() -> {
                try {
                    if (messageId == 6) { // this if statement should never become true
                        for (int i = 1; i < 6; i++) {
                            subscriber.messageArrivedComplete(i, QOS);
                        }
                    }
                    receivedResponses.countDown();
                } catch (Exception e) {
                    log.error("Failure", e);
                }
            });
        }};
        MqttSubscription[] subscriptions = {new MqttSubscription(RECEIVE_MAX_TOPIC, QOS)};
        subscriber.subscribe(subscriptions, listeners);

        MqttClient publisher = new MqttClient(SERVER_URI + mqttPort, PUB_CLIENT);
        publisher.connectWithResult(new MqttConnectionOptions());

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .until(publisher::isConnected);

        for (int i = 0; i < 10; i++) {
            publisher.publish(RECEIVE_MAX_TOPIC, PAYLOAD, QOS, false);
        }

        boolean await = receivedResponses.await(3, TimeUnit.SECONDS);
        Assert.assertFalse(await);

        Assert.assertEquals(5, receivedResponses.getCount());

        publisher.disconnect();
        publisher.close();

        subscriber.disconnect();
        subscriber.close();
    }

    @Test
    public void givenReceiveMaximumSetTo1_whenSend2Messages_thenReceiveMessagesWithDelay() throws Throwable {
        MqttClient subscriber = super.givenReceiveMaximumSetTo1_whenSend2Messages_thenReceiveMessagesWithDelay(SUB_CLIENT, true);

        subscriber.disconnect();
        subscriber.close();
    }

    @Test
    public void givenReceiveMaximumSetTo2_whenSend2Messages_thenReceiveMessagesWithoutDelay() throws Throwable {
        MqttClient subscriber = super.givenReceiveMaximumSetTo2_whenSend2Messages_thenReceiveMessagesWithoutDelay(SUB_CLIENT, true);

        subscriber.disconnect();
        subscriber.close();
    }

}
