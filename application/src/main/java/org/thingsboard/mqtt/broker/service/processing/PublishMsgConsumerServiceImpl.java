/**
 * Copyright © 2016-2025 The Thingsboard Authors
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

import com.google.common.collect.Maps;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;
import org.thingsboard.mqtt.broker.gen.queue.PublishMsgProto;
import org.thingsboard.mqtt.broker.queue.TbQueueConsumer;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.PublishMsgQueueFactory;
import org.thingsboard.mqtt.broker.service.limits.RateLimitService;
import org.thingsboard.mqtt.broker.service.stats.PublishMsgConsumerStats;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;


@Service
@Slf4j
@RequiredArgsConstructor
public class PublishMsgConsumerServiceImpl implements PublishMsgConsumerService {

    public static final long MAX_VALUE = 1_000_000_000L;

    private final List<TbQueueConsumer<TbProtoQueueMsg<PublishMsgProto>>> publishMsgConsumers = new ArrayList<>();
    private final MsgDispatcherService msgDispatcherService;
    private final PublishMsgQueueFactory publishMsgQueueFactory;
    private final AckStrategyFactory ackStrategyFactory;
    private final SubmitStrategyFactory submitStrategyFactory;
    private final ServiceInfoProvider serviceInfoProvider;
    private final StatsManager statsManager;
    private final RateLimitService rateLimitService;

    private volatile boolean stopped = false;
    private ExecutorService consumersExecutor;

    @Value("${queue.msg-all.threads-count}")
    private int threadsCount;
    @Value("${queue.msg-all.consumers-count}")
    private int consumersCount;
    @Value("${queue.msg-all.poll-interval}")
    private long pollDuration;
    @Value("${queue.msg-all.pack-processing-timeout}")
    private long packProcessingTimeout;

    @PostConstruct
    public void init() {
        consumersExecutor = ThingsBoardExecutors.initExecutorService(threadsCount, "msg-all-consumer");
    }

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

                    List<TbProtoQueueMsg<PublishMsgProto>> msgsAfterRateLimits = applyRateLimits(consumer, msgs);
                    if (msgsAfterRateLimits == null) {
                        continue;
                    }

                    AckStrategy ackStrategy = ackStrategyFactory.newInstance(consumerId);
                    SubmitStrategy submitStrategy = submitStrategyFactory.newInstance(consumerId);
                    long packId = counter.incrementAndGet();
                    if (packId == MAX_VALUE) {
                        counter.set(0);
                    }
                    var pendingMsgMap = toPendingPubMsgWithIdMap(msgsAfterRateLimits, packId);
                    submitStrategy.init(pendingMsgMap);

                    long packProcessingStart = System.nanoTime();
                    while (!stopped) {
                        PackProcessingContext ctx = new PackProcessingContext(submitStrategy.getPendingMap());
                        int totalMsgCount = ctx.getPendingMap().size();
                        submitStrategy.process(msg -> {
                            long msgProcessingStart = System.nanoTime();
                            msgDispatcherService.processPublishMsg(msg, new BasePublishMsgCallback(msg.getId(), ctx));
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
                    stats.logPackProcessingTime(msgsAfterRateLimits.size(), System.nanoTime() - packProcessingStart, TimeUnit.NANOSECONDS);
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

    private List<TbProtoQueueMsg<PublishMsgProto>> applyRateLimits(TbQueueConsumer<TbProtoQueueMsg<PublishMsgProto>> consumer,
                                                                   List<TbProtoQueueMsg<PublishMsgProto>> msgs) {
        if (rateLimitService.isTotalMsgsLimitEnabled()) {
            int availableTokens = (int) rateLimitService.tryConsumeAsMuchAsPossibleTotalMsgs(msgs.size());
            if (availableTokens == 0) {
                log.debug("No available tokens left for total msgs bucket during consumer polling. Skipping {} messages", msgs.size());
                consumer.commitSync();
                return null;
            }
            if (availableTokens == msgs.size()) {
                return msgs;
            }
            if (log.isDebugEnabled() && availableTokens < msgs.size()) {
                log.debug("Hitting total messages rate limits on consumer polling. Skipping {} messages", msgs.size() - availableTokens);
            }
            return msgs.subList(0, availableTokens);
        }
        return msgs;
    }

    private Map<UUID, PublishMsgWithId> toPendingPubMsgWithIdMap(List<TbProtoQueueMsg<PublishMsgProto>> msgs, long packId) {
        Map<UUID, PublishMsgWithId> publishMsgPendingMap = Maps.newLinkedHashMapWithExpectedSize(msgs.size());
        int i = 0;
        for (var msg : msgs) {
            UUID id = new UUID(packId, i++);
            publishMsgPendingMap.put(id, new PublishMsgWithId(id, msg.getValue(), msg.getHeaders()));
        }
        return publishMsgPendingMap;
    }


    @PreDestroy
    public void destroy() {
        stopped = true;
        publishMsgConsumers.forEach(TbQueueConsumer::unsubscribeAndClose);
        if (consumersExecutor != null) {
            ThingsBoardExecutors.shutdownAndAwaitTermination(consumersExecutor, "Publish msg consumer");
        }
    }
}
