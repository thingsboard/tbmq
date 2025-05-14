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
import org.thingsboard.mqtt.broker.common.data.dto.ShortMqttClientAuthProvider;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientAuthProviderDto;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.security.basic.BasicAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttClientAuthProviderService;
import org.thingsboard.mqtt.broker.exception.DataValidationException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DaoSqlTest
public class MqttClientAuthProviderServiceTest extends AbstractServiceTest {

    @Autowired
    private MqttClientAuthProviderService mqttClientAuthProviderService;

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() throws Exception {
        PageData<ShortMqttClientAuthProvider> authProviders = mqttClientAuthProviderService.getAuthProviders(new PageLink(1000));
        for (var provider : authProviders.getData()) {
            mqttClientAuthProviderService.deleteAuthProvider(provider.getId());
        }
    }

    @Test
    public void testSaveMqttClientAuthProvider() {
        MqttClientAuthProviderDto mqttClientAuthProvider = getMqttClientAuthProvider();
        assertThatCode(() -> mqttClientAuthProviderService.saveAuthProvider(mqttClientAuthProvider)).doesNotThrowAnyException();
    }

    @Test
    public void testUpdateNonExistingMqttClientAuthProvider() {
        MqttClientAuthProviderDto mqttClientAuthProvider = getMqttClientAuthProvider();
        mqttClientAuthProvider.setId(UUID.fromString("e993d875-2e5b-48f2-ba6b-63074800a3ce"));
        assertThatThrownBy(() -> mqttClientAuthProviderService.saveAuthProvider(mqttClientAuthProvider))
                .isInstanceOf(DataValidationException.class)
                .hasMessage("Unable to update non-existent MQTT client auth provider!");
    }

    @Test
    public void testUpdateMqttClientAuthProviderType() {
        MqttClientAuthProviderDto mqttClientAuthProvider = getMqttClientAuthProvider();
        MqttClientAuthProviderDto savedAuthProvider = mqttClientAuthProviderService.saveAuthProvider(mqttClientAuthProvider);
        assertThat(savedAuthProvider).isNotNull();

        savedAuthProvider.setType(MqttClientAuthProviderType.SSL);

        assertThatThrownBy(() -> mqttClientAuthProviderService.saveAuthProvider(savedAuthProvider))
                .isInstanceOf(DataValidationException.class)
                .hasMessage("MQTT client auth provider type can't be changed!");
    }

    @Test
    public void testSaveMqttClientAuthProviderWithNullType() {
        MqttClientAuthProviderDto mqttClientAuthProvider = getMqttClientAuthProvider(null, new BasicAuthProviderConfiguration());
        assertThatThrownBy(() -> mqttClientAuthProviderService.saveAuthProvider(mqttClientAuthProvider))
                .isInstanceOf(DataValidationException.class)
                .hasMessage("MQTT client auth provider type should be specified!");
    }

    @Test
    public void testSaveMqttClientAuthProviderWithNullConfiguration() {
        MqttClientAuthProviderDto mqttClientAuthProvider = getMqttClientAuthProvider(MqttClientAuthProviderType.BASIC, null);
        assertThatThrownBy(() -> mqttClientAuthProviderService.saveAuthProvider(mqttClientAuthProvider))
                .isInstanceOf(DataValidationException.class)
                .hasMessage("MQTT client auth provider configuration should be specified!");
    }

    @Test
    public void testSaveMqttClientAuthProviderWithExistingType() {
        MqttClientAuthProviderDto mqttClientAuthProvider = getMqttClientAuthProvider();

        MqttClientAuthProviderDto savedAuthProvider = mqttClientAuthProviderService.saveAuthProvider(mqttClientAuthProvider);
        assertThat(savedAuthProvider).isNotNull();

        MqttClientAuthProviderDto anotherBasicMqttClientAuthProvider = getMqttClientAuthProvider();

        assertThatThrownBy(() -> mqttClientAuthProviderService.saveAuthProvider(anotherBasicMqttClientAuthProvider))
                .isInstanceOf(DataValidationException.class)
                .hasMessage("MQTT client auth provider with such type already registered!");
    }

