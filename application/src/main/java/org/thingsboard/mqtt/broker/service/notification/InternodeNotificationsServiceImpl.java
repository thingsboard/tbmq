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

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.gen.queue.InternodeNotificationProto;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.InternodeNotificationsQueueFactory;
import org.thingsboard.mqtt.broker.service.auth.providers.MqttAuthProviderManager;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InternodeNotificationsServiceImpl implements InternodeNotificationsService {

    private final InternodeNotificationsQueueFactory internodeNotificationsQueueFactory;
    private final ServiceInfoProvider serviceInfoProvider;
    private final InternodeNotificationsHelper helper;

    private final MqttAuthProviderManager mqttAuthProviderManager;

    private TbQueueProducer<TbProtoQueueMsg<InternodeNotificationProto>> internodeNotificationsProducer;

    @PostConstruct
    public void init() {
        this.internodeNotificationsProducer = internodeNotificationsQueueFactory.createProducer(serviceInfoProvider.getServiceId());
    }

    @Override
    public void broadcast(InternodeNotificationProto notificationProto) {
        List<String> serviceIds = helper.getServiceIds();
        for (String serviceId : serviceIds) {
            if (!isMyNode(serviceId)) {
                broadcastToNode(serviceId, notificationProto);
                continue;
            }
            if (notificationProto.hasMqttAuthProviderProto()) {
                mqttAuthProviderManager
                        .handleProviderNotification(notificationProto.getMqttAuthProviderProto());
            }
        }
    }

    private boolean isMyNode(String serviceId) {
        return serviceInfoProvider.getServiceId().equals(serviceId);
    }

    private void broadcastToNode(String serviceId, InternodeNotificationProto notificationProto) {
        String topic = helper.getServiceTopic(serviceId);
        internodeNotificationsProducer.send(topic, null, new TbProtoQueueMsg<>(serviceId, notificationProto), new TbQueueCallback() {
            @Override
            public void onSuccess(TbQueueMsgMetadata metadata) {
                if (log.isTraceEnabled()) {
                    log.trace("[{}] Notification for broker node {} sent successfully.", serviceId, notificationProto);
                }
            }

            @Override
            public void onFailure(Throwable t) {
                log.warn("[{}] Failed to send notification for broker node {}.", serviceId, notificationProto, t);
            }
        });
    }

    @PreDestroy
    public void destroy() {
        if (internodeNotificationsProducer != null) {
            internodeNotificationsProducer.stop();
        }
    }

}
