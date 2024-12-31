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
package org.thingsboard.mqtt.broker.service.install.update;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = DefaultDataUpdateService.class)
public class DefaultDataUpdateServiceTest {

    @MockBean
    AdminSettingsService adminSettingsService;

    @SpyBean
    DefaultDataUpdateService service;

    @Test
    public void testUpdateData_createsWsClientSettingsWhenNotExist() throws Exception {
        // Given
        given(adminSettingsService.findAdminSettingsByKey(BrokerConstants.WEBSOCKET_KEY))
                .willReturn(null);

        // When
        service.updateData();

        // Then
        then(adminSettingsService).should().findAdminSettingsByKey(BrokerConstants.WEBSOCKET_KEY);
        then(adminSettingsService).should().saveAdminSettings(any(AdminSettings.class));

        ArgumentCaptor<AdminSettings> captor = ArgumentCaptor.forClass(AdminSettings.class);
        then(adminSettingsService).should().saveAdminSettings(captor.capture());

        AdminSettings savedSettings = captor.getValue();
        assertThat(savedSettings).isNotNull();
        assertThat(savedSettings.getKey()).isEqualTo(BrokerConstants.WEBSOCKET_KEY);
        assertThat(savedSettings.getJsonValue()).isNotNull();
    }

    @Test
    public void testUpdateData_updatesJsonValueWhenNull() throws Exception {
        // Given
        AdminSettings wsSettings = new AdminSettings();
        wsSettings.setKey(BrokerConstants.WEBSOCKET_KEY);
        wsSettings.setJsonValue(null);

        given(adminSettingsService.findAdminSettingsByKey(BrokerConstants.WEBSOCKET_KEY))
                .willReturn(wsSettings);

        // When
        service.updateData();

        // Then
        then(adminSettingsService).should().findAdminSettingsByKey(BrokerConstants.WEBSOCKET_KEY);
        then(adminSettingsService).should().saveAdminSettings(wsSettings);
        assertThat(wsSettings.getJsonValue()).isNotNull();
    }

    @Test
    public void testUpdateData_updatesMaxMessagesWhenMissing() throws Exception {
        // Given
        ObjectNode jsonValue = mock(ObjectNode.class);
        given(jsonValue.get("maxMessages")).willReturn(null);

        AdminSettings wsSettings = new AdminSettings();
        wsSettings.setKey(BrokerConstants.WEBSOCKET_KEY);
        wsSettings.setJsonValue(jsonValue);

        given(adminSettingsService.findAdminSettingsByKey(BrokerConstants.WEBSOCKET_KEY))
                .willReturn(wsSettings);

        // When
        service.updateData();

        // Then
        then(adminSettingsService).should().findAdminSettingsByKey(BrokerConstants.WEBSOCKET_KEY);
        then(adminSettingsService).should().saveAdminSettings(wsSettings);
        then(jsonValue).should().put("maxMessages", 1000);
    }
}
