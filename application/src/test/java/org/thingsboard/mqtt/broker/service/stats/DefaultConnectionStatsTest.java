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
package org.thingsboard.mqtt.broker.service.stats;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Before;
import org.junit.Test;
import org.thingsboard.mqtt.broker.common.stats.DefaultStatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsType;

import static org.junit.Assert.assertEquals;

public class DefaultConnectionStatsTest {

    private SimpleMeterRegistry meterRegistry;
    private DefaultConnectionStats connectionStats;

    @Before
    public void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        StatsFactory statsFactory = new DefaultStatsFactory(meterRegistry);
        connectionStats = new DefaultConnectionStats(statsFactory);
    }

    @Test
    public void givenFreshStats_whenIncrementEachOutcome_thenCountsAndPrometheusCountersIncrease() {
        connectionStats.onConnectionAccepted();
        connectionStats.onConnectionAccepted();
        connectionStats.onConnectionRefused();
        connectionStats.onConnectionError();

        assertEquals(2, connectionStats.getAcceptedCount());
        assertEquals(1, connectionStats.getRefusedCount());
        assertEquals(1, connectionStats.getErrorCount());
        assertEquals(2.0, meterRegistry.counter(StatsType.CONNECTION_ACCEPTED.getPrintName()).count(), 0.0);
        assertEquals(1.0, meterRegistry.counter(StatsType.CONNECTION_REFUSED.getPrintName()).count(), 0.0);
        assertEquals(1.0, meterRegistry.counter(StatsType.CONNECTION_ERROR.getPrintName()).count(), 0.0);
    }

    @Test
    public void givenMultipleRefusals_whenRefused_thenSingleUntaggedCounterAccumulates() {
        connectionStats.onConnectionRefused();
        connectionStats.onConnectionRefused();
        connectionStats.onConnectionRefused();

        // CE emits a single untagged connectionRefused counter; refusals carry no discriminator.
        assertEquals(3.0, meterRegistry.counter(StatsType.CONNECTION_REFUSED.getPrintName()).count(), 0.0);
        assertEquals(1, meterRegistry.find(StatsType.CONNECTION_REFUSED.getPrintName()).counters().size());
    }

    @Test
    public void givenMultipleErrors_whenError_thenSingleUntaggedCounterAccumulates() {
        connectionStats.onConnectionError();
        connectionStats.onConnectionError();
        connectionStats.onConnectionError();

        // CE emits a single untagged connectionError counter; establishment errors carry no discriminator.
        assertEquals(3.0, meterRegistry.counter(StatsType.CONNECTION_ERROR.getPrintName()).count(), 0.0);
        assertEquals(1, meterRegistry.find(StatsType.CONNECTION_ERROR.getPrintName()).counters().size());
    }

    @Test
    public void givenIncrementedStats_whenReset_thenCountsClearedButPrometheusCountersStayMonotonic() {
        connectionStats.onConnectionAccepted();
        connectionStats.onConnectionRefused();
        connectionStats.onConnectionError();

        connectionStats.reset();

        assertEquals(0, connectionStats.getAcceptedCount());
        assertEquals(0, connectionStats.getRefusedCount());
        assertEquals(0, connectionStats.getErrorCount());
        // Cumulative Prometheus counters must never be reset.
        assertEquals(1.0, meterRegistry.counter(StatsType.CONNECTION_ACCEPTED.getPrintName()).count(), 0.0);
        assertEquals(1.0, meterRegistry.counter(StatsType.CONNECTION_REFUSED.getPrintName()).count(), 0.0);
        assertEquals(1.0, meterRegistry.counter(StatsType.CONNECTION_ERROR.getPrintName()).count(), 0.0);

        // Per-interval counters keep working after reset; cumulative Prometheus counters advance.
        connectionStats.onConnectionAccepted();
        connectionStats.onConnectionRefused();
        connectionStats.onConnectionError();

        assertEquals(1, connectionStats.getAcceptedCount());
        assertEquals(1, connectionStats.getRefusedCount());
        assertEquals(1, connectionStats.getErrorCount());
        assertEquals(2.0, meterRegistry.counter(StatsType.CONNECTION_ACCEPTED.getPrintName()).count(), 0.0);
        assertEquals(2.0, meterRegistry.counter(StatsType.CONNECTION_REFUSED.getPrintName()).count(), 0.0);
        assertEquals(2.0, meterRegistry.counter(StatsType.CONNECTION_ERROR.getPrintName()).count(), 0.0);
    }
}
