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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.application.topic;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.ApplicationSharedSubscription;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.common.data.util.CallbackUtil;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.queue.constants.QueueConstants;
import org.thingsboard.mqtt.broker.queue.provider.ApplicationPersistenceMsgQueueFactory;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.util.MqttApplicationClientUtil;

import java.util.Collections;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationTopicServiceImpl implements ApplicationTopicService {
    private final TbQueueAdmin queueAdmin;
    private final ApplicationPersistenceMsgQueueFactory applicationPersistenceMsgQueueFactory;

    @Override
    public void createTopic(String clientId) {
        log.debug("[{}] Creating APPLICATION topic", clientId);
        String clientTopic = MqttApplicationClientUtil.getTopic(clientId);
        queueAdmin.createTopic(clientTopic, applicationPersistenceMsgQueueFactory.getTopicConfigs());
    }

    @Override
    public void createSharedTopic(ApplicationSharedSubscription subscription) {
        String topic = subscription.getTopic();
        log.debug("[{}] Creating shared APPLICATION topic", topic);

        final var topicToCreate = MqttApplicationClientUtil.getKafkaTopic(topic);

        Map<String, String> topicConfigs = applicationPersistenceMsgQueueFactory.getTopicConfigs();
        topicConfigs.put(QueueConstants.PARTITIONS, String.valueOf(subscription.getPartitions()));
        queueAdmin.createTopic(topicToCreate, topicConfigs);
    }

    @Override
    public void deleteTopic(String clientId, BasicCallback callback) {
        log.debug("[{}] Deleting APPLICATION topic", clientId);
        String clientTopic = MqttApplicationClientUtil.getTopic(clientId);
        queueAdmin.deleteTopic(clientTopic, callback);
        String consumerGroup = MqttApplicationClientUtil.getConsumerGroup(clientId);
        queueAdmin.deleteConsumerGroups(Collections.singleton(consumerGroup));
    }

    @Override
    public void deleteSharedTopic(ApplicationSharedSubscription subscription) {
        String topic = subscription.getTopic();
        log.debug("[{}] Deleting shared APPLICATION topic", topic);

        final var topicToDelete = MqttApplicationClientUtil.getKafkaTopic(topic);

        BasicCallback callback = CallbackUtil.createCallback(
                () -> log.info("[{}] Deleted Kafka topic successfully", topicToDelete),
                throwable -> log.error("[{}] Failed to delete Kafka topic", topicToDelete, throwable));
        queueAdmin.deleteTopic(topicToDelete, callback);
    }
}
