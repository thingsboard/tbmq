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
package org.thingsboard.mqtt.broker.queue.kafka;

import lombok.Builder;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.thingsboard.mqtt.broker.queue.TbQueueMetadataService;
import org.thingsboard.mqtt.broker.queue.kafka.settings.TbKafkaAdminSettings;

import java.util.Collections;
import java.util.Properties;

public class TbKafkaMetadataService implements TbQueueMetadataService {
    private final KafkaConsumer<Object, Object> consumer;
    private final AdminClient client;

    @Builder
    public TbKafkaMetadataService(TbKafkaAdminSettings adminSettings, String clientId, String groupId) {
        Properties props = adminSettings.toProps();
        this.client = AdminClient.create(props);
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, clientId);
        if (groupId != null) {
            props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        }
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, "100");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        this.consumer = new KafkaConsumer<>(props);
    }

    @Override
    public long getEndOffset(String topic, int partition) {
        TopicPartition topicPartition = new TopicPartition(topic, partition);
        return consumer.endOffsets(Collections.singleton(topicPartition)).get(topicPartition);
    }

    @Override
    public void deleteTopic(String topic) {
        client.deleteTopics(Collections.singleton(topic));
    }

    @Override
    public void close() {
        if (consumer != null) {
            consumer.close();
        }
        if (client != null) {
            client.close();
        }
    }
}
