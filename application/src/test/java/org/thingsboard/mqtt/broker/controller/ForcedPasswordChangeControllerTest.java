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
package org.thingsboard.mqtt.broker.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.security.Authority;
import org.thingsboard.mqtt.broker.common.data.security.UserCredentials;
import org.thingsboard.mqtt.broker.dao.user.UserService;
import org.thingsboard.mqtt.broker.service.security.auth.rest.LoginRequest;
import org.thingsboard.mqtt.broker.service.security.model.ChangePasswordRequest;
import org.thingsboard.mqtt.broker.service.security.model.ResetPasswordRequest;
import org.thingsboard.mqtt.broker.service.security.model.SecurityUser;
import org.thingsboard.mqtt.broker.service.security.model.token.JwtTokenFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs on a user of its own, never on the sysadmin: the database outlives the context, and every later class logs in as
 * the sysadmin with its default password.
 */
public class ForcedPasswordChangeControllerTest extends AbstractControllerTest {

    private static final String EMAIL = "marked@test.org";
    private static final String DEFAULT_PASSWORD = "sysadmin";

    @Autowired
    private UserService userService;
    @Autowired
    private BCryptPasswordEncoder passwordEncoder;
    @Autowired
    private JwtTokenFactory jwtTokenFactory;

    private User user;

    @Before
    public void createMarkedUser() {
        User toSave = new User();
        toSave.setAuthority(Authority.SYS_ADMIN);
        toSave.setEmail(EMAIL);
        user = userService.saveUser(toSave);
        UserCredentials credentials = userService.findUserCredentialsByUserId(user.getId());
        credentials.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        credentials.activateUserCredentials();
        userService.replaceUserCredentials(credentials);
        userService.markPasswordChangeRequired(user.getId());
    }

    @After
    public void deleteMarkedUser() {
        // @Before may have failed before the user was saved: do not hide that failure behind a NullPointerException
        if (user != null) {
            userService.deleteUser(user.getId());
        }
    }

    @Test
    public void givenMarkedUser_whenLoginTwice_thenBothRefusedAndResetWorks() throws Exception {
        String firstToken = refusedLoginResetToken();
        String secondToken = refusedLoginResetToken();
        assertThat(secondToken).isNotEqualTo(firstToken);

        doPost("/api/noauth/resetPassword", resetRequest(secondToken, DEFAULT_PASSWORD))
                .andExpect(status().isBadRequest());

        String newPassword = "N3w-pass-word";
        JsonNode tokens = readResponse(doPost("/api/noauth/resetPassword", resetRequest(secondToken, newPassword))
                .andExpect(status().isOk()), JsonNode.class);
        assertThat(tokens.has("token")).isTrue();
        assertThat(tokens.has("refreshToken")).isTrue();
        assertThat(userService.isPasswordChangeRequired(user.getId())).isFalse();

        login(EMAIL, newPassword);
    }

    @Test
    public void givenMarkedUserOnTheDefaultPassword_whenChangePasswordWithAToken_thenOnlyAnotherPasswordIsAccepted() throws Exception {
        signInWithoutLogin();

        doPost("/api/auth/changePassword", changeRequest(DEFAULT_PASSWORD, DEFAULT_PASSWORD))
                .andExpect(status().isBadRequest());
        assertThat(userService.isPasswordChangeRequired(user.getId())).isTrue();

        acceptsANewPassword(DEFAULT_PASSWORD);
    }

    @Test
    public void givenMarkedUserOnAnotherPassword_whenChangePasswordWithAToken_thenTheCurrentAndTheDefaultAreRefused() throws Exception {
        String current = "Cur-pass-w0rd";
        UserCredentials credentials = userService.findUserCredentialsByUserId(user.getId());
        credentials.setPassword(passwordEncoder.encode(current));
        userService.replaceUserCredentials(credentials);
        // a password change ends the required change, so the mark is set again
        userService.markPasswordChangeRequired(user.getId());
        signInWithoutLogin();

        doPost("/api/auth/changePassword", changeRequest(current, current)).andExpect(status().isBadRequest());
        doPost("/api/auth/changePassword", changeRequest(current, DEFAULT_PASSWORD)).andExpect(status().isBadRequest());
        assertThat(userService.isPasswordChangeRequired(user.getId())).isTrue();

        acceptsANewPassword(current);
    }

    private void acceptsANewPassword(String currentPassword) throws Exception {
        String newPassword = "N3w-pass-word";
        JsonNode tokens = readResponse(doPost("/api/auth/changePassword", changeRequest(currentPassword, newPassword))
                .andExpect(status().isOk()), JsonNode.class);
        assertThat(tokens.has("token")).isTrue();
        assertThat(tokens.has("refreshToken")).isTrue();
        assertThat(userService.isPasswordChangeRequired(user.getId())).isFalse();

        login(EMAIL, newPassword);
    }

    /**
     * The sign-in refuses a marked user, so the access token comes from the factory the sign-in itself uses.
     */
    private void signInWithoutLogin() {
        SecurityUser securityUser = new SecurityUser(user.getId());
        securityUser.setEmail(EMAIL);
        securityUser.setAuthority(Authority.SYS_ADMIN);
        securityUser.setEnabled(true);
        token = jwtTokenFactory.createAccessJwtToken(securityUser).getToken();
    }

    private static ChangePasswordRequest changeRequest(String currentPassword, String newPassword) {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword(currentPassword);
        request.setNewPassword(newPassword);
        return request;
    }

    private String refusedLoginResetToken() throws Exception {
        JsonNode body = readResponse(doPost("/api/auth/login", new LoginRequest(EMAIL, DEFAULT_PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value(15)), JsonNode.class);
        assertThat(body.get("resetToken").asText()).isNotBlank();
        return body.get("resetToken").asText();
    }

    private static ResetPasswordRequest resetRequest(String resetToken, String password) {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setResetToken(resetToken);
        request.setPassword(password);
        return request;
    }
}
