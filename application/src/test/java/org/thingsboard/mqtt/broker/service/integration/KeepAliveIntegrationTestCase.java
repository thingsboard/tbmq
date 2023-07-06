/**
 * Copyright © 2016-2023 The Thingsboard Authors
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
import org.eclipse.paho.mqttv5.client.IMqttMessageListener;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = KeepAliveIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "mqtt.keep-alive.monitoring-delay-ms=200"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class KeepAliveIntegrationTestCase extends AbstractPubSubIntegrationTest {

    @Test
    public void givenKeepAliveAsZero_whenProcessingKeepAlive_thenClientIsNotDisconnected() throws Throwable {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setKeepAliveInterval(0);
        options.setCleanStart(true);
        options.setSessionExpiryInterval(1000L);

        MqttClient client = new MqttClient("tcp://localhost:" + mqttPort, "keepAliveClient");
        client.connect(options);

        IMqttMessageListener[] listeners = {(topic, message) -> {
        }};
        MqttSubscription[] subscriptions = {new MqttSubscription("test/topic", 2)};
        client.subscribe(subscriptions, listeners);

        Thread.sleep(1000);

        boolean connected = client.isConnected();
        Assert.assertTrue(connected);

        client.disconnect();
        client.close();
    }

}
