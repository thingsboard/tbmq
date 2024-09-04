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

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.admin.DeleteTopicsResult;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.admin.DescribeConsumerGroupsResult;
import org.apache.kafka.clients.admin.DescribeLogDirsResult;
import org.apache.kafka.clients.admin.LogDirDescription;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.ReplicaInfo;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.ConsumerGroupState;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.TopicExistsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.queue.KafkaBroker;
import org.thingsboard.mqtt.broker.common.data.queue.KafkaConsumerGroup;
import org.thingsboard.mqtt.broker.common.data.queue.KafkaConsumerGroupState;
import org.thingsboard.mqtt.broker.common.data.queue.KafkaTopic;
import org.thingsboard.mqtt.broker.common.util.BrokerConstants;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.queue.constants.QueueConstants;
import org.thingsboard.mqtt.broker.queue.kafka.settings.HomePageConsumerKafkaSettings;
import org.thingsboard.mqtt.broker.queue.kafka.settings.TbKafkaAdminSettings;
import org.thingsboard.mqtt.broker.queue.kafka.settings.TbKafkaConsumerSettings;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class TbKafkaAdmin implements TbQueueAdmin {

    @Value("${queue.kafka.enable-topic-deletion:true}")
    private boolean enableTopicDeletion;
    @Value("${queue.kafka.kafka-prefix:}")
    private String kafkaPrefix;
    @Value("${queue.kafka.client-session-event-response.topic-prefix}")
    private String clientSessionEventRespTopicPrefix;

    private final AdminClient client;
    private final Set<String> topics = ConcurrentHashMap.newKeySet();
    private final Consumer<String, byte[]> consumer;
    private final Duration timeoutDuration;

    public TbKafkaAdmin(TbKafkaAdminSettings adminSettings, TbKafkaConsumerSettings consumerSettings, HomePageConsumerKafkaSettings homePageConsumerKafkaSettings) {
        client = AdminClient.create(adminSettings.toProps());
        deleteOldConsumerGroups();
        try {
            topics.addAll(client.listTopics().names().get());
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to get all topics.", e);
        }
        this.consumer = createConsumer(consumerSettings, homePageConsumerKafkaSettings);
        this.timeoutDuration = Duration.ofMillis(homePageConsumerKafkaSettings.getKafkaResponseTimeoutMs());
    }

    private Consumer<String, byte[]> createConsumer(TbKafkaConsumerSettings consumerSettings, HomePageConsumerKafkaSettings homePageConsumerKafkaSettings) {
        Properties consumerProps = consumerSettings.toProps("kafka_admin_home_page", homePageConsumerKafkaSettings.getConsumerProperties());
        consumerProps.put(ConsumerConfig.CLIENT_ID_CONFIG, kafkaPrefix + "home-page-client");
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaPrefix + "home-page-client-group");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        return new KafkaConsumer<>(consumerProps);
    }

    @Override
    public void createTopicIfNotExists(String topic, Map<String, String> topicConfigs) {
        if (!topics.contains(topic)) {
            createTopic(topic, topicConfigs);
        }
    }

    @Override
    public void createTopic(String topic, Map<String, String> topicConfigs) {
        Map<String, String> configs = new HashMap<>(topicConfigs);
        if (log.isDebugEnabled()) {
            log.debug("[{}] Creating topic", topic);
        }
        if (log.isTraceEnabled()) {
            log.trace("Topic configs - {}.", configs);
        }
        try {
            NewTopic newTopic = new NewTopic(topic, extractPartitionsNumber(configs), extractReplicationFactor(configs)).configs(configs);
            client.createTopics(Collections.singletonList(newTopic)).values().get(topic).get();
            topics.add(topic);
        } catch (ExecutionException ee) {
            if (ee.getCause() instanceof TopicExistsException) {
                topics.add(topic);
            } else {
                log.warn("[{}] Failed to create topic", topic, ee);
                throw new RuntimeException(ee);
            }
        } catch (InterruptedException ie) {
            log.warn("[{}] Creating of topic was interrupted.", topic);
        } catch (Exception e) {
            log.warn("[{}] Failed to create topic", topic, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void deleteTopic(String topic, BasicCallback callback) {
        if (!enableTopicDeletion) {
            if (log.isDebugEnabled()) {
                log.debug("Ignoring deletion of topic {}", topic);
            }
            return;
        }
        if (log.isDebugEnabled()) {
            log.debug("[{}] Deleting topic", topic);
        }
        DeleteTopicsResult result = client.deleteTopics(Collections.singletonList(topic));
        result.all().whenComplete((unused, throwable) -> {
            if (throwable == null) {
                callback.onSuccess();
            } else {
                callback.onFailure(throwable);
            }
        });
        if (result.topicNameValues().containsKey(topic)) {
            topics.remove(topic);
        }
    }

    @Override
    public void deleteConsumerGroups(Collection<String> consumerGroups) {
        if (log.isDebugEnabled()) {
            log.debug("Deleting Consumer Groups - {}", consumerGroups);
        }
        try {
            doDeleteConsumerGroups(consumerGroups);
        } catch (Exception e) {
            log.warn("Failed to delete consumer groups {}", consumerGroups, e);
        }
    }

    @Override
    public void deleteConsumerGroup(String groupId) {
        if (log.isTraceEnabled()) {
            log.trace("Executing deleteConsumerGroup {}", groupId);
        }
        try {
            doDeleteConsumerGroups(List.of(groupId));
        } catch (InterruptedException | ExecutionException e) {
            log.warn("Failed to delete Kafka consumer group {}", groupId, e);
            throw new RuntimeException(e);
        }
    }

    private void doDeleteConsumerGroups(Collection<String> consumerGroups) throws InterruptedException, ExecutionException {
        client.deleteConsumerGroups(consumerGroups).all().get();
    }

    @Override
    public int getNumberOfPartitions(String topic) {
        try {
            return client.describeTopics(Collections.singletonList(topic)).allTopicNames().get().get(topic).partitions().size();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public PageData<KafkaBroker> getClusterInfo() {
        try {
            DescribeClusterResult describeClusterResult = client.describeCluster();
            Map<Integer, Node> brokerNodes = describeClusterResultToNodes(describeClusterResult);
            DescribeLogDirsResult describeLogDirsResult = client.describeLogDirs(brokerNodes.keySet());
            Map<Integer, Map<String, LogDirDescription>> logDirDescriptionsPerBroker = describeLogDirsResult.allDescriptions().get();

            List<KafkaBroker> kafkaBrokers = new ArrayList<>();

            for (Map.Entry<Integer, Map<String, LogDirDescription>> entry : logDirDescriptionsPerBroker.entrySet()) {
                int brokerId = entry.getKey();
                long brokerTotalSize = 0L;
                for (LogDirDescription logDirDescription : entry.getValue().values()) {
                    for (ReplicaInfo replicaInfo : logDirDescription.replicaInfos().values()) {
                        brokerTotalSize += replicaInfo.size();
                    }
                }
                kafkaBrokers.add(new KafkaBroker(brokerId, brokerNodes.get(brokerId).host(), brokerTotalSize));
            }
            return new PageData<>(kafkaBrokers, 1, kafkaBrokers.size(), false);
        } catch (InterruptedException | ExecutionException e) {
            log.warn("Failed to get Kafka cluster info", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public PageData<KafkaTopic> getTopics(PageLink pageLink) {
        try {
            Map<String, KafkaTopic> kafkaTopicsMap = new HashMap<>();

            Set<String> topics = client.listTopics().names().get().stream().filter(topic -> topic.startsWith(kafkaPrefix)).collect(Collectors.toSet());
            Map<String, TopicDescription> topicDescriptionsMap = client.describeTopics(topics).allTopicNames().get();

            for (Map.Entry<String, TopicDescription> topicDescriptionEntry : topicDescriptionsMap.entrySet()) {
                String topic = topicDescriptionEntry.getKey();
                TopicDescription topicDescription = topicDescriptionEntry.getValue();
                kafkaTopicsMap.put(topic, createKafkaTopic(topic, topicDescription));
            }

            Map<Integer, Node> brokerNodes = describeClusterResultToNodes(client.describeCluster());
            DescribeLogDirsResult describeLogDirsResult = client.describeLogDirs(brokerNodes.keySet());
            Map<Integer, Map<String, LogDirDescription>> logDirDescriptionsPerBroker = describeLogDirsResult.allDescriptions().get();

            Map<String, Long> kafkaTopicSizesMap = new HashMap<>();

            for (Map.Entry<Integer, Map<String, LogDirDescription>> logDirDescriptionsEntry : logDirDescriptionsPerBroker.entrySet()) {
                for (LogDirDescription logDirDescription : logDirDescriptionsEntry.getValue().values()) {
                    for (Map.Entry<TopicPartition, ReplicaInfo> topicReplicaInfoEntry : logDirDescription.replicaInfos().entrySet()) {

                        String topic = topicReplicaInfoEntry.getKey().topic();
                        long size = topicReplicaInfoEntry.getValue().size();

                        kafkaTopicSizesMap.compute(topic, (s, currentSize) -> {
                            if (currentSize == null) {
                                currentSize = 0L;
                            }
                            return currentSize + size;
                        });
                    }
                }
            }

            for (Map.Entry<String, Long> kafkaTopicSizeEntry : kafkaTopicSizesMap.entrySet()) {
                KafkaTopic kafkaTopic = kafkaTopicsMap.get(kafkaTopicSizeEntry.getKey());
                if (kafkaTopic != null) {
                    kafkaTopic.setSize(kafkaTopicSizeEntry.getValue());
                }
            }

            List<KafkaTopic> kafkaTopics;
            if (pageLink.getTextSearch() != null) {
                kafkaTopics = kafkaTopicsMap
                        .values()
                        .stream()
                        .filter(kafkaTopic -> kafkaTopic.getName().toLowerCase().contains(pageLink.getTextSearch().toLowerCase()))
                        .collect(Collectors.toList());
            } else {
                kafkaTopics = new ArrayList<>(kafkaTopicsMap.values());
            }

            List<KafkaTopic> data = kafkaTopics.stream()
                    .sorted(KafkaTopic.sorted(pageLink))
                    .skip((long) pageLink.getPage() * pageLink.getPageSize())
                    .limit(pageLink.getPageSize())
                    .collect(Collectors.toList());

            int totalPages = (int) Math.ceil((double) kafkaTopics.size() / pageLink.getPageSize());
            return new PageData<>(data,
                    totalPages,
                    kafkaTopics.size(),
                    pageLink.getPage() < totalPages - 1);
        } catch (Exception e) {
            log.warn("Failed to get Kafka topic infos", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<String> getBrokerServiceIds() {
        try {
            Set<String> topics = client.listTopics().names().get();
            return topics
                    .stream()
                    .filter(topic -> topic.startsWith(kafkaPrefix + clientSessionEventRespTopicPrefix))
                    .map(topic -> topic.replace(kafkaPrefix + clientSessionEventRespTopicPrefix + ".", ""))
                    .collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            log.warn("Failed to get broker names", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public PageData<KafkaConsumerGroup> getConsumerGroups(PageLink pageLink) {
        try {
            List<KafkaConsumerGroup> kafkaConsumerGroups = client.listConsumerGroups().all().get()
                    .stream()
                    .filter(consumerGroupListing -> consumerGroupListing.groupId().startsWith(kafkaPrefix))
                    .map(consumerGroupListing -> {
                        KafkaConsumerGroup kafkaConsumerGroup = new KafkaConsumerGroup();
                        kafkaConsumerGroup.setGroupId(consumerGroupListing.groupId());
                        kafkaConsumerGroup.setState(getKafkaConsumerGroupState(consumerGroupListing));
                        return kafkaConsumerGroup;
                    }).collect(Collectors.toList());

            List<String> groupIds = kafkaConsumerGroups.stream().map(KafkaConsumerGroup::getGroupId).collect(Collectors.toList());
            DescribeConsumerGroupsResult describeConsumerGroupsResult = client.describeConsumerGroups(groupIds);
            Map<String, ConsumerGroupDescription> consumerGroupDescriptionsMap = describeConsumerGroupsResult.all().get();

            for (KafkaConsumerGroup kafkaConsumerGroup : kafkaConsumerGroups) {
                String groupId = kafkaConsumerGroup.getGroupId();
                ConsumerGroupDescription consumerGroupDescription = consumerGroupDescriptionsMap.get(groupId);
                if (consumerGroupDescription != null) {
                    kafkaConsumerGroup.setMembers(consumerGroupDescription.members().size());
                }

                Map<TopicPartition, OffsetAndMetadata> groupOffsets = client.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata().get();
                Map<TopicPartition, Long> endOffsets = consumer.endOffsets(groupOffsets.keySet(), timeoutDuration);

                long lag = getConsumerGroupLag(groupOffsets, endOffsets);
                kafkaConsumerGroup.setLag(lag);
            }

            if (pageLink.getTextSearch() != null) {
                kafkaConsumerGroups = kafkaConsumerGroups
                        .stream()
                        .filter(kafkaTopic -> kafkaTopic.getGroupId().toLowerCase().contains(pageLink.getTextSearch().toLowerCase()))
                        .toList();
            }

            List<KafkaConsumerGroup> data = kafkaConsumerGroups.stream()
                    .sorted(KafkaConsumerGroup.sorted(pageLink))
                    .skip((long) pageLink.getPage() * pageLink.getPageSize())
                    .limit(pageLink.getPageSize())
                    .collect(Collectors.toList());

            int totalPages = (int) Math.ceil((double) kafkaConsumerGroups.size() / pageLink.getPageSize());
            return new PageData<>(data,
                    totalPages,
                    kafkaConsumerGroups.size(),
                    pageLink.getPage() < totalPages - 1);
        } catch (Exception e) {
            log.warn("Failed to get Kafka consumer groups", e);
            throw new RuntimeException(e);
        }
    }

    private KafkaConsumerGroupState getKafkaConsumerGroupState(ConsumerGroupListing consumerGroupListing) {
        ConsumerGroupState consumerGroupState = consumerGroupListing.state().orElse(ConsumerGroupState.UNKNOWN);
        return KafkaConsumerGroupState.toState(consumerGroupState.toString());
    }

    private long getConsumerGroupLag(Map<TopicPartition, OffsetAndMetadata> groupOffsets,
                                     Map<TopicPartition, Long> endOffsets) {
        long totalLag = 0L;
        for (Map.Entry<TopicPartition, OffsetAndMetadata> groupOffsetEntry : groupOffsets.entrySet()) {
            long endOffset = endOffsets.get(groupOffsetEntry.getKey());
            long committedOffset = groupOffsetEntry.getValue().offset();
            long lag = endOffset - committedOffset;
            totalLag += lag;
        }
        return totalLag;
    }

    private Map<Integer, Node> describeClusterResultToNodes(DescribeClusterResult describeClusterResult) throws InterruptedException, ExecutionException {
        return describeClusterResult.nodes().get().stream().collect(Collectors.toMap(Node::id, Function.identity()));
    }

    private KafkaTopic createKafkaTopic(String topic, TopicDescription topicDescription) {
        KafkaTopic kafkaTopic = new KafkaTopic();
        kafkaTopic.setName(topic);
        kafkaTopic.setPartitions(topicDescription.partitions().size());
        kafkaTopic.setReplicationFactor(topicDescription.partitions().get(0).replicas().size());
        return kafkaTopic;
    }

    private int extractPartitionsNumber(Map<String, String> topicConfigs) {
        String numPartitionsStr = topicConfigs.get(QueueConstants.PARTITIONS);
        if (numPartitionsStr != null) {
            topicConfigs.remove(QueueConstants.PARTITIONS);
            return Integer.parseInt(numPartitionsStr);
        } else {
            return 1;
        }
    }

    private short extractReplicationFactor(Map<String, String> topicConfigs) {
        String replicationFactorStr = topicConfigs.get(QueueConstants.REPLICATION_FACTOR);
        if (replicationFactorStr != null) {
            topicConfigs.remove(QueueConstants.REPLICATION_FACTOR);
            return Short.parseShort(replicationFactorStr);
        } else {
            return 1;
        }
    }

    private void deleteOldConsumerGroups() {
        long start = System.nanoTime();
        KafkaFuture<Collection<ConsumerGroupListing>> allCgsFuture = client.listConsumerGroups().all();
        allCgsFuture.whenComplete((consumerGroupListings, throwable) -> {
            if (throwable == null) {
                List<String> groupIdsToDelete = consumerGroupListings
                        .stream()
                        .filter(cg -> cg.state().orElse(ConsumerGroupState.UNKNOWN).equals(ConsumerGroupState.EMPTY))
                        .map(ConsumerGroupListing::groupId)
                        .filter(this::isConsumerGroupToDelete)
                        .toList();

                if (log.isDebugEnabled()) {
                    log.debug("Found {} old consumer groups to be deleted: {}!", groupIdsToDelete.size(), groupIdsToDelete);
                }
                KafkaFuture<Void> deleteCgsFuture = client.deleteConsumerGroups(groupIdsToDelete).all();
                deleteCgsFuture.whenComplete((unused, deleteThrowable) -> {
                    if (deleteThrowable == null) {
                        long end = System.nanoTime();
                        if (log.isDebugEnabled()) {
                            log.debug("Deletion processing of old consumer groups took {} nanos", end - start);
                        }
                    } else {
                        log.warn("Failed to delete old consumer groups!", deleteThrowable);
                    }
                });
            } else {
                log.warn("Failed to get old consumer groups!", throwable);
            }
        });
    }

    private boolean isConsumerGroupToDelete(String consumerGroupId) {
        if (consumerGroupId == null) {
            throw new IllegalArgumentException("Consumer group ID cannot be null");
        }
        return BrokerConstants.CG_TO_DELETE_PREFIXES.stream().map(prefix -> kafkaPrefix + prefix).anyMatch(consumerGroupId::startsWith);
    }

    @PreDestroy
    public void destroy() {
        if (client != null) {
            client.close();
        }
    }
}
