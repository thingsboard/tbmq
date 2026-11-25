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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.context.WebApplicationContext;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.controller.AbstractControllerTest;
import org.thingsboard.mqtt.broker.dao.user.UserService;
import org.thingsboard.mqtt.broker.service.security.auth.rest.LoginRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * A fresh Lite install with {@code TBMQ_ADMIN_PASSWORD} set: the seeded sysadmin gets that password, is not marked, and
 * does not accept the default one. The class has its own context and its own empty store ({@code @DirtiesContext} of
 * the base class, a store directory per class).
 */
@TestPropertySource(properties = {
        "lite.test.system-data.enabled=false",
        "standalone.security.admin-password=Str0ng-pass"
})
public class AdminPasswordVariableLiteTest extends AbstractControllerTest {

    private static final String ADMIN_PASSWORD = "Str0ng-pass";

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
     * The base class signs in as the sysadmin with the default password, which this class's sysadmin does not accept.
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
    public void theAdminPasswordVariableSetsThePassword() throws Exception {
        login(SYS_ADMIN_EMAIL, ADMIN_PASSWORD);

        User sysadmin = userService.findUserByEmail(SYS_ADMIN_EMAIL);
        assertThat(userService.isPasswordChangeRequired(sysadmin.getId())).isFalse();
        doPost("/api/auth/login", new LoginRequest(SYS_ADMIN_EMAIL, "sysadmin")).andExpect(status().isUnauthorized());
    }

}
