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

import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ClusterSubscriptionControllerTest extends AbstractControllerTest {

    private static final String INTEGRATION_SUBSCRIPTIONS_PATH = "/api/subscription/integration?integrationId=";

    @Before
    public void beforeTest() throws Exception {
        loginSysAdmin();
    }

    @Test
    public void givenIntegrationWithoutSubscriptions_whenGetIntegrationSubscriptions_thenReturnsEmptySet() throws Exception {
        doGet(INTEGRATION_SUBSCRIPTIONS_PATH + UUID.randomUUID())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    public void givenIntegrationIdThatIsNotUuid_whenGetIntegrationSubscriptions_thenRejectsWithBadRequest() throws Exception {
        doGet(INTEGRATION_SUBSCRIPTIONS_PATH + "not-a-uuid")
                .andExpect(status().isBadRequest());
    }

}
