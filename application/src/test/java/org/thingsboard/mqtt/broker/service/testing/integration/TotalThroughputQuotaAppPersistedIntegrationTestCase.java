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
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.service.limits.RateLimitCacheService;
import org.thingsboard.mqtt.broker.service.test.util.TestUtils;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

/**
 * An APPLICATION backlog is paid for when the publish is processed, so its replay must be unmetered: the whole
 * backlog arrives even with the shared bucket held empty throughout. Charging at delivery instead used to destroy
 * such a backlog at Kafka fetch speed, after the pack had already committed its offsets.
 * <p>
 * Arithmetic (capacity 100, block size 4, lease return disabled): the warm-up draw leaves local = 4 and bucket = 96;
 * the 20 publishes cost 2 each (ingress plus one APPLICATION copy) = 40, so the budget covers them all and every
 * message is stored. The bucket is then drained, leaving at most 4 local tokens - delivering 20 messages out of 4
 * is only possible if replay is not charged. The final reconnect checks nothing is redelivered.
 */
@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = TotalThroughputQuotaAppPersistedIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "mqtt.rate-limits.total.config=100:600",
        "mqtt.rate-limits.total.block-size=4"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class TotalThroughputQuotaAppPersistedIntegrationTestCase extends AbstractTotalThroughputQuotaIntegrationTest {

    private static final String TEST_APPLICATION_CLIENT = "quota-app-persisted-client";
    private static final String PUBLISHING_CLIENT = "quota_app_publishing_client";
    public static final String APP_USERNAME = "quotaApp";
    public static final String DEV_USERNAME = "quotaDev";

    private static final String TOPIC = "quota/app";
    private static final int BACKLOG_SIZE = 20;

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
    public void givenAppBacklogPaidForAtPublish_whenQuotaIsDry_thenWholeBacklogIsReplayedUnmetered() throws Throwable {
        AtomicInteger received = new AtomicInteger();
        MqttConnectOptions appOptions = getConnectOptions(false, APP_USERNAME);

        persistedClient = new MqttClient(SERVER_URI + mqttPort, TEST_APPLICATION_CLIENT);
        persistedClient.connect(appOptions);
        persistedClient.subscribe(TOPIC, 1, (t, m) -> received.incrementAndGet());
        persistedClient.disconnect();

        double droppedBeforePublish = droppedMsgs();

        MqttClient pub = new MqttClient(SERVER_URI + mqttPort, PUBLISHING_CLIENT);
        pub.connect(getConnectOptions(true, DEV_USERNAME));
        for (int i = 0; i < BACKLOG_SIZE; i++) {            // 2 charges each: incoming plus the APPLICATION fan-out
            pub.publish(TOPIC, ("m_" + i).getBytes(), 1, false);
            Thread.sleep(50);                              // pacing so each async draw lands before the next charge
        }
        pub.disconnect();
        pub.close();
        // a fixed wait, deliberately: the backlog must be stored before the drain, and "stored" has no counter to
        // poll - the subscriber is offline, so nothing observable moves. Draining too early would charge the tail
        // after the bucket is empty and fail the assertion below for the wrong reason.
        Thread.sleep(1000);
        // nothing was truncated at admission, so all 20 are stored - which is what makes the expected replay count
        // below the full backlog rather than a number derived from a partial grant
        assertEquals("the configured budget must admit the whole backlog at publish time",
                droppedBeforePublish, droppedMsgs(), 0.0);

        // without this the test would still "pass" against a delivery-time charge if the drain silently took nothing
        drainSharedBucket("before the replay");
        double droppedBeforeReplay = droppedMsgs();

        persistedClient.connect(appOptions);               // replay: already paid for, so the quota must not be consulted
        awaitReceived("APPLICATION backlog replay", received, BACKLOG_SIZE);
        // fixed: guards against OVER-delivery, so it can only be satisfied by time passing without the count moving
        Thread.sleep(1000);
        assertEquals("a stored backlog is replayed in full regardless of quota state", BACKLOG_SIZE, received.get());
        assertEquals("replaying an already-charged backlog must not report droppedMsgs",
                droppedBeforeReplay, droppedMsgs(), 0.0);

        persistedClient.disconnect();
        persistedClient.connect(appOptions);               // committed offsets: nothing is delivered twice
        Thread.sleep(3000);                                // fixed: another negative - nothing may arrive at all
        assertEquals("a fully delivered backlog must not be redelivered", BACKLOG_SIZE, received.get());
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
