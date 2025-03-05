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

import org.thingsboard.mqtt.broker.common.data.util.StringUtils;

import java.util.Set;

import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.MULTI_LEVEL_WILDCARD;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.SINGLE_LEVEL_WILDCARD;

public class MqttConfigValidator {

    private static final Set<Integer> SUPPORTED_MQTT_VERSIONS = Set.of(3, 4, 5);
    private static final Set<Integer> SUPPORTED_MQTT_QOS = Set.of(0, 1, 2);

    public static void validate(MqttIntegrationConfig mqttIntegrationConfig) {
        if (StringUtils.isEmpty(mqttIntegrationConfig.getHost())) {
            throw new IllegalArgumentException("Host cannot be empty");
        }
        if (mqttIntegrationConfig.getPort() < 1 || mqttIntegrationConfig.getPort() > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        validateTopicName(mqttIntegrationConfig);
        if (StringUtils.isEmpty(mqttIntegrationConfig.getClientId())) {
            throw new IllegalArgumentException("Client ID is required");
        }
        if (mqttIntegrationConfig.getConnectTimeoutSec() <= 0) {
            throw new IllegalArgumentException("Connection timeout (seconds) must be greater than 0");
        }
        if (mqttIntegrationConfig.getReconnectPeriodSec() < 0) {
            throw new IllegalArgumentException("Reconnect period (seconds) must not be less than 0");
        }
        validateMqttVersion(mqttIntegrationConfig.getMqttVersion());
        validateMqttQoS(mqttIntegrationConfig.getQos());
        if (mqttIntegrationConfig.getKeepAliveSec() < 0) {
            throw new IllegalArgumentException("Keep Alive (seconds) must not be less than 0");
        }
    }

    private static void validateTopicName(MqttIntegrationConfig mqttIntegrationConfig) {
        var topicName = mqttIntegrationConfig.getTopicName();
        if (StringUtils.isEmpty(topicName)) {
            if (!mqttIntegrationConfig.isUseMsgTopicName()) {
                throw new IllegalArgumentException("Topic name is required when Dynamic topic name option is disabled");
            } else {
                return;
            }
        }
        if (mqttIntegrationConfig.isUseMsgTopicName()) {
            return;
        }
        if (topicName.contains(MULTI_LEVEL_WILDCARD)
                || topicName.contains(SINGLE_LEVEL_WILDCARD)) {
            throw new IllegalArgumentException("Topic name cannot contain wildcard characters");
        }
        if (topicName.startsWith("$")) {
            throw new IllegalArgumentException("Topic name cannot start with $ character");
        }
    }

    private static void validateMqttVersion(int mqttVersion) {
        if (!SUPPORTED_MQTT_VERSIONS.contains(mqttVersion)) {
            throw new IllegalArgumentException("Invalid MQTT version. Allowed values: 3 (MQTT_3_1), 4 (MQTT_3_1_1), 5 (MQTT_5)");
        }
    }

    private static void validateMqttQoS(int qos) {
        if (!SUPPORTED_MQTT_QOS.contains(qos)) {
            throw new IllegalArgumentException("Invalid MQTT Quality of Service (QoS). Allowed values: 0 (AT_MOST_ONCE), 1 (AT_LEAST_ONCE), 2 (EXACTLY_ONCE)");
        }
    }

}
