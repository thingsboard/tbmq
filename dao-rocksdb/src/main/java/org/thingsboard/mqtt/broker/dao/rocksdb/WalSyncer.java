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
package org.thingsboard.mqtt.broker.dao.rocksdb;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Validates {@code store.wal-sync} and, in {@code periodic} mode, syncs the WAL of the store every
 * {@code store.wal-sync-interval-ms} on a daemon thread. In {@code always} mode the store syncs on every write and
 * no thread is started. The mode is parsed by {@link WalSyncMode}, which {@link RocksDbStore} uses too.
 * <p>
 * Depends on the store, so Spring destroys it first: {@link #stop()} ends the thread before the store closes.
 */
@Slf4j
@Component
public class WalSyncer {

    private static final String THREAD_NAME = "store-wal-syncer";
    private static final long STOP_TIMEOUT_MS = 5000;

    private final RocksDbStore store;
    private final StoreProperties properties;

    private final AtomicLong failedSyncCount = new AtomicLong();
    private volatile boolean stopping;
    private ScheduledExecutorService executor;

    public WalSyncer(RocksDbStore store, StoreProperties properties) {
        this.store = store;
        this.properties = properties;
    }

    @PostConstruct
    public synchronized void start() {
        WalSyncMode mode = WalSyncMode.parse(properties.getWalSync());
        if (mode == WalSyncMode.ALWAYS) {
            log.info("store.wal-sync is '{}': the WAL is synced on every write", mode.value());
            return;
        }
        long intervalMs = properties.getWalSyncIntervalMs();
        if (intervalMs <= 0) {
            throw new IllegalStateException("store.wal-sync-interval-ms must be positive, got " + intervalMs);
        }
        if (executor != null) {
            return;
        }
        stopping = false;
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, THREAD_NAME);
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::syncOnce, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        log.info("store.wal-sync is '{}': the WAL is synced every {} ms", mode.value(), intervalMs);
    }

    private void syncOnce() {
        try {
            store.syncWal();
        } catch (Throwable t) {
            if (stopping) {
                log.debug("The WAL sync was interrupted by the shutdown", t);
                return;
            }
            failedSyncCount.incrementAndGet();
            // never rethrow: an exception would cancel the schedule
            log.error("Failed to sync the WAL of the store, will retry in {} ms", properties.getWalSyncIntervalMs(), t);
        }
    }

    @PreDestroy
    public synchronized void stop() {
        if (executor == null) {
            return;
        }
        stopping = true;
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(STOP_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                log.warn("The WAL syncer thread did not stop within {} ms", STOP_TIMEOUT_MS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        executor = null;
    }

    /**
     * Number of failed periodic syncs since startup.
     */
    public long getFailedSyncCount() {
        return failedSyncCount.get();
    }

}
