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
package org.thingsboard.mqtt.broker.service.limits;

import com.google.common.util.concurrent.MoreExecutors;
import org.junit.Before;
import org.junit.Test;
import org.thingsboard.mqtt.broker.config.TotalMsgsRateLimitsConfiguration;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.service.stats.StubThroughputQuotaStats;
import org.thingsboard.mqtt.broker.service.stats.ThroughputQuotaStats;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class ThroughputQuotaServiceImplTest {

    TotalMsgsRateLimitsConfiguration configuration;
    RateLimitCacheService rateLimitCacheService;
    StatsManager statsManager;
    ThroughputQuotaServiceImpl service;

    /** Captures draw tasks so tests decide exactly when a pending draw completes. */
    static class ManualExecutor extends AbstractExecutorService {
        final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();

        void runAll() {
            Runnable task;
            while ((task = tasks.poll()) != null) {
                task.run();
            }
        }

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        @Override
        public void shutdown() {
        }

        @Override
        public List<Runnable> shutdownNow() {
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }

    @Before
    public void setUp() {
        configuration = new TotalMsgsRateLimitsConfiguration();
        configuration.setEnabled(true);
        configuration.setConfig("100:1");
        rateLimitCacheService = mock(RateLimitCacheService.class);
        statsManager = mock(StatsManager.class);
        when(statsManager.getThroughputQuotaStats()).thenReturn(StubThroughputQuotaStats.STUB_THROUGHPUT_QUOTA_STATS);
        service = new ThroughputQuotaServiceImpl(
                configuration, new DefaultThroughputLimitProvider(configuration), rateLimitCacheService, statsManager);
    }

    @Test
    public void givenDefaultProvider_whenMultiBandwidthConfig_thenMinSustainedRate() {
        configuration.setConfig("1000:1,50000:60");
        assertEquals(833, new DefaultThroughputLimitProvider(configuration).getSustainedRatePerSec());
    }

    @Test
    public void givenQuotaDisabled_whenTryConsume_thenAlwaysGrantedWithoutRedis() {
        configuration.setEnabled(false);
        service.init();

        assertTrue(service.tryConsumeIncoming());
        assertEquals(7, service.tryConsumeOutgoing(7));
        verifyNoInteractions(rateLimitCacheService);
    }

    @Test
    public void givenDerivedBlockSize_whenInit_thenTenthOfMinSustainedRate() {
        // 100:1 -> 100 msg/s -> block 10 (warm-up draw request proves it)
        when(rateLimitCacheService.tryConsumeTotalMsgs(anyLong())).thenReturn(10L);
        service.drawExecutor = MoreExecutors.newDirectExecutorService();

        service.init();

        verify(rateLimitCacheService).tryConsumeTotalMsgs(10L);
    }

    @Test
    public void givenWarmedUpTokens_whenConsumedWithinBudget_thenGranted() {
        when(rateLimitCacheService.tryConsumeTotalMsgs(anyLong())).thenReturn(10L);
        service.drawExecutor = MoreExecutors.newDirectExecutorService();
        service.init();

        assertEquals(8, service.tryConsumeOutgoing(8));
    }

    @Test
    public void givenPendingDraw_whenConsuming_thenGrantsOnBoundedCreditThenRefuses() {
        ManualExecutor executor = new ManualExecutor();
        service.drawExecutor = executor;
        when(rateLimitCacheService.tryConsumeTotalMsgs(anyLong())).thenReturn(10L);
        service.init(); // warm-up draw queued but NOT run: local stays 0

        // credit floor is -blockSize (-10): exactly 10 packets ride on credit
        for (int i = 0; i < 10; i++) {
            assertTrue("packet " + i + " should be granted on credit", service.tryConsumeIncoming());
        }
        assertFalse("credit exhausted, must refuse", service.tryConsumeIncoming());
        assertEquals("single-flight: only the warm-up draw may be queued", 1, executor.tasks.size());

        executor.runAll(); // draw completes with 10 -> repays the deficit exactly
        assertTrue(service.tryConsumeIncoming()); // fresh credit available again
    }

    @Test
    public void givenPartialLocalBudget_whenBulkConsume_thenPartialGrant() {
        when(rateLimitCacheService.tryConsumeTotalMsgs(anyLong())).thenReturn(10L, 0L);
        service.drawExecutor = MoreExecutors.newDirectExecutorService();
        service.init(); // local = 10

        // 10 local + 10 credit = 20 available; ask for 25
        assertEquals(20, service.tryConsumeOutgoing(25));
    }

    @Test
    public void givenDryBucket_whenBackoffActive_thenRefusesLocallyAndRecoversAfterBackoff() throws InterruptedException {
        when(rateLimitCacheService.tryConsumeTotalMsgs(anyLong())).thenReturn(10L, 0L, 5L);
        service.drawExecutor = MoreExecutors.newDirectExecutorService();
        service.init(); // draw#1: local = 10

        assertEquals(10, service.tryConsumeOutgoing(10)); // exhausts local -> draw#2 returns 0 -> dry backoff opens
        assertFalse("dry backoff must refuse without credit", service.tryConsumeIncoming());
        verify(rateLimitCacheService, times(2)).tryConsumeTotalMsgs(anyLong()); // dry window: refusals must not reach Redis

        Thread.sleep(60); // DRY_BACKOFF_NANOS is 50 ms
        assertTrue("after backoff, credit resumes and a new draw is scheduled", service.tryConsumeIncoming());
    }

    @Test
    public void givenRedisFailure_whenDraw_thenFailsOpenAndCountsDegraded() {
        ThroughputQuotaStats quotaStats = mock(ThroughputQuotaStats.class);
        when(statsManager.getThroughputQuotaStats()).thenReturn(quotaStats);
        when(rateLimitCacheService.tryConsumeTotalMsgs(anyLong())).thenThrow(new RuntimeException("redis down"));
        service.drawExecutor = MoreExecutors.newDirectExecutorService();

        service.init(); // warm-up draw fails -> fail-open window opens

        assertTrue(service.tryConsumeIncoming());
        assertEquals(1000, service.tryConsumeOutgoing(1000)); // everything granted, nothing queued
        verify(quotaStats).incrementRedisDegraded();
    }

    @Test
    public void givenPositiveLocalBalance_whenReturnTick_thenReturnedToBucketAndZeroed() {
        when(rateLimitCacheService.tryConsumeTotalMsgs(anyLong())).thenReturn(10L);
        service.drawExecutor = MoreExecutors.newDirectExecutorService();
        service.init(); // local = 10
        assertEquals(3, service.tryConsumeOutgoing(3)); // local = 7

        service.returnUnusedTokens();

        verify(rateLimitCacheService).returnTotalMsgs(7L);
        service.returnUnusedTokens(); // second tick must be a no-op: balance was zeroed
        verify(rateLimitCacheService, times(1)).returnTotalMsgs(anyLong());
        // returned budget is gone locally: next consume needs a fresh draw (mock grants 10 again)
        assertTrue(service.tryConsumeIncoming());
    }

    @Test
    public void givenCreditDeficit_whenReturnTick_thenNothingReturned() {
        ManualExecutor executor = new ManualExecutor();
        service.drawExecutor = executor;
        service.init(); // warm-up queued, local 0
        assertTrue(service.tryConsumeIncoming()); // local = -1 (credit)

        service.returnUnusedTokens();

        verify(rateLimitCacheService, never()).returnTotalMsgs(anyLong());
    }
}
