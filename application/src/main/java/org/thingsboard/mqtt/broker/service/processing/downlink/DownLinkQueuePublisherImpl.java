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
package org.thingsboard.mqtt.broker.service.processing.downlink;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.DownLinkPersistentPublishMsgQueueFactory;
import org.thingsboard.mqtt.broker.queue.provider.DownLinkBasicPublishMsgQueueFactory;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;

import javax.annotation.PostConstruct;

@Slf4j
@Service
@RequiredArgsConstructor
class DownLinkQueuePublisherImpl implements DownLinkQueuePublisher {
    private final ServiceInfoProvider serviceInfoProvider;
    private final DownLinkBasicPublishMsgQueueFactory downLinkBasicPublishMsgQueueFactory;
    private final DownLinkPersistentPublishMsgQueueFactory downLinkPersistentPublishMsgQueueFactory;
    private final DownLinkPublisherHelper downLinkPublisherHelper;
    private final ClientLogger clientLogger;

    private TbQueueProducer<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> basicProducer;
    private TbQueueProducer<TbProtoQueueMsg<QueueProtos.PersistedDevicePublishMsgProto>> persistedDeviceProducer;

    @PostConstruct
    public void init() {
        this.basicProducer = downLinkBasicPublishMsgQueueFactory.createProducer(serviceInfoProvider.getServiceId());
        this.persistedDeviceProducer = downLinkPersistentPublishMsgQueueFactory.createProducer(serviceInfoProvider.getServiceId());
    }

    // TODO: what to do if sending msg to Kafka fails?
    @Override
    public void publishBasicMsg(String targetServiceId, String clientId, QueueProtos.PublishMsgProto msg) {
        String topic = downLinkPublisherHelper.getBasicDownLinkServiceTopic(targetServiceId);
        clientLogger.logEvent(clientId, "Sending msg to basic down-link queue");
        basicProducer.send(topic,
                new TbProtoQueueMsg<>(clientId, msg),
                new TbQueueCallback() {
                    @Override
                    public void onSuccess(TbQueueMsgMetadata metadata) {
                        clientLogger.logEvent(clientId, "Sent msg to basic down-link queue");
                        log.trace("[{}] Successfully published BASIC msg to {} service.", clientId, targetServiceId);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        log.info("[{}] Failed to publish BASIC msg to {} service. Exception - {}, reason - {}.",
                                clientId, targetServiceId, t.getClass().getSimpleName(), t.getMessage());
                    }
                }
        );
    }

    @Override
    public void publishPersistentMsg(String targetServiceId, String clientId, QueueProtos.PersistedDevicePublishMsgProto msg) {
        String topic = downLinkPublisherHelper.getPersistentDownLinkServiceTopic(targetServiceId);
        clientLogger.logEvent(clientId, "Sending msg to persistent down-link queue");
        persistedDeviceProducer.send(topic,
                new TbProtoQueueMsg<>(clientId, msg),
                new TbQueueCallback() {
                    @Override
                    public void onSuccess(TbQueueMsgMetadata metadata) {
                        clientLogger.logEvent(clientId, "Sent msg to persistent down-link queue");
                        log.trace("[{}] Successfully published PERSISTENT msg to {} service.", clientId, targetServiceId);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        log.info("[{}] Failed to publish PERSISTENT msg to {} service. Exception - {}, reason - {}.",
                                clientId, targetServiceId, t.getClass().getSimpleName(), t.getMessage());
                    }
                }
        );
    }
}
