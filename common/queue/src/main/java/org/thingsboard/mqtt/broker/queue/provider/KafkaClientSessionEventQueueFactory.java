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
public class KafkaClientSessionEventQueueFactory implements ClientSessionEventQueueFactory {

    private final TbKafkaSettings eventSettings;
    private final TbKafkaSettings eventResponseSettings;
    private final TbQueueAdmin queueAdmin;
    private final Map<String, String> clientSessionEventConfigs;
    private final Map<String, String> clientSessionEventResponseConfigs;
    private final TbKafkaConsumerStatsService consumerStatsService;

    public KafkaClientSessionEventQueueFactory(@Qualifier("client-session-event") TbKafkaSettings eventSettings,
                                               @Qualifier("client-session-event-response") TbKafkaSettings eventResponseSettings,
                                               TbKafkaTopicConfigs kafkaTopicConfigs,
                                               TbQueueAdmin queueAdmin,
                                               TbKafkaConsumerStatsService consumerStatsService) {
        this.eventSettings = eventSettings;
        this.eventResponseSettings = eventResponseSettings;

        this.clientSessionEventConfigs = kafkaTopicConfigs.getClientSessionEventConfigs();
        this.clientSessionEventResponseConfigs = kafkaTopicConfigs.getClientSessionEventResponseConfigs();

        this.consumerStatsService = consumerStatsService;

        this.queueAdmin = queueAdmin;
    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<QueueProtos.ClientSessionEventProto>> createEventProducer() {
        TbKafkaProducerTemplate.TbKafkaProducerTemplateBuilder<TbProtoQueueMsg<QueueProtos.ClientSessionEventProto>> producerBuilder = TbKafkaProducerTemplate.builder();
        producerBuilder.settings(eventSettings);
        producerBuilder.clientId("client-session-event-producer");
        producerBuilder.topic(eventSettings.getTopic());
        producerBuilder.topicConfigs(clientSessionEventConfigs);
        producerBuilder.admin(queueAdmin);
        return producerBuilder.build();
    }

    @Override
    public TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.ClientSessionEventProto>> createEventConsumer(String consumerName) {
        TbKafkaConsumerTemplate.TbKafkaConsumerTemplateBuilder<TbProtoQueueMsg<QueueProtos.ClientSessionEventProto>> consumerBuilder = TbKafkaConsumerTemplate.builder();
        consumerBuilder.settings(eventSettings);
        consumerBuilder.topic(eventSettings.getTopic());
        consumerBuilder.topicConfigs(clientSessionEventConfigs);
        consumerBuilder.clientId("client-session-event-consumer-" + consumerName);
        consumerBuilder.groupId("client-session-event-consumer-group");
        consumerBuilder.decoder(msg -> new TbProtoQueueMsg<>(msg.getKey(), QueueProtos.ClientSessionEventProto.parseFrom(msg.getData()), msg.getHeaders(),
                msg.getPartition(), msg.getOffset()));
        consumerBuilder.admin(queueAdmin);
        consumerBuilder.statsService(consumerStatsService);
        return consumerBuilder.build();
    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<QueueProtos.ClientSessionEventResponseProto>> createEventResponseProducer() {
        TbKafkaProducerTemplate.TbKafkaProducerTemplateBuilder<TbProtoQueueMsg<QueueProtos.ClientSessionEventResponseProto>> producerBuilder = TbKafkaProducerTemplate.builder();
        producerBuilder.settings(eventResponseSettings);
        producerBuilder.clientId("client-session-event-response-producer");
        producerBuilder.topic(eventResponseSettings.getTopic());
        producerBuilder.topicConfigs(clientSessionEventResponseConfigs);
        producerBuilder.admin(queueAdmin);
        return producerBuilder.build();
    }

    @Override
    public TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.ClientSessionEventResponseProto>> createEventResponseConsumer() {
        TbKafkaConsumerTemplate.TbKafkaConsumerTemplateBuilder<TbProtoQueueMsg<QueueProtos.ClientSessionEventResponseProto>> consumerBuilder = TbKafkaConsumerTemplate.builder();
        consumerBuilder.settings(eventResponseSettings);
        consumerBuilder.topic(eventResponseSettings.getTopic());
        consumerBuilder.topicConfigs(clientSessionEventResponseConfigs);
        consumerBuilder.clientId("client-session-event-response-consumer");
        consumerBuilder.groupId("client-session-event-response-consumer-group");
        consumerBuilder.decoder(msg -> new TbProtoQueueMsg<>(msg.getKey(), QueueProtos.ClientSessionEventResponseProto.parseFrom(msg.getData()), msg.getHeaders()));
        consumerBuilder.admin(queueAdmin);
        consumerBuilder.statsService(consumerStatsService);
        return consumerBuilder.build();
    }
}
