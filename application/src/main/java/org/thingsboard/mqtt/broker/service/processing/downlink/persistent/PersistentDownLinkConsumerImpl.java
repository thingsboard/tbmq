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
package org.thingsboard.mqtt.broker.service.processing.downlink.persistent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueConsumer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.DownLinkPersistentPublishMsgQueueFactory;
import org.thingsboard.mqtt.broker.service.processing.downlink.DownLinkPublisherHelper;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PersistentDownLinkConsumerImpl implements PersistentDownLinkConsumer {
    private final ExecutorService consumersExecutor = Executors.newCachedThreadPool(ThingsBoardThreadFactory.forName("persistent-downlink-publish-msg-consumer"));

    private volatile boolean stopped = false;

    private final List<TbQueueConsumer<TbProtoQueueMsg<QueueProtos.DevicePublishMsgProto>>> consumers = new ArrayList<>();

    private final DownLinkPersistentPublishMsgQueueFactory downLinkPersistentPublishMsgQueueFactory;
    private final ServiceInfoProvider serviceInfoProvider;
    private final DownLinkPublisherHelper downLinkPublisherHelper;
    private final PersistentDownLinkProcessor processor;

    // TODO: don't push msg to kafka if it's the same serviceId
    @Value("${queue.persistent-downlink-publish-msg.consumers-count}")
    private int consumersCount;
    @Value("${queue.persistent-downlink-publish-msg.poll-interval}")
    private long pollDuration;


    @Override
    public void startConsuming() {
        String topic = downLinkPublisherHelper.getPersistentDownLinkServiceTopic(serviceInfoProvider.getServiceId());
        String uniqueGroupId = serviceInfoProvider.getServiceId() + System.currentTimeMillis();
        for (int i = 0; i < consumersCount; i++) {
            String consumerId = serviceInfoProvider.getServiceId() + "-" + i;
            TbQueueConsumer<TbProtoQueueMsg<QueueProtos.DevicePublishMsgProto>> consumer = downLinkPersistentPublishMsgQueueFactory
                    .createConsumer(topic, consumerId, uniqueGroupId);
            consumers.add(consumer);
            consumer.subscribe();
            launchConsumer(consumerId, consumer);
        }
    }

    private void launchConsumer(String consumerId, TbQueueConsumer<TbProtoQueueMsg<QueueProtos.DevicePublishMsgProto>> consumer) {
        consumersExecutor.submit(() -> {
            while (!stopped) {
                try {
                    List<TbProtoQueueMsg<QueueProtos.DevicePublishMsgProto>> msgs = consumer.poll(pollDuration);
                    if (msgs.isEmpty()) {
                        continue;
                    }

                    for (TbProtoQueueMsg<QueueProtos.DevicePublishMsgProto> msg : msgs) {
                        processor.process(msg.getKey(), msg.getValue());
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
        consumers.forEach(TbQueueConsumer::unsubscribeAndClose);
        consumersExecutor.shutdownNow();
    }
}
