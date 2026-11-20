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
package org.thingsboard.mqtt.broker.dao.rocksdb.disk;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreProperties;
import org.thingsboard.mqtt.broker.dao.store.StoreCapacity;
import org.thingsboard.mqtt.broker.dao.store.StoreFullException;

import java.nio.file.Path;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * Checks the free space of the volume that holds {@code store.path} every {@code store.disk.check-interval-ms} on a
 * daemon thread. The watermark is the larger of {@code store.disk.min-free-percent} of the volume and
 * {@code store.disk.min-free-bytes}; below it the store refuses new data, and updates and deletes go on.
 * <p>
 * RocksDB background errors reported by the store also degrade the store. Such a state clears only when a check sees
 * space above the watermark and the recovery callback the store registered says the store takes writes again; until
 * then every check tries again. An error RocksDB never recovers from on its own stays degraded until a restart.
 * <p>
 * The store depends on the monitor, never the other way round: the store registers its callback at open.
 */
@Slf4j
@Component
@StandaloneComponent
public class DiskSpaceMonitor implements StoreCapacity {

    private static final String THREAD_NAME = "store-disk-monitor";
    private static final long STOP_TIMEOUT_MS = 5000;

    private final StoreProperties properties;
    private final FreeSpaceProbe probe;

    // null until the first successful check
    private volatile Reading reading;
    // null while there is no background error to recover from
    private final AtomicReference<String> backgroundError = new AtomicReference<>();
    private volatile BooleanSupplier recovery;
    // guards the executor; check() synchronizes on the monitor itself, so stop() never waits for a check it blocks
    private final Object lifecycleLock = new Object();
    private ScheduledExecutorService executor;

    public DiskSpaceMonitor(StoreProperties properties, FreeSpaceProbe probe) {
        this.properties = properties;
        this.probe = probe;
    }

    @PostConstruct
    public void start() {
        synchronized (lifecycleLock) {
            doStart();
        }
    }

    private void doStart() {
        StoreProperties.Disk disk = properties.getDisk();
        if (disk.getCheckIntervalMs() <= 0) {
            throw new IllegalStateException("store.disk.check-interval-ms must be positive, got " + disk.getCheckIntervalMs());
        }
        if (disk.getMinFreePercent() < 0 || disk.getMinFreePercent() > 100) {
            throw new IllegalStateException("store.disk.min-free-percent must be between 0 and 100, got " + disk.getMinFreePercent());
        }
        if (disk.getMinFreeBytes() < 0) {
            throw new IllegalStateException("store.disk.min-free-bytes must not be negative, got " + disk.getMinFreeBytes());
        }
        if (executor != null) {
            return;
        }
        check();
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, THREAD_NAME);
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::checkSafely, disk.getCheckIntervalMs(), disk.getCheckIntervalMs(), TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void stop() {
        synchronized (lifecycleLock) {
            if (executor == null) {
                return;
            }
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(STOP_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    log.warn("The store disk monitor thread did not stop within {} ms", STOP_TIMEOUT_MS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            executor = null;
        }
    }

    private void checkSafely() {
        try {
            check();
        } catch (Throwable t) {
            // never rethrow: an exception would cancel the schedule
            log.error("Failed to check the free disk space of the store", t);
        }
    }

    /**
     * Reads the free space once, updates the watermark state and, when space is back after a background error, runs
     * the recovery callback. A probe failure is logged and keeps the last reading.
     */
    public synchronized void check() {
        String path = properties.getPath();
        FreeSpaceProbe.Space space;
        try {
            space = probe.probe(Path.of(path));
        } catch (Exception e) {
            log.warn("Failed to read the free disk space of the store at {}: {}", path, e.toString());
            return;
        }
        StoreProperties.Disk disk = properties.getDisk();
        long watermark = watermark(space.totalBytes(), disk.getMinFreePercent(), disk.getMinFreeBytes());
        boolean below = space.usableBytes() < watermark;
        boolean wasBelow = belowWatermark();
        reading = new Reading(space.usableBytes(), watermark, below);
        if (below && !wasBelow) {
            log.warn("Free disk space of the store at {} is below the watermark: {} bytes free, watermark {} bytes. "
                    + "New data is refused until space is freed", path, space.usableBytes(), watermark);
        } else if (!below && wasBelow) {
            log.info("Free disk space of the store at {} is back above the watermark: {} bytes free, watermark {} bytes",
                    path, space.usableBytes(), watermark);
        }
        String error = backgroundError.get();
        if (!below && error != null) {
            tryRecovery(error);
        }
    }

    private void tryRecovery(String error) {
        BooleanSupplier callback = recovery;
        boolean recovered;
        try {
            recovered = callback != null && callback.getAsBoolean();
        } catch (RuntimeException e) {
            log.warn("The store failed to recover from a background error", e);
            recovered = false;
        }
        if (recovered) {
            // a background error reported meanwhile stays until the next check
            if (backgroundError.compareAndSet(error, null)) {
                log.info("The store at {} recovered from the background error: {}", properties.getPath(), error);
            }
        } else {
            log.debug("The store at {} has not recovered from the background error yet: {}", properties.getPath(), error);
        }
    }

    /**
     * Called from RocksDB's background-error listener: the store stays degraded until a check sees space above the
     * watermark and the recovery callback succeeds.
     */
    public void reportBackgroundError(String message) {
        if (backgroundError.getAndSet(message) == null) {
            log.warn("The store at {} reported a background error: {}", properties.getPath(), message);
        } else {
            log.debug("The store at {} reported a background error: {}", properties.getPath(), message);
        }
    }

    /**
     * Registered by the store at open. Returns whether the store takes writes again; called by {@link #check()} when
     * space is above the watermark after a background error, and again on the next check while it returns false.
     */
    public void onRecovery(BooleanSupplier resume) {
        this.recovery = resume;
    }

    @Override
    public boolean belowWatermark() {
        Reading current = reading;
        return current != null && current.below();
    }

    @Override
    public Optional<String> degradedReason() {
        StringJoiner reason = new StringJoiner("; ");
        Reading current = reading;
        if (current != null && current.below()) {
            reason.add(fullMessage(current));
        }
        String error = backgroundError.get();
        if (error != null) {
            reason.add("The store reported a background error: " + error);
        }
        return reason.length() == 0 ? Optional.empty() : Optional.of(reason.toString());
    }

    /**
     * The exception the store throws for a new-data write below the watermark.
     */
    public StoreFullException storeFullException() {
        Reading current = reading;
        return new StoreFullException(current != null ? fullMessage(current)
                : "Not enough free disk space for the store at " + properties.getPath());
    }

    private String fullMessage(Reading current) {
        return "Not enough free disk space for the store at " + properties.getPath() + ": " + current.usableBytes()
                + " bytes free, watermark " + current.watermark() + " bytes";
    }

    /**
     * {@code max(totalBytes * minFreePercent / 100, minFreeBytes)}, without overflow.
     */
    static long watermark(long totalBytes, int minFreePercent, long minFreeBytes) {
        long percentOfTotal = totalBytes / 100 * minFreePercent + totalBytes % 100 * minFreePercent / 100;
        return Math.max(percentOfTotal, minFreeBytes);
    }

    private record Reading(long usableBytes, long watermark, boolean below) {
    }

}
