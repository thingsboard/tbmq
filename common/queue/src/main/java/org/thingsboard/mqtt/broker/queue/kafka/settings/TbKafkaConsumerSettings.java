/**
 * Copyright © 2016-2023 The Thingsboard Authors
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
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.queue.util.QueueUtil;

import java.util.Properties;

@Setter
@Component
public class TbKafkaConsumerSettings {
    @Value("${queue.kafka.bootstrap.servers}")
    private String servers;

    @Value("${queue.kafka.default.consumer.max-poll-records}")
    private int maxPollRecords;

    @Value("${queue.kafka.default.consumer.partition-assignment-strategy}")
    private String partitionAssignmentStrategy;

    @Value("${queue.kafka.default.consumer.session-timeout-ms}")
    private int sessionTimeoutMs;

    @Value("${queue.kafka.default.consumer.max-poll-interval-ms}")
    private int maxPollIntervalMs;

    @Value("${queue.kafka.default.consumer.max-partition-fetch-bytes}")
    private int maxPartitionFetchBytes;

    @Value("${queue.kafka.default.consumer.fetch-max-bytes}")
    private int fetchMaxBytes;


    public Properties toProps(String customProperties) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, servers);
        props.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, partitionAssignmentStrategy);
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, sessionTimeoutMs);
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, maxPollIntervalMs);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);
        props.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, maxPartitionFetchBytes);
        props.put(ConsumerConfig.FETCH_MAX_BYTES_CONFIG, fetchMaxBytes);
        if (customProperties != null) {
            QueueUtil.getConfigs(customProperties).forEach(props::put);
        }
        return props;
    }

}
