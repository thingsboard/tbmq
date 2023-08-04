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
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.junit.After;
import org.junit.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.common.data.MqttQoS;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.service.test.util.TestUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.Assert.assertThat;

@Slf4j
public abstract class AbstractQoSVerificationIntegrationTestCase extends AbstractPubSubIntegrationTest {

    protected static final int QOS_1 = MqttQoS.AT_LEAST_ONCE.value();
    protected static final int QOS_2 = MqttQoS.EXACTLY_ONCE.value();

    @Autowired
    private MqttClientCredentialsService credentialsService;

    protected MqttClientCredentials applicationCredentials;
    protected MqttClientCredentials deviceCredentials;

    @Before
    public void init() {
        log.warn("Before test start...");
        applicationCredentials = credentialsService.saveCredentials(
                TestUtils.createApplicationClientCredentials("test_sub_client", null)
        );
        deviceCredentials = credentialsService.saveCredentials(
                TestUtils.createDeviceClientCredentials("test_pub_client", null)
        );
    }

    @After
    public void clear() {
        log.warn("After test finish...");
        credentialsService.deleteCredentials(applicationCredentials.getId());
        credentialsService.deleteCredentials(deviceCredentials.getId());
    }

    protected void process(int qos, boolean subscriberCleanSession) throws MqttException, InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);
        CountDownLatch receivedResponses = new CountDownLatch(2);

        MqttClient subClient = new MqttClient(SERVER_URI + mqttPort, "test_sub_client");
        subClient.setManualAcks(true);
        MqttConnectOptions subConnectOptions = new MqttConnectOptions();
        subConnectOptions.setCleanSession(subscriberCleanSession);
        subClient.connect(subConnectOptions);
        subClient.subscribe("test", qos, (topic, message) -> {
            log.error("[{}] Received msg with id: {}, isDup: {}", topic, message.getId(), message.isDuplicate());

            counter.incrementAndGet();
            receivedResponses.countDown();
        });

        MqttClient pubClient = new MqttClient(SERVER_URI + mqttPort, "test_pub_client");
        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setCleanSession(true);
        pubClient.connect(connectOptions);
        pubClient.publish("test", "data".getBytes(), qos, false);

        boolean await = receivedResponses.await(10, TimeUnit.SECONDS);
        log.error("The result of awaiting is: [{}]", await);

        subClient.messageArrivedComplete(1, qos);

        pubClient.disconnect();
        subClient.disconnect();
        pubClient.close();
        subClient.close();

        assertThat(counter.get(), greaterThanOrEqualTo(2));
    }
}
