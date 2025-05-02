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
import org.thingsboard.mqtt.broker.common.data.security.MqttClientAuthProvider;
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
        MqttClientAuthProvider mqttClientAuthProvider = getMqttClientAuthProvider();
        assertThatCode(() -> mqttClientAuthProviderService.saveAuthProvider(mqttClientAuthProvider)).doesNotThrowAnyException();
    }

    @Test
    public void testUpdateNonExistingMqttClientAuthProvider() {
        MqttClientAuthProvider mqttClientAuthProvider = getMqttClientAuthProvider();
        mqttClientAuthProvider.setId(UUID.fromString("e993d875-2e5b-48f2-ba6b-63074800a3ce"));
        assertThatThrownBy(() -> mqttClientAuthProviderService.saveAuthProvider(mqttClientAuthProvider))
                .isInstanceOf(DataValidationException.class)
                .hasMessage("Unable to update non-existent MQTT Client auth provider!");
    }

    @Test
    public void testUpdateMqttClientAuthProviderType() {
        MqttClientAuthProvider mqttClientAuthProvider = getMqttClientAuthProvider();
        MqttClientAuthProvider savedAuthProvider = mqttClientAuthProviderService.saveAuthProvider(mqttClientAuthProvider);
        assertThat(savedAuthProvider).isNotNull();

        savedAuthProvider.setType(MqttClientAuthProviderType.SSL);

        assertThatThrownBy(() -> mqttClientAuthProviderService.saveAuthProvider(savedAuthProvider))
                .isInstanceOf(DataValidationException.class)
                .hasMessage("MQTT client auth provider type can't be changed!");
    }

    @Test
    public void testSaveMqttClientAuthProviderWithNullType() {
        MqttClientAuthProvider mqttClientAuthProvider = getMqttClientAuthProvider(null, new BasicAuthProviderConfiguration());
        assertThatThrownBy(() -> mqttClientAuthProviderService.saveAuthProvider(mqttClientAuthProvider))
                .isInstanceOf(DataValidationException.class)
                .hasMessage("MQTT Client auth provider type should be specified!");
    }

    @Test
    public void testSaveMqttClientAuthProviderWithNullConfiguration() {
        MqttClientAuthProvider mqttClientAuthProvider = getMqttClientAuthProvider(MqttClientAuthProviderType.BASIC, null);
        assertThatThrownBy(() -> mqttClientAuthProviderService.saveAuthProvider(mqttClientAuthProvider))
                .isInstanceOf(DataValidationException.class)
                .hasMessage("MQTT Client auth provider configuration should be specified!");
    }

    @Test
    public void testSaveMqttClientAuthProviderWithExistingType() {
        MqttClientAuthProvider mqttClientAuthProvider = getMqttClientAuthProvider();

        MqttClientAuthProvider savedAuthProvider = mqttClientAuthProviderService.saveAuthProvider(mqttClientAuthProvider);
        assertThat(savedAuthProvider).isNotNull();

        MqttClientAuthProvider anotherBasicMqttClientAuthProvider = getMqttClientAuthProvider();

        assertThatThrownBy(() -> mqttClientAuthProviderService.saveAuthProvider(anotherBasicMqttClientAuthProvider))
                .isInstanceOf(DataValidationException.class)
                .hasMessage("MQTT client auth provider with such type already registered!");
    }

    @Test
    public void testFindMqttClientAuthProvider() {
        MqttClientAuthProvider mqttClientAuthProvider = getMqttClientAuthProvider();

        MqttClientAuthProvider savedAuthProvider = mqttClientAuthProviderService.saveAuthProvider(mqttClientAuthProvider);
        assertThat(savedAuthProvider).isNotNull();
        assertThat(savedAuthProvider.getType()).isEqualTo(MqttClientAuthProviderType.BASIC);
        assertThat(savedAuthProvider.getMqttClientAuthProviderConfiguration()).isNotNull();
        assertThat(savedAuthProvider.isEnabled()).isFalse();

        Optional<MqttClientAuthProvider> foundAuthProvider = mqttClientAuthProviderService.getAuthProviderById(savedAuthProvider.getId());
        assertThat(foundAuthProvider.isPresent()).isTrue();
        assertThat(foundAuthProvider.get()).isEqualTo(savedAuthProvider);
    }

    private MqttClientAuthProvider getMqttClientAuthProvider() {
        return getMqttClientAuthProvider(MqttClientAuthProviderType.BASIC, new BasicAuthProviderConfiguration());
    }

    private MqttClientAuthProvider getMqttClientAuthProvider(MqttClientAuthProviderType type, MqttClientAuthProviderConfiguration configuration) {
        var mqttClientAuthProvider = new MqttClientAuthProvider();
        mqttClientAuthProvider.setType(type);
        mqttClientAuthProvider.setMqttClientAuthProviderConfiguration(configuration);
        return mqttClientAuthProvider;
    }

}
