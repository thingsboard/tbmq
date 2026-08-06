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
import static org.junit.Assert.assertTrue;

/**
 * Proves that a quota-truncated APPLICATION pack still settles: the refused tail is excluded from delivery, the pack
 * commits its offsets instead of stalling or retrying forever, and the dropped messages never come back.
 * <p>
 * Ledger arithmetic (capacity 10, block size 2, lease return disabled, refill 1 token per 60 s - negligible here):
 * <ul>
 *     <li>boot: the warm-up draw takes one block, leaving local = 2 and bucket = 8;</li>
 *     <li>ingress: 6 publishes 50 ms apart. Every second charge empties the node and triggers a draw of one block that
 *     lands well inside the 50 ms pacing, so the draws at charges 2, 4 and 6 take the bucket 8 -> 6 -> 4 -> 2 and leave
 *     local = 2 when the APPLICATION subscriber reconnects;</li>
 *     <li>replay: unlike the device path, the application processor charges the whole delivery round in ONE bulk
 *     {@code tryConsumeOutgoing(6)} that is settled by a single atomic CAS - there is no pacing race with the async
 *     draws. The grant is therefore exactly min(6, local 2 + bounded credit 2) = 4: 4 messages are delivered, 2 are
 *     excluded from the submit strategy and reported as droppedMsgs, and the pack commits all 6 Kafka offsets.</li>
 * </ul>
 * The counts are still asserted as a bound: should Kafka ever split the 6-message backlog across polls, each poll would
 * be its own bulk charge and more messages could squeeze through on fresh credit.
 */
@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = TotalThroughputQuotaAppPersistedIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "mqtt.rate-limits.total.enabled=true",
        "mqtt.rate-limits.total.config=10:600",
        "mqtt.rate-limits.total.block-size=2",
        "mqtt.rate-limits.total.lease-return-ms=600000"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class TotalThroughputQuotaAppPersistedIntegrationTestCase extends AbstractPubSubIntegrationTest {

    private static final String TEST_APPLICATION_CLIENT = "quota-app-persisted-client";
    private static final String PUBLISHING_CLIENT = "quota_app_publishing_client";
    public static final String APP_USERNAME = "quotaApp";
    public static final String DEV_USERNAME = "quotaDev";

    @Autowired
    private MqttClientCredentialsService credentialsService;

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
    public void givenAppBacklog_whenQuotaDropsPartOfPack_thenPackCommitsAndNoRedelivery() throws Throwable {
        AtomicInteger received = new AtomicInteger();
        MqttConnectOptions appOptions = getConnectOptions(false, APP_USERNAME);

        persistedClient = new MqttClient(SERVER_URI + mqttPort, TEST_APPLICATION_CLIENT);
        persistedClient.connect(appOptions);
        persistedClient.subscribe("quota/app", 1, (t, m) -> received.incrementAndGet());
        persistedClient.disconnect();

        MqttClient pub = new MqttClient(SERVER_URI + mqttPort, PUBLISHING_CLIENT);
        pub.connect(getConnectOptions(true, DEV_USERNAME));
        for (int i = 0; i < 6; i++) {                       // ingress: 6 charges leave local = 2 and bucket = 2
            pub.publish("quota/app", ("m_" + i).getBytes(), 1, false);
            Thread.sleep(50);                               // pacing so each async draw lands before the next charge
        }
        pub.disconnect();
        pub.close();

        persistedClient.connect(appOptions);                // pack replay: one bulk charge grants 2 local + 2 credit
        Thread.sleep(5000);
        int deliveredOnReplay = received.get();
        assertTrue("pack must make progress (no stall)", deliveredOnReplay >= 4);
        assertTrue("quota must drop at least one message", deliveredOnReplay < 6);

        persistedClient.disconnect();
        persistedClient.connect(appOptions);                // committed offsets: dropped messages never come back
        Thread.sleep(3000);
        assertEquals("quota-dropped pack tail must not be redelivered", deliveredOnReplay, received.get());
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
}
