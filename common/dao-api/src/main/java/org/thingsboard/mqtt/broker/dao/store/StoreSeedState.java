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
 * Whether the management defaults have been seeded into the embedded store. The marker is written once the whole seed
 * is in the store, so a store without it gets the seed again on the next start.
 */
public interface StoreSeedState {

    /**
     * Whether the store holds the marker of a finished seed.
     */
    boolean isSeeded();

    /**
     * Writes the marker of a finished seed, durably. Harmless when the marker is already there.
     */
    void markSeeded();

    /**
     * Whether {@link #markSeeded()} ran during this start, that is, whether this start seeded the store.
     */
    boolean seededInThisStart();

}
