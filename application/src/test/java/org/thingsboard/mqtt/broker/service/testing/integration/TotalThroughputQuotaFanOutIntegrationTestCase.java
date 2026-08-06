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

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertTrue;

/**
 * Proves the packet-accurate fan-out arithmetic: one publish delivered to N subscribers costs N+1 quota tokens.
 * <p>
 * The block size is pinned to 2 rather than 1: with a single-token block the two egress charges of the same message
 * land microseconds apart, so the second charge always arrives while the first one's async draw is still in flight
 * and gets refused - a deterministic starvation that says nothing about the arithmetic under test.
 */
@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = TotalThroughputQuotaFanOutIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "mqtt.rate-limits.total.enabled=true",
        "mqtt.rate-limits.total.config=9:600",
        "mqtt.rate-limits.total.block-size=2",
        "mqtt.rate-limits.total.lease-return-ms=600000"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class TotalThroughputQuotaFanOutIntegrationTestCase extends AbstractPubSubIntegrationTest {

    @Test
    public void givenTwoSubscribers_whenPublishing_thenChargedNPlusOnePerMsgAndBurstDeliveredInFull() throws Throwable {
        AtomicInteger receivedA = new AtomicInteger();
        AtomicInteger receivedB = new AtomicInteger();
        CountDownLatch firstThree = new CountDownLatch(6);

        MqttClient subA = new MqttClient(SERVER_URI + mqttPort, "quota_fanout_sub_a");
        subA.connect(new MqttConnectOptions());
        subA.subscribe("quota/fanout", 0, (t, m) -> {
            receivedA.incrementAndGet();
            firstThree.countDown();
        });
        MqttClient subB = new MqttClient(SERVER_URI + mqttPort, "quota_fanout_sub_b");
        subB.connect(new MqttConnectOptions());
        subB.subscribe("quota/fanout", 0, (t, m) -> {
            receivedB.incrementAndGet();
            firstThree.countDown();
        });

        MqttClient pub = new MqttClient(SERVER_URI + mqttPort, "quota_fanout_pub");
        MqttConnectOptions pubOptions = new MqttConnectOptions();
        pubOptions.setAutomaticReconnect(false);
        pub.connect(pubOptions);

        // each publish costs 1 (ingress) + 2 (egress) = 3; capacity 9 covers exactly 3 messages
        for (int i = 0; i < 3; i++) {
            pub.publish("quota/fanout", ("burst_" + i).getBytes(), 1, false);
            Thread.sleep(100);
        }
        assertTrue("burst within capacity must be delivered in full", firstThree.await(10, TimeUnit.SECONDS));

        // Message 4 exceeds the budget. Per-node over-delivery can never pass capacity(9) + blockSize(2) = 11 tokens
        // and the burst already spent 9, so message 4 can win at most 2 of them. Ingress is charged first and always
        // takes one, which leaves at most ONE subscriber copy - and whether even that one lands depends on the fixed
        // 50 ms dry backoff racing the Kafka consumer hop. The arithmetic is therefore asserted as a bound.
        try {
            pub.publish("quota/fanout", "over".getBytes(), 1, false);
        } catch (Exception e) {
            log.info("Publisher refused/disconnected on over-budget publish", e);
        }
        Thread.sleep(1000);

        int a = receivedA.get();
        int b = receivedB.get();
        assertTrue("subscriber A must keep the full in-budget burst, but received " + a, a >= 3);
        assertTrue("subscriber B must keep the full in-budget burst, but received " + b, b >= 3);
        assertTrue("the over-budget message may add at most one subscriber copy (6 in-budget deliveries + 1), " +
                "but total deliveries were " + (a + b), a + b <= 7);

        // the over-budget publisher may or may not have been disconnected by the broker, and close() rejects a
        // still-connected client, so the connection state is checked rather than asserted
        for (MqttClient c : List.of(subA, subB, pub)) {
            if (c.isConnected()) {
                c.disconnect();
            }
            c.close();
        }
    }
}
