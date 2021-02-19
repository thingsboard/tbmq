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
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.queue.TbQueueMsg;
import org.thingsboard.mqtt.broker.queue.common.AbstractTbQueueConsumerTemplate;
import org.thingsboard.mqtt.broker.queue.kafka.settings.TbKafkaSettings;
import org.thingsboard.mqtt.broker.queue.kafka.stats.TbKafkaConsumerStatsService;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Slf4j
public class TbKafkaConsumerTemplate<T extends TbQueueMsg> extends AbstractTbQueueConsumerTemplate<ConsumerRecord<String, byte[]>, T> {

    private final TbQueueAdmin admin;
    private final KafkaConsumer<String, byte[]> consumer;
    private final TbKafkaDecoder<T> decoder;
    private final Map<String, String> topicConfigs;

    private final TbKafkaConsumerStatsService statsService;
    private final String groupId;


    @Builder
    private TbKafkaConsumerTemplate(TbKafkaSettings settings, TbKafkaDecoder<T> decoder,
                                    String clientId, String groupId, String topic,
                                    boolean autoCommit, int autoCommitIntervalMs,
                                    TbQueueAdmin admin, TbKafkaConsumerStatsService statsService,
                                    Map<String, String> topicConfigs) {
        super(topic);
        Properties props = settings.toConsumerProps();
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, clientId);
        if (groupId != null) {
            props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        }
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, autoCommit);
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, autoCommitIntervalMs);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArrayDeserializer");

        this.statsService = statsService;
        this.groupId = groupId;
        if (statsService != null) {
            statsService.registerClientGroup(groupId);
        }

        this.admin = admin;
        this.consumer = new KafkaConsumer<>(props);
        this.decoder = decoder;
        this.topicConfigs = topicConfigs;
    }

    @Override
    protected void doSubscribe(String topic) {
        admin.createTopicIfNotExists(topic, topicConfigs);
        consumer.subscribe(Collections.singletonList(topic));
    }

    @Override
    protected void doAssignPartition(String topic, int partition) {
        admin.createTopicIfNotExists(topic, topicConfigs);
        consumer.assign(Collections.singletonList(new TopicPartition(topic, partition)));
    }

    @Override
    protected void doAssignAllPartitions(String topic) {
        admin.createTopicIfNotExists(topic, topicConfigs);
        int numberOfPartitions = admin.getNumberOfPartitions(topic);
        List<TopicPartition> allTopicPartitions = new ArrayList<>();
        for (int i = 0; i < numberOfPartitions; i++) {
            allTopicPartitions.add(new TopicPartition(topic, i));
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
    protected void doCommit() {
        consumer.commitAsync();
    }

    @Override
    protected void doCommit(String topic, int partition, long offset) {
        Map<TopicPartition, OffsetAndMetadata> offsetMap = Map.of(new TopicPartition(topic, partition), new OffsetAndMetadata(offset));
        consumer.commitAsync(offsetMap, (offsets, exception) -> {
            if (exception != null) {
                log.warn("[{}][{}] Failed to commit offset {}.", topic, partition, offset);
                log.trace("Detailed error stack trace:", exception);
            }
        });
    }

    @Override
    protected void doUnsubscribeAndClose() {
        if (consumer != null) {
            consumer.unsubscribe();
            consumer.close();
        }
        if (statsService != null) {
            statsService.unregisterClientGroup(groupId);
        }
    }

    @Override
    public long getOffset(String topic, int partition) {
        return consumer.position(new TopicPartition(topic, partition));
    }


    @Override
    public void seekToTheBeginning() {
        consumer.seekToBeginning(Collections.emptyList());
    }
}
