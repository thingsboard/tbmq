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
package org.thingsboard.mqtt.broker.standalone.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetricsTokenAuthorizationManagerTest {

    @Test
    void grantsOnlyTheExactBearerToken() {
        MetricsTokenAuthorizationManager manager = new MetricsTokenAuthorizationManager("t");

        assertThat(granted(manager, "Bearer t")).isTrue();
        assertThat(granted(manager, "Bearer t ")).isFalse();
        assertThat(granted(manager, " Bearer t")).isFalse();
        assertThat(granted(manager, "bearer t")).isFalse();
        assertThat(granted(manager, "BEARER t")).isFalse();
        assertThat(granted(manager, "Bearer T")).isFalse();
        assertThat(granted(manager, "Bearer tt")).isFalse();
        assertThat(granted(manager, "Bearer ")).isFalse();
        assertThat(granted(manager, "Bearer")).isFalse();
        assertThat(granted(manager, "t")).isFalse();
        assertThat(granted(manager, "Basic t")).isFalse();
        assertThat(granted(manager, null)).isFalse();
    }

    @Test
    void aBlankConfiguredTokenGrantsNothing() {
        for (String configured : new String[]{"", "  ", null}) {
            MetricsTokenAuthorizationManager manager = new MetricsTokenAuthorizationManager(configured);

            assertThat(granted(manager, "Bearer ")).as("empty token, configured '%s'", configured).isFalse();
            assertThat(granted(manager, "Bearer   ")).as("blank token, configured '%s'", configured).isFalse();
            assertThat(granted(manager, "Bearer " + configured)).as("configured '%s'", configured).isFalse();
            assertThat(granted(manager, null)).as("no header, configured '%s'", configured).isFalse();
        }
    }

    @Test
    void aTokenOutsideVisibleAsciiRefusesTheStartWithoutNamingTheValue() {
        for (String configured : new String[]{"s3cr\u00e9t", "s3 cret", " s3cret", "s3cret ", "s3\tcret", "s3\u007Fcret"}) {
            assertThatThrownBy(() -> new MetricsTokenAuthorizationManager(configured))
                    .as("configured '%s'", configured)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("TBMQ_METRICS_TOKEN")
                    .satisfies(e -> assertThat(e.getMessage()).doesNotContain(configured.trim()));
        }
    }

    @Test
    void everyVisibleAsciiCharacterIsAccepted() {
        StringBuilder token = new StringBuilder();
        for (char c = 0x21; c <= 0x7E; c++) {
            token.append(c);
        }

        assertThat(granted(new MetricsTokenAuthorizationManager(token.toString()), "Bearer " + token)).isTrue();
    }

    @Test
    void theTokenIsNotPartOfTheToString() {
        assertThat(new MetricsTokenAuthorizationManager("s3cret-value").toString()).doesNotContain("s3cret-value");
    }

    private static boolean granted(MetricsTokenAuthorizationManager manager, String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        if (authorization != null) {
            request.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        }
        return manager.authorize(() -> null, new RequestAuthorizationContext(request)).isGranted();
    }

}
