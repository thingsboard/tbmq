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

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DeleteConsumerGroupsResult;
import org.apache.kafka.clients.admin.DeleteTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.queue.constants.QueueConstants;
import org.thingsboard.mqtt.broker.queue.kafka.settings.TbKafkaAdminSettings;

import javax.annotation.PreDestroy;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

@Slf4j
@Component
public class TbKafkaAdmin implements TbQueueAdmin {
    @Value("${queue.kafka.enable-topic-deletion:true}")
    private boolean enableTopicDeletion;

    private final AdminClient client;
    private final Set<String> topics = ConcurrentHashMap.newKeySet();

    public TbKafkaAdmin(TbKafkaAdminSettings adminSettings) {
        client = AdminClient.create(adminSettings.toProps());

        try {
            topics.addAll(client.listTopics().names().get());
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to get all topics.", e);
        }
    }

    @Override
    public void createTopicIfNotExists(String topic, Map<String, String> topicConfigs) {
        // TODO: rethink this logic to work when multiple nodes have access to same topics
        if (!topics.contains(topic)) {
            createTopic(topic, topicConfigs);
        }
    }

    @Override
    public void createTopic(String topic, Map<String, String> topicConfigs) {
        log.debug("[{}] Creating topic", topic);
        log.trace("Topic configs - {}.", topicConfigs);
        try {
            NewTopic newTopic = new NewTopic(topic, extractPartitionsNumber(topicConfigs), extractReplicationFactor(topicConfigs)).configs(topicConfigs);
            client.createTopics(Collections.singletonList(newTopic)).values().get(topic).get();
            topics.add(topic);
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
    public void deleteTopic(String topic) {
        if (!enableTopicDeletion) {
            log.debug("Ignoring deletion of topic {}", topic);
            return;
        }
        log.debug("[{}] Deleting topic", topic);
        DeleteTopicsResult result = client.deleteTopics(Collections.singletonList(topic));
        if (result.values().containsKey(topic)) {
            topics.remove(topic);
        }
    }

    @Override
    public void deleteConsumerGroups(Collection<String> consumerGroups) {
        log.debug("Deleting Consumer Groups - {}", consumerGroups);
        try {
            DeleteConsumerGroupsResult result = client.deleteConsumerGroups(consumerGroups);
            result.all().get();
        } catch (Exception e) {
            log.warn("Failed to delete consumer groups {}. Exception - {}, reason - {}.", consumerGroups, e.getClass().getSimpleName(), e.getMessage());
        }
    }

    @Override
    public int getNumberOfPartitions(String topic) {
        try {
            return client.describeTopics(Collections.singletonList(topic)).all().get().get(topic).partitions().size();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
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
            return  1;
        }
    }

    @PreDestroy
    public void destroy() {
        if (client != null) {
            client.close();
        }
    }
}
