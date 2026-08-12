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
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.junit.After;
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
import org.thingsboard.mqtt.broker.service.test.util.TestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.DROPPED_MSGS;

/**
 * The primary prepaid case: a publish is charged for its whole fan-out BEFORE any copy is stored, so when the budget
 * cannot cover every subscriber the fan-out is truncated at admission rather than at delivery.
 * <p>
 * What must hold, and what this pins:
 * <ul>
 *     <li>the fan-out is split into a delivered prefix and a reported-dropped remainder, with nothing unaccounted for:
 *     {@code delivered + droppedMsgs == subscriber count}. That identity is the whole contract - a copy that is neither
 *     stored nor reported is exactly the silent loss prepaid charging exists to prevent;</li>
 *     <li>the truncation is genuine: some subscribers get the message and some get nothing;</li>
 *     <li>the refused copies are never stored, so they do not arrive later. There is no deferral at any charge site any
 *     more, and a subscriber above the cut stays empty however long it waits or however often it reconnects.</li>
 * </ul>
 * WHICH subscribers win is deliberately not asserted: truncation drops the tail of the subscription list, and that list
 * order is not something a client controls. The count is the contract.
 * <p>
 * Ledger arithmetic (capacity 100, block size 4, lease return disabled, refill 0.17 tokens/s - negligible here): the
 * warm-up draw leaves local = 4, the shared bucket is then drained directly through {@link RateLimitCacheService} so the
 * node has nothing left to draw, and the single publish charges 1 for ingress plus 10 for the fan-out against a pool
 * that can cover only part of it. The exact size of the granted prefix depends on the block size and the bounded credit,
 * so it is asserted as a strict truncation plus the accounting identity rather than as a fixed number.
 */
@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = TotalThroughputQuotaFanOutIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "mqtt.rate-limits.total.enabled=true",
        "mqtt.rate-limits.total.config=100:600",
        "mqtt.rate-limits.total.block-size=4",
        "mqtt.rate-limits.total.lease-return-ms=600000"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class TotalThroughputQuotaFanOutIntegrationTestCase extends AbstractPubSubIntegrationTest {

    private static final String TOPIC = "quota/fanout";
    private static final int SUBSCRIBERS = 10;
    private static final long DRAIN_TOKENS = 10_000;

    @Autowired
    private MeterRegistry meterRegistry;
    @Autowired
    private RateLimitCacheService rateLimitCacheService;

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
        long drained = rateLimitCacheService.tryConsumeTotalMsgs(DRAIN_TOKENS);
        log.info("Drained {} tokens from the shared bucket before the wide publish", drained);
        // without this the truncation assertions below could be satisfied by a bucket that was never actually emptied
        assertTrue("the drain must actually empty the shared bucket", drained > 0);
        // the historical droppedMsgs counter is zeroed by the reporting scheduler; this Micrometer counter is monotonic
        double droppedBefore = droppedMsgs();

        MqttClient pub = new MqttClient(SERVER_URI + mqttPort, "quota_fanout_pub");
        MqttConnectOptions pubOptions = new MqttConnectOptions();
        pubOptions.setAutomaticReconnect(false);
        pub.connect(pubOptions);
        pub.publish(TOPIC, "wide".getBytes(), 1, false);
        Thread.sleep(5000);

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
        Thread.sleep(3000);
        assertEquals("quota-refused copies must never be delivered later", delivered, received.get());

        // the over-budget fan-out must not cost the publisher its connection: only the copies were refused, and the
        // ingress charge that decides the publisher's fate was granted
        assertTrue("the publisher must stay connected: its own publish was accepted", pub.isConnected());
        pub.disconnect();
        pub.close();
    }

    private double droppedMsgs() {
        return meterRegistry.get(DROPPED_MSGS).counter().count();
    }
}
