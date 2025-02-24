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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.integration;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.gen.integration.PublishIntegrationMsgProto;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.integration.IntegrationMsgQueueProvider;
import org.thingsboard.mqtt.broker.queue.publish.TbPublishServiceImpl;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.processing.PublishMsgCallback;
import org.thingsboard.mqtt.broker.service.util.IntegrationHelperService;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntegrationMsgQueuePublisherImpl implements IntegrationMsgQueuePublisher {

    private final ClientLogger clientLogger;
    private final IntegrationMsgQueueProvider msgQueueProvider;
    private final IntegrationHelperService integrationHelperService;

    private final boolean isTraceEnabled = log.isTraceEnabled();

    private TbPublishServiceImpl<PublishIntegrationMsgProto> publisher;

    @PostConstruct
    public void init() {
        this.publisher = TbPublishServiceImpl.<PublishIntegrationMsgProto>builder()
                .queueName("ieMsg")
                .producer(msgQueueProvider.getIeMsgProducer())
                .partition(0)
                .build();
        this.publisher.init();
    }

    @PreDestroy
    public void destroy() {
        this.publisher.destroy();
    }

    @Override
    public void sendMsg(String integrationId, TbProtoQueueMsg<PublishIntegrationMsgProto> queueMsg, PublishMsgCallback callback) {
        clientLogger.logEvent(integrationId, this.getClass(), "Start waiting for IE msg to be persisted");
        String ieQueueTopic = integrationHelperService.getIntegrationTopic(integrationId);
        this.publisher.send(queueMsg,
                new TbQueueCallback() {
                    @Override
                    public void onSuccess(TbQueueMsgMetadata metadata) {
                        clientLogger.logEvent(integrationId, this.getClass(), "Persisted msg in IE Queue");
                        if (isTraceEnabled) {
                            log.trace("[{}] Successfully sent publish msg to the ie queue.", integrationId);
                        }
                        callback.onSuccess();
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        log.error("[{}] Failed to send publish msg to the ie queue for MQTT topic {}.",
                                integrationId, queueMsg.getValue().getPublishMsgProto().getTopicName(), t);
                        callback.onFailure(t);
                    }
                },
                ieQueueTopic);
    }

}
