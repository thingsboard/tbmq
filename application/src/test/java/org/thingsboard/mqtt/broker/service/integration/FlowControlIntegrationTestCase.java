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
import org.awaitility.Awaitility;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
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

import java.util.concurrent.TimeUnit;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = FlowControlIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "mqtt.max-in-flight-msgs=500"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class FlowControlIntegrationTestCase extends AbstractPubSubIntegrationTest {

    @Test
    public void test() throws Throwable {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setReceiveMaximum(100);

        MqttClient client = new MqttClient(SERVER_URI + mqttPort, "receiveMaxClient");
        IMqttToken iMqttToken = client.connectWithResult(options);

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .until(client::isConnected);

        MqttProperties responseProperties = iMqttToken.getResponseProperties();
        Assert.assertEquals(500, responseProperties.getReceiveMaximum().intValue());

        client.disconnect();
        client.close();
    }

}
