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
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.stats.MessagesStats;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos.PublishMsgProto;
import org.thingsboard.mqtt.broker.queue.TbQueueConsumer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.PublishMsgQueueFactory;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@Service
@Slf4j
@RequiredArgsConstructor
public class DefaultPublishMsgConsumerService implements PublishMsgConsumerService {

    private volatile ExecutorService consumersExecutor;
    private volatile boolean stopped = false;

    @Value("${queue.publish-msg.consumers-count}")
    private int consumersCount;

    @Value("${queue.publish-msg.poll-interval}")
    private long pollDuration;

    private final List<TbQueueConsumer<TbProtoQueueMsg<PublishMsgProto>>> publishMsgConsumers = new ArrayList<>();
    private final MsgDispatcherService msgDispatcherService;
    private final PublishMsgQueueFactory publishMsgQueueFactory;
    private final StatsManager statsManager;


    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationEvent(ApplicationReadyEvent event) {
        for (int i = 0; i < consumersCount; i++) {
            publishMsgConsumers.add(publishMsgQueueFactory.createConsumer());
        }
        this.consumersExecutor = Executors.newCachedThreadPool(ThingsBoardThreadFactory.forName("publish-msg-consumer"));
        for (TbQueueConsumer<TbProtoQueueMsg<PublishMsgProto>> publishMsgConsumer : publishMsgConsumers) {
            publishMsgConsumer.subscribe();
            launchConsumer(publishMsgConsumer);
        }
    }

    private void launchConsumer(TbQueueConsumer<TbProtoQueueMsg<PublishMsgProto>> consumer) {
        consumersExecutor.submit(() -> {
            MessagesStats stats = statsManager.createPublishMsgConsumerStats();
            while (!stopped) {
                try {
                    List<TbProtoQueueMsg<PublishMsgProto>> msgs = consumer.poll(pollDuration);
                    if (msgs.isEmpty()) {
                        continue;
                    }
                    stats.incrementTotal(msgs.size());
                    for (TbProtoQueueMsg<PublishMsgProto> msg : msgs) {
                        // cannot track 'failed' messages, because this method shouldn't fail (unless code error)
                        msgDispatcherService.processPublishMsg(msg.getValue());
                    }
                    consumer.commit();
                    stats.incrementSuccessful(msgs.size());
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
            log.info("Publish Msg Consumer stopped.");
        });
    }


    @PreDestroy
    public void destroy() {
        stopped = true;
        publishMsgConsumers.forEach(TbQueueConsumer::unsubscribe);
        if (consumersExecutor != null) {
            consumersExecutor.shutdownNow();
        }
    }
}
