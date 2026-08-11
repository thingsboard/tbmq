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
import org.thingsboard.mqtt.broker.service.test.util.TestUtils;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

/**
 * Proves that a quota refusal caused by a node-local shortfall is DEFERRED, not destroyed: with the shared bucket
 * holding plenty of budget, a backlog far larger than one block must still be delivered IN FULL. Guards the defect
 * found by the 2026-08-10 load test, where an idle broker with a full bucket delivered only {@code block-size}
 * messages of a backlog and terminally destroyed the rest (73% of an APPLICATION pack, up to 96% of a DEVICE replay).
 * <p>
 * This case asserts only the delivered count, not {@code droppedMsgs}: production code destroys a refused message by
 * removing it from the persisted store (DEVICE) or excluding it from the pack before the commit (APPLICATION), so a
 * terminal drop shows up here as a delivered count permanently stuck below {@link #BACKLOG} - the two paths are
 * equivalent observables for what this test needs to prove.
 * <p>
 * Ledger arithmetic (capacity 100, block size 2, lease return disabled, refill 1 token per 6 s - negligible over
 * this test's runtime). The two {@code @Test} methods below share ONE Spring context
 * ({@code DirtiesContext.ClassMode.AFTER_CLASS}), so the throughput quota service's warm-up draw runs exactly ONCE
 * for the whole class, at boot - not once per method - and JUnit is free to run either method first. The
 * boot/ingress/replay walkthrough below is therefore only exact for whichever method happens to run first; the
 * second method starts its own ingress from whatever the first method's replay left in local/bucket, not from a
 * fresh boot:
 * <ul>
 *     <li>boot (once per class, not per method): the warm-up draw takes one block, leaving local = 2 and
 *     bucket = 98;</li>
 *     <li>ingress (per method): 10 publishes 50 ms apart. Every second charge empties the node and triggers a draw
 *     of one block that lands well inside the 50 ms pacing, so the draws at charges 2, 4, 6, 8 and 10 pull 10 tokens
 *     out of the bucket and leave local = 2 when the subscriber reconnects - for the first method to run, that is
 *     bucket 98 -> 88;</li>
 *     <li>replay (per method): the backlog of 10 is far above local (2) + one block of bounded credit (2) = 4, so
 *     each of the two persistence paths below MUST hit at least one refusal.</li>
 * </ul>
 * What actually makes both methods correct regardless of run order is a headroom invariant, not the exact figures
 * above: the one-time warm-up spends 2 tokens, each method's ingress draws about 10 more, and each method's own
 * replay-driven re-draws are bounded by its own backlog to roughly another 10 - so even after BOTH methods have run,
 * total consumption is a few dozen tokens against a 100-token capacity that refills negligibly over the test's
 * runtime. The bucket can therefore never actually run dry in either method, in either order: at every replay it
 * still holds many times the 10-message backlog's worst-case demand, so no draw this test can trigger ever comes
 * back empty, and every refusal it produces is a node-local shortfall - never a genuine exhaustion. A shortfall must
 * defer and retry, not settle terminally; that is exactly what Tasks 1-3 changed, and what a pre-fix broker gets
 * wrong.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = TotalThroughputQuotaDeferralIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "mqtt.rate-limits.total.enabled=true",
        "mqtt.rate-limits.total.config=100:600",
        "mqtt.rate-limits.total.block-size=2",
        "mqtt.rate-limits.total.lease-return-ms=600000"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class TotalThroughputQuotaDeferralIntegrationTestCase extends AbstractPubSubIntegrationTest {

    private static final int BACKLOG = 10;

    private static final String APPLICATION_CLIENT = "quota-defer-app-client";
    private static final String DEVICE_SUB_CLIENT = "quota_defer_device";
    private static final String DEVICE_PUB_FOR_DEVICE_CLIENT = "quota_defer_device_pub";
    private static final String DEVICE_PUB_FOR_APP_CLIENT = "quota_defer_app_pub";
    private static final String APP_USERNAME = "quotaDeferApp";
    private static final String DEV_USERNAME = "quotaDeferDev";

    @Autowired
    private MqttClientCredentialsService credentialsService;

    private MqttClientCredentials applicationCredentials;
    private MqttClientCredentials deviceSubCredentials;
    private MqttClientCredentials devicePubForDeviceCredentials;
    private MqttClientCredentials devicePubForAppCredentials;

    private MqttClient deviceSubClient;
    private MqttClient appSubClient;

    @Before
    public void beforeTest() throws Exception {
        applicationCredentials = credentialsService.saveCredentials(
                TestUtils.createApplicationClientCredentials(APPLICATION_CLIENT, APP_USERNAME)
        );
        // three DEVICE credential rows: the basic-auth lookup key is a (username, clientId) pair, so each distinct
        // client id that must authenticate as DEV_USERNAME needs its own row even though the username repeats.
        deviceSubCredentials = credentialsService.saveCredentials(
                TestUtils.createDeviceClientCredentials(DEVICE_SUB_CLIENT, DEV_USERNAME)
        );
        devicePubForDeviceCredentials = credentialsService.saveCredentials(
                TestUtils.createDeviceClientCredentials(DEVICE_PUB_FOR_DEVICE_CLIENT, DEV_USERNAME)
        );
        devicePubForAppCredentials = credentialsService.saveCredentials(
                TestUtils.createDeviceClientCredentials(DEVICE_PUB_FOR_APP_CLIENT, DEV_USERNAME)
        );
        enableBasicProvider();
    }

    @After
    public void clear() throws Exception {
        // clean up persisted sessions/credentials-backed clients BEFORE deleting their credentials, since the
        // clean-session reconnect below needs to authenticate; this also runs when the test body threw (e.g. the
        // Awaitility timeout on an unexpected failure), so a client left connected still gets purged.
        if (deviceSubClient != null) {
            TestUtils.clearPersistedClient(deviceSubClient, getConnectOptions(true, DEV_USERNAME));
        }
        if (appSubClient != null) {
            TestUtils.clearPersistedClient(appSubClient, getConnectOptions(true, APP_USERNAME));
        }
        credentialsService.deleteCredentials(applicationCredentials.getId());
        credentialsService.deleteCredentials(deviceSubCredentials.getId());
        credentialsService.deleteCredentials(devicePubForDeviceCredentials.getId());
        credentialsService.deleteCredentials(devicePubForAppCredentials.getId());
    }

    @Test
    public void givenDeviceBacklogAndBucketWithBudget_whenReplayed_thenAllDeliveredAndNothingDropped() throws Throwable {
        AtomicInteger received = new AtomicInteger();
        MqttConnectOptions persistent = getConnectOptions(false, DEV_USERNAME);

        deviceSubClient = new MqttClient(SERVER_URI + mqttPort, DEVICE_SUB_CLIENT);
        deviceSubClient.connect(persistent);
        deviceSubClient.subscribe("quota/defer/device", 1, (t, m) -> received.incrementAndGet());
        deviceSubClient.disconnect();

        publishBacklog("quota/defer/device", DEVICE_PUB_FOR_DEVICE_CLIENT, getConnectOptions(true, DEV_USERNAME));

        // replay: the device actor charges 1 PER MESSAGE in a tight loop. Exactly how much of the backlog is covered
        // by local balance plus bounded credit depends on whether this method ran first or second in the class (see
        // the class javadoc), but the bucket has so much headroom over the backlog's demand either way that no draw
        // here can ever come back empty. So whether a given charge lands before or after the in-flight draw
        // replenishes local is only ever a timing race, not a bucket limit, and every charge that loses that race is
        // a node-local shortfall. Pre-fix, a losing charge was treated exactly like genuine exhaustion and the
        // message was destroyed on the spot, so some prefix of the backlog was lost and never redelivered (observed
        // pre-fix: as few as 6 of 10 survived). Post-fix a losing charge is re-queued and retried every 10 ms until
        // the draw lands, so nothing needs to win the race and all 10 arrive.
        deviceSubClient.connect(persistent);
        Awaitility.await("full backlog delivered")
                .atMost(30, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(BACKLOG, received.get()));
    }

    @Test
    public void givenAppBacklogAndBucketWithBudget_whenReplayed_thenAllDeliveredAndNothingDropped() throws Throwable {
        AtomicInteger received = new AtomicInteger();
        MqttConnectOptions appOptions = getConnectOptions(false, APP_USERNAME);

        appSubClient = new MqttClient(SERVER_URI + mqttPort, APPLICATION_CLIENT);
        appSubClient.connect(appOptions);
        appSubClient.subscribe("quota/defer/app", 1, (t, m) -> received.incrementAndGet());
        appSubClient.disconnect();

        publishBacklog("quota/defer/app", DEVICE_PUB_FOR_APP_CLIENT, getConnectOptions(true, DEV_USERNAME));

        // replay: one bulk charge for the whole pack asks for 10. As the class javadoc explains, exactly how much of
        // that is covered by local balance plus bounded credit depends on whether this method ran first or second,
        // but the refused remainder is always a node-local shortfall - the bucket has far more headroom than a
        // 10-message backlog could ever drain, so this charge's draw can never come back empty. Pre-fix, that
        // remainder was excluded from delivery and the pack committed anyway, permanently losing it (observed
        // pre-fix: only 4 of 10 survived); post-fix the commit is held and the deferred remainder is retried within
        // the same pack every 10 ms until the shared bucket answers, so the pack settles - possibly over several
        // internal rounds - at all 10.
        appSubClient.connect(appOptions);
        Awaitility.await("full pack delivered across deferred rounds")
                .atMost(30, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(BACKLOG, received.get()));
    }

    private void publishBacklog(String topic, String pubClientId, MqttConnectOptions pubOptions) throws Throwable {
        MqttClient pub = new MqttClient(SERVER_URI + mqttPort, pubClientId);
        pub.connect(pubOptions);
        for (int i = 0; i < BACKLOG; i++) {
            pub.publish(topic, ("m_" + i).getBytes(), 1, false);
            Thread.sleep(50); // pacing so each async draw lands before the next charge
        }
        pub.disconnect();
        pub.close();
        Thread.sleep(1000); // let the whole backlog land on the per-client topic before the consumer starts
    }

    private MqttConnectOptions getConnectOptions(boolean cleanSession, String username) {
        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setCleanSession(cleanSession);
        connectOptions.setUserName(username);
        return connectOptions;
    }
}
