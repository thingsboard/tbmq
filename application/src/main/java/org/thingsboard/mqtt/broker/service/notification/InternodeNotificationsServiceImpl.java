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
     * The local update is applied first and unconditionally, ahead of the service registry read: it is in-process, so
     * it has no business depending on Redis being reachable, on this node being currently registered, or on any remote
     * send succeeding. {@code DefaultTbIntegrationService.delete} relies on exactly that to close its events-topic
     * leak window on the deleting node, and {@code getServiceIds} maps an unordered Redis hash
     * (TbmqSystemInfoService.getTbmqServiceIds), so this node's own id can come last.
     * <p>
     * Per-node sends are isolated from each other. The registry read and the local update are not: a failure in either
     * means nothing was notified at all, which the caller should see rather than have logged away. Applying locally
     * first does mean a local handler that throws - {@code MqttAuthProviderNotificationManagerImpl} parses the
     * configuration JSON, so it can - now sends to nobody instead of to whichever nodes happened to precede this one
     * in an unordered list. That is the better failure: an all-or-nothing broadcast the caller is told about, rather
     * than an arbitrary subset of the cluster silently diverging from the rest.
     */
    @Override
    public void broadcast(InternodeNotificationProto notificationProto) {
        applyLocally(notificationProto);
        for (String serviceId : helper.getServiceIds()) {
            if (isMyNode(serviceId)) {
                // Already applied in-process above; this node consumes only what other nodes send it.
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
     * Contained on failure, so one unreachable node cannot drop the notification for the nodes after it in the list.
     * The send is not fire-and-forget: TbKafkaProducerTemplate.send calls createTopicIfNotExists first - wired for
     * this producer by KafkaInternodeNotificationsQueueFactory.createProducer with the flag left at its default - and
     * TbKafkaAdmin.createTopic rethrows as a RuntimeException, while KafkaProducer.send can throw synchronously too.
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
