/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.testing.integration.persistentsession;

import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.common.MqttException;
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
import org.thingsboard.mqtt.broker.actors.client.service.session.ClientSessionService;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.service.test.util.TestUtils;

import java.util.concurrent.TimeUnit;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = AppClientsLimitIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "security.mqtt.basic.enabled=true",
        "mqtt.application-clients-limit=1"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class AppClientsLimitIntegrationTestCase extends AbstractPubSubIntegrationTest {

    private final String APP_CLIENTS_LIMIT_USER_NAME = "appClientsLimitUn";

    @Autowired
    private MqttClientCredentialsService credentialsService;
    @Autowired
    public ClientSessionService clientSessionService;

    private MqttClientCredentials applicationCredentials;
    private MqttClient persistedClient;

    @Before
    public void init() {
        applicationCredentials = credentialsService.saveCredentials(
                TestUtils.createApplicationClientCredentials(null, APP_CLIENTS_LIMIT_USER_NAME)
        );
    }

    @After
    public void clear() throws Exception {
        log.warn("After test finish...");
        TestUtils.clearPersistedClient(persistedClient, getOptions(true, APP_CLIENTS_LIMIT_USER_NAME));
        credentialsService.deleteCredentials(applicationCredentials.getId());
    }

    @Test
    public void givenAppClientsLimitSetTo1And1Client_whenTryConnectAnotherClient_thenRefuseNewConnection() throws Throwable {
        persistedClient = new MqttClient(SERVER_URI + mqttPort, "appClientLimit1");
        persistedClient.connect(getOptions(false, APP_CLIENTS_LIMIT_USER_NAME));
        persistedClient.disconnect();

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> {
            ClientSessionInfo clientSessionInfo = clientSessionService.getClientSessionInfo("appClientLimit1");
            return clientSessionInfo != null && ClientType.APPLICATION == clientSessionInfo.getType();
        });

        try {
            MqttClient anotherPersistedClient = new MqttClient(SERVER_URI + mqttPort, "appClientLimit2");
            anotherPersistedClient.connect(getOptions(false, APP_CLIENTS_LIMIT_USER_NAME));
        } catch (MqttException e) {
            Assert.assertTrue(e.getMessage().contains("Quota exceeded"));
        }
        Assert.assertNull(clientSessionService.getClientSessionInfo("appClientLimit2"));
    }

    @Test
    public void givenAppClientsLimitSetTo1And1Client_whenTryConnectAnotherClientWithSameClientId_thenAllowConnection() throws Throwable {
        persistedClient = new MqttClient(SERVER_URI + mqttPort, "appClientLimitSameClient");
        persistedClient.connect(getOptions(false, APP_CLIENTS_LIMIT_USER_NAME));

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> {
            ClientSessionInfo clientSessionInfo = clientSessionService.getClientSessionInfo("appClientLimitSameClient");
            return clientSessionInfo != null && ClientType.APPLICATION == clientSessionInfo.getType();
        });

        MqttClient anotherPersistedClient = new MqttClient(SERVER_URI + mqttPort, "appClientLimitSameClient");
        anotherPersistedClient.connect(getOptions(false, APP_CLIENTS_LIMIT_USER_NAME));

        Assert.assertNotNull(clientSessionService.getClientSessionInfo("appClientLimitSameClient"));

        anotherPersistedClient.disconnect();
        anotherPersistedClient.close();
    }

}
