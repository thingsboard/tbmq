/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
import org.thingsboard.mqtt.broker.common.util.BrokerConstants;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.queue.TbQueueConsumer;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaConsumerTemplate;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaProducerTemplate;
import org.thingsboard.mqtt.broker.queue.kafka.settings.BasicDownLinkPublishMsgKafkaSettings;
import org.thingsboard.mqtt.broker.queue.kafka.settings.TbKafkaConsumerSettings;
import org.thingsboard.mqtt.broker.queue.kafka.settings.TbKafkaProducerSettings;
import org.thingsboard.mqtt.broker.queue.kafka.stats.TbKafkaConsumerStatsService;
import org.thingsboard.mqtt.broker.queue.stats.ConsumerStatsManager;
import org.thingsboard.mqtt.broker.queue.stats.ProducerStatsManager;
import org.thingsboard.mqtt.broker.queue.util.QueueUtil;

import javax.annotation.PostConstruct;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaDownLinkBasicPublishMsgQueueFactory implements DownLinkBasicPublishMsgQueueFactory {

    private final TbKafkaConsumerSettings consumerSettings;
    private final TbKafkaProducerSettings producerSettings;
    private final BasicDownLinkPublishMsgKafkaSettings basicDownLinkPublishMsgKafkaSettings;
    private final TbQueueAdmin queueAdmin;
    private final TbKafkaConsumerStatsService consumerStatsService;

    @Autowired(required = false)
    private ProducerStatsManager producerStatsManager;
    @Autowired(required = false)
    private ConsumerStatsManager consumerStatsManager;

    private Map<String, String> topicConfigs;

    @PostConstruct
    public void init() {
        this.topicConfigs = QueueUtil.getConfigs(basicDownLinkPublishMsgKafkaSettings.getTopicProperties());
    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<QueueProtos.ClientPublishMsgProto>> createProducer(String id) {
        TbKafkaProducerTemplate.TbKafkaProducerTemplateBuilder<TbProtoQueueMsg<QueueProtos.ClientPublishMsgProto>> producerBuilder = TbKafkaProducerTemplate.builder();
        producerBuilder.properties(producerSettings.toProps(basicDownLinkPublishMsgKafkaSettings.getAdditionalProducerConfig()));
        producerBuilder.clientId("basic-downlink-msg-producer-" + id);
        producerBuilder.topicConfigs(topicConfigs);
        producerBuilder.admin(queueAdmin);
        producerBuilder.statsManager(producerStatsManager);
        return producerBuilder.build();
    }

    @Override
    public TbQueueConsumer<TbProtoQueueMsg<QueueProtos.ClientPublishMsgProto>> createConsumer(String topic, String consumerId, String groupId) {
        TbKafkaConsumerTemplate.TbKafkaConsumerTemplateBuilder<TbProtoQueueMsg<QueueProtos.ClientPublishMsgProto>> consumerBuilder = TbKafkaConsumerTemplate.builder();
        consumerBuilder.properties(consumerSettings.toProps(topic, basicDownLinkPublishMsgKafkaSettings.getAdditionalConsumerConfig()));
        consumerBuilder.topic(topic);
        consumerBuilder.topicConfigs(topicConfigs);
        consumerBuilder.clientId("basic-downlink-msg-consumer-" + consumerId);
        consumerBuilder.groupId(BrokerConstants.BASIC_DOWNLINK_CG_PREFIX + groupId);
        consumerBuilder.decoder(msg -> new TbProtoQueueMsg<>(msg.getKey(), QueueProtos.ClientPublishMsgProto.parseFrom(msg.getData()), msg.getHeaders()));
        consumerBuilder.admin(queueAdmin);
        consumerBuilder.statsService(consumerStatsService);
        consumerBuilder.statsManager(consumerStatsManager);
        return consumerBuilder.build();
    }
}
