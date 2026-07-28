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
package org.thingsboard.mqtt.broker.service.entity.integration;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.integration.ClientLifecycleEventTypeUtil;
import org.thingsboard.mqtt.broker.common.data.integration.Integration;
import org.thingsboard.mqtt.broker.dao.integration.IntegrationService;
import org.thingsboard.mqtt.broker.gen.queue.IntegrationLifecycleConfigProto;
import org.thingsboard.mqtt.broker.gen.queue.InternodeNotificationProto;
import org.thingsboard.mqtt.broker.service.entity.AbstractTbEntityService;
import org.thingsboard.mqtt.broker.service.integration.PlatformIntegrationService;
import org.thingsboard.mqtt.broker.service.limits.RateLimitService;
import org.thingsboard.mqtt.broker.service.notification.InternodeNotificationsService;
import org.thingsboard.mqtt.broker.service.queue.IntegrationTopicService;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultTbIntegrationService extends AbstractTbEntityService implements TbIntegrationService {

    private final IntegrationService integrationService;
    private final PlatformIntegrationService platformIntegrationService;
    private final RateLimitService rateLimitService;
    private final InternodeNotificationsService internodeNotificationsService;
    private final IntegrationTopicService integrationTopicService;

    @Override
    public Integration save(Integration integration, User currentUser) {
        boolean created = integration.getId() == null;
        Integration result = integrationService.saveIntegration(integration);
        platformIntegrationService.processIntegrationUpdate(result, created);
        createEventTopicIfOptedIn(result);
        internodeNotificationsService.broadcast(
                InternodeNotificationProto.newBuilder()
                        .setIntegrationLifecycleConfigProto(toLifecycleConfigProto(result))
                        .build());
        return result;
    }

    @Override
    public void delete(Integration integration, User currentUser) {
        boolean removed = integrationService.deleteIntegration(integration);
        if (removed) {
            rateLimitService.decrementApplicationClientsCount();
        }
        platformIntegrationService.processIntegrationDelete(integration, removed);
        internodeNotificationsService.broadcast(
                InternodeNotificationProto.newBuilder()
                        .setIntegrationLifecycleConfigProto(IntegrationLifecycleConfigProto.newBuilder()
                                .setIntegrationId(integration.getIdStr())
                                .setDeleted(true)
                                .build())
                        .build());
    }

    @Override
    public void restart(Integration integration, User currentUser) throws ThingsboardException {
        platformIntegrationService.processIntegrationRestart(integration);
    }

    /**
     * Provisions the dedicated lifecycle-events topic as soon as an integration opts in, before the config is
     * broadcast and any node starts publishing events. The events producer deliberately does not create topics
     * (see KafkaIntegrationMsgQueueFactory.createEventProducer): events are sent synchronously on the MQTT
     * processing thread, where a blocking admin call - or a missing topic stalling the send for max.block.ms -
     * is not acceptable. The Integration Executor also provisions it on start, so this only covers the window
     * before the integration is first enabled; both calls are idempotent.
     */
    private void createEventTopicIfOptedIn(Integration integration) {
        if (!ClientLifecycleEventTypeUtil.isOptedIn(integration.getConfiguration())) {
            return;
        }
        try {
            integrationTopicService.createEventTopic(integration.getIdStr());
        } catch (Exception e) {
            // The integration is already persisted; failing the request would be misleading. Events published
            // before the topic exists are dropped and counted, and the IE recreates it once the integration starts.
            log.warn("[{}][{}] Failed to create the lifecycle events topic", integration.getId(), integration.getName(), e);
        }
    }

    private IntegrationLifecycleConfigProto toLifecycleConfigProto(Integration integration) {
        IntegrationLifecycleConfigProto.Builder builder = IntegrationLifecycleConfigProto.newBuilder()
                .setIntegrationId(integration.getIdStr())
                .setDeleted(false);
        JsonNode configuration = integration.getConfiguration();
        if (configuration != null && configuration.has(ClientLifecycleEventTypeUtil.LIFECYCLE_EVENT_TYPES_KEY)) {
            configuration.get(ClientLifecycleEventTypeUtil.LIFECYCLE_EVENT_TYPES_KEY)
                    .forEach(node -> builder.addLifecycleEventTypes(node.asText()));
        }
        return builder.build();
    }

}
