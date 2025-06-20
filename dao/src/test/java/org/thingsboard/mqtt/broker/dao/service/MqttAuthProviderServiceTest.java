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
package org.thingsboard.mqtt.broker.dao.service;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.security.basic.BasicMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttAuthProviderService;
import org.thingsboard.mqtt.broker.exception.DataValidationException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DaoSqlTest
public class MqttAuthProviderServiceTest extends AbstractServiceTest {

    private MqttAuthProvider basicAuthProvider;

    @Autowired
    private MqttAuthProviderService mqttAuthProviderService;

    @Before
    public void setUp() {
        Optional<MqttAuthProvider> basicProviderOpt = mqttAuthProviderService.getAuthProviderByType(MqttAuthProviderType.MQTT_BASIC);
        assertThat(basicProviderOpt).isPresent();
        basicAuthProvider = basicProviderOpt.get();
    }

    @After
    public void tearDown() {
        if (basicAuthProvider == null) {
            return;
        }
        basicAuthProvider.setEnabled(false);
        basicAuthProvider.setType(MqttAuthProviderType.MQTT_BASIC);
        basicAuthProvider.setConfiguration(new BasicMqttAuthProviderConfiguration());
        mqttAuthProviderService.saveAuthProvider(basicAuthProvider);
    }

    @Test
    public void testUpdateMqttAuthProvider() {
        assertThat(basicAuthProvider).isNotNull();
        assertThat(basicAuthProvider.isEnabled()).isFalse();

        basicAuthProvider.setEnabled(true);
        assertThatCode(() -> {
            MqttAuthProvider updatedBasicAuthProvider = mqttAuthProviderService.saveAuthProvider(basicAuthProvider);
            assertThat(updatedBasicAuthProvider).isNotNull();
            assertThat(updatedBasicAuthProvider.getId()).isEqualTo(basicAuthProvider.getId());
            assertThat(updatedBasicAuthProvider.isEnabled()).isTrue();
        }).doesNotThrowAnyException();

        mqttAuthProviderService.disableAuthProvider(basicAuthProvider.getId());

        Optional<MqttAuthProvider> disabledBasicProviderOpt = mqttAuthProviderService.getAuthProviderById(basicAuthProvider.getId());
        assertThat(disabledBasicProviderOpt).isPresent();
        assertThat(disabledBasicProviderOpt.get().getId()).isEqualTo(basicAuthProvider.getId());
        assertThat(disabledBasicProviderOpt.get().isEnabled()).isFalse();
    }

    @Test
    public void testUpdateNonExistingMqttAuthProvider() {
        MqttAuthProvider mqttAuthProvider = getNewBasicMqttAuthProvider();
        mqttAuthProvider.setId(UUID.fromString("e993d875-2e5b-48f2-ba6b-63074800a3ce"));
        assertThatThrownBy(() -> mqttAuthProviderService.saveAuthProvider(mqttAuthProvider))
                .isInstanceOf(DataValidationException.class)
                .hasMessage("Unable to update non-existent MQTT auth provider!");
    }

    @Test
    public void testUpdateMqttAuthProviderType() {
        assertThat(basicAuthProvider).isNotNull();
        assertThat(basicAuthProvider.getType()).isEqualTo(MqttAuthProviderType.MQTT_BASIC);

        basicAuthProvider.setType(MqttAuthProviderType.X_509);

        assertThatThrownBy(() -> mqttAuthProviderService.saveAuthProvider(basicAuthProvider))
                .isInstanceOf(DataValidationException.class)
                .hasMessage("MQTT auth provider type can't be changed!");
    }

    @Test
    public void testSaveMqttAuthProviderWithNullType() {
        MqttAuthProvider mqttAuthProvider = getNewBasicMqttAuthProvider(null, new BasicMqttAuthProviderConfiguration());
        assertThatThrownBy(() -> mqttAuthProviderService.saveAuthProvider(mqttAuthProvider))
                .isInstanceOf(DataValidationException.class)
                .hasMessage("MQTT auth provider type should be specified!");
    }

    @Test
    public void testSaveMqttAuthProviderWithNullConfiguration() {
        MqttAuthProvider mqttAuthProvider = getNewBasicMqttAuthProvider(MqttAuthProviderType.MQTT_BASIC, null);
        assertThatThrownBy(() -> mqttAuthProviderService.saveAuthProvider(mqttAuthProvider))
                .isInstanceOf(DataValidationException.class)
                .hasMessage("MQTT auth provider configuration should be specified!");
    }

    @Test
    public void testSaveMqttAuthProviderWithExistingType() {
        MqttAuthProvider anotherBasicMqttAuthProvider = getNewBasicMqttAuthProvider();
        assertThatThrownBy(() -> mqttAuthProviderService.saveAuthProvider(anotherBasicMqttAuthProvider))
                .isInstanceOf(DataValidationException.class)
                .hasMessage("MQTT auth provider with such type already registered!");
    }

    @Test
    public void testFindMqttAuthProviderById() {
        assertThat(basicAuthProvider).isNotNull();
        assertThat(basicAuthProvider.getId()).isNotNull();

        Optional<MqttAuthProvider> foundAuthProvider = mqttAuthProviderService.getAuthProviderById(basicAuthProvider.getId());
        assertThat(foundAuthProvider.isPresent()).isTrue();
        assertThat(foundAuthProvider.get()).isEqualTo(basicAuthProvider);
    }

