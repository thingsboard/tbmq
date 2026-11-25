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
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lite with the actuator moved to another base path through {@code management.endpoints.web.base-path}: the token
 * protects it where it is, not where it was.
 */
@TestPropertySource(properties = {
        "lite.test.system-data.enabled=false",
        AbstractActuatorSecurityLiteTest.PROMETHEUS_ON,
        "management.endpoints.web.base-path=/manage",
        "standalone.security.metrics-token=s3cret"
})
public class ActuatorSecurityBasePathLiteTest extends AbstractActuatorSecurityLiteTest {

    @Test
    public void prometheusAndInfoNeedTheTokenUnderTheNewBasePath() throws Exception {
        for (String uri : new String[]{"/manage/prometheus", "/manage/info"}) {
            actuator(uri, null).andExpect(status().isUnauthorized());
            actuator(uri, "Bearer wrong").andExpect(status().isUnauthorized());
            actuator(uri, "Bearer s3cret").andExpect(status().isOk());
        }
        String body = actuator("/manage/prometheus", "Bearer s3cret").andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();
        assertThat(body).startsWith("# HELP");
    }

    @Test
    public void healthAndTheLinksPageAreOpenUnderTheNewBasePath() throws Exception {
        actuator("/manage/health", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        actuator("/manage/health", "Bearer wrong").andExpect(status().isOk());
        actuator("/manage/health/liveness", null).andExpect(status().is(not(HttpStatus.UNAUTHORIZED.value())));
        actuator("/manage", null).andExpect(status().isOk());
    }

    @Test
    public void theOldBasePathServesNoEndpoint() throws Exception {
        // an unknown path is not an endpoint: the UI fallback forwards it to the index page and nothing is served
        for (String uri : new String[]{"/actuator/prometheus", "/actuator/info", "/actuator/health", "/actuator"}) {
            MockHttpServletResponse response = actuator(uri, "Bearer s3cret").andReturn().getResponse();

            assertThat(response.getForwardedUrl()).as(uri).isEqualTo("/index.html");
            assertThat(response.getContentAsString()).as(uri).isEmpty();
        }
    }

}
