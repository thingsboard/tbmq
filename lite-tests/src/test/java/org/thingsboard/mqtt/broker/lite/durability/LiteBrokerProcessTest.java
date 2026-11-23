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
package org.thingsboard.mqtt.broker.lite.durability;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class LiteBrokerProcessTest {

    private static final String SYSTEM_DATA_ENABLED = "lite.test.system-data.enabled";
    private static final Path STORE = Path.of("/tmp/store");

    @Test
    void givenNoSystemDataProperty_whenBrokerProperties_thenItIsNotPassedOn() {
        Map<String, String> properties = LiteBrokerProcess.brokerProperties(STORE, 1883, 8083, Map.of(), name -> null);

        assertThat(properties).doesNotContainKey(SYSTEM_DATA_ENABLED)
                .containsEntry("store.path", STORE.toString())
                .containsEntry("listener.tcp.bind_port", "1883")
                .containsEntry("server.port", "8083");
    }

    @Test
    void givenSystemDataPropertyInTheTestJvm_whenBrokerProperties_thenItIsPassedOn() {
        Function<String, String> systemProperty = name -> SYSTEM_DATA_ENABLED.equals(name) ? "false" : null;

        Map<String, String> properties = LiteBrokerProcess.brokerProperties(STORE, 1883, 8083, Map.of(), systemProperty);

        assertThat(properties).containsEntry(SYSTEM_DATA_ENABLED, "false");
    }

    @Test
    void givenSystemDataPropertyInTheTestJvmAndInExtraProperties_whenBrokerProperties_thenExtraWins() {
        Function<String, String> systemProperty = name -> SYSTEM_DATA_ENABLED.equals(name) ? "false" : null;

        Map<String, String> properties = LiteBrokerProcess.brokerProperties(STORE, 1883, 8083,
                Map.of(SYSTEM_DATA_ENABLED, "true", "store.device-msg.read-page-size", "256"), systemProperty);

        assertThat(properties).containsEntry(SYSTEM_DATA_ENABLED, "true")
                .containsEntry("store.device-msg.read-page-size", "256");
    }

    @Test
    void givenNoDatasourceNeeded_whenBrokerProperties_thenNoDatasourcePropertyIsSet() {
        assertThat(LiteBrokerProcess.brokerProperties(STORE, 1883, 8083, Map.of(), name -> null).keySet())
                .noneMatch(key -> key.startsWith("spring.datasource"));
    }

}
