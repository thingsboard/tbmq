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

import org.junit.Test;
import org.thingsboard.mqtt.broker.controller.AbstractControllerTest;

import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * REST endpoints the single-node edition does not have answer 404, also to the system administrator.
 */
public class LiteApiSurfaceTest extends AbstractControllerTest {

    private static final String NODE_DRAIN_PATH = "/api/node/drain";

    @Test
    public void nodeDrainEndpointIsNotFound() throws Exception {
        loginSysAdmin();

        doGet(NODE_DRAIN_PATH).andExpect(status().isNotFound());
        doPost(NODE_DRAIN_PATH).andExpect(status().isNotFound());
        doDelete(NODE_DRAIN_PATH).andExpect(status().isNotFound());
    }

    @Test
    public void integrationSubscriptionsEndpointIsNotFound() throws Exception {
        loginSysAdmin();

        doGet("/api/subscription/integration?integrationId=" + UUID.randomUUID()).andExpect(status().isNotFound());
    }

}
