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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.kv.BasicTsKvEntry;
import org.thingsboard.mqtt.broker.common.data.kv.LongDataEntry;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvEntry;
import org.thingsboard.mqtt.broker.common.stats.DefaultStatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsType;
import org.thingsboard.mqtt.broker.dao.rocksdb.RecordingRocksDbStore;
import org.thingsboard.mqtt.broker.dao.rocksdb.RocksDbStore;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreColumnFamily;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreMemoryBudget;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreProperties;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreWriteClass;
import org.thingsboard.mqtt.broker.dao.store.StoreException;
import org.thingsboard.mqtt.broker.dao.store.StoreFullException;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

class ClientLatestStatsBufferTest {

    private static final String CLIENT = "client-1";
    private static final String SENT = BrokerConstants.SENT_PUBLISH_MSGS;
    private static final String RECEIVED = BrokerConstants.RECEIVED_PUBLISH_MSGS;
    private static final long HOUR_MS = 3_600_000;

    @TempDir
    Path dir;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final Logger skipperLogger = (Logger) LoggerFactory.getLogger(StatsWriteSkipper.class);
    private final Logger bufferLogger = (Logger) LoggerFactory.getLogger(ClientLatestStatsBuffer.class);
    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    private RecordingRocksDbStore store;
    private RocksDbKeyDictionaryDao dictionary;
    private ClientLatestStatsBuffer buffer;

    @BeforeEach
    void open() {
        StoreProperties properties = new StoreProperties();
        properties.setPath(dir.toString());
        store = spy(new RecordingRocksDbStore(properties, StoreMemoryBudget.forContainerMemory(1L << 30, 0, 0)));
        store.open();
        dictionary = new RocksDbKeyDictionaryDao(store);
        buffer = newBuffer(HOUR_MS);
        logged.start();
        skipperLogger.addAppender(logged);
        bufferLogger.addAppender(logged);
    }

    @AfterEach
    void close() {
        skipperLogger.detachAppender(logged);
        bufferLogger.detachAppender(logged);
        logged.stop();
        buffer.destroy();
        store.close();
    }

    @Test
    void aClientKeyIsKeptInMemoryUntilTheFlush() {
        buffer.put(CLIENT, entry(SENT, 100, 5));

        assertThat(buffer.get(CLIENT, SENT)).contains(entry(SENT, 100, 5));
        assertThat(buffer.getAll(CLIENT)).containsExactly(entry(SENT, 100, 5));
        assertThat(stored(CLIENT, SENT)).isNull();

        buffer.flush();

        assertThat(stored(CLIENT, SENT)).isEqualTo(entry(SENT, 100, 5));
        // flushed: the store answers for it from now on
        assertThat(buffer.get(CLIENT, SENT)).isEmpty();
        assertThat(buffer.getAll(CLIENT)).isEmpty();
    }

    @Test
    void theFlushIsOneNewDataBatchOfEverythingPending() {
        buffer.put(CLIENT, entry(SENT, 100, 5));
        buffer.put(CLIENT, entry(RECEIVED, 100, 6));
        buffer.put("client-2", entry(SENT, 100, 7));
        resolveKeyIds();
        store.takeWriteClasses();

        buffer.flush();

        assertThat(store.takeWriteClasses()).containsExactly(StoreWriteClass.NEW_DATA);
        assertThat(stored(CLIENT, SENT)).isEqualTo(entry(SENT, 100, 5));
        assertThat(stored(CLIENT, RECEIVED)).isEqualTo(entry(RECEIVED, 100, 6));
        assertThat(stored("client-2", SENT)).isEqualTo(entry(SENT, 100, 7));

        // nothing pending: no batch at all
        buffer.flush();
        assertThat(store.takeWriteClasses()).isEmpty();
    }

    @Test
    void theNewestTimestampWins() {
        buffer.put(CLIENT, entry(SENT, 200, 2));
        buffer.put(CLIENT, entry(SENT, 100, 1));

        assertThat(buffer.get(CLIENT, SENT)).contains(entry(SENT, 200, 2));

        buffer.put(CLIENT, entry(SENT, 300, 3));
        assertThat(buffer.get(CLIENT, SENT)).contains(entry(SENT, 300, 3));

        buffer.flush();
        assertThat(stored(CLIENT, SENT)).isEqualTo(entry(SENT, 300, 3));
    }

    @Test
    void aFailedFlushKeepsTheEntriesDirty() {
        buffer.put(CLIENT, entry(SENT, 100, 5));
        resolveKeyIds();
        store.failNextWrite(new StoreException("boom"));

        buffer.flush();

        assertThat(stored(CLIENT, SENT)).isNull();
        assertThat(buffer.get(CLIENT, SENT)).contains(entry(SENT, 100, 5));
        assertThat(warnings()).singleElement().satisfies(event -> assertThat(event.getLoggerName()).isEqualTo(ClientLatestStatsBuffer.class.getName()));

        buffer.flush();

        assertThat(stored(CLIENT, SENT)).isEqualTo(entry(SENT, 100, 5));
        assertThat(buffer.get(CLIENT, SENT)).isEmpty();
    }

