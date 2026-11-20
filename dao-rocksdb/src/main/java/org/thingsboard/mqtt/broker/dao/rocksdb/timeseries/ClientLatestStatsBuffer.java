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
package org.thingsboard.mqtt.broker.dao.rocksdb.timeseries;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvEntry;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.dao.dictionary.KeyDictionaryDao;
import org.thingsboard.mqtt.broker.dao.rocksdb.RocksDbStore;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreColumnFamily;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreWriteClass;
import org.thingsboard.mqtt.broker.dao.store.BufferedStoreWrites;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The latest per-client stats of the standalone edition that are not in {@code ts_latest} yet. Every client reports
 * its stats each minute, so they are kept in memory and written in one batch every
 * {@code standalone.stats.client-latest-flush-interval-ms} and when the shutdown calls {@link #flush()}. A broker
 * killed between two flushes loses at most one interval of them.
 * <p>
 * An entry stays here until a flush has written it, and the reads of {@link RocksDbTimeseriesLatestDao} take it over
 * the stored value until then. A flush that fails keeps its entries for the next one. An entry replaced while a flush
 * writes it stays as well: only the entry the flush wrote is dropped.
 * <p>
 * The last entry put of each key is kept, whatever its timestamp, as the SQL upsert keeps it: a clock that steps back
 * does not freeze the stats of a client. The memory holds at most one entry per (client, key) that changed since the
 * last flush.
 */
@Slf4j
@Component
@StandaloneComponent
public class ClientLatestStatsBuffer implements BufferedStoreWrites {

    private static final String FLUSH_THREAD_NAME = "client-latest-stats-flush";
    private static final long STOP_TIMEOUT_SEC = 10;

    private final RocksDbStore store;
    private final KeyDictionaryDao keyDictionaryDao;
    private final StatsWriteSkipper skipper;
    private final long intervalMs;

    // entity id -> key -> the last entry put and not flushed yet; an entity is written only inside a compute of its own
    private final ConcurrentMap<String, ConcurrentMap<String, TsKvEntry>> pending = new ConcurrentHashMap<>();
    // one flush at a time, and no removal while a flush writes, so a flush cannot write back a removed entry
    private final ReentrantLock flushLock = new ReentrantLock();
    private ScheduledExecutorService scheduler;

    public ClientLatestStatsBuffer(RocksDbStore store, KeyDictionaryDao keyDictionaryDao, StatsWriteSkipper skipper,
                                   @Value("${standalone.stats.client-latest-flush-interval-ms:600000}") long intervalMs) {
        if (intervalMs < 1) {
            throw new IllegalArgumentException("standalone.stats.client-latest-flush-interval-ms must be at least 1, got " + intervalMs);
        }
        this.store = store;
        this.keyDictionaryDao = keyDictionaryDao;
        this.skipper = skipper;
        this.intervalMs = intervalMs;
    }

    @PostConstruct
    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, FLUSH_THREAD_NAME);
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::flush, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Stops the flush thread and waits for a flush in progress. The store closes after this bean, which depends on it,
     * so no scheduled flush runs on a closed store. What is still in memory is the shutdown's to flush.
     */
    @PreDestroy
    public void destroy() {
        if (scheduler == null) {
            return;
        }
        // a scheduled flush that has not started is cancelled, one in progress finishes
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(STOP_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                log.warn("The flush of the client latest stats did not stop within {} s", STOP_TIMEOUT_SEC);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Replaces the entry of its key, whatever their timestamps.
     */
    public void put(String entityId, TsKvEntry entry) {
        pending.compute(entityId, (id, entries) -> {
            ConcurrentMap<String, TsKvEntry> result = entries != null ? entries : new ConcurrentHashMap<>();
            result.put(entry.getKey(), entry);
            return result;
        });
    }

    public Optional<TsKvEntry> get(String entityId, String key) {
        Map<String, TsKvEntry> entries = pending.get(entityId);
        return entries == null ? Optional.empty() : Optional.ofNullable(entries.get(key));
    }

    public List<TsKvEntry> getAll(String entityId) {
        Map<String, TsKvEntry> entries = pending.get(entityId);
        return entries == null ? List.of() : List.copyOf(entries.values());
    }

    /**
     * Removes the entry from memory only; the caller removes the stored value. Waits for a flush in progress, which
     * may be writing the entry, so the stored value it then removes is the last one.
     */
    public void remove(String entityId, String key) {
        flushLock.lock();
        try {
            pending.computeIfPresent(entityId, (id, entries) -> {
                entries.remove(key);
                return entries.isEmpty() ? null : entries;
            });
        } finally {
            flushLock.unlock();
        }
    }

    /**
     * Writes every entry in memory in one {@link StoreWriteClass#NEW_DATA} batch, then drops the entries it wrote. A
     * full store skips the flush and counts it; another failure is logged at WARN. Either way the entries stay for the
     * next flush.
     */
    @Override
    public void flush() {
        flushLock.lock();
        try {
            List<PendingEntry> snapshot = snapshot();
            if (snapshot.isEmpty()) {
                return;
            }
            if (skipper.write(() -> write(snapshot))) {
                snapshot.forEach(this::dropIfUnchanged);
            }
        } catch (Exception e) {
            log.warn("Failed to flush the latest stats of the clients; they stay in memory for the next flush", e);
        } finally {
            flushLock.unlock();
        }
    }

    private List<PendingEntry> snapshot() {
        List<PendingEntry> snapshot = new ArrayList<>();
        pending.forEach((entityId, entries) -> entries.values().forEach(entry -> snapshot.add(new PendingEntry(entityId, entry))));
        return snapshot;
    }

    private void write(List<PendingEntry> snapshot) {
        // resolved before the batch: a new key id is a write of its own
        List<byte[]> keys = new ArrayList<>(snapshot.size());
        for (PendingEntry pendingEntry : snapshot) {
            int keyId = keyDictionaryDao.getOrSaveKeyId(pendingEntry.entry().getKey());
            keys.add(TsKeys.latestKey(pendingEntry.entityId(), keyId));
        }
        store.write(StoreWriteClass.NEW_DATA, batch -> {
            for (int i = 0; i < snapshot.size(); i++) {
                batch.put(StoreColumnFamily.TS_LATEST, keys.get(i), TsValueCodec.encodeLatest(snapshot.get(i).entry()));
            }
        });
    }

    // an entry put while the flush wrote stays, unless it equals the entry written
    private void dropIfUnchanged(PendingEntry written) {
        pending.computeIfPresent(written.entityId(), (id, entries) -> {
            entries.remove(written.entry().getKey(), written.entry());
            return entries.isEmpty() ? null : entries;
        });
    }

    private record PendingEntry(String entityId, TsKvEntry entry) {
    }

}
