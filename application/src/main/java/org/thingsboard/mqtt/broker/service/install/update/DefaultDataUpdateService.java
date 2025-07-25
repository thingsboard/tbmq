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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.common.data.SysAdminSettingType;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.security.ssl.MqttClientAuthType;
import org.thingsboard.mqtt.broker.common.data.security.ssl.SslMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttAuthProviderService;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsService;
import org.thingsboard.mqtt.broker.service.install.data.MqttAuthSettings;

import java.util.Optional;

@Service
@Profile("install")
@Slf4j
@RequiredArgsConstructor
public class DefaultDataUpdateService implements DataUpdateService {

    @Value("${security.mqtt.basic.enabled:false}")
    private boolean basicAuthEnabled;
    @Value("${security.mqtt.ssl.enabled:false}")
    private boolean x509AuthEnabled;
    @Value("${security.mqtt.ssl.skip_validity_check_for_client_cert:false}")
    private boolean skipValidityCheckForClientCert;

    private final AdminSettingsService adminSettingsService;
    private final MqttAuthProviderService mqttAuthProviderService;

    @Override
    public void updateData() throws Exception {
        log.info("Updating data ...");
        //TODO: should be cleaned after each release
        createMqttAuthSettingsIfNotExist();
        createMqttAuthProvidersIfNotExist();
        log.info("Data updated.");
    }

    private void createMqttAuthSettingsIfNotExist() {
        log.info("Starting MQTT auth setting creation...");
        AdminSettings settings = adminSettingsService.findAdminSettingsByKey(SysAdminSettingType.MQTT_AUTHORIZATION.getKey());
        if (settings != null) {
            log.info("MQTT auth settings already exists. Skipping!");
            return;
        }
        MqttAuthSettings mqttAuthSettings = new MqttAuthSettings();
        mqttAuthSettings.setPriorities(MqttAuthProviderType.defaultPriorityList);
        AdminSettings adminSettings = MqttAuthSettings.toAdminSettings(mqttAuthSettings);
        adminSettingsService.saveAdminSettings(adminSettings);
        log.info("Finished MQTT auth setting creation!");
    }

    private void createMqttAuthProvidersIfNotExist() {
        log.info("Starting MQTT auth providers creation...");
        for (var type : MqttAuthProviderType.values()) {
            Optional<MqttAuthProvider> mqttAuthProviderOpt = mqttAuthProviderService.getAuthProviderByType(type);
            if (mqttAuthProviderOpt.isPresent()) {
                log.info("Mqtt auth provider: {} already exists. Skipping!", type);
                continue;
            }
            log.info("Creating {} auth provider...", type.getDisplayName());
            MqttAuthProvider mqttAuthProvider = switch (type) {
                case MQTT_BASIC -> MqttAuthProvider.defaultBasicAuthProvider(isBasicAuthEnabled());
                case SCRAM -> MqttAuthProvider.defaultScramAuthProvider(true);
                case JWT -> MqttAuthProvider.defaultJwtAuthProvider(false);
                case X_509 -> {
                    MqttAuthProvider x509AuthProvider = MqttAuthProvider.defaultSslAuthProvider(isX509AuthEnabled());
                    var configuration = (SslMqttAuthProviderConfiguration) x509AuthProvider.getConfiguration();
                    configuration.setSkipValidityCheckForClientCert(isX509SkipValidityCheckForClientCertIsSetToTrue());
                    configuration.setClientAuthType(MqttClientAuthType.CLIENT_AUTH_REQUESTED);
                    x509AuthProvider.setConfiguration(configuration);
                    yield x509AuthProvider;
                }
            };
            mqttAuthProviderService.saveAuthProvider(mqttAuthProvider);
            log.info("Created {} auth provider!", type.getDisplayName());
        }
        log.info("Finished MQTT auth providers creation!");
    }

    boolean isBasicAuthEnabled() {
        return getLegacyConfig("SECURITY_MQTT_BASIC_ENABLED", basicAuthEnabled);
    }

    boolean isX509AuthEnabled() {
        return getLegacyConfig("SECURITY_MQTT_SSL_ENABLED", x509AuthEnabled);
    }

    boolean isX509SkipValidityCheckForClientCertIsSetToTrue() {
        return getLegacyConfig("SECURITY_MQTT_SSL_SKIP_VALIDITY_CHECK_FOR_CLIENT_CERT", skipValidityCheckForClientCert);
    }

    boolean getLegacyConfig(String env, boolean ymlProperty) {
        return Optional.ofNullable(System.getenv(env)).map(Boolean::parseBoolean).orElse(ymlProperty);
    }

}
