/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.integration.service.integration.kafka;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KafkaConfigValidatorTest {

    @Test
    void testValidConfig() {
        KafkaIntegrationConfig config = new KafkaIntegrationConfig(
                "localhost:9092",
                "test-topic",
                null,
                null,
                3,
                16384,
                1,
                33554432,
                "all",
                "gzip",
                null,
                null,
                Map.of(),
                Map.of(),
                StandardCharsets.UTF_8.name()
        );
        KafkaConfigValidator.validate(config);
    }

    @Test
    void testInvalidBootstrapServers() {
        KafkaIntegrationConfig config = new KafkaIntegrationConfig(
                "",
                "test-topic",
                null,
                null,
                3,
                16384,
                1,
                33554432,
                "all",
                "gzip",
                null,
                null,
                Map.of(),
                Map.of(),
                StandardCharsets.UTF_8.name()
        );
        assertThatThrownBy(() -> KafkaConfigValidator.validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Bootstrap servers cannot be empty");
    }

    @Test
    void testInvalidTopic() {
        KafkaIntegrationConfig config = new KafkaIntegrationConfig(
                "localhost:9092",
                "",
                null,
                null,
                3,
                16384,
                1,
                33554432,
                "all",
                "gzip",
                null,
                null,
                Map.of(),
                Map.of(),
                StandardCharsets.UTF_8.name()
        );
        assertThatThrownBy(() -> KafkaConfigValidator.validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Topic is required");
    }

    @Test
    void testNegativeRetries() {
        KafkaIntegrationConfig config = new KafkaIntegrationConfig(
                "localhost:9092",
                "test-topic",
                null,
                null,
                -1,
                16384,
                1,
                33554432,
                "all",
                "gzip",
                null,
                null,
                Map.of(),
                Map.of(),
                StandardCharsets.UTF_8.name()
        );
        assertThatThrownBy(() -> KafkaConfigValidator.validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Retries must not be less than 0");
    }

    @Test
    void testInvalidAcks() {
        KafkaIntegrationConfig config = new KafkaIntegrationConfig(
                "localhost:9092",
                "test-topic",
                null,
                null,
                3,
                16384,
                1,
                33554432,
                "invalid",
                "gzip",
                null,
                null,
                Map.of(),
                Map.of(),
                StandardCharsets.UTF_8.name()
        );
        assertThatThrownBy(() -> KafkaConfigValidator.validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid acks value");
    }

    @Test
    void testInvalidCompression() {
        KafkaIntegrationConfig config = new KafkaIntegrationConfig(
                "localhost:9092",
                "test-topic",
                null,
                null,
                3,
                16384,
                1,
                33554432,
                "-1",
                "invalid",
                null,
                null,
                Map.of(),
                Map.of(),
                StandardCharsets.UTF_8.name()
        );
        assertThatThrownBy(() -> KafkaConfigValidator.validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid compression type");
    }

    @Test
    void testMissingSecurityProtocolWithSSLConfig() {
        Map<String, String> properties = new HashMap<>();
        properties.put("ssl.keystore.location", "/path/to/keystore");

        KafkaIntegrationConfig config = new KafkaIntegrationConfig(
                "localhost:9092",
                "test-topic",
                null,
                null,
                3,
                16384,
                1,
                33554432,
                "all",
                "gzip",
                null,
                null,
                properties,
                Map.of(),
                StandardCharsets.UTF_8.name()
        );
        assertThatThrownBy(() -> KafkaConfigValidator.validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Detected SSL-related configurations, but 'security.protocol' is missing");
    }

}
