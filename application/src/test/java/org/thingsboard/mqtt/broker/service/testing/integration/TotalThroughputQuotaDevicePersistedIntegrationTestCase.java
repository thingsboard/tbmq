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
import org.awaitility.Awaitility;
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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.DROPPED_MSGS;

/**
 * The device-replay half of the prepaid-charging regression: a backlog that accumulated while a DEVICE subscriber was
 * offline used to be destroyed one message at a time by the actor's per-message charge, and each drop also REMOVED the
 * message from the persisted store, so it was gone for good.
 * <p>
 * Under prepaid charging the device fan-out pays for the copy before it is stored, so replay is unmetered: the whole
 * backlog must arrive even with the shared bucket held empty for the entire replay.
 * <p>
 * Ledger arithmetic (capacity 100, full refill every 600 s = 0.17 tokens/s - negligible over the run, block size 4,
 * lease return disabled):
 * <ul>
 *     <li>boot: the warm-up draw takes one block, leaving local = 4 and bucket = 96;</li>
 *     <li>publish: each of the 20 publishes costs 2 - one incoming charge plus one device fan-out charge - so 40 of the
 *     96 remaining tokens. The budget covers the whole backlog, which is asserted rather than assumed: any fan-out
 *     truncation would report droppedMsgs at publish time, and the assertion below requires none. All 20 messages are
 *     therefore stored;</li>
 *     <li>replay: the shared bucket is drained directly through {@link RateLimitCacheService} - the cheapest faithful
 *     stand-in for another cluster node having spent the budget - which leaves the node holding at most one block of
 *     local tokens. The actor delivered 1 per charge, so 20 deliveries out of 4 local tokens is only possible if replay
 *     is not charged at all.</li>
 * </ul>
 * The final reconnect proves the store was left intact rather than emptied by a drop path: nothing extra arrives, and
 * nothing is missing.
 */
@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = TotalThroughputQuotaDevicePersistedIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "mqtt.rate-limits.total.enabled=true",
        "mqtt.rate-limits.total.config=100:600",
        "mqtt.rate-limits.total.block-size=4",
        "mqtt.rate-limits.total.lease-return-ms=600000"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class TotalThroughputQuotaDevicePersistedIntegrationTestCase extends AbstractPubSubIntegrationTest {

    private static final String TOPIC = "quota/device";
    private static final int BACKLOG_SIZE = 20;
    private static final long DRAIN_TOKENS = 10_000;

    @Autowired
    private MeterRegistry meterRegistry;
    @Autowired
    private RateLimitCacheService rateLimitCacheService;

    private MqttClient persistedClient;

    @After
    public void clear() throws Exception {
        log.warn("After test finish...");
        if (persistedClient != null) {
            // reconnects with cleanSession=true, which wipes the persistent session and any leftover backlog
            TestUtils.clearPersistedClient(persistedClient, new MqttConnectOptions());
        }
    }

    @Test
    public void givenDeviceBacklogPaidForAtPublish_whenQuotaIsDry_thenWholeBacklogIsReplayedUnmetered() throws Throwable {
        AtomicInteger received = new AtomicInteger();

        MqttConnectOptions persistent = new MqttConnectOptions();
        persistent.setCleanSession(false);
        persistedClient = new MqttClient(SERVER_URI + mqttPort, "quota_device_persisted");
        persistedClient.connect(persistent);
        persistedClient.subscribe(TOPIC, 1, (t, m) -> received.incrementAndGet());
        persistedClient.disconnect();

        // the historical droppedMsgs counter is zeroed by the reporting scheduler; this Micrometer counter is monotonic
        double droppedBeforePublish = droppedMsgs();

        MqttClient pub = new MqttClient(SERVER_URI + mqttPort, "quota_device_pub");
        pub.connect(new MqttConnectOptions());
        for (int i = 0; i < BACKLOG_SIZE; i++) {            // 2 charges each: incoming plus the device fan-out
            pub.publish(TOPIC, ("m_" + i).getBytes(), 1, false);
            Thread.sleep(50);                              // pacing so each async draw lands before the next charge
        }
        pub.disconnect();
        pub.close();
        // let the whole backlog reach the persisted store before the bucket is drained
        Thread.sleep(1000);
        // the budget covered the whole fan-out, so nothing was truncated at admission and all 20 are stored. This is
        // what makes the expected replay count below the full backlog rather than a number derived from a partial grant
        assertEquals("the configured budget must admit the whole backlog at publish time",
                droppedBeforePublish, droppedMsgs(), 0.0);

        long drained = rateLimitCacheService.tryConsumeTotalMsgs(DRAIN_TOKENS);
        log.info("Drained {} tokens from the shared bucket before the replay", drained);
        // without this the test would still "pass" against a delivery-time charge if the drain silently took nothing
        assertTrue("the drain must actually empty the shared bucket", drained > 0);
        double droppedBeforeReplay = droppedMsgs();

        persistedClient.connect(persistent);               // replay: already paid for, so the quota must not be consulted
        awaitReceived(received, BACKLOG_SIZE);
        Thread.sleep(1000);
        assertEquals("a stored backlog is replayed in full regardless of quota state", BACKLOG_SIZE, received.get());
        assertEquals("replaying an already-charged backlog must not report droppedMsgs",
                droppedBeforeReplay, droppedMsgs(), 0.0);

        persistedClient.disconnect();
        persistedClient.connect(persistent);               // acknowledged and removed: nothing is delivered twice
        Thread.sleep(3000);
        assertEquals("a fully delivered backlog must not be redelivered", BACKLOG_SIZE, received.get());
    }

    private void awaitReceived(AtomicInteger received, int expected) {
        // matcher form rather than a boolean lambda so a timeout reports how much of the backlog did arrive - the
        // size of the data loss - instead of an opaque "condition was not fulfilled"
        Awaitility.await("device backlog replay")
                .atMost(30, TimeUnit.SECONDS)
                .until(received::get, greaterThanOrEqualTo(expected));
    }

    private double droppedMsgs() {
        return meterRegistry.get(DROPPED_MSGS).counter().count();
    }
}
