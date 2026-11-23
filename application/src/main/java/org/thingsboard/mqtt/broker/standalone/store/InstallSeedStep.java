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
package org.thingsboard.mqtt.broker.standalone.store;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.data.security.UserCredentials;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketConnection;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttAuthProviderService;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsService;
import org.thingsboard.mqtt.broker.dao.store.StoreSeedState;
import org.thingsboard.mqtt.broker.dao.user.UserService;
import org.thingsboard.mqtt.broker.dao.ws.WebSocketConnectionService;
import org.thingsboard.mqtt.broker.dao.ws.WebSocketSubscriptionService;
import org.thingsboard.mqtt.broker.dto.AdminDto;
import org.thingsboard.mqtt.broker.service.install.DefaultSystemDataLoaderService;
import org.thingsboard.mqtt.broker.service.user.AdminService;

import java.util.UUID;

/**
 * Seeds the management defaults of the cluster install into a store that has no seed marker: the admin settings, the
 * auth providers with Basic enabled, the sysadmin, the system WebSocket credentials and the sysadmin's default
 * WebSocket connection, through the same services and the same defaults as {@link DefaultSystemDataLoaderService}.
 * <p>
 * Each step skips what the store already holds and the marker is written last, so a seed that stops halfway is
 * finished on the next start without duplicates.
 */
@Slf4j
@Component
@StandaloneComponent
@Order(0)
public class InstallSeedStep implements ManagementDataStep {

    private static final String SYSADMIN_EMAIL = DefaultSystemDataLoaderService.SYSADMIN_EMAIL;

    private final StoreSeedState seedState;
    private final AdminSettingsService adminSettingsService;
    private final MqttAuthProviderService mqttAuthProviderService;
    private final UserService userService;
    private final AdminService adminService;
    private final MqttClientCredentialsService mqttClientCredentialsService;
    private final WebSocketConnectionService webSocketConnectionService;
    private final WebSocketSubscriptionService webSocketSubscriptionService;

    public InstallSeedStep(StoreSeedState seedState, AdminSettingsService adminSettingsService,
                           MqttAuthProviderService mqttAuthProviderService, UserService userService, AdminService adminService,
                           MqttClientCredentialsService mqttClientCredentialsService,
                           WebSocketConnectionService webSocketConnectionService,
                           WebSocketSubscriptionService webSocketSubscriptionService) {
        this.seedState = seedState;
        this.adminSettingsService = adminSettingsService;
        this.mqttAuthProviderService = mqttAuthProviderService;
        this.userService = userService;
        this.adminService = adminService;
        this.mqttClientCredentialsService = mqttClientCredentialsService;
        this.webSocketConnectionService = webSocketConnectionService;
        this.webSocketSubscriptionService = webSocketSubscriptionService;
    }

    @Override
    public void apply() {
        if (seedState.isSeeded()) {
            return;
        }
        log.info("Seeding the management defaults into a store that has not been seeded yet");
        seedAdminSettings();
        seedAuthProviders();
        User sysadmin = seedSysadmin();
        MqttClientCredentials systemCredentials = seedSystemWebSocketCredentials();
        seedDefaultWebSocketConnection(sysadmin, systemCredentials);
        seedState.markSeeded();
    }

    private void seedAdminSettings() {
        for (AdminSettings settings : DefaultSystemDataLoaderService.defaultAdminSettings()) {
            if (adminSettingsService.findAdminSettingsByKey(settings.getKey()) == null) {
                try {
                    adminSettingsService.saveAdminSettings(settings);
                } catch (RuntimeException e) {
                    throw new IllegalStateException("Failed to seed the admin settings '" + settings.getKey() + "': " + e.getMessage(), e);
                }
            } else {
                log.debug("The admin settings {} exist, skipping them", settings.getKey());
            }
        }
    }

    private void seedAuthProviders() {
        for (MqttAuthProvider provider : DefaultSystemDataLoaderService.defaultAuthProviders()) {
            if (mqttAuthProviderService.getAuthProviderByType(provider.getType()).isEmpty()) {
                try {
                    mqttAuthProviderService.saveAuthProvider(provider);
                } catch (RuntimeException e) {
                    throw new IllegalStateException("Failed to seed the " + provider.getType() + " auth provider: " + e.getMessage(), e);
                }
            } else {
                log.debug("The auth provider {} exists, skipping it", provider.getType());
            }
        }
    }

    private User seedSysadmin() {
        try {
            User sysadmin = userService.findUserByEmail(SYSADMIN_EMAIL);
            if (sysadmin == null) {
                return adminService.createAdmin(AdminDto.fromEmail(SYSADMIN_EMAIL), false);
            }
            UserCredentials credentials = userService.findUserCredentialsByUserId(sysadmin.getId());
            if (credentials == null) {
                log.warn("The sysadmin {} has no credentials, creating them with the default password", SYSADMIN_EMAIL);
                adminService.activateAdminCredentials(userService.createUserCredentials(sysadmin.getId()));
            } else if (!credentials.isEnabled() && StringUtils.isEmpty(credentials.getPassword())) {
                log.warn("The sysadmin {} has inactive credentials without a password, activating them with the default password",
                        SYSADMIN_EMAIL);
                adminService.activateAdminCredentials(credentials);
            }
            return sysadmin;
        } catch (ThingsboardException | RuntimeException e) {
            throw new IllegalStateException("Failed to seed the sysadmin " + SYSADMIN_EMAIL + ": " + e.getMessage(), e);
        }
    }

    private MqttClientCredentials seedSystemWebSocketCredentials() {
        MqttClientCredentials credentials = mqttClientCredentialsService.findSystemWebSocketCredentials();
        if (credentials != null) {
            return credentials;
        }
        try {
            return mqttClientCredentialsService.saveSystemWebSocketCredentials();
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to seed the system WebSocket client credentials: " + e.getMessage(), e);
        }
    }

    private void seedDefaultWebSocketConnection(User sysadmin, MqttClientCredentials systemCredentials) {
        UUID sysadminId = sysadmin.getId();
        WebSocketConnection connection = webSocketConnectionService.findWebSocketConnectionByName(
                sysadminId, BrokerConstants.WEB_SOCKET_DEFAULT_CONNECTION_NAME);
        try {
            if (connection == null) {
                webSocketConnectionService.saveDefaultWebSocketConnection(sysadminId, systemCredentials.getId());
            } else if (hasNoSubscriptions(connection)) {
                log.warn("The {} of the sysadmin {} has no subscription, creating the default one",
                        BrokerConstants.WEB_SOCKET_DEFAULT_CONNECTION_NAME, SYSADMIN_EMAIL);
                webSocketSubscriptionService.saveDefaultWebSocketSubscription(connection.getId());
            }
        } catch (ThingsboardException | RuntimeException e) {
            throw new IllegalStateException("Failed to create the " + BrokerConstants.WEB_SOCKET_DEFAULT_CONNECTION_NAME
                    + " of the sysadmin " + SYSADMIN_EMAIL + ": " + e.getMessage(), e);
        }
    }

    private boolean hasNoSubscriptions(WebSocketConnection connection) {
        return webSocketSubscriptionService.getWebSocketSubscriptions(connection.getId(), new PageLink(1))
                .getData().isEmpty();
    }

}
