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
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;

import static org.junit.Assert.assertEquals;

public class DefaultClientDisconnectStatsTest {

    private SimpleMeterRegistry meterRegistry;
    private DefaultClientDisconnectStats clientDisconnectStats;

    @Before
    public void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        StatsFactory statsFactory = new DefaultStatsFactory(meterRegistry);
        clientDisconnectStats = new DefaultClientDisconnectStats(statsFactory);
    }

    @Test
    public void givenFreshStats_whenIncrement_thenCountAndPrometheusCounterIncrease() {
        clientDisconnectStats.increment(DisconnectReasonType.ON_DISCONNECT_MSG);
        clientDisconnectStats.increment(DisconnectReasonType.ON_KEEP_ALIVE);

        assertEquals(2, clientDisconnectStats.getCount());
        assertEquals(2.0, meterRegistry.counter(StatsType.CLIENT_DISCONNECTS.getPrintName()).count(), 0.0);
    }

    @Test
    public void givenDifferentReasons_whenIncrement_thenAllMapToSingleUntaggedCounter() {
        clientDisconnectStats.increment(DisconnectReasonType.ON_DISCONNECT_MSG);
        clientDisconnectStats.increment(DisconnectReasonType.ON_CLUSTER_CONFLICTING_SESSIONS);
        clientDisconnectStats.increment(DisconnectReasonType.ON_RATE_LIMITS);

        // CE emits a single untagged clientDisconnects counter; the reason is intentionally ignored
        // (the reason breakdown is a PE-only extension). All reasons accumulate into one series.
        assertEquals(3.0, meterRegistry.counter(StatsType.CLIENT_DISCONNECTS.getPrintName()).count(), 0.0);
        assertEquals(1, meterRegistry.find(StatsType.CLIENT_DISCONNECTS.getPrintName()).counters().size());
    }

    @Test
    public void givenIncrementedStats_whenReset_thenCountClearedButPrometheusCounterStaysMonotonic() {
        clientDisconnectStats.increment(DisconnectReasonType.ON_DISCONNECT_MSG);
        clientDisconnectStats.increment(DisconnectReasonType.ON_DISCONNECT_MSG);
        clientDisconnectStats.increment(DisconnectReasonType.ON_DISCONNECT_MSG);

        clientDisconnectStats.reset();

        // Per-interval count cleared for the next log line...
        assertEquals(0, clientDisconnectStats.getCount());
        // ...but the cumulative Prometheus counter must never be reset.
        assertEquals(3.0, meterRegistry.counter(StatsType.CLIENT_DISCONNECTS.getPrintName()).count(), 0.0);

        clientDisconnectStats.increment(DisconnectReasonType.ON_KEEP_ALIVE);

        assertEquals(1, clientDisconnectStats.getCount());
        assertEquals(4.0, meterRegistry.counter(StatsType.CLIENT_DISCONNECTS.getPrintName()).count(), 0.0);
    }
}
