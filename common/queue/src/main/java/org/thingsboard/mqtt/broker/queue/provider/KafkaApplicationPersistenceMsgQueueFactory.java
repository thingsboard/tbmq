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
package org.thingsboard.mqtt.broker.queue.provider;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaConsumerTemplate;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaProducerTemplate;
import org.thingsboard.mqtt.broker.queue.kafka.settings.ApplicationPersistenceMsgKafkaSettings;
import org.thingsboard.mqtt.broker.queue.kafka.settings.ApplicationSharedTopicMsgKafkaSettings;
import org.thingsboard.mqtt.broker.queue.util.QueueUtil;

import java.util.Map;
import java.util.Properties;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaApplicationPersistenceMsgQueueFactory extends AbstractQueueFactory implements ApplicationPersistenceMsgQueueFactory {

    private final Map<String, String> requiredConsumerProperties = Map.of("auto.offset.reset", "latest");

    private final ApplicationPersistenceMsgKafkaSettings applicationPersistenceMsgSettings;
    private final ApplicationSharedTopicMsgKafkaSettings applicationSharedTopicMsgSettings;

    private Map<String, String> topicConfigs;
    private Map<String, String> sharedTopicConfigs;

    @PostConstruct
    public void init() {
        this.topicConfigs = validateAndConfigurePartitionsForTopic(applicationPersistenceMsgSettings.getTopicProperties(), "Application persistent message");
        this.sharedTopicConfigs = QueueUtil.getConfigs(applicationSharedTopicMsgSettings.getTopicProperties());
    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> createProducer(String serviceId) {
        return createProducer(applicationPersistenceMsgSettings.getAdditionalProducerConfig(), "application-persisted-msg-producer-", serviceId);
    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> createSharedSubsProducer(String serviceId) {
        return createProducer(applicationSharedTopicMsgSettings.getAdditionalProducerConfig(), "application-shared-msg-producer-", serviceId);
    }

    private TbKafkaProducerTemplate<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> createProducer(String additionalProducerConfig, String clientIdPrefix, String serviceId) {
        TbKafkaProducerTemplate.TbKafkaProducerTemplateBuilder<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> producerBuilder = TbKafkaProducerTemplate.builder();
        producerBuilder.properties(producerSettings.toProps(additionalProducerConfig));
        producerBuilder.clientId(kafkaPrefix + clientIdPrefix + serviceId);
        producerBuilder.createTopicIfNotExists(false);
        producerBuilder.statsManager(producerStatsManager);
        return producerBuilder.build();
    }

    @Override
    public TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> createConsumer(
            String topic, String consumerGroupId, String consumerId) {

        String clientId = "application-persisted-msg-consumer-" + consumerId;

        Properties props = consumerSettings.toProps(topic, applicationPersistenceMsgSettings.getAdditionalConsumerConfig());
        QueueUtil.overrideProperties("ApplicationMsgQueue-" + consumerId, props, requiredConsumerProperties);

        return createConsumer(topic, consumerGroupId, clientId, props);
    }

    @Override
    public TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> createConsumerForSharedTopic(
            String topic, String consumerGroupId, String consumerId) {

        String clientId = "application-shared-msg-consumer-" + consumerId;
        Properties props = consumerSettings.toProps(topic, applicationSharedTopicMsgSettings.getAdditionalConsumerConfig());

        return createConsumer(topic, consumerGroupId, clientId, props);
    }

    private TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> createConsumer(
            String topic, String consumerGroupId, String clientId, Properties props) {

        TbKafkaConsumerTemplate.TbKafkaConsumerTemplateBuilder<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> consumerBuilder = TbKafkaConsumerTemplate.builder();
        consumerBuilder.properties(props);
        consumerBuilder.topic(topic);
        consumerBuilder.clientId(kafkaPrefix + clientId);
        consumerBuilder.groupId(consumerGroupId);
        consumerBuilder.decoder(msg -> new TbProtoQueueMsg<>(msg.getKey(), QueueProtos.PublishMsgProto.parseFrom(msg.getData()), msg.getHeaders(),
                msg.getPartition(), msg.getOffset()));
        consumerBuilder.autoCommit(false);
        consumerBuilder.statsService(consumerStatsService);
        consumerBuilder.createTopicIfNotExists(false);
        return consumerBuilder.build();
    }

    @Override
    public Map<String, String> getTopicConfigs() {
        return topicConfigs;
    }

    @Override
    public Map<String, String> getSharedTopicConfigs() {
        return sharedTopicConfigs;
    }
}
