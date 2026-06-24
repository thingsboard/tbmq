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

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IntegrationLifecycleEventTypeCacheImpl implements IntegrationLifecycleEventTypeCache {

    private final Map<String, Set<ClientLifecycleEventType>> byIntegrationId = new ConcurrentHashMap<>();

    private volatile Map<ClientLifecycleEventType, Set<String>> byEventType = Map.of();

    @Override
    public synchronized void put(String integrationId, Set<ClientLifecycleEventType> eventTypes) {
        if (eventTypes == null || eventTypes.isEmpty()) {
            byIntegrationId.remove(integrationId);
        } else {
            byIntegrationId.put(integrationId, Set.copyOf(eventTypes));
        }
        rebuildReverseIndex();
    }

    @Override
    public synchronized void remove(String integrationId) {
        byIntegrationId.remove(integrationId);
        rebuildReverseIndex();
    }

    @Override
    public Set<String> getIntegrationIds(ClientLifecycleEventType eventType) {
        return byEventType.getOrDefault(eventType, Set.of());
    }

    private void rebuildReverseIndex() {
        Map<ClientLifecycleEventType, Set<String>> mutable = new HashMap<>();
        byIntegrationId.forEach((integrationId, eventTypes) ->
                eventTypes.forEach(eventType ->
                        mutable.computeIfAbsent(eventType, k -> new HashSet<>()).add(integrationId)));
        Map<ClientLifecycleEventType, Set<String>> immutable = new EnumMap<>(ClientLifecycleEventType.class);
        mutable.forEach((eventType, ids) -> immutable.put(eventType, Set.copyOf(ids)));
        this.byEventType = immutable;
    }

}
