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

import org.thingsboard.mqtt.broker.common.data.integration.ClientLifecycleEventType;
import org.thingsboard.mqtt.broker.gen.queue.IntegrationLifecycleConfigProto;

import java.util.Set;

public interface IntegrationLifecycleEventTypeCache {

    void put(String integrationId, Set<ClientLifecycleEventType> eventTypes);

    void remove(String integrationId);

    Set<String> getIntegrationIds(ClientLifecycleEventType eventType);

    /**
     * Applies an integration lifecycle-config notification (delete -> {@link #remove}, otherwise parse the event-type
     * names and {@link #put}). Owns the delete/parse semantics so the broadcast-to-self and consume paths share one
     * implementation instead of duplicating it.
     */
    void processIntegrationLifecycleConfig(IntegrationLifecycleConfigProto proto);

}
