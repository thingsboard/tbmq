/**
 * Copyright © 2016-2023 The Thingsboard Authors
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

import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.security.Authority;
import org.thingsboard.mqtt.broker.common.data.security.UserCredentials;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.exception.DataValidationException;
import org.thingsboard.mqtt.broker.dao.user.UserService;

@DaoSqlTest
public class UserServiceTest extends AbstractServiceTest {
    @Autowired
    private UserService userService;

    @Test
    public void testFindUserByEmail() {
        User user = userService.findUserByEmail("sysadmin@thingsboard.org");
        Assert.assertNotNull(user);
        Assert.assertEquals(Authority.SYS_ADMIN, user.getAuthority());
        user = userService.findUserByEmail("fake@thingsboard.org");
        Assert.assertNull(user);
    }

    @Test
    public void testFindUserById() {
        User user = userService.findUserByEmail("sysadmin@thingsboard.org");
        Assert.assertNotNull(user);
        User foundUser = userService.findUserById(user.getId());
        Assert.assertNotNull(foundUser);
        Assert.assertEquals(user, foundUser);
    }

    @Test
    public void testFindUserCredentials() {
        User user = userService.findUserByEmail("sysadmin@thingsboard.org");
        Assert.assertNotNull(user);
        UserCredentials userCredentials = userService.findUserCredentialsByUserId(user.getId());
        Assert.assertNotNull(userCredentials);
    }

    @Test
    public void testSaveUser() {
        User user = new User();
        user.setAuthority(Authority.SYS_ADMIN);
        user.setEmail("admin2@thingsboard.org");
        User savedUser = userService.saveUser(user);
        Assert.assertNotNull(savedUser);
        Assert.assertNotNull(savedUser.getId());
        Assert.assertTrue(savedUser.getCreatedTime() > 0);
        Assert.assertEquals(user.getEmail(), savedUser.getEmail());
        Assert.assertEquals(user.getAuthority(), savedUser.getAuthority());
        UserCredentials userCredentials = userService.findUserCredentialsByUserId(savedUser.getId());
        Assert.assertNotNull(userCredentials);
        Assert.assertNotNull(userCredentials.getId());
        Assert.assertNotNull(userCredentials.getUserId());
        Assert.assertNotNull(userCredentials.getActivateToken());

        savedUser.setFirstName("Joe");
        savedUser.setLastName("Downs");

        userService.saveUser(savedUser);
        savedUser = userService.findUserById(savedUser.getId());
        Assert.assertEquals("Joe", savedUser.getFirstName());
        Assert.assertEquals("Downs", savedUser.getLastName());

        userService.deleteUser(savedUser.getId());
    }

    @Test(expected = DataValidationException.class)
    public void testSaveUserWithSameEmail() {
        User user = new User();
        user.setAuthority(Authority.SYS_ADMIN);
        user.setEmail("sysadmin@thingsboard.org");
        userService.saveUser(user);
    }

    @Test(expected = DataValidationException.class)
    public void testSaveUserWithInvalidEmail() {
        User user = new User();
        user.setAuthority(Authority.SYS_ADMIN);
        user.setEmail("tenant_thingsboard.org");
        userService.saveUser(user);
    }

    @Test(expected = DataValidationException.class)
    public void testSaveUserWithEmptyEmail() {
        User user = new User();
        user.setAuthority(Authority.SYS_ADMIN);
        user.setEmail(null);
        userService.saveUser(user);
    }

    @Test
    public void testDeleteUser() {
        User user = new User();
        user.setAuthority(Authority.SYS_ADMIN);
        user.setEmail("admin2@thingsboard.org");
        User savedUser = userService.saveUser(user);
        Assert.assertNotNull(savedUser);
        Assert.assertNotNull(savedUser.getId());

        User foundUser = userService.findUserById(savedUser.getId());
        Assert.assertNotNull(foundUser);

        UserCredentials userCredentials = userService.findUserCredentialsByUserId(foundUser.getId());
        Assert.assertNotNull(userCredentials);

        userService.deleteUser(foundUser.getId());

        userCredentials = userService.findUserCredentialsByUserId(foundUser.getId());
        foundUser = userService.findUserById(foundUser.getId());
        Assert.assertNull(foundUser);
        Assert.assertNull(userCredentials);
    }
}
