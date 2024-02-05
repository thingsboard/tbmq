/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.integration;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;

import static org.junit.Assert.assertFalse;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = RateLimitsIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "mqtt.rate-limits.enabled=true",
        "mqtt.rate-limits.client-config=10:1,300:60"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class RateLimitsIntegrationTestCase extends AbstractPubSubIntegrationTest {

    @Test
    public void testClientDisconnectedWhenRateLimitsDetected() throws Throwable {
        MqttClient pubClient = new MqttClient(SERVER_URI + mqttPort, "test_rate_limits");
        pubClient.connect();

        for (int i = 0; i < 500; i++) {
            try {
                pubClient.publish("test/rate/limits", ("data_" + i).getBytes(), 1, false);
            } catch (Exception e) {
                log.error("Failed to publish msg", e);
                break;
            }
        }

        assertFalse(pubClient.isConnected());
        pubClient.close();
    }
}
