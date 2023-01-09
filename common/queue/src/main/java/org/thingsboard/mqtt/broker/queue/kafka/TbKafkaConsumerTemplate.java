/**
 * Copyright © 2016-2022 The Thingsboard Authors
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
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.queue.TbQueueMsg;
import org.thingsboard.mqtt.broker.queue.common.AbstractTbQueueConsumerTemplate;
import org.thingsboard.mqtt.broker.queue.kafka.stats.TbKafkaConsumerStatsService;
import org.thingsboard.mqtt.broker.queue.stats.ConsumerStatsManager;
import org.thingsboard.mqtt.broker.queue.stats.Timer;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

@Slf4j
public class TbKafkaConsumerTemplate<T extends TbQueueMsg> extends AbstractTbQueueConsumerTemplate<ConsumerRecord<String, byte[]>, T> {
    private static final long DEFAULT_CLOSE_TIMEOUT = 3000;

    private final TbQueueAdmin admin;
    private final KafkaConsumer<String, byte[]> consumer;
    private final TbKafkaDecoder<T> decoder;
    private final Map<String, String> topicConfigs;

    private final TbKafkaConsumerStatsService statsService;
    private final String groupId;
    private final Timer commitTimer;

    private final long closeTimeoutMs;
    private final boolean createTopicIfNotExists;


    /*
        Not thread-safe
     */

    @Builder
    private TbKafkaConsumerTemplate(Properties properties, TbKafkaDecoder<T> decoder,
                                    String clientId, String groupId, String topic,
                                    boolean autoCommit, int autoCommitIntervalMs,
                                    long closeTimeoutMs,
                                    TbQueueAdmin admin, TbKafkaConsumerStatsService statsService,
                                    Boolean createTopicIfNotExists,
                                    Map<String, String> topicConfigs,
                                    ConsumerStatsManager statsManager) {
        super(topic);
        properties.put(ConsumerConfig.CLIENT_ID_CONFIG, clientId);
        if (groupId != null) {
            properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        }
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, autoCommit);
        properties.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, autoCommitIntervalMs);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);

        this.statsService = statsService;
        this.groupId = groupId;
        if (groupId != null && statsService != null) {
            statsService.registerClientGroup(groupId);
        }

        this.closeTimeoutMs = closeTimeoutMs > 0 ? closeTimeoutMs : DEFAULT_CLOSE_TIMEOUT;

        this.admin = admin;
        Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
        this.consumer = new KafkaConsumer<>(properties);
        this.decoder = decoder;
        this.topicConfigs = topicConfigs;
        this.createTopicIfNotExists = createTopicIfNotExists != null ? createTopicIfNotExists : true;
        this.commitTimer = statsManager != null ? statsManager.createCommitTimer(clientId) : (amount, unit) -> {
        };
    }

    @Override
    protected void doSubscribe(String topic) {
        if (createTopicIfNotExists) {
            admin.createTopicIfNotExists(topic, topicConfigs);
        }
        consumer.subscribe(Collections.singletonList(topic));
    }

    @Override
    protected void doAssignPartition(String topic, int partition) {
        if (createTopicIfNotExists) {
            admin.createTopicIfNotExists(topic, topicConfigs);
        }
        consumer.assign(Collections.singletonList(newTopicPartition(topic, partition)));
    }

    @Override
    protected void doAssignAllPartitions(String topic) {
        if (createTopicIfNotExists) {
            admin.createTopicIfNotExists(topic, topicConfigs);
        }
        int numberOfPartitions = admin.getNumberOfPartitions(topic);
        List<TopicPartition> allTopicPartitions = new ArrayList<>();
        for (int i = 0; i < numberOfPartitions; i++) {
            allTopicPartitions.add(newTopicPartition(topic, i));
        }
        consumer.assign(allTopicPartitions);

    }

    @Override
    protected List<ConsumerRecord<String, byte[]>> doPoll(long durationInMillis) {
        ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(durationInMillis));
        if (records.isEmpty()) {
            return Collections.emptyList();
        } else {
            List<ConsumerRecord<String, byte[]>> recordList = new ArrayList<>(256);
            records.forEach(recordList::add);
            return recordList;
        }
    }

    @Override
    public T decode(ConsumerRecord<String, byte[]> record) throws IOException {
        return decoder.decode(new KafkaTbQueueMsg(record));
    }

    @Override
    protected void doCommitSync() {
        long startTime = System.nanoTime();
        consumer.commitSync();
        commitTimer.logTime(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
    }

    @Override
    protected void doCommit(String topic, int partition, long offset) {
        Map<TopicPartition, OffsetAndMetadata> offsetMap = Map.of(newTopicPartition(topic, partition), new OffsetAndMetadata(offset));
        consumer.commitAsync(offsetMap, (offsets, exception) -> {
            if (exception != null) {
                log.warn("[{}][{}] Failed to commit offset {}.", topic, partition, offset);
                if (log.isTraceEnabled()) {
                    log.trace("Detailed error stack trace:", exception);
                }
            }
        });
    }

    @Override
    protected void doUnsubscribeAndClose() {
        if (consumer != null) {
            consumer.unsubscribe();
            consumer.close(Duration.ofMillis(closeTimeoutMs));
        }
        if (groupId != null && statsService != null) {
            statsService.unregisterClientGroup(groupId);
        }
    }

    @Override
    public long doGetEndOffset(String topic, int partition) {
        TopicPartition topicPartition = newTopicPartition(topic, partition);
        return consumer.endOffsets(Collections.singletonList(topicPartition)).getOrDefault(topicPartition, 0L);
    }

    @Override
    public Optional<Long> doGetCommittedOffset(String topic, int partition) {
        TopicPartition topicPartition = newTopicPartition(topic, partition);
        return Optional.ofNullable(
                consumer.committed(Collections.singleton(topicPartition)).get(topicPartition)
        ).map(OffsetAndMetadata::offset);
    }

    @Override
    public String getConsumerGroupId() {
        return groupId;
    }

    private TopicPartition newTopicPartition(String topic, int partition) {
        return new TopicPartition(topic, partition);
    }


    @Override
    public void doSeekToTheBeginning() {
        consumer.seekToBeginning(Collections.emptyList());
    }
}
