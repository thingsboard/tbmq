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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.device.queue;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.DevicePersistenceMsgQueueFactory;
import org.thingsboard.mqtt.broker.service.processing.PublishMsgCallback;

import javax.annotation.PreDestroy;

@Slf4j
@Service
public class DeviceMsgQueueServiceImpl implements DeviceMsgQueueService {
    private final TbQueueProducer<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> producer;

    public DeviceMsgQueueServiceImpl(DevicePersistenceMsgQueueFactory devicePersistenceMsgQueueFactory) {
        this.producer = devicePersistenceMsgQueueFactory.createProducer();
    }

    @Override
    public void sendMsg(String clientId, QueueProtos.PublishMsgProto msgProto, PublishMsgCallback callback) {
        producer.send(new TbProtoQueueMsg<>(clientId, msgProto),
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

    @PreDestroy
    public void destroy() {
        producer.stop();
    }
}
