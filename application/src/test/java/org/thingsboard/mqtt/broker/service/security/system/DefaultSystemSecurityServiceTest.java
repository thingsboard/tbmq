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
package org.thingsboard.mqtt.broker.service.security.system;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.thingsboard.mqtt.broker.common.data.security.UserCredentials;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsService;
import org.thingsboard.mqtt.broker.dao.store.StoreFullException;
import org.thingsboard.mqtt.broker.dao.user.UserService;
import org.thingsboard.mqtt.broker.exception.DataValidationException;
import org.thingsboard.mqtt.broker.service.security.exception.UserPasswordExpiredException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultSystemSecurityServiceTest {

    private static final String PASSWORD = "sysadmin";

    private final UserService userService = mock(UserService.class);
    private final BCryptPasswordEncoder encoder = mock(BCryptPasswordEncoder.class);
    private final AdminSettingsService adminSettingsService = mock(AdminSettingsService.class);
    private final DefaultSystemSecurityService service = new DefaultSystemSecurityService(userService, encoder, adminSettingsService);

    @Test
    void givenAFullDisk_whenTheLastLoginCannotBeStored_thenTheLoginGoesOn() {
        UserCredentials credentials = enabledCredentials();
        doThrow(new StoreFullException("Not enough free disk space for the store"))
                .when(userService).onUserLoginSuccessful(credentials.getUserId());

        assertThatCode(() -> service.validateUserCredentials(credentials, "sysadmin@thingsboard.org", PASSWORD))
                .doesNotThrowAnyException();
        verify(userService).onUserLoginSuccessful(credentials.getUserId());
    }

    @Test
    void givenAnyOtherFailure_whenTheLastLoginCannotBeStored_thenTheLoginFailsAsBefore() {
        UserCredentials credentials = enabledCredentials();
        IllegalStateException failure = new IllegalStateException("database is down");
        doThrow(failure).when(userService).onUserLoginSuccessful(credentials.getUserId());

        assertThatThrownBy(() -> service.validateUserCredentials(credentials, "sysadmin@thingsboard.org", PASSWORD))
                .isSameAs(failure);
    }

    @Test
    void aMarkedUserGetsTheExpiredPasswordFlow() {
        UserCredentials credentials = enabledCredentials();
        UserCredentials withToken = new UserCredentials();
        withToken.setResetToken("reset-token");
        when(userService.isPasswordChangeRequired(credentials.getUserId())).thenReturn(true);
        when(userService.requestExpiredPasswordReset(credentials.getId())).thenReturn(withToken);

        assertThatThrownBy(() -> service.validateUserCredentials(credentials, "sysadmin@thingsboard.org", PASSWORD))
                .isInstanceOfSatisfying(UserPasswordExpiredException.class,
                        e -> assertThat(e.getResetToken()).isEqualTo("reset-token"));
        verify(userService).onUserLoginSuccessful(credentials.getUserId());
    }

    @Test
    void aMarkedUserWithAWrongPasswordIsRefusedWithoutAResetToken() {
        UserCredentials credentials = enabledCredentials();
        when(userService.isPasswordChangeRequired(credentials.getUserId())).thenReturn(true);

        assertThatThrownBy(() -> service.validateUserCredentials(credentials, "sysadmin@thingsboard.org", "wrong"))
                .isInstanceOf(BadCredentialsException.class);
        verify(userService, never()).requestExpiredPasswordReset(credentials.getId());
    }

    @Test
    void anUnmarkedUserLogsIn() {
        UserCredentials credentials = enabledCredentials();
        when(userService.isPasswordChangeRequired(credentials.getUserId())).thenReturn(false);

        assertThatCode(() -> service.validateUserCredentials(credentials, "sysadmin@thingsboard.org", PASSWORD))
                .doesNotThrowAnyException();
        verify(userService, never()).requestExpiredPasswordReset(credentials.getId());
    }

    @Test
    void aMarkedResetRefusesTheDefaultAndTheCurrentPassword() {
        UserCredentials credentials = enabledCredentials();
        when(encoder.matches("Current-pass1", "hash")).thenReturn(true);
        when(userService.isPasswordChangeRequired(credentials.getUserId())).thenReturn(true);

        assertThatThrownBy(() -> service.validateRequiredPasswordChange("sysadmin", credentials))
                .isInstanceOf(DataValidationException.class)
                .hasMessage("Choose a password other than the default and the current one");
        assertThatThrownBy(() -> service.validateRequiredPasswordChange("Current-pass1", credentials))
                .isInstanceOf(DataValidationException.class)
                .hasMessage("Choose a password other than the default and the current one");
        assertThatCode(() -> service.validateRequiredPasswordChange("N3w-pass", credentials))
                .doesNotThrowAnyException();
    }

    @Test
    void anUnmarkedUserMayChooseTheDefaultPassword() {
        UserCredentials credentials = enabledCredentials();
        when(userService.isPasswordChangeRequired(credentials.getUserId())).thenReturn(false);

        assertThatCode(() -> service.validateRequiredPasswordChange("sysadmin", credentials))
                .doesNotThrowAnyException();
    }

    private UserCredentials enabledCredentials() {
        UserCredentials credentials = new UserCredentials();
        credentials.setId(UUID.randomUUID());
        credentials.setUserId(UUID.randomUUID());
        credentials.setPassword("hash");
        credentials.setEnabled(true);
        when(encoder.matches(PASSWORD, "hash")).thenReturn(true);
        return credentials;
    }

}
