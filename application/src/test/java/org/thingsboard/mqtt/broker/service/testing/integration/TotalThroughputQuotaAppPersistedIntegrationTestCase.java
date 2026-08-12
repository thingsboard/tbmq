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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.service.limits.RateLimitCacheService;
import org.thingsboard.mqtt.broker.service.test.util.TestUtils;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.DROPPED_MSGS;

/**
 * The regression test for the defect that motivated prepaid charging: a backlog that accumulated while an APPLICATION
 * subscriber was offline used to be destroyed at replay time, at Kafka fetch speed, whenever the quota was exhausted -
 * and the pack committed those offsets, so the messages never came back.
 * <p>
 * Under prepaid charging the quota is spent when the publish is processed, before the copy is stored. A stored message
 * has therefore already been paid for, and its delivery is unmetered: the whole backlog must arrive even with the
 * shared bucket held empty for the entire replay.
 * <p>
 * Ledger arithmetic (capacity 100, full refill every 600 s = 0.17 tokens/s - negligible over the run, block size 4,
 * lease return disabled):
 * <ul>
 *     <li>boot: the warm-up draw takes one block, leaving local = 4 and bucket = 96;</li>
 *     <li>publish: each of the 20 publishes costs 2 - one incoming charge plus one APPLICATION fan-out charge - so 40
 *     of the 96 remaining tokens. The budget covers the whole backlog, which is asserted rather than assumed: any
 *     fan-out truncation would report droppedMsgs at publish time, and the assertion below requires none. All 20
 *     messages are therefore stored;</li>
 *     <li>replay: the shared bucket is drained directly through {@link RateLimitCacheService} - the cheapest faithful
 *     stand-in for another cluster node having spent the budget - which leaves the node holding at most one block of
 *     local tokens. Delivering 20 messages out of 4 local tokens is only possible if replay is not charged at all.</li>
 * </ul>
 * The final reconnect re-checks the arithmetic from the other side: everything was delivered AND acknowledged, so
 * there is nothing left in the per-client topic to redeliver.
 */
@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = TotalThroughputQuotaAppPersistedIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "mqtt.rate-limits.total.enabled=true",
        "mqtt.rate-limits.total.config=100:600",
        "mqtt.rate-limits.total.block-size=4",
        "mqtt.rate-limits.total.lease-return-ms=600000"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class TotalThroughputQuotaAppPersistedIntegrationTestCase extends AbstractPubSubIntegrationTest {

    private static final String TEST_APPLICATION_CLIENT = "quota-app-persisted-client";
    private static final String PUBLISHING_CLIENT = "quota_app_publishing_client";
    public static final String APP_USERNAME = "quotaApp";
    public static final String DEV_USERNAME = "quotaDev";

    private static final String TOPIC = "quota/app";
    private static final int BACKLOG_SIZE = 20;
    private static final long DRAIN_TOKENS = 10_000;

    @Autowired
    private MqttClientCredentialsService credentialsService;
    @Autowired
    private MeterRegistry meterRegistry;
    @Autowired
    private RateLimitCacheService rateLimitCacheService;

    private MqttClientCredentials applicationCredentials;
    private MqttClientCredentials deviceCredentials;
    private MqttClient persistedClient;

    @Before
    public void beforeTest() throws Exception {
        applicationCredentials = credentialsService.saveCredentials(
                TestUtils.createApplicationClientCredentials(TEST_APPLICATION_CLIENT, APP_USERNAME)
        );
        deviceCredentials = credentialsService.saveCredentials(
                TestUtils.createDeviceClientCredentials(PUBLISHING_CLIENT, DEV_USERNAME)
        );
        enableBasicProvider();
    }

    @After
    public void clear() throws Exception {
        log.warn("After test finish...");
        if (persistedClient.isConnected()) {
            persistedClient.disconnect();
        }
        MqttConnectOptions connectOptions = getConnectOptions(true, APP_USERNAME);
        persistedClient.connect(connectOptions);
        log.warn("After test finish... Persisted client connected: {}", isConnected());
        persistedClient.disconnect();
        persistedClient.close();

        credentialsService.deleteCredentials(applicationCredentials.getId());
        credentialsService.deleteCredentials(deviceCredentials.getId());
    }

    @Test
    public void givenAppBacklogPaidForAtPublish_whenQuotaIsDry_thenWholeBacklogIsReplayedUnmetered() throws Throwable {
        AtomicInteger received = new AtomicInteger();
        MqttConnectOptions appOptions = getConnectOptions(false, APP_USERNAME);

        persistedClient = new MqttClient(SERVER_URI + mqttPort, TEST_APPLICATION_CLIENT);
        persistedClient.connect(appOptions);
        persistedClient.subscribe(TOPIC, 1, (t, m) -> received.incrementAndGet());
        persistedClient.disconnect();

        // the historical droppedMsgs counter is zeroed by the reporting scheduler; this Micrometer counter is monotonic
        double droppedBeforePublish = droppedMsgs();

        MqttClient pub = new MqttClient(SERVER_URI + mqttPort, PUBLISHING_CLIENT);
        pub.connect(getConnectOptions(true, DEV_USERNAME));
        for (int i = 0; i < BACKLOG_SIZE; i++) {            // 2 charges each: incoming plus the APPLICATION fan-out
            pub.publish(TOPIC, ("m_" + i).getBytes(), 1, false);
            Thread.sleep(50);                              // pacing so each async draw lands before the next charge
        }
        pub.disconnect();
        pub.close();
        // let the whole backlog land on the per-client APPLICATION topic before the bucket is drained
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

        persistedClient.connect(appOptions);               // replay: already paid for, so the quota must not be consulted
        awaitReceived(received, BACKLOG_SIZE);
        Thread.sleep(1000);
        assertEquals("a stored backlog is replayed in full regardless of quota state", BACKLOG_SIZE, received.get());
        assertEquals("replaying an already-charged backlog must not report droppedMsgs",
                droppedBeforeReplay, droppedMsgs(), 0.0);

        persistedClient.disconnect();
        persistedClient.connect(appOptions);               // committed offsets: nothing is delivered twice
        Thread.sleep(3000);
        assertEquals("a fully delivered backlog must not be redelivered", BACKLOG_SIZE, received.get());
    }

    private void awaitReceived(AtomicInteger received, int expected) {
        // matcher form rather than a boolean lambda so a timeout reports how much of the backlog did arrive - the
        // size of the data loss - instead of an opaque "condition was not fulfilled"
        Awaitility.await("APPLICATION backlog replay")
                .atMost(30, TimeUnit.SECONDS)
                .until(received::get, greaterThanOrEqualTo(expected));
    }

    private boolean isConnected() {
        return Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> persistedClient.isConnected(), Boolean::booleanValue);
    }

    private MqttConnectOptions getConnectOptions(boolean cleanSession, String username) {
        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setCleanSession(cleanSession);
        connectOptions.setUserName(username);
        return connectOptions;
    }

    private double droppedMsgs() {
        return meterRegistry.get(DROPPED_MSGS).counter().count();
    }
}