    @Test
    public void testFindMqttAuthProviderByType() {
        assertThat(basicAuthProvider).isNotNull();
        assertThat(basicAuthProvider.getId()).isNotNull();

        Optional<MqttAuthProvider> foundAuthProvider = mqttAuthProviderService.getAuthProviderByType(MqttAuthProviderType.MQTT_BASIC);
        assertThat(foundAuthProvider.isPresent()).isTrue();
        assertThat(foundAuthProvider.get()).isEqualTo(basicAuthProvider);
    }


    @Test
    public void testDeleteNonExistingMqttAuthProvider() {
        assertThat(mqttAuthProviderService.deleteAuthProvider(UUID.fromString("9b519fe5-4712-4926-9e24-debf24a7c660"))).isFalse();
    }

    @Test
    public void testEnableMqttAuthProvider() {
        assertThat(basicAuthProvider).isNotNull();
        assertThat(basicAuthProvider.getId()).isNotNull();
        assertThat(basicAuthProvider.isEnabled()).isFalse();

        assertThat(mqttAuthProviderService.enableAuthProvider(basicAuthProvider.getId())).isPresent();

        Optional<MqttAuthProvider> authProviderById = mqttAuthProviderService.getAuthProviderById(basicAuthProvider.getId());
        assertThat(authProviderById.isPresent()).isTrue();
        assertThat(authProviderById.get().isEnabled()).isTrue();
    }

    @Test
    public void testEnableAlreadyEnabledMqttAuthProvider() {
        assertThat(basicAuthProvider).isNotNull();
        assertThat(basicAuthProvider.getId()).isNotNull();
        assertThat(basicAuthProvider.isEnabled()).isFalse();

        basicAuthProvider.setEnabled(true);

        // save enabled
        MqttAuthProvider savedAuthProvider = mqttAuthProviderService.saveAuthProvider(basicAuthProvider);

        assertThat(savedAuthProvider).isNotNull();
        assertThat(savedAuthProvider.getType()).isEqualTo(MqttAuthProviderType.MQTT_BASIC);
        assertThat(savedAuthProvider.getConfiguration()).isNotNull();
        assertThat(savedAuthProvider.isEnabled()).isTrue();

        assertThat(mqttAuthProviderService.enableAuthProvider(savedAuthProvider.getId())).isEmpty();

        Optional<MqttAuthProvider> authProviderById = mqttAuthProviderService.getAuthProviderById(savedAuthProvider.getId());
        assertThat(authProviderById.isPresent()).isTrue();
        assertThat(authProviderById.get().isEnabled()).isTrue();
    }


    @Test
    public void testEnableNonExistingMqttAuthProvider() {
        assertThatThrownBy(() -> mqttAuthProviderService.enableAuthProvider(UUID.fromString("ab196e2f-e6bf-4138-8f4e-95de7748fc0a")))
                .isInstanceOf(DataValidationException.class)
                .hasMessage("Unable to enable non-existent MQTT auth provider!");
    }

    @Test
    public void testDisableMqttAuthProvider() {
        assertThat(basicAuthProvider).isNotNull();
        assertThat(basicAuthProvider.getId()).isNotNull();
        assertThat(basicAuthProvider.isEnabled()).isFalse();

        basicAuthProvider.setEnabled(true);

        // save enabled
        MqttAuthProvider savedAuthProvider = mqttAuthProviderService.saveAuthProvider(basicAuthProvider);

        assertThat(savedAuthProvider).isNotNull();
        assertThat(savedAuthProvider.getType()).isEqualTo(MqttAuthProviderType.MQTT_BASIC);
        assertThat(savedAuthProvider.getConfiguration()).isNotNull();
        assertThat(savedAuthProvider.isEnabled()).isTrue();

        assertThat(mqttAuthProviderService.disableAuthProvider(savedAuthProvider.getId())).isPresent();

        Optional<MqttAuthProvider> authProviderById = mqttAuthProviderService.getAuthProviderById(savedAuthProvider.getId());
        assertThat(authProviderById.isPresent()).isTrue();
        assertThat(authProviderById.get().isEnabled()).isFalse();
    }

    @Test
    public void testDisableAlreadyDisabledMqttAuthProvider() {
        assertThat(basicAuthProvider).isNotNull();
        assertThat(basicAuthProvider.getId()).isNotNull();
        assertThat(basicAuthProvider.isEnabled()).isFalse();

        assertThat(mqttAuthProviderService.disableAuthProvider(basicAuthProvider.getId())).isEmpty();

        Optional<MqttAuthProvider> authProviderById = mqttAuthProviderService.getAuthProviderById(basicAuthProvider.getId());
        assertThat(authProviderById.isPresent()).isTrue();
        assertThat(authProviderById.get().isEnabled()).isFalse();
    }

    @Test
    public void testDisableNonExistingMqttAuthProvider() {
        assertThatThrownBy(() -> mqttAuthProviderService.disableAuthProvider(UUID.fromString("6e2fb006-d5ad-4539-ad6a-203abff9521b")))
                .isInstanceOf(DataValidationException.class)
                .hasMessage("Unable to disable non-existent MQTT auth provider!");
    }


    private MqttAuthProvider getNewBasicMqttAuthProvider() {
        return getNewBasicMqttAuthProvider(MqttAuthProviderType.MQTT_BASIC, new BasicMqttAuthProviderConfiguration());
    }

    private MqttAuthProvider getNewBasicMqttAuthProvider(MqttAuthProviderType type, MqttAuthProviderConfiguration configuration) {
        var mqttAuthProvider = new MqttAuthProvider();
        mqttAuthProvider.setType(type);
        mqttAuthProvider.setConfiguration(configuration);
        return mqttAuthProvider;
    }

}
