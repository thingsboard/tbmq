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
package org.thingsboard.mqtt.broker.queue.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.queue.TbQueueConsumer;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.constants.QueueConstants;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaConsumerTemplate;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaProducerTemplate;
import org.thingsboard.mqtt.broker.queue.kafka.settings.HistoricalDataTotalKafkaSettings;
import org.thingsboard.mqtt.broker.queue.kafka.settings.TbKafkaConsumerSettings;
import org.thingsboard.mqtt.broker.queue.kafka.settings.TbKafkaProducerSettings;
import org.thingsboard.mqtt.broker.queue.kafka.stats.TbKafkaConsumerStatsService;
import org.thingsboard.mqtt.broker.queue.stats.ConsumerStatsManager;
import org.thingsboard.mqtt.broker.queue.stats.ProducerStatsManager;
import org.thingsboard.mqtt.broker.queue.util.QueueUtil;

import javax.annotation.PostConstruct;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaHistoricalDataQueueFactory implements HistoricalDataQueueFactory {

    private final TbKafkaConsumerSettings consumerSettings;
    private final TbKafkaProducerSettings producerSettings;
    private final HistoricalDataTotalKafkaSettings historicalDataTotalKafkaSettings;
    private final TbQueueAdmin queueAdmin;
    private final TbKafkaConsumerStatsService consumerStatsService;

    @Autowired(required = false)
    private ProducerStatsManager producerStatsManager;
    @Autowired(required = false)
    private ConsumerStatsManager consumerStatsManager;

    private Map<String, String> topicConfigs;

    @PostConstruct
    public void init() {
        this.topicConfigs = QueueUtil.getConfigs(historicalDataTotalKafkaSettings.getTopicProperties());
        String configuredPartitions = topicConfigs.get(QueueConstants.PARTITIONS);
        if (configuredPartitions != null && Integer.parseInt(configuredPartitions) != 1) {
            log.warn("Historical data topic must have only 1 partition.");
        }
        topicConfigs.put(QueueConstants.PARTITIONS, "1");
    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<QueueProtos.ToUsageStatsMsgProto>> createProducer(String serviceId) {
        TbKafkaProducerTemplate.TbKafkaProducerTemplateBuilder<TbProtoQueueMsg<QueueProtos.ToUsageStatsMsgProto>> producerBuilder = TbKafkaProducerTemplate.builder();
        producerBuilder.properties(producerSettings.toProps(historicalDataTotalKafkaSettings.getAdditionalProducerConfig()));
        producerBuilder.clientId("historical-data-total-producer-" + serviceId);
        producerBuilder.topicConfigs(topicConfigs);
        producerBuilder.admin(queueAdmin);
        producerBuilder.statsManager(producerStatsManager);
        return producerBuilder.build();
    }

    @Override
    public TbQueueConsumer<TbProtoQueueMsg<QueueProtos.ToUsageStatsMsgProto>> createConsumer(String topic, String serviceId) {
        TbKafkaConsumerTemplate.TbKafkaConsumerTemplateBuilder<TbProtoQueueMsg<QueueProtos.ToUsageStatsMsgProto>> consumerBuilder = TbKafkaConsumerTemplate.builder();
        consumerBuilder.properties(consumerSettings.toProps(topic, historicalDataTotalKafkaSettings.getAdditionalConsumerConfig()));
        consumerBuilder.topic(topic);
        consumerBuilder.topicConfigs(topicConfigs);
        consumerBuilder.clientId("historical-data-total-" + serviceId + "-consumer");
        consumerBuilder.groupId("historical-data-total-consumer-group");
        consumerBuilder.decoder(msg -> new TbProtoQueueMsg<>(msg.getKey(), QueueProtos.ToUsageStatsMsgProto.parseFrom(msg.getData()), msg.getHeaders()));
        consumerBuilder.admin(queueAdmin);
        consumerBuilder.statsService(consumerStatsService);
        consumerBuilder.statsManager(consumerStatsManager);
        return consumerBuilder.build();
    }
}
