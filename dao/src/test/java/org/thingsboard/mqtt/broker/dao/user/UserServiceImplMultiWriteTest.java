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
package org.thingsboard.mqtt.broker.dao.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.security.Authority;
import org.thingsboard.mqtt.broker.common.data.security.UserCredentials;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The service operations that write more than one entity, and what each leaves behind when it stops between its writes.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplMultiWriteTest {

    @Mock
    private UserDao userDao;
    @Mock
    private UserCredentialsDao userCredentialsDao;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userDao, userCredentialsDao);
    }

    @Test
    void replaceIsOneDaoCall() {
        UUID userId = UUID.randomUUID();
        UUID oldId = UUID.randomUUID();
        User user = user(userId);
        UserCredentials existing = credentials(oldId, userId);
        UserCredentials replacement = credentials(oldId, userId);
        replacement.setEnabled(true);
        replacement.setPassword("new password");
        UserCredentials saved = credentials(UUID.randomUUID(), userId);
        when(userCredentialsDao.findById(oldId)).thenReturn(existing);
        when(userDao.findById(userId)).thenReturn(user);
        when(userDao.findByEmail(user.getEmail())).thenReturn(user);
        when(userCredentialsDao.replace(eq(oldId), any())).thenReturn(saved);

        UserCredentials result = userService.replaceUserCredentials(replacement);

        ArgumentCaptor<UserCredentials> replaced = ArgumentCaptor.forClass(UserCredentials.class);
        verify(userCredentialsDao).replace(eq(oldId), replaced.capture());
        assertThat(replaced.getValue()).isSameAs(replacement);
        assertThat(replaced.getValue().getId()).as("the replacement is saved under a new id").isNull();
        assertThat(result).isSameAs(saved);
        verify(userCredentialsDao, never()).removeById(any());
        verify(userCredentialsDao, never()).save(any());
        // the password history is a later, separate write: a missing entry is harmless
        verify(userDao).save(user);
    }

    @Test
    void aUserWithoutCredentialsCanBeDeleted() {
        UUID userId = UUID.randomUUID();
        when(userCredentialsDao.findByUserId(userId)).thenReturn(null);

        assertThatCode(() -> userService.deleteUser(userId)).doesNotThrowAnyException();

        verify(userDao).removeById(userId);
        verify(userCredentialsDao, never()).removeById(any());
    }

    @Test
    void aUserWithCredentialsLosesThemBeforeItIsDeleted() {
        UUID userId = UUID.randomUUID();
        UUID credentialsId = UUID.randomUUID();
        when(userCredentialsDao.findByUserId(userId)).thenReturn(credentials(credentialsId, userId));

        userService.deleteUser(userId);

        InOrder order = inOrder(userCredentialsDao, userDao);
        order.verify(userCredentialsDao).removeById(credentialsId);
        order.verify(userDao).removeById(userId);
    }

    private static User user(UUID id) {
        User user = new User(id);
        user.setEmail("user@thingsboard.org");
        user.setAuthority(Authority.SYS_ADMIN);
        return user;
    }

    private static UserCredentials credentials(UUID id, UUID userId) {
        UserCredentials credentials = new UserCredentials();
        credentials.setId(id);
        credentials.setUserId(userId);
        return credentials;
    }

}
