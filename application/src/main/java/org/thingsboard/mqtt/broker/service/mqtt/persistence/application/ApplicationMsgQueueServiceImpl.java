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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.ApplicationPersistenceMsgQueueFactory;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.util.MqttApplicationClientUtil;
import org.thingsboard.mqtt.broker.service.processing.PublishMsgCallback;

import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationMsgQueueServiceImpl implements ApplicationMsgQueueService {
    private final Map<String, TbQueueProducer<TbProtoQueueMsg<QueueProtos.PublishMsgProto>>> applicationProducers = new ConcurrentHashMap<>();

    private final ApplicationPersistenceMsgQueueFactory applicationPersistenceMsgQueueFactory;
    private final ServiceInfoProvider serviceInfoProvider;

    @Override
    public void sendMsg(String clientId, QueueProtos.PublishMsgProto msgProto, PublishMsgCallback callback) {
        String clientQueueTopic = MqttApplicationClientUtil.getTopic(clientId);
        TbQueueProducer<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> applicationProducer = applicationProducers.computeIfAbsent(clientId,
                id -> applicationPersistenceMsgQueueFactory.createProducer(clientQueueTopic, createProducerId(clientId)));
        applicationProducer.send(new TbProtoQueueMsg<>(msgProto.getTopicName(), msgProto),
                new TbQueueCallback() {
                    @Override
                    public void onSuccess(TbQueueMsgMetadata metadata) {
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

    private String createProducerId(String clientId) {
        return serviceInfoProvider.getServiceId() + "-" + clientId;
    }

    @Override
    public void clearQueueProducer(String clientId) {
        TbQueueProducer<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> removedProducer = applicationProducers.remove(clientId);
        if (removedProducer != null) {
            removedProducer.stop();
        } else {
            log.trace("[{}] No producer found.", clientId);
        }
    }

    @PreDestroy
    public void destroy() {
        for (TbQueueProducer<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> producer : applicationProducers.values()) {
            producer.stop();
        }
    }
}
