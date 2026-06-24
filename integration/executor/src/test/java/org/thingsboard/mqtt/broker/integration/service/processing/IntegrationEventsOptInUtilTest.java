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
package org.thingsboard.mqtt.broker.integration.service.processing;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.common.data.integration.IntegrationLifecycleMsg;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;

import static org.assertj.core.api.Assertions.assertThat;

class IntegrationEventsOptInUtilTest {

    private IntegrationLifecycleMsg msgWithConfig(String json) {
        JsonNode config = json == null ? null : JacksonUtil.toJsonNode(json);
        return IntegrationLifecycleMsg.builder().configuration(config).build();
    }

    @Test
    void givenNonEmptyLifecycleEventTypes_whenIsOptedIn_thenTrue() {
        var msg = msgWithConfig("{\"lifecycleEventTypes\":[\"CLIENT_CONNECTED\"]}");
        assertThat(IntegrationEventsOptInUtil.isOptedIn(msg)).isTrue();
    }

    @Test
    void givenEmptyLifecycleEventTypesArray_whenIsOptedIn_thenFalse() {
        var msg = msgWithConfig("{\"lifecycleEventTypes\":[]}");
        assertThat(IntegrationEventsOptInUtil.isOptedIn(msg)).isFalse();
    }

    @Test
    void givenMissingLifecycleEventTypes_whenIsOptedIn_thenFalse() {
        var msg = msgWithConfig("{\"topicFilters\":[\"a/b\"]}");
        assertThat(IntegrationEventsOptInUtil.isOptedIn(msg)).isFalse();
    }

    @Test
    void givenNullConfiguration_whenIsOptedIn_thenFalse() {
        assertThat(IntegrationEventsOptInUtil.isOptedIn(msgWithConfig(null))).isFalse();
    }

    @Test
    void givenNullLifecycleMsg_whenIsOptedIn_thenFalse() {
        assertThat(IntegrationEventsOptInUtil.isOptedIn(null)).isFalse();
    }
}
