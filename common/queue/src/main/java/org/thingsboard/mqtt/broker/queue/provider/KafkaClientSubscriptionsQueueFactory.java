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

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaConsumerTemplate;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaProducerTemplate;
import org.thingsboard.mqtt.broker.queue.kafka.settings.ClientSubscriptionsKafkaSettings;
import org.thingsboard.mqtt.broker.queue.util.QueueUtil;

import java.util.Map;
import java.util.Properties;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaClientSubscriptionsQueueFactory extends AbstractQueueFactory implements ClientSubscriptionsQueueFactory {

    private final ClientSubscriptionsKafkaSettings clientSubscriptionsSettings;

    private Map<String, String> topicConfigs;

    @PostConstruct
    public void init() {
        this.topicConfigs = validateAndConfigureCleanupPolicyForTopic(clientSubscriptionsSettings.getTopicProperties(), "Client subscriptions");
    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<QueueProtos.ClientSubscriptionsProto>> createProducer() {
        TbKafkaProducerTemplate.TbKafkaProducerTemplateBuilder<TbProtoQueueMsg<QueueProtos.ClientSubscriptionsProto>> producerBuilder = TbKafkaProducerTemplate.builder();
        producerBuilder.properties(producerSettings.toProps(clientSubscriptionsSettings.getAdditionalProducerConfig()));
        producerBuilder.clientId(kafkaPrefix + "client-subscriptions-producer");
        producerBuilder.defaultTopic(clientSubscriptionsSettings.getKafkaTopic());
        producerBuilder.topicConfigs(topicConfigs);
        producerBuilder.admin(queueAdmin);
        producerBuilder.statsManager(producerStatsManager);
        return producerBuilder.build();
    }

    @Override
    public TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.ClientSubscriptionsProto>> createConsumer(String consumerId, String groupId) {
        TbKafkaConsumerTemplate.TbKafkaConsumerTemplateBuilder<TbProtoQueueMsg<QueueProtos.ClientSubscriptionsProto>> consumerBuilder = TbKafkaConsumerTemplate.builder();

        Properties props = consumerSettings.toProps(clientSubscriptionsSettings.getKafkaTopic(), clientSubscriptionsSettings.getAdditionalConsumerConfig());
        QueueUtil.overrideProperties("ClientSubscriptionsQueue-" + consumerId, props, requiredConsumerProperties);
        consumerBuilder.properties(props);

        consumerBuilder.topic(clientSubscriptionsSettings.getKafkaTopic());
        consumerBuilder.topicConfigs(topicConfigs);
        consumerBuilder.clientId(kafkaPrefix + "client-subscriptions-consumer-" + consumerId);
        consumerBuilder.groupId(kafkaPrefix + BrokerConstants.CLIENT_SUBSCRIPTIONS_CG_PREFIX + groupId);
        consumerBuilder.decoder(msg -> new TbProtoQueueMsg<>(msg.getKey(), QueueProtos.ClientSubscriptionsProto.parseFrom(msg.getData()), msg.getHeaders(),
                msg.getPartition(), msg.getOffset()));
        consumerBuilder.admin(queueAdmin);
        consumerBuilder.statsService(consumerStatsService);
        consumerBuilder.statsManager(consumerStatsManager);
        return consumerBuilder.build();
    }
}
