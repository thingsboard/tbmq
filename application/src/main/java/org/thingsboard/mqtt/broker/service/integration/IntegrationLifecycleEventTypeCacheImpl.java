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

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.integration.ClientLifecycleEventType;
import org.thingsboard.mqtt.broker.common.data.integration.ClientLifecycleEventTypeUtil;
import org.thingsboard.mqtt.broker.gen.queue.IntegrationLifecycleConfigProto;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class IntegrationLifecycleEventTypeCacheImpl implements IntegrationLifecycleEventTypeCache {

    // Mutations are serialized by the instance monitor (put/remove/rebuildReverseIndex all run under synchronized);
    // reads never touch this map - they use the immutable byEventType snapshot below. A plain HashMap suffices: the
    // synchronization, not the map type, provides thread-safety.
    private final Map<String, Set<ClientLifecycleEventType>> byIntegrationId = new HashMap<>();

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
    public synchronized boolean remove(String integrationId) {
        if (byIntegrationId.remove(integrationId) == null) {
            // Nothing was cached for it, so the reverse index cannot change. Keeps repeated removals (e.g. the
            // periodic cleanup of a long-disabled integration) from rebuilding and republishing the snapshot.
            return false;
        }
        rebuildReverseIndex();
        return true;
    }

    @Override
    public Set<String> getIntegrationIds(ClientLifecycleEventType eventType) {
        return byEventType.getOrDefault(eventType, Set.of());
    }

    @Override
    public void processIntegrationLifecycleConfig(IntegrationLifecycleConfigProto proto) {
        if (proto.getDeleted()) {
            remove(proto.getIntegrationId());
            return;
        }
        Set<ClientLifecycleEventType> eventTypes = ClientLifecycleEventTypeUtil.parse(
                proto.getLifecycleEventTypesList(),
                name -> log.warn("[{}] Unknown lifecycle event type: {}", proto.getIntegrationId(), name));
        put(proto.getIntegrationId(), eventTypes);
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
