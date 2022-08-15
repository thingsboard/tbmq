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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.ApplicationPersistenceMsgQueueFactory;
import org.thingsboard.mqtt.broker.queue.publish.TbPublishBlockingQueue;
import org.thingsboard.mqtt.broker.queue.stats.ProducerStatsManager;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.util.MqttApplicationClientUtil;
import org.thingsboard.mqtt.broker.service.processing.PublishMsgCallback;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationMsgQueuePublisherImpl implements ApplicationMsgQueuePublisher {
    private TbPublishBlockingQueue<QueueProtos.PublishMsgProto> publisherQueue;

    private final ClientLogger clientLogger;
    private final ApplicationPersistenceMsgQueueFactory applicationPersistenceMsgQueueFactory;
    private final ServiceInfoProvider serviceInfoProvider;
    private final ProducerStatsManager statsManager;

    @Value("${queue.application-persisted-msg.publisher-thread-max-delay}")
    private long maxDelay;

    @PostConstruct
    public void init() {
        this.publisherQueue = TbPublishBlockingQueue.<QueueProtos.PublishMsgProto>builder()
                .queueName("applicationMsg")
                .producer(applicationPersistenceMsgQueueFactory.createProducer(serviceInfoProvider.getServiceId()))
                .maxDelay(maxDelay)
                .statsManager(statsManager)
                .build();
        this.publisherQueue.init();
    }
    
    @Override
    public void sendMsg(String clientId, QueueProtos.PublishMsgProto msgProto, PublishMsgCallback callback) {
        clientLogger.logEvent(clientId, this.getClass(), "Start waiting for APPLICATION msg to be persisted");
        String clientQueueTopic = MqttApplicationClientUtil.getTopic(clientId);
        publisherQueue.add(new TbProtoQueueMsg<>(msgProto.getTopicName(), msgProto),
                new TbQueueCallback() {
                    @Override
                    public void onSuccess(TbQueueMsgMetadata metadata) {
                        clientLogger.logEvent(clientId, this.getClass(), "Persisted msg in APPLICATION Queue");
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
                },
                clientQueueTopic);
    }

    @Override
    public void sendMsgToSharedTopic(String sharedTopic, QueueProtos.PublishMsgProto msgProto, PublishMsgCallback callback) {
        publisherQueue.add(new TbProtoQueueMsg<>(msgProto),
                new TbQueueCallback() {
                    @Override
                    public void onSuccess(TbQueueMsgMetadata metadata) {
                        log.trace("[{}] Successfully sent publish msg to the shared topic queue.", sharedTopic);
                        callback.onSuccess();
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        log.error("[{}] Failed to send publish msg to the shared topic queue. Reason - {}.",
                                sharedTopic, t.getMessage());
                        log.debug("Detailed error: ", t);
                        callback.onFailure(t);
                    }
                },
                MqttApplicationClientUtil.getKafkaTopic(sharedTopic));
    }

    @PreDestroy
    public void destroy() {
        publisherQueue.destroy();
    }
}
