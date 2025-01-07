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
package org.thingsboard.mqtt.broker.service.processing.downlink.persistent;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.queue.TbQueueConsumer;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.DownLinkPersistentPublishMsgQueueFactory;
import org.thingsboard.mqtt.broker.service.processing.downlink.DownLinkPublisherHelper;
import org.thingsboard.mqtt.broker.util.MqttPropertiesUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

@Slf4j
@Service
@RequiredArgsConstructor
public class PersistentDownLinkConsumerImpl implements PersistentDownLinkConsumer {

    private final List<TbQueueConsumer<TbProtoQueueMsg<QueueProtos.DevicePublishMsgProto>>> consumers = new ArrayList<>();

    private final DownLinkPersistentPublishMsgQueueFactory downLinkPersistentPublishMsgQueueFactory;
    private final ServiceInfoProvider serviceInfoProvider;
    private final DownLinkPublisherHelper downLinkPublisherHelper;
    private final PersistentDownLinkProcessor processor;
    private final TbQueueAdmin queueAdmin;

    @Value("${queue.persisted-downlink-msg.consumers-count}")
    private int consumersCount;
    @Value("${queue.persisted-downlink-msg.poll-interval}")
    private long pollDuration;
    @Value("${queue.persisted-downlink-msg.threads-count}")
    private int threadsCount;

    private volatile boolean stopped = false;
    private ExecutorService consumersExecutor;

    @PostConstruct
    public void init() {
        this.consumersExecutor = ThingsBoardExecutors.initExecutorService(threadsCount, "persisted-downlink-msg-consumer");
    }

    @Override
    public void startConsuming() {
        String topic = downLinkPublisherHelper.getPersistentDownLinkServiceTopic(serviceInfoProvider.getServiceId());
        long currentCgSuffix = System.currentTimeMillis();
        String uniqueGroupId = serviceInfoProvider.getServiceId() + "-" + currentCgSuffix;
        for (int i = 0; i < consumersCount; i++) {
            String consumerId = serviceInfoProvider.getServiceId() + "-" + i;
            TbQueueConsumer<TbProtoQueueMsg<QueueProtos.DevicePublishMsgProto>> consumer = downLinkPersistentPublishMsgQueueFactory
                    .createConsumer(topic, consumerId, uniqueGroupId);
            consumers.add(consumer);
            consumer.subscribe();
            launchConsumer(consumerId, consumer);
        }
        queueAdmin.deleteOldConsumerGroups(BrokerConstants.PERSISTED_DOWNLINK_CG_PREFIX, serviceInfoProvider.getServiceId(), currentCgSuffix);
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
                        DevicePublishMsg devicePublishMsg = ProtoConverter.protoToDevicePublishMsg(msg.getValue());
                        MqttPropertiesUtil.addMsgExpiryIntervalToProps(devicePublishMsg.getProperties(), msg.getHeaders());
                        processor.process(msg.getKey(), devicePublishMsg);
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
            ThingsBoardExecutors.shutdownAndAwaitTermination(consumersExecutor, "Persistent downlink consumer");
        }
    }

    private void deleteUniqueConsumerGroup() {
        if (!CollectionUtils.isEmpty(consumers)) {
            TbQueueConsumer<TbProtoQueueMsg<QueueProtos.DevicePublishMsgProto>> consumer = consumers.get(0);
            if (consumer.getConsumerGroupId() != null) {
                queueAdmin.deleteConsumerGroups(Collections.singleton(consumer.getConsumerGroupId()));
            }
        }
    }
}
