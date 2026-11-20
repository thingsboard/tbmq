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
package org.thingsboard.mqtt.broker.actors.client.service.connect;

import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.util.edition.ClusterComponent;
import org.thingsboard.mqtt.broker.dao.store.StoreCapacity;

import java.util.Optional;

/**
 * The cluster edition keeps its data in external stores, which this node does not watch: never below the watermark,
 * never degraded.
 */
@Component
@ClusterComponent
public class NoOpStoreCapacity implements StoreCapacity {

    @Override
    public boolean belowWatermark() {
        return false;
    }

    @Override
    public Optional<String> degradedReason() {
        return Optional.empty();
    }

}
