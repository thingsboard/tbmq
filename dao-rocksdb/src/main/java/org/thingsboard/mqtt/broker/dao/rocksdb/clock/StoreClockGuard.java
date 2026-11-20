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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/**
 * Records the wall-clock time in the store's meta every {@code store.clock.record-interval-ms} on a daemon thread, never
 * moving the record backwards, and postpones the age-based cleanups after a start with the clock behind that record:
 * ages computed from such a clock are wrong. The postponement ends when the clock passes the record, or at the latest
 * after {@code store.clock.max-postpone-ms} of uptime spent postponing, since the record itself can be wrong (written
 * by a boot with a clock far in the future). At that cap the guard accepts the current clock and overwrites the record
 * with it, the one case where the record moves backwards; otherwise the cleanups would wait until that future date.
 * <p>
 * The cap counts the uptime spent postponing across restarts, not only the uptime of this boot: nothing else is written
 * while the cleanups are postponed, so a node restarting more often than the cap would otherwise start counting from
 * zero on every boot, never reach it, and never run its age-based cleanups. The uptime spent so far is written on every
 * recording while postponed and once more at {@link #stop()}, unless the recorder thread does not stop in time; a boot
 * that crashes before its first recording, within one record interval, adds nothing to it. It is removed when the
 * postponement ends, and at a start that is not postponed.
 * <p>
 * The record is the meta entry {@code clock}: the epoch milliseconds as 8 big-endian bytes. The uptime spent postponing
 * is the meta entry {@code clock-postponed}: milliseconds as 8 big-endian bytes. Both are written as an
 * {@link StoreWriteClass#UPDATE}, so a full disk never refuses them.
 * <p>
 * Depends on the store, so Spring destroys it first: {@link #stop()} ends the thread before the store closes.
 */
@Slf4j
@Component
@StandaloneComponent
public class StoreClockGuard implements CleanupClockGuard {

    static final byte[] CLOCK_KEY = "clock".getBytes(StandardCharsets.UTF_8);
    static final byte[] POSTPONED_KEY = "clock-postponed".getBytes(StandardCharsets.UTF_8);

    private static final String THREAD_NAME = "store-clock-recorder";
    private static final long STOP_TIMEOUT_MS = 5000;

    private final RocksDbStore store;
    private final StoreProperties.ClockGuard settings;
    private final Clock clock;
    private final LongSupplier nanoClock;

