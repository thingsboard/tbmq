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
package org.thingsboard.mqtt.broker.controller;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.common.data.ResourceUsageAlertSettings;
import org.thingsboard.mqtt.broker.common.data.SysAdminSettingType;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DaoSqlTest
public class AdminControllerTest extends AbstractControllerTest {

    private static final String SETTINGS_URL = "/api/admin/settings";
    private static final String RESOURCE_USAGE_ALERTS_URL = SETTINGS_URL + "/" + SysAdminSettingType.RESOURCE_USAGE_ALERTS.getKey();

    @Autowired
    private AdminSettingsService adminSettingsService;

    @Before
    public void beforeTest() throws Exception {
        adminSettingsService.deleteAdminSettingsByKey(SysAdminSettingType.RESOURCE_USAGE_ALERTS.getKey());
        loginSysAdmin();
    }

    @After
    public void afterTest() {
        adminSettingsService.deleteAdminSettingsByKey(SysAdminSettingType.RESOURCE_USAGE_ALERTS.getKey());
    }

    @Test
    public void givenNothingSaved_whenGetResourceUsageAlerts_thenDefaultsAreReturned() throws Exception {
        AdminSettings adminSettings = doGet(RESOURCE_USAGE_ALERTS_URL, AdminSettings.class);

        assertThat(adminSettings.getId()).isNull();
        assertThat(adminSettings.getKey()).isEqualTo(SysAdminSettingType.RESOURCE_USAGE_ALERTS.getKey());
        assertThat(toSettings(adminSettings)).isEqualTo(ResourceUsageAlertSettings.defaults());
    }

    @Test
    public void givenPartialRowWithDroppedFields_whenGetResourceUsageAlerts_thenMissingValuesAreDefaults() throws Exception {
        AdminSettings row = new AdminSettings();
        row.setKey(SysAdminSettingType.RESOURCE_USAGE_ALERTS.getKey());
        row.setJsonValue(JacksonUtil.toJsonNode("{\"highUsage\":60,\"emailEnabled\":true,\"emails\":[\"ops@example.com\"]}"));
        adminSettingsService.saveAdminSettings(row);

        AdminSettings adminSettings = doGet(RESOURCE_USAGE_ALERTS_URL, AdminSettings.class);

        assertThat(adminSettings.getId()).isNotNull();
        assertThat(toSettings(adminSettings)).isEqualTo(new ResourceUsageAlertSettings(60, ResourceUsageAlertSettings.DEFAULT_CRITICAL_USAGE));
        assertThat(adminSettings.getJsonValue().has("emails")).isFalse();
    }

    @Test
    public void givenValidSettings_whenSaved_thenTheyAreReadBack() throws Exception {
        ResourceUsageAlertSettings settings = new ResourceUsageAlertSettings(60, 90);

        save(settings).andExpect(status().isOk());

        AdminSettings stored = doGet(RESOURCE_USAGE_ALERTS_URL, AdminSettings.class);
        assertThat(stored.getId()).isNotNull();
        assertThat(toSettings(stored)).isEqualTo(settings);

        ResourceUsageAlertSettings updated = new ResourceUsageAlertSettings(70, 80);
        stored.setJsonValue(JacksonUtil.valueToTree(updated));
        doPost(SETTINGS_URL, stored).andExpect(status().isOk());

        assertThat(toSettings(doGet(RESOURCE_USAGE_ALERTS_URL, AdminSettings.class))).isEqualTo(updated);
    }

    @Test
    public void givenInvalidThresholds_whenSaved_thenRejected() throws Exception {
        save(new ResourceUsageAlertSettings(0, 85))
                .andExpect(status().isBadRequest())
                .andExpect(statusReason(containsString("Resource usage thresholds")));
        save(new ResourceUsageAlertSettings(85, 85)).andExpect(status().isBadRequest());
        save(new ResourceUsageAlertSettings(90, 80)).andExpect(status().isBadRequest());
        save(new ResourceUsageAlertSettings(75, 101)).andExpect(status().isBadRequest());

        assertThat(doGet(RESOURCE_USAGE_ALERTS_URL, AdminSettings.class).getId()).isNull();
    }

    @Test
    public void givenMalformedSettings_whenSaved_thenRejected() throws Exception {
        AdminSettings adminSettings = new AdminSettings();
        adminSettings.setKey(SysAdminSettingType.RESOURCE_USAGE_ALERTS.getKey());
        adminSettings.setJsonValue(JacksonUtil.toJsonNode("{\"highUsage\":\"abc\"}"));

        doPost(SETTINGS_URL, adminSettings)
                .andExpect(status().isBadRequest())
                .andExpect(statusReason(containsString("Invalid resource usage alert settings")));
    }

    private ResultActions save(ResourceUsageAlertSettings settings) throws Exception {
        AdminSettings adminSettings = new AdminSettings();
        adminSettings.setKey(SysAdminSettingType.RESOURCE_USAGE_ALERTS.getKey());
        adminSettings.setJsonValue(JacksonUtil.valueToTree(settings));
        return doPost(SETTINGS_URL, adminSettings);
    }

    private static ResourceUsageAlertSettings toSettings(AdminSettings adminSettings) {
        return JacksonUtil.convertValue(adminSettings.getJsonValue(), ResourceUsageAlertSettings.class);
    }

}
