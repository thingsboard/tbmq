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
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.ApplicationPersistenceMsgQueueFactory;

import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultApplicationPersistenceSessionService implements ApplicationPersistenceSessionService {
    private final Map<String, TbQueueProducer<TbProtoQueueMsg<QueueProtos.PublishMsgProto>>> applicationProducers = new ConcurrentHashMap<>();

    private final ApplicationPersistenceMsgQueueFactory applicationPersistenceMsgQueueFactory;
    private final ApplicationLastPublishCtxService lastPublishCtxService;

    @Override
    public void processMsgPersistence(String clientId, int subscriptionQoSValue, QueueProtos.PublishMsgProto receivedPublishMsgProto) {
        // We have to set 'packetId' here and not on the side of the consumer because if the message was sent and not acknowledged, it needs to be sent again with the same 'packetId'
        int nextPacketId = lastPublishCtxService.getNextPacketId(clientId);
        int minQoSValue = Math.min(subscriptionQoSValue, receivedPublishMsgProto.getQos());
        QueueProtos.PublishMsgProto persistedPublishMsgProto = QueueProtos.PublishMsgProto.newBuilder(receivedPublishMsgProto)
                .setPacketId(nextPacketId)
                .setQos(minQoSValue)
                .build();

        lastPublishCtxService.saveLastPublishCtx(clientId, nextPacketId);
        savePublishMsgProto(clientId, persistedPublishMsgProto);
    }

    @Override
    public void clearPersistedCtx(String clientId) {
        lastPublishCtxService.clearContext(clientId);
        clearProducer(clientId);
    }

    private void clearProducer(String clientId) {
        TbQueueProducer<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> removedProducer = applicationProducers.remove(clientId);
        if (removedProducer != null) {
            removedProducer.stop();
        } else {
            log.trace("[{}] No producer found.", clientId);
        }
    }

    private void savePublishMsgProto(String clientId, QueueProtos.PublishMsgProto publishMsgProto) {
        TbQueueProducer<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> applicationProducer = applicationProducers.computeIfAbsent(clientId,
                id -> applicationPersistenceMsgQueueFactory.createProducer(clientId));
        applicationProducer.send(new TbProtoQueueMsg<>(publishMsgProto.getTopicName(), publishMsgProto),
                new TbQueueCallback() {
                    @Override
                    public void onSuccess(TbQueueMsgMetadata metadata) {
                        log.trace("[{}] Successfully sent publish msg to the queue.", clientId);
                    }
                    @Override
                    public void onFailure(Throwable t) {
                        // TODO do we need to decrement offset counter?
                        log.error("[{}] Failed to send publish msg to the queue for MQTT topic {}. Reason - {}.",
                                clientId, publishMsgProto.getTopicName(), t.getMessage());
                        log.debug("Detailed error: ", t);
                    }
                });
    }

    @PreDestroy
    public void destroy() {
        for (TbQueueProducer<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> producer : applicationProducers.values()) {
            producer.stop();
        }
    }
}