    @Test
    public void testFindMqttClientAuthProvider() {
        MqttClientAuthProviderDto mqttClientAuthProvider = getMqttClientAuthProvider();

        MqttClientAuthProviderDto savedAuthProvider = mqttClientAuthProviderService.saveAuthProvider(mqttClientAuthProvider);
        assertThat(savedAuthProvider).isNotNull();
        assertThat(savedAuthProvider.getType()).isEqualTo(MqttClientAuthProviderType.BASIC);
        assertThat(savedAuthProvider.getConfiguration()).isNotNull();
        assertThat(savedAuthProvider.isEnabled()).isFalse();

        Optional<MqttClientAuthProviderDto> foundAuthProvider = mqttClientAuthProviderService.getAuthProviderById(savedAuthProvider.getId());
        assertThat(foundAuthProvider.isPresent()).isTrue();
        assertThat(foundAuthProvider.get()).isEqualTo(savedAuthProvider);
    }

    @Test
    public void testFindEnabledMqttClientAuthProviders() {
        MqttClientAuthProviderDto mqttClientAuthProvider = getMqttClientAuthProvider();

        MqttClientAuthProviderDto savedAuthProvider = mqttClientAuthProviderService.saveAuthProvider(mqttClientAuthProvider);
        assertThat(savedAuthProvider).isNotNull();
        assertThat(savedAuthProvider.getType()).isEqualTo(MqttClientAuthProviderType.BASIC);
        assertThat(savedAuthProvider.getConfiguration()).isNotNull();
        assertThat(savedAuthProvider.isEnabled()).isFalse();

        PageData<MqttClientAuthProviderDto> enabledAuthProviders = mqttClientAuthProviderService.getEnabledAuthProviders(new PageLink(1000));
        assertThat(enabledAuthProviders.getTotalElements()).isEqualTo(0);

        mqttClientAuthProviderService.enableAuthProvider(savedAuthProvider.getId());

        enabledAuthProviders = mqttClientAuthProviderService.getEnabledAuthProviders(new PageLink(1000));
        assertThat(enabledAuthProviders.getTotalElements()).isEqualTo(1);
        assertThat(enabledAuthProviders.getData().get(0).getId()).isEqualTo(savedAuthProvider.getId());
    }

    @Test
    public void testDeleteNonExistingMqttClientAuthProvider() {
        assertThat(mqttClientAuthProviderService.deleteAuthProvider(UUID.fromString("9b519fe5-4712-4926-9e24-debf24a7c660"))).isFalse();
    }

    @Test
    public void testEnableMqttClientAuthProvider() {
        MqttClientAuthProviderDto mqttClientAuthProvider = getMqttClientAuthProvider();

        // save disabled
        MqttClientAuthProviderDto savedAuthProvider = mqttClientAuthProviderService.saveAuthProvider(mqttClientAuthProvider);

        assertThat(savedAuthProvider).isNotNull();
        assertThat(savedAuthProvider.getType()).isEqualTo(MqttClientAuthProviderType.BASIC);
        assertThat(savedAuthProvider.getConfiguration()).isNotNull();
        assertThat(savedAuthProvider.isEnabled()).isFalse();

        assertThat(mqttClientAuthProviderService.enableAuthProvider(savedAuthProvider.getId())).isTrue();

        Optional<MqttClientAuthProviderDto> authProviderById = mqttClientAuthProviderService.getAuthProviderById(savedAuthProvider.getId());
        assertThat(authProviderById.isPresent()).isTrue();
        assertThat(authProviderById.get().isEnabled()).isTrue();
    }

    @Test
    public void testEnableAlreadyEnabledMqttClientAuthProvider() {
        MqttClientAuthProviderDto mqttClientAuthProvider = getMqttClientAuthProvider();
        mqttClientAuthProvider.setEnabled(true);

        // save enabled
        MqttClientAuthProviderDto savedAuthProvider = mqttClientAuthProviderService.saveAuthProvider(mqttClientAuthProvider);

        assertThat(savedAuthProvider).isNotNull();
        assertThat(savedAuthProvider.getType()).isEqualTo(MqttClientAuthProviderType.BASIC);
        assertThat(savedAuthProvider.getConfiguration()).isNotNull();
        assertThat(savedAuthProvider.isEnabled()).isTrue();

        assertThat(mqttClientAuthProviderService.enableAuthProvider(savedAuthProvider.getId())).isFalse();

        Optional<MqttClientAuthProviderDto> authProviderById = mqttClientAuthProviderService.getAuthProviderById(savedAuthProvider.getId());
        assertThat(authProviderById.isPresent()).isTrue();
        assertThat(authProviderById.get().isEnabled()).isTrue();
    }


