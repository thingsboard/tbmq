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
package org.thingsboard.mqtt.broker.service.user;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.thingsboard.mqtt.broker.common.data.security.UserCredentials;
import org.thingsboard.mqtt.broker.dao.user.UserService;
import org.thingsboard.mqtt.broker.dao.ws.WebSocketConnectionService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AdminServiceImplTest {

    private final UserService userService = mock(UserService.class);
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final AdminServiceImpl adminService = new AdminServiceImpl(userService, passwordEncoder, mock(WebSocketConnectionService.class));

    @Test
    void disabledCredentialsAreActivatedWithTheDefaultPassword() {
        UserCredentials credentials = new UserCredentials();
        credentials.setId(UUID.randomUUID());
        credentials.setUserId(UUID.randomUUID());
        credentials.setEnabled(false);
        credentials.setActivateToken("token");

        adminService.activateAdminCredentials(credentials);

        ArgumentCaptor<UserCredentials> saved = ArgumentCaptor.forClass(UserCredentials.class);
        verify(userService).saveUserCredentials(saved.capture());
        assertThat(saved.getValue().isEnabled()).isTrue();
        assertThat(saved.getValue().getActivateToken()).isNull();
        assertThat(passwordEncoder.matches(AdminServiceImpl.SYSADMIN_PASSWORD, saved.getValue().getPassword())).isTrue();
    }

    @Test
    void disabledCredentialsAreActivatedWithTheGivenPassword() {
        UserCredentials credentials = new UserCredentials();
        credentials.setId(UUID.randomUUID());
        credentials.setUserId(UUID.randomUUID());
        credentials.setEnabled(false);
        credentials.setActivateToken("token");

        adminService.activateAdminCredentials(credentials, "Str0ng-pass");

        ArgumentCaptor<UserCredentials> saved = ArgumentCaptor.forClass(UserCredentials.class);
        verify(userService).saveUserCredentials(saved.capture());
        assertThat(saved.getValue().isEnabled()).isTrue();
        assertThat(passwordEncoder.matches("Str0ng-pass", saved.getValue().getPassword())).isTrue();
    }

    @Test
    void enabledCredentialsAreRefused() {
        UserCredentials credentials = new UserCredentials();
        credentials.setUserId(UUID.randomUUID());
        credentials.setEnabled(true);

        assertThatThrownBy(() -> adminService.activateAdminCredentials(credentials))
                .isInstanceOf(IllegalStateException.class);
        verify(userService, never()).saveUserCredentials(any());
    }

    @Test
    void credentialsThatAlreadyHaveAPasswordAreRefused() {
        UserCredentials credentials = new UserCredentials();
        credentials.setUserId(UUID.randomUUID());
        credentials.setEnabled(false);
        credentials.setPassword("$2a$10$hash");

        assertThatThrownBy(() -> adminService.activateAdminCredentials(credentials))
                .isInstanceOf(IllegalStateException.class);
        assertThat(credentials.getPassword()).isEqualTo("$2a$10$hash");
        verify(userService, never()).saveUserCredentials(any());
    }

}
