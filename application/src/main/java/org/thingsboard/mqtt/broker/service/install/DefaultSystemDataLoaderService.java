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
package org.thingsboard.mqtt.broker.service.install;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.AdminSettings;
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

import java.util.List;

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
        defaultAdminSettings().forEach(adminSettingsService::saveAdminSettings);
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
        defaultAuthProviders().forEach(mqttAuthProviderService::saveAuthProvider);
    }

    /**
     * The admin settings of a new installation, in the order they are saved. Each call creates new objects.
     */
    public static List<AdminSettings> defaultAdminSettings() {
        return List.of(
                GeneralSettings.createDefaults(),
                MailSettings.createDefaults(),
                ConnectivitySettings.createConnectivitySettings(),
                WebSocketClientSettings.createWsClientSettings(),
                MqttAuthSettings.createDefaults());
    }

    /**
     * The auth providers of a new installation, in the order they are saved: Basic enabled, the others disabled. Each
     * call creates new objects.
     */
    public static List<MqttAuthProvider> defaultAuthProviders() {
        return List.of(
                MqttAuthProvider.defaultBasicAuthProvider(true),
                MqttAuthProvider.defaultSslAuthProvider(false),
                MqttAuthProvider.defaultJwtAuthProvider(false),
                MqttAuthProvider.defaultScramAuthProvider(false),
                MqttAuthProvider.defaultHttpAuthProvider(false));
    }

}
