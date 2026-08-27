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
package org.thingsboard.mqtt.broker.actors.client.service.subscription.integration;

import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;

import java.util.Set;

public interface IntegrationSubscriptionUpdateService {

    /**
     * Applies the given subscription set to the integration. An empty set clears all of them.
     */
    void processSubscriptionsUpdate(String integrationId, Set<TopicSubscription> subscriptions);

    /**
     * Removes all of the integration's subscriptions and persists the empty set unconditionally.
     * <p>
     * Deliberately does not check whether this node currently sees any: that view is node-local and eventually
     * consistent, so skipping the write would let the persisted subscriptions outlive a deleted integration and come
     * back on the next restart. Use {@link #hasSubscriptions(String)} when a caller needs to know whether there was
     * anything to clear.
     */
    void clearSubscriptions(String integrationId);

    /**
     * Whether this node currently sees any subscriptions for the integration. A node-local, eventually-consistent
     * read: only safe for a caller that can tolerate a stale answer, e.g. the periodic cleanup deciding whether an
     * integration disabled for the whole TTL is still attached to the data stream.
     */
    boolean hasSubscriptions(String integrationId);

}
