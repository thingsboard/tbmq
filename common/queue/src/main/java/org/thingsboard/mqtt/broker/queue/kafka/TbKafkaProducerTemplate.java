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
package org.thingsboard.mqtt.broker.queue.kafka;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.thingsboard.mqtt.broker.common.data.StringUtils;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsg;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.stats.ProducerStatsManager;
import org.thingsboard.mqtt.broker.queue.stats.Timer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

@Slf4j
public class TbKafkaProducerTemplate<T extends TbQueueMsg> implements TbQueueProducer<T> {

    private final KafkaProducer<String, byte[]> producer;

    private final String defaultTopic;

    private final TbQueueAdmin admin;
    private final Map<String, String> topicConfigs;
    private final boolean createTopicIfNotExists;
    private final Timer sendTimer;

    @Builder
    private TbKafkaProducerTemplate(Properties properties, String defaultTopic, String clientId, TbQueueAdmin admin,
                                    Boolean createTopicIfNotExists, Map<String, String> topicConfigs, ProducerStatsManager statsManager) {
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArraySerializer");
        if (!StringUtils.isEmpty(clientId)) {
            properties.put(ProducerConfig.CLIENT_ID_CONFIG, clientId);
        }
        Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
        this.producer = new KafkaProducer<>(properties);
        this.admin = admin;
        this.defaultTopic = defaultTopic;
        this.topicConfigs = topicConfigs;
        this.createTopicIfNotExists = createTopicIfNotExists != null ? createTopicIfNotExists : true;
        this.sendTimer = statsManager != null ? statsManager.createSendTimer(clientId) : (amount, unit) -> {};
    }

    @Override
    public String getDefaultTopic() {
        return defaultTopic;
    }

    @Override
    public void send(T msg, TbQueueCallback callback) {
        if (StringUtils.isEmpty(defaultTopic)) {
            throw new RuntimeException("No default topic defined for producer.");
        }
        send(defaultTopic, null, msg, callback);
    }

    @Override
    public void send(String topic, Integer partition, T msg, TbQueueCallback callback) {
        if (admin != null && topicConfigs != null && createTopicIfNotExists) {
            admin.createTopicIfNotExists(topic, topicConfigs);
        }

        long startTime = System.nanoTime();
        ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic, partition, msg.getKey(), msg.getData(), extractHeaders(msg));
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
        sendTimer.logTime(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
    }

    private List<Header> extractHeaders(T msg) {
        var entries = msg.getHeaders().getData().entrySet();
        List<Header> headers = new ArrayList<>(entries.size());
        for (var entry : entries) {
            headers.add(new RecordHeader(entry.getKey(), entry.getValue()));
        }
        return headers;
    }

    @Override
    public void stop() {
        if (producer != null) {
            producer.close();
        }
    }
}
