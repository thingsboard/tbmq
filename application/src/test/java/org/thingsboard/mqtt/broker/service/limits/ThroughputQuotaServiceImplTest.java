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
import static org.junit.Assert.assertSame;
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
        // @Value is not processed here, so mirror the shipped default: without it the field would be 0, which is the
        // "fail closed the instant Redis goes away" setting and would silently change what every fail-open test means.
        service.degradedGraceMs = 30_000;
    }

    @Test
    public void givenDefaultProvider_whenMultiBandwidthConfig_thenMinSustainedRate() {
        configuration.setConfig("1000:1,50000:60");
        assertEquals(833, new DefaultThroughputLimitProvider(configuration).getSustainedRatePerSec());
    }

    // block-size auto-sizing derives from this number, so the switch to a single-bandwidth default must not have
    // moved the derived block under deployments that never set one
    @Test
    public void givenDefaultProvider_whenShippedSingleBandwidthConfig_thenSameSustainedRate() {
        configuration.setConfig("50000:60");
        assertEquals(833, new DefaultThroughputLimitProvider(configuration).getSustainedRatePerSec());
    }

    // the provider used to re-split the config string without the bucket builder's parts.length check, so a
    // malformed entry died here with an ArrayIndexOutOfBoundsException. Both paths now share one parser.
    @Test
    public void givenMalformedConfig_whenDerivingSustainedRate_thenSameClearErrorAsTheBucketBuilder() {
        configuration.setConfig("1000");
        DefaultThroughputLimitProvider provider = new DefaultThroughputLimitProvider(configuration);

        assertThrows(IllegalArgumentException.class, provider::getSustainedRatePerSec);
        assertThrows(IllegalArgumentException.class, configuration::totalMsgsBucketConfiguration);
    }

    @Test
    public void givenQuotaDisabled_whenTryConsume_thenAlwaysGrantedWithoutRedis() {
        configuration.setEnabled(false);
        service.init();

        assertTrue(service.tryConsumeIncoming());
        assertEquals(7, service.tryConsumeOutgoing(7));
        verifyNoInteractions(rateLimitCacheService);
    }

    // init() never creates the draw executor when the quota is off, so bookkeeping that reaches it would NPE into
    // the publish dispatcher - which charges the fan-out through here on EVERY publish. Grant and do nothing else.
    @Test
    public void givenQuotaDisabled_whenBlockingCharge_thenGrantedWithoutTouchingTheDrawExecutor() {
        configuration.setEnabled(false);
        service.init();

        assertEquals(5, service.tryConsumeOutgoingBlocking(5));
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

        // credit floor is -burstAllowance (-100): exactly that many packets ride on credit
        int burstAllowance = service.deriveBurstAllowance();
        for (int i = 0; i < burstAllowance; i++) {
            assertTrue("packet " + i + " should be granted on credit", service.tryConsumeIncoming());
        }
        assertFalse("credit exhausted, must refuse", service.tryConsumeIncoming());
        assertEquals("single-flight: only the warm-up draw may be queued", 1, executor.tasks.size());

        executor.runAll(); // draw completes with 10 -> repays part of the deficit
        assertTrue(service.tryConsumeIncoming()); // fresh credit available again
    }

    // The defect this pins: ingress runs on the Netty event loop and so can never wait for Redis, which makes
    // node-local credit - not the licensed rate - what admits an arriving burst. With the floor tied to blockSize, a
    // burst one packet wider than a block was refused while the shared bucket still held most of a second's budget.
    @Test
    public void givenABurstWiderThanTheBlock_whenNoDrawCanLand_thenAllGrantedFromCredit() {
        ManualExecutor executor = new ManualExecutor();
        service.drawExecutor = executor;
        when(rateLimitCacheService.tryConsumeTotalMsgs(anyLong())).thenReturn(10L);
        service.init(); // warm-up draw queued but NOT run, so no draw can land mid-burst

        int burst = service.deriveDefaultBlockSize() + 1;
        for (int i = 0; i < burst; i++) {
            assertTrue("packet " + i + " of a burst the cluster has budget for must not be refused",
                    service.tryConsumeIncoming());
        }
    }

    // A burst leaves the pool deep in credit. Repaying one block per round-trip would keep it negative - and the node
    // refusing - for as many round-trips as the burst was wide, so a draw has to cover what was actually borrowed.
    @Test
    public void givenABurstRanUpADeficit_whenTheDrawIsScheduled_thenItCoversTheWholeDeficit() {
        ManualExecutor executor = new ManualExecutor();
        service.drawExecutor = executor;
        when(rateLimitCacheService.tryConsumeTotalMsgs(anyLong())).thenReturn(10L);
        service.init();
        executor.runAll(); // warm-up lands: local = 10
        clearInvocations(rateLimitCacheService);

        service.tryConsumeOutgoing(60); // 10 local + 50 on credit
        executor.runAll();

        verify(rateLimitCacheService).tryConsumeTotalMsgs(60L); // the 50 borrowed plus one block
    }

    @Test
    public void givenPartialLocalBudget_whenBulkConsume_thenPartialGrant() {
        when(rateLimitCacheService.tryConsumeTotalMsgs(anyLong())).thenReturn(10L, 0L);
        service.drawExecutor = MoreExecutors.newDirectExecutorService();
        service.init(); // local = 10

        // 10 local + 100 credit = 110 available; ask for 125
        assertEquals(110, service.tryConsumeOutgoing(125));
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

    // Fail-open is a bridge over a Redis blip, not a licence to serve unmetered traffic for as long as Redis stays
    // down: the 1 s window is re-armed by every failed draw, so without a deadline on the CONTINUOUS outage the
    // quota would never resume enforcing.
    @Test
    public void givenRedisDownPastGrace_whenConsuming_thenStopsGrantingFreely() throws InterruptedException {
        service.drawExecutor = MoreExecutors.newDirectExecutorService();
        service.degradedGraceMs = 100;
        when(rateLimitCacheService.tryConsumeTotalMsgs(anyLong())).thenThrow(new RuntimeException("redis down"));

        service.init(); // warm-up draw fails -> degraded, the grace starts

        assertTrue("within the grace the quota must still fail open", service.tryConsumeIncoming());

        Thread.sleep(150); // past the grace, while the fail-open window is still being re-armed

        assertFalse("past the grace an unreachable shared bucket must stop granting", service.tryConsumeIncoming());
        assertEquals("no bulk charge may ride on a grace that has expired", 0, service.tryConsumeOutgoing(1000));
    }

    // The deadline must measure ONE continuous outage. A node that loses Redis for a moment every so often is
    // healthy between the blips, so each new outage starts its own grace rather than inheriting what an earlier one
    // already spent - otherwise an intermittent Redis eventually locks the quota permanently closed.
    @Test
    public void givenIntermittentRedis_whenADrawSucceedsBetween_thenTheGraceClockRestarts() throws InterruptedException {
        service.drawExecutor = MoreExecutors.newDirectExecutorService();
        service.degradedGraceMs = 200;
        when(rateLimitCacheService.tryConsumeTotalMsgs(anyLong())).thenThrow(new RuntimeException("redis down"));
        service.init(); // fails -> the first outage's grace starts here

        Thread.sleep(150); // 150 of the 200 ms consumed

        doReturn(5L).when(rateLimitCacheService).tryConsumeTotalMsgs(anyLong());
        service.draw(10); // Redis answers: the outage is over, local pool holds 5

        when(rateLimitCacheService.tryConsumeTotalMsgs(anyLong())).thenThrow(new RuntimeException("down again"));
        service.draw(10); // a NEW outage begins

        Thread.sleep(100); // 250+ ms since the FIRST failure, but only ~100 into the new grace

        // 5 local + 100 burst credit is the most a non-degraded node could grant, so 1000 proves fail-open is live
        assertEquals("a fresh outage gets a fresh grace, not the remains of the previous one",
                1000, service.tryConsumeOutgoing(1000));
    }

    // The failure mode this pins is the original bug in miniature: the fail-open WINDOW is re-armed by every failed
    // draw, which is why it never ends. The grace must not inherit that property - it is anchored to when the outage
    // started, not to the most recent attempt, or traffic keeps buying itself another grace.
    @Test
    public void givenRedisDown_whenLaterDrawsAlsoFail_thenTheGraceStillExpires() throws InterruptedException {
        service.drawExecutor = MoreExecutors.newDirectExecutorService();
        service.degradedGraceMs = 1500;
        when(rateLimitCacheService.tryConsumeTotalMsgs(anyLong())).thenThrow(new RuntimeException("redis down"));
        service.init(); // stamps the start of the outage

        Thread.sleep(1100); // FAIL_OPEN_NANOS is 1 s, so the window has lapsed and the next charge draws again

        assertTrue("still inside the grace", service.tryConsumeIncoming()); // this charge runs another failing draw

        Thread.sleep(500); // ~1.6 s into a 1.5 s grace

        assertFalse("a failed draw must not push the deadline out - the grace measures the outage, not the attempt",
                service.tryConsumeIncoming());
    }

    // The blocking charge runs on an actor or consumer thread, and drawOnCallerThread blocks it on the Jedis socket
    // for up to redis.standalone.connectTimeout (ships at 30 s). Past the grace the answer is already "refuse", so
    // it must neither borrow nor block - but it still has to leave a probe behind, or a node whose only traffic is
    // persisted fan-out would never discover that Redis came back.
    @Test
    public void givenGraceExpired_whenBlockingCharge_thenRefusesWithoutDrawingOnCallerThread() throws InterruptedException {
        ManualExecutor executor = new ManualExecutor();
        service.drawExecutor = executor;
        service.degradedGraceMs = 1200;
        when(rateLimitCacheService.tryConsumeTotalMsgs(anyLong())).thenThrow(new RuntimeException("redis down"));
        service.init();
        executor.runAll(); // the warm-up draw fails: the outage starts here

        Thread.sleep(1300); // past the grace, and past the fail-open window that would otherwise short-circuit
        clearInvocations(rateLimitCacheService);

        assertEquals("past the grace the blocking charge grants nothing", 0, service.tryConsumeOutgoingBlocking(50));
        verify(rateLimitCacheService, never()).tryConsumeTotalMsgs(anyLong()); // nothing ran on this thread
        assertEquals("a recovery probe must still be queued for the draw executor", 1, executor.tasks.size());
    }

    // Refusing past the grace is only half the contract: the node must still notice Redis coming back. Recovery has
    // to be driven through the public charge methods, because those are the only things production calls - a test
    // that hand-invokes draw() would pass even if no charge could ever reach Redis again.
    @Test
    public void givenGraceExpired_whenRedisReturns_thenEnforcementResumes() throws InterruptedException {
        service.drawExecutor = MoreExecutors.newDirectExecutorService();
        service.degradedGraceMs = 100;
        service.degradedProbeIntervalNanos = TimeUnit.MILLISECONDS.toNanos(20);
        when(rateLimitCacheService.tryConsumeTotalMsgs(anyLong())).thenThrow(new RuntimeException("redis down"));
        service.init();

        Thread.sleep(150);
        assertFalse("past the grace the node refuses", service.tryConsumeIncoming());

        doReturn(10L).when(rateLimitCacheService).tryConsumeTotalMsgs(anyLong());
        Thread.sleep(30); // let the probe interval elapse, so the next charge is allowed to reach Redis

        // Charges are the only thing that ever reaches Redis, so this one has to probe. It still refuses - it found
        // the pool empty - but its draw lands and clears the degraded state.
        assertFalse("the probing charge itself is still refused", service.tryConsumeIncoming());

        // Recovered, so grants come from the drawn block plus the node's ordinary burst credit. Asking for far more
        // than that is what separates recovery from a fail-open window: a node still failing open returns all 5000.
        assertEquals("a node that could not probe Redis would never recover",
                10 + service.deriveBurstAllowance(), service.tryConsumeOutgoing(5000));
    }

    private AtomicLong pool() {
        return (AtomicLong) ReflectionTestUtils.getField(service, "localTokens");
    }

    // --- a degraded node and the tokens it already owns. A zero grace is "expired from the first failure", which
    //     makes these deterministic: no wall-clock wait for the deadline. ---

    @Test
    public void givenDegradedNodeWithOwnedTokens_whenReturnTick_thenTokensAreKeptLocal() {
        service.drawExecutor = MoreExecutors.newDirectExecutorService();
        when(rateLimitCacheService.tryConsumeTotalMsgs(anyLong())).thenThrow(new RuntimeException("redis down"));
        service.init(); // warm-up draw fails -> degraded
        pool().set(25); // drawn before the outage

        service.returnUnusedTokens();

        verify(rateLimitCacheService, never()).returnTotalMsgs(anyLong());
        assertEquals("the tick zeroes the pool before the Redis call, so against a hanging socket it would strand "
                + "the only tokens a grace-expired node may still spend", 25, pool().get());
    }

    @Test
    public void givenGraceExpiredWithOwnedTokens_whenConsuming_thenSpendsOnlyWhatWasDrawn() {
        service.drawExecutor = MoreExecutors.newDirectExecutorService();
        service.degradedGraceMs = 0;
        when(rateLimitCacheService.tryConsumeTotalMsgs(anyLong())).thenThrow(new RuntimeException("redis down"));
        service.init();
        pool().set(5);

        assertEquals("tokens the node actually drew must still be granted", 3, service.tryConsumeOutgoing(3));
        assertEquals("the remainder of the pool - and not one packet of credit", 2, service.tryConsumeOutgoing(10));
        assertFalse("pool spent: past the grace the node lends nothing", service.tryConsumeIncoming());
    }

    // repayCreditIfOwed's grace-expired side: a blocking charge that fully grants from owned tokens drains the
    // pool, and the fail-open window - armed by the outage's own failures - must not suppress the top-up, because
    // past the grace the spend was real budget.
    @Test
    public void givenGraceExpired_whenBlockingChargeGrantsFromOwnedTokens_thenTheDrainedPoolSchedulesTheTopUp() {
        ManualExecutor executor = new ManualExecutor();
        service.drawExecutor = executor;
        service.degradedGraceMs = 0;
        service.degradedProbeIntervalNanos = 0; // the probe gate must not hide the scheduling under test
        when(rateLimitCacheService.tryConsumeTotalMsgs(anyLong())).thenThrow(new RuntimeException("redis down"));
        service.init();
        executor.runAll(); // warm-up draw fails: degraded, grace over, fail-open window freshly armed
        pool().set(30);

        assertEquals("owned tokens grant in full without a caller-thread draw",
                30, service.tryConsumeOutgoingBlocking(30));

        assertEquals("the drained pool owes a refill: the armed window must not suppress the top-up",
                1, executor.tasks.size());
        verify(rateLimitCacheService, times(1)).tryConsumeTotalMsgs(anyLong()); // the warm-up only
    }

    // --- the grace clamp's boundaries. ---

    @Test
    public void givenNegativeGrace_whenInit_thenWarnsAndBehavesAsZero() {
        service.drawExecutor = MoreExecutors.newDirectExecutorService();
        service.degradedGraceMs = -5;
        when(rateLimitCacheService.tryConsumeTotalMsgs(anyLong())).thenThrow(new RuntimeException("redis down"));

        Logger logger = (Logger) LoggerFactory.getLogger(ThroughputQuotaServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.init(); // the clamp warns, then the warm-up draw fails

            assertEquals("a negative grace must be called out at startup", 1L,
                    appender.list.stream()
                            .filter(event -> event.getLevel() == Level.WARN)
                            .filter(event -> event.getFormattedMessage().contains("degraded-grace-ms is negative"))
                            .count());
        } finally {
            logger.detachAppender(appender);
        }
        assertFalse("clamped to 0: the first failed draw must already refuse", service.tryConsumeIncoming());
    }

    // the yml promises that 0 "refuses the moment a draw fails"
    @Test
    public void givenZeroGrace_whenTheFirstDrawFails_thenRefusesImmediately() {
        service.drawExecutor = MoreExecutors.newDirectExecutorService();
        service.degradedGraceMs = 0;
        when(rateLimitCacheService.tryConsumeTotalMsgs(anyLong())).thenThrow(new RuntimeException("redis down"));

        service.init(); // the warm-up draw fails

        assertFalse("no fail-open moment at all with a zero grace", service.tryConsumeIncoming());
        assertEquals(0, service.tryConsumeOutgoing(1000));
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

        int burstAllowance = service.deriveBurstAllowance();
        // queues the next draw. Spending the WHOLE credit matters: what follows must exercise the degraded
        // short-circuit, and with credit left it would be answered from credit instead.
        assertEquals("bounded credit is spent first", burstAllowance, service.tryConsumeOutgoing(burstAllowance));
        // that draw is in flight against a Redis that is still down and will only re-arm the window when
        // its socket finally times out (seconds): until then the quota must stay open on the degraded flag
        assertTrue("degraded node must not refuse while a failing draw is in flight", service.tryConsumeIncoming());
        assertEquals(500, service.tryConsumeOutgoing(500));

        doReturn(10L).when(rateLimitCacheService).tryConsumeTotalMsgs(anyLong());
        executor.runAll(); // the pending draw finally succeeds: the flag clears, the deficit is repaid

        assertEquals("the repaid deficit leaves exactly one block grantable", 10, service.tryConsumeOutgoing(50));
        assertFalse("with Redis healthy again, an exhausted node must refuse", service.tryConsumeIncoming());
    }

    // One shortfall must cost exactly one Redis call: taking credit as well would leave a deficit that only a second,
    // concurrent draw could repay. This is also what keeps the node's lease from becoming the ceiling on how many
    // subscribers a publish can reach.
    @Test
    public void givenShortPool_whenBlockingCharge_thenOneDrawAndNoSecondQueued() {
        ManualExecutor executor = new ManualExecutor();
        service.drawExecutor = executor;
        when(rateLimitCacheService.tryConsumeTotalMsgs(anyLong())).thenAnswer(inv -> inv.getArgument(0));
        service.init();
        executor.runAll(); // the warm-up draw lands: block 10 tokens local
        clearInvocations(rateLimitCacheService);

        int granted = service.tryConsumeOutgoingBlocking(50);

        assertEquals("the pool plus one synchronous draw must cover the whole demand", 50, granted);
        verify(rateLimitCacheService, times(1)).tryConsumeTotalMsgs(anyLong());
        assertTrue("the blocking charge must not also queue an asynchronous draw", executor.tasks.isEmpty());
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

            service.tryConsumeOutgoingBlocking(50); // draws on this thread, fails, marks the node degraded
            failureRecorded.countDown();
            drawPool.shutdown();
            assertTrue(drawPool.awaitTermination(5, TimeUnit.SECONDS)); // the stale success has landed

            assertTrue("a success that started before the failure must not clear the degraded flag",
                    service.redisHealth().degraded());
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
        int burstAllowance = service.deriveBurstAllowance();
        assertEquals(burstAllowance, service.tryConsumeOutgoing(burstAllowance)); // spends the credit, queues a draw
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
        // one block plus the single packet of credit the grant above took
        verify(rateLimitCacheService).tryConsumeTotalMsgs(11L);
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

    // --- RedisHealth transitions. These are the compound rules the service used to keep across four volatile
    //     fields by write-ordering convention; as pure functions on one immutable snapshot, the interleavings that
    //     needed latches (or were impossible to pin at all) become plain argument values. ---

    @Test
    public void givenHealthy_whenADrawFails_thenTheOutageStartsAtThatFailure() {
        ThroughputQuotaServiceImpl.RedisHealth health =
                ThroughputQuotaServiceImpl.RedisHealth.healthy(100).failed(500);

        assertTrue(health.degraded());
        assertEquals(500, health.degradedSinceNanos());
        assertEquals(500, health.lastFailureNanos());
    }

    @Test
    public void givenDegraded_whenLaterDrawsAlsoFail_thenTheOutageStartDoesNotSlide() {
        ThroughputQuotaServiceImpl.RedisHealth health =
                ThroughputQuotaServiceImpl.RedisHealth.healthy(0).failed(100).failed(900).failed(2_000);

        assertEquals("the grace measures the outage, not the latest attempt", 100, health.degradedSinceNanos());
        assertEquals("probing keys off the most recent attempt", 2_000, health.lastFailureNanos());
    }

    // The interleaving the old volatile fields could not exclude: a success that read the failure timestamp
    // BEFORE the failure stamped it could still clear the flag afterwards. As a CAS transition the decision
    // re-runs against the failed state, so the stale success leaves it untouched.
    @Test
    public void givenAFailureLandedAfterTheDrawStarted_whenThatDrawSucceeds_thenDegradedIsKept() {
        ThroughputQuotaServiceImpl.RedisHealth health =
                ThroughputQuotaServiceImpl.RedisHealth.healthy(0).failed(100);

        assertSame("a stale success must leave the state untouched", health, health.answered(50, 200));
    }

    @Test
    public void givenTheDrawStartedAfterTheLastFailure_whenItSucceeds_thenHealthyAgain() {
        ThroughputQuotaServiceImpl.RedisHealth health =
                ThroughputQuotaServiceImpl.RedisHealth.healthy(0).failed(100).answered(150, 200);

        assertFalse(health.degraded());
    }

    @Test
    public void givenRecovered_whenTheWindowIsRead_thenItIsClosedImmediately() {
        ThroughputQuotaServiceImpl.RedisHealth degraded =
                ThroughputQuotaServiceImpl.RedisHealth.healthy(0).failed(100);

        assertTrue("each failure arms the reprieve", degraded.failOpenWindowActive(101));
        assertFalse("recovery must disarm it, or the rest of the second passes unmetered",
                degraded.answered(150, 200).failOpenWindowActive(201));
    }

    @Test
    public void givenANewOutageAfterARecovery_whenTheGraceIsChecked_thenItRunsFromTheNewOutage() {
        ThroughputQuotaServiceImpl.RedisHealth health = ThroughputQuotaServiceImpl.RedisHealth.healthy(0)
                .failed(100).answered(150, 200) // the first outage ends
                .failed(300);                   // a new one begins

        assertFalse("a fresh outage gets a fresh grace", health.graceExpired(350, 100));
        assertTrue(health.graceExpired(401, 100));
    }

    @Test
    public void givenHealthy_whenTheGraceIsChecked_thenItNeverExpires() {
        assertFalse("a healthy node never consults the clock",
                ThroughputQuotaServiceImpl.RedisHealth.healthy(0).graceExpired(Long.MAX_VALUE / 2, 0));
    }

    // elapsed == grace must count as expired: the ">=" is what makes a zero grace refuse at the failure instant,
    // and a refactor to ">" would silently break exactly that promise
    @Test
    public void givenTheGraceBoundary_whenChecked_thenElapsedEqualToTheGraceIsExpired() {
        ThroughputQuotaServiceImpl.RedisHealth health = ThroughputQuotaServiceImpl.RedisHealth.healthy(0).failed(100);

        assertTrue("zero grace: already expired at the failure instant", health.graceExpired(100, 0));
        assertFalse("one nano short of the grace is still inside it", health.graceExpired(149, 50));
        assertTrue("exactly the grace is past it", health.graceExpired(150, 50));
    }
}
