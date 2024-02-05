/**
 * Copyright © 2016-2024 The Thingsboard Authors
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thingsboard.mqtt.broker.common.data.StringUtils;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.Authority;
import org.thingsboard.mqtt.broker.common.data.security.UserCredentials;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.exception.DataValidationException;
import org.thingsboard.mqtt.broker.dao.exception.IncorrectParameterException;
import org.thingsboard.mqtt.broker.dao.service.DataValidator;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.thingsboard.mqtt.broker.dao.service.Validator.validateId;
import static org.thingsboard.mqtt.broker.dao.service.Validator.validatePageLink;
import static org.thingsboard.mqtt.broker.dao.service.Validator.validateString;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    public static final String USER_PASSWORD_HISTORY = "userPasswordHistory";
    private static final String LAST_LOGIN_TS = "lastLoginTs";

    private static final int DEFAULT_TOKEN_LENGTH = 30;
    public static final String INCORRECT_USER_ID = "Incorrect userId ";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${security.user_login_case_sensitive:true}")
    private boolean userLoginCaseSensitive;

    private final UserDao userDao;
    private final UserCredentialsDao userCredentialsDao;

    @Override
    public User findUserByEmail(String email) {
        if (log.isTraceEnabled()) {
            log.trace("Executing findUserByEmail [{}]", email);
        }
        validateString(email, "Incorrect email " + email);
        if (userLoginCaseSensitive) {
            return userDao.findByEmail(email);
        } else {
            return userDao.findByEmail(email.toLowerCase());
        }
    }

    @Override
    public User findUserById(UUID userId) {
        if (log.isTraceEnabled()) {
            log.trace("Executing findUserById [{}]", userId);
        }
        validateId(userId, INCORRECT_USER_ID + userId);
        return userDao.findById(userId);
    }

    @Override
    public User saveUser(User user) {
        if (log.isTraceEnabled()) {
            log.trace("Executing saveUser [{}]", user);
        }
        userValidator.validate(user);
        if (!userLoginCaseSensitive) {
            user.setEmail(user.getEmail().toLowerCase());
        }
        User savedUser = userDao.save(user);
        if (user.getId() == null) {
            UserCredentials userCredentials = new UserCredentials();
            userCredentials.setEnabled(false);
            userCredentials.setActivateToken(StringUtils.randomAlphanumeric(DEFAULT_TOKEN_LENGTH));
            userCredentials.setUserId(savedUser.getId());
            saveUserCredentialsAndPasswordHistory(userCredentials);
        }
        return savedUser;
    }

    @Override
    public UserCredentials findUserCredentialsByUserId(UUID userId) {
        if (log.isTraceEnabled()) {
            log.trace("Executing findUserCredentialsByUserId [{}]", userId);
        }
        validateId(userId, INCORRECT_USER_ID + userId);
        return userCredentialsDao.findByUserId(userId);
    }

    @Override
    public UserCredentials saveUserCredentials(UserCredentials userCredentials) {
        if (log.isTraceEnabled()) {
            log.trace("Executing saveUserCredentials [{}]", userCredentials);
        }
        userCredentialsValidator.validate(userCredentials);
        return saveUserCredentialsAndPasswordHistory(userCredentials);
    }

    @Override
    public UserCredentials replaceUserCredentials(UserCredentials userCredentials) {
        if (log.isTraceEnabled()) {
            log.trace("Executing replaceUserCredentials [{}]", userCredentials);
        }
        userCredentialsValidator.validate(userCredentials);
        userCredentialsDao.removeById(userCredentials.getId());
        userCredentials.setId(null);
        return saveUserCredentialsAndPasswordHistory(userCredentials);
    }

    @Override
    @Transactional
    public void deleteUser(UUID userId) {
        if (log.isTraceEnabled()) {
            log.trace("Executing deleteUser [{}]", userId);
        }
        validateId(userId, INCORRECT_USER_ID + userId);
        UserCredentials userCredentials = userCredentialsDao.findByUserId(userId);
        if (userCredentials == null) {
            throw new IncorrectParameterException("Cannot find user credentials!");
        }
        userCredentialsDao.removeById(userCredentials.getId());
        userDao.removeById(userId);
    }

    @Override
    public PageData<User> findUsers(PageLink pageLink) {
        if (log.isTraceEnabled()) {
            log.trace("Executing findUsers, pageLink [{}]", pageLink);
        }
        validatePageLink(pageLink);
        return userDao.findAll(pageLink);
    }

    @Override
    public void onUserLoginSuccessful(UUID userId) {
        if (log.isTraceEnabled()) {
            log.trace("Executing onUserLoginSuccessful [{}]", userId);
        }
        User user = findUserById(userId);
        setLastLoginTs(user);
        saveUser(user);
    }

    @Override
    public UserCredentials requestPasswordReset(String email) {
        if (log.isTraceEnabled()) {
            log.trace("Executing requestPasswordReset email [{}]", email);
        }
        DataValidator.validateEmail(email);
        User user = findUserByEmail(email);
        if (user == null) {
            throw new UsernameNotFoundException(String.format("Unable to find user by email [%s]", email));
        }
        UserCredentials userCredentials = userCredentialsDao.findByUserId(user.getId());
        if (!userCredentials.isEnabled()) {
            throw new DisabledException(String.format("User credentials not enabled [%s]", email));
        }
        userCredentials.setResetToken(StringUtils.randomAlphanumeric(DEFAULT_TOKEN_LENGTH));
        return saveUserCredentials(userCredentials);
    }

    @Override
    public UserCredentials findUserCredentialsByResetToken(String resetToken) {
        if (log.isTraceEnabled()) {
            log.trace("Executing findUserCredentialsByResetToken [{}]", resetToken);
        }
        validateString(resetToken, "Incorrect resetToken " + resetToken);
        return userCredentialsDao.findByResetToken(resetToken);
    }

    private void setLastLoginTs(User user) {
        JsonNode additionalInfo = user.getAdditionalInfo();
        if (!(additionalInfo instanceof ObjectNode)) {
            additionalInfo = JacksonUtil.newObjectNode();
        }
        ((ObjectNode) additionalInfo).put(LAST_LOGIN_TS, System.currentTimeMillis());
        user.setAdditionalInfo(additionalInfo);
    }

    private UserCredentials saveUserCredentialsAndPasswordHistory(UserCredentials userCredentials) {
        UserCredentials result = userCredentialsDao.save(userCredentials);
        User user = findUserById(userCredentials.getUserId());
        if (userCredentials.getPassword() != null) {
            updatePasswordHistory(user, userCredentials);
        }
        return result;
    }

    private void updatePasswordHistory(User user, UserCredentials userCredentials) {
        JsonNode additionalInfo = user.getAdditionalInfo();
        if (!(additionalInfo instanceof ObjectNode)) {
            additionalInfo = objectMapper.createObjectNode();
        }
        if (additionalInfo.has(USER_PASSWORD_HISTORY)) {
            JsonNode userPasswordHistoryJson = additionalInfo.get(USER_PASSWORD_HISTORY);
            Map<String, String> userPasswordHistoryMap = objectMapper.convertValue(userPasswordHistoryJson, new TypeReference<>() {
            });
            userPasswordHistoryMap.put(Long.toString(System.currentTimeMillis()), userCredentials.getPassword());
            userPasswordHistoryJson = objectMapper.valueToTree(userPasswordHistoryMap);
            ((ObjectNode) additionalInfo).replace(USER_PASSWORD_HISTORY, userPasswordHistoryJson);
        } else {
            Map<String, String> userPasswordHistoryMap = new HashMap<>();
            userPasswordHistoryMap.put(Long.toString(System.currentTimeMillis()), userCredentials.getPassword());
            JsonNode userPasswordHistoryJson = objectMapper.valueToTree(userPasswordHistoryMap);
            ((ObjectNode) additionalInfo).set(USER_PASSWORD_HISTORY, userPasswordHistoryJson);
        }
        user.setAdditionalInfo(additionalInfo);
        saveUser(user);
    }

    private final DataValidator<User> userValidator = new DataValidator<>() {
        @Override
        protected void validateDataImpl(User user) {
            if (StringUtils.isEmpty(user.getEmail())) {
                throw new DataValidationException("User email should be specified!");
            }

            validateEmail(user.getEmail());

            Authority authority = user.getAuthority();
            if (authority == null) {
                throw new DataValidationException("User authority isn't defined!");
            }

            User existentUserWithEmail = findUserByEmail(user.getEmail());
            if (existentUserWithEmail != null && !isSameData(existentUserWithEmail, user)) {
                throw new DataValidationException("User with email '" + user.getEmail() + "' "
                        + " already present in database!");
            }
        }
    };

    private final DataValidator<UserCredentials> userCredentialsValidator = new DataValidator<>() {
        @Override
        protected void validateCreate(UserCredentials userCredentials) {
            throw new IncorrectParameterException("Creation of new user credentials is prohibited.");
        }

        @Override
        protected void validateDataImpl(UserCredentials userCredentials) {
            if (userCredentials.getUserId() == null) {
                throw new DataValidationException("User credentials should be assigned to user!");
            }
            if (userCredentials.isEnabled()) {
                if (StringUtils.isEmpty(userCredentials.getPassword())) {
                    throw new DataValidationException("Enabled user credentials should have password!");
                }
                if (StringUtils.isNotEmpty(userCredentials.getActivateToken())) {
                    throw new DataValidationException("Enabled user credentials can't have activate token!");
                }
            }
            UserCredentials existingUserCredentialsEntity = userCredentialsDao.findById(userCredentials.getId());
            if (existingUserCredentialsEntity == null) {
                throw new DataValidationException("Unable to update non-existent user credentials!");
            }
            User user = findUserById(userCredentials.getUserId());
            if (user == null) {
                throw new DataValidationException("Can't assign user credentials to non-existent user!");
            }
        }
    };
}
