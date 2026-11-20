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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.security.Authority;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.data.security.UserCredentials;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketConnection;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttAuthProviderService;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsService;
import org.thingsboard.mqtt.broker.dao.store.StoreSeedState;
import org.thingsboard.mqtt.broker.dao.user.UserService;
import org.thingsboard.mqtt.broker.dao.ws.WebSocketConnectionService;
import org.thingsboard.mqtt.broker.dto.AdminDto;
import org.thingsboard.mqtt.broker.service.user.AdminService;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstallSeedStepTest {

    private static final String SYSADMIN_EMAIL = "sysadmin@thingsboard.org";

    @Mock
    private StoreSeedState seedState;
    @Mock
    private AdminSettingsService adminSettingsService;
    @Mock
    private MqttAuthProviderService mqttAuthProviderService;
    @Mock
    private UserService userService;
    @Mock
    private AdminService adminService;
    @Mock
    private MqttClientCredentialsService mqttClientCredentialsService;
    @Mock
    private WebSocketConnectionService webSocketConnectionService;

    private InstallSeedStep step;

    private final User sysadmin = sysadmin();
    private final MqttClientCredentials systemCredentials = systemCredentials();

    @BeforeEach
    void setUp() {
        step = new InstallSeedStep(seedState, adminSettingsService, mqttAuthProviderService, userService, adminService,
                mqttClientCredentialsService, webSocketConnectionService);
        when(mqttAuthProviderService.getAuthProviderByType(any())).thenReturn(Optional.empty());
    }

    @Test
    void anEmptyStoreGetsTheWholeInstallSeedAndTheMarkerLast() throws ThingsboardException {
        when(adminService.createAdmin(any(), anyBoolean())).thenReturn(sysadmin);
        when(mqttClientCredentialsService.saveSystemWebSocketCredentials()).thenReturn(systemCredentials);

        step.apply();

        InOrder order = inOrder(adminSettingsService, mqttAuthProviderService, adminService, mqttClientCredentialsService,
                webSocketConnectionService, seedState);
        ArgumentCaptor<AdminSettings> settings = ArgumentCaptor.forClass(AdminSettings.class);
        order.verify(adminSettingsService, times(5)).saveAdminSettings(settings.capture());
        ArgumentCaptor<MqttAuthProvider> providers = ArgumentCaptor.forClass(MqttAuthProvider.class);
        order.verify(mqttAuthProviderService, times(5)).saveAuthProvider(providers.capture());
        order.verify(adminService).createAdmin(AdminDto.fromEmail(SYSADMIN_EMAIL), false);
        order.verify(mqttClientCredentialsService).saveSystemWebSocketCredentials();
        order.verify(webSocketConnectionService).saveDefaultWebSocketConnection(sysadmin.getId(), systemCredentials.getId());
        order.verify(seedState).markSeeded();
        order.verifyNoMoreInteractions();

        assertThat(settings.getAllValues()).extracting(AdminSettings::getKey)
                .containsExactly("general", "mail", "connectivity", "websocket", "mqttAuthorization");
        assertThat(providers.getAllValues()).extracting(MqttAuthProvider::getType, MqttAuthProvider::isEnabled)
                .containsExactly(
                        tuple(MqttAuthProviderType.MQTT_BASIC, true),
                        tuple(MqttAuthProviderType.X_509, false),
                        tuple(MqttAuthProviderType.JWT, false),
                        tuple(MqttAuthProviderType.SCRAM, false),
                        tuple(MqttAuthProviderType.HTTP, false));
    }

    @Test
    void aSeededStoreIsLeftAlone() {
        when(seedState.isSeeded()).thenReturn(true);

        step.apply();

        verify(seedState, never()).markSeeded();
        verifyNoInteractions(adminSettingsService, mqttAuthProviderService, userService, adminService,
                mqttClientCredentialsService, webSocketConnectionService);
    }

    @Test
    void aSeedInterruptedAfterTheProvidersFinishesWithoutDuplicates() throws ThingsboardException {
        when(adminSettingsService.findAdminSettingsByKey(anyString())).thenReturn(new AdminSettings());
        when(mqttAuthProviderService.getAuthProviderByType(any())).thenReturn(Optional.of(new MqttAuthProvider()));
        when(adminService.createAdmin(any(), anyBoolean())).thenReturn(sysadmin);
        when(mqttClientCredentialsService.saveSystemWebSocketCredentials()).thenReturn(systemCredentials);

        step.apply();

        verify(adminSettingsService, times(5)).findAdminSettingsByKey(anyString());
        verify(adminSettingsService, never()).saveAdminSettings(any());
        verify(mqttAuthProviderService, times(5)).getAuthProviderByType(any());
        verify(mqttAuthProviderService, never()).saveAuthProvider(any());
        InOrder order = inOrder(adminService, mqttClientCredentialsService, webSocketConnectionService, seedState);
        order.verify(adminService).createAdmin(AdminDto.fromEmail(SYSADMIN_EMAIL), false);
        order.verify(mqttClientCredentialsService).saveSystemWebSocketCredentials();
        order.verify(webSocketConnectionService).saveDefaultWebSocketConnection(sysadmin.getId(), systemCredentials.getId());
        order.verify(seedState).markSeeded();
    }

    @Test
    void aSeedInterruptedBeforeTheMarkerOnlyWritesTheMarker() throws ThingsboardException {
        when(adminSettingsService.findAdminSettingsByKey(anyString())).thenReturn(new AdminSettings());
        when(mqttAuthProviderService.getAuthProviderByType(any())).thenReturn(Optional.of(new MqttAuthProvider()));
        when(userService.findUserByEmail(SYSADMIN_EMAIL)).thenReturn(sysadmin);
        when(userService.findUserCredentialsByUserId(sysadmin.getId())).thenReturn(activeCredentials(sysadmin.getId()));
        when(mqttClientCredentialsService.findSystemWebSocketCredentials()).thenReturn(systemCredentials);
        when(webSocketConnectionService.findWebSocketConnectionByName(sysadmin.getId(), BrokerConstants.WEB_SOCKET_DEFAULT_CONNECTION_NAME))
                .thenReturn(new WebSocketConnection());

        step.apply();

        verify(adminSettingsService, never()).saveAdminSettings(any());
        verify(mqttAuthProviderService, never()).saveAuthProvider(any());
        verify(adminService, never()).createAdmin(any(), anyBoolean());
        verify(adminService, never()).activateAdminCredentials(any());
        verify(userService, never()).createUserCredentials(any());
        verify(mqttClientCredentialsService, never()).saveSystemWebSocketCredentials();
        verify(webSocketConnectionService, never()).saveDefaultWebSocketConnection(any(), any());
        verify(seedState).markSeeded();
    }

    @Test
    void aSysadminWithoutCredentialsIsRepaired() throws ThingsboardException {
        when(userService.findUserByEmail(SYSADMIN_EMAIL)).thenReturn(sysadmin);
        when(userService.findUserCredentialsByUserId(sysadmin.getId())).thenReturn(null);
        UserCredentials created = disabledCredentials(sysadmin.getId());
        when(userService.createUserCredentials(sysadmin.getId())).thenReturn(created);
        when(mqttClientCredentialsService.saveSystemWebSocketCredentials()).thenReturn(systemCredentials);

        step.apply();

        InOrder order = inOrder(userService, adminService, webSocketConnectionService, seedState);
        order.verify(userService).createUserCredentials(sysadmin.getId());
        order.verify(adminService).activateAdminCredentials(created);
        order.verify(webSocketConnectionService).saveDefaultWebSocketConnection(sysadmin.getId(), systemCredentials.getId());
        order.verify(seedState).markSeeded();
        verify(adminService, never()).createAdmin(any(), anyBoolean());
    }

    @Test
    void aSysadminWithDisabledCredentialsAndNoPasswordIsActivated() throws ThingsboardException {
        when(userService.findUserByEmail(SYSADMIN_EMAIL)).thenReturn(sysadmin);
        UserCredentials disabled = disabledCredentials(sysadmin.getId());
        when(userService.findUserCredentialsByUserId(sysadmin.getId())).thenReturn(disabled);
        when(mqttClientCredentialsService.saveSystemWebSocketCredentials()).thenReturn(systemCredentials);

        step.apply();

        verify(adminService).activateAdminCredentials(disabled);
        verify(userService, never()).createUserCredentials(any());
        verify(adminService, never()).createAdmin(any(), anyBoolean());
        verify(seedState).markSeeded();
    }

    @Test
    void aSysadminWithActiveCredentialsIsLeftAsItIs() throws ThingsboardException {
        when(userService.findUserByEmail(SYSADMIN_EMAIL)).thenReturn(sysadmin);
        when(userService.findUserCredentialsByUserId(sysadmin.getId())).thenReturn(activeCredentials(sysadmin.getId()));
        when(mqttClientCredentialsService.saveSystemWebSocketCredentials()).thenReturn(systemCredentials);

        step.apply();

        verify(adminService, never()).activateAdminCredentials(any());
        verify(adminService, never()).createAdmin(any(), anyBoolean());
        verify(userService, never()).createUserCredentials(any());
        verify(webSocketConnectionService).saveDefaultWebSocketConnection(sysadmin.getId(), systemCredentials.getId());
    }

    @Test
    void anExistingSystemCredentialGetsTheDefaultConnection() throws ThingsboardException {
        when(adminService.createAdmin(any(), anyBoolean())).thenReturn(sysadmin);
        when(mqttClientCredentialsService.findSystemWebSocketCredentials()).thenReturn(systemCredentials);

        step.apply();

        verify(mqttClientCredentialsService, never()).saveSystemWebSocketCredentials();
        verify(webSocketConnectionService).saveDefaultWebSocketConnection(sysadmin.getId(), systemCredentials.getId());
    }

    @Test
    void aFailedSysadminCreationNamesWhatFailedAndLeavesTheMarkerUnwritten() throws ThingsboardException {
        when(adminService.createAdmin(any(), anyBoolean()))
                .thenThrow(new ThingsboardException("boom", ThingsboardErrorCode.GENERAL));

        assertThatThrownBy(() -> step.apply())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(SYSADMIN_EMAIL)
                .hasCauseInstanceOf(ThingsboardException.class);
        verify(seedState, never()).markSeeded();
    }

    @Test
    void aFailedDefaultConnectionNamesWhatFailedAndLeavesTheMarkerUnwritten() throws ThingsboardException {
        when(adminService.createAdmin(any(), anyBoolean())).thenReturn(sysadmin);
        when(mqttClientCredentialsService.saveSystemWebSocketCredentials()).thenReturn(systemCredentials);
        when(webSocketConnectionService.saveDefaultWebSocketConnection(eq(sysadmin.getId()), any()))
                .thenThrow(new ThingsboardException("boom", ThingsboardErrorCode.GENERAL));

        assertThatThrownBy(() -> step.apply())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(BrokerConstants.WEB_SOCKET_DEFAULT_CONNECTION_NAME)
                .hasCauseInstanceOf(ThingsboardException.class);
        verify(seedState, never()).markSeeded();
    }

    private static User sysadmin() {
        User user = new User(UUID.randomUUID());
        user.setEmail(SYSADMIN_EMAIL);
        user.setAuthority(Authority.SYS_ADMIN);
        return user;
    }

    private static MqttClientCredentials systemCredentials() {
        MqttClientCredentials credentials = new MqttClientCredentials();
        credentials.setId(UUID.randomUUID());
        return credentials;
    }

    private static UserCredentials disabledCredentials(UUID userId) {
        UserCredentials credentials = new UserCredentials();
        credentials.setId(UUID.randomUUID());
        credentials.setUserId(userId);
        credentials.setEnabled(false);
        credentials.setActivateToken("token");
        return credentials;
    }

    private static UserCredentials activeCredentials(UUID userId) {
        UserCredentials credentials = new UserCredentials();
        credentials.setId(UUID.randomUUID());
        credentials.setUserId(userId);
        credentials.setEnabled(true);
        credentials.setPassword("$2a$10$hash");
        return credentials;
    }

}
