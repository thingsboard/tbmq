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
package org.thingsboard.mqtt.broker.standalone.store;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.dao.store.StoreCapacity;

/**
 * Reports the store as {@code ok} or {@code degraded} with the reason. Always UP: a container health check that
 * restarted the broker on a full disk would start it into the same full disk, while the broker keeps serving
 * non-persistent traffic and the writes that free space.
 */
@Component
@StandaloneComponent
@RequiredArgsConstructor
public class StandaloneStoreHealthIndicator implements HealthIndicator {

    private final StoreCapacity storeCapacity;

    @Override
    public Health health() {
        return storeCapacity.degradedReason()
                .map(reason -> Health.up().withDetail("store", "degraded").withDetail("reason", reason).build())
                .orElseGet(() -> Health.up().withDetail("store", "ok").build());
    }

}
