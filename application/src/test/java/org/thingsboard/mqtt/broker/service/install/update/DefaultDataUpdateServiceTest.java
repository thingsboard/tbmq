/**
 * Copyright © 2016-2025 The Thingsboard Authors
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.mqtt.broker.service.install.update;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.common.data.SysAdminSettingType;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.security.basic.BasicMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.jwt.JwtMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.scram.ScramMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.ssl.SslMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttAuthProviderService;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsService;
import org.thingsboard.mqtt.broker.service.install.data.MqttAuthSettings;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willCallRealMethod;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = DefaultDataUpdateService.class)
public class DefaultDataUpdateServiceTest {

    @MockBean
    AdminSettingsService adminSettingsService;
    @MockBean
    MqttAuthProviderService mqttAuthProviderService;

    @SpyBean
    DefaultDataUpdateService service;

    @Before
    public void resetMqttAuthFlags() {
        ReflectionTestUtils.setField(service, "basicAuthEnabled", false);
        ReflectionTestUtils.setField(service, "x509AuthEnabled", false);
        ReflectionTestUtils.setField(service, "skipValidityCheckForClientCert", false);
        ReflectionTestUtils.setField(service, "authStrategy", "BOTH");
    }

    @Test
    public void whenMqttSettingNotExist_andYmlIsSingle_thenCreateSettingWithListenerOnlyTrue() throws Exception {
        // given
        ReflectionTestUtils.setField(service, "authStrategy", "SINGLE");
        given(adminSettingsService.findAdminSettingsByKey(SysAdminSettingType.MQTT_AUTHORIZATION.getKey()))
                .willReturn(null);
        willCallRealMethod().given(service).isUseListenerBasedProviderOnly();

        // when
        service.updateData();

        // then
        ArgumentCaptor<AdminSettings> adminSettingsArgumentCaptor = ArgumentCaptor.forClass(AdminSettings.class);
        then(adminSettingsService).should().saveAdminSettings(adminSettingsArgumentCaptor.capture());

        AdminSettings saved = adminSettingsArgumentCaptor.getValue();
        assertThat(saved.getKey()).isEqualTo(SysAdminSettingType.MQTT_AUTHORIZATION.getKey());
        assertThat(saved.getJsonValue()).isNotNull();
        MqttAuthSettings settings = MqttAuthSettings.fromJsonValue(saved.getJsonValue());

        assertThat(settings).isNotNull();
        assertThat(settings.isUseListenerBasedProviderOnly()).isTrue();
        assertThat(settings.getPriorities()).isEqualTo(MqttAuthProviderType.getDefaultPriorityList());
    }

    @Test
    public void whenMqttSettingNotExist_andYmlIsBoth_thenCreateSettingWithListenerOnlyFalse() throws Exception {
        // given
        given(adminSettingsService.findAdminSettingsByKey(SysAdminSettingType.MQTT_AUTHORIZATION.getKey()))
                .willReturn(null);
        willCallRealMethod().given(service).isUseListenerBasedProviderOnly();

        // when
        service.updateData();

        // then
        ArgumentCaptor<AdminSettings> captor = ArgumentCaptor.forClass(AdminSettings.class);
        then(adminSettingsService).should().saveAdminSettings(captor.capture());

        AdminSettings saved = captor.getValue();
        assertThat(saved.getKey()).isEqualTo(SysAdminSettingType.MQTT_AUTHORIZATION.getKey());
        assertThat(saved.getJsonValue()).isNotNull();
        MqttAuthSettings settings = MqttAuthSettings.fromJsonValue(saved.getJsonValue());

        assertThat(settings).isNotNull();
        assertThat(settings.isUseListenerBasedProviderOnly()).isFalse();
        assertThat(settings.getPriorities()).isEqualTo(MqttAuthProviderType.getDefaultPriorityList());
    }

    @Test
    public void whenMqttSettingNotExist_andYmlIsInvalid_thenCreateSettingWithListenerOnlyFalse() throws Exception {
        // given
        ReflectionTestUtils.setField(service, "authStrategy", "foobar");
        given(adminSettingsService.findAdminSettingsByKey(SysAdminSettingType.MQTT_AUTHORIZATION.getKey()))
                .willReturn(null);
        willCallRealMethod().given(service).isUseListenerBasedProviderOnly();

        // when
        service.updateData();

        // then
        ArgumentCaptor<AdminSettings> captor = ArgumentCaptor.forClass(AdminSettings.class);
        then(adminSettingsService).should().saveAdminSettings(captor.capture());

        AdminSettings saved = captor.getValue();
        assertThat(saved.getKey()).isEqualTo(SysAdminSettingType.MQTT_AUTHORIZATION.getKey());
        assertThat(saved.getJsonValue()).isNotNull();
        MqttAuthSettings settings = MqttAuthSettings.fromJsonValue(saved.getJsonValue());

        assertThat(settings).isNotNull();
        assertThat(settings.isUseListenerBasedProviderOnly()).isFalse();
        assertThat(settings.getPriorities()).isEqualTo(MqttAuthProviderType.getDefaultPriorityList());
    }

    @Test
    public void whenMqttSettingExists_thenSkipCreation() throws Exception {
        // given
        given(adminSettingsService.findAdminSettingsByKey(SysAdminSettingType.MQTT_AUTHORIZATION.getKey()))
                .willReturn(mock(AdminSettings.class));

        // when
        service.updateData();

        // then
        then(adminSettingsService).should(never()).saveAdminSettings(any());
    }

    @Test
    public void whenMqttSettingNotExist_andEnvMockedToTrue_thenCreateSettingWithListenerOnlyTrue() throws Exception {
        // given
        given(adminSettingsService.findAdminSettingsByKey(SysAdminSettingType.MQTT_AUTHORIZATION.getKey()))
                .willReturn(null);

        willReturn(true).given(service).isUseListenerBasedProviderOnly();

        // when
        service.updateData();

        // then
        ArgumentCaptor<AdminSettings> captor = ArgumentCaptor.forClass(AdminSettings.class);
        then(adminSettingsService).should().saveAdminSettings(captor.capture());

        MqttAuthSettings settings = MqttAuthSettings.fromJsonValue(captor.getValue().getJsonValue());

        assertThat(settings).isNotNull();
        assertThat(settings.isUseListenerBasedProviderOnly()).isTrue();
        assertThat(settings.getPriorities()).isEqualTo(MqttAuthProviderType.getDefaultPriorityList());
    }

    @Test
    public void whenMqttSettingNotExist_andEnvMockedToFalse_thenCreateSettingWithListenerOnlyFalse() throws Exception {
        // given
        given(adminSettingsService.findAdminSettingsByKey(SysAdminSettingType.MQTT_AUTHORIZATION.getKey()))
                .willReturn(null);

        willReturn(false).given(service).isUseListenerBasedProviderOnly();

        // when
        service.updateData();

        // then
        ArgumentCaptor<AdminSettings> captor = ArgumentCaptor.forClass(AdminSettings.class);
        then(adminSettingsService).should().saveAdminSettings(captor.capture());

        MqttAuthSettings settings = MqttAuthSettings.fromJsonValue(captor.getValue().getJsonValue());

        assertThat(settings).isNotNull();
        assertThat(settings.isUseListenerBasedProviderOnly()).isFalse();
        assertThat(settings.getPriorities()).isEqualTo(MqttAuthProviderType.getDefaultPriorityList());
    }

    @Test
    public void shouldCreateAllAuthProviders_whenNoneExist() throws Exception {
        // given
        for (MqttAuthProviderType type : MqttAuthProviderType.values()) {
            given(mqttAuthProviderService.getAuthProviderByType(type)).willReturn(Optional.empty());
        }

        // Enable all providers via field injection
        ReflectionTestUtils.setField(service, "basicAuthEnabled", true);
        ReflectionTestUtils.setField(service, "x509AuthEnabled", true);
        ReflectionTestUtils.setField(service, "skipValidityCheckForClientCert", true);

        willCallRealMethod().given(service).isBasicAuthEnabled();
        willCallRealMethod().given(service).isX509AuthEnabled();
        willCallRealMethod().given(service).isX509SkipValidityCheckForClientCertIsSetToTrue();

        // when
        service.updateData();

        // then
        ArgumentCaptor<MqttAuthProvider> captor = ArgumentCaptor.forClass(MqttAuthProvider.class);
        then(mqttAuthProviderService).should(times(4)).saveAuthProvider(captor.capture());

        List<MqttAuthProvider> savedProviders = captor.getAllValues();
        assertThat(savedProviders).hasSize(4);

        MqttAuthProvider basic = savedProviders.get(0);
        assertThat(basic.getType()).isEqualTo(MqttAuthProviderType.MQTT_BASIC);
        assertThat(basic.isEnabled()).isTrue();
        assertThat(basic.getConfiguration()).isEqualTo(new BasicMqttAuthProviderConfiguration());

        MqttAuthProvider x509 = savedProviders.get(1);
        assertThat(x509.getType()).isEqualTo(MqttAuthProviderType.X_509);
        assertThat(x509.isEnabled()).isTrue();
        assertThat(x509.getConfiguration()).isInstanceOf(SslMqttAuthProviderConfiguration.class);
        SslMqttAuthProviderConfiguration configuration = (SslMqttAuthProviderConfiguration) x509.getConfiguration();
        assertThat(configuration.isSkipValidityCheckForClientCert()).isTrue();

        MqttAuthProvider jwt = savedProviders.get(2);
        assertThat(jwt.getType()).isEqualTo(MqttAuthProviderType.JWT);
        assertThat(jwt.isEnabled()).isFalse();
        assertThat(jwt.getConfiguration()).isEqualTo(JwtMqttAuthProviderConfiguration.defaultConfiguration());

        MqttAuthProvider scram = savedProviders.get(3);
        assertThat(scram.getType()).isEqualTo(MqttAuthProviderType.SCRAM);
        assertThat(scram.isEnabled()).isTrue();
        assertThat(scram.getConfiguration()).isEqualTo(new ScramMqttAuthProviderConfiguration());
    }

    @Test
    public void shouldCreateAllAuthProvidersDisabledExceptScram_whenNoneExist() throws Exception {
        // given
        for (MqttAuthProviderType type : MqttAuthProviderType.values()) {
            given(mqttAuthProviderService.getAuthProviderByType(type)).willReturn(Optional.empty());
        }

        willCallRealMethod().given(service).isBasicAuthEnabled();
        willCallRealMethod().given(service).isX509AuthEnabled();

        // when
        service.updateData();

        // then
        ArgumentCaptor<MqttAuthProvider> captor = ArgumentCaptor.forClass(MqttAuthProvider.class);
        then(mqttAuthProviderService).should(times(4)).saveAuthProvider(captor.capture());

        List<MqttAuthProvider> savedProviders = captor.getAllValues();
        assertThat(savedProviders).hasSize(4);

        MqttAuthProvider basic = savedProviders.get(0);
        assertThat(basic.getType()).isEqualTo(MqttAuthProviderType.MQTT_BASIC);
        assertThat(basic.isEnabled()).isFalse();
        assertThat(basic.getConfiguration()).isEqualTo(new BasicMqttAuthProviderConfiguration());

        MqttAuthProvider x509 = savedProviders.get(1);
        assertThat(x509.getType()).isEqualTo(MqttAuthProviderType.X_509);
        assertThat(x509.isEnabled()).isFalse();
        assertThat(x509.getConfiguration()).isEqualTo(new SslMqttAuthProviderConfiguration());

        MqttAuthProvider jwt = savedProviders.get(2);
        assertThat(jwt.getType()).isEqualTo(MqttAuthProviderType.JWT);
        assertThat(jwt.isEnabled()).isFalse();
        assertThat(jwt.getConfiguration()).isEqualTo(JwtMqttAuthProviderConfiguration.defaultConfiguration());

        MqttAuthProvider scram = savedProviders.get(3);
        assertThat(scram.getType()).isEqualTo(MqttAuthProviderType.SCRAM);
        assertThat(scram.isEnabled()).isTrue();
        assertThat(scram.getConfiguration()).isEqualTo(new ScramMqttAuthProviderConfiguration());
    }

    @Test
    public void shouldNotCreateAnyAuthProvider_whenAllAlreadyExist() throws Exception {
        // given
        for (MqttAuthProviderType type : MqttAuthProviderType.values()) {
            MqttAuthProvider existingProvider = mock(MqttAuthProvider.class);
            given(mqttAuthProviderService.getAuthProviderByType(type)).willReturn(Optional.of(existingProvider));
        }

        // when
        service.updateData();

        // then
        then(mqttAuthProviderService).should(never()).saveAuthProvider(any());
    }

}
