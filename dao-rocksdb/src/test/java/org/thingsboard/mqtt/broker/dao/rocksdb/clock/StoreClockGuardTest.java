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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.common.primitives.Longs;
import com.google.common.util.concurrent.Uninterruptibles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.thingsboard.mqtt.broker.dao.rocksdb.RocksDbStore;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreColumnFamily;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreMemoryBudget;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreProperties;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreWriteClass;
import org.thingsboard.mqtt.broker.dao.rocksdb.disk.TestDiskSpaceMonitors;
import org.thingsboard.mqtt.broker.dao.store.StoreException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoreClockGuardTest {

    private static final byte[] CLOCK_KEY = "clock".getBytes(StandardCharsets.UTF_8);
    private static final byte[] POSTPONED_KEY = "clock-postponed".getBytes(StandardCharsets.UTF_8);
    private static final long T0 = 1_700_000_000_000L;
    private static final long HOUR_MS = TimeUnit.HOURS.toMillis(1);
    private static final long DAY_MS = TimeUnit.DAYS.toMillis(1);

    @TempDir
    Path dir;

    private final MutableClock clock = new MutableClock(T0);
    private final AtomicLong uptimeNanos = new AtomicLong();
    private final List<StoreClockGuard> guards = new ArrayList<>();
    private StoreProperties properties;
    private RocksDbStore store;
    private ListAppender<ILoggingEvent> logged;
    private Logger guardLogger;

    @BeforeEach
    void open() {
        properties = new StoreProperties();
        properties.setPath(dir.toString());
        // long enough that the scheduled recording never runs during a test: the tests record by hand
        properties.getClock().setRecordIntervalMs(HOUR_MS);
        store = new RocksDbStore(properties, StoreMemoryBudget.forContainerMemory(1L << 30, 0, 0), TestDiskSpaceMonitors.roomy(properties), List.of());
        store.open();
        guardLogger = (Logger) LoggerFactory.getLogger(StoreClockGuard.class);
        logged = new ListAppender<>();
        logged.start();
        guardLogger.addAppender(logged);
        guardLogger.setAdditive(false);
    }

    @AfterEach
    void close() {
        guards.forEach(StoreClockGuard::stop);
        guardLogger.detachAppender(logged);
        guardLogger.setAdditive(true);
        store.close();
    }

    @Test
    void aClockBehindTheRecordPostponesUntilItPassesIt() {
        writeRecord(T0 + HOUR_MS);

        StoreClockGuard guard = start();

        assertThat(guard.cleanupAllowed()).isFalse();
        assertThat(warnings()).anySatisfy(message -> assertThat(message)
                .contains(Instant.ofEpochMilli(T0).toString())
                .contains(Instant.ofEpochMilli(T0 + HOUR_MS).toString()));

        advance(HOUR_MS - 1);
        guard.record();
        assertThat(guard.cleanupAllowed()).isFalse();
        assertThat(readRecord()).isEqualTo(T0 + HOUR_MS);           // not moved back while postponed
        assertThat(store.get(StoreColumnFamily.META, POSTPONED_KEY)).isNotNull();

        advance(1);
        assertThat(guard.cleanupAllowed()).isTrue();
        assertThat(store.get(StoreColumnFamily.META, POSTPONED_KEY)).isNull();
        advance(-HOUR_MS);
        assertThat(guard.cleanupAllowed()).isTrue();                // once allowed, allowed until the next start
    }

    @Test
    void theRecordNeverMovesBackwardsWhileRunning() {
        StoreClockGuard guard = start();
        assertThat(readRecord()).isEqualTo(T0);

        advance(TimeUnit.MINUTES.toMillis(1));
        guard.record();
        assertThat(readRecord()).isEqualTo(T0 + TimeUnit.MINUTES.toMillis(1));

        clock.set(T0 - HOUR_MS);
        guard.record();
        assertThat(readRecord()).isEqualTo(T0 + TimeUnit.MINUTES.toMillis(1));
        assertThat(guard.cleanupAllowed()).isTrue();

        clock.set(T0 + TimeUnit.MINUTES.toMillis(2));
        guard.record();
        assertThat(readRecord()).isEqualTo(T0 + TimeUnit.MINUTES.toMillis(2));
    }

    @Test
    void afterTheCapTheGuardAcceptsTheClockAndRewritesTheRecordOnce() {
        writeRecord(T0 + 10 * DAY_MS);
        StoreClockGuard guard = start();
        assertThat(guard.cleanupAllowed()).isFalse();

        advance(DAY_MS - 1);
        assertThat(guard.cleanupAllowed()).isFalse();
        int warningsBefore = warnings().size();

        advance(1);
        assertThat(guard.cleanupAllowed()).isTrue();
        assertThat(readRecord()).isEqualTo(clock.millis());
        assertThat(warnings()).hasSize(warningsBefore + 1);
        assertThat(guard.cleanupAllowed()).isTrue();
        assertThat(warnings()).hasSize(warningsBefore + 1);         // the cap ends once

        advance(TimeUnit.MINUTES.toMillis(1));
        guard.record();
        assertThat(readRecord()).isEqualTo(clock.millis());         // recording goes on from the accepted clock

        guard.stop();
        uptimeNanos.set(0);
        StoreClockGuard restarted = start();
        assertThat(restarted.cleanupAllowed()).isTrue();            // a restart now does not postpone
    }

    @Test
    void theCapAlsoEndsOnARecordingWithoutACleanupAsking() {
        writeRecord(T0 + 10 * DAY_MS);
        StoreClockGuard guard = start();

        advance(DAY_MS);
        guard.record();

        assertThat(readRecord()).isEqualTo(clock.millis());
        assertThat(guard.cleanupAllowed()).isTrue();
    }

    @Test
    void theCapCountsTheUptimeSpentPostponingAcrossRestarts() {
        writeRecord(T0 + 10 * DAY_MS);
        StoreClockGuard first = start();
        for (int hour = 0; hour < 20; hour++) {
            advance(HOUR_MS);
            first.record();
        }
        assertThat(first.cleanupAllowed()).isFalse();
        first.stop();

        uptimeNanos.set(0);                                         // a new boot: its uptime starts again
        StoreClockGuard second = start();

        assertThat(second.cleanupAllowed()).isFalse();
        assertThat(warnings()).last().satisfies(message -> assertThat(message).contains(String.valueOf(20 * HOUR_MS)));
        advance(4 * HOUR_MS - 1);
        assertThat(second.cleanupAllowed()).isFalse();
        int warningsBefore = warnings().size();

        advance(1);
        assertThat(second.cleanupAllowed()).isTrue();
        assertThat(readRecord()).isEqualTo(clock.millis());
        assertThat(warnings()).hasSize(warningsBefore + 1);
        assertThat(store.get(StoreColumnFamily.META, POSTPONED_KEY)).isNull();
        second.record();
        assertThat(warnings()).hasSize(warningsBefore + 1);         // the record is overwritten once
    }

    @Test
    void theUptimeSpentPostponingIsEightBigEndianBytesUnderItsKey() {
        writeRecord(T0 + 10 * DAY_MS);
        StoreClockGuard guard = start();

        advance(HOUR_MS);
        guard.record();

        assertThat(store.get(StoreColumnFamily.META, POSTPONED_KEY))
                .containsExactly(0x00, 0x00, 0x00, 0x00, 0x00, 0x36, 0xEE, 0x80);
    }

    @Test
    void stopRecordsTheUptimeSpentPostponingSinceTheLastRecording() {
        writeRecord(T0 + 10 * DAY_MS);
        StoreClockGuard guard = start();
        advance(HOUR_MS);
        guard.record();

        advance(TimeUnit.MINUTES.toMillis(30));
        guard.stop();

        assertThat(Longs.fromByteArray(store.get(StoreColumnFamily.META, POSTPONED_KEY)))
                .isEqualTo(HOUR_MS + TimeUnit.MINUTES.toMillis(30));
    }

    @Test
    void aStartThatIsNotPostponedRemovesALeftoverUptimeSpentPostponing() {
        writeRecord(T0 - HOUR_MS);
        store.put(StoreWriteClass.UPDATE, StoreColumnFamily.META, POSTPONED_KEY, Longs.toByteArray(5 * HOUR_MS));

        StoreClockGuard guard = start();

        assertThat(guard.cleanupAllowed()).isTrue();
        assertThat(store.get(StoreColumnFamily.META, POSTPONED_KEY)).isNull();
    }

    @Test
    void anUptimeSpentPostponingOfTheWrongLengthCountsAsNoneAndIsLogged() {
        writeRecord(T0 + 10 * DAY_MS);
        store.put(StoreWriteClass.UPDATE, StoreColumnFamily.META, POSTPONED_KEY, new byte[]{1, 2, 3});

        StoreClockGuard guard = start();
        advance(DAY_MS - 1);

        assertThat(guard.cleanupAllowed()).isFalse();
        assertThat(warnings()).anySatisfy(message -> assertThat(message).contains("clock-postponed").contains("3 bytes"));
    }

    @Test
    void aClockAheadOfTheRecordAllowsAtOnce() {
        writeRecord(T0 - HOUR_MS);

        StoreClockGuard guard = start();

        assertThat(guard.cleanupAllowed()).isTrue();
        assertThat(readRecord()).isEqualTo(T0);                     // recorded once at startup
        assertThat(warnings()).isEmpty();
    }

    @Test
    void aStoreWithoutARecordAllowsAtOnceAndGetsOne() {
        StoreClockGuard guard = start();

        assertThat(guard.cleanupAllowed()).isTrue();
        assertThat(readRecord()).isEqualTo(T0);
    }

    @Test
    void aRecordOfTheWrongLengthIsReplacedAndDoesNotPostpone() {
        store.put(StoreWriteClass.UPDATE, StoreColumnFamily.META, CLOCK_KEY, new byte[]{1, 2, 3});

        StoreClockGuard guard = start();

        assertThat(guard.cleanupAllowed()).isTrue();
        assertThat(readRecord()).isEqualTo(T0);
        assertThat(warnings()).hasSize(1);
    }

    @Test
    void theRecordIsEightBigEndianBytesUnderTheClockKey() {
        start();

        assertThat(store.get(StoreColumnFamily.META, CLOCK_KEY))
                .containsExactly(0x00, 0x00, 0x01, 0x8B, 0xCF, 0xE5, 0x68, 0x00);
    }

    @Test
    void theRecorderRunsOnADaemonThreadAndSurvivesAFailedWrite() throws InterruptedException {
        RocksDbStore failingStore = mock(RocksDbStore.class);
        AtomicReference<Thread> thread = new AtomicReference<>();
        CountDownLatch recorded = new CountDownLatch(1);
        doThrow(new StoreException("disk")).doAnswer(invocation -> {
            thread.set(Thread.currentThread());
            recorded.countDown();
            return null;
        }).when(failingStore).put(eq(StoreWriteClass.UPDATE), eq(StoreColumnFamily.META), eq(CLOCK_KEY), any());
        properties.getClock().setRecordIntervalMs(20);
        AtomicLong wall = new AtomicLong(T0);
        // every recording sees a later clock, so each one writes
        Clock ticking = new MutableClock(T0) {
            @Override
            public long millis() {
                return wall.addAndGet(1);
            }
        };
        StoreClockGuard guard = new StoreClockGuard(failingStore, properties, ticking, uptimeNanos::get);
        guards.add(guard);

        guard.start();                                              // the startup recording fails and is only logged

        assertThat(recorded.await(5, TimeUnit.SECONDS)).isTrue();
        verify(failingStore, timeout(5000).atLeast(3)).put(eq(StoreWriteClass.UPDATE), eq(StoreColumnFamily.META), eq(CLOCK_KEY), any());
        assertThat(thread.get().getName()).isEqualTo("store-clock-recorder");
        assertThat(thread.get().isDaemon()).isTrue();
        assertThat(guard.cleanupAllowed()).isTrue();
    }

    @Test
    void stopDoesNotWaitForARecorderStuckInTheStore() throws InterruptedException {
        RocksDbStore stuckStore = mock(RocksDbStore.class);
        when(stuckStore.get(StoreColumnFamily.META, CLOCK_KEY)).thenReturn(Longs.toByteArray(T0 + DAY_MS));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        // the postponed recording holds the guard's monitor in a put that ignores the interrupt
        doAnswer(invocation -> {
            entered.countDown();
            Uninterruptibles.awaitUninterruptibly(release);
            return null;
        }).when(stuckStore).put(eq(StoreWriteClass.UPDATE), eq(StoreColumnFamily.META), eq(POSTPONED_KEY), any());
        properties.getClock().setRecordIntervalMs(20);
        StoreClockGuard guard = new StoreClockGuard(stuckStore, properties, clock, uptimeNanos::get);
        guards.add(guard);
        guard.start();
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

        try {
            // the stop timeout is 5 s
            assertTimeoutPreemptively(Duration.ofSeconds(10), guard::stop);
        } finally {
            release.countDown();
        }

        verify(stuckStore, times(1)).put(eq(StoreWriteClass.UPDATE), eq(StoreColumnFamily.META), eq(POSTPONED_KEY), any());
        assertThat(warnings()).anySatisfy(message -> assertThat(message).contains("did not stop"));
    }

    @Test
    void invalidSettingsFailStartup() {
        properties.getClock().setRecordIntervalMs(0);
        StoreClockGuard zeroInterval = new StoreClockGuard(store, properties, clock, uptimeNanos::get);
        assertThatThrownBy(zeroInterval::start).hasMessageContaining("store.clock.record-interval-ms");

        properties.getClock().setRecordIntervalMs(HOUR_MS);
        properties.getClock().setMaxPostponeMs(-1);
        StoreClockGuard negativeCap = new StoreClockGuard(store, properties, clock, uptimeNanos::get);
        assertThatThrownBy(negativeCap::start).hasMessageContaining("store.clock.max-postpone-ms");
    }

    private StoreClockGuard start() {
        StoreClockGuard guard = new StoreClockGuard(store, properties, clock, uptimeNanos::get);
        guards.add(guard);
        guard.start();
        return guard;
    }

    /**
     * Moves the wall clock and the uptime together, as time passing does.
     */
    private void advance(long ms) {
        clock.set(clock.millis() + ms);
        if (ms > 0) {
            uptimeNanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(ms));
        }
    }

    private void writeRecord(long epochMs) {
        store.put(StoreWriteClass.UPDATE, StoreColumnFamily.META, CLOCK_KEY, Longs.toByteArray(epochMs));
    }

    private long readRecord() {
        return Longs.fromByteArray(store.get(StoreColumnFamily.META, CLOCK_KEY));
    }

    private List<String> warnings() {
        return logged.list.stream().filter(event -> event.getLevel() == Level.WARN).map(ILoggingEvent::getFormattedMessage).toList();
    }

    private static class MutableClock extends Clock {

        private volatile long millis;

        MutableClock(long millis) {
            this.millis = millis;
        }

        void set(long millis) {
            this.millis = millis;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis());
        }

        @Override
        public long millis() {
            return millis;
        }

    }

}
