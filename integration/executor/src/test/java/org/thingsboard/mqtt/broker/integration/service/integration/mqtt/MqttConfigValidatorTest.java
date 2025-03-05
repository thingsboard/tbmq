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
package org.thingsboard.mqtt.broker.integration.service.integration.mqtt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MqttConfigValidatorTest {

    @Test
    void testValidMqttConfig() {
        assertDoesNotThrow(() -> MqttConfigValidator.validate(createValidConfig()));
    }

    @Test
    void testEmptyHostThrowsException() {
        MqttIntegrationConfig config = createValidConfig();
        config.setHost("");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                MqttConfigValidator.validate(config));
        assertEquals("Host cannot be empty", exception.getMessage());
    }

    @Test
    void testInvalidPortThrowsException() {
        MqttIntegrationConfig config = createValidConfig();
        config.setPort(70000);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                MqttConfigValidator.validate(config));
        assertEquals("Port must be between 1 and 65535", exception.getMessage());
    }

    @Test
    void testEmptyTopicThrowsException() {
        MqttIntegrationConfig config = createValidConfig();
        config.setTopicName("");
        config.setUseMsgTopicName(false);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                MqttConfigValidator.validate(config));
        assertEquals("Topic name is required when useMsgTopicName is disabled!", exception.getMessage());
    }

    @Test
    void testEmptyTopicDoesNotThrowException() {
        MqttIntegrationConfig config = createValidConfig();
        config.setTopicName("");
        config.setUseMsgTopicName(true);

        assertDoesNotThrow(() -> MqttConfigValidator.validate(config));
    }

    @Test
    void testSingleWildcardTopicDoesNotThrowException() {
        MqttIntegrationConfig config = createValidConfig();
        config.setTopicName("abc/+");
        config.setUseMsgTopicName(true);

        assertDoesNotThrow(() -> MqttConfigValidator.validate(config));
    }

    @Test
    void testSingleWildcardTopicThrowsException() {
        MqttIntegrationConfig config = createValidConfig();
        config.setTopicName("abc/+");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                MqttConfigValidator.validate(config));
        assertEquals("Topic name cannot contain wildcard characters", exception.getMessage());
    }

    @Test
    void testMultiWildcardTopicThrowsException() {
        MqttIntegrationConfig config = createValidConfig();
        config.setTopicName("abc/#");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                MqttConfigValidator.validate(config));
        assertEquals("Topic name cannot contain wildcard characters", exception.getMessage());
    }

    @Test
    void test$TopicThrowsException() {
        MqttIntegrationConfig config = createValidConfig();
        config.setTopicName("$abc/abc");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                MqttConfigValidator.validate(config));
        assertEquals("Topic name cannot start with $ character", exception.getMessage());
    }

    @Test
    void testEmptyClientIdThrowsException() {
        MqttIntegrationConfig config = createValidConfig();
        config.setClientId("");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                MqttConfigValidator.validate(config));
        assertEquals("Client ID is required", exception.getMessage());
    }

    @Test
    void testZeroConnectionTimeoutThrowsException() {
        MqttIntegrationConfig config = createValidConfig();
        config.setConnectTimeoutSec(0);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                MqttConfigValidator.validate(config));
        assertEquals("Connection timeout (seconds) must be greater than 0", exception.getMessage());
    }

    @Test
    void testNegativeReconnectPeriodThrowsException() {
        MqttIntegrationConfig config = createValidConfig();
        config.setReconnectPeriodSec(-1);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                MqttConfigValidator.validate(config));
        assertEquals("Reconnect period (seconds) must not be less than 0", exception.getMessage());
    }

    @Test
    void testInvalidMqttVersionThrowsException() {
        MqttIntegrationConfig config = createValidConfig();
        config.setMqttVersion(6);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                MqttConfigValidator.validate(config));
        assertEquals("Invalid MQTT version. Allowed values: 3 (MQTT_3_1), 4 (MQTT_3_1_1), 5 (MQTT_5)", exception.getMessage());
    }

    @Test
    void testInvalidQoSThrowsException() {
        MqttIntegrationConfig config = createValidConfig();
        config.setQos(3);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                MqttConfigValidator.validate(config));
        assertEquals("Invalid MQTT Quality of Service (QoS). Allowed values: 0 (AT_MOST_ONCE), 1 (AT_LEAST_ONCE), 2 (EXACTLY_ONCE)", exception.getMessage());
    }

    @Test
    void testNegativeKeepAliveThrowsException() {
        MqttIntegrationConfig config = createValidConfig();
        config.setKeepAliveSec(-1);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                MqttConfigValidator.validate(config));
        assertEquals("Keep Alive (seconds) must not be less than 0", exception.getMessage());
    }

    private MqttIntegrationConfig createValidConfig() {
        MqttIntegrationConfig config = new MqttIntegrationConfig();
        config.setHost("mqtt.example.com");
        config.setPort(1883);
        config.setTopicName("test/topic");
        config.setClientId("client-123");
        config.setConnectTimeoutSec(10);
        config.setReconnectPeriodSec(5);
        config.setMqttVersion(3);
        config.setQos(1);
        config.setKeepAliveSec(30);
        return config;
    }
}
