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
package org.thingsboard.mqtt.broker.dao.rocksdb.devicemsg;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgQueueStore;
import org.thingsboard.mqtt.broker.dao.messages.NewDeviceMsg;
import org.thingsboard.mqtt.broker.dao.messages.QueueAppend;
import org.thingsboard.mqtt.broker.dao.messages.StoredDeviceMsg;
import org.thingsboard.mqtt.broker.dao.rocksdb.RocksDbStore;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreMemoryBudget;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreProperties;
import org.thingsboard.mqtt.broker.dao.store.StoreException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class RocksDbDeviceMsgExpiryTest {

    private static final int LIMIT = 10;
    private static final int TTL_SECONDS = 600;

    @TempDir
    Path dir;

    private final MutableClock clock = new MutableClock(1_700_000_000_000L);
    private RocksDbStore rocksDb;
    private RocksDbDeviceMsgQueueStore store;

    @BeforeEach
    void open() {
        StoreProperties properties = new StoreProperties();
        properties.setPath(dir.toString());
        rocksDb = new RocksDbStore(properties, StoreMemoryBudget.forContainerMemory(1L << 30, 0, 0));
        rocksDb.open();
        store = new RocksDbDeviceMsgQueueStore(rocksDb, LIMIT, TTL_SECONDS, clock);
    }

    @AfterEach
    void close() {
        rocksDb.close();
    }

    @Test
    void mqtt_3_3_2_5_anUnsentCopyIsDeletedOnceItsIntervalHasPassed() {
        store.append(List.of(new QueueAppend("c1", List.of(msg(clock.millis(), 5), msg(clock.millis(), null)))));
        clock.advanceSeconds(6);
        assertThat(store.read("c1", 0, 100)).extracting(StoredDeviceMsg::packetId).containsExactly(2);
        assertThat(store.info("c1").count()).isEqualTo(1);
    }

    @Test
    void mqtt_4_3_3_7_aSentEntryOutlivesItsMessageExpiryInterval() {
        store.append(List.of(new QueueAppend("c1", List.of(msg(clock.millis(), 5)))));
        store.readForDelivery("c1", 0, 1);
        clock.advanceSeconds(599);                                                     // far past the 5 s interval, before the 600 s operator TTL
        var read = store.read("c1", 0, 100);
        assertThat(read).hasSize(1);
        assertThat(read.get(0).sent()).isTrue();
    }

    @Test
    void aPubRelEntryOutlivesTheMessageExpiryIntervalButNotTheOperatorTtl() {   // Trello #652
        store.append(List.of(new QueueAppend("c1", List.of(msg(clock.millis(), 5)))));
        store.readForDelivery("c1", 0, 1);
        store.markPubRel("c1", 1);
        clock.advanceSeconds(599);
        assertThat(store.read("c1", 0, 100)).hasSize(1);
        clock.advanceSeconds(2);                                                       // operator TTL is 600 s
        assertThat(store.read("c1", 0, 100)).isEmpty();
    }

    @Test
    void aPubRelEntryIsSentWithoutASentMark() {
        store.append(List.of(new QueueAppend("c1", List.of(msg(clock.millis(), 5)))));
        store.markPubRel("c1", 1);
        clock.advanceSeconds(6);

        var read = store.read("c1", 0, 100);
        assertThat(read).hasSize(1);
        assertThat(read.get(0).sent()).isTrue();
    }

    @Test
    void anIntervalOfZeroLivesForOneSecondOnly() {
        store.append(List.of(new QueueAppend("c1", List.of(msg(clock.millis(), 0)))));
        assertThat(store.read("c1", 0, 100)).hasSize(1);
        clock.advanceMillis(1001);
        assertThat(store.read("c1", 0, 100)).isEmpty();
    }

    @Test
    void mqtt_1_5_3_anIntervalOfTwoToThe31SecondsOrMoreIsReadAsUnsigned() {      // Trello #657
        // netty reads the Four Byte Integer into a signed int: 0xFFFFFFFF arrives as -1, 2^31 s as Integer.MIN_VALUE
        long createdTimeMs = clock.millis();
        store.append(List.of(new QueueAppend("c1", List.of(msg(createdTimeMs, -1), msg(createdTimeMs, Integer.MIN_VALUE)))));
        clock.advanceSeconds(TTL_SECONDS - 1);

        assertThat(store.read("c1", 0, 100))
                .extracting(StoredDeviceMsg::packetId, StoredDeviceMsg::msgExpiresAtMs)
                .containsExactly(tuple(1, createdTimeMs + 0xFFFF_FFFFL * 1000L), tuple(2, createdTimeMs + (1L << 31) * 1000L));
    }

    @Test
    void expiredEntriesAreDroppedBeforeAnyLiveOneWhenTheLimitIsHit() {          // Trello #655; limit 10
        store.append(List.of(new QueueAppend("c1", List.of(
                msg(clock.millis(), null), msg(clock.millis(), 1), msg(clock.millis(), 1)))));   // ids 1 (live), 2 and 3 (short-lived)
        store.append(List.of(new QueueAppend("c1", msgs(7))));                               // ids 4..10, queue is full
        clock.advanceSeconds(2);
        store.append(List.of(new QueueAppend("c1", msgs(2))));                               // ids 11, 12
        assertThat(store.read("c1", 0, 100)).extracting(StoredDeviceMsg::packetId).containsExactly(1, 4, 5, 6, 7, 8, 9, 10, 11, 12);
        assertThat(store.info("c1").count()).isEqualTo(10);
        assertThat(store.expiredEntries()).isEqualTo(2);
    }

    @Test
    void whenExpiryFreesTooLittleTheOldestLiveEntriesAreDroppedToo() {          // Trello #655; limit 10
        store.append(List.of(new QueueAppend("c1", List.of(msg(clock.millis(), null), msg(clock.millis(), 1)))));
        store.append(List.of(new QueueAppend("c1", msgs(8))));                               // ids 3..10, queue is full
        clock.advanceSeconds(2);
        store.append(List.of(new QueueAppend("c1", msgs(3))));                               // ids 11..13: id 2 expired, id 1 and 3 dropped

        assertThat(store.read("c1", 0, 100)).extracting(StoredDeviceMsg::packetId).containsExactly(4, 5, 6, 7, 8, 9, 10, 11, 12, 13);
        assertThat(store.info("c1").count()).isEqualTo(10);
        assertThat(store.info("c1").firstSequence()).isEqualTo(4);
        assertThat(store.expiredEntries()).isEqualTo(1);
    }

    @Test
    void aFullQueueWithNothingExpiredIsTrimmedWithoutAScan() {
        store.append(List.of(new QueueAppend("c1", msgs(10))));
        long seeksBefore = store.iteratorSeekCount();                                        // test-visible counter
        store.append(List.of(new QueueAppend("c1", msgs(5))));
        assertThat(store.iteratorSeekCount()).isEqualTo(seeksBefore);
    }

    @Test
    void aFullQueueWhoseHeadOutlivedTheTtlIsScannedAndTrimmedOfExpiredEntries() {
        store.append(List.of(new QueueAppend("c1", msgs(4))));
        clock.advanceSeconds(TTL_SECONDS + 1);
        store.append(List.of(new QueueAppend("c1", msgs(6))));                               // queue is full, ids 1..4 outlived the TTL
        long seeksBefore = store.iteratorSeekCount();

        store.append(List.of(new QueueAppend("c1", msgs(1))));

        assertThat(store.iteratorSeekCount()).isEqualTo(seeksBefore + 1);
        assertThat(store.read("c1", 0, 100)).extracting(StoredDeviceMsg::packetId).containsExactly(5, 6, 7, 8, 9, 10, 11);
        assertThat(store.info("c1").count()).isEqualTo(7);
        assertThat(store.expiredEntries()).isEqualTo(4);
    }

    @Test
    void readThatDeletesTheHeadMovesTheFirstSequenceToTheNextLiveEntry() {
        store.append(List.of(new QueueAppend("c1", List.of(msg(clock.millis(), 5), msg(clock.millis(), 5),
                msg(clock.millis(), null), msg(clock.millis(), null)))));
        clock.advanceSeconds(6);

        assertThat(store.read("c1", 0, 1)).extracting(StoredDeviceMsg::packetId).containsExactly(3);

        assertThat(store.info("c1").firstSequence()).isEqualTo(3);
        assertThat(store.info("c1").count()).isEqualTo(2);
        assertThat(store.remove("c1", 3)).isTrue();
        assertThat(store.remove("c1", 4)).isTrue();
        assertThat(store.info("c1").count()).isZero();
        assertThat(store.info("c1").firstSequence()).isEqualTo(5);
    }

    @Test
    void readOfAnEntirelyExpiredQueueEmptiesIt() {
        store.append(List.of(new QueueAppend("c1", List.of(msg(clock.millis(), 1), msg(clock.millis(), 1)))));
        clock.advanceSeconds(2);

        assertThat(store.read("c1", 0, 100)).isEmpty();

        assertThat(store.info("c1").count()).isZero();
        assertThat(store.info("c1").firstSequence()).isEqualTo(3);
        assertThat(store.append(List.of(new QueueAppend("c1", msgs(1)))).get(0).previousPacketId()).isEqualTo(2);
        assertThat(store.read("c1", 0, 100)).extracting(StoredDeviceMsg::packetId).containsExactly(3);
    }

    @Test
    void sweepRemovesExpiredEntriesOfQueuesNobodyReads() {
        store.append(List.of(new QueueAppend("gone", List.of(msg(clock.millis(), 5), msg(clock.millis(), 5)))));
        clock.advanceSeconds(6);
        assertThat(store.sweepExpired()).isEqualTo(2);
        assertThat(store.info("gone").count()).isZero();
    }

    @Test
    void sweepKeepsSentAndLiveEntriesAndDoesNotRescanAQueueWithNothingDue() {
        store.append(List.of(new QueueAppend("c1", List.of(msg(clock.millis(), 5), msg(clock.millis(), 5),
                msg(clock.millis(), 60)))));
        store.append(List.of(new QueueAppend("c2", msgs(3))));
        store.readForDelivery("c1", 0, 1);
        clock.advanceSeconds(6);

        assertThat(store.sweepExpired()).isOne();
        assertThat(store.read("c1", 0, 100)).extracting(StoredDeviceMsg::packetId).containsExactly(1, 3);
        assertThat(store.info("c1").count()).isEqualTo(2);
        assertThat(store.info("c1").firstSequence()).isEqualTo(1);

        long seeksBefore = store.iteratorSeekCount();
        assertThat(store.sweepExpired()).isZero();
        assertThat(store.iteratorSeekCount()).isEqualTo(seeksBefore + 1);                    // the walk over the metas only
    }

    @Test
    void sweepRemovesEntriesThatOutlivedTheTtl() {
        store.append(List.of(new QueueAppend("c1", msgs(3))));
        clock.advanceSeconds(TTL_SECONDS / 2);
        store.append(List.of(new QueueAppend("c1", msgs(2))));
        clock.advanceSeconds(TTL_SECONDS / 2 + 1);

        assertThat(store.sweepExpired()).isEqualTo(3);
        assertThat(store.info("c1").count()).isEqualTo(2);
        assertThat(store.info("c1").firstSequence()).isEqualTo(4);
        assertThat(store.read("c1", 0, 100)).extracting(StoredDeviceMsg::packetId).containsExactly(4, 5);
    }

    @Test
    void expiredEntriesCountsEveryEntryDeletedBecauseItExpired() {
        store.append(List.of(new QueueAppend("read", List.of(msg(clock.millis(), 1)))));
        store.append(List.of(new QueueAppend("swept", List.of(msg(clock.millis(), 1), msg(clock.millis(), 1)))));
        clock.advanceSeconds(2);

        store.read("read", 0, 100);
        assertThat(store.expiredEntries()).isEqualTo(1);
        store.sweepExpired();
        assertThat(store.expiredEntries()).isEqualTo(3);
        store.sweepExpired();
        assertThat(store.expiredEntries()).isEqualTo(3);
    }

    @Test
    void readForDeliveryMarksThePageSentAndReportsTheFlagsFromBeforeTheCall() {
        store.append(List.of(new QueueAppend("c1", msgs(5))));

        assertThat(store.readForDelivery("c1", 0, 4)).extracting(StoredDeviceMsg::sent).containsOnly(false);
        assertThat(store.info("c1").sentUpTo()).isEqualTo(4);
        assertThat(store.readForDelivery("c1", 0, 2)).extracting(StoredDeviceMsg::sent).containsExactly(true, true);
        assertThat(store.info("c1").sentUpTo()).isEqualTo(4);                              // never goes down
        assertThat(store.readForDelivery("c1", 4, 100)).extracting(StoredDeviceMsg::sequence, StoredDeviceMsg::sent)
                .containsExactly(tuple(5L, false));
        assertThat(store.info("c1").sentUpTo()).isEqualTo(5);
    }

    @Test
    void readDoesNotMarkAnythingSent() {
        store.append(List.of(new QueueAppend("c1", msgs(2))));

        store.read("c1", 0, 100);

        assertThat(store.info("c1").sentUpTo()).isZero();
    }

    @Test
    void readForDeliveryOfAnEmptyPageLeavesTheMarkAlone() {
        store.append(List.of(new QueueAppend("c1", msgs(2))));

        assertThat(store.readForDelivery("c1", 2, 100)).isEmpty();
        assertThat(store.readForDelivery("unknown", 0, 100)).isEmpty();

        assertThat(store.info("c1").sentUpTo()).isZero();
        assertThat(store.info("unknown").sentUpTo()).isZero();
    }

    @Test
    void readForDeliveryDeletesTheExpiredEntriesAndMarksTheRestInOneStep() {
        store.append(List.of(new QueueAppend("c1", List.of(msg(clock.millis(), 5), msg(clock.millis(), null),
                msg(clock.millis(), 5), msg(clock.millis(), null)))));
        clock.advanceSeconds(6);

        assertThat(store.readForDelivery("c1", 0, 100)).extracting(StoredDeviceMsg::packetId).containsExactly(2, 4);

        var info = store.info("c1");
        assertThat(info.count()).isEqualTo(2);
        assertThat(info.firstSequence()).isEqualTo(2);
        assertThat(info.sentUpTo()).isEqualTo(4);
        assertThat(store.expiredEntries()).isEqualTo(2);
    }

    @Test
    void anEntryReadForDeliveryIsNeitherSweptNorTrimmedByItsMessageExpiry() {          // limit 10
        store.append(List.of(new QueueAppend("c1", List.of(msg(clock.millis(), 5)))));
        store.readForDelivery("c1", 0, 100);
        clock.advanceSeconds(6);

        assertThat(store.sweepExpired()).isZero();
        store.append(List.of(new QueueAppend("c1", msgs(LIMIT))));

        assertThat(store.read("c1", 0, 100)).hasSize(LIMIT).extracting(StoredDeviceMsg::packetId).doesNotContain(1);
        assertThat(store.expiredEntries()).isZero();                                       // the sent entry was trimmed, not expired
    }

    @Test
    void theSweeperSweepsOnADaemonThreadAndSurvivesAFailedSweep() throws InterruptedException {
        Logger sweeperLogger = (Logger) LoggerFactory.getLogger(DeviceMsgExpirySweeper.class);
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        logged.start();
        sweeperLogger.addAppender(logged);
        sweeperLogger.setAdditive(false);
        DeviceMsgQueueStore mockStore = mock(DeviceMsgQueueStore.class);
        AtomicReference<Thread> thread = new AtomicReference<>();
        CountDownLatch swept = new CountDownLatch(1);
        doThrow(new StoreException("sweep")).doAnswer(invocation -> {
            thread.set(Thread.currentThread());
            swept.countDown();
            return 0;
        }).when(mockStore).sweepExpired();
        DeviceMsgExpirySweeper sweeper = new DeviceMsgExpirySweeper(mockStore, 50);
        try {
            sweeper.start();

            assertThat(swept.await(1, TimeUnit.SECONDS)).isTrue();
            verify(mockStore, timeout(1000).atLeast(3)).sweepExpired();
            assertThat(thread.get().getName()).isEqualTo("store-device-msg-sweeper");
            assertThat(thread.get().isDaemon()).isTrue();
            assertThat(logged.list).filteredOn(event -> event.getLevel() == Level.ERROR).hasSize(1);
        } finally {
            sweeper.stop();
            sweeperLogger.detachAppender(logged);
            sweeperLogger.setAdditive(true);
        }
    }

    @Test
    void aNonPositiveSweepIntervalFailsStartup() {
        DeviceMsgExpirySweeper sweeper = new DeviceMsgExpirySweeper(mock(DeviceMsgQueueStore.class), 0);

        assertThatThrownBy(sweeper::start).hasMessageContaining("sweep-interval-ms");
    }

    private NewDeviceMsg msg(long createdTimeMs, Integer msgExpiryIntervalSec) {
        return new NewDeviceMsg(bytes("m"), createdTimeMs, msgExpiryIntervalSec);
    }

    private List<NewDeviceMsg> msgs(int n) {
        return IntStream.range(0, n).mapToObj(i -> new NewDeviceMsg(bytes("m" + i), clock.millis(), null)).toList();
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

}
