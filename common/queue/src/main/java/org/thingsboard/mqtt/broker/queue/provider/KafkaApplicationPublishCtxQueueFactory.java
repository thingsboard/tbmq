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

import java.util.Map;

import static org.thingsboard.mqtt.broker.queue.constants.QueueConstants.CLEANUP_POLICY_PROPERTY;
import static org.thingsboard.mqtt.broker.queue.constants.QueueConstants.COMPACT_POLICY;

@Slf4j
@Component
public class KafkaApplicationPublishCtxQueueFactory implements ApplicationPublishCtxQueueFactory {
    private final TbKafkaSettings kafkaSettings;
    private final TbQueueAdmin queueAdmin;
    private final Map<String, String> applicationPublishCtxTopicConfigs;

    public KafkaApplicationPublishCtxQueueFactory(@Qualifier("application-publish-ctx") TbKafkaSettings kafkaSettings,
                                                  TbKafkaTopicConfigs kafkaTopicConfigs,
                                                  TbQueueAdmin queueAdmin) {
        this.kafkaSettings = kafkaSettings;

        this.applicationPublishCtxTopicConfigs = kafkaTopicConfigs.getApplicationPublishCtxConfigs();
        String configuredLogCleanupPolicy = applicationPublishCtxTopicConfigs.get(CLEANUP_POLICY_PROPERTY);
        if (configuredLogCleanupPolicy != null && !configuredLogCleanupPolicy.equals(COMPACT_POLICY)) {
            log.warn("Application publish ctx clean-up policy should be " + COMPACT_POLICY + ".");
        }
        applicationPublishCtxTopicConfigs.put(CLEANUP_POLICY_PROPERTY, COMPACT_POLICY);

        this.queueAdmin = queueAdmin;
    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<QueueProtos.LastPublishCtxProto>> createProducer() {
        TbKafkaProducerTemplate.TbKafkaProducerTemplateBuilder<TbProtoQueueMsg<QueueProtos.LastPublishCtxProto>> producerBuilder = TbKafkaProducerTemplate.builder();
        producerBuilder.settings(kafkaSettings);
        producerBuilder.clientId("application-publish-ctx-producer");
        producerBuilder.topic(kafkaSettings.getTopic());
        producerBuilder.topicConfigs(applicationPublishCtxTopicConfigs);
        producerBuilder.admin(queueAdmin);
        return producerBuilder.build();
    }

    @Override
    public TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.LastPublishCtxProto>> createConsumer() {
        TbKafkaConsumerTemplate.TbKafkaConsumerTemplateBuilder<TbProtoQueueMsg<QueueProtos.LastPublishCtxProto>> consumerBuilder = TbKafkaConsumerTemplate.builder();
        consumerBuilder.settings(kafkaSettings);
        consumerBuilder.topic(kafkaSettings.getTopic());
        consumerBuilder.topicConfigs(applicationPublishCtxTopicConfigs);
        consumerBuilder.clientId("application-publish-ctx-consumer");
        consumerBuilder.groupId("application-publish-cxt-consumer-group");
        consumerBuilder.decoder(msg -> new TbProtoQueueMsg<>(msg.getKey(), QueueProtos.LastPublishCtxProto.parseFrom(msg.getData()), msg.getHeaders()));
        consumerBuilder.admin(queueAdmin);
        return consumerBuilder.build();
    }
}
