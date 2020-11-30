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

import org.apache.kafka.clients.producer.ProducerConfig;

import java.util.List;
import java.util.Properties;

public interface TbKafkaSettings {

    String getServers();

    String getTopic();

    String getAcks();

    int getRetries();

    int getBatchSize();

    long getLingerMs();

    long getBufferMemory();

    short getReplicationFactor();

    int getMaxPollRecords();

    int getMaxPollIntervalMs();

    int getMaxPartitionFetchBytes();

    int getFetchMaxBytes();

    List<TbKafkaProperty> getOther();

    default Properties toProps() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, getServers());
        props.put(ProducerConfig.RETRIES_CONFIG, getRetries());
        props.put(ProducerConfig.ACKS_CONFIG, getAcks());
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, getBatchSize());
        props.put(ProducerConfig.LINGER_MS_CONFIG, getLingerMs());
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, getBufferMemory());

        if (getOther() != null) {
            getOther().forEach(kv -> props.put(kv.getKey(), kv.getValue()));
        }
        return props;
    }

}
