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
package org.thingsboard.mqtt.broker.service.integration;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = RateLimitsIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "mqtt.rate-limits.incoming-publish.enabled=true",
        "mqtt.rate-limits.incoming-publish.client-config=100:1,300:60",
        "mqtt.rate-limits.outgoing-publish.enabled=true",
        "mqtt.rate-limits.outgoing-publish.client-config=10:1,300:60"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class RateLimitsIntegrationTestCase extends AbstractPubSubIntegrationTest {

    @Test
    public void givenPublisher_whenRateLimitsDetected_thenGotDisconnected() throws Throwable {
        MqttClient pubClient = new MqttClient(SERVER_URI + mqttPort, "test_rate_limits");
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(false);
        pubClient.connect(options);

        for (int i = 0; i < 500; i++) {
            try {
                pubClient.publish("test/rate/limits", ("data_" + i).getBytes(), 1, false);
            } catch (Exception e) {
                log.error("Failed to publish msg", e);
                break;
            }
        }

        assertFalse(pubClient.isConnected());
        pubClient.close();
    }

    @Test
    public void givenNonPersistentSubscriberWithQos0_whenRateLimitsDetected_thenGotDisconnected() throws Throwable {
        CountDownLatch latch = new CountDownLatch(50);

        MqttClient subClient = new MqttClient(SERVER_URI + mqttPort, "subscriber_rate_limits");
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(false);
        subClient.connect(options);
        subClient.subscribe("outgoing/rate/limits", 0, (topic, message) -> latch.countDown());

        MqttClient pubClient = new MqttClient(SERVER_URI + mqttPort, "publisher_rate_limits");
        pubClient.connect();

        for (int i = 0; i < 50; i++) {
            pubClient.publish("outgoing/rate/limits", ("data_" + i).getBytes(), 0, false);
        }

        latch.await(2, TimeUnit.SECONDS);

        assertFalse(subClient.isConnected());

        pubClient.disconnect();
        pubClient.close();

        subClient.close();
    }

}
