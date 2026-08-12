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

    private final TotalMsgsRateLimitsConfiguration totalMsgsRateLimitsConfiguration;
    private final ThroughputLimitProvider throughputLimitProvider;
    private final RateLimitCacheService rateLimitCacheService;
    private final StatsManager statsManager;

    @Value("${mqtt.rate-limits.total.block-size:0}")
    int configuredBlockSize;
    @Value("${mqtt.rate-limits.total.lease-return-ms:1000}")
    long leaseReturnMs;

    // test seams: init() creates them only when still null; volatile so the caller threads that read
    // them see the fully constructed executors and not a stale null
    volatile ExecutorService drawExecutor;
    volatile ScheduledExecutorService leaseReturnScheduler;

    private volatile boolean enabled;
    private volatile int blockSize;
    private final AtomicLong localTokens = new AtomicLong(0);
    private final AtomicBoolean drawInFlight = new AtomicBoolean(false);
    private volatile long dryUntilNanos;
    private volatile long failOpenUntilNanos;
    // Redis is unreachable: written by the draw thread, read by every caller. Cleared by ANY completed
    // draw, so it can never latch on; at startup it is false, so a node whose very first draw is still
    // failing rides the bounded credit until that draw returns - one block, deliberately accepted.
    private volatile boolean redisDegraded;
    private final AtomicLong lastClampWarnNanos = new AtomicLong();
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
        lastClampWarnNanos.set(now - CLAMP_WARN_INTERVAL_NANOS - 1); // the very first clamp warns immediately
        blockSize = configuredBlockSize > 0 ? configuredBlockSize : deriveDefaultBlockSize();
        stats = statsManager.getThroughputQuotaStats();
        if (drawExecutor == null) {
            drawExecutor = ThingsBoardExecutors.initSingleExecutorService("throughput-quota-draw");
        }
        if (leaseReturnScheduler == null && leaseReturnMs > 0) {
            leaseReturnScheduler = ThingsBoardExecutors.newSingleScheduledThreadPool("throughput-quota-lease-return");
            leaseReturnScheduler.scheduleAtFixedRate(this::returnUnusedTokens, leaseReturnMs, leaseReturnMs, TimeUnit.MILLISECONDS);
        }
        log.info("Total throughput quota enabled, block size: {}", blockSize);
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
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean tryConsumeIncoming() {
        return tryConsume(1) == 1;
    }

    @Override
    public int tryConsumeOutgoing(int n) {
        return tryConsume(n);
    }

    @Override
    public int tryConsumeOutgoingWaiting(int n) {
        // covers the quota being disabled and a non-positive n too: tryConsume grants both in full, so
        // neither can reach the draw below
        int granted = tryConsume(n);
        if (granted >= n) {
            return granted;
        }
        long now = System.nanoTime();
        if (now - failOpenUntilNanos < 0) {
            return granted; // Redis degraded: tryConsume already failed open, there is nothing to wait for
        }
        if (now - dryUntilNanos < 0) {
            return granted; // a completed draw reported the shared bucket dry: this refusal is genuine
        }
        // The local pool fell short while the shared bucket may still hold budget - the case that used to
        // destroy acknowledged messages. Draw here rather than hand back a partial grant the caller must
        // discard: this is a background delivery loop, so one Redis round trip is far cheaper than data loss.
        return granted + drawOnCallerThread(n - granted);
    }

    private int drawOnCallerThread(int shortfall) {
        // draw a whole block even for a small shortfall, exactly as scheduleDrawIfNeeded does: a device replay
        // charges 1 per message, so drawing only the shortfall would cost one Redis round trip PER MESSAGE
        // and throw away the amortisation the block design exists for.
        long drawSize = Math.max(blockSize, shortfall);
        try {
            long drawn = rateLimitCacheService.tryConsumeTotalMsgs(drawSize);
            redisDegraded = false; // the draw completed - dry or not, Redis answered
            if (drawn <= 0) {
                dryUntilNanos = System.nanoTime() + DRY_BACKOFF_NANOS;
                warnQuotaClamped();
                return 0;
            }
            int granted = (int) Math.min(shortfall, drawn);
            if (drawn > granted) {
                localTokens.addAndGet(drawn - granted); // keep the rest of the block local so draws amortise
            }
            return granted;
        } catch (Exception e) {
            redisDegraded = true;
            failOpenUntilNanos = System.nanoTime() + FAIL_OPEN_NANOS;
            stats.incrementRedisDegraded();
            log.warn("Failed to draw total throughput quota tokens from Redis, failing open for {} ms",
                    TimeUnit.NANOSECONDS.toMillis(FAIL_OPEN_NANOS), e);
            return shortfall; // fail open, consistent with tryConsume
        }
    }

    private int tryConsume(int n) {
        if (!enabled || n <= 0) {
            return Math.max(0, n);
        }
        long now = System.nanoTime();
        if (now - failOpenUntilNanos < 0) {
            return n; // Redis is degraded: fail open, never queue, never stall
        }
        // while a draw may still help, demand is granted on bounded credit down to -blockSize;
        // once a draw confirmed the bucket dry, refuse locally until the backoff elapses
        long creditFloor = now - dryUntilNanos < 0 ? 0L : -(long) blockSize;
        while (true) {
            long current = localTokens.get();
            long available = current - creditFloor;
            if (available <= 0) {
                scheduleDrawIfNeeded(n);
                // the fail-open window above lapses while the next draw is still hanging on an unreachable
                // Redis, so without this the quota would turn fail-closed for that draw's whole duration
                return redisDegraded ? n : 0;
            }
            int granted = (int) Math.min(n, available);
            if (localTokens.compareAndSet(current, current - granted)) {
                if (current - granted <= 0) {
                    scheduleDrawIfNeeded(blockSize);
                }
                return granted;
            }
        }
    }

    private void scheduleDrawIfNeeded(long shortfall) {
        if (System.nanoTime() - dryUntilNanos < 0) {
            return; // bucket known dry: no Redis traffic during the backoff window
        }
        if (!drawInFlight.compareAndSet(false, true)) {
            return; // single-flight: one draw at a time per node
        }
        long drawSize = Math.max(blockSize, shortfall);
        try {
            drawExecutor.execute(() -> draw(drawSize));
        } catch (RejectedExecutionException e) {
            drawInFlight.set(false); // shutting down
        } catch (Throwable t) {
            drawInFlight.set(false); // never wedge the single-flight latch: a stuck latch stops every future draw
            throw t;
        }
    }

    void draw(long drawSize) {
        try {
            long granted = rateLimitCacheService.tryConsumeTotalMsgs(drawSize);
            redisDegraded = false; // the draw completed - dry or not, Redis answered
            if (granted > 0) {
                localTokens.addAndGet(granted); // repays any credit deficit first
            } else {
                dryUntilNanos = System.nanoTime() + DRY_BACKOFF_NANOS;
                warnQuotaClamped();
            }
        } catch (Exception e) {
            redisDegraded = true;
            failOpenUntilNanos = System.nanoTime() + FAIL_OPEN_NANOS;
            stats.incrementRedisDegraded();
            log.warn("Failed to draw total throughput quota tokens from Redis, failing open for {} ms",
                    TimeUnit.NANOSECONDS.toMillis(FAIL_OPEN_NANOS), e);
        } finally {
            drawInFlight.set(false);
        }
    }

    private void warnQuotaClamped() {
        long now = System.nanoTime();
        // no longer reached only from the single-flight draw thread: tryConsumeOutgoingWaiting draws on the
        // caller's thread, so an exhausted bucket can bring every delivery thread through here at once.
        // CAS the timestamp so exactly one of them per interval wins the right to log, instead of relying
        // on a single writer as the plain read-then-write did.
        long last = lastClampWarnNanos.get();
        if (now - last > CLAMP_WARN_INTERVAL_NANOS && lastClampWarnNanos.compareAndSet(last, now)) {
            log.warn("Total throughput quota exhausted - refusing PUBLISH packets until the shared budget refills " +
                    "(see droppedMsgs and throughputQuotaDegraded metrics)");
        }
    }

    void returnUnusedTokens() {
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
            localTokens.addAndGet(current); // keep the budget locally rather than losing it
            log.debug("Failed to return unused total throughput quota tokens to Redis", e);
        }
    }

    int deriveDefaultBlockSize() {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1, throughputLimitProvider.getSustainedRatePerSec() / 10));
    }
}
