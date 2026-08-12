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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.common.util.concurrent.MoreExecutors;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.thingsboard.mqtt.broker.config.TotalMsgsRateLimitsConfiguration;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.service.stats.StubThroughputQuotaStats;
import org.thingsboard.mqtt.broker.service.stats.ThroughputQuotaStats;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
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

    /** Blows up on the first submission (the wedge scenario), then behaves like a plain ManualExecutor. */
    static class OneShotThrowingExecutor extends ManualExecutor {
        boolean thrown;

        @Override
        public void execute(Runnable command) {
            if (!thrown) {
                thrown = true;
                throw new IllegalStateException("executor blew up");
            }
            super.execute(command);
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
    public void givenRedisDown_whenFailOpenWindowLapsesWithDrawInFlight_thenKeepsGrantingUntilDrawCompletes() throws InterruptedException {
        ManualExecutor executor = new ManualExecutor();
        service.drawExecutor = executor;
        when(rateLimitCacheService.tryConsumeTotalMsgs(anyLong())).thenThrow(new RuntimeException("redis down"));
        service.init(); // warm-up draw queued
        executor.runAll(); // it fails: the node is marked degraded and the fail-open window opens

        Thread.sleep(1100); // FAIL_OPEN_NANOS is 1 s: the window has lapsed

        assertEquals("bounded credit is spent first", 10, service.tryConsumeOutgoing(10)); // queues the next draw
        // that draw is in flight against a Redis that is still down and will only re-arm the window when
        // its socket finally times out (seconds): until then the quota must stay open on the degraded flag
        assertTrue("degraded node must not refuse while a failing draw is in flight", service.tryConsumeIncoming());
        assertEquals(500, service.tryConsumeOutgoing(500));

        doReturn(10L).when(rateLimitCacheService).tryConsumeTotalMsgs(anyLong());
        executor.runAll(); // the pending draw finally succeeds: the flag clears, the deficit is repaid

        assertEquals("the repaid deficit leaves exactly one block grantable", 10, service.tryConsumeOutgoing(50));
        assertFalse("with Redis healthy again, an exhausted node must refuse", service.tryConsumeIncoming());
    }

    // The waiting charge draws synchronously for exactly what it needs, so it must not also take credit: the credit
    // would leave a deficit repayable only by a second, concurrent draw, which is how this path came to issue two
    // Redis calls per shortfall and run them against each other.
    //
    // This is also what keeps prepaid charging honest, so it is not quota-service trivia to be deleted: the persistent
    // fan-out charges its whole width in one bulk call, and a charge that could only ever grant localTokens + blockSize
    // would make the node's lease - not the cluster budget - the ceiling on how many subscribers a publish can reach.
    @Test
    public void givenShortPool_whenWaitingCharge_thenOneDrawAndNoSecondQueued() {
        ManualExecutor executor = new ManualExecutor();
        service.drawExecutor = executor;
        when(rateLimitCacheService.tryConsumeTotalMsgs(anyLong())).thenAnswer(inv -> inv.getArgument(0));
        service.init();
        executor.runAll(); // the warm-up draw lands: block 10 tokens local
        clearInvocations(rateLimitCacheService);

        int granted = service.tryConsumeOutgoingWaiting(50);

        assertEquals("the pool plus one synchronous draw must cover the whole demand", 50, granted);
        verify(rateLimitCacheService, times(1)).tryConsumeTotalMsgs(anyLong());
        assertTrue("the waiting charge must not also queue an asynchronous draw", executor.tasks.isEmpty());
        AtomicLong localTokens = (AtomicLong) ReflectionTestUtils.getField(service, "localTokens");
        assertEquals("that same draw must repay the credit the call took, or the node would deliver tokens it "
                + "never paid the shared bucket for", 0L, localTokens.get());
    }

    // Two draws can be in flight at once, so a slow success must not clear a degraded state that a newer failure set.
    @Test
    public void givenDrawInFlight_whenAnotherDrawFails_thenStaleSuccessKeepsDegraded() throws Exception {
        CountDownLatch asyncDrawStarted = new CountDownLatch(1);
        CountDownLatch failureRecorded = new CountDownLatch(1);
        AtomicInteger call = new AtomicInteger();
        when(rateLimitCacheService.tryConsumeTotalMsgs(anyLong())).thenAnswer(inv -> {
            if (call.getAndIncrement() == 0) {          // the warm-up draw, on the draw executor
                asyncDrawStarted.countDown();
                failureRecorded.await(5, TimeUnit.SECONDS); // still in flight while the caller's draw fails
                return 10L;                             // then answers successfully: a STALE success
            }
            throw new RuntimeException("redis down");   // the caller-thread draw
        });
        ExecutorService drawPool = Executors.newSingleThreadExecutor();
        service.drawExecutor = drawPool;
        try {
            service.init();
            assertTrue(asyncDrawStarted.await(5, TimeUnit.SECONDS));

            service.tryConsumeOutgoingWaiting(50); // draws on this thread, fails, marks the node degraded
            failureRecorded.countDown();
            drawPool.shutdown();
            assertTrue(drawPool.awaitTermination(5, TimeUnit.SECONDS)); // the stale success has landed

            assertEquals("a success that started before the failure must not clear the degraded flag",
                    true, ReflectionTestUtils.getField(service, "redisDegraded"));
        } finally {
            drawPool.shutdownNow();
        }
    }

    @Test
    public void givenHealthyDryDraw_whenCreditExhausted_thenStillRefuses() throws InterruptedException {
        ManualExecutor executor = new ManualExecutor();
        service.drawExecutor = executor;
        when(rateLimitCacheService.tryConsumeTotalMsgs(anyLong())).thenReturn(0L);
        service.init();
        executor.runAll(); // a draw that completed dry proves Redis is healthy: no fail-open

        assertFalse("a completed dry draw must not open the quota", service.tryConsumeIncoming());

        Thread.sleep(60); // DRY_BACKOFF_NANOS is 50 ms
        assertEquals(10, service.tryConsumeOutgoing(10)); // spends the credit and queues the next draw
        assertFalse("credit exhausted with a healthy draw in flight must refuse", service.tryConsumeIncoming());
    }

    @Test
    public void givenDrawSchedulingThrows_whenNextExhaustion_thenSingleFlightReleasedAndDrawRuns() {
        OneShotThrowingExecutor executor = new OneShotThrowingExecutor();
        service.drawExecutor = executor;
        when(rateLimitCacheService.tryConsumeTotalMsgs(anyLong())).thenReturn(10L);

        assertThrows(IllegalStateException.class, service::init); // warm-up draw scheduling blows up

        assertTrue(service.tryConsumeIncoming()); // exhausts the local balance -> schedules a draw again
        assertEquals("the single-flight latch must have been released", 1, executor.tasks.size());
        executor.runAll();
        verify(rateLimitCacheService).tryConsumeTotalMsgs(10L);
    }

    @Test
    public void givenRepeatedDryDraws_whenClamping_thenWarnsOncePerInterval() {
        service.drawExecutor = new ManualExecutor();
        when(rateLimitCacheService.tryConsumeTotalMsgs(anyLong())).thenReturn(0L);
        service.init();

        Logger logger = (Logger) LoggerFactory.getLogger(ThroughputQuotaServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.draw(10);
            service.draw(10);

            assertEquals("the clamp WARN must be rate limited to one line per interval", 1L,
                    appender.list.stream()
                            .filter(event -> event.getLevel() == Level.WARN)
                            .filter(event -> event.getFormattedMessage().contains("Total throughput quota exhausted"))
                            .count());
        } finally {
            logger.detachAppender(appender);
        }
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
