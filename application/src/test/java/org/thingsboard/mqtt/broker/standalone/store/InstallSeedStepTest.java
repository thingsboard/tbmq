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
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.Authority;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.data.security.UserCredentials;
import org.thingsboard.mqtt.broker.common.data.security.model.SecuritySettings;
import org.thingsboard.mqtt.broker.common.data.security.model.UserPasswordPolicy;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketConnection;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketSubscription;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttAuthProviderService;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsService;
import org.thingsboard.mqtt.broker.dao.store.StoreFullException;
import org.thingsboard.mqtt.broker.dao.store.StoreSeedState;
import org.thingsboard.mqtt.broker.dao.user.UserService;
import org.thingsboard.mqtt.broker.dao.ws.WebSocketConnectionService;
import org.thingsboard.mqtt.broker.dao.ws.WebSocketSubscriptionService;
import org.thingsboard.mqtt.broker.dto.AdminDto;
import org.thingsboard.mqtt.broker.exception.DataValidationException;
import org.thingsboard.mqtt.broker.service.install.DefaultSystemDataLoaderService;
import org.thingsboard.mqtt.broker.service.security.system.SystemSecurityService;
import org.thingsboard.mqtt.broker.service.user.AdminService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstallSeedStepTest {

    private static final String SYSADMIN_EMAIL = DefaultSystemDataLoaderService.SYSADMIN_EMAIL;

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
    @Mock
    private WebSocketSubscriptionService webSocketSubscriptionService;

    @Mock
    private SystemSecurityService systemSecurityService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private InstallSeedStep step;

    private final User sysadmin = sysadmin();
    private final MqttClientCredentials systemCredentials = systemCredentials();

    @BeforeEach
    void setUp() {
        step = stepWithAdminPassword("");
        when(mqttAuthProviderService.getAuthProviderByType(any())).thenReturn(Optional.empty());
        SecuritySettings securitySettings = new SecuritySettings();
        securitySettings.setPasswordPolicy(new UserPasswordPolicy());
        securitySettings.getPasswordPolicy().setMinimumLength(6);
        when(systemSecurityService.getSecuritySettings()).thenReturn(securitySettings);
    }

    private InstallSeedStep stepWithAdminPassword(String adminPassword) {
        return new InstallSeedStep(seedState, adminSettingsService, mqttAuthProviderService, userService, adminService,
                mqttClientCredentialsService, webSocketConnectionService, webSocketSubscriptionService,
                systemSecurityService, passwordEncoder, adminPassword);
    }

    @Test
    void blankVariableCreatesTheDefaultSysadminAndMarksIt() throws ThingsboardException {
        when(adminService.createAdmin(any(), anyBoolean())).thenReturn(sysadmin);
        when(userService.findUserCredentialsByUserId(sysadmin.getId())).thenReturn(credentialsWithPassword("sysadmin"));
        when(mqttClientCredentialsService.saveSystemWebSocketCredentials()).thenReturn(systemCredentials);

        stepWithAdminPassword("   ").apply();

        verify(adminService).createAdmin(AdminDto.fromEmail(SYSADMIN_EMAIL), false);
        verify(userService).markPasswordChangeRequired(sysadmin.getId());
        verify(seedState).markSeeded();
    }

    @Test
    void aSetVariableCreatesTheSysadminWithItAndNoMark() throws ThingsboardException {
        when(adminService.createAdmin(any(), anyBoolean())).thenReturn(sysadmin);
        when(userService.findUserCredentialsByUserId(sysadmin.getId())).thenReturn(credentialsWithPassword("Str0ng-pass"));
        when(mqttClientCredentialsService.saveSystemWebSocketCredentials()).thenReturn(systemCredentials);

        stepWithAdminPassword("Str0ng-pass").apply();

        verify(systemSecurityService).validatePasswordByPolicy(eq("Str0ng-pass"), any(UserPasswordPolicy.class));
        verify(adminService).createAdmin(AdminDto.builder().email(SYSADMIN_EMAIL).password("Str0ng-pass").build(), false);
        verify(userService, never()).markPasswordChangeRequired(any());
        verify(seedState).markSeeded();
    }

    @Test
    void theDefaultOrAShortPasswordFailsFirstStartNamingTheVariable() {
        assertThatThrownBy(() -> stepWithAdminPassword("sysadmin").apply())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TBMQ_ADMIN_PASSWORD must not be the default password");

        doThrow(new DataValidationException("Password must be 6 or more characters in length."))
                .when(systemSecurityService).validatePasswordByPolicy(eq("abc"), any(UserPasswordPolicy.class));
        assertThatThrownBy(() -> stepWithAdminPassword("abc").apply())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TBMQ_ADMIN_PASSWORD does not meet the password policy: Password must be 6 or more characters in length.")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("abc"));

        // refused before the first write
        verifyNoInteractions(adminSettingsService, mqttAuthProviderService, userService, adminService,
                mqttClientCredentialsService, webSocketConnectionService);
        verify(seedState, never()).markSeeded();
    }

    @Test
    void aRerunMarksAnUnmarkedSysadminStillOnTheDefaultPassword() {
        when(userService.findUserByEmail(SYSADMIN_EMAIL)).thenReturn(sysadmin);
        when(userService.findUserCredentialsByUserId(sysadmin.getId())).thenReturn(credentialsWithPassword("sysadmin"));
        when(mqttClientCredentialsService.saveSystemWebSocketCredentials()).thenReturn(systemCredentials);

        step.apply();

        verify(userService).markPasswordChangeRequired(sysadmin.getId());
        verify(seedState).markSeeded();
    }

    @Test
    void aRerunLeavesAMarkedOrChangedSysadminAlone() {
        when(userService.findUserByEmail(SYSADMIN_EMAIL)).thenReturn(sysadmin);
        when(userService.findUserCredentialsByUserId(sysadmin.getId())).thenReturn(credentialsWithPassword("Str0ng-pass"));
        when(mqttClientCredentialsService.saveSystemWebSocketCredentials()).thenReturn(systemCredentials);

        step.apply();

        verify(userService, never()).markPasswordChangeRequired(any());

        when(userService.findUserCredentialsByUserId(sysadmin.getId())).thenReturn(credentialsWithPassword("sysadmin"));
        when(userService.isPasswordChangeRequired(sysadmin.getId())).thenReturn(true);

        step.apply();

        verify(userService, never()).markPasswordChangeRequired(any());
    }

    @Test
    void aRepairedSysadminOnTheDefaultPasswordIsMarked() throws ThingsboardException {
        when(userService.findUserByEmail(SYSADMIN_EMAIL)).thenReturn(sysadmin);
        UserCredentials disabled = disabledCredentials(sysadmin.getId());
        when(userService.findUserCredentialsByUserId(sysadmin.getId())).thenReturn(disabled);
        // the activation saves the credentials with the default password, which the seed reads again
        doAnswer(invocation -> {
            when(userService.findUserCredentialsByUserId(sysadmin.getId())).thenReturn(credentialsWithPassword("sysadmin"));
            return null;
        }).when(adminService).activateAdminCredentials(disabled);
        when(mqttClientCredentialsService.saveSystemWebSocketCredentials()).thenReturn(systemCredentials);

        step.apply();

        verify(adminService).activateAdminCredentials(disabled);
        verify(userService).markPasswordChangeRequired(sysadmin.getId());
    }

    @Test
    void aSeededStoreIgnoresTheVariableAndMarksNothing() {
        when(seedState.isSeeded()).thenReturn(true);
        when(userService.findUserByEmail(SYSADMIN_EMAIL)).thenReturn(sysadmin);
        when(userService.findUserCredentialsByUserId(sysadmin.getId())).thenReturn(credentialsWithPassword("sysadmin"));
        Logger logger = (Logger) LoggerFactory.getLogger(InstallSeedStep.class);
        Level level = logger.getLevel();
        logger.setLevel(Level.INFO);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            stepWithAdminPassword("Str0ng-pass").apply();
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(level);
        }

        assertThat(appender.list).filteredOn(event -> event.getLevel() == Level.INFO)
                .extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("TBMQ_ADMIN_PASSWORD is ignored: the store was set up before");
        assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(message -> message.contains("Str0ng-pass"));
        verify(userService, never()).markPasswordChangeRequired(any());
        verify(seedState, never()).markSeeded();
        verifyNoInteractions(adminSettingsService, adminService, mqttClientCredentialsService);
    }

    @Test
    void aSeededStoreWithoutTheVariableLogsNothingAboutIt() {
        when(seedState.isSeeded()).thenReturn(true);
        Logger logger = (Logger) LoggerFactory.getLogger(InstallSeedStep.class);
        Level level = logger.getLevel();
        logger.setLevel(Level.INFO);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            step.apply();
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(level);
        }

        assertThat(appender.list).isEmpty();
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
        WebSocketConnection connection = new WebSocketConnection();
        connection.setId(UUID.randomUUID());
        when(adminSettingsService.findAdminSettingsByKey(anyString())).thenReturn(new AdminSettings());
        when(mqttAuthProviderService.getAuthProviderByType(any())).thenReturn(Optional.of(new MqttAuthProvider()));
        when(userService.findUserByEmail(SYSADMIN_EMAIL)).thenReturn(sysadmin);
        when(userService.findUserCredentialsByUserId(sysadmin.getId())).thenReturn(activeCredentials(sysadmin.getId()));
        when(mqttClientCredentialsService.findSystemWebSocketCredentials()).thenReturn(systemCredentials);
        when(webSocketConnectionService.findWebSocketConnectionByName(sysadmin.getId(), BrokerConstants.WEB_SOCKET_DEFAULT_CONNECTION_NAME))
                .thenReturn(connection);
        when(webSocketSubscriptionService.getWebSocketSubscriptions(eq(connection.getId()), any(PageLink.class)))
                .thenReturn(new PageData<>(List.of(new WebSocketSubscription()), 1, 1, false));

        step.apply();

        verify(webSocketSubscriptionService, never()).saveDefaultWebSocketSubscription(any());
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
    void aDefaultConnectionWithoutItsSubscriptionGetsTheSubscription() throws ThingsboardException {
        WebSocketConnection connection = new WebSocketConnection();
        connection.setId(UUID.randomUUID());
        sysadminWithActiveCredentialsAndSystemCredentials();
        when(webSocketConnectionService.findWebSocketConnectionByName(sysadmin.getId(), BrokerConstants.WEB_SOCKET_DEFAULT_CONNECTION_NAME))
                .thenReturn(connection);
        when(webSocketSubscriptionService.getWebSocketSubscriptions(eq(connection.getId()), any(PageLink.class)))
                .thenReturn(new PageData<>(List.of(), 0, 0, false));

        step.apply();

        InOrder order = inOrder(webSocketSubscriptionService, seedState);
        order.verify(webSocketSubscriptionService).saveDefaultWebSocketSubscription(connection.getId());
        order.verify(seedState).markSeeded();
        verify(webSocketConnectionService, never()).saveDefaultWebSocketConnection(any(), any());
    }

    @Test
    void aFailedSettingsSaveNamesTheKeyAndKeepsTheCause() {
        RuntimeException cause = new RuntimeException("disk full");
        when(adminSettingsService.saveAdminSettings(any())).thenThrow(cause);

        assertThatThrownBy(() -> step.apply())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'general'")
                .hasMessageContaining("disk full")
                .hasCause(cause);
        verify(seedState, never()).markSeeded();
    }

    @Test
    void aFailedProviderSaveNamesTheTypeAndKeepsTheCause() {
        RuntimeException cause = new RuntimeException("disk full");
        when(mqttAuthProviderService.saveAuthProvider(any())).thenThrow(cause);

        assertThatThrownBy(() -> step.apply())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(MqttAuthProviderType.MQTT_BASIC.name())
                .hasMessageContaining("disk full")
                .hasCause(cause);
        verify(seedState, never()).markSeeded();
    }

    @Test
    void aFailedSystemCredentialsSaveNamesWhatFailedAndKeepsTheCause() throws ThingsboardException {
        RuntimeException cause = new RuntimeException("disk full");
        when(adminService.createAdmin(any(), anyBoolean())).thenReturn(sysadmin);
        when(mqttClientCredentialsService.saveSystemWebSocketCredentials()).thenThrow(cause);

        assertThatThrownBy(() -> step.apply())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("system WebSocket")
                .hasMessageContaining("disk full")
                .hasCause(cause);
        verify(seedState, never()).markSeeded();
    }

    private void sysadminWithActiveCredentialsAndSystemCredentials() {
        when(userService.findUserByEmail(SYSADMIN_EMAIL)).thenReturn(sysadmin);
        when(userService.findUserCredentialsByUserId(sysadmin.getId())).thenReturn(activeCredentials(sysadmin.getId()));
        when(mqttClientCredentialsService.findSystemWebSocketCredentials()).thenReturn(systemCredentials);
    }

    @Test
    void aFailedSysadminCreationNamesWhatFailedAndLeavesTheMarkerUnwritten() throws ThingsboardException {
        StoreFullException full = new StoreFullException("the store is full");
        when(adminService.createAdmin(any(), anyBoolean())).thenThrow(full);

        assertThatThrownBy(() -> step.apply())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sysadmin " + SYSADMIN_EMAIL)
                .hasMessageContaining("the store is full")
                .hasCause(full);
        verify(seedState, never()).markSeeded();
    }

    @Test
    void aFailedSysadminRepairNamesWhatFailedAndLeavesTheMarkerUnwritten() {
        when(userService.findUserByEmail(SYSADMIN_EMAIL)).thenReturn(sysadmin);
        when(userService.findUserCredentialsByUserId(sysadmin.getId())).thenReturn(null);
        StoreFullException full = new StoreFullException("the store is full");
        when(userService.createUserCredentials(sysadmin.getId())).thenThrow(full);

        assertThatThrownBy(() -> step.apply())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sysadmin " + SYSADMIN_EMAIL)
                .hasCause(full);
        verify(adminService, never()).activateAdminCredentials(any());
        verify(seedState, never()).markSeeded();
    }

    @Test
    void aFailedSysadminActivationNamesWhatFailedAndLeavesTheMarkerUnwritten() {
        when(userService.findUserByEmail(SYSADMIN_EMAIL)).thenReturn(sysadmin);
        UserCredentials disabled = disabledCredentials(sysadmin.getId());
        when(userService.findUserCredentialsByUserId(sysadmin.getId())).thenReturn(disabled);
        StoreFullException full = new StoreFullException("the store is full");
        doThrow(full).when(adminService).activateAdminCredentials(disabled);

        assertThatThrownBy(() -> step.apply())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sysadmin " + SYSADMIN_EMAIL)
                .hasCause(full);
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

    private UserCredentials credentialsWithPassword(String password) {
        UserCredentials credentials = activeCredentials(sysadmin.getId());
        credentials.setPassword(passwordEncoder.encode(password));
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
