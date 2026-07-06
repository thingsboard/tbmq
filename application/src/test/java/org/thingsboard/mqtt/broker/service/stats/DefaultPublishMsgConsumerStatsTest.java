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

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class DefaultPublishMsgConsumerStatsTest {

    private DefaultPublishMsgConsumerStats stats;

    @Before
    public void setUp() {
        StatsFactory statsFactory = new DefaultStatsFactory(new SimpleMeterRegistry());
        stats = new DefaultPublishMsgConsumerStats("consumer-1", statsFactory);
    }

    @Test
    public void givenNoPacksProcessed_whenGetAvgPackSize_thenReturnsZeroNotNaN() {
        // At idle no pack has been logged, so the divisor is 0. The result must be a clean 0,
        // not NaN (which would poison the periodic log line).
        assertEquals(0.0, stats.getAvgPackSize(), 0.0);
    }

    @Test
    public void givenPacksProcessed_whenGetAvgPackSize_thenReturnsCeilOfAverage() {
        stats.logPackProcessingTime(3, 1, TimeUnit.MILLISECONDS);
        stats.logPackProcessingTime(4, 1, TimeUnit.MILLISECONDS);

        // (3 + 4) / 2 = 3.5 -> ceil -> 4
        assertEquals(4.0, stats.getAvgPackSize(), 0.0);
    }

    @Test
    public void givenReset_whenGetAvgPackSize_thenReturnsZeroNotNaN() {
        stats.logPackProcessingTime(5, 1, TimeUnit.MILLISECONDS);

        stats.reset();

        assertEquals(0.0, stats.getAvgPackSize(), 0.0);
    }
}
