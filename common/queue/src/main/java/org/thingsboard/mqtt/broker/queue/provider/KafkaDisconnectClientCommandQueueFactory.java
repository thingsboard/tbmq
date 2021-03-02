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

@Slf4j
@Component
public class KafkaDisconnectClientCommandQueueFactory implements DisconnectClientCommandQueueFactory {

    private final TbKafkaSettings kafkaSettings;
    private final TbQueueAdmin queueAdmin;
    private final Map<String, String> topicConfigs;
    private final TbKafkaConsumerStatsService consumerStatsService;

    public KafkaDisconnectClientCommandQueueFactory(@Qualifier("disconnect-client-command") TbKafkaSettings kafkaSettings,
                                                    TbKafkaTopicConfigs kafkaTopicConfigs,
                                                    TbQueueAdmin queueAdmin,
                                                    TbKafkaConsumerStatsService consumerStatsService) {
        this.kafkaSettings = kafkaSettings;
        this.topicConfigs = kafkaTopicConfigs.getDisconnectClientCommandConfigs();
        this.consumerStatsService = consumerStatsService;
        this.queueAdmin = queueAdmin;
    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<QueueProtos.DisconnectClientCommandProto>> createProducer() {
        TbKafkaProducerTemplate.TbKafkaProducerTemplateBuilder<TbProtoQueueMsg<QueueProtos.DisconnectClientCommandProto>> producerBuilder = TbKafkaProducerTemplate.builder();
        producerBuilder.settings(kafkaSettings);
        producerBuilder.clientId("disconnect-client-command-producer");
        producerBuilder.topic(kafkaSettings.getTopic());
        producerBuilder.topicConfigs(topicConfigs);
        producerBuilder.admin(queueAdmin);
        return producerBuilder.build();
    }

    @Override
    public TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.DisconnectClientCommandProto>> createConsumer() {
        TbKafkaConsumerTemplate.TbKafkaConsumerTemplateBuilder<TbProtoQueueMsg<QueueProtos.DisconnectClientCommandProto>> consumerBuilder = TbKafkaConsumerTemplate.builder();
        consumerBuilder.settings(kafkaSettings);
        consumerBuilder.topic(kafkaSettings.getTopic());
        consumerBuilder.topicConfigs(topicConfigs);
        consumerBuilder.clientId("disconnect-client-command-consumer");
        consumerBuilder.groupId("disconnect-client-command-consumer-group");
        consumerBuilder.decoder(msg -> new TbProtoQueueMsg<>(msg.getKey(), QueueProtos.DisconnectClientCommandProto.parseFrom(msg.getData()), msg.getHeaders()));
        consumerBuilder.admin(queueAdmin);
        consumerBuilder.statsService(consumerStatsService);
        return consumerBuilder.build();
    }
}
