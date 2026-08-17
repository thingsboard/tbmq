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
import org.awaitility.Awaitility;
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
import org.thingsboard.mqtt.broker.service.limits.RateLimitCacheService;
import org.thingsboard.mqtt.broker.service.test.util.TestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The primary prepaid case: a publish is charged for its whole fan-out BEFORE any copy is stored, so a budget short
 * of the fan-out truncates it at admission. The contract is the accounting identity
 * {@code delivered + droppedMsgs == subscriber count} - a copy that is neither stored nor reported is exactly the
 * silent loss prepaid charging exists to prevent - plus the fact that refused copies never arrive later, since they
 * were never stored.
 * <p>
 * WHICH subscribers win is deliberately not asserted: truncation drops the tail of the subscription list, which is
 * not something a client controls. The count is the contract.
 * <p>
 * Arithmetic (capacity 100, block size 4, lease return disabled): the warm-up draw leaves local = 4, the bucket is
 * drained so nothing can be drawn, and the single publish charges 1 for ingress plus 10 for the fan-out against a
 * pool that covers only part of it. The granted prefix depends on the block size and bounded credit, so it is
 * asserted as a strict truncation rather than a fixed number.
 */
@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = TotalThroughputQuotaFanOutIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "mqtt.rate-limits.total.config=100:600",
        "mqtt.rate-limits.total.block-size=4"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class TotalThroughputQuotaFanOutIntegrationTestCase extends AbstractTotalThroughputQuotaIntegrationTest {

    private static final String TOPIC = "quota/fanout";
    private static final int SUBSCRIBERS = 10;

    private final List<MqttClient> persistedSubscribers = new ArrayList<>();

    @After
    public void clear() throws Exception {
        for (MqttClient subscriber : persistedSubscribers) {
            // reconnects with cleanSession=true, which wipes the persistent session and any leftover backlog
            TestUtils.clearPersistedClient(subscriber, new MqttConnectOptions());
        }
    }

    @Test
    public void givenFanOutWiderThanTheBudget_whenPublishing_thenTruncatedAtPersistAndFullyAccountedFor() throws Throwable {
        AtomicInteger received = new AtomicInteger();

        MqttConnectOptions persistent = new MqttConnectOptions();
        persistent.setCleanSession(false);
        for (int i = 0; i < SUBSCRIBERS; i++) {
            MqttClient subscriber = new MqttClient(SERVER_URI + mqttPort, "quota_fanout_sub_" + i);
            persistedSubscribers.add(subscriber);
            subscriber.connect(persistent);
            subscriber.subscribe(TOPIC, 1, (t, m) -> received.incrementAndGet());
        }

        // the node holds only its warm-up block once the shared bucket is empty, so the fan-out cannot be covered
        drainSharedBucket("before the wide publish");
        double droppedBefore = droppedMsgs();

        MqttClient pub = new MqttClient(SERVER_URI + mqttPort, "quota_fanout_pub");
        MqttConnectOptions pubOptions = new MqttConnectOptions();
        pubOptions.setAutomaticReconnect(false);
        pub.connect(pubOptions);
        pub.publish(TOPIC, "wide".getBytes(), 1, false);
        // poll the accounting identity itself instead of sleeping a fixed 5 s: every subscriber copy ends up either
        // delivered or reported dropped, so the split is final the moment the two add up to the fan-out width. The
        // matcher form reports the sum it did reach, which is the number worth seeing if this ever times out.
        Awaitility.await("fan-out split settled")
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> received.get() + (int) Math.round(droppedMsgs() - droppedBefore),
                        greaterThanOrEqualTo(SUBSCRIBERS));

        int delivered = received.get();
        int dropped = (int) Math.round(droppedMsgs() - droppedBefore);
        log.info("Wide fan-out split: {} delivered, {} reported dropped", delivered, dropped);
        assertTrue("a budget short of the fan-out must not serve every subscriber, but served " + delivered,
                delivered < SUBSCRIBERS);
        assertTrue("the granted prefix must not be empty, but nothing was delivered", delivered > 0);
        assertEquals("every subscriber copy must be either delivered or reported dropped",
                SUBSCRIBERS, delivered + dropped);

        // no deferral anywhere: a refused copy was never stored, so waiting and reconnecting cannot produce it
        for (MqttClient subscriber : persistedSubscribers) {
            subscriber.disconnect();
            subscriber.connect(persistent);
        }
        // a timed wait, unavoidably: this asserts that nothing MORE arrives, and there is no counter whose movement
        // could end the wait early - only elapsed quiet time can build confidence in a negative
        assertNothingMoreArrives("quota-refused copies must never be delivered later", received, delivered);

        // the over-budget fan-out must not cost the publisher its connection: only the copies were refused, and the
        // ingress charge that decides the publisher's fate was granted
        assertTrue("the publisher must stay connected: its own publish was accepted", pub.isConnected());
        pub.disconnect();
        pub.close();
    }
}
