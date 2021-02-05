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

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.queue.TbQueueConsumer;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaAdmin;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaConsumerTemplate;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaProducerTemplate;
import org.thingsboard.mqtt.broker.queue.kafka.settings.TbKafkaAdminSettings;
import org.thingsboard.mqtt.broker.queue.kafka.settings.TbKafkaSettings;
import org.thingsboard.mqtt.broker.queue.kafka.settings.TbKafkaTopicConfigs;

import javax.annotation.PreDestroy;

@Component
public class KafkaPublishMsgQueueFactory implements PublishMsgQueueFactory {

    private final TbKafkaSettings kafkaSettings;
    private final TbQueueAdmin publishMsgAdmin;

    public KafkaPublishMsgQueueFactory(@Qualifier("publish-msg") TbKafkaSettings kafkaSettings,
                                       TbKafkaTopicConfigs kafkaTopicConfigs,
                                       TbKafkaAdminSettings kafkaAdminSettings) {
        this.kafkaSettings = kafkaSettings;

        this.publishMsgAdmin = new TbKafkaAdmin(kafkaAdminSettings, kafkaTopicConfigs.getPublishMsgConfigs());
    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> createProducer() {
        TbKafkaProducerTemplate.TbKafkaProducerTemplateBuilder<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> requestBuilder = TbKafkaProducerTemplate.builder();
        requestBuilder.settings(kafkaSettings);
        requestBuilder.clientId("publish-msg-producer");
        requestBuilder.defaultTopic(kafkaSettings.getTopic());
        requestBuilder.admin(publishMsgAdmin);
        return requestBuilder.build();
    }

    @Override
    public TbQueueConsumer<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> createConsumer(String id) {
        TbKafkaConsumerTemplate.TbKafkaConsumerTemplateBuilder<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> consumerBuilder = TbKafkaConsumerTemplate.builder();
        consumerBuilder.settings(kafkaSettings);
        consumerBuilder.topic(kafkaSettings.getTopic());
        consumerBuilder.clientId("publish-msg-consumer-" + id);
        consumerBuilder.groupId("publish-msg-consumer-group");
        consumerBuilder.decoder(msg -> new TbProtoQueueMsg<>(msg.getKey(), QueueProtos.PublishMsgProto.parseFrom(msg.getData()), msg.getHeaders()));
        consumerBuilder.admin(publishMsgAdmin);
        return consumerBuilder.build();
    }


    @PreDestroy
    private void destroy() {
        if (publishMsgAdmin != null) {
            publishMsgAdmin.destroy();
        }
    }
}
