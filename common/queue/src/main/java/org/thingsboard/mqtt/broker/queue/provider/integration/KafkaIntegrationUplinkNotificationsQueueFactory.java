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
import org.thingsboard.mqtt.broker.gen.integration.UplinkIntegrationNotificationMsgProto;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaConsumerTemplate;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaProducerTemplate;
import org.thingsboard.mqtt.broker.queue.kafka.settings.integration.IntegrationUplinkNotificationsKafkaSettings;
import org.thingsboard.mqtt.broker.queue.provider.AbstractQueueFactory;
import org.thingsboard.mqtt.broker.queue.util.QueueUtil;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaIntegrationUplinkNotificationsQueueFactory extends AbstractQueueFactory implements IntegrationUplinkNotificationsQueueFactory {

    private final IntegrationUplinkNotificationsKafkaSettings integrationUplinkNotificationsKafkaSettings;

    private Map<String, String> topicConfigs;

    @PostConstruct
    public void init() {
        this.topicConfigs = QueueUtil.getConfigs(integrationUplinkNotificationsKafkaSettings.getTopicProperties());
    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<UplinkIntegrationNotificationMsgProto>> createProducer(String serviceId) {
        TbKafkaProducerTemplate.TbKafkaProducerTemplateBuilder<TbProtoQueueMsg<UplinkIntegrationNotificationMsgProto>> producerBuilder = TbKafkaProducerTemplate.builder();
        producerBuilder.properties(producerSettings.toProps(integrationUplinkNotificationsKafkaSettings.getAdditionalProducerConfig()));
        producerBuilder.clientId(kafkaPrefix + "ie-uplink-notifications-producer-" + serviceId);
        producerBuilder.admin(queueAdmin);
        producerBuilder.topicConfigs(topicConfigs);
        producerBuilder.statsManager(producerStatsManager);
        return producerBuilder.build();
    }

    @Override
    public TbQueueControlledOffsetConsumer<TbProtoQueueMsg<UplinkIntegrationNotificationMsgProto>> createConsumer(String topic, String serviceId) {
        TbKafkaConsumerTemplate.TbKafkaConsumerTemplateBuilder<TbProtoQueueMsg<UplinkIntegrationNotificationMsgProto>> consumerBuilder = TbKafkaConsumerTemplate.builder();

        consumerBuilder.properties(consumerSettings.toProps(topic, integrationUplinkNotificationsKafkaSettings.getAdditionalConsumerConfig()));
        consumerBuilder.decoder(msg -> new TbProtoQueueMsg<>(msg.getKey(), UplinkIntegrationNotificationMsgProto.parseFrom(msg.getData()),
                msg.getHeaders(), msg.getPartition(), msg.getOffset()));
        consumerBuilder.clientId(kafkaPrefix + "ie-uplink-notifications-consumer-" + serviceId);
        consumerBuilder.groupId(kafkaPrefix + "ie-uplink-notifications-consumer-group-" + serviceId);
        consumerBuilder.topic(topic);
        consumerBuilder.admin(queueAdmin);
        consumerBuilder.statsService(consumerStatsService);
        consumerBuilder.topicConfigs(topicConfigs);
        consumerBuilder.statsManager(consumerStatsManager);
        return consumerBuilder.build();
    }

}
