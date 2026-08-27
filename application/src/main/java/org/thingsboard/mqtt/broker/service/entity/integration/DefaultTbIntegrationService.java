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

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultTbIntegrationService extends AbstractTbEntityService implements TbIntegrationService {

    private final IntegrationService integrationService;
    private final PlatformIntegrationService platformIntegrationService;
    private final RateLimitService rateLimitService;
    private final InternodeNotificationsService internodeNotificationsService;

    @Override
    public Integration save(Integration integration, User currentUser) {
        boolean created = integration.getId() == null;
        Integration result = integrationService.saveIntegration(integration);
        platformIntegrationService.processIntegrationUpdate(result, created);
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
        // Detach the events stream before processIntegrationDelete deletes the topics. The event type cache is
        // node-local while the topics go for the whole cluster, and nothing on the publish path checks whether the
        // integration still exists, so a node that still has the event types cached recreates the events topic on its
        // next event through the producer's createTopicIfNotExists. That leak is permanent: the row is gone, and the
        // cleanup sweep only ever reaches a topic through its row. IntegrationCleanupServiceImpl.cleanUpIfExpired
        // orders the two the same way.
        //
        // This closes the window on this node, where the broadcast evicts in-process and synchronously, and only
        // narrows it on the others - broadcast returns once the notification is produced, not once they applied it.
        // Unconditional and safe here: with the row already gone there is no re-enable for the eviction to race,
        // unlike in the sweep, whose row survives.
        //
        // The finally is what makes that ordering affordable: broadcast reads Redis and produces to Kafka, whose admin
        // call rethrows, and with the row gone a skipped cleanup would leak both topics permanently. The local eviction
        // still happens - broadcast applies it before it reads the registry or sends anything.
        try {
            internodeNotificationsService.broadcast(
                    InternodeNotificationProto.newBuilder()
                            .setIntegrationLifecycleConfigProto(IntegrationLifecycleConfigProto.newBuilder()
                                    .setIntegrationId(integration.getIdStr())
                                    .setDeleted(true)
                                    .build())
                            .build());
        } finally {
            platformIntegrationService.processIntegrationDelete(integration, removed);
        }
    }

    @Override
    public void restart(Integration integration, User currentUser) throws ThingsboardException {
        platformIntegrationService.processIntegrationRestart(integration);
    }

    private IntegrationLifecycleConfigProto toLifecycleConfigProto(Integration integration) {
        IntegrationLifecycleConfigProto.Builder builder = IntegrationLifecycleConfigProto.newBuilder()
                .setIntegrationId(integration.getIdStr())
                .setDeleted(false);
        JsonNode configuration = integration.getConfiguration();
        if (ClientLifecycleEventTypeUtil.isOptedIn(configuration)) {
            configuration.get(ClientLifecycleEventTypeUtil.LIFECYCLE_EVENT_TYPES_KEY)
                    .forEach(node -> builder.addLifecycleEventTypes(node.asText()));
        }
        return builder.build();
    }

}
