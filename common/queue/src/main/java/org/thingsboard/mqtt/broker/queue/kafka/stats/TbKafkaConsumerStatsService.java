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
package org.thingsboard.mqtt.broker.queue.kafka.stats;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.queue.kafka.settings.StatsConsumerKafkaSettings;
import org.thingsboard.mqtt.broker.queue.kafka.settings.TbKafkaConsumerSettings;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class TbKafkaConsumerStatsService {

    private final Set<String> monitoredGroups = ConcurrentHashMap.newKeySet();

    private final TbQueueAdmin tbQueueAdmin;
    private final StatsConsumerKafkaSettings statsConsumerSettings;
    private final TbKafkaConsumerSettings consumerSettings;
    private final TbKafkaConsumerStatisticConfig statsConfig;

    @Value("${queue.kafka.kafka-prefix:}")
    private String kafkaPrefix;

    private Consumer<String, byte[]> consumer;
    private ScheduledExecutorService statsPrintScheduler;

    @PostConstruct
    public void init() {
        if (!statsConfig.getEnabled()) {
            return;
        }
        this.statsPrintScheduler = Executors.newSingleThreadScheduledExecutor(ThingsBoardThreadFactory.forName("kafka-consumer-stats"));

        Properties consumerProps = consumerSettings.toProps("stats_dummy", statsConsumerSettings.getConsumerProperties());
        consumerProps.put(ConsumerConfig.CLIENT_ID_CONFIG, kafkaPrefix + "consumer-stats-loader-client");
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaPrefix + "consumer-stats-loader-client-group");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        this.consumer = new KafkaConsumer<>(consumerProps);

        startLogScheduling();
    }

    private void startLogScheduling() {
        Duration timeoutDuration = Duration.ofMillis(statsConfig.getKafkaResponseTimeoutMs());
        statsPrintScheduler.scheduleWithFixedDelay(() -> {
            if (!isStatsPrintRequired()) {
                return;
            }
            for (String groupId : monitoredGroups) {
                try {
                    Map<TopicPartition, OffsetAndMetadata> groupOffsets = tbQueueAdmin.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata()
                            .get(statsConfig.getKafkaResponseTimeoutMs(), TimeUnit.MILLISECONDS);
                    Map<TopicPartition, Long> endOffsets = consumer.endOffsets(groupOffsets.keySet(), timeoutDuration);

                    List<GroupTopicStats> lagTopicsStats = getTopicsStatsWithLag(groupOffsets, endOffsets);
                    if (!lagTopicsStats.isEmpty()) {
                        StringBuilder builder = new StringBuilder();
                        for (int i = 0; i < lagTopicsStats.size(); i++) {
                            builder.append(lagTopicsStats.get(i).toString());
                            if (i != lagTopicsStats.size() - 1) {
                                builder.append(", ");
                            }
                        }
                        log.info("[{}] Topic partitions with lag: [{}].", groupId, builder);
                    }
                } catch (Exception e) {
                    log.warn("[{}] Failed to get consumer group stats.", groupId, e);
                }
            }

        }, statsConfig.getPrintIntervalMs(), statsConfig.getPrintIntervalMs(), TimeUnit.MILLISECONDS);
    }

    private boolean isStatsPrintRequired() {
        return log.isInfoEnabled();
    }

    private List<GroupTopicStats> getTopicsStatsWithLag(Map<TopicPartition, OffsetAndMetadata> groupOffsets, Map<TopicPartition, Long> endOffsets) {
        List<GroupTopicStats> consumerGroupStats = new ArrayList<>();
        for (TopicPartition topicPartition : groupOffsets.keySet()) {
            long endOffset = endOffsets.get(topicPartition);
            long committedOffset = groupOffsets.get(topicPartition).offset();
            long lag = endOffset - committedOffset;
            if (lag != 0) {
                GroupTopicStats groupTopicStats = GroupTopicStats.builder()
                        .topic(topicPartition.topic())
                        .partition(topicPartition.partition())
                        .lag(lag)
                        .build();
                consumerGroupStats.add(groupTopicStats);
            }
        }
        return consumerGroupStats;
    }

    public void registerClientGroup(String groupId) {
        if (statsConfig.getEnabled() && !StringUtils.isEmpty(groupId)) {
            monitoredGroups.add(groupId);
        }
    }

    public void unregisterClientGroup(String groupId) {
        if (statsConfig.getEnabled() && !StringUtils.isEmpty(groupId)) {
            monitoredGroups.remove(groupId);
        }
    }

    @PreDestroy
    public void destroy() {
        if (statsPrintScheduler != null) {
            ThingsBoardExecutors.shutdownAndAwaitTermination(statsPrintScheduler, "Kafka consumer stats");
        }
        if (consumer != null) {
            consumer.close();
        }
    }

    @Builder
    @Data
    private static class GroupTopicStats {

        private String topic;
        private int partition;
        private long lag;

        @Override
        public String toString() {
            return "[" +
                    "topic=[" + topic + ']' +
                    ", partition=[" + partition + "]" +
                    ", lag=[" + lag + "]" +
                    "]";
        }
    }
}
