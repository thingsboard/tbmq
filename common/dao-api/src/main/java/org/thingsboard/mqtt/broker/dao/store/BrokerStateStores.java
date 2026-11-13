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
package org.thingsboard.mqtt.broker.dao.store;

/**
 * The key/value stores that hold the broker state, one per column family.
 */
public interface BrokerStateStores {

    /**
     * Persistent client sessions (column family {@code client_session}).
     */
    KeyValueStore clientSessions();

    /**
     * Subscriptions of persistent clients ({@code client_subscriptions}).
     */
    KeyValueStore clientSubscriptions();

    /**
     * Retained messages, keyed by topic ({@code retained_msg}).
     */
    KeyValueStore retainedMessages();

    /**
     * Blocked clients ({@code blocked_client}).
     */
    KeyValueStore blockedClients();

    /**
     * Client session attributes ({@code client_session_attr}).
     */
    KeyValueStore clientSessionAttributes();

}
