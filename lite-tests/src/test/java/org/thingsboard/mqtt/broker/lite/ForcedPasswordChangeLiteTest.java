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
package org.thingsboard.mqtt.broker.lite;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.context.WebApplicationContext;
import org.thingsboard.mqtt.broker.controller.AbstractControllerTest;
import org.thingsboard.mqtt.broker.dao.user.UserService;
import org.thingsboard.mqtt.broker.service.security.auth.rest.LoginRequest;
import org.thingsboard.mqtt.broker.service.security.model.ResetPasswordRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * The real defaults of a fresh Lite install with no {@code TBMQ_ADMIN_PASSWORD}: the seeded sysadmin keeps the default
 * password and is marked, so it must choose another one before it can sign in. The class has its own context and its
 * own empty store ({@code @DirtiesContext} of the base class, a store directory per class), so no other class sees the
 * password it sets.
 */
@TestPropertySource(properties = "lite.test.system-data.enabled=false")
public class ForcedPasswordChangeLiteTest extends AbstractControllerTest {

    private static final String DEFAULT_PASSWORD = "sysadmin";

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private UserService userService;

    /**
     * The base class disables every auth provider before each test; this test starts from the seeded ones.
     */
    @Override
    public void beforeTest() {
    }

    /**
     * The base class signs in as the sysadmin, which this class must not do before the test says so.
     */
    @Override
    @Before
    public void setupWebTest() {
        if (mockMvc == null) {
            mockMvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        }
    }

    @Override
    @After
    public void teardownWebTest() {
    }

    @Test
    public void theDefaultSysadminMustChangeThePassword() throws Exception {
        UUID sysadminId = userService.findUserByEmail(SYS_ADMIN_EMAIL).getId();
        assertThat(userService.isPasswordChangeRequired(sysadminId)).isTrue();

        String firstToken = refusedLoginResetToken();
        String secondToken = refusedLoginResetToken();
        assertThat(secondToken).isNotEqualTo(firstToken);

        doPost("/api/noauth/resetPassword", resetRequest(secondToken, DEFAULT_PASSWORD)).andExpect(status().isBadRequest());

        String newPassword = "N3w-pass-word";
        JsonNode tokens = readResponse(doPost("/api/noauth/resetPassword", resetRequest(secondToken, newPassword))
                .andExpect(status().isOk()), JsonNode.class);
        assertThat(tokens.has("token")).isTrue();
        assertThat(tokens.has("refreshToken")).isTrue();

        assertThat(userService.isPasswordChangeRequired(sysadminId)).isFalse();
        login(SYS_ADMIN_EMAIL, newPassword);
        doPost("/api/auth/login", new LoginRequest(SYS_ADMIN_EMAIL, DEFAULT_PASSWORD)).andExpect(status().isUnauthorized());
    }

    private String refusedLoginResetToken() throws Exception {
        JsonNode body = readResponse(doPost("/api/auth/login", new LoginRequest(SYS_ADMIN_EMAIL, DEFAULT_PASSWORD))
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
