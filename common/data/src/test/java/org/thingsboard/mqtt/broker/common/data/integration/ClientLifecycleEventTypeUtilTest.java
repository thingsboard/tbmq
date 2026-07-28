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
package org.thingsboard.mqtt.broker.common.data.integration;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ClientLifecycleEventTypeUtilTest {

    static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void givenConfigWithEventTypes_whenIsOptedIn_thenTrue() {
        ObjectNode configuration = MAPPER.createObjectNode();
        configuration.putArray(ClientLifecycleEventTypeUtil.LIFECYCLE_EVENT_TYPES_KEY).add("CLIENT_CONNECTED");

        assertThat(ClientLifecycleEventTypeUtil.isOptedIn(configuration)).isTrue();
    }

    @Test
    void givenConfigWithEmptyEventTypes_whenIsOptedIn_thenFalse() {
        ObjectNode configuration = MAPPER.createObjectNode();
        configuration.putArray(ClientLifecycleEventTypeUtil.LIFECYCLE_EVENT_TYPES_KEY);

        assertThat(ClientLifecycleEventTypeUtil.isOptedIn(configuration)).isFalse();
    }

    @Test
    void givenConfigWithoutEventTypesKey_whenIsOptedIn_thenFalse() {
        assertThat(ClientLifecycleEventTypeUtil.isOptedIn(MAPPER.createObjectNode())).isFalse();
    }

    @Test
    void givenNullConfig_whenIsOptedIn_thenFalse() {
        assertThat(ClientLifecycleEventTypeUtil.isOptedIn(null)).isFalse();
    }

}
