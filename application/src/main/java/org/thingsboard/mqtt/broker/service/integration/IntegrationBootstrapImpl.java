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
package org.thingsboard.mqtt.broker.service.integration;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.integration.ClientLifecycleEventType;
import org.thingsboard.mqtt.broker.common.data.integration.ClientLifecycleEventTypeUtil;
import org.thingsboard.mqtt.broker.common.data.integration.Integration;
import org.thingsboard.mqtt.broker.common.util.edition.ClusterComponent;
import org.thingsboard.mqtt.broker.dao.integration.IntegrationService;

import java.util.List;
import java.util.Set;

@Slf4j
@Component
@ClusterComponent
@RequiredArgsConstructor
public class IntegrationBootstrapImpl implements IntegrationBootstrap {

    private final IntegrationService integrationService;
    private final IntegrationLifecycleEventTypeCache lifecycleEventTypeCache;
    private final IntegrationExpiryChecker expiryChecker;

    @Override
    public void loadLifecycleEventCache() {
        List<Integration> integrations = integrationService.findAllIntegrations();
        int cached = 0;
        for (Integration integration : integrations) {
            JsonNode configuration = integration.getConfiguration();
            if (!ClientLifecycleEventTypeUtil.isOptedIn(configuration)) {
                continue;
            }
            if (expiryChecker.isExpired(integration)) {
                // The cleanup sweep already detached this integration, or is about to. Re-attaching it here would
                // resume lifecycle events for it until the next sweep - up to integrations.cleanup.period later -
                // and would make the sweep redo its full work after every restart.
                log.debug("[{}][{}] Expired disconnected integration, not attaching it to the events stream",
                        integration.getId(), integration.getName());
                continue;
            }
            Set<ClientLifecycleEventType> eventTypes = ClientLifecycleEventTypeUtil.parse(
                    configuration.get(ClientLifecycleEventTypeUtil.LIFECYCLE_EVENT_TYPES_KEY),
                    name -> log.warn("[{}] Unknown lifecycle event type: {}", integration.getId(), name));
            if (!eventTypes.isEmpty()) {
                lifecycleEventTypeCache.put(integration.getIdStr(), eventTypes);
                cached++;
            }
        }
        log.info("Loaded lifecycle event type cache: cached {} of {} integrations.", cached, integrations.size());
    }

    @Override
    public int integrationsCount() {
        return integrationService.findIntegrationsCount();
    }

}
