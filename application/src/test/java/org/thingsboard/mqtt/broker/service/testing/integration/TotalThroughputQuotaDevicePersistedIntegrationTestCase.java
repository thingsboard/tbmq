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
import org.thingsboard.mqtt.broker.service.limits.RateLimitCacheService;
import org.thingsboard.mqtt.broker.service.test.util.TestUtils;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

/**
 * The device half of the same contract as {@link TotalThroughputQuotaAppPersistedIntegrationTestCase}: the copy is
 * paid for before it is stored, so replay is unmetered. Charging at delivery instead used to destroy the backlog one
 * message at a time, and each drop REMOVED the message from the store as well.
 * <p>
 * Arithmetic (capacity 100, block size 4, lease return disabled): the warm-up draw leaves local = 4 and bucket = 96;
 * the 20 publishes cost 2 each (ingress plus one device copy) = 40, so all are stored. The bucket is then drained,
 * leaving at most 4 local tokens - 20 deliveries out of 4 is only possible if replay is not charged. The final
 * reconnect checks the store was left intact rather than emptied by a drop path.
 */
@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = TotalThroughputQuotaDevicePersistedIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "mqtt.rate-limits.total.config=100:600",
        "mqtt.rate-limits.total.block-size=4"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class TotalThroughputQuotaDevicePersistedIntegrationTestCase extends AbstractTotalThroughputQuotaIntegrationTest {

    private static final String TOPIC = "quota/device";
    private static final int BACKLOG_SIZE = 20;

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

        double droppedBeforePublish = droppedMsgs();
        double processedBefore = publishesProcessed();

        MqttClient pub = new MqttClient(SERVER_URI + mqttPort, "quota_device_pub");
        pub.connect(new MqttConnectOptions());
        for (int i = 0; i < BACKLOG_SIZE; i++) {            // 2 charges each: incoming plus the device fan-out
            pub.publish(TOPIC, ("m_" + i).getBytes(), 1, false);
            pacePublish(processedBefore, i);
        }
        pub.disconnect();
        pub.close();
        // the pacing above also settles the last publish, so the whole backlog has reached the persisted store by
        // here: draining any earlier would charge the tail against an empty bucket and fail the assertion below for
        // the wrong reason.
        // Nothing was truncated at admission, so all 20 are stored - which is what makes the expected replay count
        // below the full backlog rather than a number derived from a partial grant
        assertEquals("the configured budget must admit the whole backlog at publish time",
                droppedBeforePublish, droppedMsgs(), 0.0);

        // without this the test would still "pass" against a delivery-time charge if the drain silently took nothing
        drainSharedBucket("before the replay");
        double droppedBeforeReplay = droppedMsgs();

        persistedClient.connect(persistent);               // replay: already paid for, so the quota must not be consulted
        awaitReceived("device backlog replay", received, BACKLOG_SIZE);
        // no settling wait here: OVER-delivery is caught by the held equality after the reconnect below, which any
        // extra copy would break just as surely
        assertEquals("a stored backlog is replayed in full regardless of quota state", BACKLOG_SIZE, received.get());
        assertEquals("replaying an already-charged backlog must not report droppedMsgs",
                droppedBeforeReplay, droppedMsgs(), 0.0);

        persistedClient.disconnect();
        persistedClient.connect(persistent);               // acknowledged and removed: nothing is delivered twice
        assertNothingMoreArrives("a fully delivered backlog must not be redelivered", received, BACKLOG_SIZE);
    }
}
