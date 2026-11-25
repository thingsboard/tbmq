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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;
import org.thingsboard.mqtt.broker.controller.AbstractControllerTest;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * Sends actuator requests through the full filter chain, with no user signed in. A subclass sets the metrics token
 * through its own {@code @TestPropertySource}, so each has its own context and its own empty store.
 */
public abstract class AbstractActuatorSecurityLiteTest extends AbstractControllerTest {

    static final String PROMETHEUS_ON = "management.prometheus.metrics.export.enabled=true";

    @Autowired
    private WebApplicationContext webApplicationContext;

    /**
     * The base class disables every auth provider before each test; these tests sign nobody in.
     */
    @Override
    public void beforeTest() {
    }

    /**
     * The base class signs in as the sysadmin with the default password; these requests carry no user.
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

    ResultActions actuator(String uri, String authorization) throws Exception {
        var request = get(uri);
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return mockMvc.perform(request);
    }

}
