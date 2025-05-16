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
package org.thingsboard.mqtt.broker.service.install.update;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.common.data.SysAdminSettingType;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsService;
import org.thingsboard.mqtt.broker.service.install.data.WebSocketClientSettings;

@Service
@Profile("install")
@Slf4j
@RequiredArgsConstructor
public class DefaultDataUpdateService implements DataUpdateService {

    private final AdminSettingsService adminSettingsService;

    @Override
    public void updateData() throws Exception {
        log.info("Updating data ...");
        updateWsClientSettings();
        log.info("Data updated.");
    }

    private void saveWsClientSettings() {
        log.info("Creating WebSocket client settings ...");
        adminSettingsService.saveAdminSettings(WebSocketClientSettings.createWsClientSettings());
        log.info("WebSocket client settings created!");
    }

    void updateWsClientSettings() {
        AdminSettings wsSettings = adminSettingsService.findAdminSettingsByKey(SysAdminSettingType.WEBSOCKET.getKey());
        if (wsSettings == null) {
            saveWsClientSettings();
            return;
        }
        JsonNode jsonValue = wsSettings.getJsonValue();
        if (jsonValue == null) {
            log.info("Creating correct JSON value for WebSocket client settings ...");
            wsSettings.setJsonValue(WebSocketClientSettings.createWsClientJsonValue());
            adminSettingsService.saveAdminSettings(wsSettings);
            log.info("WebSocket client settings updated with correct JSON value!");
            return;
        }
        JsonNode maxMessages = jsonValue.get("maxMessages");
        if (maxMessages == null || maxMessages.isNull()) {
            log.info("Setting 'maxMessages' value for WebSocket client settings ...");

            ObjectNode objectNode = (ObjectNode) jsonValue;
            objectNode.put("maxMessages", 1000);

            wsSettings.setJsonValue(objectNode);
            adminSettingsService.saveAdminSettings(wsSettings);
            log.info("WebSocket client settings updated with 'maxMessages' value!");
        }
    }

}
