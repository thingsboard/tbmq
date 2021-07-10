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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.application;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.ApplicationPersistenceMsgQueueFactory;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.util.MqttApplicationClientUtil;
import org.thingsboard.mqtt.broker.service.processing.PublishMsgCallback;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ApplicationMsgQueueServiceImpl implements ApplicationMsgQueueService {
    private final ExecutorService publishExecutor = Executors.newSingleThreadExecutor(ThingsBoardThreadFactory.forName("application-publish-thread"));
    private final BlockingQueue<ApplicationPublishMsg> pendingMessagesQueue = new LinkedBlockingQueue<>();
    
    private final TbQueueProducer<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> applicationProducer;
    private final ClientLogger clientLogger;

    @Value("${queue.application-persisted-msg.publisher-thread-max-delay}")
    private long maxDelay;

    public ApplicationMsgQueueServiceImpl(ApplicationPersistenceMsgQueueFactory applicationPersistenceMsgQueueFactory, ServiceInfoProvider serviceInfoProvider, ClientLogger clientLogger, StatsManager statsManager) {
        this.applicationProducer = applicationPersistenceMsgQueueFactory.createProducer(serviceInfoProvider.getServiceId());
        this.clientLogger = clientLogger;
        statsManager.registerPendingApplicationPersistentMessages(pendingMessagesQueue);
    }
    
    @PostConstruct
    public void init() {
        publishExecutor.execute(this::publishApplicationMessages);
    }

    @Override
    public void sendMsg(String clientId, QueueProtos.PublishMsgProto msgProto, PublishMsgCallback callback) {
        clientLogger.logEvent(clientId, "Start waiting for APPLICATION msg to be persisted");
        pendingMessagesQueue.add(new ApplicationPublishMsg(clientId, msgProto, callback));
    }

    private void publishApplicationMessages() {
        while (!Thread.interrupted()) {
            ApplicationPublishMsg applicationPublishMsg;
            try {
                applicationPublishMsg = pendingMessagesQueue.poll(maxDelay, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                log.info("Queue polling was interrupted.");
                break;
            }
            if (applicationPublishMsg == null) {
                continue;
            }
            String clientId = applicationPublishMsg.getApplicationClientId();
            PublishMsgCallback callback = applicationPublishMsg.getCallback();
            QueueProtos.PublishMsgProto msgProto = applicationPublishMsg.getMsgProto();
            try {
                sendMsgToApplicationQueue(clientId, callback, msgProto);
            } catch (Exception e) {
                log.error("[{}] Failed to send msg to the APPLICATION queue", clientId, e);
                callback.onFailure(e);
            }
        }
    }

    private void sendMsgToApplicationQueue(String clientId, PublishMsgCallback callback, QueueProtos.PublishMsgProto msgProto) {
        String clientQueueTopic = MqttApplicationClientUtil.getTopic(clientId);
        clientLogger.logEvent(clientId, "Persisting msg in APPLICATION Queue");
        applicationProducer.send(clientQueueTopic, new TbProtoQueueMsg<>(msgProto.getTopicName(), msgProto),
                new TbQueueCallback() {
                    @Override
                    public void onSuccess(TbQueueMsgMetadata metadata) {
                        clientLogger.logEvent(clientId, "Persisted msg in APPLICATION Queue");
                        log.trace("[{}] Successfully sent publish msg to the queue.", clientId);
                        callback.onSuccess();
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        log.error("[{}] Failed to send publish msg to the queue for MQTT topic {}. Reason - {}.",
                                clientId, msgProto.getTopicName(), t.getMessage());
                        log.debug("Detailed error: ", t);
                        callback.onFailure(t);
                    }
                });
    }

    @PreDestroy
    public void destroy() {
        publishExecutor.shutdownNow();
        applicationProducer.stop();
    }
    
    
    @Getter
    @RequiredArgsConstructor
    private static class ApplicationPublishMsg {
        private final String applicationClientId;
        private final QueueProtos.PublishMsgProto msgProto;
        private final PublishMsgCallback callback;
    }
}
