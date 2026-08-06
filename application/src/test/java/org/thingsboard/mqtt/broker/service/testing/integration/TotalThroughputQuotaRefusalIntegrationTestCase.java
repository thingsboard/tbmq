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
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = TotalThroughputQuotaRefusalIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "mqtt.rate-limits.total.enabled=true",
        "mqtt.rate-limits.total.config=5:600",
        "mqtt.rate-limits.total.block-size=1",
        "mqtt.rate-limits.total.lease-return-ms=600000"
})
@DaoSqlTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(SpringRunner.class)
public class TotalThroughputQuotaRefusalIntegrationTestCase extends AbstractPubSubIntegrationTest {

    @Test
    public void a_givenMqtt3Publisher_whenQuotaExhausted_thenDisconnected() throws Throwable {
        MqttClient pubClient = new MqttClient(SERVER_URI + mqttPort, "quota_refusal_v3");
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(false);
        pubClient.connect(options);

        // capacity 5 + 1 block of credit: at most 6 publishes pass, the next refusal disconnects a 3.x client
        for (int i = 0; i < 20; i++) {
            try {
                pubClient.publish("quota/refusal", ("data_" + i).getBytes(), 1, false);
                Thread.sleep(50); // let the async draw settle so credit accounting is deterministic
            } catch (Exception e) {
                log.info("Publish failed as expected after quota refusal", e);
                break;
            }
        }

        assertFalse(pubClient.isConnected());
        pubClient.close();
    }

    @Test
    public void b_givenMqtt5Publisher_whenQuotaExhausted_thenQuotaExceededReasonCodeAndStillConnected() throws Throwable {
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
            Thread.sleep(50);
        }

        // 0x97 = 151 = QUOTA_EXCEEDED; the bucket was exhausted by test a_, so refusals MUST appear
        assertTrue("expected at least one QUOTA_EXCEEDED puback", reasonCodes.contains(151));
        assertTrue(pubClient.isConnected());
        pubClient.disconnect();
        pubClient.close();
    }
}
