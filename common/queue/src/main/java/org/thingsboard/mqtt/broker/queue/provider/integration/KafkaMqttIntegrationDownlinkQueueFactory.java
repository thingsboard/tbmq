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
package org.thingsboard.mqtt.broker.queue.provider.integration;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.gen.integration.DownlinkIntegrationMsgProto;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaConsumerTemplate;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaProducerTemplate;
import org.thingsboard.mqtt.broker.queue.kafka.settings.integration.MqttIntegrationDownlinkKafkaSettings;
import org.thingsboard.mqtt.broker.queue.provider.AbstractQueueFactory;
import org.thingsboard.mqtt.broker.queue.util.QueueUtil;

import java.util.Map;
import java.util.Properties;

import static org.thingsboard.mqtt.broker.queue.constants.QueueConstants.MQTT_TOPIC_SUFFIX;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaMqttIntegrationDownlinkQueueFactory extends AbstractQueueFactory implements MqttIntegrationDownlinkQueueFactory {

    private final MqttIntegrationDownlinkKafkaSettings mqttIntegrationDownlinkKafkaSettings;

    private Map<String, String> topicConfigs;

    @PostConstruct
    public void init() {
        this.topicConfigs = validateAndConfigureCleanupPolicyForTopic(mqttIntegrationDownlinkKafkaSettings.getTopicProperties(), "MQTT IE Downlink");
    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<DownlinkIntegrationMsgProto>> createProducer(String serviceId) {
        TbKafkaProducerTemplate.TbKafkaProducerTemplateBuilder<TbProtoQueueMsg<DownlinkIntegrationMsgProto>> producerBuilder = TbKafkaProducerTemplate.builder();
        producerBuilder.properties(producerSettings.toProps(mqttIntegrationDownlinkKafkaSettings.getAdditionalProducerConfig()));
        producerBuilder.defaultTopic(mqttIntegrationDownlinkKafkaSettings.getKafkaTopicPrefix() + MQTT_TOPIC_SUFFIX);
        producerBuilder.clientId(kafkaPrefix + "mqtt-ie-downlink-producer-" + serviceId);
        producerBuilder.admin(queueAdmin);
        producerBuilder.topicConfigs(topicConfigs);
        producerBuilder.statsManager(producerStatsManager);
        return producerBuilder.build();
    }

    @Override
    public TbQueueControlledOffsetConsumer<TbProtoQueueMsg<DownlinkIntegrationMsgProto>> createConsumer(String consumerId) {
        TbKafkaConsumerTemplate.TbKafkaConsumerTemplateBuilder<TbProtoQueueMsg<DownlinkIntegrationMsgProto>> consumerBuilder = TbKafkaConsumerTemplate.builder();
        String topic = mqttIntegrationDownlinkKafkaSettings.getKafkaTopicPrefix() + MQTT_TOPIC_SUFFIX;

        Properties props = consumerSettings.toProps(topic, mqttIntegrationDownlinkKafkaSettings.getAdditionalConsumerConfig());
        QueueUtil.overrideProperties("MqttIeDownlink", props, requiredConsumerProperties);
        consumerBuilder.properties(props);

        consumerBuilder.decoder(msg -> new TbProtoQueueMsg<>(msg.getKey(), DownlinkIntegrationMsgProto.parseFrom(msg.getData()),
                msg.getHeaders(), msg.getPartition(), msg.getOffset()));
        consumerBuilder.clientId(kafkaPrefix + "mqtt-ie-downlink-consumer-" + consumerId);
        consumerBuilder.groupId(kafkaPrefix + "mqtt-ie-downlink-consumer-group");
        consumerBuilder.topic(topic);
        consumerBuilder.admin(queueAdmin);
        consumerBuilder.statsService(consumerStatsService);
        consumerBuilder.topicConfigs(topicConfigs);
        consumerBuilder.statsManager(consumerStatsManager);
        return consumerBuilder.build();
    }
}
