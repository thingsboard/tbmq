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

import io.netty.handler.codec.mqtt.MqttVersion;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
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
import org.thingsboard.mqtt.MqttClient;
import org.thingsboard.mqtt.MqttClientConfig;
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.actors.client.service.session.ClientSessionService;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.service.limits.RateLimitService;
import org.thingsboard.mqtt.broker.service.test.util.TestUtils;

import java.util.concurrent.TimeUnit;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = SessionsLimitIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "mqtt.sessions-limit=1"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class SessionsLimitIntegrationTestCase extends AbstractPubSubIntegrationTest {

    private final String USER_NAME = "sessionsLimitUn";

    @Autowired
    private MqttClientCredentialsService credentialsService;
    @Autowired
    public ClientSessionService clientSessionService;
    @Autowired
    private RateLimitService rateLimitService;

    private MqttClientCredentials credentials;
    private MqttClient client1;
    private MqttClient client2;

    @Before
    public void beforeTest() {
        credentials = credentialsService.saveCredentials(TestUtils.createDeviceClientCredentials(null, USER_NAME));
        enableBasicProvider();
        // The sessions limit is cluster-wide and its counter is kept in the cache, seeded on broker start with
        // the number of existing sessions. All integration tests share the same broker port, so a session left
        // behind by another test class can occupy the single allowed session and make the clients of this test
        // get rejected with QUOTA_EXCEEDED. Reset the counter to start from a deterministic state.
        rateLimitService.initSessionCount(0);
    }

    @After
    public void clear() throws Exception {
        disconnect(client1);
        disconnect(client2);
        awaitSessionRemoved(client1);
        awaitSessionRemoved(client2);
        credentialsService.deleteCredentials(credentials.getId());
    }

    @Test
    public void givenSessionsLimitSetTo1And1Client_whenTryConnectAnotherClient_thenRefuseNewConnection() throws Throwable {
        client1 = MqttClient.create(getConfig("test_sessions_limit_1"), null, externalExecutorService);
        client1.connect(LOCALHOST, mqttPort).get(30, TimeUnit.SECONDS);
        Assert.assertTrue(client1.isConnected());

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> {
            ClientSessionInfo clientSessionInfo1 = clientSessionService.getClientSessionInfo("test_sessions_limit_1");
            return clientSessionInfo1 != null && clientSessionInfo1.isConnected();
        });

        client2 = MqttClient.create(getConfig("test_sessions_limit_2"), null, externalExecutorService);
        client2.connect(LOCALHOST, mqttPort).get(30, TimeUnit.SECONDS);
        ClientSessionInfo clientSessionInfo2 = clientSessionService.getClientSessionInfo("test_sessions_limit_2");
        Assert.assertNull(clientSessionInfo2);
    }

    @Test
    public void givenSessionsLimitSetTo1And1Client_whenTryConnectAnotherClientWithSameClientId_thenAllowConnection() throws Throwable {
        client1 = MqttClient.create(getConfig("test_sessions_limit_same_client"), null, externalExecutorService);
        client1.connect(LOCALHOST, mqttPort).get(30, TimeUnit.SECONDS);
        Assert.assertTrue(client1.isConnected());

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> {
            ClientSessionInfo clientSessionInfo1 = clientSessionService.getClientSessionInfo("test_sessions_limit_same_client");
            return clientSessionInfo1 != null && clientSessionInfo1.isConnected();
        });

        client2 = MqttClient.create(getConfig("test_sessions_limit_same_client"), null, externalExecutorService);
        client2.connect(LOCALHOST, mqttPort).get(30, TimeUnit.SECONDS);
        ClientSessionInfo clientSessionInfo2 = clientSessionService.getClientSessionInfo("test_sessions_limit_same_client");
        Assert.assertNotNull(clientSessionInfo2);
    }

    private void disconnect(MqttClient client) {
        if (client != null && client.isConnected()) {
            client.disconnect();
        }
    }

    private void awaitSessionRemoved(MqttClient client) {
        if (client == null) {
            return;
        }
        String clientId = client.getClientConfig().getClientId();
        // Session removal is async and it is what decrements the cluster-wide sessions counter,
        // so wait for it to complete to not affect the next test method.
        Awaitility.await().atMost(10, TimeUnit.SECONDS)
                .until(() -> clientSessionService.getClientSessionInfo(clientId) == null);
    }

    private MqttClientConfig getConfig(String clientId) {
        MqttClientConfig config = new MqttClientConfig();
        config.setClientId(clientId);
        config.setUsername(USER_NAME);
        config.setCleanSession(true);
        config.setProtocolVersion(MqttVersion.MQTT_3_1_1);
        config.setReconnect(false);
        return config;
    }

}
