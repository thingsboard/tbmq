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
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaAdmin;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaConsumerTemplate;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaProducerTemplate;
import org.thingsboard.mqtt.broker.queue.kafka.settings.TbKafkaAdminSettings;
import org.thingsboard.mqtt.broker.queue.kafka.settings.TbKafkaSettings;
import org.thingsboard.mqtt.broker.queue.kafka.settings.TbKafkaTopicConfigs;

import javax.annotation.PreDestroy;
import java.util.Map;

import static org.thingsboard.mqtt.broker.queue.constants.QueueConstants.COMPACT_POLICY;
import static org.thingsboard.mqtt.broker.queue.constants.QueueConstants.CLEANUP_POLICY_PROPERTY;

@Slf4j
@Component
public class KafkaApplicationPublishCtxQueueFactory implements ApplicationPublishCtxQueueFactory {
    private final TbKafkaSettings kafkaSettings;
    private final TbQueueAdmin queueAdmin;

    public KafkaApplicationPublishCtxQueueFactory(@Qualifier("application-publish-ctx") TbKafkaSettings kafkaSettings,
                                                  TbKafkaTopicConfigs kafkaTopicConfigs,
                                                  TbKafkaAdminSettings kafkaAdminSettings) {
        this.kafkaSettings = kafkaSettings;

        Map<String, String> applicationPublishCtxConfigs = kafkaTopicConfigs.getApplicationPublishCtxConfigs();
        String configuredLogCleanupPolicy = applicationPublishCtxConfigs.get(CLEANUP_POLICY_PROPERTY);
        if (configuredLogCleanupPolicy != null && !configuredLogCleanupPolicy.equals(COMPACT_POLICY)) {
            log.warn("Application publish ctx clean-up policy should be " + COMPACT_POLICY + ".");
        }
        applicationPublishCtxConfigs.put(CLEANUP_POLICY_PROPERTY, COMPACT_POLICY);

        this.queueAdmin = new TbKafkaAdmin(kafkaAdminSettings, applicationPublishCtxConfigs);
    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<QueueProtos.LastPublishCtxProto>> createProducer() {
        TbKafkaProducerTemplate.TbKafkaProducerTemplateBuilder<TbProtoQueueMsg<QueueProtos.LastPublishCtxProto>> requestBuilder = TbKafkaProducerTemplate.builder();
        requestBuilder.settings(kafkaSettings);
        requestBuilder.clientId("application-publish-ctx-producer");
        requestBuilder.defaultTopic(kafkaSettings.getTopic());
        requestBuilder.admin(queueAdmin);
        return requestBuilder.build();
    }

    @Override
    public TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.LastPublishCtxProto>> createConsumer() {
        TbKafkaConsumerTemplate.TbKafkaConsumerTemplateBuilder<TbProtoQueueMsg<QueueProtos.LastPublishCtxProto>> consumerBuilder = TbKafkaConsumerTemplate.builder();
        consumerBuilder.settings(kafkaSettings);
        consumerBuilder.topic(kafkaSettings.getTopic());
        consumerBuilder.clientId("application-publish-ctx-consumer");
        consumerBuilder.groupId("application-publish-cxt-consumer-group");
        consumerBuilder.decoder(msg -> new TbProtoQueueMsg<>(msg.getKey(), QueueProtos.LastPublishCtxProto.parseFrom(msg.getData()), msg.getHeaders()));
        consumerBuilder.admin(queueAdmin);
        return consumerBuilder.build();
    }

    @PreDestroy
    private void destroy() {
        if (queueAdmin != null) {
            queueAdmin.destroy();
        }
    }
}
