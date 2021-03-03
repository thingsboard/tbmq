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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaConsumerTemplate;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaProducerTemplate;
import org.thingsboard.mqtt.broker.queue.kafka.settings.ClientSessionKafkaSettings;
import org.thingsboard.mqtt.broker.queue.kafka.settings.TbKafkaConsumerSettings;
import org.thingsboard.mqtt.broker.queue.kafka.settings.TbKafkaProducerSettings;
import org.thingsboard.mqtt.broker.queue.kafka.stats.TbKafkaConsumerStatsService;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.Properties;

import static org.thingsboard.mqtt.broker.queue.constants.QueueConstants.CLEANUP_POLICY_PROPERTY;
import static org.thingsboard.mqtt.broker.queue.constants.QueueConstants.COMPACT_POLICY;
import static org.thingsboard.mqtt.broker.queue.util.ParseConfigUtil.getConfigs;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaClientSessionQueueFactory implements ClientSessionQueueFactory {
    private final TbKafkaConsumerSettings consumerSettings;
    private final TbKafkaProducerSettings producerSettings;
    private final ClientSessionKafkaSettings clientSessionSettings;
    private final TbQueueAdmin queueAdmin;
    private final TbKafkaConsumerStatsService consumerStatsService;

    private Map<String, String> topicConfigs;

    @PostConstruct
    public void init() {
        this.topicConfigs = getConfigs(clientSessionSettings.getTopicProperties());
        String configuredLogCleanupPolicy = topicConfigs.get(CLEANUP_POLICY_PROPERTY);
        if (configuredLogCleanupPolicy != null && !configuredLogCleanupPolicy.equals(COMPACT_POLICY)) {
            log.warn("Client session clean-up policy should be " + COMPACT_POLICY + ".");
        }
        topicConfigs.put(CLEANUP_POLICY_PROPERTY, COMPACT_POLICY);

    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<QueueProtos.ClientSessionProto>> createProducer() {
        TbKafkaProducerTemplate.TbKafkaProducerTemplateBuilder<TbProtoQueueMsg<QueueProtos.ClientSessionProto>> producerBuilder = TbKafkaProducerTemplate.builder();
        producerBuilder.properties(producerSettings.toProps(clientSessionSettings.getProducerProperties()));
        producerBuilder.clientId("client-session-producer");
        producerBuilder.topic(clientSessionSettings.getTopic());
        producerBuilder.topicConfigs(topicConfigs);
        producerBuilder.admin(queueAdmin);
        return producerBuilder.build();
    }

    @Override
    public TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.ClientSessionProto>> createConsumer(String id) {
        TbKafkaConsumerTemplate.TbKafkaConsumerTemplateBuilder<TbProtoQueueMsg<QueueProtos.ClientSessionProto>> consumerBuilder = TbKafkaConsumerTemplate.builder();
        consumerBuilder.properties(consumerSettings.toProps(clientSessionSettings.getConsumerProperties()));
        consumerBuilder.topic(clientSessionSettings.getTopic());
        consumerBuilder.topicConfigs(topicConfigs);
        consumerBuilder.clientId("client-session-consumer-" + id);
        consumerBuilder.decoder(msg -> new TbProtoQueueMsg<>(msg.getKey(), QueueProtos.ClientSessionProto.parseFrom(msg.getData()), msg.getHeaders()));
        consumerBuilder.admin(queueAdmin);
        consumerBuilder.statsService(consumerStatsService);
        return consumerBuilder.build();
    }
}
