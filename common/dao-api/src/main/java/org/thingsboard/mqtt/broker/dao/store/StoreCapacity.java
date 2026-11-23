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

import java.util.Optional;

/**
 * Whether the broker store has room for new data. The standalone edition reads it from the free space of the store's
 * volume; the cluster edition never runs out (its stores are external).
 */
public interface StoreCapacity {

    /**
     * @return whether the store refuses new data ({@link StoreFullException}) because its volume is below the
     * free-space watermark
     */
    boolean belowWatermark();

    /**
     * @return why the store is degraded (below the watermark, or a background error it has not recovered from yet),
     * or empty when it is healthy
     */
    Optional<String> degradedReason();

}
