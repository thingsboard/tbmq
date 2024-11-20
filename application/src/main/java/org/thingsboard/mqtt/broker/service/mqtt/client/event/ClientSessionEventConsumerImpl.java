/**
 * Copyright © 2016-2024 The Thingsboard Authors
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

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.actors.client.messages.ClientCallback;
import org.thingsboard.mqtt.broker.actors.client.messages.cluster.SessionClusterManagementMsg;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueConsumer;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.ClientSessionEventQueueFactory;
import org.thingsboard.mqtt.broker.service.stats.ClientSessionEventConsumerStats;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;

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

    private final List<TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.ClientSessionEventProto>>> eventConsumers = new ArrayList<>();

    private final ClientSessionEventQueueFactory clientSessionEventQueueFactory;
    private final ClientSessionCallbackMsgFactory callbackMsgFactory;
    private final ServiceInfoProvider serviceInfoProvider;
    private final ClientSessionEventActorManager clientSessionEventActorManager;
    private final StatsManager statsManager;

    @Value("${queue.client-session-event.consumers-count}")
    private int consumersCount;
    @Value("${queue.client-session-event.poll-interval}")
    private long pollDuration;
    @Value("${queue.client-session-event.batch-wait-timeout-ms:2000}")
    private long waitTimeoutMs;

    private volatile boolean stopped = false;
    private ExecutorService consumersExecutor;

    @Override
    public void startConsuming() {
        this.consumersExecutor = Executors.newFixedThreadPool(consumersCount, ThingsBoardThreadFactory.forName("client-session-event-consumer"));
        for (int i = 0; i < consumersCount; i++) {
            initConsumer(i);
        }
    }

    private void initConsumer(int consumerIndex) {
        String consumerId = serviceInfoProvider.getServiceId() + "-" + consumerIndex;
        var eventConsumer = clientSessionEventQueueFactory.createEventConsumer(consumerId);
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
                    if (log.isDebugEnabled()) {
                        log.debug("Going to process {} client session events", msgs.size());
                    }
                }
                long packProcessingStart = System.nanoTime();
                processMessages(msgs);
                stats.logPackProcessingTime(msgs.size(), System.nanoTime() - packProcessingStart, TimeUnit.NANOSECONDS);
                consumer.commitSync();
            } catch (Exception e) {
                if (!stopped) {
                    log.error("Failed to process messages from queue.", e);
                    try {
                        Thread.sleep(pollDuration);
                    } catch (InterruptedException e2) {
                        if (log.isTraceEnabled()) {
                            log.trace("Failed to wait until the server has capacity to handle new requests", e2);
                        }
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
                    log.warn("[{}] Failed to process {} msg.", clientId, msg.getValue().getEventType(), t);
                    latch.countDown();
                }
            };

            SessionClusterManagementMsg sessionClusterManagementMsg = callbackMsgFactory.createSessionClusterManagementMsg(msg, callback);
            try {
                clientSessionEventActorManager.sendSessionClusterManagementMsg(clientId, sessionClusterManagementMsg);
            } catch (Exception e) {
                log.warn("[{}] Failed to send {} msg to actor.", clientId, sessionClusterManagementMsg.getMsgType(), e);
            }
        }

        boolean waitSuccessful = false;
        try {
            waitSuccessful = latch.await(waitTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            log.warn("Batch processing was interrupted");
        }
        if (!waitSuccessful) {
            log.warn("Failed to process {} events in time", latch.getCount());
        }
    }

    @PreDestroy
    public void destroy() {
        stopped = true;
        eventConsumers.forEach(TbQueueConsumer::unsubscribeAndClose);
        if (consumersExecutor != null) {
            ThingsBoardExecutors.shutdownAndAwaitTermination(consumersExecutor, "Client session event consumer");
        }
    }
}
