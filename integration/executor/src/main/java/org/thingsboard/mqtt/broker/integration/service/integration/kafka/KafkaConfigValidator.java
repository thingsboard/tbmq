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

import org.apache.kafka.clients.CommonClientConfigs;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;

import java.util.Map;

public class KafkaConfigValidator {

    private static final String SSL = "ssl.";

    public static void validate(KafkaIntegrationConfig kafkaIntegrationConfig) {
        validateBootstrapServers(kafkaIntegrationConfig.getBootstrapServers());
        validateTopic(kafkaIntegrationConfig.getTopic());
        if (kafkaIntegrationConfig.getRetries() < 0) {
            throw new IllegalArgumentException("Retries must not be less than 0");
        }
        if (kafkaIntegrationConfig.getBatchSize() < 0) {
            throw new IllegalArgumentException("Batch size must not be less than 0");
        }
        if (kafkaIntegrationConfig.getLinger() < 0) {
            throw new IllegalArgumentException("Linger ms must not be less than 0");
        }
        if (kafkaIntegrationConfig.getBufferMemory() < 0) {
            throw new IllegalArgumentException("Buffer memory must not be less than 0");
        }
        if (!isValidAcks(kafkaIntegrationConfig.getAcks())) {
            throw new IllegalArgumentException("Invalid acks value");
        }
        if (!isValidCompressionType(kafkaIntegrationConfig.getCompression())) {
            throw new IllegalArgumentException("Invalid compression type");
        }
        validateOtherProps(kafkaIntegrationConfig);
    }

    public static void validateBootstrapServers(String bootstrapServers) {
        doValidateBootstrapServers(bootstrapServers);
    }

    private static void doValidateBootstrapServers(String bootstrapServers) {
        if (StringUtils.isEmpty(bootstrapServers)) {
            throw new IllegalArgumentException("Bootstrap servers cannot be empty");
        }

        String[] servers = bootstrapServers.split(",");
        for (String server : servers) {
            if (!isValidHostPort(server.trim())) {
                throw new IllegalArgumentException("Invalid bootstrap server format: " + server);
            }
        }
    }

    private static boolean isValidHostPort(String server) {
        String[] parts = server.split(":");

        if (parts.length != 2) {
            return false;
        }

        String host = parts[0].trim();
        String portStr = parts[1].trim();

        if (host.isEmpty()) {
            return false;
        }

        try {
            int port = Integer.parseInt(portStr);
            return port >= 1 && port <= 65535;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static void validateTopic(String topic) {
        if (StringUtils.isEmpty(topic)) {
            throw new IllegalArgumentException("Topic is required");
        }
    }

    private static boolean isValidAcks(String acks) {
        return "0".equals(acks) || "1".equals(acks) || "-1".equals(acks) || "all".equals(acks);
    }

    private static boolean isValidCompressionType(String compressionType) {
        return "none".equals(compressionType) ||
                "gzip".equals(compressionType) ||
                "snappy".equals(compressionType) ||
                "lz4".equals(compressionType) ||
                "zstd".equals(compressionType);
    }

    private static void validateOtherProps(KafkaIntegrationConfig kafkaIntegrationConfig) {
        if (!CollectionUtils.isEmpty(kafkaIntegrationConfig.getOtherProperties())) {
            Map<String, String> properties = kafkaIntegrationConfig.getOtherProperties();
            boolean sslConfigPresent = properties.keySet().stream().anyMatch(k -> k.contains(SSL));
            if (sslConfigPresent && !properties.containsKey(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG)) {
                throw new IllegalArgumentException(
                        "Detected SSL-related configurations, but 'security.protocol' is missing. " +
                                "The default protocol is 'PLAINTEXT'. Please ensure 'security.protocol' is set correctly"
                );
            }
        }
    }
}
