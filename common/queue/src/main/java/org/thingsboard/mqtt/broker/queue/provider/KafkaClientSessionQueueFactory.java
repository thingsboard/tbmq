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
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaAdmin;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaConsumerTemplate;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaProducerTemplate;
import org.thingsboard.mqtt.broker.queue.kafka.settings.ClientSessionKafkaSettings;
import org.thingsboard.mqtt.broker.queue.kafka.settings.TbKafkaTopicConfigs;

import javax.annotation.PreDestroy;
import java.util.Map;

import static org.thingsboard.mqtt.broker.queue.constants.QueueConstants.ACKS_ALL_PROPERTY;
import static org.thingsboard.mqtt.broker.queue.constants.QueueConstants.COMPACT_POLICY;
import static org.thingsboard.mqtt.broker.queue.constants.QueueConstants.CLEANUP_POLICY_PROPERTY;

@Slf4j
@Component
public class KafkaClientSessionQueueFactory implements ClientSessionQueueFactory {
    private final ClientSessionKafkaSettings clientSessionKafkaSettings;
    private final TbQueueAdmin clientSessionAdmin;

    public KafkaClientSessionQueueFactory(ClientSessionKafkaSettings clientSessionKafkaSettings,
                                          TbKafkaTopicConfigs kafkaTopicConfigs) {
        this.clientSessionKafkaSettings = clientSessionKafkaSettings;

        if (!StringUtils.isEmpty(clientSessionKafkaSettings.getAcks()) && !clientSessionKafkaSettings.getAcks().equals(ACKS_ALL_PROPERTY)) {
            log.warn("Producer ACKS should be '" + ACKS_ALL_PROPERTY + "'. It's required to not miss subscriptions in case if broker restarts.");
        }
        this.clientSessionKafkaSettings.setAcks(ACKS_ALL_PROPERTY);

        Map<String, String> clientSessionConfigs = kafkaTopicConfigs.getClientSessionConfigs();
        String configuredLogCleanupPolicy = clientSessionConfigs.get(CLEANUP_POLICY_PROPERTY);
        if (configuredLogCleanupPolicy != null && !configuredLogCleanupPolicy.equals(COMPACT_POLICY)) {
            log.warn("Client session clean-up policy should be " + COMPACT_POLICY + ".");
        }
        clientSessionConfigs.put(CLEANUP_POLICY_PROPERTY, COMPACT_POLICY);

        this.clientSessionAdmin = new TbKafkaAdmin(clientSessionKafkaSettings, clientSessionConfigs);
    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<QueueProtos.ClientSessionProto>> createProducer() {
        TbKafkaProducerTemplate.TbKafkaProducerTemplateBuilder<TbProtoQueueMsg<QueueProtos.ClientSessionProto>> requestBuilder = TbKafkaProducerTemplate.builder();
        requestBuilder.settings(clientSessionKafkaSettings);
        requestBuilder.clientId("client-session-producer");
        requestBuilder.defaultTopic(clientSessionKafkaSettings.getTopic());
        requestBuilder.admin(clientSessionAdmin);
        return requestBuilder.build();
    }

    @Override
    public TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.ClientSessionProto>> createConsumer() {
        TbKafkaConsumerTemplate.TbKafkaConsumerTemplateBuilder<TbProtoQueueMsg<QueueProtos.ClientSessionProto>> consumerBuilder = TbKafkaConsumerTemplate.builder();
        consumerBuilder.settings(clientSessionKafkaSettings);
        consumerBuilder.topic(clientSessionKafkaSettings.getTopic());
        consumerBuilder.clientId("client-session-consumer");
        consumerBuilder.groupId("client-session-consumer-group");
        consumerBuilder.decoder(msg -> new TbProtoQueueMsg<>(msg.getKey(), QueueProtos.ClientSessionProto.parseFrom(msg.getData()), msg.getHeaders()));
        consumerBuilder.admin(clientSessionAdmin);
        return consumerBuilder.build();
    }


    @PreDestroy
    private void destroy() {
        if (clientSessionAdmin != null) {
            clientSessionAdmin.destroy();
        }
    }
}
