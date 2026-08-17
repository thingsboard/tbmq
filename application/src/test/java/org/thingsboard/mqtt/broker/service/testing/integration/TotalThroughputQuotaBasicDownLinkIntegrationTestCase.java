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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The ordinary non-persistent delivery path - clean-session subscribers served by {@code BasicDownLinkProcessorImpl}.
 * Unit tests pin the refusal branch; what they cannot show is that a refused copy is actually reported, which is what
 * the identity {@code delivered + droppedMsgs == subscriber count} asserts here.
 * <p>
 * This site is deliberately not shaped like the persistent fan-out. Nothing is stored for a non-persistent
 * subscriber, so delivery time IS admission time and each copy is charged one at a time through the LEASE-CAPPED
 * {@code tryConsumeOutgoing()}. A burst can therefore be dropped because this NODE's pool ran dry even while the
 * cluster still holds budget, where a persistent fan-out would draw and be delivered in full. That is accepted for
 * best-effort QoS 0. It is documented rather than asserted: reproducing it depends on whether the background draw
 * keeps pace, which no assertion can pin without becoming flaky.
 * <p>
 * Arithmetic (capacity 100, block size 4, lease return disabled): the warm-up draw leaves local = 4, the bucket is
 * drained, and the publish charges 1 for ingress plus 1 per subscriber against a pool that covers the ingress and
 * only part of the fan-out. The granted prefix depends on the block size and bounded credit, so it is asserted as a
 * strict truncation rather than a fixed number.
 */
@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = TotalThroughputQuotaBasicDownLinkIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "mqtt.rate-limits.total.config=100:600",
        "mqtt.rate-limits.total.block-size=4"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class TotalThroughputQuotaBasicDownLinkIntegrationTestCase extends AbstractTotalThroughputQuotaIntegrationTest {

    private static final String TOPIC = "quota/basic";
    private static final int SUBSCRIBERS = 10;

    private final List<MqttClient> subscribers = new ArrayList<>();
    private MqttClient pub;

    @After
    public void clear() throws Exception {
        for (MqttClient subscriber : subscribers) {
            disconnectAndClose(subscriber);
        }
        disconnectAndClose(pub);
    }

    @Test
    public void givenNonPersistentSubscribers_whenQuotaIsDry_thenDropsAreReportedAndFullyAccountedFor() throws Throwable {
        AtomicInteger received = new AtomicInteger();

        // cleanSession=true is what makes these non-persistent, so the dispatcher routes them through
        // DownLinkProxyImpl#sendBasicMsg to BasicDownLinkProcessorImpl - the charge site under test
        MqttConnectOptions cleanSession = new MqttConnectOptions();
        cleanSession.setCleanSession(true);
        for (int i = 0; i < SUBSCRIBERS; i++) {
            MqttClient subscriber = new MqttClient(SERVER_URI + mqttPort, "quota_basic_sub_" + i);
            subscribers.add(subscriber);
            subscriber.connect(cleanSession);
            subscriber.subscribe(TOPIC, 1, (t, m) -> received.incrementAndGet());
        }

        // the node keeps only its warm-up block once the shared bucket is empty, so it cannot serve every subscriber
        drainSharedBucket("before the non-persistent fan-out");
        double droppedBefore = droppedMsgs();

        pub = new MqttClient(SERVER_URI + mqttPort, "quota_basic_pub");
        MqttConnectOptions pubOptions = new MqttConnectOptions();
        pubOptions.setAutomaticReconnect(false);
        pub.connect(pubOptions);
        pub.publish(TOPIC, "basic".getBytes(), 1, false);

        // poll the accounting identity rather than sleeping: every copy ends up either delivered or reported dropped,
        // so the split is final the moment the two add up to the subscriber count
        Awaitility.await("non-persistent fan-out split settled")
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> received.get() + (int) Math.round(droppedMsgs() - droppedBefore),
                        greaterThanOrEqualTo(SUBSCRIBERS));

        int delivered = received.get();
        int dropped = (int) Math.round(droppedMsgs() - droppedBefore);
        log.info("Non-persistent fan-out split: {} delivered, {} reported dropped", delivered, dropped);
        assertTrue("a dry quota must not serve every subscriber, but served " + delivered, delivered < SUBSCRIBERS);
        assertTrue("the granted prefix must not be empty, but nothing was delivered", delivered > 0);
        assertEquals("every non-persistent copy must be either delivered or reported dropped",
                SUBSCRIBERS, delivered + dropped);

        // nothing was stored for a non-persistent subscriber, so a refused copy has nowhere to come back from
        assertNothingMoreArrives("quota-refused copies must never arrive later", received, delivered);

        // the fan-out was refused, not the publish: the ingress charge was granted, so the publisher keeps its session
        assertTrue("the publisher must stay connected: its own publish was accepted", pub.isConnected());
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
