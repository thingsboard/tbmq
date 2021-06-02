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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueConsumer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.DownLinkBasicPublishMsgQueueFactory;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsgDeliveryService;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCtxService;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BasicDownLinkConsumerService {
    private final ExecutorService consumersExecutor = Executors.newCachedThreadPool(ThingsBoardThreadFactory.forName("basic-downlink-publish-msg-consumer"));

    private volatile boolean stopped = false;

    private final List<TbQueueConsumer<TbProtoQueueMsg<QueueProtos.PublishMsgProto>>> consumers = new ArrayList<>();

    private final DownLinkBasicPublishMsgQueueFactory downLinkBasicPublishMsgQueueFactory;
    private final ServiceInfoProvider serviceInfoProvider;
    private final DownLinkPublisherHelper downLinkPublisherHelper;
    private final ClientSessionCtxService clientSessionCtxService;
    private final ClientMqttActorManager clientMqttActorManager;
    private final PublishMsgDeliveryService publishMsgDeliveryService;

    @Value("${queue.basic-downlink-publish-msg.consumers-count}")
    private int consumersCount;
    @Value("${queue.basic-downlink-publish-msg.poll-interval}")
    private long pollDuration;


    @PostConstruct
    public void init() {
        String topic = downLinkPublisherHelper.getBasicDownLinkServiceTopic(serviceInfoProvider.getServiceId());
        String uniqueGroupId = serviceInfoProvider.getServiceId() + System.currentTimeMillis();
        for (int i = 0; i < consumersCount; i++) {
            String consumerId = serviceInfoProvider.getServiceId() + "-" + i;
            TbQueueConsumer<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> consumer = downLinkBasicPublishMsgQueueFactory.createConsumer(topic, consumerId, uniqueGroupId);
            consumers.add(consumer);
            consumer.subscribe();
            launchConsumer(consumerId, consumer);
        }
    }

    private void launchConsumer(String consumerId, TbQueueConsumer<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> consumer) {
        consumersExecutor.submit(() -> {
            while (!stopped) {
                try {
                    List<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> msgs = consumer.poll(pollDuration);
                    if (msgs.isEmpty()) {
                        continue;
                    }

                    // TODO: do we still need to check if client is subscribed for topic
                    // TODO: maybe send msg to Client Actor (to be sure that we don't send msg after client unsubscribes)
                    for (TbProtoQueueMsg<QueueProtos.PublishMsgProto> msg : msgs) {
                        String clientId = msg.getKey();
                        QueueProtos.PublishMsgProto publishMsgProto = msg.getValue();
                        ClientSessionCtx clientSessionCtx = clientSessionCtxService.getClientSessionCtx(clientId);
                        if (clientSessionCtx == null) {
                            log.trace("[{}] No client session on the node.", clientId);
                            continue;
                        }
                        try {
                            deliverMsgToClient(clientSessionCtx, publishMsgProto);
                        } catch (Exception e) {
                            log.debug("[{}] Failed to deliver msg to client. Exception - {}, reason - {}.", clientId, e.getClass().getSimpleName(), e.getMessage());
                            log.trace("Detailed error: ", e);
                            clientMqttActorManager.disconnect(clientId, clientSessionCtx.getSessionId(), new DisconnectReason(DisconnectReasonType.ON_ERROR,
                                    "Failed to deliver PUBLISH msg"));
                        }
                    }
                } catch (Exception e) {
                    if (!stopped) {
                        log.error("[{}] Failed to process messages from queue.", consumerId, e);
                        try {
                            Thread.sleep(pollDuration);
                        } catch (InterruptedException e2) {
                            log.trace("[{}] Failed to wait until the server has capacity to handle new requests", consumerId, e2);
                        }
                    }
                }
            }
            log.info("[{}] Publish Msg Consumer stopped.", consumerId);
        });
    }

    private void deliverMsgToClient(ClientSessionCtx clientSessionCtx, QueueProtos.PublishMsgProto publishMsgProto) {
        publishMsgDeliveryService.sendPublishMsgToClient(clientSessionCtx, clientSessionCtx.getMsgIdSeq().nextMsgId(),
                publishMsgProto.getTopicName(), publishMsgProto.getQos(),
                false, publishMsgProto.getPayload().toByteArray());
    }


    @PreDestroy
    public void destroy() {
        stopped = true;
        consumers.forEach(TbQueueConsumer::unsubscribeAndClose);
        consumersExecutor.shutdownNow();
    }
}
