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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.ThingsboardMqttBrokerApplication;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttAuthProviderService;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsService;
import org.thingsboard.mqtt.broker.dao.user.UserService;
import org.thingsboard.mqtt.broker.standalone.store.ManagementDataStep;
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
@DaoSqlTest
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

    @Test
    public void theStoreHoldsTheSystemDataRows() {
        assertThat(steps).hasAtLeastOneElementOfType(SystemDataTestStep.class);
        assertThat(userService.findUserByEmail("sysadmin@thingsboard.org").getId())
                .isEqualTo(UUID.fromString("5a797660-4612-11e7-a919-92ebcb67fe33"));
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
    public void applyingTwiceChangesNothing() {
        steps.forEach(ManagementDataStep::apply);
        steps.forEach(ManagementDataStep::apply);

        assertThat(mqttAuthProviderService.getAuthProviders(new PageLink(10)).getData()).hasSize(5);
        assertThat(userService.findUserByEmail("sysadmin@thingsboard.org")).isNotNull();
        assertThat(adminSettingsService.findAdminSettingsByKey("mqttAuthorization").getJsonValue().get("priorities")).hasSize(3);
    }

}
