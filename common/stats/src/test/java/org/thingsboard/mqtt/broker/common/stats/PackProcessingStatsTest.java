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
package org.thingsboard.mqtt.broker.common.stats;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class PackProcessingStatsTest {

    private final PackProcessingStats stats =
            new PackProcessingStats(new SimpleMeterRegistry().timer("test.pack.processing.time"));

    @Test
    void givenNoPacksRecorded_whenGetAvgPackSize_thenZeroNotNaN() {
        // Idle interval: no pack recorded, so the divisor is 0. Must be a clean 0, not NaN.
        assertThat(stats.getAvgPackSize()).isZero();
    }

    @Test
    void givenPacksRecorded_whenGetAvgPackSize_thenReturnsCeilOfAverage() {
        stats.record(3, 1, TimeUnit.MILLISECONDS);
        stats.record(4, 1, TimeUnit.MILLISECONDS);

        // (3 + 4) / 2 = 3.5 -> ceil -> 4
        assertThat(stats.getAvgPackSize()).isEqualTo(4.0);
    }

    @Test
    void givenPacksRecorded_whenGetAvgProcessingTime_thenReflectsTimer() {
        stats.record(3, 2, TimeUnit.MILLISECONDS);
        stats.record(4, 4, TimeUnit.MILLISECONDS);

        // (2ms + 4ms) / 2 = 3ms
        assertThat(stats.getAvgProcessingTime()).isEqualTo(3.0);
    }

    @Test
    void givenReset_whenGetAvgPackSize_thenZeroNotNaN() {
        stats.record(5, 1, TimeUnit.MILLISECONDS);

        stats.reset();

        assertThat(stats.getAvgPackSize()).isZero();
    }
}
