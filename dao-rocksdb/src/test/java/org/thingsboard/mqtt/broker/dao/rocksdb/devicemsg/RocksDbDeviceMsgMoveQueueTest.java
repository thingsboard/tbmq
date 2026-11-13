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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

class RocksDbDeviceMsgMoveQueueTest {

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
        store = storeWithLimit(LIMIT);
    }

    @AfterEach
    void close() {
        rocksDb.close();
    }

    @Test
    void movedEntriesGetTheMembersNextPacketIdsAndLeaveTheGroupEmpty() {
        store.append(List.of(new QueueAppend("m1", msgs(2)), new QueueAppend("ss_g_t", msgs(3))));
        assertThat(store.moveQueue("ss_g_t", "m1", b -> b)).isEqualTo(3);
        assertThat(store.read("m1", 0, 100)).extracting(StoredDeviceMsg::packetId).containsExactly(1, 2, 3, 4, 5);
        assertThat(store.read("ss_g_t", 0, 100)).isEmpty();
        assertThat(store.info("ss_g_t").lastPacketId()).isEqualTo(3);          // Trello #654: the group's counter is kept
    }

    @Test
    void aNewGroupMessageAfterAMoveDoesNotReuseAPacketId() {             // Trello #654
        store.append(List.of(new QueueAppend("ss_g_t", msgs(3))));
        store.moveQueue("ss_g_t", "m1", b -> b);
        assertThat(store.append(List.of(new QueueAppend("ss_g_t", msgs(1)))).get(0).previousPacketId()).isEqualTo(3);
    }

    @Test
    void theBodyMapperIsAppliedAndExpiredEntriesStayBehind() {
        store.append(List.of(new QueueAppend("ss_g_t", List.of(msg(clock.millis(), 1), msg(clock.millis(), null)))));
        clock.advanceSeconds(2);
        assertThat(store.moveQueue("ss_g_t", "m1", b -> new byte[]{42})).isEqualTo(1);
        assertThat(store.read("m1", 0, 100).get(0).body()).containsExactly(42);
        assertThat(store.expiredEntries()).isOne();
    }

    @Test
    void anAppendToTheMemberDuringAMoveLosesNothing() throws Exception {
        var wide = storeWithLimit(65_535);
        wide.append(List.of(new QueueAppend("ss_g_t", IntStream.range(0, 5_000).mapToObj(i -> msg("g" + i)).toList())));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> appender = pool.submit(() -> {
                start.await();
                for (int i = 0; i < 5_000; i++) {
                    wide.append(List.of(new QueueAppend("m1", List.of(msg("a" + i)))));
                }
                return null;
            });
            Future<Integer> mover = pool.submit(() -> {
                start.await();
                while (wide.info("m1").count() < 100) {
                    Thread.onSpinWait();
                }
                return wide.moveQueue("ss_g_t", "m1", b -> b);
            });

            start.countDown();
            appender.get(60, TimeUnit.SECONDS);
            assertThat(mover.get(60, TimeUnit.SECONDS)).isEqualTo(5_000);
        } finally {
            pool.shutdownNow();
        }

        List<StoredDeviceMsg> member = wide.read("m1", 0, 20_000);
        List<String> bodies = member.stream().map(msg -> new String(msg.body(), StandardCharsets.UTF_8)).toList();
        assertThat(wide.info("m1").count()).isEqualTo(10_000);
        assertThat(member).extracting(StoredDeviceMsg::packetId)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 10_000).boxed().toList());
        assertThat(bodies).filteredOn(body -> body.startsWith("g"))
                .containsExactlyElementsOf(IntStream.range(0, 5_000).mapToObj(i -> "g" + i).toList());
        assertThat(bodies).filteredOn(body -> body.startsWith("a"))
                .containsExactlyElementsOf(IntStream.range(0, 5_000).mapToObj(i -> "a" + i).toList());
        int firstGroupBody = bodies.indexOf("g0");                                           // the move is atomic: one contiguous run
        assertThat(bodies.subList(firstGroupBody, firstGroupBody + 5_000))
                .containsExactlyElementsOf(IntStream.range(0, 5_000).mapToObj(i -> "g" + i).toList());
        assertThat(wide.info("ss_g_t").count()).isZero();
    }

    @Test
    void aQueueCannotBeMovedIntoItself() {
        store.append(List.of(new QueueAppend("ss_g_t", msgs(2))));
        assertThatThrownBy(() -> store.moveQueue("ss_g_t", "ss_g_t", b -> b)).isInstanceOf(IllegalArgumentException.class);
        assertThat(store.info("ss_g_t").count()).isEqualTo(2);
    }

    @Test
    void theEmptiedGroupContinuesItsSequenceAndPacketIds() {             // Trello #654
        store.append(List.of(new QueueAppend("ss_g_t", msgs(3))));
        store.readForDelivery("ss_g_t", 0, 2);

        store.moveQueue("ss_g_t", "m1", b -> b);

        var group = store.info("ss_g_t");
        assertThat(group.count()).isZero();
        assertThat(group.lastSequence()).isEqualTo(3);
        assertThat(group.firstSequence()).isEqualTo(4);
        assertThat(group.lastPacketId()).isEqualTo(3);
        store.append(List.of(new QueueAppend("ss_g_t", msgs(1))));
        assertThat(store.read("ss_g_t", 0, 100)).extracting(StoredDeviceMsg::sequence, StoredDeviceMsg::packetId, StoredDeviceMsg::sent)
                .containsExactly(tuple(4L, 4, false));
    }

    @Test
    void movedEntriesAreUnsentPublishesThatKeepTheirTimes() {
        long created = clock.millis() - 1_000;
        store.append(List.of(new QueueAppend("ss_g_t", List.of(msg(created, 30), msg(created, null), msg(created, 60)))));
        store.readForDelivery("ss_g_t", 0, 2);
        store.markPubRel("ss_g_t", 2);

        store.moveQueue("ss_g_t", "m1", b -> b);

        assertThat(store.read("m1", 0, 100))
                .extracting(StoredDeviceMsg::packetType, StoredDeviceMsg::sent, StoredDeviceMsg::createdTimeMs, StoredDeviceMsg::msgExpiresAtMs)
                .containsExactly(
                        tuple(PersistedPacketType.PUBLISH, false, created, created + 30_000),
                        tuple(PersistedPacketType.PUBLISH, false, created, 0L),
                        tuple(PersistedPacketType.PUBLISH, false, created, created + 60_000));
    }

    @Test
    void movedEntriesKeepTheirOperatorTtl() {
        store.append(List.of(new QueueAppend("ss_g_t", msgs(1))));
        clock.advanceSeconds(TTL_SECONDS / 2);
        store.moveQueue("ss_g_t", "m1", b -> b);
        clock.advanceSeconds(TTL_SECONDS / 2 + 1);

        assertThat(store.read("m1", 0, 100)).isEmpty();
    }

    @Test
    void movedEntriesThatOutliveTheirTtlBeforeTheMembersHeadAreSwept() {   // TTL 600 s
        store.append(List.of(new QueueAppend("ss_g_t", msgs(2))));               // the group collects first
        clock.advanceSeconds(TTL_SECONDS / 2);
        store.append(List.of(new QueueAppend("m1", msgs(1))));
        store.moveQueue("ss_g_t", "m1", b -> b);                                 // behind the member's head, expiring 300 s earlier
        clock.advanceSeconds(TTL_SECONDS / 2 + 1);

        assertThat(store.sweepExpired()).isEqualTo(2);
        assertThat(store.read("m1", 0, 100)).extracting(StoredDeviceMsg::packetId).containsExactly(1);
    }

    @Test
    void aFullMemberQueueDropsItsExpiredMovedEntriesBeforeAnyLiveOne() {  // limit 10, TTL 600 s
        store.append(List.of(new QueueAppend("ss_g_t", msgs(2))));
        clock.advanceSeconds(TTL_SECONDS / 2);
        store.append(List.of(new QueueAppend("m1", msgs(8))));
        store.moveQueue("ss_g_t", "m1", b -> b);                                 // the member is full: 8 own entries and 2 moved
        clock.advanceSeconds(TTL_SECONDS / 2 + 1);

        store.append(List.of(new QueueAppend("m1", msgs(2))));

        assertThat(store.read("m1", 0, 100)).extracting(StoredDeviceMsg::packetId)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 11, 12);
        assertThat(store.expiredEntries()).isEqualTo(2);
    }

    @Test
    void aMovedEntryCountsTowardTheMembersMessageExpiryBound() {
        store.append(List.of(new QueueAppend("m1", msgs(1))));
        store.append(List.of(new QueueAppend("ss_g_t", List.of(msg(clock.millis(), 5)))));
        store.moveQueue("ss_g_t", "m1", b -> b);
        clock.advanceSeconds(6);

        assertThat(store.sweepExpired()).isOne();
        assertThat(store.read("m1", 0, 100)).extracting(StoredDeviceMsg::packetId).containsExactly(1);
    }

    @Test
    void mqtt_3_3_2_5_aSentGroupEntryWhoseIntervalHasPassedIsNotMovedToAnotherMember() {
        store.append(List.of(new QueueAppend("ss_g_t", List.of(msg(clock.millis(), 5), msg(clock.millis(), null)))));
        store.readForDelivery("ss_g_t", 0, 2);
        clock.advanceSeconds(6);

        assertThat(store.moveQueue("ss_g_t", "m1", b -> b)).isOne();

        assertThat(store.read("m1", 0, 100)).extracting(StoredDeviceMsg::packetId, StoredDeviceMsg::msgExpiresAtMs)
                .containsExactly(tuple(1, 0L));
        assertThat(store.expiredEntries()).isOne();
    }

    @Test
    void theMembersLimitAppliesToTheMovedEntries() {                     // limit 10
        store.append(List.of(new QueueAppend("m1", msgs(8)), new QueueAppend("ss_g_t", msgs(5))));

        assertThat(store.moveQueue("ss_g_t", "m1", b -> b)).isEqualTo(5);

        assertThat(store.read("m1", 0, 100)).extracting(StoredDeviceMsg::packetId).containsExactly(4, 5, 6, 7, 8, 9, 10, 11, 12, 13);
        assertThat(store.info("m1").count()).isEqualTo(10);
        assertThat(store.info("m1").firstSequence()).isEqualTo(4);
    }

    @Test
    void anEmptyOrEntirelyExpiredGroupMovesNothing() {
        assertThat(store.moveQueue("unknown", "m1", b -> b)).isZero();

        store.append(List.of(new QueueAppend("ss_g_t", List.of(msg(clock.millis(), 1), msg(clock.millis(), 1)))));
        clock.advanceSeconds(2);
        assertThat(store.moveQueue("ss_g_t", "m1", b -> b)).isZero();

        assertThat(store.info("ss_g_t").count()).isZero();
        assertThat(store.info("ss_g_t").firstSequence()).isEqualTo(3);
        assertThat(store.expiredEntries()).isEqualTo(2);
        assertThat(store.info("m1").count()).isZero();
        assertThat(store.info("m1").lastPacketId()).isZero();
    }

    @Test
    void aFailingBodyMapperLeavesBothQueuesUntouched() {
        store.append(List.of(new QueueAppend("m1", msgs(1)), new QueueAppend("ss_g_t", msgs(2))));

        assertThatThrownBy(() -> store.moveQueue("ss_g_t", "m1", b -> {
            throw new IllegalStateException("mapper");
        })).hasMessage("mapper");

        assertThat(store.read("ss_g_t", 0, 100)).extracting(StoredDeviceMsg::packetId).containsExactly(1, 2);
        assertThat(store.read("m1", 0, 100)).extracting(StoredDeviceMsg::packetId).containsExactly(1);
    }

    private RocksDbDeviceMsgQueueStore storeWithLimit(int limit) {
        return new RocksDbDeviceMsgQueueStore(rocksDb, limit, TTL_SECONDS, clock);
    }

    private NewDeviceMsg msg(long createdTimeMs, Integer msgExpiryIntervalSec) {
        return new NewDeviceMsg(bytes("m"), createdTimeMs, msgExpiryIntervalSec);
    }

    private NewDeviceMsg msg(String body) {
        return new NewDeviceMsg(bytes(body), clock.millis(), null);
    }

    private List<NewDeviceMsg> msgs(int n) {
        return IntStream.range(0, n).mapToObj(i -> msg("m" + i)).toList();
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

}
