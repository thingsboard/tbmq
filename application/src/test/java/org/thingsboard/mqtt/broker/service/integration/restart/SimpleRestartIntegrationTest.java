/**
 * Copyright © 2016-2020 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.integration.restart;

import lombok.extern.slf4j.Slf4j;
import net.jodah.concurrentunit.Waiter;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.service.integration.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.service.test.util.RestartingSpringJUnit4ClassRunner;
import org.thingsboard.mqtt.broker.service.test.util.SpringRestarter;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = SimpleRestartIntegrationTest.class, loader = SpringBootContextLoader.class)
@DaoSqlTest
@RunWith(RestartingSpringJUnit4ClassRunner.class)
public class SimpleRestartIntegrationTest extends AbstractPubSubIntegrationTest {

    private static final int NUMBER_OF_MSGS_IN_SEQUENCE = 50;
    private static final String TEST_TOPIC = "test";

    @Test
    public void testBrokerRestart() throws Throwable {
        AtomicReference<TestPublishMsg> previousMsg = new AtomicReference<>();

        testPubSub(0, previousMsg);

        SpringRestarter.getInstance().restart();

        testPubSub(NUMBER_OF_MSGS_IN_SEQUENCE, previousMsg);
    }

    private void testPubSub(int startSequence, AtomicReference<TestPublishMsg> previousMsg) throws Throwable {
        MqttClient pubClient = new MqttClient("tcp://localhost:" + mqttPort, "app_restart_test_pub");
        pubClient.connect();

        MqttClient subClient = new MqttClient("tcp://localhost:" + mqttPort, "app_restart_test_sub");
        subClient.connect();

        Waiter waiter = new Waiter();

        subClient.subscribe(TEST_TOPIC, 0, (topic, message) -> {
            TestPublishMsg currentMsg = mapper.readValue(message.getPayload(), TestPublishMsg.class);
            if (previousMsg.get() != null) {
                waiter.assertEquals(previousMsg.get().sequenceId + 1, currentMsg.sequenceId);
            }
            if (currentMsg.sequenceId == startSequence + NUMBER_OF_MSGS_IN_SEQUENCE - 1) {
                waiter.resume();
            }
            previousMsg.getAndSet(currentMsg);
        });
        for (int j = startSequence; j < startSequence + NUMBER_OF_MSGS_IN_SEQUENCE; j++) {
            MqttMessage msg = new MqttMessage();
            TestPublishMsg payload = new TestPublishMsg(0, j, false);
            msg.setPayload(mapper.writeValueAsBytes(payload));
            msg.setQos(0);
            pubClient.publish(TEST_TOPIC, msg);
        }
        waiter.await(1, TimeUnit.SECONDS);

        pubClient.disconnect();
        pubClient.close();

        subClient.disconnect();
        subClient.close();
    }
}
