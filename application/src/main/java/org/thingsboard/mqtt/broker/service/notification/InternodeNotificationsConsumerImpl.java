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
package org.thingsboard.mqtt.broker.service.notification;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.gen.queue.InternodeNotificationProto;
import org.thingsboard.mqtt.broker.queue.TbQueueConsumer;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.InternodeNotificationsQueueFactory;
import org.thingsboard.mqtt.broker.service.auth.providers.MqttClientAuthProviderManager;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
@RequiredArgsConstructor
public class InternodeNotificationsConsumerImpl implements InternodeNotificationsConsumer {

    private final ExecutorService consumerExecutor = Executors.newSingleThreadExecutor(ThingsBoardThreadFactory.forName("internode-notifications-consumer"));
    private final InternodeNotificationsQueueFactory internodeNotificationsQueueFactory;
    private final InternodeNotificationsHelperImpl helper;
    private final ServiceInfoProvider serviceInfoProvider;
    private final MqttClientAuthProviderManager mqttClientAuthProviderManager;

    private volatile boolean stopped = false;

    @Value("${queue.internode-notifications.poll-interval}")
    private long pollDuration;

    private TbQueueConsumer<TbProtoQueueMsg<InternodeNotificationProto>> consumer;

    @Override
    public void startConsuming() {
        initConsumer();
        consumerExecutor.execute(this::processInternodeNotifications);
    }

    private void processInternodeNotifications() {
        while (!stopped) {
            try {
                List<TbProtoQueueMsg<InternodeNotificationProto>> msgs = consumer.poll(pollDuration);
                if (msgs.isEmpty()) {
                    continue;
                }
                for (TbProtoQueueMsg<InternodeNotificationProto> msg : msgs) {
                    processInternodeNotification(msg);
                }
                consumer.commitSync();
            } catch (Exception e) {
                if (!stopped) {
                    log.error("Failed to process internode notification messages from queue.", e);
                    try {
                        Thread.sleep(pollDuration);
                    } catch (InterruptedException e2) {
                        if (log.isTraceEnabled()) {
                            log.trace("Failed to wait until the server has capacity to handle new requests", e2);
                        }
                    }
                }
            }
        }
        log.info("Node Notification Consumer stopped.");
    }

    private void processInternodeNotification(TbProtoQueueMsg<InternodeNotificationProto> msg) {
        // TODO: how to use this key?
        String key = msg.getKey();
        InternodeNotificationProto notificationProto = msg.getValue();
        if (notificationProto.hasMqttClientAuthProviderProto()) {
            mqttClientAuthProviderManager.handleProviderNotification(notificationProto.getMqttClientAuthProviderProto());
        }
    }

    private void initConsumer() {
        String serviceId = serviceInfoProvider.getServiceId();
        String topic = helper.getServiceTopic(serviceId);
        this.consumer = internodeNotificationsQueueFactory.createConsumer(topic, serviceId);
        this.consumer.subscribe();
    }

    @PreDestroy
    public void destroy() {
        stopped = true;
        ThingsBoardExecutors.shutdownAndAwaitTermination(consumerExecutor, "Internode notifications");
        if (consumer != null) {
            consumer.unsubscribeAndClose();
        }
    }
}
