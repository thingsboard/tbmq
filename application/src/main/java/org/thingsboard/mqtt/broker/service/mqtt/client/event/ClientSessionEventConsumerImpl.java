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
package org.thingsboard.mqtt.broker.service.mqtt.client.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.actors.client.messages.ClientCallback;
import org.thingsboard.mqtt.broker.actors.client.messages.cluster.SessionClusterManagementMsg;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueConsumer;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.ClientSessionEventQueueFactory;
import org.thingsboard.mqtt.broker.service.stats.ClientSessionEventConsumerStats;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClientSessionEventConsumerImpl implements ClientSessionEventConsumer {

    private ExecutorService consumersExecutor;
    private volatile boolean stopped = false;

    private final ClientSessionEventQueueFactory clientSessionEventQueueFactory;
    private final ClientSessionCallbackMsgFactory callbackMsgFactory;
    private final ServiceInfoProvider serviceInfoProvider;
    private final ClientSessionEventActorManager clientSessionEventActorManager;
    private final StatsManager statsManager;

    @Value("${queue.client-session-event.consumers-count}")
    private int consumersCount;
    @Value("${queue.client-session-event.poll-interval}")
    private long pollDuration;

    private final List<TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.ClientSessionEventProto>>> eventConsumers = new ArrayList<>();

    @Override
    public void startConsuming() {
        this.consumersExecutor = Executors.newFixedThreadPool(consumersCount, ThingsBoardThreadFactory.forName("client-session-event-consumer"));
        for (int i = 0; i < consumersCount; i++) {
            initConsumer(i);
        }
    }

    private void initConsumer(int consumerIndex) {
        String consumerId = serviceInfoProvider.getServiceId() + "-" + consumerIndex;
        TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.ClientSessionEventProto>> eventConsumer = clientSessionEventQueueFactory.createEventConsumer(consumerId);
        eventConsumers.add(eventConsumer);
        eventConsumer.subscribe();
        ClientSessionEventConsumerStats stats = statsManager.createClientSessionEventConsumerStats(consumerId);
        consumersExecutor.submit(() -> processClientSessionEvents(eventConsumer, stats));
    }

    private void processClientSessionEvents(TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.ClientSessionEventProto>> consumer, ClientSessionEventConsumerStats stats) {
        while (!stopped) {
            try {
                List<TbProtoQueueMsg<QueueProtos.ClientSessionEventProto>> msgs = consumer.poll(pollDuration);
                if (msgs.isEmpty()) {
                    continue;
                } else {
                    log.debug("Going to process {} client session events", msgs.size());
                }
                // TODO: Possible issues:
                //          - if consumer gets disconnected from Kafka, partition will be rebalanced to different Node and therefore same Client may be concurrently processed
                //          - failures (make sure method can be safely called multiple times)
                //          - will block till timeout if message is lost in Actor System
                consumer.commitSync();
                long packProcessingStart = System.nanoTime();
                processMessages(msgs);
                stats.logPackProcessingTime(msgs.size(), System.nanoTime() - packProcessingStart, TimeUnit.NANOSECONDS);
            } catch (Exception e) {
                if (!stopped) {
                    log.error("Failed to process messages from queue.", e);
                    try {
                        Thread.sleep(pollDuration);
                    } catch (InterruptedException e2) {
                        log.trace("Failed to wait until the server has capacity to handle new requests", e2);
                    }
                }
            }
        }
        log.info("Client Session Event Consumer stopped.");
    }

    private void processMessages(List<TbProtoQueueMsg<QueueProtos.ClientSessionEventProto>> msgs) {
        CountDownLatch latch = new CountDownLatch(msgs.size());
        for (TbProtoQueueMsg<QueueProtos.ClientSessionEventProto> msg : msgs) {
            String clientId = msg.getKey();

            ClientCallback callback = new ClientCallback() {
                @Override
                public void onSuccess() {
                    latch.countDown();
                }

                @Override
                public void onFailure(Throwable t) {
                    log.warn("[{}] Failed to process {} msg. Exception - {}, reason - {}.",
                            clientId, msg.getValue().getEventType(), t.getClass().getSimpleName(), t.getMessage());
                    log.trace("Detailed error:", t);
                    latch.countDown();
                }
            };

            SessionClusterManagementMsg sessionClusterManagementMsg = callbackMsgFactory.createSessionClusterManagementMsg(msg, callback);
            try {
                clientSessionEventActorManager.sendSessionClusterManagementMsg(clientId, sessionClusterManagementMsg);
            } catch (Exception e) {
                log.warn("[{}] Failed to send {} msg to actor. Exception - {}, reason - {}.", clientId, sessionClusterManagementMsg.getMsgType(),
                        e.getClass().getSimpleName(), e.getMessage());
                log.trace("Detailed error: ", e);
            }
        }
    }

    @PreDestroy
    public void destroy() {
        stopped = true;
        eventConsumers.forEach(TbQueueConsumer::unsubscribeAndClose);
        if (consumersExecutor != null) {
            consumersExecutor.shutdownNow();
        }
    }
}
