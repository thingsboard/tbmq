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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.thingsboard.mqtt.broker.common.data.security.UserCredentials;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsService;
import org.thingsboard.mqtt.broker.dao.store.StoreFullException;
import org.thingsboard.mqtt.broker.dao.user.UserService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    private UserCredentials enabledCredentials() {
        UserCredentials credentials = new UserCredentials();
        credentials.setUserId(UUID.randomUUID());
        credentials.setPassword("hash");
        credentials.setEnabled(true);
        when(encoder.matches(PASSWORD, "hash")).thenReturn(true);
        return credentials;
    }

}
