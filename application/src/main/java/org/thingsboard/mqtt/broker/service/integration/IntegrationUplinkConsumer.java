/**
 * Copyright © 2016-2026 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.integration;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.callback.TbCallback;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;
import org.thingsboard.mqtt.broker.gen.integration.IntegrationValidationResponseProto;
import org.thingsboard.mqtt.broker.gen.integration.UplinkIntegrationMsgProto;
import org.thingsboard.mqtt.broker.gen.integration.UplinkIntegrationNotificationMsgProto;
import org.thingsboard.mqtt.broker.queue.TbQueueConsumer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.integration.IntegrationUplinkQueueProvider;
import org.thingsboard.mqtt.broker.service.IntegrationManagerService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@Slf4j
@RequiredArgsConstructor
public class IntegrationUplinkConsumer {

    private final IntegrationUplinkQueueProvider uplinkQueueProvider;
    private final TbmqIntegrationApiService tbmqIntegrationApiService;
    private final IntegrationManagerService integrationManagerService;

    private volatile boolean stopped = false;
    private ExecutorService consumerExecutor;
    private ExecutorService notificationsConsumerExecutor;

    @Value("${queue.integration-uplink.poll-interval}")
    private long pollDuration;
    @Value("${queue.integration-uplink.pack-processing-timeout}")
    private long packProcessingTimeout;
    @Value("${queue.integration-uplink-notifications.poll-interval}")
    private long notificationsPollDuration;

    @PostConstruct
    public void init() {
        consumerExecutor = ThingsBoardExecutors.initSingleExecutorService("ie-uplink-consumer");
        notificationsConsumerExecutor = ThingsBoardExecutors.initSingleExecutorService("ie-uplink-notifications-consumer");

        var consumer = uplinkQueueProvider.getIeUplinkConsumer();
        consumer.subscribe();
        launchConsumer(consumer);

        var notificationsConsumer = uplinkQueueProvider.getIeUplinkNotificationsConsumer();
        notificationsConsumer.subscribe();
        launchNotificationsConsumer(notificationsConsumer);
    }

    @PreDestroy
    public void destroy() {
        stopped = true;
        if (consumerExecutor != null) {
            ThingsBoardExecutors.shutdownAndAwaitTermination(consumerExecutor, "IE uplink consumer");
        }
        if (notificationsConsumerExecutor != null) {
            ThingsBoardExecutors.shutdownAndAwaitTermination(notificationsConsumerExecutor, "IE uplink notifications consumer");
        }
        integrationManagerService.proceedGracefulShutdown();
    }

    // Package-private so a test can drive one loop iteration and assert the commit follows the await.
    void launchConsumer(TbQueueConsumer<TbProtoQueueMsg<UplinkIntegrationMsgProto>> consumer) {
        consumerExecutor.submit(() -> {
            while (!stopped) {
                try {
                    List<TbProtoQueueMsg<UplinkIntegrationMsgProto>> msgs = consumer.poll(pollDuration);
                    if (msgs.isEmpty()) {
                        continue;
                    }

                    Map<String, List<TbProtoQueueMsg<UplinkIntegrationMsgProto>>> byMessageKey = groupByMessageKey(msgs);
                    // Awaited before committing so the next pack cannot overlap the current one and reorder
                    // the events of an integration that appears in both.
                    awaitPackProcessing(processPack(byMessageKey), byMessageKey.keySet());
                    consumer.commitSync();
                } catch (Exception e) {
                    if (!stopped) {
                        log.error("Failed to process messages from ie uplink queue.", e);
                        try {
                            Thread.sleep(pollDuration);
                        } catch (InterruptedException e2) {
                            if (log.isDebugEnabled()) {
                                log.debug("Failed to wait until the server has capacity to handle new ie uplink requests", e2);
                            }
                        }
                    }
                }
            }
            log.info("IE Uplink Consumer stopped");
        });
    }

    /**
     * Processes a pack per integration: events of one integration are applied strictly one after another, while
     * different integrations progress independently.
     * <p>
     * The IE keys every event by the integration id (see DefaultIntegrationApiService.sendEventData), so a single
     * integration's events already arrive ordered within one partition. Overlapping their asynchronous processing
     * throws that ordering away and lets an older event win - e.g. a STOPPED left over from a previous IE run
     * overwriting the status of the newer STARTED, which leaves a running integration displayed as pending.
     * <p>
     * Only the lifecycle events carry that status, so chaining the statistics and error events of the same
     * integration too is stricter than required. They are chained anyway because the event type only becomes known
     * once the serialized payload is decoded, which happens in the handler - telling them apart here would mean
     * decoding every event twice. {@code max.poll.records} for this topic bounds how long a single chain can get.
     * <p>
     * Deliberately a CompletableFuture chain rather than the PackProcessingContext shape the device, application and
     * IE-side consumers use: those need a pending map and a latch to drive retry and ack strategies, and this stream
     * has neither - uplink packs are not retried.
     */
    CompletableFuture<Void> processPack(Map<String, List<TbProtoQueueMsg<UplinkIntegrationMsgProto>>> byMessageKey) {
        return CompletableFuture.allOf(byMessageKey.values().stream()
                .map(this::processSequentially)
                .toArray(CompletableFuture[]::new));
    }

    void awaitPackProcessing(CompletableFuture<Void> pack, Collection<String> integrationIds) {
        try {
            pack.get(packProcessingTimeout, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // Deliberately proceeds to the commit: uplink packs are not retried today (see the TODO in handle),
            // and blocking the consumer indefinitely would stall every integration behind one stuck event.
            // The per-integration ordering guarantee lapses here, though: the abandoned chains keep running while
            // the next pack is dispatched, so an integration present in both can again be applied out of order.
            // The ids are logged so that a status that ends up stale can be traced back to this line.
            log.warn("Timed out after {} ms awaiting the ie uplink pack processing of {}", packProcessingTimeout, integrationIds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while awaiting the ie uplink pack processing");
        } catch (Exception e) {
            log.warn("Failed to await the ie uplink pack processing", e);
        }
    }

    /**
     * Groups by the queue message key, which the IE sets to the integration id for events
     * (DefaultIntegrationApiService.sendEventData) and to a random UUID for service info, so anything not tied to an
     * integration lands in a group of its own and is never ordered against something else.
     */
    Map<String, List<TbProtoQueueMsg<UplinkIntegrationMsgProto>>> groupByMessageKey(List<TbProtoQueueMsg<UplinkIntegrationMsgProto>> msgs) {
        Map<String, List<TbProtoQueueMsg<UplinkIntegrationMsgProto>>> byMessageKey = new LinkedHashMap<>();
        for (TbProtoQueueMsg<UplinkIntegrationMsgProto> msg : msgs) {
            byMessageKey.computeIfAbsent(msg.getKey(), __ -> new ArrayList<>()).add(msg);
        }
        return byMessageKey;
    }

    /**
     * Chains the group so each message is handled only after the previous one's callback fires.
     * <p>
     * Note where each link runs: the callback completes on a DB thread, not on the consumer thread - a DB read-pool
     * thread for a lifecycle event, and the events insert queue's own worker thread for anything else, since
     * TbSqlBlockingQueue sets the future inside its drain loop and a direct-executor listener runs synchronously
     * there. In the second case the next message's insert is submitted from the insert queue's own thread, which is
     * safe only because that queue is unbounded and its add never blocks. Bounding it for backpressure would
     * deadlock this chain with no error.
     */
    private CompletableFuture<Void> processSequentially(List<TbProtoQueueMsg<UplinkIntegrationMsgProto>> group) {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (TbProtoQueueMsg<UplinkIntegrationMsgProto> msg : group) {
            chain = chain.thenCompose(__ -> handle(msg));
        }
        return chain;
    }

    private CompletableFuture<Void> handle(TbProtoQueueMsg<UplinkIntegrationMsgProto> msg) {
        // Never completed exceptionally: a single failed event must not skip the remaining events of the same
        // integration, which would leave its status reflecting an even older event.
        CompletableFuture<Void> processed = new CompletableFuture<>();
        try {
            // TODO: improve the retry strategy
            tbmqIntegrationApiService.handle(msg, new TbCallback() {
                @Override
                public void onSuccess() {
                    processed.complete(null);
                }

                @Override
                public void onFailure(Throwable t) {
                    log.warn("Failed to process integration msg: {}", msg, t);
                    processed.complete(null);
                }
            });
        } catch (Throwable e) {
            log.warn("Failed to process integration msg: {}", msg, e);
            processed.complete(null);
        }
        return processed;
    }

    private void launchNotificationsConsumer(TbQueueConsumer<TbProtoQueueMsg<UplinkIntegrationNotificationMsgProto>> notificationsConsumer) {
        notificationsConsumerExecutor.submit(() -> {
            while (!stopped) {
                try {
                    List<TbProtoQueueMsg<UplinkIntegrationNotificationMsgProto>> msgs = notificationsConsumer.poll(notificationsPollDuration);
                    if (msgs.isEmpty()) {
                        continue;
                    }

                    for (TbProtoQueueMsg<UplinkIntegrationNotificationMsgProto> msg : msgs) {
                        try {
                            // TODO: improve the retry strategy
                            handleNotification(msg, TbCallback.EMPTY);
                        } catch (Throwable e) {
                            log.warn("Failed to process integration notification msg: {}", msg, e);
                        }
                    }
                    notificationsConsumer.commitSync();
                } catch (Exception e) {
                    if (!stopped) {
                        log.error("Failed to process messages from ie uplink notifications queue.", e);
                        try {
                            Thread.sleep(notificationsPollDuration);
                        } catch (InterruptedException e2) {
                            if (log.isDebugEnabled()) {
                                log.debug("Failed to wait until the server has capacity to handle new ie uplink notification requests", e2);
                            }
                        }
                    }
                }
            }
            log.info("IE Uplink Notification Consumer stopped");
        });
    }

    protected void handleNotification(TbProtoQueueMsg<UplinkIntegrationNotificationMsgProto> msg, TbCallback callback) {
        UplinkIntegrationNotificationMsgProto uplinkIntegrationNotificationMsg = msg.getValue();
        if (uplinkIntegrationNotificationMsg.hasIntegrationValidationResponseMsg()) {
            log.trace("Forwarding message to Integration service {}", uplinkIntegrationNotificationMsg.getIntegrationValidationResponseMsg());
            forwardToIntegrationManagerService(uplinkIntegrationNotificationMsg.getIntegrationValidationResponseMsg(), callback);
        } else {
            log.debug("Unexpected message in UplinkIntegrationNotificationMsgProto {}", uplinkIntegrationNotificationMsg);
        }
    }

    private void forwardToIntegrationManagerService(IntegrationValidationResponseProto integrationDownlinkMsg, TbCallback callback) {
        integrationManagerService.handleValidationResponse(integrationDownlinkMsg, callback);
    }
}
