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
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.service.limits.RateLimitCacheService;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Three retained-message contracts, one per phase: a retained set is delivered IN FULL whenever the shared bucket
 * can cover it, even when the node-local pool alone could not; it truncates only once the bucket is genuinely dry;
 * and that truncation is not a drop - no droppedMsgs, and the retain store is left intact for the next subscriber.
 * <p>
 * Arithmetic (capacity 20, refill 4 tokens/s, block size 1, lease return disabled). The 5 setup publishes each draw
 * a token inside the 50 ms pacing, so none is refused. Phase 1: one wildcard SUBSCRIBE is ONE bulk charge of 5, and
 * the local pool covers at most 2, so the rest can only come from drawing on the spot - without that, a subscriber
 * would silently get ~2 of 5 however much budget the cluster held. Phase 2: the bucket is drained, so the same
 * SUBSCRIBE must truncate. Phase 3: after a refill it is granted in full again, which is only possible if the store
 * survived.
 * <p>
 * The droppedMsgs ledger opens only once the store is populated: the setup publishes have no subscriber yet, and the
 * dispatcher counts a PUBLISH matching no subscription as dropped independently of the quota.
 */
@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = TotalThroughputQuotaRetainedIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "mqtt.rate-limits.total.config=20:5",
        "mqtt.rate-limits.total.block-size=1"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class TotalThroughputQuotaRetainedIntegrationTestCase extends AbstractTotalThroughputQuotaIntegrationTest {

    private static final String TOPIC_PREFIX = "quota/retained/";
    private static final String WILDCARD_FILTER = TOPIC_PREFIX + "#";
    private static final int RETAINED_COUNT = 5;

    @Test
    public void givenRetainedMessages_whenQuotaTruncates_thenNoDroppedMsgsAndStoreIntact() throws Throwable {
        double droppedBeforeSetupPublishes = droppedMsgs();
        MqttClient pub = new MqttClient(SERVER_URI + mqttPort, "quota_retained_pub");
        pub.connect(new MqttConnectOptions());
        for (int i = 0; i < RETAINED_COUNT; i++) {          // ingress: 5 paced charges, each covered by a fresh draw
            pub.publish(TOPIC_PREFIX + i, ("r_" + i).getBytes(), 1, true);
            Thread.sleep(50);
        }
        pub.disconnect();
        pub.close();
        // the setup publishes have no subscriber yet, and the dispatcher counts a PUBLISH matching no subscription
        // as dropped - accounting that predates the quota. Wait for exactly those five, then open the real ledger,
        // so the assertions below cover only the retained DELIVERY path.
        awaitDroppedMsgsAtLeast("retain store populated", droppedBeforeSetupPublishes + RETAINED_COUNT);
        double droppedBeforeRetainedDelivery = droppedMsgs();

        // phase 1 - one wildcard SUBSCRIBE is one bulk charge of 5 that outruns the node-local pool, so it is only
        // delivered in full if the shortfall is drawn from the shared bucket rather than destroyed
        AtomicInteger receivedA = new AtomicInteger();
        CountDownLatch latchA = new CountDownLatch(RETAINED_COUNT);
        MqttClient subA = new MqttClient(SERVER_URI + mqttPort, "quota_retained_sub_a");
        subA.connect(new MqttConnectOptions());
        subscribeWildcard(subA, countingListener(receivedA, latchA));
        assertTrue("a bulk retained charge the shared bucket can cover must be delivered in full, but got "
                + receivedA.get(), latchA.await(10, TimeUnit.SECONDS));
        Thread.sleep(500);
        assertEquals("with budget the retained set is delivered exactly once", RETAINED_COUNT, receivedA.get());
        disconnectAndClose(subA);

        // phase 2 - nothing left to draw, so the refusal is genuine and the set MUST truncate. How much survives
        // depends on the node-local pool at that instant, so only the truncation itself is asserted; the prefix
        // shape is pinned by MqttSubscribeHandlerTest. The drain asserts it took something, or an undrained bucket
        // delivering slowly would satisfy the negative check below for the wrong reason.
        drainSharedBucket("to force a genuine refusal");
        double droppedBeforeTruncation = droppedMsgs();
        AtomicInteger receivedB = new AtomicInteger();
        MqttClient subB = new MqttClient(SERVER_URI + mqttPort, "quota_retained_sub_b");
        subB.connect(new MqttConnectOptions());
        subscribeWildcard(subB, (t, m) -> receivedB.incrementAndGet());
        // fixed: a PARTIAL delivery of unknown size has no target count to poll for - only elapsed time can
        // establish that the rest is never coming
        Thread.sleep(2000);
        int truncated = receivedB.get();
        assertTrue("a dry shared bucket must truncate the retained set, but got " + truncated,
                truncated < RETAINED_COUNT);
        assertEquals("retained truncation must NOT report droppedMsgs",
                droppedBeforeTruncation, droppedMsgs(), 0.0);
        disconnectAndClose(subB);

        // phase 3 - the truncated messages were never consumed: after a refill the store still serves all five.
        // fixed: waits on the bucket refilling (20:5, so 5 s), and the shared balance cannot be read without
        // consuming the very budget this phase needs
        Thread.sleep(6000);
        AtomicInteger receivedC = new AtomicInteger();
        CountDownLatch latchC = new CountDownLatch(RETAINED_COUNT);
        MqttClient subC = new MqttClient(SERVER_URI + mqttPort, "quota_retained_sub_c");
        subC.connect(new MqttConnectOptions());
        subscribeWildcard(subC, countingListener(receivedC, latchC));
        assertTrue("retain store intact: the next subscriber must get everything, but got " + receivedC.get(),
                latchC.await(10, TimeUnit.SECONDS));
        Thread.sleep(500);
        assertEquals("retain store intact: the next subscriber gets everything", RETAINED_COUNT, receivedC.get());
        disconnectAndClose(subC);

        assertEquals("no phase of the retained delivery path may report droppedMsgs",
                droppedBeforeRetainedDelivery, droppedMsgs(), 0.0);
    }

    private IMqttMessageListener countingListener(AtomicInteger counter, CountDownLatch latch) {
        return (topic, msg) -> {
            counter.incrementAndGet();
            latch.countDown();
        };
    }

    private void subscribeWildcard(MqttClient client, IMqttMessageListener listener) throws Exception {
        client.subscribe(WILDCARD_FILTER, 1, listener);
    }

    private void disconnectAndClose(MqttClient client) throws Exception {
        if (client.isConnected()) {
            client.disconnect();
        }
        client.close();
    }
}
