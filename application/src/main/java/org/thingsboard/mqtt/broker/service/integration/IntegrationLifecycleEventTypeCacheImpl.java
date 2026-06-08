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

import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.integration.ClientLifecycleEventType;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IntegrationLifecycleEventTypeCacheImpl implements IntegrationLifecycleEventTypeCache {

    private final Map<String, Set<ClientLifecycleEventType>> byIntegrationId = new ConcurrentHashMap<>();

    @Override
    public void put(String integrationId, Set<ClientLifecycleEventType> eventTypes) {
        if (eventTypes == null || eventTypes.isEmpty()) {
            byIntegrationId.remove(integrationId);
        } else {
            byIntegrationId.put(integrationId, Collections.unmodifiableSet(eventTypes));
        }
    }

    @Override
    public void remove(String integrationId) {
        byIntegrationId.remove(integrationId);
    }

    @Override
    public Set<String> getIntegrationIds(ClientLifecycleEventType eventType) {
        Set<String> result = ConcurrentHashMap.newKeySet();
        byIntegrationId.forEach((integrationId, eventTypes) -> {
            if (eventTypes.contains(eventType)) {
                result.add(integrationId);
            }
        });
        return result;
    }

}
