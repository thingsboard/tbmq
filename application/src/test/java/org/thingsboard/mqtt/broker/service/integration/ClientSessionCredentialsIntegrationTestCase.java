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
import org.awaitility.Awaitility;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
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
import org.thingsboard.mqtt.broker.cache.CacheConstants;
import org.thingsboard.mqtt.broker.cache.CacheNameResolver;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.service.test.util.TestUtils;

import java.util.concurrent.TimeUnit;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = ClientSessionCredentialsIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "security.mqtt.basic.enabled=true"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class ClientSessionCredentialsIntegrationTestCase extends AbstractPubSubIntegrationTest {

    private final String USER_NAME = "clientSessionCredsUn";

    @Autowired
    private MqttClientCredentialsService credentialsService;
    @Autowired
    public ClientSessionService clientSessionService;
    @Autowired
    public CacheNameResolver cacheNameResolver;

    private MqttClientCredentials credentials;

    @Before
    public void init() {
        credentials = credentialsService.saveCredentials(TestUtils.createDeviceClientCredentials(null, USER_NAME));
    }

    @After
    public void clear() throws Exception {
        credentialsService.deleteCredentials(credentials.getId());
    }

    @Test
    public void givenConnectingClient_whenCheckClientCredsThatAuthenticatedClient_thenReturnExpectedCredsName() throws Throwable {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setUserName(USER_NAME);

        String clientId = "clientSessionCredentials";
        MqttClient client = new MqttClient(SERVER_URI + mqttPort, clientId);
        client.connect(options);

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .until(client::isConnected);

        String credentialsName = cacheNameResolver.getCache(CacheConstants.CLIENT_SESSION_CREDENTIALS_CACHE).get(clientId, String.class);
        Assert.assertEquals(credentials.getName(), credentialsName);

        client.disconnect();
        client.close();

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .until(() -> clientSessionService.getClientSessionInfo(client.getClientId()) == null);

        credentialsName = cacheNameResolver.getCache(CacheConstants.CLIENT_SESSION_CREDENTIALS_CACHE).get(clientId, String.class);
        Assert.assertNull(credentialsName);
    }

    @Test
    public void givenPersistentConnectingClient_whenCheckClientCredsThatAuthenticatedClient_thenReturnExpectedCredsName() throws Throwable {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setUserName(USER_NAME);
        options.setCleanStart(false);

        String clientId = "clientSessionCredentialsPersistent";
        MqttClient client = new MqttClient(SERVER_URI + mqttPort, clientId);
        client.connect(options);

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .until(client::isConnected);

        String credentialsName = cacheNameResolver.getCache(CacheConstants.CLIENT_SESSION_CREDENTIALS_CACHE).get(clientId, String.class);
        Assert.assertEquals(credentials.getName(), credentialsName);

        client.disconnect();

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .until(() -> clientSessionService.getClientSessionInfo(client.getClientId()).isDisconnected());

        credentialsName = cacheNameResolver.getCache(CacheConstants.CLIENT_SESSION_CREDENTIALS_CACHE).get(clientId, String.class);
        Assert.assertEquals(credentials.getName(), credentialsName);

        options.setCleanStart(true);
        client.connect(options);
        client.disconnect();
        client.close();

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .until(() -> clientSessionService.getClientSessionInfo(client.getClientId()) == null);

        credentialsName = cacheNameResolver.getCache(CacheConstants.CLIENT_SESSION_CREDENTIALS_CACHE).get(clientId, String.class);
        Assert.assertNull(credentialsName);
    }

}
