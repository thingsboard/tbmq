/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2026 ThingsBoard, Inc. All Rights Reserved.
 *
 * NOTICE: All information contained herein is, and remains
 * the property of ThingsBoard, Inc. and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to ThingsBoard, Inc.
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 *
 * Dissemination of this information or reproduction of this material is strictly forbidden
 * unless prior written permission is obtained from COMPANY.
 *
 * Access to the source code contained herein is hereby forbidden to anyone except current COMPANY employees,
 * managers or contractors who have executed Confidentiality and Non-disclosure agreements
 * explicitly covering such access.
 *
 * The copyright notice above does not evidence any actual or intended publication
 * or disclosure  of  this source code, which includes
 * information that is confidential and/or proprietary, and is a trade secret, of  COMPANY.
 * ANY REPRODUCTION, MODIFICATION, DISTRIBUTION, PUBLIC  PERFORMANCE,
 * OR PUBLIC DISPLAY OF OR THROUGH USE  OF THIS  SOURCE CODE  WITHOUT
 * THE EXPRESS WRITTEN CONSENT OF COMPANY IS STRICTLY PROHIBITED,
 * AND IN VIOLATION OF APPLICABLE LAWS AND INTERNATIONAL TREATIES.
 * THE RECEIPT OR POSSESSION OF THIS SOURCE CODE AND/OR RELATED INFORMATION
 * DOES NOT CONVEY OR IMPLY ANY RIGHTS TO REPRODUCE, DISCLOSE OR DISTRIBUTE ITS CONTENTS,
 * OR TO MANUFACTURE, USE, OR SELL ANYTHING THAT IT  MAY DESCRIBE, IN WHOLE OR IN PART.
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
    public void givenValidSettings_whenSaved_thenTheyAreReadBack() throws Exception {
        ResourceUsageAlertSettings settings = new ResourceUsageAlertSettings(60, 90, true, "ops@example.com; admin@example.com");

        save(settings).andExpect(status().isOk());

        AdminSettings stored = doGet(RESOURCE_USAGE_ALERTS_URL, AdminSettings.class);
        assertThat(stored.getId()).isNotNull();
        assertThat(toSettings(stored)).isEqualTo(settings);

        ResourceUsageAlertSettings updated = new ResourceUsageAlertSettings(70, 80, false, "");
        stored.setJsonValue(JacksonUtil.valueToTree(updated));
        doPost(SETTINGS_URL, stored).andExpect(status().isOk());

        assertThat(toSettings(doGet(RESOURCE_USAGE_ALERTS_URL, AdminSettings.class))).isEqualTo(updated);
    }

    @Test
    public void givenInvalidThresholds_whenSaved_thenRejected() throws Exception {
        save(new ResourceUsageAlertSettings(0, 85, false, null))
                .andExpect(status().isBadRequest())
                .andExpect(statusReason(containsString("Resource usage thresholds")));
        save(new ResourceUsageAlertSettings(85, 85, false, null)).andExpect(status().isBadRequest());
        save(new ResourceUsageAlertSettings(90, 80, false, null)).andExpect(status().isBadRequest());
        save(new ResourceUsageAlertSettings(75, 101, false, null)).andExpect(status().isBadRequest());

        assertThat(doGet(RESOURCE_USAGE_ALERTS_URL, AdminSettings.class).getId()).isNull();
    }

    @Test
    public void givenInvalidEmail_whenSaved_thenRejected() throws Exception {
        save(new ResourceUsageAlertSettings(75, 85, true, "ops@example.com, not-an-email"))
                .andExpect(status().isBadRequest())
                .andExpect(statusReason(containsString("not-an-email")));

        assertThat(doGet(RESOURCE_USAGE_ALERTS_URL, AdminSettings.class).getId()).isNull();
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
