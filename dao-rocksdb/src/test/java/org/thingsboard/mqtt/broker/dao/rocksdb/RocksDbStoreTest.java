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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.FlushOptions;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.thingsboard.mqtt.broker.dao.store.StoreException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class RocksDbStoreTest {

    @TempDir
    Path dir;

    private final List<RocksDbStore> opened = new ArrayList<>();

    @AfterEach
    void closeAll() {
        opened.forEach(RocksDbStore::close);
        opened.clear();
    }

    @Test
    void firstOpenWritesMetaAndASecondOpenReadsTheSameInstallId() {
        UUID id = open(dir).meta().installId();
        closeAll();

        assertThat(open(dir).meta()).isEqualTo(new StoreMeta(1, "LITE", id));
    }

    @Test
    void allEightColumnFamiliesExistAfterOpen() {
        RocksDbStore store = open(dir);

        assertThat(StoreColumnFamily.values()).hasSize(8);
        for (StoreColumnFamily cf : StoreColumnFamily.values()) {
            byte[] key = bytes("key-" + cf.name());
            store.put(cf, key, bytes("value-" + cf.name()));
            assertThat(store.get(cf, key)).isEqualTo(bytes("value-" + cf.name()));
        }
    }

    @Test
    void theOnDiskColumnFamilyNamesArePinned() throws RocksDBException {
        open(dir).close();

        List<String> names;
        try (Options options = new Options()) {
            names = RocksDB.listColumnFamilies(options, dir.toString()).stream()
                    .map(name -> new String(name, StandardCharsets.UTF_8)).toList();
        }
        // these names are the on-disk schema: changing one orphans its data
        assertThat(names).containsExactlyInAnyOrder("default", "meta", "client_session", "client_subscriptions",
                "retained_msg", "blocked_client", "client_session_attr", "device_msg", "device_msg_meta");
    }

    @Test
    void aSecondStoreOnTheSamePathFailsWithAMessageThatNamesThePath() {
        RocksDbStore first = open(dir);

        assertThatThrownBy(() -> open(dir))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining(dir.toString());

        first.put(StoreColumnFamily.RETAINED_MSG, bytes("k"), bytes("v"));
        assertThat(first.get(StoreColumnFamily.RETAINED_MSG, bytes("k"))).isEqualTo(bytes("v"));
    }

    @Test
    void aPathWithoutWritePermissionFailsWithAMessageThatNamesThePath() throws Exception {
        assertThat(dir.toFile().setWritable(false)).isTrue();
        try {
            assumeFalse(Files.isWritable(dir), "running as a user that ignores directory permissions");

            assertThatThrownBy(() -> open(dir.resolve("db")))
                    .isInstanceOf(StoreException.class)
                    .hasMessageContaining(dir.toString());
        } finally {
            dir.toFile().setWritable(true);
        }
    }

    @Test
    void aStoreOfAnotherProductIsRefused() {
        open(dir).put(StoreColumnFamily.META, RocksDbStore.META_PRODUCT_KEY, bytes("X"));
        closeAll();

        assertThatThrownBy(() -> open(dir))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("product 'X'")
                .hasMessageContaining(dir.toString());
    }

    @Test
    void aStoreWithAHigherSchemaVersionIsRefused() {
        open(dir).put(StoreColumnFamily.META, RocksDbStore.META_SCHEMA_VERSION_KEY, new byte[]{0, 0, 0, 2});
        closeAll();

        assertThatThrownBy(() -> open(dir))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("schema version 2")
                .hasMessageContaining(dir.toString());
    }

    @Test
    void aRefusedOpenReleasesTheLock() {
        open(dir).put(StoreColumnFamily.META, RocksDbStore.META_PRODUCT_KEY, bytes("X"));
        closeAll();
        assertThatThrownBy(() -> open(dir)).hasMessageContaining("product 'X'");

        // a second refusal for the same reason, not "lock held by the first attempt"
        assertThatThrownBy(() -> open(dir)).hasMessageContaining("product 'X'");
    }

    @Test
    void writesAreReadableAfterCloseAndReopen() {
        RocksDbStore store = open(dir);
        store.put(StoreColumnFamily.CLIENT_SESSION, bytes("c1"), bytes("session"));
        closeAll();

        assertThat(open(dir).get(StoreColumnFamily.CLIENT_SESSION, bytes("c1"))).isEqualTo(bytes("session"));
    }

    @Test
    void deleteRemovesTheKey() {
        RocksDbStore store = open(dir);
        store.put(StoreColumnFamily.BLOCKED_CLIENT, bytes("a"), bytes("1"));

        store.delete(StoreColumnFamily.BLOCKED_CLIENT, bytes("a"));

        assertThat(store.get(StoreColumnFamily.BLOCKED_CLIENT, bytes("a"))).isNull();
    }

    @Test
    void aWriteBatchAppliesPutsDeletesAndRangeDeletesTogether() {
        RocksDbStore store = open(dir);
        store.put(StoreColumnFamily.BLOCKED_CLIENT, bytes("a"), bytes("1"));
        for (String k : List.of("k1", "k2", "k3", "k4")) {
            store.put(StoreColumnFamily.RETAINED_MSG, bytes(k), bytes("v"));
        }

        store.write(batch -> {
            batch.put(StoreColumnFamily.BLOCKED_CLIENT, bytes("b"), bytes("2"));
            batch.delete(StoreColumnFamily.BLOCKED_CLIENT, bytes("a"));
            batch.deleteRange(StoreColumnFamily.RETAINED_MSG, bytes("k2"), bytes("k4"));
        });

        assertThat(store.get(StoreColumnFamily.BLOCKED_CLIENT, bytes("a"))).isNull();
        assertThat(store.get(StoreColumnFamily.BLOCKED_CLIENT, bytes("b"))).isEqualTo(bytes("2"));
        assertThat(keys(store, StoreColumnFamily.RETAINED_MSG, null, null)).containsExactly("k1", "k4");
    }

    @Test
    void aBodyThatThrowsWritesNothing() {
        RocksDbStore store = open(dir);

        assertThatThrownBy(() -> store.write(batch -> {
            batch.put(StoreColumnFamily.BLOCKED_CLIENT, bytes("a"), bytes("1"));
            throw new IllegalArgumentException("boom");
        })).isInstanceOf(IllegalArgumentException.class).hasMessage("boom");

        assertThat(store.get(StoreColumnFamily.BLOCKED_CLIENT, bytes("a"))).isNull();
    }

    @Test
    void aBatchKeptAfterTheWriteEndsCannotBeUsed() {
        RocksDbStore store = open(dir);
        List<StoreBatch> kept = new ArrayList<>();
        store.write(kept::add);

        StoreBatch batch = kept.get(0);
        assertThatThrownBy(() -> batch.put(StoreColumnFamily.META, bytes("k"), bytes("v")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> batch.delete(StoreColumnFamily.META, bytes("k")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> batch.deleteRange(StoreColumnFamily.META, bytes("a"), bytes("b")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aScanWalksOneColumnFamilyInKeyOrderBetweenItsBounds() {
        RocksDbStore store = open(dir);
        for (String k : List.of("b", "a", "d", "c")) {
            store.put(StoreColumnFamily.RETAINED_MSG, bytes(k), bytes("v"));
        }
        store.put(StoreColumnFamily.CLIENT_SESSION, bytes("z"), bytes("3"));

        assertThat(keys(store, StoreColumnFamily.RETAINED_MSG, null, null)).containsExactly("a", "b", "c", "d");
        assertThat(keys(store, StoreColumnFamily.RETAINED_MSG, bytes("b"), bytes("d"))).containsExactly("b", "c");
        assertThat(keys(store, StoreColumnFamily.RETAINED_MSG, bytes("b"), null)).containsExactly("b", "c", "d");
        assertThat(keys(store, StoreColumnFamily.RETAINED_MSG, null, bytes("c"))).containsExactly("a", "b");
        assertThat(keys(store, StoreColumnFamily.RETAINED_MSG, bytes("e"), null)).isEmpty();
    }

    @Test
    void aDeviceMsgScanReadsOneQueueAndAlsoARangeThatCrossesQueuePrefixes() {
        RocksDbStore store = open(dir);
        for (long prefix : new long[]{1, 2}) {
            for (int seq = 1; seq <= 3; seq++) {
                store.put(StoreColumnFamily.DEVICE_MSG, deviceKey(prefix, seq), bytes("p" + prefix + "s" + seq));
            }
        }

        // both bounds in the prefix of one queue
        assertThat(values(store, deviceKey(1, 2), deviceKey(1, 4))).containsExactly("p1s2", "p1s3");
        assertThat(values(store, deviceKey(2, 0), deviceKey(2, 9))).containsExactly("p2s1", "p2s2", "p2s3");
        // bounds in two different prefixes
        assertThat(values(store, deviceKey(1, 3), deviceKey(2, 3))).containsExactly("p1s3", "p2s1", "p2s2");
        assertThat(values(store, null, null)).hasSize(6);
        assertThat(values(store, deviceKey(1, 3), null)).containsExactly("p1s3", "p2s1", "p2s2", "p2s3");
    }

    @Test
    void syncWalSucceedsOnAnOpenStore() {
        RocksDbStore store = open(dir);
        store.put(StoreColumnFamily.RETAINED_MSG, bytes("a"), bytes("1"));

        assertThatCode(store::syncWal).doesNotThrowAnyException();
    }

    @Test
    void alwaysModeWritesWithSyncAndPeriodicModeDoesNot() {
        assertThat(open(dir.resolve("always"), "always").syncsEveryWrite()).isTrue();
        assertThat(open(dir.resolve("periodic"), "periodic").syncsEveryWrite()).isFalse();
    }

    @Test
    void anUnknownWalSyncModeFailsTheOpenAndListsTheValidValues() {
        assertThatThrownBy(() -> open(dir, "sometimes"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("periodic")
                .hasMessageContaining("always")
                .satisfies(e -> {
                    for (WalSyncMode mode : WalSyncMode.values()) {
                        assertThat(e).hasMessageContaining("'" + mode.value() + "'");
                    }
                });
    }

    @Test
    void aScanThatHitsACorruptBlockFailsInsteadOfEndingEarly() throws Exception {
        RocksDbStore store = open(dir);
        Random random = new Random(42);
        for (int i = 0; i < 1000; i++) {
            byte[] value = new byte[1024];
            random.nextBytes(value);
            store.put(StoreColumnFamily.CLIENT_SESSION, bytes(String.format("key-%04d", i)), value);
        }
        store.close();
        flushAll(dir);
        Path sst;
        try (var files = Files.list(dir)) {
            sst = files.filter(f -> f.toString().endsWith(".sst")).max(Comparator.comparingLong(RocksDbStoreTest::size))
                    .orElseThrow();
        }
        try (FileChannel channel = FileChannel.open(sst, StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(new byte[64]), 128); // inside the first data block
        }

        RocksDbStore reopened = open(dir);

        assertThatThrownBy(() -> keys(reopened, StoreColumnFamily.CLIENT_SESSION, null, null))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("scan client_session");
    }

    @Test
    void aStoreWithAnUnknownColumnFamilyAndALaterSchemaIsRefusedWithTheSchemaMessage() throws RocksDBException {
        open(dir).put(StoreColumnFamily.META, RocksDbStore.META_SCHEMA_VERSION_KEY, new byte[]{0, 0, 0, 2});
        closeAll();
        addColumnFamily(dir, "future_cf");

        assertThatThrownBy(() -> open(dir))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("schema version 2")
                .hasMessageContaining(dir.toString());
    }

    @Test
    void aStoreWithAnUnknownColumnFamilyOpensAndClosesCleanlyWhenTheSchemaMatches() throws RocksDBException {
        UUID id = open(dir).meta().installId();
        closeAll();
        addColumnFamily(dir, "future_cf");

        RocksDbStore store = open(dir);

        assertThat(store.meta().installId()).isEqualTo(id);
    }

    @Test
    void closeIsIdempotentAndAClosedStoreRefusesEveryOperation() {
        RocksDbStore store = open(dir);

        store.close();

        assertThatCode(store::close).doesNotThrowAnyException();
        assertThatThrownBy(() -> store.get(StoreColumnFamily.META, bytes("k")))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("get from meta")
                .hasMessageContaining(dir.toString());
        assertThatThrownBy(() -> store.put(StoreColumnFamily.META, bytes("k"), bytes("v"))).isInstanceOf(StoreException.class);
        assertThatThrownBy(() -> store.delete(StoreColumnFamily.META, bytes("k"))).isInstanceOf(StoreException.class);
        assertThatThrownBy(() -> store.write(batch -> batch.put(StoreColumnFamily.META, bytes("k"), bytes("v"))))
                .isInstanceOf(StoreException.class).hasMessageContaining("write a batch");
        assertThatThrownBy(() -> store.scan(StoreColumnFamily.META, null, null, it -> null))
                .isInstanceOf(StoreException.class).hasMessageContaining("scan meta");
        assertThatThrownBy(store::syncWal).isInstanceOf(StoreException.class);
        assertThatThrownBy(store::meta).isInstanceOf(StoreException.class);
    }

    @Test
    void closingWhileOtherThreadsReadWriteBatchAndScanOnlyEverSurfacesAStoreException() throws Exception {
        int threads = 8;
        for (int round = 0; round < 5; round++) {
            RocksDbStore store = open(dir.resolve("round-" + round));
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch working = new CountDownLatch(threads);
            AtomicInteger refused = new AtomicInteger();
            List<Future<Throwable>> results = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                int id = t;
                results.add(executor.submit(() -> {
                    working.countDown();
                    try {
                        for (long i = 0; ; i++) {
                            byte[] key = bytes("k-" + id + "-" + (i % 1000));
                            store.put(StoreColumnFamily.CLIENT_SESSION, key, bytes("v" + i));
                            store.get(StoreColumnFamily.CLIENT_SESSION, key);
                            store.write(batch -> {
                                batch.put(StoreColumnFamily.DEVICE_MSG, deviceKey(id, 1), key);
                                batch.deleteRange(StoreColumnFamily.RETAINED_MSG, bytes("a"), bytes("b"));
                            });
                            store.scan(StoreColumnFamily.DEVICE_MSG, null, null, it -> {
                                int n = 0;
                                while (it.isValid() && n++ < 10) {
                                    it.key();
                                    it.next();
                                }
                                return n;
                            });
                            store.delete(StoreColumnFamily.CLIENT_SESSION, key);
                        }
                    } catch (StoreException e) {
                        refused.incrementAndGet();
                        return null;
                    } catch (Throwable e) {
                        return e;
                    }
                }));
            }
            assertThat(working.await(10, TimeUnit.SECONDS)).isTrue();
            TimeUnit.MILLISECONDS.sleep(50);

            store.close();

            for (Future<Throwable> result : results) {
                assertThat(result.get(10, TimeUnit.SECONDS)).isNull();
            }
            assertThat(refused.get()).isEqualTo(threads);
            executor.shutdown();
        }
    }

    private static List<String> keys(RocksDbStore store, StoreColumnFamily cf, byte[] lower, byte[] upper) {
        return store.scan(cf, lower, upper, it -> {
            List<String> keys = new ArrayList<>();
            for (; it.isValid(); it.next()) {
                keys.add(new String(it.key(), StandardCharsets.UTF_8));
            }
            return keys;
        });
    }

    private static List<String> values(RocksDbStore store, byte[] lower, byte[] upper) {
        return store.scan(StoreColumnFamily.DEVICE_MSG, lower, upper, it -> {
            List<String> values = new ArrayList<>();
            for (; it.isValid(); it.next()) {
                values.add(new String(it.value(), StandardCharsets.UTF_8));
            }
            return values;
        });
    }

    /**
     * A device_msg-shaped key: an 8-byte queue prefix, then a 4-byte sequence.
     */
    private static byte[] deviceKey(long prefix, int seq) {
        return ByteBuffer.allocate(12).putLong(prefix).putInt(seq).array();
    }

    private static long size(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Writes every memtable of the store at {@code path} to SST files, through a raw RocksDB handle.
     */
    private static void flushAll(Path path) throws RocksDBException {
        try (DBOptions dbOptions = new DBOptions(); ColumnFamilyOptions options = new ColumnFamilyOptions();
             FlushOptions flushOptions = new FlushOptions().setWaitForFlush(true)) {
            List<ColumnFamilyDescriptor> descriptors = new ArrayList<>();
            descriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, options));
            for (StoreColumnFamily cf : StoreColumnFamily.values()) {
                descriptors.add(new ColumnFamilyDescriptor(bytes(cf.columnFamilyName()), options));
            }
            List<ColumnFamilyHandle> handles = new ArrayList<>();
            try (RocksDB raw = RocksDB.open(dbOptions, path.toString(), descriptors, handles)) {
                raw.flush(flushOptions, handles);
                handles.forEach(ColumnFamilyHandle::close);
            }
        }
    }

    private static void addColumnFamily(Path path, String name) throws RocksDBException {
        try (DBOptions dbOptions = new DBOptions(); ColumnFamilyOptions options = new ColumnFamilyOptions()) {
            List<ColumnFamilyDescriptor> descriptors = new ArrayList<>();
            descriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, options));
            for (StoreColumnFamily cf : StoreColumnFamily.values()) {
                descriptors.add(new ColumnFamilyDescriptor(bytes(cf.columnFamilyName()), options));
            }
            List<ColumnFamilyHandle> handles = new ArrayList<>();
            try (RocksDB raw = RocksDB.open(dbOptions, path.toString(), descriptors, handles)) {
                handles.add(raw.createColumnFamily(new ColumnFamilyDescriptor(bytes(name), options)));
                handles.forEach(ColumnFamilyHandle::close);
            }
        }
    }

    private RocksDbStore open(Path path) {
        return open(path, "periodic");
    }

    private RocksDbStore open(Path path, String walSync) {
        StoreProperties properties = new StoreProperties();
        properties.setPath(path.toString());
        properties.setWalSync(walSync);
        RocksDbStore store = new RocksDbStore(properties, StoreMemoryBudget.forContainerMemory(1L << 30, 0, 0));
        opened.add(store);
        store.open();
        return store;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

}
