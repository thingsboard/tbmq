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
package org.thingsboard.mqtt.broker.service.processing.downlink.basic;

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
import org.thingsboard.mqtt.broker.service.processing.downlink.DownLinkPublisherHelper;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BasicDownLinkConsumerImpl implements BasicDownLinkConsumer {
    private final ExecutorService consumersExecutor = Executors.newCachedThreadPool(ThingsBoardThreadFactory.forName("basic-downlink-publish-msg-consumer"));

    private volatile boolean stopped = false;

    private final List<TbQueueConsumer<TbProtoQueueMsg<QueueProtos.ClientPublishMsgProto>>> consumers = new ArrayList<>();

    private final DownLinkBasicPublishMsgQueueFactory downLinkBasicPublishMsgQueueFactory;
    private final ServiceInfoProvider serviceInfoProvider;
    private final DownLinkPublisherHelper downLinkPublisherHelper;

    private final BasicDownLinkProcessor processor;

    @Value("${queue.basic-downlink-publish-msg.consumers-count}")
    private int consumersCount;
    @Value("${queue.basic-downlink-publish-msg.poll-interval}")
    private long pollDuration;


    @Override
    public void startConsuming() {
        String topic = downLinkPublisherHelper.getBasicDownLinkServiceTopic(serviceInfoProvider.getServiceId());
        String uniqueGroupId = serviceInfoProvider.getServiceId() + System.currentTimeMillis();
        for (int i = 0; i < consumersCount; i++) {
            String consumerId = serviceInfoProvider.getServiceId() + "-" + i;
            TbQueueConsumer<TbProtoQueueMsg<QueueProtos.ClientPublishMsgProto>> consumer = downLinkBasicPublishMsgQueueFactory.createConsumer(topic, consumerId, uniqueGroupId);
            consumers.add(consumer);
            consumer.subscribe();
            launchConsumer(consumerId, consumer);
        }
    }

    private void launchConsumer(String consumerId, TbQueueConsumer<TbProtoQueueMsg<QueueProtos.ClientPublishMsgProto>> consumer) {
        consumersExecutor.submit(() -> {
            while (!stopped) {
                try {
                    List<TbProtoQueueMsg<QueueProtos.ClientPublishMsgProto>> msgs = consumer.poll(pollDuration);
                    if (msgs.isEmpty()) {
                        continue;
                    }

                    for (TbProtoQueueMsg<QueueProtos.ClientPublishMsgProto> msg : msgs) {
                        QueueProtos.ClientPublishMsgProto clientPublishMsgProto = msg.getValue();
                        processor.process(clientPublishMsgProto.getClientId(), clientPublishMsgProto.getPublishMsg());
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
