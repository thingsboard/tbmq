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
import org.thingsboard.mqtt.broker.common.data.dto.ShortMqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
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

    @Autowired
    private MqttAuthProviderService mqttAuthProviderService;

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() throws Exception {
        PageData<ShortMqttAuthProvider> authProviders = mqttAuthProviderService.getAuthProviders(new PageLink(1000));
        for (var provider : authProviders.getData()) {
            mqttAuthProviderService.deleteAuthProvider(provider.getId());
        }
    }

    @Test
    public void testSaveMqttAuthProvider() {
        MqttAuthProvider mqttAuthProvider = getMqttAuthProvider();
        assertThatCode(() -> mqttAuthProviderService.saveAuthProvider(mqttAuthProvider)).doesNotThrowAnyException();
    }

    @Test
    public void testUpdateNonExistingMqttAuthProvider() {
        MqttAuthProvider mqttAuthProvider = getMqttAuthProvider();
        mqttAuthProvider.setId(UUID.fromString("e993d875-2e5b-48f2-ba6b-63074800a3ce"));
        assertThatThrownBy(() -> mqttAuthProviderService.saveAuthProvider(mqttAuthProvider))
                .isInstanceOf(DataValidationException.class)
                .hasMessage("Unable to update non-existent MQTT client auth provider!");
    }

    @Test
    public void testUpdateMqttAuthProviderType() {
        MqttAuthProvider mqttAuthProvider = getMqttAuthProvider();
        MqttAuthProvider savedAuthProvider = mqttAuthProviderService.saveAuthProvider(mqttAuthProvider);
        assertThat(savedAuthProvider).isNotNull();

        savedAuthProvider.setType(MqttAuthProviderType.SSL);

        assertThatThrownBy(() -> mqttAuthProviderService.saveAuthProvider(savedAuthProvider))
                .isInstanceOf(DataValidationException.class)
                .hasMessage("MQTT client auth provider type can't be changed!");
    }

    @Test
    public void testSaveMqttAuthProviderWithNullType() {
        MqttAuthProvider mqttAuthProvider = getMqttAuthProvider(null, new BasicMqttAuthProviderConfiguration());
        assertThatThrownBy(() -> mqttAuthProviderService.saveAuthProvider(mqttAuthProvider))
                .isInstanceOf(DataValidationException.class)
                .hasMessage("MQTT client auth provider type should be specified!");
    }

    @Test
    public void testSaveMqttAuthProviderWithNullConfiguration() {
        MqttAuthProvider mqttAuthProvider = getMqttAuthProvider(MqttAuthProviderType.BASIC, null);
        assertThatThrownBy(() -> mqttAuthProviderService.saveAuthProvider(mqttAuthProvider))
                .isInstanceOf(DataValidationException.class)
                .hasMessage("MQTT client auth provider configuration should be specified!");
    }

    @Test
    public void testSaveMqttAuthProviderWithExistingType() {
        MqttAuthProvider mqttAuthProvider = getMqttAuthProvider();

        MqttAuthProvider savedAuthProvider = mqttAuthProviderService.saveAuthProvider(mqttAuthProvider);
        assertThat(savedAuthProvider).isNotNull();

        MqttAuthProvider anotherBasicMqttAuthProvider = getMqttAuthProvider();

        assertThatThrownBy(() -> mqttAuthProviderService.saveAuthProvider(anotherBasicMqttAuthProvider))
                .isInstanceOf(DataValidationException.class)
                .hasMessage("MQTT client auth provider with such type already registered!");
    }

    @Test
    public void testFindMqttAuthProvider() {
        MqttAuthProvider mqttAuthProvider = getMqttAuthProvider();

        MqttAuthProvider savedAuthProvider = mqttAuthProviderService.saveAuthProvider(mqttAuthProvider);
        assertThat(savedAuthProvider).isNotNull();
        assertThat(savedAuthProvider.getType()).isEqualTo(MqttAuthProviderType.BASIC);
        assertThat(savedAuthProvider.getConfiguration()).isNotNull();
        assertThat(savedAuthProvider.isEnabled()).isFalse();

        Optional<MqttAuthProvider> foundAuthProvider = mqttAuthProviderService.getAuthProviderById(savedAuthProvider.getId());
        assertThat(foundAuthProvider.isPresent()).isTrue();
        assertThat(foundAuthProvider.get()).isEqualTo(savedAuthProvider);
    }

    @Test
    public void testFindEnabledMqttAuthProviders() {
        MqttAuthProvider mqttAuthProvider = getMqttAuthProvider();

        MqttAuthProvider savedAuthProvider = mqttAuthProviderService.saveAuthProvider(mqttAuthProvider);
        assertThat(savedAuthProvider).isNotNull();
        assertThat(savedAuthProvider.getType()).isEqualTo(MqttAuthProviderType.BASIC);
        assertThat(savedAuthProvider.getConfiguration()).isNotNull();
        assertThat(savedAuthProvider.isEnabled()).isFalse();

        PageData<MqttAuthProvider> enabledAuthProviders = mqttAuthProviderService.getEnabledAuthProviders(new PageLink(1000));
        assertThat(enabledAuthProviders.getTotalElements()).isEqualTo(0);

        mqttAuthProviderService.enableAuthProvider(savedAuthProvider.getId());

        enabledAuthProviders = mqttAuthProviderService.getEnabledAuthProviders(new PageLink(1000));
        assertThat(enabledAuthProviders.getTotalElements()).isEqualTo(1);
        assertThat(enabledAuthProviders.getData().get(0).getId()).isEqualTo(savedAuthProvider.getId());
    }

    @Test
    public void testDeleteNonExistingMqttAuthProvider() {
        assertThat(mqttAuthProviderService.deleteAuthProvider(UUID.fromString("9b519fe5-4712-4926-9e24-debf24a7c660"))).isFalse();
    }

    @Test
    public void testEnableMqttAuthProvider() {
        MqttAuthProvider mqttAuthProvider = getMqttAuthProvider();

        // save disabled
        MqttAuthProvider savedAuthProvider = mqttAuthProviderService.saveAuthProvider(mqttAuthProvider);

        assertThat(savedAuthProvider).isNotNull();
        assertThat(savedAuthProvider.getType()).isEqualTo(MqttAuthProviderType.BASIC);
        assertThat(savedAuthProvider.getConfiguration()).isNotNull();
        assertThat(savedAuthProvider.isEnabled()).isFalse();

        assertThat(mqttAuthProviderService.enableAuthProvider(savedAuthProvider.getId())).isTrue();

        Optional<MqttAuthProvider> authProviderById = mqttAuthProviderService.getAuthProviderById(savedAuthProvider.getId());
        assertThat(authProviderById.isPresent()).isTrue();
        assertThat(authProviderById.get().isEnabled()).isTrue();
    }

    @Test
    public void testEnableAlreadyEnabledMqttAuthProvider() {
        MqttAuthProvider mqttAuthProvider = getMqttAuthProvider();
        mqttAuthProvider.setEnabled(true);

        // save enabled
        MqttAuthProvider savedAuthProvider = mqttAuthProviderService.saveAuthProvider(mqttAuthProvider);

        assertThat(savedAuthProvider).isNotNull();
        assertThat(savedAuthProvider.getType()).isEqualTo(MqttAuthProviderType.BASIC);
        assertThat(savedAuthProvider.getConfiguration()).isNotNull();
        assertThat(savedAuthProvider.isEnabled()).isTrue();

        assertThat(mqttAuthProviderService.enableAuthProvider(savedAuthProvider.getId())).isFalse();

        Optional<MqttAuthProvider> authProviderById = mqttAuthProviderService.getAuthProviderById(savedAuthProvider.getId());
        assertThat(authProviderById.isPresent()).isTrue();
        assertThat(authProviderById.get().isEnabled()).isTrue();
    }


    @Test
    public void testEnableNonExistingMqttAuthProvider() {
        assertThatThrownBy(() -> mqttAuthProviderService.enableAuthProvider(UUID.fromString("ab196e2f-e6bf-4138-8f4e-95de7748fc0a")))
                .isInstanceOf(DataValidationException.class)
                .hasMessage("Unable to enable non-existent MQTT client auth provider!");
    }

    @Test
    public void testDisableMqttAuthProvider() {
        MqttAuthProvider mqttAuthProvider = getMqttAuthProvider();
        mqttAuthProvider.setEnabled(true);

        // save enabled
        MqttAuthProvider savedAuthProvider = mqttAuthProviderService.saveAuthProvider(mqttAuthProvider);

        assertThat(savedAuthProvider).isNotNull();
        assertThat(savedAuthProvider.getType()).isEqualTo(MqttAuthProviderType.BASIC);
        assertThat(savedAuthProvider.getConfiguration()).isNotNull();
        assertThat(savedAuthProvider.isEnabled()).isTrue();

        assertThat(mqttAuthProviderService.disableAuthProvider(savedAuthProvider.getId())).isTrue();

        Optional<MqttAuthProvider> authProviderById = mqttAuthProviderService.getAuthProviderById(savedAuthProvider.getId());
        assertThat(authProviderById.isPresent()).isTrue();
        assertThat(authProviderById.get().isEnabled()).isFalse();
    }

    @Test
    public void testDisableAlreadyDisabledMqttAuthProvider() {
        MqttAuthProvider mqttAuthProvider = getMqttAuthProvider();

        // save enabled
        MqttAuthProvider savedAuthProvider = mqttAuthProviderService.saveAuthProvider(mqttAuthProvider);

        assertThat(savedAuthProvider).isNotNull();
        assertThat(savedAuthProvider.getType()).isEqualTo(MqttAuthProviderType.BASIC);
        assertThat(savedAuthProvider.getConfiguration()).isNotNull();
        assertThat(savedAuthProvider.isEnabled()).isFalse();

        assertThat(mqttAuthProviderService.disableAuthProvider(savedAuthProvider.getId())).isFalse();

        Optional<MqttAuthProvider> authProviderById = mqttAuthProviderService.getAuthProviderById(savedAuthProvider.getId());
        assertThat(authProviderById.isPresent()).isTrue();
        assertThat(authProviderById.get().isEnabled()).isFalse();
    }

    @Test
    public void testDisableNonExistingMqttAuthProvider() {
        assertThatThrownBy(() -> mqttAuthProviderService.disableAuthProvider(UUID.fromString("6e2fb006-d5ad-4539-ad6a-203abff9521b")))
                .isInstanceOf(DataValidationException.class)
                .hasMessage("Unable to disable non-existent MQTT client auth provider!");
    }



    private MqttAuthProvider getMqttAuthProvider() {
        return getMqttAuthProvider(MqttAuthProviderType.BASIC, new BasicMqttAuthProviderConfiguration());
    }

    private MqttAuthProvider getMqttAuthProvider(MqttAuthProviderType type, MqttAuthProviderConfiguration configuration) {
        var mqttAuthProvider = new MqttAuthProvider();
        mqttAuthProvider.setType(type);
        mqttAuthProvider.setConfiguration(configuration);
        return mqttAuthProvider;
    }

}
