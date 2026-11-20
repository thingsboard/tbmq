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
package org.thingsboard.mqtt.broker.lite;

import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.controller.AbstractControllerTest;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsDao;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttAuthProviderDao;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsDao;
import org.thingsboard.mqtt.broker.dao.user.UserDao;
import org.thingsboard.mqtt.broker.dao.ws.WebSocketConnectionDao;
import org.thingsboard.mqtt.broker.dao.ws.WebSocketSubscriptionDao;
import org.thingsboard.mqtt.broker.service.security.auth.rest.LoginRequest;
import org.thingsboard.mqtt.broker.testsupport.EditionTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The real defaults of a fresh Lite install: without the test step that brings the data to {@code system-data.sql},
 * the store holds what the first-start seed wrote, and the broker and the REST API behave as on a new install.
 */
@EditionTest
@TestPropertySource(properties = "lite.test.system-data.enabled=false")
public class FirstStartLiteTest extends AbstractControllerTest {

    @Autowired
    private AdminSettingsDao adminSettingsDao;
    @Autowired
    private MqttAuthProviderDao mqttAuthProviderDao;
    @Autowired
    private UserDao userDao;
    @Autowired
    private MqttClientCredentialsDao mqttClientCredentialsDao;
    @Autowired
    private WebSocketConnectionDao webSocketConnectionDao;
    @Autowired
    private WebSocketSubscriptionDao webSocketSubscriptionDao;

    /**
     * The base class disables every auth provider before each test; these tests check the providers as seeded.
     */
    @Override
    public void beforeTest() {
    }

    @Test
    public void aFreshStoreHoldsExactlyTheInstallSeed() {
        assertThat(adminSettingsDao.find()).extracting(AdminSettings::getKey)
                .containsExactlyInAnyOrder("general", "mail", "connectivity", "websocket", "mqttAuthorization");
        assertThat(mqttAuthProviderDao.find()).extracting(MqttAuthProvider::getType)
                .containsExactlyInAnyOrder(MqttAuthProviderType.values());
        List<User> users = userDao.find();
        assertThat(users).extracting(User::getEmail).containsExactly("sysadmin@thingsboard.org");
        List<MqttClientCredentials> credentials = mqttClientCredentialsDao.find();
        assertThat(credentials).extracting(MqttClientCredentials::getName)
                .containsExactly(BrokerConstants.WS_SYSTEM_MQTT_CLIENT_CREDENTIALS_NAME);
        assertThat(webSocketConnectionDao.find()).singleElement().satisfies(connection -> {
            assertThat(connection.getName()).isEqualTo(BrokerConstants.WEB_SOCKET_DEFAULT_CONNECTION_NAME);
            assertThat(connection.getUserId()).isEqualTo(users.get(0).getId());
            assertThat(webSocketSubscriptionDao.findAllByWebSocketConnectionId(connection.getId(), new PageLink(10)).getData())
                    .singleElement()
                    .satisfies(subscription -> assertThat(subscription.getConfiguration().getTopicFilter())
                            .isEqualTo(BrokerConstants.WEB_SOCKET_DEFAULT_SUBSCRIPTION_TOPIC_FILTER));
        });
    }

    @Test
    public void basicIsTheOnlyEnabledProvider() {
        assertThat(mqttAuthProviderDao.find()).filteredOn(MqttAuthProvider::isEnabled)
                .extracting(MqttAuthProvider::getType)
                .containsExactly(MqttAuthProviderType.MQTT_BASIC);
    }

    @Test
    public void anAnonymousClientIsRefused() throws Exception {
        MqttClient client = new MqttClient(SERVER_URI + mqttPort, "first-start-anonymous", new MemoryPersistence());
        try {
            MqttConnectionOptions options = new MqttConnectionOptions();
            options.setCleanStart(true);

            Throwable failure = catchThrowable(() -> client.connect(options));

            assertThat(failure).isInstanceOf(MqttException.class);
            // 0x87 Not Authorized: what the Basic provider answers a client without a username
            assertThat(((MqttException) failure).getReasonCode()).isEqualTo(0x87);
            assertThat(client.isConnected()).isFalse();
        } finally {
            client.close();
        }
    }

    @Test
    public void theSeededSysadminCanLogIn() throws Exception {
        doPost("/api/auth/login", new LoginRequest("sysadmin@thingsboard.org", "sysadmin")).andExpect(status().isOk());
    }

}
