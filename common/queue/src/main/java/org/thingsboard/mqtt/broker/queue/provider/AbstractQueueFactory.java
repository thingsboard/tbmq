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
package org.thingsboard.mqtt.broker.queue.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.queue.constants.QueueConstants;
import org.thingsboard.mqtt.broker.queue.kafka.settings.TbKafkaConsumerSettings;
import org.thingsboard.mqtt.broker.queue.kafka.settings.TbKafkaProducerSettings;
import org.thingsboard.mqtt.broker.queue.kafka.stats.TbKafkaConsumerStatsService;
import org.thingsboard.mqtt.broker.queue.stats.ConsumerStatsManager;
import org.thingsboard.mqtt.broker.queue.stats.ProducerStatsManager;
import org.thingsboard.mqtt.broker.queue.util.QueueUtil;

import java.util.Map;

import static org.thingsboard.mqtt.broker.queue.constants.QueueConstants.CLEANUP_POLICY_PROPERTY;
import static org.thingsboard.mqtt.broker.queue.constants.QueueConstants.COMPACT_POLICY;

@Slf4j
public abstract class AbstractQueueFactory {

    protected final Map<String, String> requiredConsumerProperties = Map.of("auto.offset.reset", "earliest");

    @Autowired
    protected TbKafkaConsumerSettings consumerSettings;
    @Autowired
    protected TbKafkaProducerSettings producerSettings;
    @Autowired
    protected TbQueueAdmin queueAdmin;
    @Autowired
    protected TbKafkaConsumerStatsService consumerStatsService;

    @Autowired(required = false)
    protected ProducerStatsManager producerStatsManager;
    @Autowired(required = false)
    protected ConsumerStatsManager consumerStatsManager;

    @Value("${queue.kafka.kafka-prefix:}")
    protected String kafkaPrefix;

    protected Map<String, String> validateAndConfigurePartitionsForTopic(String topicProperties, String topicName) {
        var topicConfigs = QueueUtil.getConfigs(topicProperties);
        String configuredPartitions = topicConfigs.get(QueueConstants.PARTITIONS);
        if (configuredPartitions != null && Integer.parseInt(configuredPartitions) != 1) {
            log.warn("{} topic must have only 1 partition!", topicName);
        }
        topicConfigs.put(QueueConstants.PARTITIONS, "1");
        return topicConfigs;
    }

    protected Map<String, String> validateAndConfigureCleanupPolicyForTopic(String topicProperties, String topicName) {
        var topicConfigs = QueueUtil.getConfigs(topicProperties);
        String configuredLogCleanupPolicy = topicConfigs.get(CLEANUP_POLICY_PROPERTY);
        if (configuredLogCleanupPolicy != null && !configuredLogCleanupPolicy.equals(COMPACT_POLICY)) {
            log.warn("{} clean-up policy should be compact!", topicName);
        }
        topicConfigs.put(CLEANUP_POLICY_PROPERTY, COMPACT_POLICY);
        return topicConfigs;
    }

}