    // set once the cleanups may run; never cleared until the next start
    private volatile boolean allowed;
    // not guarded by this, so that stop() takes it even from a recording stuck in the store
    private final AtomicReference<ScheduledExecutorService> executor = new AtomicReference<>();
    // everything below is guarded by this
    private long startNanos;
    // the uptime earlier boots spent postponing, read from the store at a postponed start; never negative
    private long spentMs;
    // the latest time recorded, or read from the store at start; Long.MIN_VALUE when there is none
    private long recordedMs = Long.MIN_VALUE;

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
        if (executor.get() != null) {
            return;
        }
        startNanos = nanoClock.getAsLong();
        recordedMs = readRecord();
        long nowMs = clock.millis();
        if (nowMs < recordedMs) {
            allowed = false;
            spentMs = readSpent();
            log.warn("The clock ({}) is behind the time last recorded in the store ({}): the age-based cleanups are postponed " +
                            "until the clock passes it, for at most {} ms of uptime, of which earlier boots already spent {} ms",
                    Instant.ofEpochMilli(nowMs), Instant.ofEpochMilli(recordedMs), settings.getMaxPostponeMs(), spentMs);
        } else {
            allowed = true;
            spentMs = 0;
            removeSpentSafely();
            recordSafely();
        }
        ScheduledExecutorService recorder = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, THREAD_NAME);
            thread.setDaemon(true);
            return thread;
        });
        recorder.scheduleWithFixedDelay(this::recordSafely, settings.getRecordIntervalMs(), settings.getRecordIntervalMs(),
                TimeUnit.MILLISECONDS);
        executor.set(recorder);
    }

    @PreDestroy
    public void stop() {
        ScheduledExecutorService toStop = executor.getAndSet(null);
        if (toStop == null) {
            return;
        }
        // outside the monitor: a recording in progress holds it
        toStop.shutdownNow();
        boolean terminated;
        try {
            terminated = toStop.awaitTermination(STOP_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            terminated = toStop.isTerminated();
        }
        if (!terminated) {
            // a recording stuck in the store may hold the monitor: do not wait for it. The next boot misses at most
            // the uptime of this one since its last recording
            if (allowed) {
                log.warn("The store clock recorder thread did not stop within {} ms", STOP_TIMEOUT_MS);
            } else {
                log.warn("The store clock recorder thread did not stop within {} ms: the uptime spent postponing the " +
                        "age-based cleanups since its last recording is not recorded", STOP_TIMEOUT_MS);
            }
            return;
        }
        synchronized (this) {
            if (!allowed) {
                try {
                    writeSpent();
                } catch (Throwable t) {
                    log.warn("Failed to record the uptime spent postponing the age-based cleanups in the store: {}", t.toString());
                }
            }
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
     * One recording: ends a postponement that is over, then writes the clock if it is ahead of the record. While the
     * postponement goes on, writes the uptime spent postponing instead.
     */
    synchronized void record() {
        if (!checkPostponement()) {
            writeSpent();
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
            removeSpentSafely();
            return true;
        }
        long postponedMs = postponedMs();
        if (postponedMs < settings.getMaxPostponeMs()) {
            return false;
        }
        log.warn("The clock ({}) is still behind the time last recorded in the store ({}) after {} ms of uptime spent " +
                        "postponing across restarts: accepting the clock, overwriting the record with it and resuming the " +
                        "age-based cleanups", Instant.ofEpochMilli(nowMs), Instant.ofEpochMilli(recordedMs), postponedMs);
        allowed = true;
        try {
            writeRecord(nowMs);
        } catch (Throwable t) {
            // the next recording writes the clock again: it is ahead of the record kept in memory
            log.warn("Failed to overwrite the clock record in the store: {}", t.toString());
            recordedMs = nowMs - 1;
        }
        removeSpentSafely();
        return true;
    }

    // under the monitor: the uptime spent postponing by the earlier boots and this one, saturated at Long.MAX_VALUE
    private long postponedMs() {
        long uptimeMs = TimeUnit.NANOSECONDS.toMillis(nanoClock.getAsLong() - startNanos);
        return uptimeMs > Long.MAX_VALUE - spentMs ? Long.MAX_VALUE : spentMs + uptimeMs;
    }

    // under the monitor
    private void writeSpent() {
        store.put(StoreWriteClass.UPDATE, StoreColumnFamily.META, POSTPONED_KEY, Longs.toByteArray(postponedMs()));
    }

    // under the monitor; reads first, so that a start that is not postponed does not write a tombstone every time
    private void removeSpentSafely() {
        try {
            if (store.get(StoreColumnFamily.META, POSTPONED_KEY) != null) {
                store.delete(StoreWriteClass.DELETE, StoreColumnFamily.META, POSTPONED_KEY);
            }
        } catch (Throwable t) {
            log.warn("Failed to remove the uptime spent postponing the age-based cleanups from the store: {}", t.toString());
        }
    }

    private long readSpent() {
        byte[] bytes = store.get(StoreColumnFamily.META, POSTPONED_KEY);
        if (bytes == null) {
            return 0;
        }
        if (bytes.length != Long.BYTES) {
            log.warn("The clock-postponed entry in the store has {} bytes instead of {}: ignoring it", bytes.length, Long.BYTES);
            return 0;
        }
        long spent = Longs.fromByteArray(bytes);
        if (spent < 0) {
            log.warn("The clock-postponed entry in the store is negative ({}): ignoring it", spent);
            return 0;
        }
        return spent;
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
