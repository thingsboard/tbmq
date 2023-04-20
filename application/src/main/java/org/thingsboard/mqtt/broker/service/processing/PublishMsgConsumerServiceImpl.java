/**
 * Copyright © 2016-2023 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.processing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos.PublishMsgProto;
import org.thingsboard.mqtt.broker.queue.TbQueueConsumer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.PublishMsgQueueFactory;
import org.thingsboard.mqtt.broker.service.stats.PublishMsgConsumerStats;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;


@Service
@Slf4j
@RequiredArgsConstructor
public class PublishMsgConsumerServiceImpl implements PublishMsgConsumerService {

    public static final long MAX_VALUE = 1_000_000_000L;

    // TODO: remove all cachedThreadPool
    private final ExecutorService consumersExecutor = Executors.newCachedThreadPool(ThingsBoardThreadFactory.forName("publish-msg-consumer"));
    private volatile boolean stopped = false;

    @Value("${queue.publish-msg.consumers-count}")
    private int consumersCount;
    @Value("${queue.publish-msg.poll-interval}")
    private long pollDuration;
    @Value("${queue.publish-msg.pack-processing-timeout}")
    private long packProcessingTimeout;

    private final List<TbQueueConsumer<TbProtoQueueMsg<PublishMsgProto>>> publishMsgConsumers = new ArrayList<>();
    private final MsgDispatcherService msgDispatcherService;
    private final PublishMsgQueueFactory publishMsgQueueFactory;
    private final AckStrategyFactory ackStrategyFactory;
    private final SubmitStrategyFactory submitStrategyFactory;
    private final ServiceInfoProvider serviceInfoProvider;
    private final StatsManager statsManager;

    @Override
    public void startConsuming() {
        for (int i = 0; i < consumersCount; i++) {
            String consumerId = serviceInfoProvider.getServiceId() + "-" + i;
            // TODO: think about the fact that all consumed messages can be processed multiple time (if kafka is disconnected while msgs are processing)
            TbQueueConsumer<TbProtoQueueMsg<PublishMsgProto>> consumer = publishMsgQueueFactory.createConsumer(consumerId);
            publishMsgConsumers.add(consumer);
            consumer.subscribe();
            launchConsumer(consumerId, consumer);
        }
    }

    private void launchConsumer(String consumerId, TbQueueConsumer<TbProtoQueueMsg<PublishMsgProto>> consumer) {
        PublishMsgConsumerStats stats = statsManager.createPublishMsgConsumerStats(consumerId);
        final AtomicLong counter = new AtomicLong(0);
        consumersExecutor.submit(() -> {
            while (!stopped) {
                try {
                    List<TbProtoQueueMsg<PublishMsgProto>> msgs = consumer.poll(pollDuration);
                    if (msgs.isEmpty()) {
                        continue;
                    }

                    AckStrategy ackStrategy = ackStrategyFactory.newInstance(consumerId);
                    SubmitStrategy submitStrategy = submitStrategyFactory.newInstance(consumerId);
                    long packId = counter.incrementAndGet();
                    if (packId == MAX_VALUE) {
                        counter.set(0);
                    }
                    var pendingMsgMap = toPendingPubMsgWithIdMap(msgs, packId);
                    submitStrategy.init(pendingMsgMap);

                    long packProcessingStart = System.nanoTime();
                    while (!stopped) {
                        PackProcessingContext ctx = new PackProcessingContext(submitStrategy.getPendingMap());
                        int totalMsgCount = ctx.getPendingMap().size();
                        submitStrategy.process(msg -> {
                            long msgProcessingStart = System.nanoTime();
                            msgDispatcherService.processPublishMsg(msg.getPublishMsgProto(), new BasePublishMsgCallback(msg.getId(), ctx));
                            stats.logMsgProcessingTime(System.nanoTime() - msgProcessingStart, TimeUnit.NANOSECONDS);
                        });

                        if (!stopped) {
                            ctx.await(packProcessingTimeout, TimeUnit.MILLISECONDS);
                        }
                        PackProcessingResult result = new PackProcessingResult(ctx);
                        ctx.cleanup();
                        ProcessingDecision decision = ackStrategy.analyze(result);

                        stats.log(totalMsgCount, result, decision.isCommit());

                        if (decision.isCommit()) {
                            consumer.commitSync();
                            break;
                        } else {
                            submitStrategy.update(decision.getReprocessMap());
                        }
                    }
                    stats.logPackProcessingTime(msgs.size(), System.nanoTime() - packProcessingStart, TimeUnit.NANOSECONDS);
                } catch (Exception e) {
                    if (!stopped) {
                        log.error("[{}] Failed to process messages from queue.", consumerId, e);
                        try {
                            Thread.sleep(pollDuration);
                        } catch (InterruptedException e2) {
                            if (log.isDebugEnabled()) {
                                log.debug("[{}] Failed to wait until the server has capacity to handle new requests", consumerId, e2);
                            }
                        }
                    }
                }
            }
            log.info("[{}] Publish Msg Consumer stopped.", consumerId);
        });
    }

    private ConcurrentMap<UUID, PublishMsgWithId> toPendingPubMsgWithIdMap(List<TbProtoQueueMsg<PublishMsgProto>> msgs, long packId) {
        ConcurrentMap<UUID, PublishMsgWithId> publishMsgPendingMap = new ConcurrentHashMap<>(msgs.size());
        int i = 0;
        for (var msg : msgs) {
            UUID id = new UUID(packId, i++);
            publishMsgPendingMap.put(id, new PublishMsgWithId(id, msg.getValue()));
        }
        return publishMsgPendingMap;
    }


    @PreDestroy
    public void destroy() {
        stopped = true;
        publishMsgConsumers.forEach(TbQueueConsumer::unsubscribeAndClose);
        consumersExecutor.shutdownNow();
    }
}
