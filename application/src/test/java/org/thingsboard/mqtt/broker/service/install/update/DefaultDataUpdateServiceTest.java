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
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.data.security.basic.BasicMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.jwt.JwtMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.scram.ScramMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.ssl.SslMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = DefaultDataUpdateService.class)
public class DefaultDataUpdateServiceTest {

    @MockBean
    AdminSettingsService adminSettingsService;
    @MockBean
    MqttAuthProviderService mqttAuthProviderService;
    @MockBean
    MqttClientCredentialsService mqttClientCredentialsService;

    @SpyBean
    DefaultDataUpdateService service;

    @Before
    public void resetMqttAuthFlags() {
        ReflectionTestUtils.setField(service, "basicAuthEnabled", false);
        ReflectionTestUtils.setField(service, "x509AuthEnabled", false);
        ReflectionTestUtils.setField(service, "skipValidityCheckForClientCert", false);

        when(mqttClientCredentialsService.getFullCredentials(any())).thenReturn(PageData.emptyPageData());
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
    public void whenMqttSettingNotExist_thenCreateSetting() throws Exception {
        // given
        given(adminSettingsService.findAdminSettingsByKey(SysAdminSettingType.MQTT_AUTHORIZATION.getKey()))
                .willReturn(null);

        // when
        service.updateData();

        // then
        ArgumentCaptor<AdminSettings> captor = ArgumentCaptor.forClass(AdminSettings.class);
        then(adminSettingsService).should().saveAdminSettings(captor.capture());

        MqttAuthSettings settings = MqttAuthSettings.fromJsonValue(captor.getValue().getJsonValue());

        assertThat(settings).isNotNull();
        assertThat(settings.getPriorities()).isEqualTo(MqttAuthProviderType.defaultPriorityList);
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
        assertThat(x509.getConfiguration()).isEqualTo(SslMqttAuthProviderConfiguration.defaultConfiguration());

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

    @Test
    public void deduplicate_noDuplicates_doesNotSaveAnything() {
        // given
        MqttClientCredentials c1 = new MqttClientCredentials();
        c1.setName("alpha");
        MqttClientCredentials c2 = new MqttClientCredentials();
        c2.setName("beta");
        @SuppressWarnings("unchecked")
        PageData<MqttClientCredentials> page = (PageData<MqttClientCredentials>) mock(PageData.class);
        when(page.getData()).thenReturn(List.of(c1, c2));
        when(page.hasNext()).thenReturn(false);

        when(mqttClientCredentialsService.getFullCredentials(any())).thenReturn(page);
        when(mqttClientCredentialsService.findCredentialsByName(any())).thenReturn(null);

        // when
        service.deduplicateMqttClientCredentialNames();

        // then
        then(mqttClientCredentialsService).should(never()).saveCredentials(any());
    }

    @Test
    public void deduplicate_singlePageDuplicates_renamesWithThreeLetterSuffix() {
        // given
        String dup = "client-A";
        MqttClientCredentials c1 = new MqttClientCredentials();
        c1.setName(dup);
        MqttClientCredentials c2 = new MqttClientCredentials();
        c2.setName(dup); // duplicate

        @SuppressWarnings("unchecked")
        PageData<MqttClientCredentials> page = (PageData<MqttClientCredentials>) mock(PageData.class);
        when(page.getData()).thenReturn(List.of(c1, c2));
        when(page.hasNext()).thenReturn(false);

        when(mqttClientCredentialsService.getFullCredentials(any())).thenReturn(page);
        // ensure the first generated candidate is available
        when(mqttClientCredentialsService.findCredentialsByName(any())).thenReturn(null);

        ArgumentCaptor<MqttClientCredentials> savedCaptor = ArgumentCaptor.forClass(MqttClientCredentials.class);

        // when
        service.deduplicateMqttClientCredentialNames();

        // then
        then(mqttClientCredentialsService).should(times(1)).saveCredentials(savedCaptor.capture());
        MqttClientCredentials updated = savedCaptor.getValue();

        assertThat(updated.getName()).isNotEqualTo(dup);
        assertThat(updated.getName()).matches(dup + " [A-Za-z]{3}");
        assertThat(updated.getName().length()).isEqualTo(dup.length() + 4);
    }

    @Test
    public void deduplicate_acrossPages_usesSeenSetBetweenPages() {
        // given
        String dup = "reused-name";

        // page 1 contains the original occurrence and a unique one
        MqttClientCredentials p1c1 = new MqttClientCredentials();
        p1c1.setName(dup);
        MqttClientCredentials p1c2 = new MqttClientCredentials();
        p1c2.setName("unique-1");

        // page 2 contains a duplicate of the name from page 1; it must be renamed
        MqttClientCredentials p2c1 = new MqttClientCredentials();
        p2c1.setName(dup);
        MqttClientCredentials p2c2 = new MqttClientCredentials();
        p2c2.setName("unique-2");

        @SuppressWarnings("unchecked")
        PageData<MqttClientCredentials> page1 = (PageData<MqttClientCredentials>) mock(PageData.class);
        when(page1.getData()).thenReturn(List.of(p1c1, p1c2));
        when(page1.hasNext()).thenReturn(true);

        @SuppressWarnings("unchecked")
        PageData<MqttClientCredentials> page2 = (PageData<MqttClientCredentials>) mock(PageData.class);
        when(page2.getData()).thenReturn(List.of(p2c1, p2c2));
        when(page2.hasNext()).thenReturn(false);

        when(mqttClientCredentialsService.getFullCredentials(any())).thenReturn(page1, page2);
        when(mqttClientCredentialsService.findCredentialsByName(any())).thenReturn(null);

        ArgumentCaptor<MqttClientCredentials> savedCaptor = ArgumentCaptor.forClass(MqttClientCredentials.class);

        // when
        service.deduplicateMqttClientCredentialNames();

        // then
        then(mqttClientCredentialsService).should(times(1)).saveCredentials(savedCaptor.capture());
        MqttClientCredentials updated = savedCaptor.getValue();

        // only the page-2 duplicate should be renamed
        assertThat(updated).isSameAs(p2c1);
        assertThat(updated.getName()).matches(dup + " [A-Za-z]{3}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void deduplicate_overlengthName_throws() {
        // given
        // base length 252 will become 252 + 1(space) + 3 = 256 (>255) => must throw
        String tooLongBase = "x".repeat(252);
        MqttClientCredentials c1 = new MqttClientCredentials();
        c1.setName(tooLongBase);
        MqttClientCredentials c2 = new MqttClientCredentials();
        c2.setName(tooLongBase); // duplicate, will attempt to append suffix and exceed limit

        @SuppressWarnings("unchecked")
        PageData<MqttClientCredentials> page = (PageData<MqttClientCredentials>) mock(PageData.class);
        when(page.getData()).thenReturn(List.of(c1, c2));
        when(page.hasNext()).thenReturn(false);

        when(mqttClientCredentialsService.getFullCredentials(any())).thenReturn(page);
        when(mqttClientCredentialsService.findCredentialsByName(any())).thenReturn(null);

        // when
        service.deduplicateMqttClientCredentialNames();

        // then -> exception expected
    }

}
