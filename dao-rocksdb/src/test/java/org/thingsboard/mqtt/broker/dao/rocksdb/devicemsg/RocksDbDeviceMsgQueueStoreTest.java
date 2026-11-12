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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.thingsboard.mqtt.broker.common.data.PersistedPacketType;
import org.thingsboard.mqtt.broker.dao.messages.NewDeviceMsg;
import org.thingsboard.mqtt.broker.dao.messages.QueueAppend;
import org.thingsboard.mqtt.broker.dao.messages.StoredDeviceMsg;
import org.thingsboard.mqtt.broker.dao.rocksdb.RocksDbStore;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreMemoryBudget;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreProperties;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

class RocksDbDeviceMsgQueueStoreTest {

    private static final int LIMIT = 10;
    private static final int TTL_SECONDS = 600;
    private static final long NOW_MS = 1_700_000_000_000L;
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(NOW_MS), ZoneOffset.UTC);

    @TempDir
    Path dir;

    private RocksDbStore rocksDb;
    private RocksDbDeviceMsgQueueStore store;

    @BeforeEach
    void open() {
        StoreProperties properties = new StoreProperties();
        properties.setPath(dir.toString());
        rocksDb = new RocksDbStore(properties, StoreMemoryBudget.forContainerMemory(1L << 30, 0, 0));
        rocksDb.open();
        store = storeWithLimit(LIMIT);
    }

    @AfterEach
    void close() {
        rocksDb.close();
    }

    @Test
    void appendAssignsPacketIdsInOrderAndReturnsThePreviousOne() {
        var r1 = store.append(List.of(new QueueAppend("c1", msgs(3)))).get(0);
        var r2 = store.append(List.of(new QueueAppend("c1", msgs(2)))).get(0);

        assertThat(r1.previousPacketId()).isZero();
        assertThat(r2.previousPacketId()).isEqualTo(3);
        assertThat(r2.firstSequence()).isEqualTo(4);
        assertThat(r2.appended()).isEqualTo(2);
        assertThat(store.read("c1", 0, 100)).extracting(StoredDeviceMsg::packetId).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void appendWritesTheHeaderAndTheBody() {
        store.append(List.of(new QueueAppend("c1", List.of(
                new NewDeviceMsg(bytes("a"), NOW_MS - 5_000, null),
                new NewDeviceMsg(bytes("b"), NOW_MS - 5_000, 30)))));

        List<StoredDeviceMsg> read = store.read("c1", 0, 100);
        assertThat(read).extracting(StoredDeviceMsg::sequence).containsExactly(1L, 2L);
        assertThat(read).extracting(StoredDeviceMsg::packetType).containsOnly(PersistedPacketType.PUBLISH);
        assertThat(read).extracting(StoredDeviceMsg::sent).containsOnly(false);
        assertThat(read).extracting(StoredDeviceMsg::createdTimeMs).containsOnly(NOW_MS - 5_000);
        assertThat(read).extracting(StoredDeviceMsg::msgExpiresAtMs).containsExactly(0L, NOW_MS + 25_000);
        assertThat(read).extracting(m -> new String(m.body(), StandardCharsets.UTF_8)).containsExactly("a", "b");
    }

    @Test
    void fifteenAppendedWithLimitTenKeepsTheNewestTen() {        // same numbers as DeviceMsgServiceTest#testSaveAndGetMessages
        store.append(List.of(new QueueAppend("c1", msgs(15))));

        assertThat(store.read("c1", 0, 100)).extracting(StoredDeviceMsg::packetId).containsExactly(6, 7, 8, 9, 10, 11, 12, 13, 14, 15);
        assertThat(store.info("c1").count()).isEqualTo(10);
        assertThat(store.info("c1").firstSequence()).isEqualTo(6);
    }

    @Test
    void aFullQueueDropsItsOldestEntriesOnAppend() {
        store.append(List.of(new QueueAppend("c1", msgs(10))));
        store.append(List.of(new QueueAppend("c1", msgs(3))));

        assertThat(store.read("c1", 0, 100)).extracting(StoredDeviceMsg::packetId).containsExactly(4, 5, 6, 7, 8, 9, 10, 11, 12, 13);
        assertThat(store.info("c1").count()).isEqualTo(10);
        assertThat(store.info("c1").firstSequence()).isEqualTo(4);
    }

    @Test
    void readIsPagedAndResumesAfterTheLastSequence() {
        store.append(List.of(new QueueAppend("c1", msgs(7))));

        var page1 = store.read("c1", 0, 3);
        var page2 = store.read("c1", page1.get(2).sequence(), 3);

        assertThat(page1).extracting(StoredDeviceMsg::packetId).containsExactly(1, 2, 3);
        assertThat(page2).extracting(StoredDeviceMsg::packetId).containsExactly(4, 5, 6);
    }

    @Test
    void packetIdsWrapAt65535AndNeverCollideWithAStoredEntry() {  // Review Focus 2; limit 65_535 for this test
        var full = storeWithLimit(65_535);
        appendInBatches(full, "c1", 65_535);                             // ids 1..65535, queue full
        full.append(List.of(new QueueAppend("c1", msgs(3))));            // ids wrap to 1, 2, 3 and the three oldest are trimmed

        var all = full.read("c1", 0, 70_000);
        assertThat(all).hasSize(65_535);
        assertThat(all).extracting(StoredDeviceMsg::packetId).doesNotHaveDuplicates();
        assertThat(all.get(all.size() - 1).packetId()).isEqualTo(3);
        assertThat(full.remove("c1", 4)).isTrue();                       // the oldest surviving entry
        assertThat(full.read("c1", 0, 1).get(0).packetId()).isEqualTo(5);
    }

    @Test
    void aWrappedPacketIdDropsTheUnacknowledgedEntryItWouldCollideWith() {
        var wide = storeWithLimit(65_535);
        wide.append(List.of(new QueueAppend("c1", msgs(1))));           // id 1 stays unacknowledged
        for (int sent = 0; sent < 65_534; ) {                            // ids 2..65535 are appended and acknowledged
            int n = Math.min(5_000, 65_534 - sent);
            wide.append(List.of(new QueueAppend("c1", msgs(n))));
            for (int i = 0; i < n; i++) {
                assertThat(wide.remove("c1", 2 + sent + i)).isTrue();
            }
            sent += n;
        }

        wide.append(List.of(new QueueAppend("c1", msgs(1))));           // id 1 again

        assertThat(wide.read("c1", 0, 100)).extracting(StoredDeviceMsg::sequence).containsExactly(65_536L);
        assertThat(wide.info("c1").count()).isEqualTo(1);
        assertThat(wide.remove("c1", 1)).isTrue();
        assertThat(wide.info("c1").count()).isZero();
    }

    @Test
    void removeDeletesExactlyTheNamedEntry() {
        store.append(List.of(new QueueAppend("c1", msgs(4))));

        assertThat(store.remove("c1", 2)).isTrue();
        assertThat(store.remove("c1", 2)).isFalse();
        assertThat(store.remove("c1", 9)).isFalse();
        assertThat(store.remove("c1", 0)).isFalse();
        assertThat(store.remove("c1", 65_536)).isFalse();
        assertThat(store.remove("unknown", 1)).isFalse();
        assertThat(store.read("c1", 0, 100)).extracting(StoredDeviceMsg::packetId).containsExactly(1, 3, 4);
        assertThat(store.info("c1").count()).isEqualTo(3);
    }

    @Test
    void removingTheHeadAdvancesTheFirstSequencePastTheHoles() {
        store.append(List.of(new QueueAppend("c1", msgs(5))));
        store.remove("c1", 2);
        store.remove("c1", 3);

        store.remove("c1", 1);
        assertThat(store.info("c1").firstSequence()).isEqualTo(4);

        store.remove("c1", 4);
        store.remove("c1", 5);
        assertThat(store.info("c1")).satisfies(info -> {
            assertThat(info.count()).isZero();
            assertThat(info.firstSequence()).isEqualTo(6);
            assertThat(info.lastSequence()).isEqualTo(5);
        });

        store.append(List.of(new QueueAppend("c1", msgs(1))));
        assertThat(store.read("c1", 0, 100)).extracting(StoredDeviceMsg::sequence, StoredDeviceMsg::packetId)
                .containsExactly(tuple(6L, 6));
        assertThat(store.info("c1").firstSequence()).isEqualTo(6);
    }

    @Test
    void trimOfAQueueWithHolesDropsTheOldestLiveEntries() {
        store.append(List.of(new QueueAppend("c1", msgs(10))));
        store.remove("c1", 3);
        store.remove("c1", 5);

        store.append(List.of(new QueueAppend("c1", msgs(4))));           // 12 live entries, limit 10

        assertThat(store.read("c1", 0, 100)).extracting(StoredDeviceMsg::packetId).containsExactly(4, 6, 7, 8, 9, 10, 11, 12, 13, 14);
        assertThat(store.info("c1").count()).isEqualTo(10);
        assertThat(store.info("c1").firstSequence()).isEqualTo(4);
    }

    @Test
    void trimOfAQueueWithHolesCanDropEveryOldEntryAndSomeNewOnes() {
        store.append(List.of(new QueueAppend("c1", msgs(5))));
        store.remove("c1", 2);

        store.append(List.of(new QueueAppend("c1", msgs(12))));          // 4 old + 12 new, limit 10

        assertThat(store.read("c1", 0, 100)).extracting(StoredDeviceMsg::packetId).containsExactly(8, 9, 10, 11, 12, 13, 14, 15, 16, 17);
        assertThat(store.info("c1").count()).isEqualTo(10);
        assertThat(store.info("c1").firstSequence()).isEqualTo(8);
    }

    @Test
    void markPubRelChangesOnlyThePacketType() {
        store.append(List.of(new QueueAppend("c1", msgs(2))));

        assertThat(store.markPubRel("c1", 2)).isTrue();
        assertThat(store.markPubRel("c1", 3)).isFalse();

        List<StoredDeviceMsg> read = store.read("c1", 0, 100);
        assertThat(read).extracting(StoredDeviceMsg::packetType).containsExactly(PersistedPacketType.PUBLISH, PersistedPacketType.PUBREL);
        assertThat(new String(read.get(1).body(), StandardCharsets.UTF_8)).isEqualTo("m1");
        assertThat(store.info("c1").count()).isEqualTo(2);
    }

    @Test
    void twoQueuesInOneAppendShareNothing() {                      // Review Focus 1
        store.append(List.of(new QueueAppend("ab", msgs(2)), new QueueAppend("abc", msgs(3))));

        store.removeQueue("ab");

        assertThat(store.read("ab", 0, 100)).isEmpty();
        assertThat(store.read("abc", 0, 100)).hasSize(3);
        assertThat(store.info("ab").lastPacketId()).isZero();
        assertThat(store.info("abc").lastPacketId()).isEqualTo(3);
    }

    @Test
    void oneQueueTwiceInOneAppendContinuesItsIds() {
        var results = store.append(List.of(new QueueAppend("c1", msgs(2)), new QueueAppend("c2", msgs(1)),
                new QueueAppend("c1", msgs(3))));

        assertThat(results).extracting(r -> r.queueKey() + ":" + r.previousPacketId() + ":" + r.firstSequence())
                .containsExactly("c1:0:1", "c2:0:1", "c1:2:3");
        assertThat(store.read("c1", 0, 100)).extracting(StoredDeviceMsg::packetId).containsExactly(1, 2, 3, 4, 5);
        assertThat(store.info("c1").count()).isEqualTo(5);
    }

    @Test
    void resetPacketIdIsRefusedWhileEntriesExist() {               // Trello #654
        store.append(List.of(new QueueAppend("ss_g_t", msgs(2))));
        assertThat(store.resetPacketId("ss_g_t", 0)).isFalse();

        store.remove("ss_g_t", 1);
        store.remove("ss_g_t", 2);
        assertThat(store.resetPacketId("ss_g_t", 0)).isTrue();

        assertThat(store.append(List.of(new QueueAppend("ss_g_t", msgs(1)))).get(0).previousPacketId()).isZero();
        assertThat(store.read("ss_g_t", 0, 100)).extracting(StoredDeviceMsg::packetId).containsExactly(1);
    }

    @Test
    void concurrentAppendAndAckKeepCountAndIdsConsistent() throws Exception {
        var wide = storeWithLimit(65_535);
        int appenders = 4;
        int perAppender = 2_500;
        ExecutorService pool = Executors.newFixedThreadPool(appenders + 1);
        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean appending = new AtomicBoolean(true);
        try {
            List<Future<?>> appends = new ArrayList<>();
            for (int t = 0; t < appenders; t++) {
                appends.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < perAppender; i++) {
                        wide.append(List.of(new QueueAppend("c1", msgs(1))));
                    }
                    return null;
                }));
            }
            Future<Integer> acker = pool.submit(() -> {
                start.await();
                int acked = 0;
                long cursor = 0;
                while (appending.get()) {
                    for (StoredDeviceMsg msg : wide.read("c1", cursor, 100)) {
                        assertThat(wide.remove("c1", msg.packetId())).isTrue();
                        cursor = msg.sequence();
                        acked++;
                    }
                }
                return acked;
            });

            start.countDown();
            for (Future<?> append : appends) {
                append.get(60, TimeUnit.SECONDS);
            }
            appending.set(false);
            int acked = acker.get(60, TimeUnit.SECONDS);

            List<StoredDeviceMsg> left = wide.read("c1", 0, 70_000);
            assertThat(acked).isPositive();
            assertThat(wide.info("c1").count()).isEqualTo(left.size());
            assertThat(acked + left.size()).isEqualTo(appenders * perAppender);
            assertThat(wide.info("c1").lastPacketId()).isEqualTo(appenders * perAppender);
            assertThat(left).extracting(StoredDeviceMsg::packetId).doesNotHaveDuplicates();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void theStateSurvivesAReopen() {
        store.append(List.of(new QueueAppend("c1", msgs(3))));
        store.markPubRel("c1", 2);

        reopen();

        assertThat(store.read("c1", 0, 100)).extracting(StoredDeviceMsg::packetType)
                .containsExactly(PersistedPacketType.PUBLISH, PersistedPacketType.PUBREL, PersistedPacketType.PUBLISH);
        assertThat(store.info("c1").lastPacketId()).isEqualTo(3);
    }

    @Test
    void aLimitAbove65535IsRejected() {
        assertThatThrownBy(() -> storeWithLimit(65_536)).isInstanceOf(IllegalArgumentException.class);
    }

    private RocksDbDeviceMsgQueueStore storeWithLimit(int limit) {
        return new RocksDbDeviceMsgQueueStore(rocksDb, limit, TTL_SECONDS, CLOCK);
    }

    private void reopen() {
        rocksDb.close();
        open();
    }

    private static void appendInBatches(RocksDbDeviceMsgQueueStore target, String queueKey, int count) {
        for (int appended = 0; appended < count; ) {
            int n = Math.min(5_000, count - appended);
            target.append(List.of(new QueueAppend(queueKey, msgs(n))));
            appended += n;
        }
    }

    private static List<NewDeviceMsg> msgs(int n) {
        return IntStream.range(0, n).mapToObj(i -> new NewDeviceMsg(bytes("m" + i), NOW_MS, null)).toList();
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

}
