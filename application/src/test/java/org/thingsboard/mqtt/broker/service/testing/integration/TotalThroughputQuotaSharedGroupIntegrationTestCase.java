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
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = TotalThroughputQuotaSharedGroupIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "mqtt.rate-limits.total.enabled=true",
        "mqtt.rate-limits.total.config=6:600",
        "mqtt.rate-limits.total.block-size=1",
        "mqtt.rate-limits.total.lease-return-ms=600000"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class TotalThroughputQuotaSharedGroupIntegrationTestCase extends AbstractPubSubIntegrationTest {

    @Test
    public void givenSharedGroupOfThree_whenPublishing_thenGroupChargedOncePerMsg() throws Throwable {
        AtomicInteger totalReceived = new AtomicInteger();
        CountDownLatch threeDelivered = new CountDownLatch(3);
        List<MqttClient> members = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            MqttClient member = new MqttClient(SERVER_URI + mqttPort, "quota_shared_member_" + i);
            // a per-subscription listener is useless for a shared subscription: Paho routes an incoming message by
            // matching the registered filter against the topic NAME, and "$share/qg/quota/shared" never matches
            // "quota/shared", so the message would be silently discarded by the client. Hence the general callback.
            member.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    totalReceived.incrementAndGet();
                    threeDelivered.countDown();
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                }
            });
            member.connect(new MqttConnectOptions());
            member.subscribe("$share/qg/quota/shared", 1);
            members.add(member);
        }

        MqttClient pub = new MqttClient(SERVER_URI + mqttPort, "quota_shared_pub");
        MqttConnectOptions pubOptions = new MqttConnectOptions();
        pubOptions.setAutomaticReconnect(false);
        pub.connect(pubOptions);

        // each publish costs 1 ingress + 1 egress (ONE group member) = 2; capacity 6 covers exactly 3 messages.
        // under the old subscription-entry charge each would have cost 4 and only 1 message would fit.
        for (int i = 0; i < 3; i++) {
            pub.publish("quota/shared", ("msg_" + i).getBytes(), 1, false);
            Thread.sleep(100);
        }
        assertTrue(threeDelivered.await(10, TimeUnit.SECONDS));

        try {
            pub.publish("quota/shared", "over".getBytes(), 1, false);
        } catch (Exception e) {
            log.info("Publisher refused/disconnected on over-budget publish", e);
        }
        Thread.sleep(1000);

        assertEquals("exactly one member per message, three messages total", 3, totalReceived.get());
        for (MqttClient c : members) {
            c.disconnect();
            c.close();
        }
        // the over-budget publisher may or may not have been disconnected by the broker, and close() rejects a
        // still-connected client, so the connection state is checked rather than asserted
        if (pub.isConnected()) {
            pub.disconnect();
        }
        pub.close();
    }
}
