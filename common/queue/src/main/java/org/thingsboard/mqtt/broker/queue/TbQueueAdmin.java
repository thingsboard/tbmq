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
package org.thingsboard.mqtt.broker.queue;

import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.common.Node;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.queue.KafkaBroker;
import org.thingsboard.mqtt.broker.common.data.queue.KafkaConsumerGroup;
import org.thingsboard.mqtt.broker.common.data.queue.KafkaTopic;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public interface TbQueueAdmin {

    void createTopicIfNotExists(String topic, Map<String, String> topicConfigs);

    void createTopic(String topic, Map<String, String> topicConfigs);

    void deleteTopic(String topic, BasicCallback callback);

    void deleteConsumerGroups(Collection<String> consumerGroups);

    void deleteConsumerGroup(String groupId) throws ExecutionException, InterruptedException, TimeoutException;

    int getNumberOfPartitions(String topic);

    Collection<Node> getNodes() throws Exception;

    @Deprecated(forRemoval = true, since = "2.3")
    PageData<KafkaBroker> getClusterInfo();

    CompletableFuture<PageData<KafkaBroker>> getClusterInfoAsync();

    @Deprecated(forRemoval = true, since = "2.3")
    PageData<KafkaTopic> getTopics(PageLink pageLink);

    CompletableFuture<PageData<KafkaTopic>> getTopicsAsync(PageLink pageLink);

    @Deprecated(forRemoval = true, since = "2.3")
    PageData<KafkaConsumerGroup> getConsumerGroups(PageLink pageLink);

    CompletableFuture<PageData<KafkaConsumerGroup>> getConsumerGroupsAsync(PageLink pageLink);

    void deleteOldConsumerGroups(String consumerGroupPrefix, String serviceId, long currentCgSuffix);

    ListConsumerGroupOffsetsResult listConsumerGroupOffsets(String groupId);
}
