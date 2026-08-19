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
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Ingress runs on the Netty event loop, so it can never wait for Redis: on a local miss it refuses at once and only
 * schedules a draw. That makes the node's credit floor - not the licensed rate - its instantaneous admission limit,
 * and a burst wider than that floor used to be refused however much budget the cluster still held. Field reports of
 * drops at a fifth of the configured rate came from exactly this, with devices on aligned reporting intervals
 * arriving together.
 * <p>
 * The unit test pins the arithmetic; this pins the behaviour end to end, over a real broker and a real shared bucket.
 * <p>
 * Arithmetic (capacity 200 over 5 s, so a sustained 40/s, block size 4, lease return pushed past the run): the
 * warm-up draw leaves local = 4 and the burst charges 1 for ingress plus 1 for the subscriber, 24 in all against a
 * bucket holding 200. Nothing here is short of budget - only of local credit. With the floor at blockSize the node
 * had 4 local plus 4 of credit and refused the rest of the burst; with the floor at one second of the configured
 * rate it absorbs the burst and settles the debt on the next draw.
 * <p>
 * Both the concurrency and the QoS are load-bearing. One client publishing in a loop does NOT reproduce this: even
 * at QoS 0 the packets are paced by the socket, a draw lands between them and credit never runs out - such a test
 * passes with the defect still in place. The burst has to arrive from separate connections released together, which
 * is what devices on aligned reporting intervals do. QoS 1 would reintroduce the pacing by blocking on each PUBACK.
 */
@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = TotalThroughputQuotaBurstIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "mqtt.rate-limits.total.config=200:5",
        "mqtt.rate-limits.total.block-size=4"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class TotalThroughputQuotaBurstIntegrationTestCase extends AbstractTotalThroughputQuotaIntegrationTest {

    private static final String TOPIC = "quota/burst";
    private static final int BURST = 12; // wider than the block, well inside both the credit floor and the bucket

    private final List<MqttClient> publishers = new ArrayList<>();
    private MqttClient sub;

    @After
    public void clear() throws Exception {
        for (MqttClient publisher : publishers) {
            disconnectAndClose(publisher);
        }
        disconnectAndClose(sub);
    }

    @Test
    public void givenABurstWiderThanTheBlock_whenTheBucketHasBudget_thenNothingIsRefused() throws Throwable {
        AtomicInteger received = new AtomicInteger();
        sub = new MqttClient(SERVER_URI + mqttPort, "quota_burst_sub");
        sub.connect(new MqttConnectOptions());
        sub.subscribe(TOPIC, 0, (topic, msg) -> received.incrementAndGet());

        for (int i = 0; i < BURST; i++) {
            MqttClient publisher = new MqttClient(SERVER_URI + mqttPort, "quota_burst_pub_" + i);
            publisher.connect(new MqttConnectOptions());
            publishers.add(publisher);
        }

        double droppedBefore = droppedMsgs();

        // every publisher parks on the same latch, so the whole burst hits the node inside one draw round-trip
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch published = new CountDownLatch(BURST);
        ExecutorService pool = Executors.newFixedThreadPool(BURST);
        try {
            for (int i = 0; i < BURST; i++) {
                MqttClient publisher = publishers.get(i);
                byte[] payload = ("b_" + i).getBytes();
                pool.submit(() -> {
                    start.await();
                    publisher.publish(TOPIC, payload, 0, false);
                    published.countDown();
                    return null;
                });
            }
            start.countDown();
            assertTrue("every publisher must get its packet out", published.await(AWAIT_TIMEOUT_SEC, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        awaitReceived("burst delivered in full", received, BURST);
        assertNothingMoreArrives("the burst is delivered exactly once", received, BURST);
        assertEquals("a burst the cluster has budget for must not be refused",
                droppedBefore, droppedMsgs(), 0.0);
    }

    private void disconnectAndClose(MqttClient client) throws Exception {
        if (client == null) {
            return;
        }
        if (client.isConnected()) {
            client.disconnect();
        }
        client.close();
    }
}
