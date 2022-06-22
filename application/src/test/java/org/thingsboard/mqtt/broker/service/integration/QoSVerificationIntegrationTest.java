/**
 * Copyright © 2016-2020 The Thingsboard Authors
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
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = QoSVerificationIntegrationTest.class, loader = SpringBootContextLoader.class)
@DaoSqlTest
@RunWith(SpringRunner.class)
public class QoSVerificationIntegrationTest extends AbstractPubSubIntegrationTest {

    // TODO: 22/06/2022 implement qos tests

    @Test
    @Ignore
    public void test() throws Throwable {

        AtomicInteger counter = new AtomicInteger(0);
        CountDownLatch receivedResponses = new CountDownLatch(1);

        MqttClient subClient = new MqttClient("tcp://localhost:" + mqttPort, "test_sub_client" + UUID.randomUUID().toString().substring(0, 5));
//        subClient.setManualAcks(true);
        subClient.connect();
        subClient.subscribe("test", 1, (topic, message) -> {
            log.error("[{}] Received msg: {}", topic, message);

            counter.incrementAndGet();
            receivedResponses.countDown();
        });

        MqttClient pubClient = new MqttClient("tcp://localhost:" + mqttPort, "test_pub_client" + UUID.randomUUID().toString().substring(0, 5));
        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setCleanSession(true);
        pubClient.connect(connectOptions);
        pubClient.publish("test", "data".getBytes(), 1, false);

        boolean await = receivedResponses.await(1, TimeUnit.SECONDS);
        assertTrue(await);

        pubClient.close();
        subClient.close();

        assertThat(counter.get(), greaterThanOrEqualTo(1));
    }
}
