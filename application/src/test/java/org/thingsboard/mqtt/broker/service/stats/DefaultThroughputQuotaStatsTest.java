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
import org.thingsboard.mqtt.broker.common.stats.DefaultCounter;
import org.thingsboard.mqtt.broker.common.stats.DefaultStatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsType;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DefaultThroughputQuotaStatsTest {

    private SimpleMeterRegistry meterRegistry;
    private DefaultThroughputQuotaStats throughputQuotaStats;

    @Before
    public void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        StatsFactory statsFactory = new DefaultStatsFactory(meterRegistry);
        throughputQuotaStats = new DefaultThroughputQuotaStats(statsFactory);
    }

    @Test
    public void givenStats_whenIncrementRedisDegraded_thenCounterIncremented() {
        StatsFactory statsFactory = mock(StatsFactory.class);
        DefaultCounter counter = mock(DefaultCounter.class);
        when(statsFactory.createDefaultCounter(eq(StatsType.THROUGHPUT_QUOTA_DEGRADED.getPrintName()), eq("cause"), eq("redis")))
                .thenReturn(counter);

        DefaultThroughputQuotaStats stats = new DefaultThroughputQuotaStats(statsFactory);
        stats.incrementRedisDegraded();
        stats.reset();

        verify(counter).increment();
        verify(counter).clear();
    }

    @Test
    public void givenFreshStats_whenIncrementRedisDegraded_thenCountAndPrometheusCounterIncrease() {
        throughputQuotaStats.incrementRedisDegraded();
        throughputQuotaStats.incrementRedisDegraded();

        assertEquals(2, throughputQuotaStats.getCount());
        assertEquals(2.0, meterRegistry.counter(StatsType.THROUGHPUT_QUOTA_DEGRADED.getPrintName(), "cause", "redis").count(), 0.0);
    }

    @Test
    public void givenIncrementedStats_whenReset_thenCountClearedButPrometheusCounterStaysMonotonic() {
        throughputQuotaStats.incrementRedisDegraded();
        throughputQuotaStats.incrementRedisDegraded();
        throughputQuotaStats.incrementRedisDegraded();

        throughputQuotaStats.reset();

        // Per-interval count cleared for the next log line...
        assertEquals(0, throughputQuotaStats.getCount());
        // ...but the cumulative Prometheus counter must never be reset.
        assertEquals(3.0, meterRegistry.counter(StatsType.THROUGHPUT_QUOTA_DEGRADED.getPrintName(), "cause", "redis").count(), 0.0);

        throughputQuotaStats.incrementRedisDegraded();

        assertEquals(1, throughputQuotaStats.getCount());
        assertEquals(4.0, meterRegistry.counter(StatsType.THROUGHPUT_QUOTA_DEGRADED.getPrintName(), "cause", "redis").count(), 0.0);
    }
}
