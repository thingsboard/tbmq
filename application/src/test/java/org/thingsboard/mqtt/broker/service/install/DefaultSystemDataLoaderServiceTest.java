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

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttAuthProviderService;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsService;
import org.thingsboard.mqtt.broker.dao.ws.WebSocketConnectionService;
import org.thingsboard.mqtt.broker.service.install.data.ConnectivitySettings;
import org.thingsboard.mqtt.broker.service.install.data.GeneralSettings;
import org.thingsboard.mqtt.broker.service.install.data.MailSettings;
import org.thingsboard.mqtt.broker.service.install.data.MqttAuthSettings;
import org.thingsboard.mqtt.broker.service.install.data.WebSocketClientSettings;
import org.thingsboard.mqtt.broker.service.user.AdminService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class DefaultSystemDataLoaderServiceTest {

    private final AdminSettingsService adminSettingsService = mock(AdminSettingsService.class);
    private final MqttAuthProviderService mqttAuthProviderService = mock(MqttAuthProviderService.class);

    private final DefaultSystemDataLoaderService loader = new DefaultSystemDataLoaderService(mock(AdminService.class),
            adminSettingsService, mock(MqttClientCredentialsService.class), mqttAuthProviderService, mock(WebSocketConnectionService.class));

    @Test
    void theDefaultsAreTheInstallSeed() {
        assertThat(DefaultSystemDataLoaderService.defaultAdminSettings())
                .extracting(AdminSettings::getKey)
                .containsExactly("general", "mail", "connectivity", "websocket", "mqttAuthorization");
        assertThat(DefaultSystemDataLoaderService.defaultAdminSettings())
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactly(GeneralSettings.createDefaults(), MailSettings.createDefaults(),
                        ConnectivitySettings.createConnectivitySettings(), WebSocketClientSettings.createWsClientSettings(),
                        MqttAuthSettings.createDefaults());

        assertThat(DefaultSystemDataLoaderService.defaultAuthProviders())
                .extracting(MqttAuthProvider::getType, MqttAuthProvider::isEnabled)
                .containsExactly(
                        tuple(MqttAuthProviderType.MQTT_BASIC, true),
                        tuple(MqttAuthProviderType.X_509, false),
                        tuple(MqttAuthProviderType.JWT, false),
                        tuple(MqttAuthProviderType.SCRAM, false),
                        tuple(MqttAuthProviderType.HTTP, false));
        assertThat(DefaultSystemDataLoaderService.defaultAuthProviders())
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactly(MqttAuthProvider.defaultBasicAuthProvider(true), MqttAuthProvider.defaultSslAuthProvider(false),
                        MqttAuthProvider.defaultJwtAuthProvider(false), MqttAuthProvider.defaultScramAuthProvider(false),
                        MqttAuthProvider.defaultHttpAuthProvider(false));
    }

    @Test
    void eachCallGivesFreshObjects() {
        assertThat(DefaultSystemDataLoaderService.defaultAdminSettings().get(0))
                .isNotSameAs(DefaultSystemDataLoaderService.defaultAdminSettings().get(0));
        assertThat(DefaultSystemDataLoaderService.defaultAuthProviders().get(0))
                .isNotSameAs(DefaultSystemDataLoaderService.defaultAuthProviders().get(0));
    }

    @Test
    void theInstallSavesTheDefaultSettingsInListOrder() {
        loader.createAdminSettings();

        ArgumentCaptor<AdminSettings> saved = ArgumentCaptor.forClass(AdminSettings.class);
        verify(adminSettingsService, times(5)).saveAdminSettings(saved.capture());
        assertThat(saved.getAllValues()).usingRecursiveFieldByFieldElementComparator()
                .containsExactlyElementsOf(DefaultSystemDataLoaderService.defaultAdminSettings());
    }

    @Test
    void theInstallSavesTheDefaultAuthProvidersInListOrder() {
        loader.createMqttAuthProviders();

        ArgumentCaptor<MqttAuthProvider> saved = ArgumentCaptor.forClass(MqttAuthProvider.class);
        verify(mqttAuthProviderService, times(5)).saveAuthProvider(saved.capture());
        List<MqttAuthProvider> expected = DefaultSystemDataLoaderService.defaultAuthProviders();
        assertThat(saved.getAllValues()).usingRecursiveFieldByFieldElementComparator().containsExactlyElementsOf(expected);
    }

}
