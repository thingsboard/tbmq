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
import org.thingsboard.mqtt.broker.dao.rocksdb.RocksDbStore;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreColumnFamily;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreMemoryBudget;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreProperties;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreWriteClass;
import org.thingsboard.mqtt.broker.dao.store.StoreException;
import org.thingsboard.mqtt.broker.dao.store.StoreFullException;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

class StatsWriteSkipperTest {

    private static final String NODE = "tbmq-lite";
    private static final String NODE_KEY = BrokerConstants.INCOMING_MSGS;
    private static final String CPU_KEY = BrokerConstants.CPU_USAGE;

    @TempDir
    Path dir;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AtomicLong nanos = new AtomicLong();
    private final Logger logger = (Logger) LoggerFactory.getLogger(StatsWriteSkipper.class);
    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    private RocksDbStore store;
    private RocksDbKeyDictionaryDao dictionary;
    private StatsWriteSkipper skipper;
    private ClientLatestStatsBuffer buffer;
    private RocksDbTimeseriesDao timeseriesDao;
    private RocksDbTimeseriesLatestDao latestDao;

    @BeforeEach
    void open() {
        StoreProperties properties = new StoreProperties();
        properties.setPath(dir.toString());
        store = spy(new RocksDbStore(properties, StoreMemoryBudget.forContainerMemory(1L << 30, 0, 0)));
        store.open();
        dictionary = new RocksDbKeyDictionaryDao(store);
        skipper = new StatsWriteSkipper(new DefaultStatsFactory(registry), nanos::get);
        RocksDbAggregationTimeseriesDao aggregation = new RocksDbAggregationTimeseriesDao(store, dictionary);
        buffer = new ClientLatestStatsBuffer(store, dictionary, skipper, TimeUnit.HOURS.toMillis(1));
        timeseriesDao = new RocksDbTimeseriesDao(store, dictionary, aggregation, skipper);
        latestDao = new RocksDbTimeseriesLatestDao(store, dictionary, aggregation, buffer, skipper);
        // the dictionary entries exist: only the stats writes below meet the full store
        dictionary.getOrSaveKeyId(NODE_KEY);
        dictionary.getOrSaveKeyId(CPU_KEY);
        logged.start();
        logger.addAppender(logged);
    }

    @AfterEach
    void close() {
        logger.detachAppender(logged);
        logged.stop();
        buffer.destroy();
        store.close();
    }

    @Test
    void aTimeseriesSaveAndADirectLatestWriteOnAFullStoreCompleteAndAreCounted() throws Exception {
        storeIsFull();

        timeseriesDao.save(NODE, entry(NODE_KEY, 100, 1)).get();
        latestDao.saveLatest(NODE, entry(CPU_KEY, 100, 2)).get();

        assertThat(skipped()).isEqualTo(2);
        // one WARN for two skips within a minute
        assertThat(warnings()).singleElement().satisfies(event -> assertThat(event.getFormattedMessage()).contains("full"));
        assertThat(latestDao.findLatestOpt(NODE, CPU_KEY).get()).isEmpty();
    }

    @Test
    void theWarningComesBackOnceAMinuteHasPassed() throws Exception {
        storeIsFull();

        timeseriesDao.save(NODE, entry(NODE_KEY, 100, 1)).get();
        nanos.addAndGet(TimeUnit.SECONDS.toNanos(59));
        timeseriesDao.save(NODE, entry(NODE_KEY, 200, 1)).get();
        assertThat(warnings()).hasSize(1);

        nanos.addAndGet(TimeUnit.SECONDS.toNanos(1));
        timeseriesDao.save(NODE, entry(NODE_KEY, 300, 1)).get();

        assertThat(warnings()).hasSize(2);
        assertThat(skipped()).isEqualTo(3);
    }

    @Test
    void anotherStoreFailureIsAFailedFutureAndIsNotCounted() {
        doThrow(new StoreException("boom")).when(store).put(eq(StoreWriteClass.NEW_DATA), eq(StoreColumnFamily.TS), any(), any());

        assertThatThrownBy(() -> timeseriesDao.save(NODE, entry(NODE_KEY, 100, 1)).get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(StoreException.class);
        assertThat(skipped()).isZero();
    }

    private void storeIsFull() {
        doThrow(new StoreFullException("full")).when(store).put(any(), any(), any(), any());
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
