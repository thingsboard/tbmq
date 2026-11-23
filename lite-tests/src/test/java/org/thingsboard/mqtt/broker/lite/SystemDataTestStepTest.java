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

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.ThingsboardMqttBrokerApplication;
import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.security.UserCredentials;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsDao;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttAuthProviderDao;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttAuthProviderService;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsDao;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsService;
import org.thingsboard.mqtt.broker.dao.user.UserCredentialsDao;
import org.thingsboard.mqtt.broker.dao.user.UserDao;
import org.thingsboard.mqtt.broker.dao.user.UserService;
import org.thingsboard.mqtt.broker.dao.ws.WebSocketConnectionDao;
import org.thingsboard.mqtt.broker.standalone.store.ManagementDataStep;
import org.thingsboard.mqtt.broker.testsupport.EditionTest;
import org.thingsboard.mqtt.broker.testsupport.lite.SystemDataTestStep;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The guard for the management data of a Lite context: the rows of {@code system-data.sql} are there, whichever backend
 * the DAOs write to.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ThingsboardMqttBrokerApplication.class)
@ActiveProfiles("test")
@EditionTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class SystemDataTestStepTest {

    @ClassRule
    public static TempStorePathRule store = new TempStorePathRule();

    @Autowired
    private UserService userService;
    @Autowired
    private MqttAuthProviderService mqttAuthProviderService;
    @Autowired
    private AdminSettingsService adminSettingsService;
    @Autowired
    private List<ManagementDataStep> steps;
    @Autowired
    private AdminSettingsDao adminSettingsDao;
    @Autowired
    private MqttAuthProviderDao mqttAuthProviderDao;
    @Autowired
    private MqttClientCredentialsDao mqttClientCredentialsDao;
    @Autowired
    private WebSocketConnectionDao webSocketConnectionDao;
    @Autowired
    private UserDao userDao;
    @Autowired
    private UserCredentialsDao userCredentialsDao;

    @Test
    public void theStoreHoldsTheSystemDataRows() {
        assertThat(steps).hasAtLeastOneElementOfType(SystemDataTestStep.class);
        // not the id of system-data.sql: the install seed creates the sysadmin with an id of its own, and no shared test
        // uses that id
        assertThat(userService.findUserByEmail("sysadmin@thingsboard.org")).isNotNull();
        assertThat(mqttAuthProviderService.getAuthProviders(new PageLink(10)).getData())
                .hasSize(5)
                .noneMatch(MqttAuthProvider::isEnabled)
                .extracting(MqttAuthProvider::getType)
                .containsExactlyInAnyOrder(MqttAuthProviderType.values());
        assertThat(adminSettingsService.findAdminSettingsByKey("general")).isNotNull();
        assertThat(adminSettingsService.findAdminSettingsByKey("mail")).isNotNull();
        assertThat(adminSettingsService.findAdminSettingsByKey("mqttAuthorization").getJsonValue().get("priorities")).hasSize(3);
    }

    @Test
    public void afterAFreshSeedTheDataEqualsSystemDataSql() {
        assertThat(adminSettingsDao.find()).extracting(AdminSettings::getKey)
                .containsExactlyInAnyOrder("general", "mail", "mqttAuthorization");
        assertThat(adminSettingsDao.findByKey("general").getJsonValue())
                .isEqualTo(JacksonUtil.toJsonNode("{\"baseUrl\": \"http://localhost:8083\"}"));
        assertThat(adminSettingsDao.findByKey("mail").getJsonValue().get("mailFrom").asText())
                .isEqualTo("Thingsboard <sysadmin@localhost.localdomain>");
        assertThat(adminSettingsDao.findByKey("mqttAuthorization").getJsonValue())
                .isEqualTo(JacksonUtil.toJsonNode("{\"priorities\":[\"MQTT_BASIC\",\"X_509\",\"JWT\"]}"));

        List<MqttAuthProvider> providers = mqttAuthProviderDao.find();
        assertThat(providers).hasSize(5).noneMatch(MqttAuthProvider::isEnabled)
                .extracting(MqttAuthProvider::getId)
                .containsExactlyInAnyOrder(
                        UUID.fromString("bc0a90bf-de56-4953-92ca-7ef7b159c2fd"),
                        UUID.fromString("e5b71665-0a22-4398-8f93-5c7c3c145a7d"),
                        UUID.fromString("5590d101-628f-414c-9960-5966a9c42c31"),
                        UUID.fromString("3a7ffd44-eb9c-4334-951d-a8bb3cb4804f"),
                        UUID.fromString("7d5b83a2-9f31-4e4b-8e1c-5d9f0c2a4b6e"));
        assertThat(JacksonUtil.valueToTree(mqttAuthProviderDao.findByType(MqttAuthProviderType.JWT).getConfiguration())
                .at("/jwtVerifierConfiguration/jwtSignAlgorithmConfiguration/secret").asText())
                .isEqualTo("please-change-this-32-char-jwt-secret");

        assertThat(mqttClientCredentialsDao.find()).isEmpty();
        assertThat(webSocketConnectionDao.find()).isEmpty();

        User sysadmin = userDao.findByEmail("sysadmin@thingsboard.org");
        assertThat(userDao.find()).extracting(User::getId).containsExactly(sysadmin.getId());
        UserCredentials credentials = userCredentialsDao.findByUserId(sysadmin.getId());
        assertThat(credentials.isEnabled()).isTrue();
        assertThat(new BCryptPasswordEncoder().matches("sysadmin", credentials.getPassword())).isTrue();
    }

    @Test
    public void applyingTwiceChangesNothing() {
        steps.forEach(ManagementDataStep::apply);
        steps.forEach(ManagementDataStep::apply);

        assertThat(mqttAuthProviderService.getAuthProviders(new PageLink(10)).getData()).hasSize(5);
        assertThat(userService.findUserByEmail("sysadmin@thingsboard.org")).isNotNull();
        assertThat(adminSettingsService.findAdminSettingsByKey("mqttAuthorization").getJsonValue().get("priorities")).hasSize(3);
    }

}
