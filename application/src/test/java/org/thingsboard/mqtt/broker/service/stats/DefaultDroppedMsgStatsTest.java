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
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.stats.DefaultStatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;

import static org.junit.Assert.assertEquals;

public class DefaultDroppedMsgStatsTest {

    private SimpleMeterRegistry meterRegistry;
    private DefaultDroppedMsgStats droppedMsgStats;

    @Before
    public void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        StatsFactory statsFactory = new DefaultStatsFactory(meterRegistry);
        droppedMsgStats = new DefaultDroppedMsgStats(statsFactory);
    }

    @Test
    public void givenFreshStats_whenIncrement_thenCountAndPrometheusCounterIncrease() {
        droppedMsgStats.increment();
        droppedMsgStats.increment();

        assertEquals(2, droppedMsgStats.getCount());
        assertEquals(2.0, meterRegistry.counter(BrokerConstants.DROPPED_MSGS).count(), 0.0);
    }

    @Test
    public void givenFreshStats_whenIncrementByCount_thenCountAndPrometheusCounterIncreaseByCount() {
        droppedMsgStats.increment(5);

        assertEquals(5, droppedMsgStats.getCount());
        assertEquals(5.0, meterRegistry.counter(BrokerConstants.DROPPED_MSGS).count(), 0.0);
    }

    @Test
    public void givenIncrementedStats_whenReset_thenCountClearedButPrometheusCounterStaysMonotonic() {
        droppedMsgStats.increment(3);

        droppedMsgStats.reset();

        // The per-interval count is cleared so the next periodic log line starts fresh...
        assertEquals(0, droppedMsgStats.getCount());
        // ...but the cumulative Prometheus counter must never be reset.
        assertEquals(3.0, meterRegistry.counter(BrokerConstants.DROPPED_MSGS).count(), 0.0);

        droppedMsgStats.increment(2);

        assertEquals(2, droppedMsgStats.getCount());
        assertEquals(5.0, meterRegistry.counter(BrokerConstants.DROPPED_MSGS).count(), 0.0);
    }
}
