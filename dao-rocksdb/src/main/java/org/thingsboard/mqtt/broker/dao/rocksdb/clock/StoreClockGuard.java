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
package org.thingsboard.mqtt.broker.dao.rocksdb.clock;

import com.google.common.primitives.Longs;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.dao.clock.CleanupClockGuard;
import org.thingsboard.mqtt.broker.dao.rocksdb.RocksDbStore;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreColumnFamily;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreProperties;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreWriteClass;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Records the wall-clock time in the store's meta every {@code store.clock.record-interval-ms} on a daemon thread, never
 * moving the record backwards, and postpones the age-based cleanups after a start with the clock behind that record:
 * ages computed from such a clock are wrong. The postponement ends when the clock passes the record, or at the latest
 * after {@code store.clock.max-postpone-ms} of uptime, since the record itself can be wrong (written by a boot with a
 * clock far in the future). At that cap the guard accepts the current clock and overwrites the record with it, the one
 * case where the record moves backwards; otherwise a node restarting more often than the cap would postpone its
 * cleanups on every boot until that future date.
 * <p>
 * The record is the meta entry {@code clock}: the epoch milliseconds as 8 big-endian bytes, written as an
 * {@link StoreWriteClass#UPDATE}, so a full disk never refuses it.
 * <p>
 * Depends on the store, so Spring destroys it first: {@link #stop()} ends the thread before the store closes.
 */
@Slf4j
@Component
@StandaloneComponent
public class StoreClockGuard implements CleanupClockGuard {

    static final byte[] CLOCK_KEY = "clock".getBytes(StandardCharsets.UTF_8);

    private static final String THREAD_NAME = "store-clock-recorder";
    private static final long STOP_TIMEOUT_MS = 5000;

    private final RocksDbStore store;
    private final StoreProperties.ClockGuard settings;
    private final Clock clock;
    private final LongSupplier nanoClock;

    // set once the cleanups may run; never cleared until the next start
    private volatile boolean allowed;
    // everything below is guarded by this
    private long startNanos;
    // the latest time recorded, or read from the store at start; Long.MIN_VALUE when there is none
    private long recordedMs = Long.MIN_VALUE;
    private ScheduledExecutorService executor;

    @Autowired
    public StoreClockGuard(RocksDbStore store, StoreProperties properties) {
        this(store, properties, Clock.systemUTC(), System::nanoTime);
    }

    // the clocks are seams for the tests: the wall clock is what is recorded, the nano clock measures the uptime
    StoreClockGuard(RocksDbStore store, StoreProperties properties, Clock clock, LongSupplier nanoClock) {
        this.store = store;
        this.settings = properties.getClock();
        this.clock = clock;
        this.nanoClock = nanoClock;
    }

    @PostConstruct
    public synchronized void start() {
        if (settings.getRecordIntervalMs() <= 0) {
            throw new IllegalStateException("store.clock.record-interval-ms must be positive, got " + settings.getRecordIntervalMs());
        }
        if (settings.getMaxPostponeMs() < 0) {
            throw new IllegalStateException("store.clock.max-postpone-ms must not be negative, got " + settings.getMaxPostponeMs());
        }
        if (executor != null) {
            return;
        }
        startNanos = nanoClock.getAsLong();
        recordedMs = readRecord();
        long nowMs = clock.millis();
        if (nowMs < recordedMs) {
            allowed = false;
            log.warn("The clock ({}) is behind the time last recorded in the store ({}): the age-based cleanups are postponed " +
                            "until the clock passes it, for at most {} ms", Instant.ofEpochMilli(nowMs), Instant.ofEpochMilli(recordedMs),
                    settings.getMaxPostponeMs());
        } else {
            allowed = true;
            recordSafely();
        }
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, THREAD_NAME);
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::recordSafely, settings.getRecordIntervalMs(), settings.getRecordIntervalMs(),
                TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void stop() {
        ScheduledExecutorService toStop;
        synchronized (this) {
            toStop = executor;
            executor = null;
        }
        if (toStop == null) {
            return;
        }
        // outside the monitor: a recording in progress holds it
        toStop.shutdownNow();
        try {
            if (!toStop.awaitTermination(STOP_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                log.warn("The store clock recorder thread did not stop within {} ms", STOP_TIMEOUT_MS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean cleanupAllowed() {
        if (allowed) {
            return true;
        }
        synchronized (this) {
            return checkPostponement();
        }
    }

    /**
     * One recording: ends a postponement that is over, then writes the clock if it is ahead of the record.
     */
    synchronized void record() {
        if (!checkPostponement()) {
            return;
        }
        long nowMs = clock.millis();
        if (nowMs > recordedMs) {
            writeRecord(nowMs);
        }
    }

    private void recordSafely() {
        try {
            record();
        } catch (Throwable t) {
            // never rethrow: an exception would cancel the schedule
            log.warn("Failed to record the clock in the store, will retry in {} ms: {}", settings.getRecordIntervalMs(), t.toString());
        }
    }

    // under the monitor
    private boolean checkPostponement() {
        if (allowed) {
            return true;
        }
        long nowMs = clock.millis();
        if (nowMs >= recordedMs) {
            log.info("The clock ({}) has passed the time last recorded in the store: the age-based cleanups resume",
                    Instant.ofEpochMilli(nowMs));
            allowed = true;
            return true;
        }
        long uptimeMs = TimeUnit.NANOSECONDS.toMillis(nanoClock.getAsLong() - startNanos);
        if (uptimeMs < settings.getMaxPostponeMs()) {
            return false;
        }
        log.warn("The clock ({}) is still behind the time last recorded in the store ({}) after {} ms of uptime: accepting the " +
                        "clock, overwriting the record with it and resuming the age-based cleanups",
                Instant.ofEpochMilli(nowMs), Instant.ofEpochMilli(recordedMs), uptimeMs);
        allowed = true;
        try {
            writeRecord(nowMs);
        } catch (Throwable t) {
            // the next recording writes the clock again: it is ahead of the record kept in memory
            log.warn("Failed to overwrite the clock record in the store: {}", t.toString());
            recordedMs = nowMs - 1;
        }
        return true;
    }

    // under the monitor
    private void writeRecord(long epochMs) {
        store.put(StoreWriteClass.UPDATE, StoreColumnFamily.META, CLOCK_KEY, Longs.toByteArray(epochMs));
        recordedMs = epochMs;
    }

    private long readRecord() {
        byte[] bytes = store.get(StoreColumnFamily.META, CLOCK_KEY);
        if (bytes == null) {
            return Long.MIN_VALUE;
        }
        if (bytes.length != Long.BYTES) {
            log.warn("The clock record in the store has {} bytes instead of {}: ignoring it", bytes.length, Long.BYTES);
            return Long.MIN_VALUE;
        }
        return Longs.fromByteArray(bytes);
    }

}