    @Test
    public void testEnableNonExistingMqttClientAuthProvider() {
        assertThatThrownBy(() -> mqttClientAuthProviderService.enableAuthProvider(UUID.fromString("ab196e2f-e6bf-4138-8f4e-95de7748fc0a")))
                .isInstanceOf(DataValidationException.class)
                .hasMessage("Unable to enable non-existent MQTT client auth provider!");
    }

    @Test
    public void testDisableMqttClientAuthProvider() {
        MqttClientAuthProviderDto mqttClientAuthProvider = getMqttClientAuthProvider();
        mqttClientAuthProvider.setEnabled(true);

        // save enabled
        MqttClientAuthProviderDto savedAuthProvider = mqttClientAuthProviderService.saveAuthProvider(mqttClientAuthProvider);

        assertThat(savedAuthProvider).isNotNull();
        assertThat(savedAuthProvider.getType()).isEqualTo(MqttClientAuthProviderType.BASIC);
        assertThat(savedAuthProvider.getConfiguration()).isNotNull();
        assertThat(savedAuthProvider.isEnabled()).isTrue();

        assertThat(mqttClientAuthProviderService.disableAuthProvider(savedAuthProvider.getId())).isTrue();

        Optional<MqttClientAuthProviderDto> authProviderById = mqttClientAuthProviderService.getAuthProviderById(savedAuthProvider.getId());
        assertThat(authProviderById.isPresent()).isTrue();
        assertThat(authProviderById.get().isEnabled()).isFalse();
    }

    @Test
    public void testDisableAlreadyDisabledMqttClientAuthProvider() {
        MqttClientAuthProviderDto mqttClientAuthProvider = getMqttClientAuthProvider();

        // save enabled
        MqttClientAuthProviderDto savedAuthProvider = mqttClientAuthProviderService.saveAuthProvider(mqttClientAuthProvider);

        assertThat(savedAuthProvider).isNotNull();
        assertThat(savedAuthProvider.getType()).isEqualTo(MqttClientAuthProviderType.BASIC);
        assertThat(savedAuthProvider.getConfiguration()).isNotNull();
        assertThat(savedAuthProvider.isEnabled()).isFalse();

        assertThat(mqttClientAuthProviderService.disableAuthProvider(savedAuthProvider.getId())).isFalse();

        Optional<MqttClientAuthProviderDto> authProviderById = mqttClientAuthProviderService.getAuthProviderById(savedAuthProvider.getId());
        assertThat(authProviderById.isPresent()).isTrue();
        assertThat(authProviderById.get().isEnabled()).isFalse();
    }

    @Test
    public void testDisableNonExistingMqttClientAuthProvider() {
        assertThatThrownBy(() -> mqttClientAuthProviderService.disableAuthProvider(UUID.fromString("6e2fb006-d5ad-4539-ad6a-203abff9521b")))
                .isInstanceOf(DataValidationException.class)
                .hasMessage("Unable to disable non-existent MQTT client auth provider!");
    }



    private MqttClientAuthProviderDto getMqttClientAuthProvider() {
        return getMqttClientAuthProvider(MqttClientAuthProviderType.BASIC, new BasicAuthProviderConfiguration());
    }

    private MqttClientAuthProviderDto getMqttClientAuthProvider(MqttClientAuthProviderType type, MqttClientAuthProviderConfiguration configuration) {
        var mqttClientAuthProvider = new MqttClientAuthProviderDto();
        mqttClientAuthProvider.setType(type);
        mqttClientAuthProvider.setConfiguration(configuration);
        return mqttClientAuthProvider;
    }

}
