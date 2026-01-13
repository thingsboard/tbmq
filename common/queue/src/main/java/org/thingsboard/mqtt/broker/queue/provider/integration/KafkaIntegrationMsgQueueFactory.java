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
import org.thingsboard.mqtt.broker.gen.integration.PublishIntegrationMsgProto;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaConsumerTemplate;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaProducerTemplate;
import org.thingsboard.mqtt.broker.queue.kafka.settings.integration.IntegrationMsgKafkaSettings;
import org.thingsboard.mqtt.broker.queue.provider.AbstractQueueFactory;
import org.thingsboard.mqtt.broker.queue.util.QueueUtil;

import java.util.Map;
import java.util.Properties;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaIntegrationMsgQueueFactory extends AbstractQueueFactory implements IntegrationMsgQueueFactory {

    private final IntegrationMsgKafkaSettings integrationMsgKafkaSettings;

    private Map<String, String> topicConfigs;

    @PostConstruct
    public void init() {
        this.topicConfigs = validateAndConfigurePartitionsForTopic(integrationMsgKafkaSettings.getTopicProperties(), "IE message");
    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<PublishIntegrationMsgProto>> createProducer(String serviceId) {
        TbKafkaProducerTemplate.TbKafkaProducerTemplateBuilder<TbProtoQueueMsg<PublishIntegrationMsgProto>> producerBuilder = TbKafkaProducerTemplate.builder();
        producerBuilder.properties(producerSettings.toProps(integrationMsgKafkaSettings.getAdditionalProducerConfig()));
        producerBuilder.clientId(kafkaPrefix + "ie-msg-producer-" + serviceId);
        producerBuilder.admin(queueAdmin);
        producerBuilder.topicConfigs(topicConfigs);
        producerBuilder.statsManager(producerStatsManager);
        return producerBuilder.build();
    }

    @Override
    public TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishIntegrationMsgProto>> createConsumer(String topic, String consumerGroupId, String consumerId) {
        String clientId = "ie-msg-consumer-" + consumerId;

        Properties props = consumerSettings.toProps(topic, integrationMsgKafkaSettings.getAdditionalConsumerConfig());
        QueueUtil.overrideProperties("IeMsgQueue-" + consumerId, props, requiredConsumerProperties);

        TbKafkaConsumerTemplate.TbKafkaConsumerTemplateBuilder<TbProtoQueueMsg<PublishIntegrationMsgProto>> consumerBuilder = TbKafkaConsumerTemplate.builder();
        consumerBuilder.properties(props);
        consumerBuilder.decoder(msg -> new TbProtoQueueMsg<>(msg.getKey(), PublishIntegrationMsgProto.parseFrom(msg.getData()), msg.getHeaders(),
                msg.getPartition(), msg.getOffset()));
        consumerBuilder.clientId(kafkaPrefix + clientId);
        consumerBuilder.groupId(consumerGroupId);
        consumerBuilder.topic(topic);
        consumerBuilder.statsService(consumerStatsService);
        consumerBuilder.autoCommit(false);
        consumerBuilder.createTopicIfNotExists(false);
        return consumerBuilder.build();
    }

    @Override
    public Map<String, String> getTopicConfigs() {
        return topicConfigs;
    }
}
