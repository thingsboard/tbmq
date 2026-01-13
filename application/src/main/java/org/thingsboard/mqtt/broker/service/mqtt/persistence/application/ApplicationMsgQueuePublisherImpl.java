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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.application;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;
import org.thingsboard.mqtt.broker.gen.queue.PublishMsgProto;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.ApplicationPersistenceMsgQueueFactory;
import org.thingsboard.mqtt.broker.queue.publish.TbPublishServiceImpl;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.util.ApplicationClientHelperService;
import org.thingsboard.mqtt.broker.service.processing.PublishMsgCallback;

import java.util.concurrent.ExecutorService;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationMsgQueuePublisherImpl implements ApplicationMsgQueuePublisher {

    private final ClientLogger clientLogger;
    private final ApplicationPersistenceMsgQueueFactory applicationPersistenceMsgQueueFactory;
    private final ServiceInfoProvider serviceInfoProvider;
    private final ApplicationClientHelperService appClientHelperService;

    private TbPublishServiceImpl<PublishMsgProto> publisher;
    private TbPublishServiceImpl<PublishMsgProto> sharedSubsPublisher;

    @Value("${mqtt.handler.app_msg_callback_threads:2}")
    private int threadsCount;
    @Value("${queue.application-persisted-msg.client-id-validation:true}")
    private boolean validateClientId;
    @Value("${queue.application-persisted-msg.shared-topic-validation:true}")
    private boolean validateSharedTopicFilter;

    private ExecutorService callbackProcessor;

    @PostConstruct
    public void init() {
        callbackProcessor = ThingsBoardExecutors.initExecutorService(threadsCount, "app-msg-callback-processor");
        publisher = TbPublishServiceImpl.<PublishMsgProto>builder()
                .queueName("applicationMsg")
                .producer(applicationPersistenceMsgQueueFactory.createProducer(serviceInfoProvider.getServiceId()))
                .partition(0)
                .build();
        publisher.init();
        sharedSubsPublisher = TbPublishServiceImpl.<PublishMsgProto>builder()
                .queueName("applicationSharedSubsMsg")
                .producer(applicationPersistenceMsgQueueFactory.createSharedSubsProducer(serviceInfoProvider.getServiceId()))
                .build();
        sharedSubsPublisher.init();
    }

    @Override
    public void sendMsg(String clientId, TbProtoQueueMsg<PublishMsgProto> queueMsg, PublishMsgCallback callback) {
        clientLogger.logEventWithDetails(clientId, getClass(), ctx -> ctx
                .msg("Persisting APPLICATION msg in queue")
                .kv("topic", queueMsg.getValue().getTopicName())
        );
        String clientQueueTopic = appClientHelperService.getAppTopic(clientId, validateClientId);
        publisher.send(queueMsg,
                new TbQueueCallback() {
                    @Override
                    public void onSuccess(TbQueueMsgMetadata metadata) {
                        callbackProcessor.submit(() -> {
                            clientLogger.logEventWithDetails(clientId, ApplicationMsgQueuePublisherImpl.class, ctx -> ctx
                                    .msg("APPLICATION msg acknowledged")
                                    .kv("topic", queueMsg.getValue().getTopicName())
                            );
                            callback.onSuccess();
                        });
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        callbackProcessor.submit(() -> {
                            log.error("[{}] Failed to send publish msg to the queue for MQTT topic {}.",
                                    clientId, queueMsg.getValue().getTopicName(), t);
                            callback.onFailure(t);
                        });
                    }
                },
                clientQueueTopic);
    }

    @Override
    public void sendMsgToSharedTopic(String sharedTopic, TbProtoQueueMsg<PublishMsgProto> queueMsg, PublishMsgCallback callback) {
        sharedSubsPublisher.send(queueMsg,
                new TbQueueCallback() {
                    @Override
                    public void onSuccess(TbQueueMsgMetadata metadata) {
                        callbackProcessor.submit(callback::onSuccess);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        callbackProcessor.submit(() -> {
                            log.error("[{}] Failed to send publish msg to the shared topic queue.",
                                    sharedTopic, t);
                            callback.onFailure(t);
                        });
                    }
                },
                appClientHelperService.getSharedAppTopic(sharedTopic, validateSharedTopicFilter));
    }

    @PreDestroy
    public void destroy() {
        publisher.destroy();
        sharedSubsPublisher.destroy();
        if (callbackProcessor != null) {
            ThingsBoardExecutors.shutdownAndAwaitTermination(callbackProcessor, "Application queue callback");
        }
    }
}
