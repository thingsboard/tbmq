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
import org.thingsboard.mqtt.broker.service.auth.AuthorizationRoutingService;
import org.thingsboard.mqtt.broker.service.auth.providers.MqttAuthProviderNotificationManager;
import org.thingsboard.mqtt.broker.service.integration.IntegrationLifecycleEventTypeCache;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionStatsCleanupProcessor;

@Slf4j
@Service
@RequiredArgsConstructor
public class InternodeNotificationsServiceImpl implements InternodeNotificationsService {

    private final InternodeNotificationsQueueFactory internodeNotificationsQueueFactory;
    private final ServiceInfoProvider serviceInfoProvider;
    private final InternodeNotificationsHelper helper;

    private final MqttAuthProviderNotificationManager mqttClientAuthProviderManager;
    private final ClientSessionStatsCleanupProcessor clientSessionStatsCleanupProcessor;
    private final AuthorizationRoutingService authorizationRoutingService;
    private final IntegrationLifecycleEventTypeCache integrationLifecycleEventTypeCache;

    private TbQueueProducer<TbProtoQueueMsg<InternodeNotificationProto>> internodeNotificationsProducer;

    @PostConstruct
    public void init() {
        this.internodeNotificationsProducer = internodeNotificationsQueueFactory.createProducer(serviceInfoProvider.getServiceId());
    }

    /**
     * The local update goes first and unconditionally, ahead of the registry read: it is in-process, so it must not
     * depend on Redis, on this node being registered, or on any remote send - TbmqSystemInfoService.getTbmqServiceIds
     * maps an unordered hash, so this node's own id can come last. Per-node sends are isolated from each other; a
     * failure in the registry read or the local update still propagates, since then nothing was notified at all.
     */
    @Override
    public void broadcast(InternodeNotificationProto notificationProto) {
        applyLocally(notificationProto);
        for (String serviceId : helper.getServiceIds()) {
            if (isMyNode(serviceId)) {
                // Already applied in-process; this node only consumes what others send it.
                continue;
            }
            broadcastToNode(serviceId, notificationProto);
        }
    }

    private void applyLocally(InternodeNotificationProto notificationProto) {
        String serviceId = serviceInfoProvider.getServiceId();
        if (notificationProto.hasMqttAuthSettingsProto()) {
            log.trace("[{}] Forwarding message to local MQTT authorization routing service {}", serviceId, notificationProto.getMqttAuthSettingsProto());
            authorizationRoutingService.onMqttAuthSettingsUpdate(notificationProto.getMqttAuthSettingsProto());
            return;
        }
        if (notificationProto.hasMqttAuthProviderProto()) {
            log.trace("[{}] Forwarding message to local MQTT auth provider manager {}", serviceId, notificationProto.getMqttAuthProviderProto());
            mqttClientAuthProviderManager.handleProviderNotification(notificationProto.getMqttAuthProviderProto());
            return;
        }
        if (notificationProto.hasClientSessionStatsCleanupProto()) {
            log.trace("[{}] Forwarding message to local MQTT client session stats cleanup processor {}", serviceId, notificationProto.getClientSessionStatsCleanupProto());
            clientSessionStatsCleanupProcessor.processClientSessionStatsCleanup(notificationProto.getClientSessionStatsCleanupProto());
            return;
        }
        if (notificationProto.hasIntegrationLifecycleConfigProto()) {
            log.trace("[{}] Forwarding message to local integration lifecycle event type cache {}", serviceId, notificationProto.getIntegrationLifecycleConfigProto());
            integrationLifecycleEventTypeCache.processIntegrationLifecycleConfig(notificationProto.getIntegrationLifecycleConfigProto());
        }
    }

    private boolean isMyNode(String serviceId) {
        return serviceInfoProvider.getServiceId().equals(serviceId);
    }

    /**
     * Contained on failure, so one unreachable node cannot drop the notification for the nodes after it. The send can
     * throw: TbKafkaProducerTemplate.send calls createTopicIfNotExists first, and TbKafkaAdmin.createTopic rethrows.
     */
    private void broadcastToNode(String serviceId, InternodeNotificationProto notificationProto) {
        String topic = helper.getServiceTopic(serviceId);
        TbQueueCallback callback = new TbQueueCallback() {
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
        };
        try {
            internodeNotificationsProducer.send(topic, null, new TbProtoQueueMsg<>(serviceId, notificationProto), callback);
        } catch (Exception e) {
            // Routed through the same callback so a synchronous failure is logged like an asynchronous one.
            callback.onFailure(e);
        }
    }

    @PreDestroy
    public void destroy() {
        if (internodeNotificationsProducer != null) {
            internodeNotificationsProducer.stop();
        }
    }

}
