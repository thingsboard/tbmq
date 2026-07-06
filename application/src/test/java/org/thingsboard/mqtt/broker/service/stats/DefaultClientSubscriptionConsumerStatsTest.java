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
import static org.junit.Assert.assertTrue;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.STATS_NAME_TAG;

public class DefaultClientSubscriptionConsumerStatsTest {

    private static final String NAME = StatsType.CLIENT_SUBSCRIPTIONS_CONSUMER.getPrintName();

    private SimpleMeterRegistry meterRegistry;

    @Before
    public void setUp() {
        meterRegistry = new SimpleMeterRegistry();
    }

    @Test
    public void givenStats_whenCreated_thenCountersUseRecordStatsNames() {
        StatsFactory statsFactory = new DefaultStatsFactory(meterRegistry);
        new DefaultClientSubscriptionConsumerStats(statsFactory);

        // The consumer counts Kafka records read per poll, not topic subscriptions, so the stats names
        // are `*Records` rather than the misleading `*Subscriptions`.
        assertEquals(1, meterRegistry.find(NAME).tag(STATS_NAME_TAG, "totalRecords").counters().size());
        assertEquals(1, meterRegistry.find(NAME).tag(STATS_NAME_TAG, "acceptedRecords").counters().size());
        assertEquals(1, meterRegistry.find(NAME).tag(STATS_NAME_TAG, "ignoredRecords").counters().size());
        // The old `*Subscriptions` stats names are gone.
        assertTrue(meterRegistry.find(NAME).tag(STATS_NAME_TAG, "totalSubscriptions").counters().isEmpty());
    }
}
