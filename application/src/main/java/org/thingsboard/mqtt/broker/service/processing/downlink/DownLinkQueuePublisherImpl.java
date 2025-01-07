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
package org.thingsboard.mqtt.broker.service.processing.downlink;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.DownLinkBasicPublishMsgQueueFactory;
import org.thingsboard.mqtt.broker.queue.provider.DownLinkPersistentPublishMsgQueueFactory;
import org.thingsboard.mqtt.broker.queue.publish.TbPublishServiceImpl;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.util.MqttPropertiesUtil;

import java.util.concurrent.ExecutorService;

@Slf4j
@Service
@RequiredArgsConstructor
class DownLinkQueuePublisherImpl implements DownLinkQueuePublisher {

    private final ServiceInfoProvider serviceInfoProvider;
    private final DownLinkBasicPublishMsgQueueFactory downLinkBasicPublishMsgQueueFactory;
    private final DownLinkPersistentPublishMsgQueueFactory downLinkPersistentPublishMsgQueueFactory;
    private final DownLinkPublisherHelper downLinkPublisherHelper;
    private final ClientLogger clientLogger;

    private final boolean isTraceEnabled = log.isTraceEnabled();

    @Value("${mqtt.handler.downlink_msg_callback_threads:2}")
    private int threadsCount;

    private TbPublishServiceImpl<QueueProtos.ClientPublishMsgProto> basicPublisher;
    private TbPublishServiceImpl<QueueProtos.DevicePublishMsgProto> persistentPublisher;

    private ExecutorService callbackProcessor;

    @PostConstruct
    public void init() {
        this.callbackProcessor = ThingsBoardExecutors.initExecutorService(threadsCount, "downlink-msg-callback-processor");
        this.basicPublisher = TbPublishServiceImpl.<QueueProtos.ClientPublishMsgProto>builder()
                .queueName("basicDownlink")
                .producer(downLinkBasicPublishMsgQueueFactory.createProducer(serviceInfoProvider.getServiceId()))
                .build();
        this.basicPublisher.init();
        this.persistentPublisher = TbPublishServiceImpl.<QueueProtos.DevicePublishMsgProto>builder()
                .queueName("persistentDownlink")
                .producer(downLinkPersistentPublishMsgQueueFactory.createProducer(serviceInfoProvider.getServiceId()))
                .build();
        this.persistentPublisher.init();
    }

    @Override
    public void publishBasicMsg(String targetServiceId, String clientId, QueueProtos.PublishMsgProto msg) {
        String topic = downLinkPublisherHelper.getBasicDownLinkServiceTopic(targetServiceId);
        QueueProtos.ClientPublishMsgProto clientPublishMsgProto = QueueProtos.ClientPublishMsgProto.newBuilder()
                .setClientId(clientId)
                .setPublishMsg(msg)
                .build();
        clientLogger.logEvent(clientId, this.getClass(), "Putting msg to basic down-link queue");
        basicPublisher.send(new TbProtoQueueMsg<>(msg.getTopicName(), clientPublishMsgProto),
                new TbQueueCallback() {
                    @Override
                    public void onSuccess(TbQueueMsgMetadata metadata) {
                        callbackProcessor.submit(() -> {
                            clientLogger.logEvent(clientId, this.getClass(), "Sent msg to basic down-link queue");
                            if (isTraceEnabled) {
                                log.trace("[{}] Successfully published BASIC msg to {} service.", clientId, targetServiceId);
                            }
                        });
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        callbackProcessor.submit(() ->
                                log.warn("[{}] Failed to publish BASIC msg to {} service.", clientId, targetServiceId, t));
                    }
                },
                topic
        );
    }

    @Override
    public void publishPersistentMsg(String targetServiceId, String clientId, DevicePublishMsg devicePublishMsg) {
        String topic = downLinkPublisherHelper.getPersistentDownLinkServiceTopic(targetServiceId);
        clientLogger.logEvent(clientId, this.getClass(), "Putting msg to persistent down-link queue");
        QueueProtos.DevicePublishMsgProto msg = ProtoConverter.toDevicePublishMsgProto(devicePublishMsg);
        persistentPublisher.send(new TbProtoQueueMsg<>(clientId, msg, MqttPropertiesUtil.createHeaders(devicePublishMsg)),
                new TbQueueCallback() {
                    @Override
                    public void onSuccess(TbQueueMsgMetadata metadata) {
                        callbackProcessor.submit(() -> {
                            clientLogger.logEvent(clientId, this.getClass(), "Sent msg to persistent down-link queue");
                            if (isTraceEnabled) {
                                log.trace("[{}] Successfully published PERSISTENT msg to {} service.", clientId, targetServiceId);
                            }
                        });
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        callbackProcessor.submit(() ->
                                log.warn("[{}] Failed to publish PERSISTENT msg to {} service.", clientId, targetServiceId, t));
                    }
                },
                topic
        );
    }

    @PreDestroy
    public void destroy() {
        if (basicPublisher != null) {
            basicPublisher.destroy();
        }
        if (persistentPublisher != null) {
            persistentPublisher.destroy();
        }
        if (callbackProcessor != null) {
            ThingsBoardExecutors.shutdownAndAwaitTermination(callbackProcessor, "Downlink queues callback");
        }
    }
}
