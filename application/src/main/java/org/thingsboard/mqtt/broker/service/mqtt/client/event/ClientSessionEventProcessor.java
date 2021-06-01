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
import org.thingsboard.mqtt.broker.actors.client.messages.CallbackMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.ClientCallback;
import org.thingsboard.mqtt.broker.actors.client.messages.cluster.SessionClusterManagementMsg;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueConsumer;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.ClientSessionEventQueueFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClientSessionEventProcessor {

    // TODO: add manual control over ClientSession for Admins

    private ExecutorService consumersExecutor;
    private volatile boolean stopped = false;

    private final ClientSessionEventQueueFactory clientSessionEventQueueFactory;
    private final ClientSessionCallbackMsgFactory callbackMsgFactory;
    private final ServiceInfoProvider serviceInfoProvider;
    private final ClientSessionEventActorManager clientSessionEventActorManager;

    @Value("${queue.client-session-event.consumers-count}")
    private int consumersCount;
    @Value("${queue.client-session-event.poll-interval}")
    private long pollDuration;
    @Value("${queue.client-session-event.client-threads-count}")
    private int clientThreadsCount;

    @Value("${queue.client-session-event.acknowledge-wait-timeout-ms:200}")
    private long ackTimeoutMs;

    private final List<TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.ClientSessionEventProto>>> eventConsumers = new ArrayList<>();

    @PostConstruct
    public void init() {
        this.clientThreadsCount = clientThreadsCount <= 0 ? Runtime.getRuntime().availableProcessors() : clientThreadsCount;
        this.consumersExecutor = Executors.newFixedThreadPool(consumersCount, ThingsBoardThreadFactory.forName("client-session-event-consumer"));
        for (int i = 0; i < consumersCount; i++) {
            initConsumer(i);
        }
    }

    private void initConsumer(int consumerId) {
        String consumerName = serviceInfoProvider.getServiceId() + "-" + consumerId;
        TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.ClientSessionEventProto>> eventConsumer = clientSessionEventQueueFactory.createEventConsumer(consumerName);
        eventConsumers.add(eventConsumer);
        eventConsumer.subscribe();
        consumersExecutor.submit(() -> processClientSessionEvents(eventConsumer));
    }

    private void processClientSessionEvents(TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.ClientSessionEventProto>> consumer) {
        while (!stopped) {
            try {
                List<TbProtoQueueMsg<QueueProtos.ClientSessionEventProto>> msgs = consumer.poll(pollDuration);
                if (msgs.isEmpty()) {
                    continue;
                }
                // TODO: if consumer gets disconnected from Kafka, partition will be rebalanced to different Node and therefore same Client may be concurrently processed
                for (TbProtoQueueMsg<QueueProtos.ClientSessionEventProto> msg : msgs) {
                    // TODO: think about failures (make sure method can be safely called multiple times)
                    // TODO: process messages in batch and wait for all to finish
                    consumer.commit(msg.getPartition(), msg.getOffset() + 1);
                    // TODO (priority 1): will block till timeout if message is lost in Actor System
                    processMsg(msg);
                }
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

    private void processMsg(TbProtoQueueMsg<QueueProtos.ClientSessionEventProto> msg) {
        String clientId = msg.getKey();

        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        CountDownLatch updateWaiter = new CountDownLatch(1);

        ClientCallback callback = new ClientCallback() {
            @Override
            public void onSuccess() {
                updateWaiter.countDown();
            }

            @Override
            public void onFailure(Throwable t) {
                errorRef.getAndSet(t);
                updateWaiter.countDown();
            }
        };

        SessionClusterManagementMsg sessionClusterManagementMsg = callbackMsgFactory.createSessionClusterManagementMsg(msg, callback);
        clientSessionEventActorManager.sendCallbackMsg(clientId, sessionClusterManagementMsg);

        boolean waitSuccessful = false;
        try {
            // TODO is this OK that the thread is blocked?
            waitSuccessful = updateWaiter.await(ackTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            errorRef.getAndSet(e);
        }
        Throwable error = errorRef.get();
        if (!waitSuccessful || error != null) {
            log.warn("[{}] Failed to process {} msg. Reason - {}.",
                    clientId, sessionClusterManagementMsg.getMsgType(), error != null ? error.getMessage() : "timeout waiting");
            if (error != null) {
                log.trace("Detailed error:", error);
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
