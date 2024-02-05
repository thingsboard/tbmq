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
package org.thingsboard.mqtt.broker.service.integration.persistentsession;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.service.integration.parent.AbstractRequestResponseIntegrationTestCase;
import org.thingsboard.mqtt.broker.service.test.util.TestUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertTrue;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = DeviceRequestResponseIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "security.mqtt.basic.enabled=true"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class DeviceRequestResponseIntegrationTestCase extends AbstractRequestResponseIntegrationTestCase {

    @Before
    public void init() {
        super.init(TestUtils.createDeviceClientCredentials(null, REQUEST_RESPONSE_USER_NAME));
    }

    @After
    public void clear() throws Exception {
        super.clear();
    }

    @Test
    public void givenDeviceSubscribedClient_whenPubMsgWithResponseTopicAndCorrelationData_thenReceiveMsgWithSpecifiedProperties() throws Throwable {
        CountDownLatch receivedResponses = new CountDownLatch(1);
        AtomicBoolean receivedMsg = new AtomicBoolean(false);

        MqttConnectionOptions options = getOptions(false, REQUEST_RESPONSE_USER_NAME);
        MqttClient persistedClient = connectClientAndSubscribe(options, receivedMsg, receivedResponses);
        persistedClient.disconnect();

        connectPubClientSendMsgAndClose(CORRELATION_DATA, RESPONSE_TOPIC, false);

        persistedClient.connect(getOptions(false, REQUEST_RESPONSE_USER_NAME));

        boolean await = receivedResponses.await(5, TimeUnit.SECONDS);
        log.error("The result of awaiting of message receiving is: [{}]", await);

        assertTrue(receivedMsg.get());
    }

}
