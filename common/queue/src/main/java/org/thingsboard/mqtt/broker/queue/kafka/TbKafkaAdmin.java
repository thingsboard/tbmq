/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.admin.DeleteTopicsResult;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.admin.DescribeConsumerGroupsResult;
import org.apache.kafka.clients.admin.DescribeLogDirsResult;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.ListConsumerGroupsOptions;
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
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.queue.KafkaBroker;
import org.thingsboard.mqtt.broker.common.data.queue.KafkaConsumerGroup;
import org.thingsboard.mqtt.broker.common.data.queue.KafkaConsumerGroupState;
import org.thingsboard.mqtt.broker.common.data.queue.KafkaTopic;
import org.thingsboard.mqtt.broker.common.util.CachedValue;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class TbKafkaAdmin implements TbQueueAdmin {

    private final TbKafkaAdminSettings adminSettings;
    private final Admin client;
    private final CachedValue<Set<String>> topics;
    private final Consumer<String, byte[]> consumer;
    private final Duration timeoutDuration;

    public TbKafkaAdmin(TbKafkaAdminSettings adminSettings, TbKafkaConsumerSettings consumerSettings, HomePageConsumerKafkaSettings homePageConsumerKafkaSettings) {
        this.adminSettings = adminSettings;
        this.client = Admin.create(adminSettings.toProps());
        this.topics = new CachedValue<>(() -> {
            Set<String> topics = ConcurrentHashMap.newKeySet();
            topics.addAll(listTopics());
            return topics;
        }, adminSettings.getTopicsCacheTtlMs());
        this.consumer = createConsumer(consumerSettings, homePageConsumerKafkaSettings);
        this.timeoutDuration = Duration.ofMillis(homePageConsumerKafkaSettings.getKafkaResponseTimeoutMs());
    }

    private Set<String> listTopics() {
        try {
            Set<String> topics = client.listTopics().names().get(adminSettings.getKafkaAdminCommandTimeout(), TimeUnit.SECONDS);
            log.trace("Listed topics: {}", topics);
            return topics;
        } catch (Exception e) {
            log.error("Failed to get all topics.", e);
            return Collections.emptySet();
        }
    }

    private Set<String> getTopics() {
        return topics.get();
    }

    private void invalidateTopics() {
        topics.invalidate();
    }

    private Consumer<String, byte[]> createConsumer(TbKafkaConsumerSettings consumerSettings, HomePageConsumerKafkaSettings homePageConsumerKafkaSettings) {
        Properties consumerProps = consumerSettings.toProps("kafka_admin_home_page", homePageConsumerKafkaSettings.getConsumerProperties());
        var kafkaPrefix = adminSettings.getKafkaPrefix();
        consumerProps.put(ConsumerConfig.CLIENT_ID_CONFIG, kafkaPrefix + "home-page-client");
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaPrefix + "home-page-client-group");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        return new KafkaConsumer<>(consumerProps);
    }

    @Override
    public void createTopicIfNotExists(String topic, Map<String, String> topicConfigs) {
        if (!getTopics().contains(topic)) {
            createTopic(topic, topicConfigs);
        }
    }

    @Override
    public void createTopic(String topic, Map<String, String> topicConfigs) {
        Map<String, String> configs = new HashMap<>(topicConfigs);
        log.debug("[{}] Creating topic", topic);
        log.trace("Topic configs - {}.", configs);
        try {
            NewTopic newTopic = new NewTopic(topic, extractPartitionsNumber(configs), extractReplicationFactor(configs)).configs(configs);
            client.createTopics(Collections.singletonList(newTopic)).values().get(topic).get(adminSettings.getKafkaAdminCommandTimeout(), TimeUnit.SECONDS);
            invalidateTopics();
        } catch (ExecutionException ee) {
            if (ee.getCause() instanceof TopicExistsException) {
                //do nothing
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
        if (!adminSettings.isEnableTopicDeletion()) {
            log.debug("Ignoring deletion of topic {}", topic);
            return;
        }
        log.debug("[{}] Deleting topic", topic);
        DeleteTopicsResult result = client.deleteTopics(Collections.singletonList(topic));
        result.all().whenComplete((unused, throwable) -> {
            if (throwable == null) {
                invalidateTopics();
                callback.onSuccess();
            } else {
                callback.onFailure(throwable);
            }
        });
    }

    @Override
    public void deleteConsumerGroups(Collection<String> consumerGroups) {
        log.debug("Deleting Consumer Groups - {}", consumerGroups);
        try {
            doDeleteConsumerGroups(consumerGroups);
        } catch (Exception e) {
            log.warn("Failed to delete consumer groups {}", consumerGroups, e);
        }
    }

    @Override
    public void deleteConsumerGroup(String groupId) throws ExecutionException, InterruptedException, TimeoutException {
        log.trace("Executing deleteConsumerGroup {}", groupId);
        doDeleteConsumerGroups(List.of(groupId));
    }

    private void doDeleteConsumerGroups(Collection<String> consumerGroups) throws InterruptedException, ExecutionException, TimeoutException {
        client.deleteConsumerGroups(consumerGroups).all().get(adminSettings.getKafkaAdminCommandTimeout(), TimeUnit.SECONDS);
    }

    @Override
    public int getNumberOfPartitions(String topic) {
        try {
            return client.describeTopics(Collections.singletonList(topic))
                    .allTopicNames()
                    .get(adminSettings.getKafkaAdminCommandTimeout(), TimeUnit.SECONDS)
                    .get(topic)
                    .partitions()
                    .size();
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Collection<Node> getNodes() throws Exception {
        return client.describeCluster().nodes().get(adminSettings.getKafkaAdminCommandTimeout(), TimeUnit.SECONDS);
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

            Set<String> topics = client.listTopics().names().get().stream().filter(topic -> topic.startsWith(adminSettings.getKafkaPrefix())).collect(Collectors.toSet());
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

            return PageData.of(data, kafkaTopics.size(), pageLink);
        } catch (Exception e) {
            log.warn("Failed to get Kafka topic infos", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public PageData<KafkaConsumerGroup> getConsumerGroups(PageLink pageLink) {
        try {
            List<KafkaConsumerGroup> kafkaConsumerGroups = client.listConsumerGroups().all().get()
                    .stream()
                    .filter(consumerGroupListing -> consumerGroupListing.groupId().startsWith(adminSettings.getKafkaPrefix()))
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

            return PageData.of(data, kafkaConsumerGroups.size(), pageLink);
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

    //todo: maybe execute this periodically using scheduler. During kill of the node there can be at least 2 groups (downlinks)
    // that will not be deleted if the state is Stable (since consumers could not gracefully leave the groups)
    @Override
    public void deleteOldConsumerGroups(String consumerGroupPrefix, String serviceId, long currentCgSuffix) {
        long start = System.nanoTime();

        ListConsumerGroupsOptions emptyConsumerGroups = new ListConsumerGroupsOptions()
                .inStates(Set.of(ConsumerGroupState.EMPTY));

        KafkaFuture<Collection<ConsumerGroupListing>> emptyCgsFuture = client.listConsumerGroups(emptyConsumerGroups).all();
        emptyCgsFuture.whenComplete((consumerGroupListings, throwable) -> {
            if (throwable != null) {
                log.warn("Failed to get old consumer groups!", throwable);
                return;
            }
            List<String> groupIdsToDelete = consumerGroupListings
                    .stream()
                    .map(ConsumerGroupListing::groupId)
                    .filter(consumerGroupId -> isConsumerGroupToDelete(consumerGroupPrefix, serviceId, currentCgSuffix, consumerGroupId))
                    .toList();

            if (CollectionUtils.isEmpty(groupIdsToDelete)) {
                log.debug("No old consumer groups found for deletion.");
                return;
            }

            log.debug("Found {} old consumer group(s) to be deleted: {}!", groupIdsToDelete.size(), groupIdsToDelete);
            KafkaFuture<Void> deleteCgsFuture = client.deleteConsumerGroups(groupIdsToDelete).all();
            deleteCgsFuture.whenComplete((unused, deleteThrowable) -> {
                if (deleteThrowable == null) {
                    long end = System.nanoTime();
                    log.debug("[{}] Deletion processing of old consumer group(s) took {} nanos", groupIdsToDelete, end - start);
                } else {
                    log.warn("Failed to delete old consumer groups!", deleteThrowable);
                }
            });
        });
    }

    @Override
    public ListConsumerGroupOffsetsResult listConsumerGroupOffsets(String groupId) {
        return client.listConsumerGroupOffsets(groupId);
    }

    private boolean isConsumerGroupToDelete(String consumerGroupPrefix, String serviceId, long currentCgSuffix, String consumerGroupId) {
        String prefix = getPrefix(consumerGroupPrefix);
        String cgSuffix = Long.toString(currentCgSuffix);
        return consumerGroupId.startsWith(prefix) && consumerGroupId.contains(serviceId) && !consumerGroupId.contains(cgSuffix);
    }

    private String getPrefix(String consumerGroupPrefix) {
        var kafkaPrefix = adminSettings.getKafkaPrefix();
        return kafkaPrefix != null ? kafkaPrefix + consumerGroupPrefix : consumerGroupPrefix;
    }

    @PreDestroy
    public void destroy() {
        if (client != null) {
            client.close();
        }
    }
}
