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
package org.thingsboard.mqtt.broker.common.data.client.credentials;

import lombok.Getter;

import java.util.Optional;

@Getter
public enum ScramAlgorithm {
    SHA_256("SCRAM-SHA-256", "HmacSHA256", "PBKDF2WithHmacSHA256", "SHA-256", 32),
    SHA_512("SCRAM-SHA-512", "HmacSHA512", "PBKDF2WithHmacSHA512", "SHA-512", 64);

    private final String mqttAlgorithmName;
    private final String hmacAlgorithm;
    private final String pbkdf2Algorithm;
    private final String hashAlgorithm;
    private final int hashByteSize;

    ScramAlgorithm(String mqttAlgorithmName, String hmacAlgorithm, String pbkdf2Algorithm, String hashAlgorithm, int hashByteSize) {
        this.mqttAlgorithmName = mqttAlgorithmName;
        this.hmacAlgorithm = hmacAlgorithm;
        this.pbkdf2Algorithm = pbkdf2Algorithm;
        this.hashAlgorithm = hashAlgorithm;
        this.hashByteSize = hashByteSize;
    }

    public static Optional<ScramAlgorithm> fromMqttName(String mqttAlgorithmName) {
        if (mqttAlgorithmName == null) {
            return Optional.empty();
        }
        ScramAlgorithm algorithm = null;
        for (var scramAlgorithm : ScramAlgorithm.values()) {
            if (scramAlgorithm.getMqttAlgorithmName().equals(mqttAlgorithmName)) {
                algorithm = scramAlgorithm;
                break;
            }
        }
        return Optional.ofNullable(algorithm);
    }

}
