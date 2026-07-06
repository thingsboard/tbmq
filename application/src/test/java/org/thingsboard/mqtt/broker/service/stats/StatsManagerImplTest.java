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

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.thingsboard.mqtt.broker.common.stats.DefaultStatsFactory;
import org.thingsboard.mqtt.broker.common.stats.MessagesStats;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsType;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.CLIENT_ID_TAG;

public class StatsManagerImplTest {

    private static final String APP_PROCESSOR = StatsType.APP_PROCESSOR.getPrintName();
    private static final String APP_PROCESSOR_LATENCY = APP_PROCESSOR + ".latency";
    private static final String SQL_QUEUE = StatsType.SQL_QUEUE.getPrintName();

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

    @SuppressWarnings("unchecked")
    private Map<String, Set<String>> sharedSubscriptionCompoundClientIds() {
        return (Map<String, Set<String>>) ReflectionTestUtils.getField(statsManager, "sharedSubscriptionCompoundClientIds");
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
    public void givenTwoSharedSubscriptionsForOneClient_whenClearOneBySubscription_thenOnlyThatCompoundRemovedAndOuterEntrySurvivesUntilLast() {
        String clientId = "app-client-1";
        TopicSharedSubscription subscription1 = new TopicSharedSubscription("topic/a", "group1");
        TopicSharedSubscription subscription2 = new TopicSharedSubscription("topic/b", "group2");
        statsManager.createSharedApplicationProcessorStats(clientId, subscription1);
        statsManager.createSharedApplicationProcessorStats(clientId, subscription2);
        String compoundClientId1 = clientId + "_group1_topic/a";
        String compoundClientId2 = clientId + "_group2_topic/b";
        assertEquals(8, appProcessorCounters(compoundClientId1));
        assertEquals(8, appProcessorCounters(compoundClientId2));

        statsManager.clearSharedApplicationProcessorStats(clientId, subscription1);

        // Only the cleared subscription's counters are deregistered; the other subscription's remain.
        assertEquals(0, appProcessorCounters(compoundClientId1));
        assertEquals(8, appProcessorCounters(compoundClientId2));
        // The outer per-client set entry survives while the client still has a tracked subscription.
        assertTrue(sharedSubscriptionCompoundClientIds().containsKey(clientId));

        statsManager.clearSharedApplicationProcessorStats(clientId, subscription2);

        // Clearing the last subscription deregisters its counters and drops the outer set entry.
        assertEquals(0, appProcessorCounters(compoundClientId2));
        assertFalse(sharedSubscriptionCompoundClientIds().containsKey(clientId));
    }

    @Test
    public void givenSqlQueueStats_whenCreated_thenQueueSizeGaugeRegisteredAndReflectsLiveDepth() {
        MessagesStats stats = statsManager.createSqlQueueStats("Events", 0);

        Gauge gauge = meterRegistry.find(SQL_QUEUE + ".Events.queueSize").tag("queueIndex", "0").gauge();
        assertNotNull(gauge);
        // No queue supplier wired yet (TbSqlBlockingQueue#init wires it later) -> depth reads 0, not NaN.
        assertEquals(0.0, gauge.value(), 0.0);

        // Once the backing queue's size supplier is wired, the gauge reflects the live depth.
        AtomicInteger depth = new AtomicInteger(0);
        stats.updateQueueSize(depth::get);
        depth.set(7);
        assertEquals(7.0, gauge.value(), 0.0);
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
