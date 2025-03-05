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
package org.thingsboard.mqtt.broker.queue.provider.integration;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.gen.integration.UplinkIntegrationMsgProto;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaConsumerTemplate;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaProducerTemplate;
import org.thingsboard.mqtt.broker.queue.kafka.settings.integration.IntegrationUplinkKafkaSettings;
import org.thingsboard.mqtt.broker.queue.provider.AbstractQueueFactory;
import org.thingsboard.mqtt.broker.queue.util.QueueUtil;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaIntegrationUplinkQueueFactory extends AbstractQueueFactory implements IntegrationUplinkQueueFactory {

    private final IntegrationUplinkKafkaSettings integrationUplinkKafkaSettings;

    private Map<String, String> topicConfigs;

    @PostConstruct
    public void init() {
        this.topicConfigs = QueueUtil.getConfigs(integrationUplinkKafkaSettings.getTopicProperties());
    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<UplinkIntegrationMsgProto>> createProducer(String serviceId) {
        TbKafkaProducerTemplate.TbKafkaProducerTemplateBuilder<TbProtoQueueMsg<UplinkIntegrationMsgProto>> producerBuilder = TbKafkaProducerTemplate.builder();
        producerBuilder.properties(producerSettings.toProps(integrationUplinkKafkaSettings.getAdditionalProducerConfig()));
        producerBuilder.defaultTopic(integrationUplinkKafkaSettings.getKafkaTopic());
        producerBuilder.clientId(kafkaPrefix + "ie-uplink-producer-" + serviceId);
        producerBuilder.admin(queueAdmin);
        producerBuilder.topicConfigs(topicConfigs);
        producerBuilder.statsManager(producerStatsManager);
        return producerBuilder.build();
    }

    @Override
    public TbQueueControlledOffsetConsumer<TbProtoQueueMsg<UplinkIntegrationMsgProto>> createConsumer(String consumerId) {
        TbKafkaConsumerTemplate.TbKafkaConsumerTemplateBuilder<TbProtoQueueMsg<UplinkIntegrationMsgProto>> consumerBuilder = TbKafkaConsumerTemplate.builder();

        consumerBuilder.properties(consumerSettings.toProps(integrationUplinkKafkaSettings.getKafkaTopic(), integrationUplinkKafkaSettings.getAdditionalConsumerConfig()));
        consumerBuilder.decoder(msg -> new TbProtoQueueMsg<>(msg.getKey(), UplinkIntegrationMsgProto.parseFrom(msg.getData()),
                msg.getHeaders(), msg.getPartition(), msg.getOffset()));
        consumerBuilder.clientId(kafkaPrefix + "ie-uplink-consumer-" + consumerId);
        consumerBuilder.groupId(kafkaPrefix + "ie-uplink-consumer-group");
        consumerBuilder.topic(integrationUplinkKafkaSettings.getKafkaTopic());
        consumerBuilder.admin(queueAdmin);
        consumerBuilder.statsService(consumerStatsService);
        consumerBuilder.topicConfigs(topicConfigs);
        consumerBuilder.statsManager(consumerStatsManager);
        return consumerBuilder.build();
    }

}
