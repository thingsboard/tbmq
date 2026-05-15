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

import static org.junit.Assert.assertEquals;

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
}
