/**
 * Copyright © 2016-2020 The Thingsboard Authors
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

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Properties;

@Slf4j
@ConfigurationProperties(prefix = "queue.kafka.client-session")
@Component
public class ClientSessionKafkaSettings implements TbKafkaSettings {

    @Getter
    @Value("${queue.kafka.bootstrap.servers}")
    private String servers;

    @Getter
    private String topic;

    @Getter
    private String acks;

    @Getter
    private int retries;

    @Getter
    private int batchSize;

    @Getter
    private long lingerMs;

    @Getter
    private long bufferMemory;

    @Getter
    private short replicationFactor;

    @Getter
    private int maxPollRecords;

    @Getter
    private int maxPollIntervalMs;

    @Getter
    private int maxPartitionFetchBytes;

    @Getter
    private int fetchMaxBytes;

    @Getter
    @Setter
    private List<TbKafkaProperty> other;

    public Properties toProps() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, servers);
        props.put(ProducerConfig.RETRIES_CONFIG, retries);
        props.put(ProducerConfig.ACKS_CONFIG, acks);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, batchSize);
        props.put(ProducerConfig.LINGER_MS_CONFIG, lingerMs);
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, bufferMemory);

        if (other != null) {
            other.forEach(kv -> props.put(kv.getKey(), kv.getValue()));
        }
        return props;
    }

}
