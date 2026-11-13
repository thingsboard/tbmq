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
package org.thingsboard.mqtt.broker.actors.client.service.subscription;

import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fences optimistic local subscription changes against older Kafka snapshots.
 * Entries exist only while local writes or their Kafka echoes are outstanding.
 * The caller serializes begin/resolve per client; complete may run on a producer thread.
 * No clock, database, persistent per-client replay ledger or per-message history.
 */
final class SubscriptionUpdateOrder {
    private final Map<String, Pending> pendingByClient = new ConcurrentHashMap<>();

    Pending begin(String clientId, Set<TopicSubscription> baseline) {
        Pending pending = pendingByClient.computeIfAbsent(clientId, ignored -> new Pending(baseline));
        synchronized (pending) {
            pending.writes++;
        }
        return pending;
    }

    void complete(Pending pending, Set<TopicSubscription> snapshot, long offset) {
        synchronized (pending) {
            if (pending.writes <= 0) {
                throw new IllegalStateException("Subscription write completed more than once");
            }
            if (offset >= 0) {
                pending.latestLocalOffset = Math.max(pending.latestLocalOffset, offset);
                pending.offer(snapshot, offset);
            }
            pending.writes--;
        }
    }

    Optional<Set<TopicSubscription>> resolve(String clientId, Set<TopicSubscription> consumed, long offset) {
        Pending pending = pendingByClient.get(clientId);
        if (pending == null) {
            return consumed == null ? Optional.empty() : Optional.of(consumed);
        }
        synchronized (pending) {
            if (consumed != null) {
                if (offset < 0) {
                    throw new IllegalArgumentException("Kafka subscription offset is required");
                }
                pending.latestConsumedOffset = Math.max(pending.latestConsumedOffset, offset);
                pending.offer(consumed, offset);
            }
            if (pending.writes != 0) {
                return Optional.empty();
            }
            // An old remote clear may have been consumed after the new local write,
            // but cannot overwrite the snapshot with the greater committed offset.
            Set<TopicSubscription> resolved = pending.latestSnapshot;
            if (pending.latestConsumedOffset >= pending.latestLocalOffset) {
                pendingByClient.remove(clientId, pending);
            }
            return Optional.of(resolved);
        }
    }

    int pendingClients() {
        return pendingByClient.size();
    }

    static final class Pending {
        private int writes;
        private long latestLocalOffset = -1;
        private long latestConsumedOffset = -1;
        private long latestOffset = -1;
        private Set<TopicSubscription> latestSnapshot;

        private Pending(Set<TopicSubscription> baseline) {
            latestSnapshot = Set.copyOf(baseline);
        }

        private void offer(Set<TopicSubscription> snapshot, long offset) {
            if (offset > latestOffset) {
                latestOffset = offset;
                latestSnapshot = Set.copyOf(snapshot);
            }
        }
    }
}
