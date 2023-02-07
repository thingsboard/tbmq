/**
 * Copyright © 2016-2023 The Thingsboard Authors
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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.constants.QueueConstants;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaConsumerTemplate;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaProducerTemplate;
import org.thingsboard.mqtt.broker.queue.kafka.settings.ApplicationPersistenceMsgKafkaSettings;
import org.thingsboard.mqtt.broker.queue.kafka.settings.ApplicationSharedTopicMsgKafkaSettings;
import org.thingsboard.mqtt.broker.queue.kafka.settings.TbKafkaConsumerSettings;
import org.thingsboard.mqtt.broker.queue.kafka.settings.TbKafkaProducerSettings;
import org.thingsboard.mqtt.broker.queue.kafka.stats.TbKafkaConsumerStatsService;
import org.thingsboard.mqtt.broker.queue.stats.ProducerStatsManager;
import org.thingsboard.mqtt.broker.queue.util.QueueUtil;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.Properties;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaApplicationPersistenceMsgQueueFactory implements ApplicationPersistenceMsgQueueFactory {
    private final Map<String, String> requiredConsumerProperties = Map.of("auto.offset.reset", "latest");

    private final TbKafkaConsumerSettings consumerSettings;
    private final TbKafkaProducerSettings producerSettings;
    private final ApplicationPersistenceMsgKafkaSettings applicationPersistenceMsgSettings;
    private final ApplicationSharedTopicMsgKafkaSettings applicationSharedTopicMsgSettings;
    private final TbKafkaConsumerStatsService consumerStatsService;

    @Autowired(required = false)
    private ProducerStatsManager producerStatsManager;

    private Map<String, String> topicConfigs;
    private Map<String, String> sharedTopicConfigs;

    @PostConstruct
    public void init() {
        this.topicConfigs = QueueUtil.getConfigs(applicationPersistenceMsgSettings.getTopicProperties());
        String configuredPartitions = topicConfigs.get(QueueConstants.PARTITIONS);
        if (configuredPartitions != null && Integer.parseInt(configuredPartitions) != 1) {
            log.warn("Application persistent message topic must have only 1 partition.");
        }
        topicConfigs.put(QueueConstants.PARTITIONS, "1");

        this.sharedTopicConfigs = QueueUtil.getConfigs(applicationSharedTopicMsgSettings.getTopicProperties());
    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> createProducer(String serviceId) {
        TbKafkaProducerTemplate.TbKafkaProducerTemplateBuilder<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> producerBuilder = TbKafkaProducerTemplate.builder();
        producerBuilder.properties(producerSettings.toProps(applicationPersistenceMsgSettings.getAdditionalProducerConfig()));
        producerBuilder.clientId("application-persisted-msg-producer-" + serviceId);
        producerBuilder.createTopicIfNotExists(false);
        producerBuilder.statsManager(producerStatsManager);
        return producerBuilder.build();
    }

    @Override
    public TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> createConsumer(
            String topic, String consumerGroup, String consumerId) {

        String clientId = "application-persisted-msg-consumer-" + consumerId;

        Properties props = consumerSettings.toProps(applicationPersistenceMsgSettings.getAdditionalConsumerConfig());
        QueueUtil.overrideProperties("ApplicationMsgQueue-" + consumerId, props, requiredConsumerProperties);

        return createConsumer(topic, consumerGroup, clientId, props);
    }

    @Override
    public TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> createConsumerForSharedTopic(
            String topic, String consumerGroup, String consumerId) {

        String clientId = "application-shared-msg-consumer-" + consumerId;
        Properties props = consumerSettings.toProps(applicationSharedTopicMsgSettings.getAdditionalConsumerConfig());

        return createConsumer(topic, consumerGroup, clientId, props);
    }

    private TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> createConsumer(
            String topic, String consumerGroup, String clientId, Properties props) {

        TbKafkaConsumerTemplate.TbKafkaConsumerTemplateBuilder<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> consumerBuilder = TbKafkaConsumerTemplate.builder();
        consumerBuilder.properties(props);
        consumerBuilder.topic(topic);
        consumerBuilder.clientId(clientId);
        consumerBuilder.groupId(consumerGroup);
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
