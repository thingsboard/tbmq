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
package org.thingsboard.mqtt.broker.service.install;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttAuthProviderService;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsService;
import org.thingsboard.mqtt.broker.dao.ws.WebSocketConnectionService;
import org.thingsboard.mqtt.broker.dto.AdminDto;
import org.thingsboard.mqtt.broker.service.install.data.ConnectivitySettings;
import org.thingsboard.mqtt.broker.service.install.data.GeneralSettings;
import org.thingsboard.mqtt.broker.service.install.data.MailSettings;
import org.thingsboard.mqtt.broker.service.install.data.MqttAuthSettings;
import org.thingsboard.mqtt.broker.service.install.data.WebSocketClientSettings;
import org.thingsboard.mqtt.broker.service.user.AdminService;

@Service
@Profile("install")
@Slf4j
@RequiredArgsConstructor
public class DefaultSystemDataLoaderService implements SystemDataLoaderService {

    private final AdminService adminService;
    private final AdminSettingsService adminSettingsService;
    private final MqttClientCredentialsService mqttClientCredentialsService;
    private final MqttAuthProviderService mqttAuthProviderService;
    private final WebSocketConnectionService webSocketConnectionService;

    private User sysadmin;
    private MqttClientCredentials systemWebSocketCredentials;

    @Override
    public void createAdmin() throws ThingsboardException {
        AdminDto adminDto = AdminDto.fromEmail("sysadmin@thingsboard.org");
        sysadmin = adminService.createAdmin(adminDto, false);
    }

    @Override
    public void createAdminSettings() {
        adminSettingsService.saveAdminSettings(GeneralSettings.createDefaults());
        adminSettingsService.saveAdminSettings(MailSettings.createDefaults());
        adminSettingsService.saveAdminSettings(ConnectivitySettings.createConnectivitySettings());
        adminSettingsService.saveAdminSettings(WebSocketClientSettings.createWsClientSettings());
        adminSettingsService.saveAdminSettings(MqttAuthSettings.createDefaults());
    }

    @Override
    public void createWebSocketMqttClientCredentials() {
        systemWebSocketCredentials = mqttClientCredentialsService.saveSystemWebSocketCredentials();
    }

    @Override
    public void createDefaultWebSocketConnection() throws ThingsboardException {
        webSocketConnectionService.saveDefaultWebSocketConnection(sysadmin.getId(), systemWebSocketCredentials.getId());
    }

    @Override
    public void createMqttAuthProviders() {
        mqttAuthProviderService.saveAuthProvider(MqttAuthProvider.defaultBasicAuthProvider(true));
        mqttAuthProviderService.saveAuthProvider(MqttAuthProvider.defaultSslAuthProvider(false));
        mqttAuthProviderService.saveAuthProvider(MqttAuthProvider.defaultJwtAuthProvider(false));
        mqttAuthProviderService.saveAuthProvider(MqttAuthProvider.defaultScramAuthProvider(false));
    }

}
