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
package org.thingsboard.mqtt.broker.service.install.update;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.common.data.SysAdminSettingType;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttAuthProviderService;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsService;
import org.thingsboard.mqtt.broker.service.install.data.MqttAuthSettings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType.HTTP;

@Service
@Profile("install")
@Slf4j
@RequiredArgsConstructor
public class DefaultDataUpdateService implements DataUpdateService {

    private final AdminSettingsService adminSettingsService;
    private final MqttAuthProviderService mqttAuthProviderService;

    @Override
    public void updateData() throws Exception {
        log.info("Updating data ...");
        //TODO: should be cleaned after each release

        updateMqttAuthSettingsIfExist();
        createMqttAuthProviderIfNotExist();

        log.info("Data updated.");
    }

    private void updateMqttAuthSettingsIfExist() {
        log.info("Updating MQTT auth setting...");
        AdminSettings settings = adminSettingsService.findAdminSettingsByKey(SysAdminSettingType.MQTT_AUTHORIZATION.getKey());
        if (settings == null) {
            throw new RuntimeException("MQTT auth settings does not exist.");
        }

        MqttAuthSettings mqttAuthSettings = JacksonUtil.convertValue(settings.getJsonValue(), MqttAuthSettings.class);
        if (mqttAuthSettings == null) {
            throw new RuntimeException("MQTT auth settings are null.");
        }
        Set<MqttAuthProviderType> newPriorities = new HashSet<>(mqttAuthSettings.getPriorities());
        newPriorities.add(HTTP);

        MqttAuthSettings newMqttAuthSettings = new MqttAuthSettings();
        newMqttAuthSettings.setPriorities(new ArrayList<>(newPriorities));
        settings.setJsonValue(JacksonUtil.valueToTree(newMqttAuthSettings));

        adminSettingsService.saveAdminSettings(settings);
        log.info("Finished MQTT auth setting update!");
    }

    private void createMqttAuthProviderIfNotExist() {
        log.info("Starting HTTP service MQTT auth provider creation...");
        Optional<MqttAuthProvider> mqttAuthProviderOpt = mqttAuthProviderService.getAuthProviderByType(HTTP);
        if (mqttAuthProviderOpt.isPresent()) {
            log.info("Mqtt auth provider: {} already exists. Skipping!", HTTP);
            return;
        }
        log.info("Creating {} auth provider...", HTTP.getDisplayName());

        MqttAuthProvider mqttAuthProvider = MqttAuthProvider.defaultHttpAuthProvider(false);
        mqttAuthProviderService.saveAuthProvider(mqttAuthProvider);

        log.info("Created {} auth provider!", HTTP.getDisplayName());
        log.info("Finished HTTP service MQTT auth provider creation!");
    }

}
