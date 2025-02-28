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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
@AllArgsConstructor
public class KafkaIntegrationConfig {

    private String bootstrapServers;
    private String topic;
    private String key;
    private String clientIdPrefix;
    private int retries;
    private int batchSize;
    private int linger;
    private int bufferMemory;
    private String acks;
    private String compression;
    private String keySerializer;
    private String valueSerializer;
    private Map<String, String> otherProperties;
    private Map<String, String> kafkaHeaders;
    private String kafkaHeadersCharset;

    public String getClientIdPrefix() {
        return StringUtils.isEmpty(clientIdPrefix) ? "tbmq-ie-kafka-producer" : clientIdPrefix;
    }

    public int getBatchSize() {
        return batchSize == 0 ? 16_384 : batchSize;
    }

    public int getBufferMemory() {
        return bufferMemory == 0 ? 33_554_432 : bufferMemory;
    }

    public String getKeySerializer() {
        return StringUtils.isEmpty(keySerializer) ? StringSerializer.class.getName() : keySerializer;
    }

    public String getValueSerializer() {
        return StringUtils.isEmpty(valueSerializer) ? StringSerializer.class.getName() : valueSerializer;
    }

    public Map<String, String> getOtherProperties() {
        return CollectionUtils.isEmpty(otherProperties) ? Map.of() : otherProperties;
    }

    public Map<String, String> getKafkaHeaders() {
        return CollectionUtils.isEmpty(kafkaHeaders) ? Map.of() : kafkaHeaders;
    }

    public Charset getKafkaHeadersCharset() {
        return StringUtils.isEmpty(kafkaHeadersCharset) ? StandardCharsets.UTF_8 : Charset.forName(kafkaHeadersCharset);
    }
}
