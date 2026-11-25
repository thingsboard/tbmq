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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lite with {@code TBMQ_METRICS_TOKEN} set: health and the links page are open, every other actuator path needs
 * exactly {@code Authorization: Bearer <token>}.
 */
@TestPropertySource(properties = {
        "lite.test.system-data.enabled=false",
        AbstractActuatorSecurityLiteTest.PROMETHEUS_ON,
        "standalone.security.metrics-token=s3cret"
})
public class ActuatorSecurityLiteTest extends AbstractActuatorSecurityLiteTest {

    private static final String[] PROTECTED = {
            "/actuator/prometheus", "/actuator/prometheus/", "/actuator/prometheus?x=1", "/actuator/info"
    };

    @Test
    public void healthIsOpenWithDetails() throws Exception {
        actuator("/actuator/health", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.standaloneStore.details.store").value("ok"));
        // the probe groups exist only on a Kubernetes platform: 404 here, but never refused
        actuator("/actuator/health/liveness", null).andExpect(status().is(not(HttpStatus.UNAUTHORIZED.value())));
        actuator("/actuator/health/readiness", null).andExpect(status().is(not(HttpStatus.UNAUTHORIZED.value())));
        actuator("/actuator", null).andExpect(status().isOk());
        // not the links page (MVC does not match a trailing slash): refused like any non-health path, never open by accident
        actuator("/actuator/", null).andExpect(status().isUnauthorized());
    }

    @Test
    public void prometheusAndInfoNeedTheToken() throws Exception {
        for (String uri : PROTECTED) {
            actuator(uri, null).andExpect(status().isUnauthorized());
            actuator(uri, "Bearer wrong").andExpect(status().isUnauthorized());
            actuator(uri, "Bearer s3cret").andExpect(status().isOk());
        }
        MvcResult result = actuator("/actuator/prometheus", "Bearer s3cret").andExpect(status().isOk()).andReturn();
        assertThat(result.getResponse().getContentAsString()).startsWith("# HELP");
    }

    @Test
    public void onlyExactlyTheConfiguredTokenOpensTheProtectedPaths() throws Exception {
        for (String header : new String[]{"Bearer ", "Bearer", "Bearer s3cret ", "Bearer  s3cret", "bearer s3cret", "BEARER s3cret",
                "Bearer S3CRET", "Basic s3cret", "s3cret"}) {
            actuator("/actuator/prometheus", header).andExpect(status().isUnauthorized());
            actuator("/actuator/info", header).andExpect(status().isUnauthorized());
        }
    }

    @Test
    public void aTokenOnAnOpenPathDoesNotBreakIt() throws Exception {
        actuator("/actuator/health", "Bearer wrong").andExpect(status().isOk());
    }

}
