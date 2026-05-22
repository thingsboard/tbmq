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
import org.thingsboard.mqtt.broker.common.stats.StatsCounter;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DefaultFlowControlStatsTest {

    private DefaultFlowControlStats flowControlStats;

    @Before
    public void setUp() {
        StatsFactory statsFactory = new DefaultStatsFactory(new SimpleMeterRegistry());
        flowControlStats = new DefaultFlowControlStats(statsFactory);
    }

    @Test
    public void givenFreshStats_whenIncOps_thenCountersIncrease() {
        flowControlStats.incDropOverflow();
        flowControlStats.incDropOverflow();
        flowControlStats.incDropTtl();
        flowControlStats.incUnknownAck();

        assertEquals(2, flowControlStats.getDropOverflow());
        assertEquals(1, flowControlStats.getDropTtl());
        assertEquals(1, flowControlStats.getUnknownAck());
    }

    @Test
    public void givenFreshStats_whenInflightAndDelayedFluctuate_thenGaugesTrackCurrentValue() {
        flowControlStats.incInflight();
        flowControlStats.incInflight();
        flowControlStats.incInflight();
        flowControlStats.decInflight();

        flowControlStats.incDelayed();
        flowControlStats.incDelayed();
        flowControlStats.decDelayed();

        assertEquals(2, flowControlStats.getInflightCount());
        assertEquals(1, flowControlStats.getDelayedQueueSize());
    }

    @Test
    public void givenIncrementedCounters_whenReset_thenDropCountersAreClearedAndGaugesUntouched() {
        flowControlStats.incDropOverflow();
        flowControlStats.incDropTtl(5);
        flowControlStats.incUnknownAck();
        flowControlStats.incInflight();
        flowControlStats.incInflight();
        flowControlStats.incDelayed();

        flowControlStats.reset();

        assertEquals(0, flowControlStats.getDropOverflow());
        assertEquals(0, flowControlStats.getDropTtl());
        assertEquals(0, flowControlStats.getUnknownAck());
        assertEquals(2, flowControlStats.getInflightCount());
        assertEquals(1, flowControlStats.getDelayedQueueSize());
    }

    @Test
    public void givenStats_whenGetStatsCounters_thenReturnsAllThreeDropCounters() {
        List<StatsCounter> counters = flowControlStats.getStatsCounters();

        assertEquals(3, counters.size());
        List<String> names = counters.stream().map(StatsCounter::getName).collect(Collectors.toList());
        assertTrue(names.contains("dropsOverflow"));
        assertTrue(names.contains("dropsTtl"));
        assertTrue(names.contains("unknownAck"));
    }
}
