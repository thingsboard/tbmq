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

import lombok.Data;
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
@ConfigurationProperties(prefix = "queue.kafka.publish-msg")
@Component("publish-msg")
@Data
public class PublishMsgKafkaSettings implements TbKafkaSettings {

    @Value("${queue.kafka.bootstrap.servers}")
    private String servers;

    private String topic;

    private String acks;

    private int retries;

    private int batchSize;

    private long lingerMs;

    private long bufferMemory;

    private short replicationFactor;

    private int maxPollRecords;

    private int maxPollIntervalMs;

    private int maxPartitionFetchBytes;

    private int fetchMaxBytes;

    private List<TbKafkaProperty> other;
}
