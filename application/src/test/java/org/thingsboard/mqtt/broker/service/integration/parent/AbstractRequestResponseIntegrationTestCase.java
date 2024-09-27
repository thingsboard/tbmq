/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.integration.parent;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.eclipse.paho.mqttv5.client.IMqttMessageListener;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.service.test.util.TestUtils;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;

@Slf4j
public abstract class AbstractRequestResponseIntegrationTestCase extends AbstractPubSubIntegrationTest {

    protected static final String RESPONSE_TOPIC = "response/topic";
    protected static final byte[] CORRELATION_DATA = BrokerConstants.DUMMY_PAYLOAD;
    protected static final String TOPIC = "request/response/topic";
    protected static final String REQUEST_RESPONSE_USER_NAME = "requestResponseUn";

    @Autowired
    private MqttClientCredentialsService credentialsService;

    private MqttClientCredentials clientCredentials;
    private MqttClient persistedClient;

    protected void init(MqttClientCredentials rawClientCredentials) {
        clientCredentials = credentialsService.saveCredentials(rawClientCredentials);
    }

    protected void clear() throws Exception {
        log.warn("After test finish...");
        TestUtils.clearPersistedClient(persistedClient, getOptions(REQUEST_RESPONSE_USER_NAME));
        credentialsService.deleteCredentials(clientCredentials.getId());
    }

    protected MqttClient connectClientAndSubscribe(MqttConnectionOptions connectionOptions,
                                                   AtomicBoolean receivedMsg,
                                                   CountDownLatch receivedResponses) throws MqttException {
        persistedClient = new MqttClient(SERVER_URI + mqttPort, RandomStringUtils.randomAlphabetic(10));
        persistedClient.connect(connectionOptions);
        IMqttMessageListener[] listeners = {(topic, msg) -> {
            log.error("[{}] Received msg with id: {}", topic, msg.getId());
            assertEquals(RESPONSE_TOPIC, msg.getProperties().getResponseTopic());
            assertEquals(new String(CORRELATION_DATA, StandardCharsets.UTF_8), new String(msg.getProperties().getCorrelationData(), StandardCharsets.UTF_8));
            receivedMsg.set(true);
            receivedResponses.countDown();
        }};
        MqttSubscription[] subscriptions = {new MqttSubscription(TOPIC, 1)};
        persistedClient.subscribe(subscriptions, listeners);
        return persistedClient;
    }

    protected void connectPubClientSendMsgAndClose(byte[] correlationData, String responseTopic, boolean retained) throws MqttException {
        MqttClient pubClient = new MqttClient(SERVER_URI + mqttPort, RandomStringUtils.randomAlphabetic(10));
        pubClient.connect(getOptions(REQUEST_RESPONSE_USER_NAME));

        MqttProperties properties = getMqttProperties(correlationData, responseTopic);
        MqttMessage mqttMessage = new MqttMessage(PAYLOAD, 1, retained, properties);
        pubClient.publish(TOPIC, mqttMessage);
        TestUtils.disconnectAndCloseClient(pubClient);
    }

    @NotNull
    protected MqttProperties getMqttProperties(byte[] correlationData, String responseTopic) {
        MqttProperties properties = new MqttProperties();
        properties.setCorrelationData(correlationData);
        properties.setResponseTopic(responseTopic);
        return properties;
    }

}
