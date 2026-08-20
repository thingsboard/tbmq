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

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;
import org.thingsboard.mqtt.broker.config.TotalMsgsRateLimitsConfiguration;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.service.stats.ThroughputQuotaStats;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThroughputQuotaServiceImpl implements ThroughputQuotaService {

    static final long DRY_BACKOFF_NANOS = TimeUnit.MILLISECONDS.toNanos(50);
    static final long FAIL_OPEN_NANOS = TimeUnit.SECONDS.toNanos(1);
    static final long CLAMP_WARN_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(60);
    // Past the grace every refused charge would otherwise queue a fresh draw, and drawInFlight bounds concurrency,
    // not frequency - against a Redis that refuses connections instantly that is one round trip and one WARN per
    // packet. Probe on an interval instead.
    static final long DEGRADED_PROBE_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(1);

    private final TotalMsgsRateLimitsConfiguration totalMsgsRateLimitsConfiguration;
    private final ThroughputLimitProvider throughputLimitProvider;
    private final RateLimitCacheService rateLimitCacheService;
    private final StatsManager statsManager;

    @Value("${mqtt.rate-limits.total.block-size:0}")
    int configuredBlockSize;
    @Value("${mqtt.rate-limits.total.lease-return-ms:1000}")
    long leaseReturnMs;
    @Value("${mqtt.rate-limits.total.degraded-grace-ms:30000}")
    long degradedGraceMs;

    // test seams: init() creates them only when still null. Volatile so callers see fully constructed executors.
    volatile ExecutorService drawExecutor;
    volatile ScheduledExecutorService leaseReturnScheduler;

    private final AtomicLong localTokens = new AtomicLong(0);
    private final AtomicBoolean drawInFlight = new AtomicBoolean(false);
    private final AtomicLong lastClampWarnNanos = new AtomicLong();

    private volatile boolean enabled;
    private volatile int blockSize;
    // How far the local pool may go negative. Separate from blockSize because the two answer different questions:
    // blockSize is how much to fetch per Redis round-trip, this is how big an arriving burst the node can absorb.
    private volatile int burstAllowance;
    private volatile long dryUntilNanos;
    private volatile long failOpenUntilNanos;
    // Redis is unreachable. Any completed draw clears it, so it can never latch on.
    private volatile boolean redisDegraded;
    // timestamp of the last draw failure, so a draw that started earlier cannot clear redisDegraded
    private volatile long lastDrawFailureNanos;
    // start of the CURRENT continuous outage, stamped only on the healthy -> degraded transition. Deliberately not
    // reset when the outage ends: redisDegraded already gates every read of it, and the next failure re-stamps.
    private volatile long degradedSinceNanos;
    private volatile long degradedGraceNanos;
    private ThroughputQuotaStats stats;

    @PostConstruct
    public void init() {
        enabled = totalMsgsRateLimitsConfiguration.isEnabled();
        if (!enabled) {
            return;
        }
        long now = System.nanoTime();
        dryUntilNanos = now;
        failOpenUntilNanos = now;
        lastDrawFailureNanos = now; // no failure yet, so every draw from here on is newer than this

        lastClampWarnNanos.set(now - CLAMP_WARN_INTERVAL_NANOS - 1); // so the first clamp warns immediately
        redisDegraded = false;
        degradedSinceNanos = now; // every other piece of degraded state is reset here; these two belong with it
        if (degradedGraceMs < 0) {
            log.warn("mqtt.rate-limits.total.degraded-grace-ms is negative ({}), treating it as 0: the quota will " +
                    "refuse PUBLISH packets as soon as a single draw fails", degradedGraceMs);
        }
        degradedGraceNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(0, degradedGraceMs));
        blockSize = configuredBlockSize > 0 ? configuredBlockSize : deriveDefaultBlockSize();
        burstAllowance = deriveBurstAllowance();
        stats = statsManager.getThroughputQuotaStats();
        if (drawExecutor == null) {
            drawExecutor = ThingsBoardExecutors.initSingleExecutorService("throughput-quota-draw");
        }
        if (leaseReturnScheduler == null && leaseReturnMs > 0) {
            leaseReturnScheduler = ThingsBoardExecutors.newSingleScheduledThreadPool("throughput-quota-lease-return");
            leaseReturnScheduler.scheduleAtFixedRate(this::returnUnusedTokens, leaseReturnMs, leaseReturnMs, TimeUnit.MILLISECONDS);
        }
        log.info("Total throughput quota enabled, block size: {}, burst allowance: {}", blockSize, burstAllowance);
        scheduleDrawIfNeeded(blockSize); // warm-up so the first packets do not ride on credit
    }

    @PreDestroy
    public void destroy() {
        if (drawExecutor != null) {
            ThingsBoardExecutors.shutdownAndAwaitTermination(drawExecutor, "Throughput quota draw");
        }
        if (leaseReturnScheduler != null) {
            ThingsBoardExecutors.shutdownAndAwaitTermination(leaseReturnScheduler, "Throughput quota lease return");
        }
    }

    @Override
    public boolean tryConsumeIncoming() {
        return tryConsume(1) == 1;
    }

    @Override
    public boolean tryConsumeOutgoing() {
        return tryConsume(1) == 1;
    }

    @Override
    public int tryConsumeOutgoing(int n) {
        return tryConsume(n);
    }

    @Override
    public int tryConsumeOutgoingBlocking(int n) {
        if (!enabled) {
            // every publish charges its fan-out through here, so a disabled quota must do no bookkeeping at all -
            // none of it is even initialised
            return Math.max(0, n);
        }
        // a non-positive n is granted in full by tryConsume, so it never reaches the draw below
        int granted = tryConsume(n, false);
        if (granted >= n) {
            repayCreditIfOwed();
            return granted;
        }
        long now = System.nanoTime();
        if (degradationGraceExpired(now)) {
            // tryConsume has already refused on the deadline. Probe for recovery on the draw executor rather than
            // here: this method runs on the CALLER's thread - an actor or consumer - and drawOnCallerThread would
            // block it on the dead Jedis socket for the whole connect timeout to learn what it already knows.
            scheduleDrawIfNeeded(blockSize);
            return granted;
        }
        if (now - failOpenUntilNanos < 0) {
            return granted; // Redis degraded: tryConsume already failed open, nothing to draw
        }
        if (now - dryUntilNanos < 0) {
            return granted; // a completed draw found the shared bucket dry, so this refusal is genuine
        }
        // the local pool fell short while the shared bucket may still hold budget - draw rather than hand back a
        // partial grant the caller would have to discard
        return granted + drawOnCallerThread(n - granted);
    }

    private int drawOnCallerThread(int shortfall) {
        // draw a whole block even for a small shortfall, so a replay charging 1 per message does not cost one
        // round trip per message. The deficit is covered too: this call replaces the draw tryConsume did not
        // schedule, and without it the credit just granted would never be paid for. A racy read is fine - an
        // over-draw lands in the pool, an under-draw leaves the rest for the next draw.
        long deficit = Math.max(0L, -localTokens.get());
        long drawSize = Math.max(blockSize, shortfall + deficit);
        long startedAt = System.nanoTime();
        try {
            long drawn = rateLimitCacheService.tryConsumeTotalMsgs(drawSize);
            markRedisAnswered(startedAt);
            if (drawn <= 0) {
                dryUntilNanos = System.nanoTime() + DRY_BACKOFF_NANOS;
                warnQuotaClamped();
                return 0;
            }
            int granted = (int) Math.min(shortfall, drawn);
            if (drawn > granted) {
                // repays the credit deficit first (localTokens is negative), then keeps the rest local to amortise
                localTokens.addAndGet(drawn - granted);
            }
            return granted;
        } catch (Exception e) {
            markRedisFailed(e);
            // fail open, consistent with tryConsume - including its deadline: bridge a blip, refuse an outage
            return degradationGraceExpired(System.nanoTime()) ? 0 : shortfall;
        }
    }

    private int tryConsume(int n) {
        return tryConsume(n, true);
    }

    /**
     * @param scheduleDraw whether an exhausted pool should kick off an asynchronous draw. True for ordinary charges,
     *                     which must not touch Redis themselves. The blocking charge passes false because it is about
     *                     to draw synchronously, and scheduling here would only race a second Redis call against it.
     */
    private int tryConsume(int n, boolean scheduleDraw) {
        if (!enabled || n <= 0) {
            return Math.max(0, n);
        }
        long now = System.nanoTime();
        // Redis has been unreachable long enough that this is an outage, not a blip: stop granting on the fail-open
        // window and on credit. Deliberately NOT an early return - the draw further down is the only thing that ever
        // notices Redis coming back, so short-circuiting here would make the refusal permanent.
        boolean graceExpired = degradationGraceExpired(now);
        if (!graceExpired && now - failOpenUntilNanos < 0) {
            return n; // Redis is degraded but within the grace: fail open, never queue, never stall
        }
        // Grant on bounded credit while a draw may still help; once a draw confirmed the bucket dry, refuse locally
        // until the backoff elapses.
        // How deep that credit goes is what separates the two families of caller. A charge that schedules a draw
        // cannot wait for it - ingress runs on the Netty event loop - so it refuses the moment credit runs out, which
        // makes this floor, not the licensed rate, its instantaneous admission limit: a burst wider than the floor is
        // refused however much budget the cluster still holds. Hence burstAllowance, wide enough for a second of the
        // configured rate. The blocking charge passes false because it is about to draw synchronously; it can pay
        // rather than borrow, so it keeps the narrower floor and settles its shortfall in the same call.
        // Enforcement is unaffected by the wider floor: a deficit is repaid out of the shared bucket before the pool
        // can turn positive again, so the licensed rate still binds over any window. Only the instantaneous overshoot
        // grows, and it is bounded by the floor.
        // Past the grace the node lends nothing: it cannot know what the cluster has left, so it spends only tokens
        // it has actually drawn and refuses once they run out.
        long creditFloor = graceExpired || now - dryUntilNanos < 0
                ? 0L
                : -(long) (scheduleDraw ? burstAllowance : blockSize);
        while (true) {
            long current = localTokens.get();
            long available = current - creditFloor;
            if (available <= 0) {
                if (scheduleDraw) {
                    scheduleDrawIfNeeded(n);
                }
                if (graceExpired) {
                    return 0;
                }
                // the fail-open window lapses while the next draw still hangs on an unreachable Redis; without
                // this check the quota would turn fail-CLOSED for that draw's whole duration
                return redisDegraded ? n : 0;
            }
            int granted = (int) Math.min(n, available);
            if (localTokens.compareAndSet(current, current - granted)) {
                if (scheduleDraw && current - granted <= 0) {
                    scheduleDrawIfNeeded(blockSize);
                }
                return granted;
            }
        }
    }

    // The blocking charge suppresses tryConsume's scheduling, so when it returns without drawing it must put the
    // top-up back: a pool at or below zero owes the shared bucket for the credit it granted.
    private void repayCreditIfOwed() {
        // Past the grace the pool lends nothing, so whatever the charge just spent was real budget and a drained
        // pool genuinely owes a refill: the fail-open window must not suppress the top-up there.
        if (!degradationGraceExpired(System.nanoTime()) && System.nanoTime() - failOpenUntilNanos < 0) {
            return; // failing open spends no real budget, so nothing is owed
        }
        if (localTokens.get() <= 0) {
            scheduleDrawIfNeeded(blockSize);
        }
    }

    private void scheduleDrawIfNeeded(long shortfall) {
        if (!enabled) {
            // sole owner of drawExecutor, which init() never creates when the quota is off. Guarding here rather than
            // only at the call sites keeps a disabled service safe whatever reaches it.
            return;
        }
        if (System.nanoTime() - dryUntilNanos < 0) {
            return; // bucket known dry: no Redis traffic during the backoff window
        }
        if (redisDegraded && System.nanoTime() - lastDrawFailureNanos < DEGRADED_PROBE_INTERVAL_NANOS) {
            return; // Redis is unreachable: one probe per interval, not one per refused packet
        }
        if (!drawInFlight.compareAndSet(false, true)) {
            return; // single-flight: one draw at a time per node
        }
        // Cover the credit already granted as well as the caller's shortfall. Without the deficit a burst that ran
        // the pool down repays one blockSize per round-trip, so the pool stays negative - and the node keeps
        // refusing - for as many round-trips as the burst was wide. A racy read is fine: an over-draw lands in the
        // pool, an under-draw leaves the rest for the next draw.
        long deficit = Math.max(0L, -localTokens.get());
        long drawSize = Math.max(blockSize, shortfall + deficit);
        try {
            drawExecutor.execute(() -> draw(drawSize));
        } catch (RejectedExecutionException e) {
            drawInFlight.set(false); // shutting down
        } catch (Throwable t) {
            drawInFlight.set(false); // never wedge the latch: a stuck one would stop every future draw
            throw t;
        }
    }

    void draw(long drawSize) {
        long startedAt = System.nanoTime();
        try {
            long granted = rateLimitCacheService.tryConsumeTotalMsgs(drawSize);
            markRedisAnswered(startedAt);
            if (granted > 0) {
                localTokens.addAndGet(granted); // repays any credit deficit first
            } else {
                dryUntilNanos = System.nanoTime() + DRY_BACKOFF_NANOS;
                warnQuotaClamped();
            }
        } catch (Exception e) {
            markRedisFailed(e);
        } finally {
            drawInFlight.set(false);
        }
    }

    // Redis answered, so the node is not cut off. Two draws can be in flight at once (an ingress-scheduled one
    // alongside a blocking caller's), so clear the flag only when no failure was recorded since this draw began -
    // otherwise a slow success that started before an outage would clear a newer failure's degraded state.
    private void markRedisAnswered(long drawStartedNanos) {
        if (lastDrawFailureNanos - drawStartedNanos < 0) {
            redisDegraded = false;
            // Disarm the window this flag owns. Leaving it armed grants every charge in full for the rest of the
            // second - unmetered traffic at the exact moment enforcement should resume.
            failOpenUntilNanos = System.nanoTime();
            // Re-stamp rather than zero: if a racing failure re-raises the flag without stamping, inheriting "just
            // now" costs one grace period, while inheriting 0 would fail the node closed instantly.
            degradedSinceNanos = failOpenUntilNanos;
        }
    }

    /**
     * Whether the node has been cut off from the shared bucket for longer than the configured grace. Reading
     * {@code redisDegraded} first is what bounds the whole thing: a healthy node never consults the clock, and a
     * recovered one stops consulting it the moment a draw lands.
     */
    private boolean degradationGraceExpired(long now) {
        // subtract rather than compare against a sum: degradedSinceNanos is a nanoTime reading, so an added
        // duration can overflow and invert the test - which would fail CLOSED on the first blip for exactly the
        // operator who configured a very long grace to avoid that.
        return redisDegraded && now - degradedSinceNanos >= degradedGraceNanos;
    }

    private void markRedisFailed(Exception e) {
        long now = System.nanoTime();
        // stamp before raising the flag, so a concurrently completing draw cannot read a stale timestamp and
        // clear what we are about to set
        lastDrawFailureNanos = now;
        if (!redisDegraded) {
            // first failure of THIS outage starts the grace clock. Guarding on the flag is what makes the deadline
            // measure one continuous outage rather than sliding forward with every failed draw - which is exactly
            // how the fail-open window itself never expires.
            degradedSinceNanos = now;
        }
        redisDegraded = true;
        failOpenUntilNanos = now + FAIL_OPEN_NANOS;
        stats.incrementRedisDegraded();
        // Say which way the quota is currently failing. Claiming "failing open" while the node is in fact refusing
        // every PUBLISH would point an operator at exactly the wrong cause during a total publish outage.
        if (degradationGraceExpired(now)) {
            log.warn("Failed to draw total throughput quota tokens from Redis; the {} ms degraded grace has elapsed, " +
                    "so PUBLISH packets are refused until Redis answers again",
                    TimeUnit.NANOSECONDS.toMillis(degradedGraceNanos), e);
        } else {
            log.warn("Failed to draw total throughput quota tokens from Redis, failing open for {} ms",
                    TimeUnit.NANOSECONDS.toMillis(FAIL_OPEN_NANOS), e);
        }
    }

    private void warnQuotaClamped() {
        long now = System.nanoTime();
        // an exhausted bucket can bring every delivery thread through here at once, so CAS the timestamp: exactly
        // one of them per interval wins the right to log
        long last = lastClampWarnNanos.get();
        if (now - last > CLAMP_WARN_INTERVAL_NANOS && lastClampWarnNanos.compareAndSet(last, now)) {
            log.warn("Total throughput quota exhausted - refusing PUBLISH packets until the shared budget refills " +
                    "(see droppedMsgs and throughputQuotaDegraded metrics)");
        }
    }

    void returnUnusedTokens() {
        if (redisDegraded) {
            // this zeroes the pool BEFORE the Redis call and only restores it in the catch, so against a socket that
            // hangs rather than refuses the node would sit on an empty pool for the whole read timeout - and past the
            // grace it lends nothing, so every packet in that window is refused although the tokens were owned.
            return;
        }
        long current = localTokens.get();
        if (current <= 0) {
            return; // never return a credit deficit
        }
        if (!localTokens.compareAndSet(current, 0)) {
            return; // raced with consumption; the next tick handles it
        }
        try {
            rateLimitCacheService.returnTotalMsgs(current);
        } catch (Exception e) {
            // Deliberately over-grants: addTokens is not idempotent, so a call that timed out AFTER Redis applied it
            // leaves these tokens both here and in the shared bucket. Accepted over dropping them, which is equally
            // permanent and shrinks the configured budget.
            localTokens.addAndGet(current);
            log.debug("Failed to return unused total throughput quota tokens to Redis", e);
        }
    }

    int deriveDefaultBlockSize() {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1, throughputLimitProvider.getSustainedRatePerSec() / 10));
    }

    // One second of the configured budget: the widest burst that can arrive within a Redis round-trip and still be
    // something the cluster is entitled to serve. Never below blockSize, so a node can always absorb what one draw
    // brings it.
    int deriveBurstAllowance() {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(blockSize, throughputLimitProvider.getSustainedRatePerSec()));
    }
}
