/**
 * Copyright © 2016-2024 The Thingsboard Authors
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

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaConsumerTemplate;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaProducerTemplate;
import org.thingsboard.mqtt.broker.queue.kafka.settings.ClientSubscriptionsKafkaSettings;
import org.thingsboard.mqtt.broker.queue.kafka.settings.TbKafkaConsumerSettings;
import org.thingsboard.mqtt.broker.queue.kafka.settings.TbKafkaProducerSettings;
import org.thingsboard.mqtt.broker.queue.kafka.stats.TbKafkaConsumerStatsService;
import org.thingsboard.mqtt.broker.queue.stats.ConsumerStatsManager;
import org.thingsboard.mqtt.broker.queue.stats.ProducerStatsManager;
import org.thingsboard.mqtt.broker.queue.util.QueueUtil;

import java.util.Map;
import java.util.Properties;

import static org.thingsboard.mqtt.broker.queue.constants.QueueConstants.CLEANUP_POLICY_PROPERTY;
import static org.thingsboard.mqtt.broker.queue.constants.QueueConstants.COMPACT_POLICY;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaClientSubscriptionsQueueFactory extends AbstractQueueFactory implements ClientSubscriptionsQueueFactory {

    private final Map<String, String> requiredConsumerProperties = Map.of("auto.offset.reset", "earliest");

    private final TbKafkaConsumerSettings consumerSettings;
    private final TbKafkaProducerSettings producerSettings;
    private final ClientSubscriptionsKafkaSettings clientSubscriptionsSettings;
    private final TbQueueAdmin queueAdmin;
    private final TbKafkaConsumerStatsService consumerStatsService;

    @Autowired(required = false)
    private ProducerStatsManager producerStatsManager;
    @Autowired(required = false)
    private ConsumerStatsManager consumerStatsManager;

    private Map<String, String> topicConfigs;

    @PostConstruct
    public void init() {
        this.topicConfigs = QueueUtil.getConfigs(clientSubscriptionsSettings.getTopicProperties());
        String configuredLogCleanupPolicy = topicConfigs.get(CLEANUP_POLICY_PROPERTY);
        if (configuredLogCleanupPolicy != null && !configuredLogCleanupPolicy.equals(COMPACT_POLICY)) {
            log.warn("Client subscriptions clean-up policy should be " + COMPACT_POLICY + ".");
        }
        topicConfigs.put(CLEANUP_POLICY_PROPERTY, COMPACT_POLICY);
    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<QueueProtos.ClientSubscriptionsProto>> createProducer() {
        TbKafkaProducerTemplate.TbKafkaProducerTemplateBuilder<TbProtoQueueMsg<QueueProtos.ClientSubscriptionsProto>> producerBuilder = TbKafkaProducerTemplate.builder();
        producerBuilder.properties(producerSettings.toProps(clientSubscriptionsSettings.getAdditionalProducerConfig()));
        producerBuilder.clientId(kafkaPrefix + "client-subscriptions-producer");
        producerBuilder.defaultTopic(clientSubscriptionsSettings.getKafkaTopic());
        producerBuilder.topicConfigs(topicConfigs);
        producerBuilder.admin(queueAdmin);
        producerBuilder.statsManager(producerStatsManager);
        return producerBuilder.build();
    }

    @Override
    public TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.ClientSubscriptionsProto>> createConsumer(String consumerId, String groupId) {
        TbKafkaConsumerTemplate.TbKafkaConsumerTemplateBuilder<TbProtoQueueMsg<QueueProtos.ClientSubscriptionsProto>> consumerBuilder = TbKafkaConsumerTemplate.builder();

        Properties props = consumerSettings.toProps(clientSubscriptionsSettings.getKafkaTopic(), clientSubscriptionsSettings.getAdditionalConsumerConfig());
        QueueUtil.overrideProperties("ClientSubscriptionsQueue-" + consumerId, props, requiredConsumerProperties);
        consumerBuilder.properties(props);

        consumerBuilder.topic(clientSubscriptionsSettings.getKafkaTopic());
        consumerBuilder.topicConfigs(topicConfigs);
        consumerBuilder.clientId(kafkaPrefix + "client-subscriptions-consumer-" + consumerId);
        consumerBuilder.groupId(kafkaPrefix + BrokerConstants.CLIENT_SUBSCRIPTIONS_CG_PREFIX + groupId);
        consumerBuilder.decoder(msg -> new TbProtoQueueMsg<>(msg.getKey(), QueueProtos.ClientSubscriptionsProto.parseFrom(msg.getData()), msg.getHeaders(),
                msg.getPartition(), msg.getOffset()));
        consumerBuilder.admin(queueAdmin);
        consumerBuilder.statsService(consumerStatsService);
        consumerBuilder.statsManager(consumerStatsManager);
        return consumerBuilder.build();
    }
}
