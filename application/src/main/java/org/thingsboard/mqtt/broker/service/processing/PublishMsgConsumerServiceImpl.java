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

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Service
@Slf4j
@RequiredArgsConstructor
public class PublishMsgConsumerServiceImpl implements PublishMsgConsumerService {

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
            TbQueueConsumer<TbProtoQueueMsg<PublishMsgProto>> consumer = publishMsgQueueFactory.createConsumer(consumerId);
            publishMsgConsumers.add(consumer);
            consumer.subscribe();
            launchConsumer(consumerId, consumer);
        }
    }

    private void launchConsumer(String consumerId, TbQueueConsumer<TbProtoQueueMsg<PublishMsgProto>> consumer) {
        PublishMsgConsumerStats stats = statsManager.createPublishMsgConsumerStats(consumerId);
        consumersExecutor.submit(() -> {
            while (!stopped) {
                try {
                    List<TbProtoQueueMsg<PublishMsgProto>> msgs = consumer.poll(pollDuration);
                    if (msgs.isEmpty()) {
                        continue;
                    }

                    AckStrategy ackStrategy = ackStrategyFactory.newInstance(consumerId);
                    SubmitStrategy submitStrategy = submitStrategyFactory.newInstance(consumerId);
                    List<PublishMsgWithId> messagesWithId = msgs.stream()
                            .map(msg -> new PublishMsgWithId(UUID.randomUUID(), msg.getValue()))
                            .collect(Collectors.toList());
                    submitStrategy.init(messagesWithId);

                    while (!stopped) {
                        PackProcessingContext ctx = new PackProcessingContext(submitStrategy.getPendingMap());
                        int totalMsgCount = ctx.getPendingMap().size();
                        submitStrategy.process(msg -> {
                            msgDispatcherService.processPublishMsg(msg.getPublishMsgProto(), new BasePublishMsgCallback(msg.getId(), ctx));
                        });

                        if (!stopped) {
                            ctx.await(packProcessingTimeout, TimeUnit.MILLISECONDS);
                        }
                        PackProcessingResult result = new PackProcessingResult(ctx);
                        ctx.cleanup();
                        ProcessingDecision decision = ackStrategy.analyze(result);

                        stats.log(totalMsgCount, result, decision.isCommit());

                        if (decision.isCommit()) {
                            consumer.commit();
                            break;
                        } else {
                            submitStrategy.update(decision.getReprocessMap());
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


    @PreDestroy
    public void destroy() {
        stopped = true;
        publishMsgConsumers.forEach(TbQueueConsumer::unsubscribeAndClose);
        consumersExecutor.shutdownNow();
    }
}
