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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PackSizeStatsTest {

    private final PackSizeStats stats = new PackSizeStats();

    @Test
    void givenNoPacksRecorded_whenGetAvg_thenZeroNotNaN() {
        // Idle interval: divisor is 0. Must be a clean 0, not NaN (which would poison the log line).
        assertThat(stats.getAvg()).isZero();
    }

    @Test
    void givenPacksRecorded_whenGetAvg_thenReturnsCeilOfAverage() {
        stats.record(3);
        stats.record(4);

        // (3 + 4) / 2 = 3.5 -> ceil -> 4
        assertThat(stats.getAvg()).isEqualTo(4.0);
    }

    @Test
    void givenReset_whenGetAvg_thenZeroNotNaN() {
        stats.record(5);

        stats.reset();

        assertThat(stats.getAvg()).isZero();
    }
}
