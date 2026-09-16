/**
 * Copyright © 2016-2026 The Thingsboard Authors
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
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.gen.queue.ClientTraceEventProto;
import org.thingsboard.mqtt.broker.queue.TbQueueConsumer;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaConsumerTemplate;
import org.thingsboard.mqtt.broker.queue.kafka.TbKafkaProducerTemplate;
import org.thingsboard.mqtt.broker.queue.kafka.settings.ClientTraceKafkaSettings;
import org.thingsboard.mqtt.broker.queue.util.QueueUtil;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class KafkaClientTraceQueueFactory extends AbstractQueueFactory implements ClientTraceQueueFactory {
    private final ClientTraceKafkaSettings settings;
    private Map<String, String> topicConfigs;

    @PostConstruct
    void init() {
        topicConfigs = QueueUtil.getConfigs(settings.getTopicProperties());
    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<ClientTraceEventProto>> createProducer(String serviceId) {
        return TbKafkaProducerTemplate.<TbProtoQueueMsg<ClientTraceEventProto>>builder()
                .properties(producerSettings.toProps(settings.getAdditionalProducerConfig()))
                .clientId(kafkaPrefix + "client-trace-producer-" + serviceId)
                .defaultTopic(settings.getKafkaTopic()).topicConfigs(topicConfigs).admin(queueAdmin)
                .statsManager(producerStatsManager).build();
    }

    @Override
    public TbQueueConsumer<TbProtoQueueMsg<ClientTraceEventProto>> createConsumer(String serviceId) {
        return TbKafkaConsumerTemplate.<TbProtoQueueMsg<ClientTraceEventProto>>builder()
                .properties(consumerSettings.toProps(settings.getKafkaTopic(), settings.getAdditionalConsumerConfig()))
                .topic(settings.getKafkaTopic()).topicConfigs(topicConfigs)
                .clientId(kafkaPrefix + "client-trace-writer-" + serviceId)
                .groupId(kafkaPrefix + "client-trace-writer")
                .decoder(msg -> new TbProtoQueueMsg<>(msg.getKey(), ClientTraceEventProto.parseFrom(msg.getData()), msg.getHeaders()))
                .admin(queueAdmin).statsService(consumerStatsService).statsManager(consumerStatsManager).build();
    }
}
