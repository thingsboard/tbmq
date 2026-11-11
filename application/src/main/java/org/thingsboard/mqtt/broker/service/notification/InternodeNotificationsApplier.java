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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.gen.queue.InternodeNotificationProto;
import org.thingsboard.mqtt.broker.service.auth.AuthorizationRoutingService;
import org.thingsboard.mqtt.broker.service.auth.providers.MqttAuthProviderNotificationManager;
import org.thingsboard.mqtt.broker.service.integration.IntegrationLifecycleEventTypeCache;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionStatsCleanupProcessor;

@Slf4j
@Component
@RequiredArgsConstructor
public class InternodeNotificationsApplier {

    private final MqttAuthProviderNotificationManager mqttAuthProviderNotificationManager;
    private final ClientSessionStatsCleanupProcessor clientSessionStatsCleanupProcessor;
    private final AuthorizationRoutingService authorizationRoutingService;
    private final IntegrationLifecycleEventTypeCache integrationLifecycleEventTypeCache;

    public void apply(String serviceId, InternodeNotificationProto notificationProto) {
        if (notificationProto.hasMqttAuthSettingsProto()) {
            log.trace("[{}] Forwarding message to local MQTT authorization routing service {}", serviceId, notificationProto.getMqttAuthSettingsProto());
            authorizationRoutingService.onMqttAuthSettingsUpdate(notificationProto.getMqttAuthSettingsProto());
            return;
        }
        if (notificationProto.hasMqttAuthProviderProto()) {
            log.trace("[{}] Forwarding message to local MQTT auth provider manager {}", serviceId, notificationProto.getMqttAuthProviderProto());
            mqttAuthProviderNotificationManager.handleProviderNotification(notificationProto.getMqttAuthProviderProto());
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

}
