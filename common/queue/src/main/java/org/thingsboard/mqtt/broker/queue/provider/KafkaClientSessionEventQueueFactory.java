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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaConsumerTemplate;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaProducerTemplate;
import org.thingsboard.mqtt.broker.queue.kafka.settings.ClientSessionEventKafkaSettings;
import org.thingsboard.mqtt.broker.queue.kafka.settings.ClientSessionEventResponseKafkaSettings;
import org.thingsboard.mqtt.broker.queue.kafka.settings.TbKafkaConsumerSettings;
import org.thingsboard.mqtt.broker.queue.kafka.settings.TbKafkaProducerSettings;
import org.thingsboard.mqtt.broker.queue.stats.ProducerStatsManager;
import org.thingsboard.mqtt.broker.queue.kafka.stats.TbKafkaConsumerStatsService;

import static org.thingsboard.mqtt.broker.queue.util.ParseConfigUtil.getConfigs;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaClientSessionEventQueueFactory implements ClientSessionEventQueueFactory {

    private final TbKafkaConsumerSettings consumerSettings;
    private final TbKafkaProducerSettings producerSettings;
    private final ClientSessionEventKafkaSettings clientSessionEventSettings;
    private final ClientSessionEventResponseKafkaSettings clientSessionEventResponseSettings;
    private final TbQueueAdmin queueAdmin;
    private final TbKafkaConsumerStatsService consumerStatsService;

    @Autowired(required = false)
    private ProducerStatsManager producerStatsManager;

    @Override
    public TbQueueProducer<TbProtoQueueMsg<QueueProtos.ClientSessionEventProto>> createEventProducer(String serviceId) {
        TbKafkaProducerTemplate.TbKafkaProducerTemplateBuilder<TbProtoQueueMsg<QueueProtos.ClientSessionEventProto>> producerBuilder = TbKafkaProducerTemplate.builder();
        producerBuilder.properties(producerSettings.toProps(clientSessionEventSettings.getProducerProperties()));
        producerBuilder.clientId("client-session-event-producer-" + serviceId);
        producerBuilder.defaultTopic(clientSessionEventSettings.getTopic());
        producerBuilder.topicConfigs(getConfigs(clientSessionEventSettings.getTopicProperties()));
        producerBuilder.admin(queueAdmin);
        producerBuilder.statsManager(producerStatsManager);
        return producerBuilder.build();
    }

    @Override
    public TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.ClientSessionEventProto>> createEventConsumer(String consumerName) {
        TbKafkaConsumerTemplate.TbKafkaConsumerTemplateBuilder<TbProtoQueueMsg<QueueProtos.ClientSessionEventProto>> consumerBuilder = TbKafkaConsumerTemplate.builder();
        consumerBuilder.properties(consumerSettings.toProps(clientSessionEventSettings.getConsumerProperties()));
        consumerBuilder.topic(clientSessionEventSettings.getTopic());
        consumerBuilder.topicConfigs(getConfigs(clientSessionEventSettings.getTopicProperties()));
        consumerBuilder.clientId("client-session-event-consumer-" + consumerName);
        consumerBuilder.groupId("client-session-event-consumer-group");
        consumerBuilder.decoder(msg -> new TbProtoQueueMsg<>(msg.getKey(), QueueProtos.ClientSessionEventProto.parseFrom(msg.getData()), msg.getHeaders(),
                msg.getPartition(), msg.getOffset()));
        consumerBuilder.admin(queueAdmin);
        consumerBuilder.statsService(consumerStatsService);
        return consumerBuilder.build();
    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<QueueProtos.ClientSessionEventResponseProto>> createEventResponseProducer(String serviceId) {
        TbKafkaProducerTemplate.TbKafkaProducerTemplateBuilder<TbProtoQueueMsg<QueueProtos.ClientSessionEventResponseProto>> producerBuilder = TbKafkaProducerTemplate.builder();
        producerBuilder.properties(producerSettings.toProps(clientSessionEventResponseSettings.getProducerProperties()));
        producerBuilder.clientId("client-session-event-response-" + serviceId);
        return producerBuilder.build();
    }

    @Override
    public TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.ClientSessionEventResponseProto>> createEventResponseConsumer(String serviceId) {
        TbKafkaConsumerTemplate.TbKafkaConsumerTemplateBuilder<TbProtoQueueMsg<QueueProtos.ClientSessionEventResponseProto>> consumerBuilder = TbKafkaConsumerTemplate.builder();
        consumerBuilder.properties(consumerSettings.toProps(clientSessionEventResponseSettings.getConsumerProperties()));
        consumerBuilder.topic(clientSessionEventResponseSettings.getTopicPrefix() + "." + serviceId);
        consumerBuilder.topicConfigs(getConfigs(clientSessionEventResponseSettings.getTopicProperties()));
        consumerBuilder.clientId("client-session-event-response-consumer-" + serviceId);
        consumerBuilder.groupId("client-session-event-response-consumer-group-" + serviceId);
        consumerBuilder.decoder(msg -> new TbProtoQueueMsg<>(msg.getKey(), QueueProtos.ClientSessionEventResponseProto.parseFrom(msg.getData()), msg.getHeaders()));
        consumerBuilder.admin(queueAdmin);
        consumerBuilder.statsService(consumerStatsService);
        return consumerBuilder.build();
    }
}
