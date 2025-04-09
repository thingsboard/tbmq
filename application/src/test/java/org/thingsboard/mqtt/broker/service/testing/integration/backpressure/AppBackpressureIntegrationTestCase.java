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
package org.thingsboard.mqtt.broker.service.testing.integration.backpressure;

import com.google.common.util.concurrent.Futures;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttVersion;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.awaitility.Awaitility;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
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
import org.thingsboard.mqtt.broker.actors.client.messages.NonWritableChannelMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.WritableChannelMsg;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.ClientSubscriptionService;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.service.test.util.TestUtils;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = AppBackpressureIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "security.mqtt.basic.enabled=true",
        "listener.write_buffer_high_water_mark=64",
        "listener.write_buffer_low_water_mark=32"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class AppBackpressureIntegrationTestCase extends AbstractPubSubIntegrationTest {

    @Autowired
    private MqttClientCredentialsService credentialsService;
    @Autowired
    private ClientMqttActorManager clientMqttActorManager;
    @Autowired
    private ClientSubscriptionService clientSubscriptionService;

    private MqttClientCredentials applicationCredentials;
    private MqttClientCredentials defaultCredentials;
    private String appClientId;

    @Before
    public void init() throws Exception {
        appClientId = RandomStringUtils.randomAlphabetic(15);
        applicationCredentials = credentialsService.saveCredentials(TestUtils.createApplicationClientCredentials(appClientId, null));
        defaultCredentials = credentialsService.saveSystemWebSocketCredentials();
    }

    @After
    public void clear() throws Exception {
        credentialsService.deleteCredentials(applicationCredentials.getId());
        credentialsService.deleteCredentials(defaultCredentials.getId());
    }

    @Test
    public void testAppClientBackpressure() throws Throwable {
        int msgCount = 50;
        CountDownLatch latch = new CountDownLatch(msgCount);

        String backpressureTopic = "backpressure/topic";

        MqttClientConfig subscriberConfig = new MqttClientConfig();
        subscriberConfig.setCleanSession(false);
        subscriberConfig.setProtocolVersion(MqttVersion.MQTT_3_1_1);
        subscriberConfig.setClientId(appClientId);

        AtomicInteger counter = new AtomicInteger();
        MqttHandler mqttHandler = (topic, payload) -> {
            log.error("[{}] Received msg: {}", topic, counter.incrementAndGet());
            latch.countDown();
            return Futures.immediateVoidFuture();
        };
        MqttClient subscriber = MqttClient.create(subscriberConfig, mqttHandler, externalExecutorService);
        subscriber.connect("localhost", mqttPort).get(30, TimeUnit.SECONDS);
        subscriber.on(backpressureTopic, mqttHandler, MqttQoS.AT_LEAST_ONCE).get(30, TimeUnit.SECONDS);

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .until(() -> clientSubscriptionService.getClientSubscriptions(appClientId).size() == 1);

        org.eclipse.paho.mqttv5.client.MqttClient publisher = new org.eclipse.paho.mqttv5.client.MqttClient(SERVER_URI + mqttPort, "backpressurePublisher");
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setUserName("tbmq_websockets_username");
        publisher.connect(options);

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .until(publisher::isConnected);

        for (int i = 0; i < msgCount; i++) {
            if (i == msgCount / 2) {
                clientMqttActorManager.notifyChannelNonWritable(appClientId, NonWritableChannelMsg.DEFAULT);
            }
            publisher.publish(backpressureTopic, PAYLOAD, 1, false);
        }

        publisher.disconnect();
        publisher.close();

        clientMqttActorManager.notifyChannelWritable(appClientId, WritableChannelMsg.DEFAULT);

        boolean await = latch.await(30, TimeUnit.SECONDS);
        assertThat(await).isTrue();

        subscriber.disconnect();

        assertThat(counter).hasValue(msgCount);
    }

}
