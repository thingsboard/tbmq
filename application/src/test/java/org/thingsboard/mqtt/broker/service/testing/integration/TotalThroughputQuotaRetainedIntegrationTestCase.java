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
import org.thingsboard.mqtt.broker.service.limits.RateLimitCacheService;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.DROPPED_MSGS;

/**
 * Proves the three retained-message contracts of the total throughput quota: a retained set is delivered IN FULL
 * whenever the shared bucket holds the budget for it - even when the node-local pool alone could not cover it - it is
 * truncated only once the shared bucket is genuinely dry, and that truncation is not a drop: it reports no droppedMsgs
 * and leaves the retain store untouched, so the very same messages are re-sent to the next subscriber.
 * <p>
 * The first contract is the one that fails without a waiting draw on the retained path. A retained set is ONE bulk
 * charge, and a plain charge can never grant more than {@code localTokens + blockSize}, so a wildcard SUBSCRIBE used to
 * be capped at ~2 of the 5 messages here no matter how much budget the cluster held - silently, and identically for
 * every later subscriber, which made the remainder effectively unreachable.
 * <p>
 * Ledger arithmetic (capacity 20, full refill every 5 s = 4 tokens/s, block size 1, lease return disabled):
 * <ul>
 *     <li>boot: the warm-up draw takes one token, leaving local = 1;</li>
 *     <li>setup: 5 retained publishes 50 ms apart. Each ingress charge empties the node and triggers a draw of one
 *     token that lands well inside the pacing, so local stays at 1 and no publish is ever refused. The bucket keeps
 *     ample headroom - well above the 5 the first subscriber needs;</li>
 *     <li>phase 1: ONE wildcard SUBSCRIBE is ONE bulk charge of 5. The local pool covers at most local (&lt;= 1) plus
 *     one block of bounded credit, so the shortfall of ~3 can only be covered by drawing from the shared bucket on the
 *     spot. All five must arrive;</li>
 *     <li>phase 2: the shared bucket is drained directly through {@link RateLimitCacheService} - the cheapest faithful
 *     stand-in for a second cluster node having spent the budget, and it perturbs neither droppedMsgs nor the retain
 *     store. The next wildcard SUBSCRIBE now finds nothing to draw, so it must truncate;</li>
 *     <li>phase 3: a 6 s pause refills the bucket to capacity; the same wildcard SUBSCRIBE is granted in full again,
 *     which can only happen if the truncation left the retain store intact.</li>
 * </ul>
 * The droppedMsgs ledger is opened only once the retain store is populated: the five setup publishes have no subscriber
 * yet, and a PUBLISH matching no subscription is counted as dropped by the dispatcher independently of the quota.
 */
@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = TotalThroughputQuotaRetainedIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "mqtt.rate-limits.total.enabled=true",
        "mqtt.rate-limits.total.config=20:5",
        "mqtt.rate-limits.total.block-size=1",
        "mqtt.rate-limits.total.lease-return-ms=600000"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class TotalThroughputQuotaRetainedIntegrationTestCase extends AbstractPubSubIntegrationTest {

    private static final String TOPIC_PREFIX = "quota/retained/";
    private static final String WILDCARD_FILTER = TOPIC_PREFIX + "#";
    private static final int RETAINED_COUNT = 5;
    private static final long DRAIN_TOKENS = 10_000;

    @Autowired
    private MeterRegistry meterRegistry;
    @Autowired
    private RateLimitCacheService rateLimitCacheService;

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

        // phase 2 - with the shared bucket drained there is nothing left to draw, so the refusal is genuine and the
        // retained set MUST truncate. How much of the prefix survives depends on the node-local pool at that instant
        // (bounded credit may still cover a message or two), so only the truncation itself is asserted here; the
        // granted-prefix shape is pinned by MqttSubscribeHandlerTest#givenQuotaPartiallyGranted_...
        long drained = rateLimitCacheService.tryConsumeTotalMsgs(DRAIN_TOKENS);
        log.info("Drained {} tokens from the shared bucket to force a genuine refusal", drained);
        // without this the phase would still "pass" if the drain silently took nothing: the truncation assertion below
        // is a negative one, and an undrained bucket that simply delivered slowly would satisfy it for the wrong reason
        assertTrue("the drain must actually empty the shared bucket", drained > 0);
        double droppedBeforeTruncation = droppedMsgs();
        AtomicInteger receivedB = new AtomicInteger();
        MqttClient subB = new MqttClient(SERVER_URI + mqttPort, "quota_retained_sub_b");
        subB.connect(new MqttConnectOptions());
        subscribeWildcard(subB, (t, m) -> receivedB.incrementAndGet());
        Thread.sleep(2000);
        int truncated = receivedB.get();
        assertTrue("a dry shared bucket must truncate the retained set, but got " + truncated,
                truncated < RETAINED_COUNT);
        assertEquals("retained truncation must NOT report droppedMsgs",
                droppedBeforeTruncation, droppedMsgs(), 0.0);
        disconnectAndClose(subB);

        // phase 3 - the truncated messages were never consumed: after a refill the store still serves all five
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

    private double droppedMsgs() {
        return meterRegistry.get(DROPPED_MSGS).counter().count();
    }
}
