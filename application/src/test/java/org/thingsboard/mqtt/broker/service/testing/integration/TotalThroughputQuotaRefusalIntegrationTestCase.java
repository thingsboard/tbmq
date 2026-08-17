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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = TotalThroughputQuotaRefusalIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "mqtt.rate-limits.total.config=5:600",
        "mqtt.rate-limits.total.block-size=1"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class TotalThroughputQuotaRefusalIntegrationTestCase extends AbstractTotalThroughputQuotaIntegrationTest {

    // Both cases need the cluster budget already spent. Draining here, rather than letting one test spend it for
    // the next, keeps them independent and runnable alone.
    @Before
    public void emptySharedBucket() {
        ensureSharedBucketEmpty();
    }

    @Test
    public void givenMqtt3Publisher_whenQuotaExhausted_thenDisconnected() throws Throwable {
        MqttClient pubClient = new MqttClient(SERVER_URI + mqttPort, "quota_refusal_v3");
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(false);
        pubClient.connect(options);

        // the shared bucket is empty, so only the node's warm-up block and its one block of credit can pass:
        // a couple of publishes get through and the first refusal disconnects a 3.x client. Deliberately unpaced -
        // these two suites are the only ones that WANT the loop to outrun the pool, and pacing merely delayed the
        // refusal by letting the dry-backoff window lapse between publishes
        for (int i = 0; i < 20; i++) {
            try {
                pubClient.publish("quota/refusal", ("data_" + i).getBytes(), 1, false);
            } catch (Exception e) {
                log.info("Publish failed as expected after quota refusal", e);
                break;
            }
        }

        // the broker's DISCONNECT and Paho noticing it are two different threads, so poll rather than read once
        Awaitility.await("the 3.x publisher is disconnected on the first refusal")
                .atMost(AWAIT_TIMEOUT_SEC, TimeUnit.SECONDS)
                .until(() -> !pubClient.isConnected());
        pubClient.close();
    }

    @Test
    public void givenMqtt5Publisher_whenQuotaExhausted_thenQuotaExceededReasonCodeAndStillConnected() throws Throwable {
        org.eclipse.paho.mqttv5.client.MqttClient pubClient =
                new org.eclipse.paho.mqttv5.client.MqttClient(SERVER_URI + mqttPort, "quota_refusal_v5");
        List<Integer> reasonCodes = new CopyOnWriteArrayList<>();
        pubClient.setCallback(new org.eclipse.paho.mqttv5.client.MqttCallback() {
            @Override
            public void disconnected(org.eclipse.paho.mqttv5.client.MqttDisconnectResponse response) {
            }

            @Override
            public void mqttErrorOccurred(org.eclipse.paho.mqttv5.common.MqttException e) {
            }

            @Override
            public void messageArrived(String topic, org.eclipse.paho.mqttv5.common.MqttMessage message) {
            }

            @Override
            public void deliveryComplete(org.eclipse.paho.mqttv5.client.IMqttToken token) {
                for (int code : token.getReasonCodes()) {
                    reasonCodes.add(code);
                }
            }

            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
            }

            @Override
            public void authPacketArrived(int reasonCode, org.eclipse.paho.mqttv5.common.packet.MqttProperties properties) {
            }
        });
        pubClient.connect(new org.eclipse.paho.mqttv5.client.MqttConnectionOptions());

        for (int i = 0; i < 10; i++) {
            pubClient.publish("quota/refusal5", ("data_" + i).getBytes(), 1, false);
        }

        // 0x97 = 151 = QUOTA_EXCEEDED; the bucket was emptied in setup, so refusals MUST appear. Polled because
        // the reason codes are collected on Paho's callback thread, which the publish loop does not wait for
        Awaitility.await("at least one QUOTA_EXCEEDED puback")
                .atMost(AWAIT_TIMEOUT_SEC, TimeUnit.SECONDS)
                .until(() -> reasonCodes.contains(151));
        assertTrue(pubClient.isConnected());
        pubClient.disconnect();
        pubClient.close();
    }
}
