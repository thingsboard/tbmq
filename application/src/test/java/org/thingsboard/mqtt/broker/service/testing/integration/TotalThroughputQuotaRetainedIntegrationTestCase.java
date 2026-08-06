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

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.DROPPED_MSGS;

/**
 * Proves the three retained-message contracts of the total throughput quota: retained delivery is complete while there
 * is budget, it is TRUNCATED to the granted prefix when there is not, and the truncation is not a drop - it reports no
 * droppedMsgs and leaves the retain store untouched, so the very same messages are re-sent to the next subscriber.
 * <p>
 * Ledger arithmetic (capacity 10, full refill every 5 s = 2 tokens/s, block size 1, lease return disabled):
 * <ul>
 *     <li>boot: the warm-up draw takes one token; the bucket is back at its capacity of 10 long before the test body
 *     starts, and local = 1;</li>
 *     <li>setup: 5 retained publishes 50 ms apart. Each ingress charge empties the node and triggers a draw of one
 *     token that lands well inside the pacing, so local stays at 1 and no publish is ever refused;</li>
 *     <li>phase 1: FIVE separate SUBSCRIBE packets, one per topic, 200 ms apart. Each is its own bulk charge of 1 that
 *     the standing token covers, so every retained message is delivered;</li>
 *     <li>phase 2: ONE wildcard SUBSCRIBE is ONE bulk {@code tryConsumeOutgoing(5)} settled by a single atomic CAS.
 *     Draws only ever add one token at a time here, so local can never exceed 1 and the grant is capped at
 *     local (<= 1) + bounded credit (one block = 1) = 2 &lt; 5 - the truncation is deterministic and does not depend on
 *     the bucket, which is still refilling. This is also why the brief's "first subscriber gets all five from one
 *     wildcard SUBSCRIBE" is unreachable and phase 1 subscribes per topic instead;</li>
 *     <li>phase 3: a 6 s pause refills the bucket to capacity; the five per-topic SUBSCRIBEs are granted again, which
 *     can only happen if truncation left the retain store intact.</li>
 * </ul>
 * The droppedMsgs ledger is opened only once the retain store is populated: the five setup publishes have no subscriber
 * yet, and a PUBLISH matching no subscription is counted as dropped by the dispatcher independently of the quota.
 */
@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = TotalThroughputQuotaRetainedIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "mqtt.rate-limits.total.enabled=true",
        "mqtt.rate-limits.total.config=10:5",
        "mqtt.rate-limits.total.block-size=1",
        "mqtt.rate-limits.total.lease-return-ms=600000"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class TotalThroughputQuotaRetainedIntegrationTestCase extends AbstractPubSubIntegrationTest {

    private static final String TOPIC_PREFIX = "quota/retained/";
    private static final String WILDCARD_FILTER = TOPIC_PREFIX + "#";
    private static final int RETAINED_COUNT = 5;
    private static final long SUBSCRIBE_PACING_MS = 200;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    public void givenRetainedMessages_whenQuotaTruncates_thenNoDroppedMsgsAndStoreIntact() throws Throwable {
        MqttClient pub = new MqttClient(SERVER_URI + mqttPort, "quota_retained_pub");
        pub.connect(new MqttConnectOptions());
        for (int i = 0; i < RETAINED_COUNT; i++) {          // ingress: 5 paced charges, each covered by a fresh draw
            pub.publish(TOPIC_PREFIX + i, ("r_" + i).getBytes(), 1, true);
            Thread.sleep(50);
        }
        pub.disconnect();
        pub.close();
        // let the dispatcher finish the five publishes before the baseline is taken: none of them has a subscriber yet,
        // and MsgDispatcherServiceImpl#processPublishMsg counts a PUBLISH with no matching subscription as a dropped
        // message. That accounting predates the quota and is not what this test measures, so the ledger below starts
        // once the retain store is populated and covers only the retained DELIVERY path.
        Thread.sleep(1000);
        // the historical droppedMsgs counter is zeroed by the reporting scheduler; this Micrometer counter is monotonic
        double droppedBeforeRetainedDelivery = droppedMsgs();

        // phase 1 - with budget, retained delivery is complete: five SUBSCRIBEs, five bulk charges of 1, five messages
        AtomicInteger receivedA = new AtomicInteger();
        CountDownLatch latchA = new CountDownLatch(RETAINED_COUNT);
        MqttClient subA = new MqttClient(SERVER_URI + mqttPort, "quota_retained_sub_a");
        subA.connect(new MqttConnectOptions());
        subscribePerTopic(subA, (t, m) -> {
            receivedA.incrementAndGet();
            latchA.countDown();
        });
        assertTrue("with budget every retained message must be delivered, but got " + receivedA.get(),
                latchA.await(10, TimeUnit.SECONDS));
        Thread.sleep(500);
        assertEquals("with budget the retained set is delivered exactly once", RETAINED_COUNT, receivedA.get());
        disconnectAndClose(subA);

        // phase 2 - one wildcard SUBSCRIBE is one bulk charge of 5 capped at local + one block, so it MUST truncate
        double droppedBeforeTruncation = droppedMsgs();
        AtomicInteger receivedB = new AtomicInteger();
        MqttClient subB = new MqttClient(SERVER_URI + mqttPort, "quota_retained_sub_b");
        subB.connect(new MqttConnectOptions());
        subB.subscribe(WILDCARD_FILTER, 1, (t, m) -> receivedB.incrementAndGet());
        Thread.sleep(2000);
        int truncated = receivedB.get();
        assertTrue("the wildcard SUBSCRIBE must be truncated by the quota, but got " + truncated,
                truncated < RETAINED_COUNT);
        assertTrue("truncation keeps the granted prefix rather than refusing everything, but got " + truncated,
                truncated >= 1);
        assertEquals("retained truncation must NOT report droppedMsgs",
                droppedBeforeTruncation, droppedMsgs(), 0.0);
        disconnectAndClose(subB);

        // phase 3 - the truncated messages were never consumed: after a refill the store still serves all five
        Thread.sleep(6000);
        AtomicInteger receivedC = new AtomicInteger();
        CountDownLatch latchC = new CountDownLatch(RETAINED_COUNT);
        MqttClient subC = new MqttClient(SERVER_URI + mqttPort, "quota_retained_sub_c");
        subC.connect(new MqttConnectOptions());
        subscribePerTopic(subC, (t, m) -> {
            receivedC.incrementAndGet();
            latchC.countDown();
        });
        assertTrue("retain store intact: the next subscriber must get everything, but got " + receivedC.get(),
                latchC.await(10, TimeUnit.SECONDS));
        Thread.sleep(500);
        assertEquals("retain store intact: the next subscriber gets everything", RETAINED_COUNT, receivedC.get());
        disconnectAndClose(subC);

        assertEquals("no phase of the retained delivery path may report droppedMsgs",
                droppedBeforeRetainedDelivery, droppedMsgs(), 0.0);
    }

    private void subscribePerTopic(MqttClient client, IMqttMessageListener listener) throws Exception {
        for (int i = 0; i < RETAINED_COUNT; i++) {
            client.subscribe(TOPIC_PREFIX + i, 1, listener);
            Thread.sleep(SUBSCRIBE_PACING_MS); // one bulk charge of 1 per packet, paced so each draw lands in between
        }
    }

    private void disconnectAndClose(MqttClient client) throws Exception {
        if (client.isConnected()) {
            client.disconnect();
        }
        client.close();
    }

    private double droppedMsgs() {
        return meterRegistry.get(DROPPED_MSGS).counter().count();
    }
}
