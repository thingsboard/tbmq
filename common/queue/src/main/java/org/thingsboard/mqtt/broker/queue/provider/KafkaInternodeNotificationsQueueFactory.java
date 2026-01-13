/**
 * Copyright © 2016-2026 The Thingsboard Authors
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
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.gen.queue.InternodeNotificationProto;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaConsumerTemplate;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaProducerTemplate;
import org.thingsboard.mqtt.broker.queue.kafka.settings.InternodeNotificationsKafkaSettings;
import org.thingsboard.mqtt.broker.queue.util.QueueUtil;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaInternodeNotificationsQueueFactory extends AbstractQueueFactory implements InternodeNotificationsQueueFactory {

    private final InternodeNotificationsKafkaSettings internodeNotificationsKafkaSettings;

    private Map<String, String> topicConfigs;

    @PostConstruct
    public void init() {
        this.topicConfigs = QueueUtil.getConfigs(internodeNotificationsKafkaSettings.getTopicProperties());
    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<InternodeNotificationProto>> createProducer(String serviceId) {
        TbKafkaProducerTemplate.TbKafkaProducerTemplateBuilder<TbProtoQueueMsg<InternodeNotificationProto>> producerBuilder = TbKafkaProducerTemplate.builder();
        producerBuilder.properties(producerSettings.toProps(internodeNotificationsKafkaSettings.getAdditionalProducerConfig()));
        producerBuilder.clientId(kafkaPrefix + "internode-notifications-producer-" + serviceId);
        producerBuilder.topicConfigs(topicConfigs);
        producerBuilder.admin(queueAdmin);
        producerBuilder.statsManager(producerStatsManager);
        return producerBuilder.build();
    }

    @Override
    public TbQueueControlledOffsetConsumer<TbProtoQueueMsg<InternodeNotificationProto>> createConsumer(String topic, String serviceId) {
        TbKafkaConsumerTemplate.TbKafkaConsumerTemplateBuilder<TbProtoQueueMsg<InternodeNotificationProto>> consumerBuilder = TbKafkaConsumerTemplate.builder();
        consumerBuilder.properties(consumerSettings.toProps(topic, internodeNotificationsKafkaSettings.getAdditionalConsumerConfig()));
        consumerBuilder.topic(topic);
        consumerBuilder.topicConfigs(topicConfigs);
        consumerBuilder.clientId(kafkaPrefix + "internode-notifications-consumer-" + serviceId);
        consumerBuilder.groupId(kafkaPrefix + "internode-notifications-consumer-group-" + serviceId);
        consumerBuilder.decoder(msg -> new TbProtoQueueMsg<>(msg.getKey(), InternodeNotificationProto.parseFrom(msg.getData()), msg.getHeaders()));
        consumerBuilder.admin(queueAdmin);
        consumerBuilder.statsService(consumerStatsService);
        consumerBuilder.statsManager(consumerStatsManager);
        return consumerBuilder.build();
    }
}
