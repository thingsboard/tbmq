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
package org.thingsboard.mqtt.broker.dao.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.Authority;
import org.thingsboard.mqtt.broker.common.data.security.UserCredentials;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketConnection;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketConnectionConfiguration;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketSubscription;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketSubscriptionConfiguration;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.user.UserCredentialsDao;
import org.thingsboard.mqtt.broker.dao.user.UserDao;
import org.thingsboard.mqtt.broker.dao.user.UserService;
import org.thingsboard.mqtt.broker.dao.user.UserServiceImpl;
import org.thingsboard.mqtt.broker.dao.ws.WebSocketConnectionService;
import org.thingsboard.mqtt.broker.dao.ws.WebSocketSubscriptionService;
import org.thingsboard.mqtt.broker.exception.DataValidationException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public abstract class AbstractUserContractTest extends AbstractContractTest {

    private final List<UUID> createdUserIds = new ArrayList<>();

    @Autowired
    protected UserService userService;
    @Autowired
    protected UserDao userDao;
    @Autowired
    protected UserCredentialsDao userCredentialsDao;
    @Autowired
    protected WebSocketConnectionService webSocketConnectionService;
    @Autowired
    protected WebSocketSubscriptionService webSocketSubscriptionService;

    @After
    public void removeCreatedUsers() {
        for (UUID userId : createdUserIds) {
            if (userService.findUserById(userId) != null) {
                userService.deleteUser(userId);
            }
        }
        createdUserIds.clear();
    }

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

    @Test
    public void testCorrectUserAdditionalInfoOnSave() {
        User user = new User();
        user.setAuthority(Authority.SYS_ADMIN);
        user.setEmail("admin3@thingsboard.org");
        User savedUser = userService.saveUser(user);

        // fake password change:
        ObjectNode additionalInfo = JacksonUtil.newObjectNode();
        additionalInfo.put(UserServiceImpl.USER_PASSWORD_CHANGED, true);
        savedUser.setAdditionalInfo(additionalInfo);

        User updatedUser = userService.saveUser(savedUser);

        JsonNode updatedAdditionalInfo = updatedUser.getAdditionalInfo();
        Assert.assertNotNull(updatedAdditionalInfo);
        Assert.assertFalse(updatedAdditionalInfo.has(UserServiceImpl.USER_PASSWORD_CHANGED));

        Assert.assertTrue(updatedAdditionalInfo.has(UserServiceImpl.USER_PASSWORD_HISTORY));
        // empty since there was no actual password change happen
        Assert.assertEquals(updatedAdditionalInfo.get(UserServiceImpl.USER_PASSWORD_HISTORY), JacksonUtil.newObjectNode());

        userService.deleteUser(updatedUser.getId());
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
        user.setEmail("test_thingsboard.org");
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

    @Test
    public void testReplaceUserCredentials_shouldUpdatePasswordHistory() {
        User user = new User();
        user.setAuthority(Authority.SYS_ADMIN);
        user.setEmail("newsysadmin@thingsboard.org");
        user.setAdditionalInfo(JacksonUtil.newObjectNode());
        User savedUser = userService.saveUser(user);

        Assert.assertNotNull(savedUser);
        Assert.assertNotNull(savedUser.getId());
        Assert.assertNotNull(savedUser.getAdditionalInfo());

        JsonNode currentUserPasswordHistory = savedUser.getAdditionalInfoField(UserServiceImpl.USER_PASSWORD_HISTORY, Function.identity(), null);
        Assert.assertNull(currentUserPasswordHistory);

        UserCredentials initialCredentials = userService.findUserCredentialsByUserId(savedUser.getId());
        Assert.assertNotNull(initialCredentials);

        String newPassword = StringUtils.randomAlphanumeric(10);

        UserCredentials newCredentials = new UserCredentials();
        newCredentials.setId(initialCredentials.getId());
        newCredentials.setUserId(savedUser.getId());
        newCredentials.setEnabled(true);
        newCredentials.setPassword(newPassword);

        UserCredentials replacedCredentials = userService.replaceUserCredentials(newCredentials);
        Assert.assertNotNull(replacedCredentials);
        Assert.assertNotNull(replacedCredentials.getId());
        Assert.assertEquals(savedUser.getId(), replacedCredentials.getUserId());
        Assert.assertEquals(newPassword, replacedCredentials.getPassword());

        User updatedUser = userService.findUserById(savedUser.getId());
        Assert.assertNotNull(updatedUser);
        Assert.assertNotNull(updatedUser.getAdditionalInfo());

        currentUserPasswordHistory = updatedUser.getAdditionalInfoField(UserServiceImpl.USER_PASSWORD_HISTORY, Function.identity(), null);

        Assert.assertNotNull(currentUserPasswordHistory);
        Assert.assertEquals(1, currentUserPasswordHistory.size());

        userService.deleteUser(savedUser.getId());
    }

    @Test
    public void replaceLeavesExactlyOneCredentialsWithTheNewId() {
        User user = saveNewUser();
        UserCredentials initial = userService.findUserCredentialsByUserId(user.getId());
        String password = StringUtils.randomAlphanumeric(10);

        UserCredentials replaced = userService.replaceUserCredentials(enabled(initial, password));

        assertThat(replaced.getId()).isNotNull().isNotEqualTo(initial.getId());
        assertThat(userCredentialsDao.findById(initial.getId())).isNull();
        assertThat(userCredentialsDao.findByActivateToken(initial.getActivateToken())).isNull();
        assertThat(userCredentialsDao.find().stream().filter(c -> user.getId().equals(c.getUserId())))
                .extracting(UserCredentials::getId).containsExactly(replaced.getId());
        UserCredentials found = userService.findUserCredentialsByUserId(user.getId());
        assertThat(found.getId()).isEqualTo(replaced.getId());
        assertThat(found.getPassword()).isEqualTo(password);
        assertThat(found.isEnabled()).isTrue();
    }

    // the javadoc of UserCredentialsDao.replace: credentials that do not exist are skipped, the replacement is saved
    @Test
    public void replacingMissingCredentialsStillSavesTheReplacement() {
        User user = saveNewUser();
        UserCredentials initial = userService.findUserCredentialsByUserId(user.getId());
        userCredentialsDao.removeById(initial.getId());
        UserCredentials replacement = enabled(initial, StringUtils.randomAlphanumeric(10));
        replacement.setId(null);

        UserCredentials saved = userCredentialsDao.replace(UUID.randomUUID(), replacement);

        assertThat(saved.getId()).isNotNull().isNotEqualTo(initial.getId());
        assertThat(userCredentialsDao.findById(saved.getId()).getUserId()).isEqualTo(user.getId());
        assertThat(userCredentialsDao.findByUserId(user.getId()).getId()).isEqualTo(saved.getId());
    }

    @Test
    public void deletingAUserRemovesItsCredentials() {
        User user = saveNewUser();
        UserCredentials credentials = userService.findUserCredentialsByUserId(user.getId());

        userService.deleteUser(user.getId());

        assertThat(userService.findUserById(user.getId())).isNull();
        assertThat(userService.findUserCredentialsByUserId(user.getId())).isNull();
        assertThat(userCredentialsDao.findById(credentials.getId())).isNull();
        assertThat(userCredentialsDao.findByActivateToken(credentials.getActivateToken())).isNull();
    }

    // what a stop between the two writes of a new user leaves: the user without credentials
    @Test
    public void aUserWithoutCredentialsCanBeDeleted() {
        User user = saveNewUser();
        UserCredentials credentials = userService.findUserCredentialsByUserId(user.getId());
        userCredentialsDao.removeById(credentials.getId());

        userService.deleteUser(user.getId());

        assertThat(userService.findUserById(user.getId())).isNull();
        assertThat(userService.findUserByEmail(user.getEmail())).isNull();
    }

    @Test
    public void emailsAreUnique() {
        User first = saveNewUser();
        User second = new User();
        second.setAuthority(Authority.SYS_ADMIN);
        second.setEmail(first.getEmail());

        assertThatThrownBy(() -> userService.saveUser(second))
                .isInstanceOf(DataValidationException.class)
                .hasMessage("User with email '" + first.getEmail() + "'  already present in database!");
        assertThat(userService.findUserByEmail(first.getEmail()).getId()).isEqualTo(first.getId());
    }

    @Test
    public void findByActivateAndResetToken() {
        User user = saveNewUser();
        UserCredentials credentials = userService.findUserCredentialsByUserId(user.getId());
        String activateToken = credentials.getActivateToken();

        assertThat(userCredentialsDao.findByActivateToken(activateToken).getId()).isEqualTo(credentials.getId());

        UserCredentials activated = enabled(credentials, StringUtils.randomAlphanumeric(10));
        userService.saveUserCredentials(activated);

        assertThat(userCredentialsDao.findByActivateToken(activateToken)).isNull();

        String resetToken = userService.requestPasswordReset(user.getEmail()).getResetToken();

        assertThat(resetToken).isNotEmpty();
        assertThat(userService.findUserCredentialsByResetToken(resetToken).getId()).isEqualTo(credentials.getId());
        assertThat(userCredentialsDao.findByResetToken(resetToken).getUserId()).isEqualTo(user.getId());
        assertThat(userCredentialsDao.findByResetToken(StringUtils.randomAlphanumeric(30))).isNull();
        assertThat(userCredentialsDao.findByActivateToken(StringUtils.randomAlphanumeric(30))).isNull();
    }

    @Test
    public void removingAMissingIdAnswersTrue() {
        assertThat(userDao.removeById(UUID.randomUUID())).isTrue();
        assertThat(userCredentialsDao.removeById(UUID.randomUUID())).isTrue();
    }

    @Test
    public void deletingAUserRemovesItsWebSocketConnections() {
        User user = saveNewUser();
        User bystander = saveNewUser();
        WebSocketConnection first = saveWebSocketConnection(user, "first");
        WebSocketConnection second = saveWebSocketConnection(user, "second");
        WebSocketConnection others = saveWebSocketConnection(bystander, "first");
        WebSocketSubscription firstSubscription = saveWebSocketSubscription(first);
        WebSocketSubscription secondSubscription = saveWebSocketSubscription(second);
        WebSocketSubscription othersSubscription = saveWebSocketSubscription(others);

        userService.deleteUser(user.getId());

        assertThat(webSocketConnectionService.getWebSocketConnectionById(first.getId())).isEmpty();
        assertThat(webSocketConnectionService.getWebSocketConnectionById(second.getId())).isEmpty();
        assertThat(webSocketConnectionService.findWebSocketConnectionByName(user.getId(), "first")).isNull();
        assertThat(webSocketSubscriptionService.getWebSocketSubscriptionById(firstSubscription.getId())).isEmpty();
        assertThat(webSocketSubscriptionService.getWebSocketSubscriptionById(secondSubscription.getId())).isEmpty();
        assertThat(webSocketConnectionService.getWebSocketConnections(user.getId(), new PageLink(10)).getData()).isEmpty();
        assertThat(webSocketConnectionService.getWebSocketConnectionById(others.getId())).isPresent();
        assertThat(webSocketSubscriptionService.getWebSocketSubscriptionById(othersSubscription.getId())).isPresent();
    }

    protected User saveNewUser() {
        User user = new User();
        user.setAuthority(Authority.SYS_ADMIN);
        user.setEmail("user-" + UUID.randomUUID() + "@thingsboard.org");
        User saved = userService.saveUser(user);
        createdUserIds.add(saved.getId());
        return saved;
    }

    private WebSocketConnection saveWebSocketConnection(User user, String name) {
        WebSocketConnection connection = new WebSocketConnection();
        connection.setName(name);
        connection.setUserId(user.getId());
        WebSocketConnectionConfiguration configuration = new WebSocketConnectionConfiguration();
        configuration.setClientId("clientId");
        configuration.setUrl("ws://localhost:8084/mqtt");
        connection.setConfiguration(configuration);
        return webSocketConnectionService.saveWebSocketConnection(connection);
    }

    private WebSocketSubscription saveWebSocketSubscription(WebSocketConnection connection) {
        WebSocketSubscription subscription = new WebSocketSubscription();
        subscription.setWebSocketConnectionId(connection.getId());
        WebSocketSubscriptionConfiguration configuration = new WebSocketSubscriptionConfiguration();
        configuration.setTopicFilter("test/#");
        subscription.setConfiguration(configuration);
        return webSocketSubscriptionService.saveWebSocketSubscription(subscription);
    }

    /**
     * Enabled credentials with the id and user of {@code credentials}, the given password and no activate token.
     */
    protected static UserCredentials enabled(UserCredentials credentials, String password) {
        UserCredentials enabled = new UserCredentials();
        enabled.setId(credentials.getId());
        enabled.setUserId(credentials.getUserId());
        enabled.setEnabled(true);
        enabled.setPassword(password);
        return enabled;
    }
}
