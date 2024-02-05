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
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaConsumerTemplate;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaProducerTemplate;
import org.thingsboard.mqtt.broker.queue.kafka.settings.ApplicationRemovedEventKafkaSettings;
import org.thingsboard.mqtt.broker.queue.kafka.settings.TbKafkaConsumerSettings;
import org.thingsboard.mqtt.broker.queue.kafka.settings.TbKafkaProducerSettings;
import org.thingsboard.mqtt.broker.queue.kafka.stats.TbKafkaConsumerStatsService;
import org.thingsboard.mqtt.broker.queue.util.QueueUtil;

import java.util.Map;
import java.util.Properties;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaApplicationRemovedEventQueueFactory implements ApplicationRemovedEventQueueFactory {

    private final Map<String, String> requiredConsumerProperties = Map.of("auto.offset.reset", "earliest");

    private final TbKafkaConsumerSettings consumerSettings;
    private final TbKafkaProducerSettings producerSettings;
    private final ApplicationRemovedEventKafkaSettings kafkaSettings;
    private final TbQueueAdmin queueAdmin;
    private final TbKafkaConsumerStatsService consumerStatsService;

    @Override
    public TbQueueProducer<TbProtoQueueMsg<QueueProtos.ApplicationRemovedEventProto>> createEventProducer(String serviceId) {
        TbKafkaProducerTemplate.TbKafkaProducerTemplateBuilder<TbProtoQueueMsg<QueueProtos.ApplicationRemovedEventProto>> producerBuilder = TbKafkaProducerTemplate.builder();
        producerBuilder.properties(producerSettings.toProps(kafkaSettings.getAdditionalProducerConfig()));
        producerBuilder.clientId("application-removed-event-producer-" + serviceId);
        producerBuilder.defaultTopic(kafkaSettings.getTopic());
        producerBuilder.topicConfigs(QueueUtil.getConfigs(kafkaSettings.getTopicProperties()));
        producerBuilder.admin(queueAdmin);
        return producerBuilder.build();
    }

    @Override
    public TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.ApplicationRemovedEventProto>> createEventConsumer(String serviceId) {
        TbKafkaConsumerTemplate.TbKafkaConsumerTemplateBuilder<TbProtoQueueMsg<QueueProtos.ApplicationRemovedEventProto>> consumerBuilder = TbKafkaConsumerTemplate.builder();
        Properties props = consumerSettings.toProps(kafkaSettings.getTopic(), kafkaSettings.getAdditionalConsumerConfig());
        QueueUtil.overrideProperties("ApplicationRemovedEventQueue-" + serviceId, props, requiredConsumerProperties);
        consumerBuilder.properties(props);
        consumerBuilder.topic(kafkaSettings.getTopic());
        consumerBuilder.topicConfigs(QueueUtil.getConfigs(kafkaSettings.getTopicProperties()));
        consumerBuilder.clientId("application-removed-event-consumer-" + serviceId);
        consumerBuilder.groupId("application-removed-event-consumer-group");
        consumerBuilder.decoder(msg -> new TbProtoQueueMsg<>(msg.getKey(), QueueProtos.ApplicationRemovedEventProto.parseFrom(msg.getData()), msg.getHeaders(),
                msg.getPartition(), msg.getOffset()));
        consumerBuilder.admin(queueAdmin);
        consumerBuilder.statsService(consumerStatsService);
        return consumerBuilder.build();
    }
}
