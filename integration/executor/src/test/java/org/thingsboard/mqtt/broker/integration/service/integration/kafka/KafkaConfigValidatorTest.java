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
package org.thingsboard.mqtt.broker.integration.service.integration.kafka;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KafkaConfigValidatorTest {

    private static KafkaIntegrationConfig validConfig() {
        KafkaIntegrationConfig config = new KafkaIntegrationConfig();
        config.setBootstrapServers("localhost:9092");
        config.setTopic("test-topic");
        config.setAcks("all");
        config.setCompression("gzip");
        return config;
    }

    @Test
    void testValidConfig() {
        KafkaIntegrationConfig config = new KafkaIntegrationConfig(
                false,
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
                StandardCharsets.UTF_8.name(),
                false
        );
        KafkaConfigValidator.validate(config);
    }

    @Test
    void testInvalidBootstrapServers() {
        KafkaIntegrationConfig config = new KafkaIntegrationConfig(
                false,
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
                StandardCharsets.UTF_8.name(),
                false
        );
        assertThatThrownBy(() -> KafkaConfigValidator.validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Bootstrap servers cannot be empty");
    }

    @Test
    void testInvalidTopic() {
        KafkaIntegrationConfig config = new KafkaIntegrationConfig(
                false,
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
                StandardCharsets.UTF_8.name(),
                false
        );
        assertThatThrownBy(() -> KafkaConfigValidator.validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Topic is required");
    }

    @Test
    void testNegativeRetries() {
        KafkaIntegrationConfig config = new KafkaIntegrationConfig(
                false,
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
                StandardCharsets.UTF_8.name(),
                false
        );
        assertThatThrownBy(() -> KafkaConfigValidator.validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Retries must not be less than 0");
    }

    @Test
    void testInvalidAcks() {
        KafkaIntegrationConfig config = new KafkaIntegrationConfig(
                false,
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
                StandardCharsets.UTF_8.name(),
                false
        );
        assertThatThrownBy(() -> KafkaConfigValidator.validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid acks value");
    }

    @Test
    void testInvalidCompression() {
        KafkaIntegrationConfig config = new KafkaIntegrationConfig(
                false,
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
                StandardCharsets.UTF_8.name(),
                false
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
                false,
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
                StandardCharsets.UTF_8.name(),
                false
        );
        assertThatThrownBy(() -> KafkaConfigValidator.validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Detected SSL-related configurations, but 'security.protocol' is missing");
    }

    @Test
    void givenTemplatedKeyAndHeaders_whenValidateTemplates_thenPasses() {
        KafkaIntegrationConfig config = validConfig();
        config.setKey("${clientId}");
        config.setKafkaHeaders(Map.of("mqtt-topic", "${topicName}", "user", "${username}"));

        KafkaConfigValidator.validateTemplates(config);
    }

    @Test
    void givenEmptyKey_whenValidateTemplates_thenPasses() {
        KafkaIntegrationConfig config = validConfig();
        config.setKey("");

        KafkaConfigValidator.validateTemplates(config);
    }

    @Test
    void givenUnknownKeyPlaceholder_whenValidateTemplates_thenThrows() {
        KafkaIntegrationConfig config = validConfig();
        config.setKey("${clientID}");

        assertThatThrownBy(() -> KafkaConfigValidator.validateTemplates(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Key: unknown placeholder '${clientID}'");
    }

    @Test
    void givenUnclosedHeaderPlaceholder_whenValidateTemplates_thenThrows() {
        KafkaIntegrationConfig config = validConfig();
        config.setKafkaHeaders(Map.of("mqtt-topic", "${topicName"));

        assertThatThrownBy(() -> KafkaConfigValidator.validateTemplates(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Header 'mqtt-topic': unclosed placeholder in '${topicName'");
    }

    @Test
    void givenInvalidTemplates_whenValidate_thenPasses() {
        // validate() also runs when a stored integration starts; templates are checked only on save
        KafkaIntegrationConfig config = validConfig();
        config.setKey("${topic}");
        config.setKafkaHeaders(Map.of("mqtt-topic", "abc${topicName"));

        KafkaConfigValidator.validate(config);
    }

}
