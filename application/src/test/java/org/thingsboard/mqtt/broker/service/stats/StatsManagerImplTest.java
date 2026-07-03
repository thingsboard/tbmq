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
import org.springframework.test.util.ReflectionTestUtils;
import org.thingsboard.mqtt.broker.common.stats.DefaultStatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsType;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;

import static org.junit.Assert.assertEquals;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.CLIENT_ID_TAG;

public class StatsManagerImplTest {

    private static final String APP_PROCESSOR = StatsType.APP_PROCESSOR.getPrintName();
    private static final String APP_PROCESSOR_LATENCY = APP_PROCESSOR + ".latency";

    private SimpleMeterRegistry meterRegistry;
    private StatsManagerImpl statsManager;

    @Before
    public void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        StatsFactory statsFactory = new DefaultStatsFactory(meterRegistry);
        statsManager = new StatsManagerImpl(statsFactory);
        ReflectionTestUtils.setField(statsManager, "applicationProcessorStatsEnabled", true);
    }

    private int appProcessorCounters(String clientId) {
        return meterRegistry.find(APP_PROCESSOR).tag(CLIENT_ID_TAG, clientId).counters().size();
    }

    @Test
    public void givenApplicationProcessorStats_whenClear_thenPerClientCountersRemovedFromRegistry() {
        String clientId = "app-client-1";
        statsManager.createApplicationProcessorStats(clientId);
        assertEquals(8, appProcessorCounters(clientId));

        statsManager.clearApplicationProcessorStats(clientId);

        assertEquals(0, appProcessorCounters(clientId));
    }

    @Test
    public void givenSharedApplicationProcessorStats_whenClearShared_thenCompoundCountersRemovedFromRegistry() {
        String clientId = "app-client-1";
        TopicSharedSubscription subscription = new TopicSharedSubscription("my/topic", "group1");
        statsManager.createSharedApplicationProcessorStats(clientId, subscription);
        String compoundClientId = clientId + "_group1_my/topic";
        assertEquals(8, appProcessorCounters(compoundClientId));

        statsManager.clearSharedApplicationProcessorStats(clientId);

        assertEquals(0, appProcessorCounters(compoundClientId));
    }

    @Test
    public void givenApplicationProcessorStats_whenClear_thenSharedLatencyTimersRetained() {
        String clientId = "app-client-1";
        statsManager.createApplicationProcessorStats(clientId);
        assertEquals(3, meterRegistry.find(APP_PROCESSOR_LATENCY).timers().size());

        statsManager.clearApplicationProcessorStats(clientId);

        // The 3 latency timers carry no clientId tag, so Micrometer shares one set across all
        // application clients. Clearing one client must NOT deregister them.
        assertEquals(3, meterRegistry.find(APP_PROCESSOR_LATENCY).timers().size());
    }
}
