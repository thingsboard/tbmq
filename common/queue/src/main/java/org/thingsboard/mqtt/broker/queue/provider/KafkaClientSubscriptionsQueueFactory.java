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
package org.thingsboard.mqtt.broker.queue.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaConsumerTemplate;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaProducerTemplate;
import org.thingsboard.mqtt.broker.queue.kafka.settings.TbKafkaSettings;
import org.thingsboard.mqtt.broker.queue.kafka.settings.TbKafkaTopicConfigs;
import org.thingsboard.mqtt.broker.queue.kafka.stats.TbKafkaConsumerStatsService;

import java.util.Map;

import static org.thingsboard.mqtt.broker.queue.constants.QueueConstants.CLEANUP_POLICY_PROPERTY;
import static org.thingsboard.mqtt.broker.queue.constants.QueueConstants.COMPACT_POLICY;

@Slf4j
@Component
public class KafkaClientSubscriptionsQueueFactory implements ClientSubscriptionsQueueFactory {
    private final TbKafkaSettings kafkaSettings;
    private final TbQueueAdmin queueAdmin;
    private final Map<String, String> clientSubscriptionsConfigs;
    private final TbKafkaConsumerStatsService consumerStatsService;

    public KafkaClientSubscriptionsQueueFactory(@Qualifier("client-subscriptions") TbKafkaSettings kafkaSettings,
                                                TbKafkaTopicConfigs kafkaTopicConfigs,
                                                TbQueueAdmin queueAdmin,
                                                TbKafkaConsumerStatsService consumerStatsService) {
        this.kafkaSettings = kafkaSettings;

        this.clientSubscriptionsConfigs = kafkaTopicConfigs.getClientSubscriptionsConfigs();
        this.consumerStatsService = consumerStatsService;
        String configuredLogCleanupPolicy = clientSubscriptionsConfigs.get(CLEANUP_POLICY_PROPERTY);
        if (configuredLogCleanupPolicy != null && !configuredLogCleanupPolicy.equals(COMPACT_POLICY)) {
            log.warn("Client subscriptions clean-up policy should be " + COMPACT_POLICY + ".");
        }
        clientSubscriptionsConfigs.put(CLEANUP_POLICY_PROPERTY, COMPACT_POLICY);

        this.queueAdmin = queueAdmin;
    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<QueueProtos.ClientSubscriptionsProto>> createProducer() {
        TbKafkaProducerTemplate.TbKafkaProducerTemplateBuilder<TbProtoQueueMsg<QueueProtos.ClientSubscriptionsProto>> producerBuilder = TbKafkaProducerTemplate.builder();
        producerBuilder.settings(kafkaSettings);
        producerBuilder.clientId("client-subscriptions-producer");
        producerBuilder.topic(kafkaSettings.getTopic());
        producerBuilder.topicConfigs(clientSubscriptionsConfigs);
        producerBuilder.admin(queueAdmin);
        return producerBuilder.build();
    }

    @Override
    public TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.ClientSubscriptionsProto>> createConsumer(String id) {
        TbKafkaConsumerTemplate.TbKafkaConsumerTemplateBuilder<TbProtoQueueMsg<QueueProtos.ClientSubscriptionsProto>> consumerBuilder = TbKafkaConsumerTemplate.builder();
        consumerBuilder.settings(kafkaSettings);
        consumerBuilder.topic(kafkaSettings.getTopic());
        consumerBuilder.topicConfigs(clientSubscriptionsConfigs);
        consumerBuilder.clientId("client-subscriptions-consumer-" + id);
        consumerBuilder.decoder(msg -> new TbProtoQueueMsg<>(msg.getKey(), QueueProtos.ClientSubscriptionsProto.parseFrom(msg.getData()), msg.getHeaders()));
        consumerBuilder.admin(queueAdmin);
        return consumerBuilder.build();
    }
}
