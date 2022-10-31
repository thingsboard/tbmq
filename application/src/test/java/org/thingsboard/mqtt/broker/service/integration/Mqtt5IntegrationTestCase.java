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

import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.eclipse.paho.mqttv5.client.IMqttMessageListener;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.util.MqttReasonCode;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = Mqtt5IntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "security.mqtt.basic.enabled=false"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class Mqtt5IntegrationTestCase extends AbstractPubSubIntegrationTest {

    static final String MY_TOPIC = "my/topic";

    @Before
    public void init() throws Exception {
    }

    @After
    public void clear() {
    }

    @Test
    public void testSendLastWillOnDisconnectMsgWithSpecialReasonCode() throws Throwable {
        CountDownLatch latch = new CountDownLatch(1);

        MqttClient subClient = new MqttClient("tcp://localhost:" + mqttPort, "subClientLastWill");
        subClient.connect();

        IMqttMessageListener[] listeners = {(topic, message) -> {
            log.error("[{}] Received msg: {}", topic, message.getProperties());
            Assert.assertEquals("will", new String(message.getPayload()));

            latch.countDown();
        }};

        MqttSubscription[] subscriptions = {new MqttSubscription(MY_TOPIC, 2)};
        subClient.subscribe(subscriptions, listeners);

        MqttAsyncClient pubClient = new MqttAsyncClient("tcp://localhost:" + mqttPort, "pubClientLastWill");

        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setWill(MY_TOPIC, new MqttMessage("will".getBytes(StandardCharsets.UTF_8), 2, false, MQTT_PROPERTIES));
        options.setWillMessageProperties(MQTT_PROPERTIES);
        pubClient.connect(options);

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .until(pubClient::isConnected);

        pubClient.disconnect(0, null, null, MqttReasonCode.DISCONNECT_WITH_WILL_MSG.value(), new MqttProperties());
        pubClient.close();

        boolean await = latch.await(10, TimeUnit.SECONDS);
        Assert.assertTrue(await);

        subClient.disconnect();
        subClient.close();
    }

}