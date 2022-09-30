/**
 * Copyright © 2016-2022 The Thingsboard Authors
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
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.service.test.util.TestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = Mqtt5PubSubIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "security.mqtt.basic.enabled=true"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class Mqtt5PubSubIntegrationTestCase extends AbstractPubSubIntegrationTest {

    static final String PUB_CLIENT_ID = "pubClientId";
    static final String SUB_CLIENT_ID = "subClientId";
    static final String MY_TOPIC = "my/topic";

    @Autowired
    private MqttClientCredentialsService credentialsService;

    private MqttClientCredentials pubCredentials;
    private MqttClientCredentials subCredentials;

    @Before
    public void init() throws Exception {
        pubCredentials = saveCredentials(PUB_CLIENT_ID, List.of("test/topic"));
        subCredentials = saveCredentials(SUB_CLIENT_ID, List.of(MY_TOPIC));
    }

    private MqttClientCredentials saveCredentials(String pubClientId, List<String> pubAuthRulePatterns) {
        return credentialsService.saveCredentials(TestUtils.createDeviceClientCredentialsWithAuth(pubClientId, pubAuthRulePatterns));
    }

    @After
    public void clear() {
        credentialsService.deleteCredentials(pubCredentials.getId());
        credentialsService.deleteCredentials(subCredentials.getId());
    }

    @Test
    public void givenPubSubClients_whenPubMsgToNoAuthTopic_thenNotReceiveMsgBySubscriber() throws Throwable {
        CountDownLatch latch = new CountDownLatch(1);

        MqttClient subClient = new MqttClient("tcp://localhost:" + mqttPort, SUB_CLIENT_ID);
        subClient.connect();
        MqttSubscription[] subscriptions = {new MqttSubscription(MY_TOPIC, 1)};
        IMqttMessageListener[] listeners = {(topic, message) -> latch.countDown()};
        subClient.subscribe(subscriptions, listeners);

        MqttClient pubClient = new MqttClient("tcp://localhost:" + mqttPort, PUB_CLIENT_ID);
        pubClient.connect();
        pubClient.publish(MY_TOPIC, "test".getBytes(StandardCharsets.UTF_8), 1, false);

        boolean await = latch.await(1, TimeUnit.SECONDS);
        Assert.assertFalse(await);

        pubClient.disconnect();
        pubClient.close();

        subClient.disconnect();
        subClient.close();
    }
}