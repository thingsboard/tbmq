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
package org.thingsboard.mqtt.broker.lite;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.security.Authority;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.security.UserCredentials;
import org.thingsboard.mqtt.broker.common.data.security.http.HttpMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttAuthProviderDao;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsDao;
import org.thingsboard.mqtt.broker.dao.user.UserCredentialsDao;
import org.thingsboard.mqtt.broker.dao.user.UserDao;
import org.thingsboard.mqtt.broker.testsupport.lite.SystemDataTestStep;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * What the step writes into an empty backend, which the Spring test cannot show while the DAOs are on PostgreSQL and
 * the rows are already there.
 */
class SystemDataTestStepWritesTest {

    private final UserDao userDao = mock(UserDao.class);
    private final UserCredentialsDao userCredentialsDao = mock(UserCredentialsDao.class);
    private final AdminSettingsDao adminSettingsDao = mock(AdminSettingsDao.class);
    private final MqttAuthProviderDao mqttAuthProviderDao = mock(MqttAuthProviderDao.class);

    private SystemDataTestStep step(boolean enabled) {
        return new SystemDataTestStep(userDao, userCredentialsDao, adminSettingsDao, mqttAuthProviderDao, enabled);
    }

    @Test
    void givenAnEmptyBackend_whenApply_thenEveryRowOfTheSqlFileIsSaved() {
        step(true).apply();

        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(userDao).save(user.capture());
        assertThat(user.getValue().getId()).isEqualTo(UUID.fromString("5a797660-4612-11e7-a919-92ebcb67fe33"));
        assertThat(user.getValue().getEmail()).isEqualTo("sysadmin@thingsboard.org");
        assertThat(user.getValue().getAuthority()).isEqualTo(Authority.SYS_ADMIN);
        assertThat(user.getValue().getCreatedTime()).isEqualTo(1592576748000L);

        ArgumentCaptor<UserCredentials> credentials = ArgumentCaptor.forClass(UserCredentials.class);
        verify(userCredentialsDao).save(credentials.capture());
        assertThat(credentials.getValue().getId()).isEqualTo(UUID.fromString("61441950-4612-11e7-a919-92ebcb67fe33"));
        assertThat(credentials.getValue().getUserId()).isEqualTo(user.getValue().getId());
        assertThat(credentials.getValue().isEnabled()).isTrue();
        assertThat(credentials.getValue().getPassword()).isEqualTo("$2a$10$5JTB8/hxWc9WAy62nCGSxeefl3KWmipA9nFpVdDa0/xfIseeBB4Bu");

        ArgumentCaptor<AdminSettings> settings = ArgumentCaptor.forClass(AdminSettings.class);
        verify(adminSettingsDao, times(3)).save(settings.capture());
        assertThat(settings.getAllValues()).extracting(AdminSettings::getKey).containsExactly("general", "mail", "mqttAuthorization");
        assertThat(settings.getAllValues().get(2).getJsonValue().get("priorities")).hasSize(3);

        ArgumentCaptor<MqttAuthProvider> providers = ArgumentCaptor.forClass(MqttAuthProvider.class);
        verify(mqttAuthProviderDao, times(5)).save(providers.capture());
        assertThat(providers.getAllValues()).noneMatch(MqttAuthProvider::isEnabled)
                .extracting(MqttAuthProvider::getType).containsExactlyInAnyOrder(MqttAuthProviderType.values());
        assertThat(providers.getAllValues()).allSatisfy(provider ->
                assertThat(provider.getConfiguration().getType()).isEqualTo(provider.getType()));
        HttpMqttAuthProviderConfiguration http = (HttpMqttAuthProviderConfiguration) providers.getAllValues().stream()
                .filter(provider -> provider.getType() == MqttAuthProviderType.HTTP).findFirst().orElseThrow().getConfiguration();
        assertThat(http.getRequestBody()).contains("\"clientId\": \"${clientId}\"").contains("\n");
    }

    @Test
    void givenEveryRowPresent_whenApply_thenNothingIsSaved() {
        when(userDao.findByEmail("sysadmin@thingsboard.org")).thenReturn(new User());
        when(userCredentialsDao.findByUserId(UUID.fromString("5a797660-4612-11e7-a919-92ebcb67fe33"))).thenReturn(new UserCredentials());
        for (String key : List.of("general", "mail", "mqttAuthorization")) {
            when(adminSettingsDao.findByKey(key)).thenReturn(new AdminSettings());
        }
        for (MqttAuthProviderType type : MqttAuthProviderType.values()) {
            when(mqttAuthProviderDao.findByType(type)).thenReturn(new MqttAuthProvider());
        }

        step(true).apply();

        verify(userDao, never()).save(any());
        verify(userCredentialsDao, never()).save(any());
        verify(adminSettingsDao, never()).save(any());
        verify(mqttAuthProviderDao, never()).save(any());
    }

    @Test
    void givenTheStepIsDisabled_whenApply_thenTheDaosAreNotTouched() {
        step(false).apply();

        verifyNoInteractions(userDao, userCredentialsDao, adminSettingsDao, mqttAuthProviderDao);
    }

}
