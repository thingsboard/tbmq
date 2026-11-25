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

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lite with no metrics token, the default: info and prometheus are refused whatever the request carries, although
 * {@code METRICS_ENDPOINTS_EXPOSE} lists them. The empty token is the one the shipped file produces for an unset
 * variable.
 */
@TestPropertySource(properties = {
        "lite.test.system-data.enabled=false",
        AbstractActuatorSecurityLiteTest.PROMETHEUS_ON,
        "standalone.security.metrics-token="
})
public class ActuatorSecurityNoTokenLiteTest extends AbstractActuatorSecurityLiteTest {

    @Test
    public void withoutATokenNothingButHealthOpens() throws Exception {
        for (String uri : new String[]{"/actuator/prometheus", "/actuator/prometheus/", "/actuator/prometheus?x=1", "/actuator/info"}) {
            for (String header : new String[]{null, "Bearer ", "Bearer", "Bearer  ", "Bearer wrong", "Basic abc"}) {
                actuator(uri, header).andExpect(status().isUnauthorized());
            }
        }
    }

    @Test
    public void healthAndTheLinksPageStayOpen() throws Exception {
        actuator("/actuator/health", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.standaloneStore.details.store").value("ok"));
        // the probe groups exist only on a Kubernetes platform: 404 here, but never refused
        actuator("/actuator/health/liveness", null).andExpect(status().is(not(HttpStatus.UNAUTHORIZED.value())));
        actuator("/actuator", null).andExpect(status().isOk());
    }

}
