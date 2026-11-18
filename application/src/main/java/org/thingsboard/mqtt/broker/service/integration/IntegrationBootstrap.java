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

/**
 * What the broker start-up needs from the integrations: the cluster edition reads them from the database, an edition
 * without integrations has nothing to load.
 */
public interface IntegrationBootstrap {

    /**
     * Populates the node-local lifecycle event type cache. Runs before the MQTT bootstraps, since a client
     * connecting before it completes would not have its lifecycle events published.
     */
    void loadLifecycleEventCache();

    int integrationsCount();

}
