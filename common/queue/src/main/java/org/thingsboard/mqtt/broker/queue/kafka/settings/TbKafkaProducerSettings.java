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
package org.thingsboard.mqtt.broker.queue.kafka.settings;

import lombok.Setter;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.queue.util.QueueUtil;

import java.util.Properties;

@Setter
@Component
public class TbKafkaProducerSettings {

    @Value("${queue.kafka.bootstrap.servers}")
    private String servers;

    @Value("${queue.kafka.default.producer.acks}")
    private String acks;

    @Value("${queue.kafka.default.producer.retries}")
    private int retries;

    @Value("${queue.kafka.default.producer.batch-size}")
    private int batchSize;

    @Value("${queue.kafka.default.producer.linger-ms}")
    private long lingerMs;

    @Value("${queue.kafka.default.producer.buffer-memory}")
    private long bufferMemory;

    @Value("${queue.kafka.default.producer.compression-type:none}")
    private String compressionType;

    public Properties toProps(String customProperties) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, servers);
        props.put(ProducerConfig.RETRIES_CONFIG, retries);
        props.put(ProducerConfig.ACKS_CONFIG, acks);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, batchSize);
        props.put(ProducerConfig.LINGER_MS_CONFIG, lingerMs);
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, bufferMemory);
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, compressionType);
        if (customProperties != null) {
            props.putAll(QueueUtil.getConfigs(customProperties));
        }
        return props;
    }
}
