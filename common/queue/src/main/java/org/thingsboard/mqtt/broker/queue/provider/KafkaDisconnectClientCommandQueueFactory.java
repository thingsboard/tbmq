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
import org.thingsboard.mqtt.broker.gen.queue.DisconnectClientCommandProto;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaConsumerTemplate;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaProducerTemplate;
import org.thingsboard.mqtt.broker.queue.kafka.settings.DisconnectClientCommandKafkaSettings;
import org.thingsboard.mqtt.broker.queue.util.QueueUtil;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaDisconnectClientCommandQueueFactory extends AbstractQueueFactory implements DisconnectClientCommandQueueFactory {

    private final DisconnectClientCommandKafkaSettings disconnectClientCommandSettings;

    private Map<String, String> topicConfigs;

    @PostConstruct
    public void init() {
        this.topicConfigs = QueueUtil.getConfigs(disconnectClientCommandSettings.getTopicProperties());
    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<DisconnectClientCommandProto>> createProducer(String serviceId) {
        TbKafkaProducerTemplate.TbKafkaProducerTemplateBuilder<TbProtoQueueMsg<DisconnectClientCommandProto>> producerBuilder = TbKafkaProducerTemplate.builder();
        producerBuilder.properties(producerSettings.toProps(disconnectClientCommandSettings.getAdditionalProducerConfig()));
        producerBuilder.clientId(kafkaPrefix + "disconnect-client-command-producer-" + serviceId);
        producerBuilder.topicConfigs(topicConfigs);
        producerBuilder.admin(queueAdmin);
        producerBuilder.statsManager(producerStatsManager);
        return producerBuilder.build();
    }

    @Override
    public TbQueueControlledOffsetConsumer<TbProtoQueueMsg<DisconnectClientCommandProto>> createConsumer(String topic, String serviceId) {
        TbKafkaConsumerTemplate.TbKafkaConsumerTemplateBuilder<TbProtoQueueMsg<DisconnectClientCommandProto>> consumerBuilder = TbKafkaConsumerTemplate.builder();
        consumerBuilder.properties(consumerSettings.toProps(topic, disconnectClientCommandSettings.getAdditionalConsumerConfig()));
        consumerBuilder.topic(topic);
        consumerBuilder.topicConfigs(topicConfigs);
        consumerBuilder.clientId(kafkaPrefix + "disconnect-client-command-consumer-" + serviceId);
        consumerBuilder.groupId(kafkaPrefix + "disconnect-client-command-consumer-group-" + serviceId);
        consumerBuilder.decoder(msg -> new TbProtoQueueMsg<>(msg.getKey(), DisconnectClientCommandProto.parseFrom(msg.getData()), msg.getHeaders()));
        consumerBuilder.admin(queueAdmin);
        consumerBuilder.statsService(consumerStatsService);
        consumerBuilder.statsManager(consumerStatsManager);
        return consumerBuilder.build();
    }
}
