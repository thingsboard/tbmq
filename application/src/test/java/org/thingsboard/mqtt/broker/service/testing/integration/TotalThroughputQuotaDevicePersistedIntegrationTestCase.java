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
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.service.test.util.TestUtils;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Proves the terminal settlement of a device-persisted delivery that the total throughput quota refuses: the message is
 * removed from the persisted store rather than left pending, so it is never redelivered on the next session.
 * <p>
 * Ledger arithmetic (capacity 8, block size 2, lease return disabled, refill 1 token per 75 s - negligible here):
 * <ul>
 *     <li>boot: the warm-up draw takes one block, leaving local = 2 and bucket = 6;</li>
 *     <li>ingress: 6 publishes 50 ms apart. Every second charge takes the node to 0 and triggers a draw of one block,
 *     and the 50 ms pacing lets each ~1 ms Redis round trip land before the next charge. The draws at charges 2, 4 and 6
 *     therefore drain the bucket 6 -> 4 -> 2 -> 0 and leave local = 2 when the subscriber reconnects;</li>
 *     <li>replay: the device actor charges 1 PER MESSAGE in a tight loop. The ceiling is local (2) + bounded credit
 *     (one block = 2) + bucket (0) = 4 deliveries, so at least 2 of the 6 backlog messages MUST be quota-dropped no
 *     matter how the loop races the async draws. What the race does decide is whether 2, 3 or 4 land: as soon as a draw
 *     confirms the bucket dry it opens a 50 ms backoff during which the credit floor is 0 and the loop is refused
 *     locally. Hence the bound 2..4 rather than an exact count.</li>
 * </ul>
 */
@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = TotalThroughputQuotaDevicePersistedIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "mqtt.rate-limits.total.enabled=true",
        "mqtt.rate-limits.total.config=8:600",
        "mqtt.rate-limits.total.block-size=2",
        "mqtt.rate-limits.total.lease-return-ms=600000"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class TotalThroughputQuotaDevicePersistedIntegrationTestCase extends AbstractPubSubIntegrationTest {

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
    public void givenOfflineDeviceBacklog_whenQuotaDropsOnReplay_thenDroppedMsgsAreNotRedelivered() throws Throwable {
        AtomicInteger received = new AtomicInteger();

        MqttConnectOptions persistent = new MqttConnectOptions();
        persistent.setCleanSession(false);
        persistedClient = new MqttClient(SERVER_URI + mqttPort, "quota_device_persisted");
        persistedClient.connect(persistent);
        persistedClient.subscribe("quota/device", 1, (t, m) -> received.incrementAndGet());
        persistedClient.disconnect();

        MqttClient pub = new MqttClient(SERVER_URI + mqttPort, "quota_device_pub");
        pub.connect(new MqttConnectOptions());
        for (int i = 0; i < 6; i++) {                       // ingress: 6 charges drain the bucket, local settles at 2
            pub.publish("quota/device", ("m_" + i).getBytes(), 1, false);
            Thread.sleep(50);                               // pacing so each async draw lands before the next charge
        }
        pub.disconnect();
        pub.close();

        persistedClient.connect(persistent);                // replay: 2 local + 2 credit + 0 bucket = 4 deliveries max
        Thread.sleep(5000);
        int deliveredOnReplay = received.get();
        assertTrue("some backlog must be delivered, but got " + deliveredOnReplay, deliveredOnReplay >= 2);
        assertTrue("quota must drop at least two of the six messages, but delivered " + deliveredOnReplay,
                deliveredOnReplay <= 4);

        persistedClient.disconnect();
        persistedClient.connect(persistent);                // dropped messages were REMOVED: nothing further arrives
        Thread.sleep(3000);
        assertEquals("quota-dropped messages must not be redelivered", deliveredOnReplay, received.get());
    }
}
