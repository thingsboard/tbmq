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
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.util.StringUtils;
import org.thingsboard.mqtt.broker.common.data.queue.TopicInfo;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsg;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.kafka.settings.TbKafkaSettings;

import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Created by ashvayka on 24.09.18.
 */
@Slf4j
public class TbKafkaProducerTemplate<T extends TbQueueMsg> implements TbQueueProducer<T> {

    private final KafkaProducer<String, byte[]> producer;

    @Getter
    private final String defaultTopic;

    @Getter
    private final TbKafkaSettings settings;

    private final TbQueueAdmin admin;

    private final Set<TopicInfo> topics;

    @Builder
    private TbKafkaProducerTemplate(TbKafkaSettings settings, String defaultTopic, String clientId, TbQueueAdmin admin) {
        Properties props = settings.toProps();
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArraySerializer");
        if (!StringUtils.isEmpty(clientId)) {
            props.put(ProducerConfig.CLIENT_ID_CONFIG, clientId);
        }
        this.settings = settings;
        this.producer = new KafkaProducer<>(props);
        this.defaultTopic = defaultTopic;
        this.admin = admin;
        topics = ConcurrentHashMap.newKeySet();
    }

    @Override
    public void init() {
    }

    @Override
    public void send(TopicInfo topic, T msg, TbQueueCallback callback) {
        createTopicIfNotExist(topic);
        String key = msg.getKey();
        byte[] data = msg.getData();
        ProducerRecord<String, byte[]> record;
        Iterable<Header> headers = msg.getHeaders().getData().entrySet().stream().map(e -> new RecordHeader(e.getKey(), e.getValue())).collect(Collectors.toList());
        record = new ProducerRecord<>(topic.getTopic(), null, key, data, headers);
        producer.send(record, (metadata, exception) -> {
            if (exception == null) {
                if (callback != null) {
                    callback.onSuccess(new KafkaTbQueueMsgMetadata(metadata));
                }
            } else {
                if (callback != null) {
                    callback.onFailure(exception);
                } else {
                    log.warn("Producer template failure: {}", exception.getMessage(), exception);
                }
            }
        });
    }

    private void createTopicIfNotExist(TopicInfo topic) {
        if (topics.contains(topic)) {
            return;
        }
        admin.createTopicIfNotExists(topic.getTopic());
        topics.add(topic);
    }

    @Override
    public void stop() {
        if (producer != null) {
            producer.close();
        }
    }
}
