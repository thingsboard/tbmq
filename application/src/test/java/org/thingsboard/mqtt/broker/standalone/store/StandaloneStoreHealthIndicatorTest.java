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

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.thingsboard.mqtt.broker.dao.store.StoreCapacity;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StandaloneStoreHealthIndicatorTest {

    private final StoreCapacity capacity = mock(StoreCapacity.class);
    private final StandaloneStoreHealthIndicator indicator = new StandaloneStoreHealthIndicator(capacity);

    @Test
    void givenNoDegradation_whenHealth_thenUpWithStoreOk() {
        when(capacity.degradedReason()).thenReturn(Optional.empty());

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).isEqualTo(Map.of("store", "ok"));
    }

    @Test
    void givenADegradedStore_whenHealth_thenStillUpWithStoreDegradedAndTheReason() {
        when(capacity.belowWatermark()).thenReturn(true);
        when(capacity.degradedReason()).thenReturn(Optional.of("Not enough free disk space for the store at /data/db: 1 bytes free, watermark 2 bytes"));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).isEqualTo(Map.of("store", "degraded",
                "reason", "Not enough free disk space for the store at /data/db: 1 bytes free, watermark 2 bytes"));
    }

}
