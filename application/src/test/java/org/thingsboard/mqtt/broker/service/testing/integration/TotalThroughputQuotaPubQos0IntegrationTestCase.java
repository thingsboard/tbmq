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
import org.eclipse.paho.client.mqttv3.IMqttToken;
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
import org.thingsboard.mqtt.broker.service.test.util.TestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The second route into {@code BasicDownLinkProcessorImpl}, and the one that is easy to miss: a QoS 0 PUBLISH takes
 * {@code PersistentMsgSubscriptions#isNonPersistentByPubQos}, which sends EVERY non-integration subscriber down the
 * basic path - persistent session or not. So a client with a persistent session and a QoS 1 subscription still has its
 * copy charged one at a time at delivery through the lease-capped {@code tryConsumeOutgoing()}, never through the
 * admission-time fan-out charge in {@code MsgPersistenceManagerImpl}.
 * <p>
 * The sibling {@link TotalThroughputQuotaBasicDownLinkIntegrationTestCase} reaches the same charge site the other way,
 * via {@code cleanSession=true}. Both are needed: that one proves the site is charged, this one proves that having a
 * session buys a subscriber nothing when the publisher chose QoS 0. Hence the reconnect assertion below is paired with
 * {@code getSessionPresent()} - without it the test could not tell this route apart from the clean-session one.
 * <p>
 * These are separate classes rather than two methods because the suite reasons about an exact node-local pool: the
 * first publish leaves the pool at {@code -blockSize} against a dry bucket, so a second test sharing the context would
 * find its very first ingress charge refused.
 * <p>
 * Arithmetic (capacity 100, block size 4, lease return pushed past the run): the warm-up draw leaves local = 4, the
 * bucket is drained so nothing can be drawn, and the publish charges 1 for ingress plus 1 per subscriber against a
 * pool that covers only part of the fan-out. The granted prefix depends on the block size and bounded credit, so it is
 * asserted as a strict truncation rather than a fixed number.
 */
@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = TotalThroughputQuotaPubQos0IntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "mqtt.rate-limits.total.config=100:600",
        "mqtt.rate-limits.total.block-size=4"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class TotalThroughputQuotaPubQos0IntegrationTestCase extends AbstractTotalThroughputQuotaIntegrationTest {

    private static final String TOPIC = "quota/pubqos0";
    private static final int SUBSCRIBERS = 10;

    private final List<MqttClient> persistedSubscribers = new ArrayList<>();
    private MqttClient pub;

    @After
    public void clear() throws Exception {
        for (MqttClient subscriber : persistedSubscribers) {
            // reconnects with cleanSession=true, which wipes the persistent session and any leftover backlog
            TestUtils.clearPersistedClient(subscriber, new MqttConnectOptions());
        }
        if (pub != null) {
            if (pub.isConnected()) {
                pub.disconnect();
            }
            pub.close();
        }
    }

    @Test
    public void givenPersistentSubscribers_whenQos0PublishWithDryQuota_thenChargedAtDeliveryAndNothingIsStored() throws Throwable {
        AtomicInteger received = new AtomicInteger();

        // cleanSession=false plus a QoS 1 subscription is exactly the shape that WOULD be persisted for a QoS 1
        // publish; only the publisher's QoS 0 moves it onto the basic path
        MqttConnectOptions persistent = new MqttConnectOptions();
        persistent.setCleanSession(false);
        for (int i = 0; i < SUBSCRIBERS; i++) {
            MqttClient subscriber = new MqttClient(SERVER_URI + mqttPort, "quota_pubqos0_sub_" + i);
            persistedSubscribers.add(subscriber);
            subscriber.connect(persistent);
            subscriber.subscribe(TOPIC, 1, (t, m) -> received.incrementAndGet());
        }

        // the node holds only its warm-up block once the shared bucket is empty, so it cannot serve every subscriber
        drainSharedBucket("before the QoS 0 publish");
        double droppedBefore = droppedMsgs();

        pub = new MqttClient(SERVER_URI + mqttPort, "quota_pubqos0_pub");
        MqttConnectOptions pubOptions = new MqttConnectOptions();
        pubOptions.setAutomaticReconnect(false);
        pub.connect(pubOptions);
        pub.publish(TOPIC, "qos0".getBytes(), 0, false);

        // poll the accounting identity rather than sleeping: every copy ends up either delivered or reported dropped,
        // so the split is final the moment the two add up to the subscriber count
        Awaitility.await("QoS 0 fan-out split settled")
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> received.get() + (int) Math.round(droppedMsgs() - droppedBefore),
                        greaterThanOrEqualTo(SUBSCRIBERS));

        int delivered = received.get();
        int dropped = (int) Math.round(droppedMsgs() - droppedBefore);
        log.info("QoS 0 fan-out split: {} delivered, {} reported dropped", delivered, dropped);
        assertTrue("a dry quota must not serve every subscriber, but served " + delivered, delivered < SUBSCRIBERS);
        assertTrue("the granted prefix must not be empty, but nothing was delivered", delivered > 0);
        assertEquals("every QoS 0 copy must be either delivered or reported dropped",
                SUBSCRIBERS, delivered + dropped);

        // the point of this suite: these sessions really are persistent, and a persistent session still gets nothing
        // back, because a QoS 0 publish stored nothing for it in the first place
        for (MqttClient subscriber : persistedSubscribers) {
            subscriber.disconnect();
            IMqttToken token = subscriber.connectWithResult(persistent);
            assertTrue("the session must survive the reconnect, or this test is not exercising the persistent route",
                    token.getSessionPresent());
        }
        // a timed wait, unavoidably: this asserts that nothing MORE arrives, and there is no counter whose movement
        // could end the wait early - only elapsed quiet time can build confidence in a negative
        assertNothingMoreArrives("a QoS 0 publish stores nothing, so no copy may be delivered on reconnect",
                received, delivered);

        // the fan-out was refused, not the publish: the ingress charge was granted, so the publisher keeps its session
        assertTrue("the publisher must stay connected: its own publish was accepted", pub.isConnected());
    }
}
