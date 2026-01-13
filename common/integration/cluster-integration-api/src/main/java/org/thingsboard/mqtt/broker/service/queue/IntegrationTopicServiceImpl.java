/**
 * Copyright © 2016-2026 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.queue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.GroupIdNotFoundException;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.queue.provider.integration.IntegrationMsgQueueProvider;
import org.thingsboard.mqtt.broker.service.util.IntegrationHelperService;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntegrationTopicServiceImpl implements IntegrationTopicService {

    private final TbQueueAdmin queueAdmin;
    private final IntegrationMsgQueueProvider integrationMsgQueueProvider;
    private final IntegrationHelperService integrationHelperService;

    @Override
    public String createTopic(String integrationId) {
        log.debug("[{}] Creating IE msg topic", integrationId);
        String ieTopic = integrationHelperService.getIntegrationTopic(integrationId);
        queueAdmin.createTopic(ieTopic, integrationMsgQueueProvider.getTopicConfigs());
        return ieTopic;
    }

    @Override
    public void deleteTopic(String integrationId, BasicCallback callback) {
        log.debug("[{}] Deleting IE msg topic", integrationId);
        deleteConsumerGroup(integrationId);
        String ieTopic = integrationHelperService.getIntegrationTopic(integrationId);
        queueAdmin.deleteTopic(ieTopic, callback);
    }

    @Override
    public void deleteConsumerGroup(String integrationId) {
        String consumerGroup = getConsumerGroup(integrationId);
        try {
            queueAdmin.deleteConsumerGroup(consumerGroup);
        } catch (Exception e) {
            if (!(e.getCause() instanceof GroupIdNotFoundException)) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public String getConsumerGroup(String integrationId) {
        return integrationHelperService.getIntegrationConsumerGroup(integrationId);
    }

}
