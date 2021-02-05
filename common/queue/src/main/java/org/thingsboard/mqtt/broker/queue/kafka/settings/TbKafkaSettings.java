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

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;

import java.util.List;
import java.util.Properties;

public interface TbKafkaSettings {

    String getServers();

    void setServers(String servers);

    String getTopic();

    String getAcks();

    int getRetries();

    int getBatchSize();

    long getLingerMs();

    long getBufferMemory();

    int getMaxPollRecords();

    int getMaxPollIntervalMs();

    int getMaxPartitionFetchBytes();

    int getFetchMaxBytes();

    List<TbKafkaProperty> getOther();

    default Properties toProducerProps() {
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

    default Properties toConsumerProps() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, getServers());
        if (getMaxPollIntervalMs() > 0) {
            props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, getMaxPollIntervalMs());
        }
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, getMaxPollRecords());
        props.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, getMaxPartitionFetchBytes());
        props.put(ConsumerConfig.FETCH_MAX_BYTES_CONFIG, getFetchMaxBytes());
        if (getOther() != null) {
            getOther().forEach(kv -> props.put(kv.getKey(), kv.getValue()));
        }
        return props;
    }

}
