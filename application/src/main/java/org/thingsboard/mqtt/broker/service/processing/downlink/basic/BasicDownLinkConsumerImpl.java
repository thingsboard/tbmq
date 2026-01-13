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
package org.thingsboard.mqtt.broker.service.processing.downlink.basic;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;
import org.thingsboard.mqtt.broker.gen.queue.ClientPublishMsgProto;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.queue.TbQueueConsumer;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.DownLinkBasicPublishMsgQueueFactory;
import org.thingsboard.mqtt.broker.service.processing.downlink.DownLinkPublisherHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

@Slf4j
@Service
@RequiredArgsConstructor
public class BasicDownLinkConsumerImpl implements BasicDownLinkConsumer {

    private final List<TbQueueConsumer<TbProtoQueueMsg<ClientPublishMsgProto>>> consumers = new ArrayList<>();

    private final DownLinkBasicPublishMsgQueueFactory downLinkBasicPublishMsgQueueFactory;
    private final ServiceInfoProvider serviceInfoProvider;
    private final DownLinkPublisherHelper downLinkPublisherHelper;
    private final BasicDownLinkProcessor processor;
    private final TbQueueAdmin queueAdmin;

    @Value("${queue.basic-downlink-msg.consumers-count}")
    private int consumersCount;
    @Value("${queue.basic-downlink-msg.poll-interval}")
    private long pollDuration;
    @Value("${queue.basic-downlink-msg.threads-count}")
    private int threadsCount;

    private volatile boolean stopped = false;
    private ExecutorService consumersExecutor;

    @PostConstruct
    public void init() {
        this.consumersExecutor = ThingsBoardExecutors.initExecutorService(threadsCount, "basic-downlink-msg-consumer");
    }

    @Override
    public void startConsuming() {
        String topic = downLinkPublisherHelper.getBasicDownLinkServiceTopic(serviceInfoProvider.getServiceId());
        long currentCgSuffix = System.currentTimeMillis();
        String uniqueGroupId = serviceInfoProvider.getServiceId() + "-" + currentCgSuffix;
        for (int i = 0; i < consumersCount; i++) {
            String consumerId = serviceInfoProvider.getServiceId() + "-" + i;
            TbQueueConsumer<TbProtoQueueMsg<ClientPublishMsgProto>> consumer = downLinkBasicPublishMsgQueueFactory.createConsumer(topic, consumerId, uniqueGroupId);
            consumers.add(consumer);
            consumer.subscribe();
            launchConsumer(consumerId, consumer);
        }
        queueAdmin.deleteOldConsumerGroups(BrokerConstants.BASIC_DOWNLINK_CG_PREFIX, serviceInfoProvider.getServiceId(), currentCgSuffix);
    }

    private void launchConsumer(String consumerId, TbQueueConsumer<TbProtoQueueMsg<ClientPublishMsgProto>> consumer) {
        consumersExecutor.submit(() -> {
            while (!stopped) {
                try {
                    List<TbProtoQueueMsg<ClientPublishMsgProto>> msgs = consumer.poll(pollDuration);
                    if (msgs.isEmpty()) {
                        continue;
                    }

                    for (TbProtoQueueMsg<ClientPublishMsgProto> msg : msgs) {
                        ClientPublishMsgProto clientPublishMsgProto = msg.getValue();
                        processor.process(clientPublishMsgProto.getClientId(), clientPublishMsgProto.getPublishMsg());
                    }
                    consumer.commitSync();
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
        deleteUniqueConsumerGroup();
        if (consumersExecutor != null) {
            ThingsBoardExecutors.shutdownAndAwaitTermination(consumersExecutor, "Basic donwlink consumer");
        }
    }

    private void deleteUniqueConsumerGroup() {
        if (!CollectionUtils.isEmpty(consumers)) {
            TbQueueConsumer<TbProtoQueueMsg<ClientPublishMsgProto>> consumer = consumers.get(0);
            if (consumer.getConsumerGroupId() != null) {
                queueAdmin.deleteConsumerGroups(Collections.singleton(consumer.getConsumerGroupId()));
            }
        }
    }
}
