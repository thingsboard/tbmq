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
@ContextConfiguration(classes = ResponseInfoIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "mqtt.response-info=test/"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class ResponseInfoIntegrationTestCase extends AbstractPubSubIntegrationTest {

    @Test
    public void givenRequestResponseInfoIsFalse_whenProcessingConnect_thenResponseInfoValueIsNotReturned() throws Throwable {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setRequestResponseInfo(false);

        MqttClient client = new MqttClient(SERVER_URI + mqttPort, "responseInfoNullClient");
        IMqttToken iMqttToken = client.connectWithResult(options);

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .until(client::isConnected);

        MqttProperties responseProperties = iMqttToken.getResponseProperties();
        Assert.assertNull(responseProperties.getResponseInfo());

        client.disconnect();
        client.close();
    }

    @Test
    public void givenRequestResponseInfoIsTrue_whenProcessingConnect_thenResponseInfoValueIsReturned() throws Throwable {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setRequestResponseInfo(true);

        MqttClient client = new MqttClient(SERVER_URI + mqttPort, "responseInfoClient");
        IMqttToken iMqttToken = client.connectWithResult(options);

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .until(client::isConnected);

        MqttProperties responseProperties = iMqttToken.getResponseProperties();
        Assert.assertEquals("test/", responseProperties.getResponseInfo());

        client.disconnect();
        client.close();
    }

}