    @Test
    void aFullDiskSkipsAndCountsInsteadOfFailing() {
        buffer.put(CLIENT, entry(SENT, 100, 5));
        resolveKeyIds();
        store.failNextWrite(new StoreFullException("full"));

        buffer.flush();
        store.failNextWrite(new StoreFullException("full"));
        buffer.flush();

        assertThat(skipped()).isEqualTo(2);
        // one WARN for two skips within a minute, and none of the failure kind
        assertThat(warnings()).singleElement().satisfies(event -> assertThat(event.getLoggerName()).isEqualTo(StatsWriteSkipper.class.getName()));
        assertThat(stored(CLIENT, SENT)).isNull();
        assertThat(buffer.get(CLIENT, SENT)).contains(entry(SENT, 100, 5));

        buffer.flush();

        assertThat(stored(CLIENT, SENT)).isEqualTo(entry(SENT, 100, 5));
    }

    @Test
    void aPutDuringAFlushIsNeitherLostNorTakenAsFlushed() {
        buffer.put(CLIENT, entry(SENT, 100, 1));
        resolveKeyIds();
        AtomicReference<Boolean> raced = new AtomicReference<>(false);
        doAnswer(invocation -> {
            if (!raced.getAndSet(true)) {
                // after the snapshot, before the batch is written
                buffer.put(CLIENT, entry(SENT, 200, 2));
            }
            return invocation.callRealMethod();
        }).when(store).write(eq(StoreWriteClass.NEW_DATA), any());

        buffer.flush();

        assertThat(stored(CLIENT, SENT)).isEqualTo(entry(SENT, 100, 1));
        assertThat(buffer.get(CLIENT, SENT)).contains(entry(SENT, 200, 2));

        buffer.flush();

        assertThat(stored(CLIENT, SENT)).isEqualTo(entry(SENT, 200, 2));
        assertThat(buffer.get(CLIENT, SENT)).isEmpty();
    }

    @Test
    void aRemovalDropsOnlyTheMemoryEntry() {
        buffer.put(CLIENT, entry(SENT, 100, 1));
        buffer.put(CLIENT, entry(RECEIVED, 100, 2));

        buffer.remove(CLIENT, SENT);

        assertThat(buffer.getAll(CLIENT)).containsExactly(entry(RECEIVED, 100, 2));
        buffer.flush();
        assertThat(stored(CLIENT, SENT)).isNull();
        assertThat(stored(CLIENT, RECEIVED)).isEqualTo(entry(RECEIVED, 100, 2));
    }

    @Test
    void theBufferFlushesOnItsIntervalOnAThreadOfItsOwn() throws Exception {
        buffer.destroy();
        buffer = newBuffer(50);
        AtomicReference<String> flushThread = new AtomicReference<>();
        doAnswer(invocation -> {
            flushThread.set(Thread.currentThread().getName());
            return invocation.callRealMethod();
        }).when(store).write(eq(StoreWriteClass.NEW_DATA), any());
        buffer.start();

        buffer.put(CLIENT, entry(SENT, 100, 5));

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (stored(CLIENT, SENT) == null && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(stored(CLIENT, SENT)).isEqualTo(entry(SENT, 100, 5));
        assertThat(flushThread.get()).isEqualTo("client-latest-stats-flush");
    }

    @Test
    void noFlushRunsAfterTheBufferIsDestroyed() throws Exception {
        buffer.destroy();
        buffer = newBuffer(50);
        buffer.start();
        buffer.destroy();
        store.takeWriteClasses();

        buffer.put(CLIENT, entry(SENT, 100, 5));
        Thread.sleep(300);

        assertThat(store.takeWriteClasses()).isEmpty();
    }

    @Test
    void anIntervalBelowOneMillisecondIsRefused() {
        assertThatThrownBy(() -> newBuffer(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("standalone.stats.client-latest-flush-interval-ms");
    }

    private ClientLatestStatsBuffer newBuffer(long intervalMs) {
        return new ClientLatestStatsBuffer(store, dictionary, new StatsWriteSkipper(new DefaultStatsFactory(registry)), intervalMs);
    }

    private void resolveKeyIds() {
        for (String key : List.of(SENT, RECEIVED)) {
            dictionary.getOrSaveKeyId(key);
        }
    }

    private TsKvEntry stored(String entityId, String key) {
        Integer keyId = dictionary.getKeyId(key);
        if (keyId == null) {
            return null;
        }
        byte[] value = store.get(StoreColumnFamily.TS_LATEST, TsKeys.latestKey(entityId, keyId));
        return value == null ? null : TsValueCodec.decodeLatest(key, value);
    }

    private double skipped() {
        return registry.get(StatsType.DISK_FULL_SKIPPED_STATS_WRITES.getPrintName()).counter().count();
    }

    private List<ILoggingEvent> warnings() {
        return logged.list.stream().filter(event -> event.getLevel().isGreaterOrEqual(Level.WARN)).toList();
    }

    private static TsKvEntry entry(String key, long ts, long value) {
        return new BasicTsKvEntry(ts, new LongDataEntry(key, value));
    }

}
