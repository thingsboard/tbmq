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
import org.thingsboard.mqtt.broker.queue.constants.QueueConstants;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaConsumerTemplate;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaProducerTemplate;
import org.thingsboard.mqtt.broker.queue.kafka.settings.TbKafkaSettings;
import org.thingsboard.mqtt.broker.queue.kafka.settings.TbKafkaTopicConfigs;
import org.thingsboard.mqtt.broker.queue.kafka.stats.TbKafkaConsumerStatsService;

import java.util.Map;

@Slf4j
@Component
public class KafkaApplicationPersistenceMsgQueueFactory implements ApplicationPersistenceMsgQueueFactory {

    public static final String TOPIC_PREFIX = "application_";
    private final TbKafkaSettings kafkaSettings;
    private final Map<String, String> applicationPersistenceMsgConfigs;
    private final TbQueueAdmin queueAdmin;
    private final TbKafkaConsumerStatsService consumerStatsService;

    public KafkaApplicationPersistenceMsgQueueFactory(@Qualifier("application-persistence-msg") TbKafkaSettings kafkaSettings,
                                                      TbKafkaTopicConfigs kafkaTopicConfigs,
                                                      TbQueueAdmin queueAdmin,
                                                      TbKafkaConsumerStatsService consumerStatsService) {
        this.kafkaSettings = kafkaSettings;
        this.queueAdmin = queueAdmin;

        this.applicationPersistenceMsgConfigs = kafkaTopicConfigs.getApplicationPersistenceMsgConfigs();
        this.consumerStatsService = consumerStatsService;
        String configuredPartitions = applicationPersistenceMsgConfigs.get(QueueConstants.PARTITIONS);
        if (configuredPartitions != null && Integer.parseInt(configuredPartitions) != 1) {
            log.warn("Application persistent message topic must have only 1 partition.");
        }
        applicationPersistenceMsgConfigs.put(QueueConstants.PARTITIONS, "1");
    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> createProducer(String clientId) {
        TbKafkaProducerTemplate.TbKafkaProducerTemplateBuilder<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> producerBuilder = TbKafkaProducerTemplate.builder();
        producerBuilder.settings(kafkaSettings);
        producerBuilder.clientId("application-persistence-msg-producer-" + clientId);
        producerBuilder.topic(TOPIC_PREFIX + clientId);
        producerBuilder.topicConfigs(applicationPersistenceMsgConfigs);
        producerBuilder.admin(queueAdmin);
        return producerBuilder.build();
    }

    @Override
    public TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> createConsumer(String clientId) {
        TbKafkaConsumerTemplate.TbKafkaConsumerTemplateBuilder<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> consumerBuilder = TbKafkaConsumerTemplate.builder();
        consumerBuilder.settings(kafkaSettings);
        consumerBuilder.topic(TOPIC_PREFIX + clientId);
        consumerBuilder.topicConfigs(applicationPersistenceMsgConfigs);
        consumerBuilder.clientId("application-persistence-msg-consumer-" + clientId);
        consumerBuilder.groupId("application-persistence-msg-consumer-group-" + clientId);
        consumerBuilder.decoder(msg -> new TbProtoQueueMsg<>(msg.getKey(), QueueProtos.PublishMsgProto.parseFrom(msg.getData()), msg.getHeaders(),
                msg.getPartition(), msg.getOffset()));
        consumerBuilder.admin(queueAdmin);
        consumerBuilder.autoCommit(false);
        consumerBuilder.statsService(consumerStatsService);
        return consumerBuilder.build();
    }

    @Override
    public String getTopic(String clientId) {
        return TOPIC_PREFIX + clientId;
    }
}
