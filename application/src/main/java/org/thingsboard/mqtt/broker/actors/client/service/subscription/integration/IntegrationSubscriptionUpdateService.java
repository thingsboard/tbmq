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
     * Applies the given subscription set to the integration, returning {@code true} when anything was actually
     * persisted. An update that would be a no-op is skipped, so a caller invoked repeatedly (e.g. the periodic
     * cleanup) does not keep rewriting the same state. Prefer {@link #clearSubscriptions(String)} over passing an
     * empty set.
     */
    boolean processSubscriptionsUpdate(String integrationId, Set<TopicSubscription> subscriptions);

    /**
     * Removes all of the integration's subscriptions, returning {@code true} when it had any - i.e. when this call
     * actually detached it from the data stream. Skipped when it had none, since persisting an empty set rewrites the
     * same state cluster-wide.
     */
    boolean clearSubscriptions(String integrationId);

}
