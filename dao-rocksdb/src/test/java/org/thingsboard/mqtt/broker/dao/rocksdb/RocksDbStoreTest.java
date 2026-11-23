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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import org.slf4j.LoggerFactory;
import org.thingsboard.mqtt.broker.dao.rocksdb.disk.TestDiskSpaceMonitors;
import org.thingsboard.mqtt.broker.dao.rocksdb.migration.MigrationContext;
import org.thingsboard.mqtt.broker.dao.rocksdb.migration.Schema1To2Step;
import org.thingsboard.mqtt.broker.dao.rocksdb.migration.StoreMigrationStep;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

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

        assertThat(open(dir).meta()).isEqualTo(new StoreMeta(2, "LITE", id));
    }

    @Test
    void everyColumnFamilyExistsAfterOpen() {
        RocksDbStore store = open(dir);

        assertThat(StoreColumnFamily.values()).hasSize(13);
        for (StoreColumnFamily cf : StoreColumnFamily.values()) {
            byte[] key = bytes("key-" + cf.name());
            store.put(StoreWriteClass.NEW_DATA, cf, key, bytes("value-" + cf.name()));
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
                "retained_msg", "blocked_client", "client_session_attr", "device_msg", "device_msg_meta",
                "entity", "entity_index", "session_ctx", "ts", "ts_latest");
    }

    @Test
    void aStoreCreatedWithOnlyTheEightPhaseTwoFamiliesOpensAndGainsTheNewOnes() throws RocksDBException {
        List<String> phaseTwo = List.of("meta", "client_session", "client_subscriptions", "retained_msg", "blocked_client",
                "client_session_attr", "device_msg", "device_msg_meta");
        Path path = dir.resolve("store");
        createWithFamilies(path, phaseTwo);

        RocksDbStore store = openMigrating(path, List.of(new Schema1To2Step()));

        for (StoreColumnFamily cf : List.of(StoreColumnFamily.ENTITY, StoreColumnFamily.ENTITY_INDEX,
                StoreColumnFamily.SESSION_CTX, StoreColumnFamily.TS, StoreColumnFamily.TS_LATEST)) {
            assertThat(store.get(cf, bytes("k"))).isNull();
            store.put(StoreWriteClass.NEW_DATA, cf, bytes("k"), bytes("v-" + cf.name()));
            assertThat(store.get(cf, bytes("k"))).isEqualTo(bytes("v-" + cf.name()));
        }
        assertThat(store.meta().schemaVersion()).isEqualTo(2);
    }

    @Test
    void aFailedMigrationStepStopsTheOpenLeavesTheVersionAndReleasesTheStore() throws RocksDBException {
        Path path = dir.resolve("store");
        createWithFamilies(path, List.of("meta"));
        StoreMigrationStep failing = new StoreMigrationStep() {
            @Override
            public int fromVersion() {
                return 1;
            }

            @Override
            public void apply(MigrationContext ctx) {
                ctx.put(StoreColumnFamily.ENTITY, bytes("written"), bytes("by the step"));
                throw new IllegalStateException("boom");
            }
        };

        assertThatThrownBy(() -> openMigrating(path, List.of(failing)))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("Migration from schema 1 to 2 failed: boom");
        byte[][] storedVersion = new byte[1][];
        withRawMeta(path, (db, meta) -> storedVersion[0] = db.get(meta, RocksDbStore.META_SCHEMA_VERSION_KEY));
        assertThat(storedVersion[0]).isEqualTo(new byte[]{0, 0, 0, 1});

        RocksDbStore store = openMigrating(path, List.of(new Schema1To2Step()));
        assertThat(store.meta().schemaVersion()).isEqualTo(2);
        // the step's write was synced before it failed
        assertThat(store.get(StoreColumnFamily.ENTITY, bytes("written"))).isEqualTo(bytes("by the step"));
    }

    @Test
    void aStoreThatNeedsAMigrationWithoutABackupsPathIsRefusedNamingTheSetting() throws RocksDBException {
        createWithFamilies(dir, List.of("meta"));
        StoreProperties properties = new StoreProperties();
        properties.setPath(dir.toString());
        RocksDbStore store = new RocksDbStore(properties, StoreMemoryBudget.forContainerMemory(1L << 30, 0, 0),
                TestDiskSpaceMonitors.roomy(properties), List.of(new Schema1To2Step()));

        assertThatThrownBy(store::open)
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("store.backups.path")
                .hasMessageContaining(dir.toString());
    }

    private RocksDbStore openMigrating(Path path, List<StoreMigrationStep> steps) {
        StoreProperties properties = new StoreProperties();
        properties.setPath(path.toString());
        properties.getBackups().setPath(dir.resolve("backups").toString());
        RocksDbStore store = new RocksDbStore(properties, StoreMemoryBudget.forContainerMemory(1L << 30, 0, 0),
                TestDiskSpaceMonitors.roomy(properties), steps);
        opened.add(store);
        store.open();
        return store;
    }

    private static void createWithFamilies(Path path, List<String> families) throws RocksDBException {
        List<ColumnFamilyDescriptor> descriptors = new ArrayList<>();
        descriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY));
        families.forEach(name -> descriptors.add(new ColumnFamilyDescriptor(name.getBytes(StandardCharsets.UTF_8))));
        List<ColumnFamilyHandle> handles = new ArrayList<>();
        try (DBOptions options = new DBOptions().setCreateIfMissing(true).setCreateMissingColumnFamilies(true);
             RocksDB db = RocksDB.open(options, path.toString(), descriptors, handles)) {
            // the meta of a store that Phase 2 had opened once
            db.put(handles.get(1), RocksDbStore.META_SCHEMA_VERSION_KEY, new byte[]{0, 0, 0, 1});
            db.put(handles.get(1), RocksDbStore.META_PRODUCT_KEY, bytes("LITE"));
            db.put(handles.get(1), RocksDbStore.META_INSTALL_ID_KEY, bytes(UUID.randomUUID().toString()));
            handles.forEach(ColumnFamilyHandle::close);
        }
    }

    @Test
    void aSecondStoreOnTheSamePathFailsWithAMessageThatNamesThePath() {
        RocksDbStore first = open(dir);

        assertThatThrownBy(() -> open(dir))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining(dir.toString());

        first.put(StoreWriteClass.NEW_DATA, StoreColumnFamily.RETAINED_MSG, bytes("k"), bytes("v"));
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
        Path path = dir.resolve("store");
        RocksDbStore store = openMigrating(path, List.of(new Schema1To2Step()));
        store.put(StoreWriteClass.UPDATE, StoreColumnFamily.META, RocksDbStore.META_PRODUCT_KEY, bytes("X"));
        // an older schema too: the refusal comes before any migration
        store.put(StoreWriteClass.UPDATE, StoreColumnFamily.META, RocksDbStore.META_SCHEMA_VERSION_KEY, new byte[]{0, 0, 0, 1});
        closeAll();

        assertThatThrownBy(() -> openMigrating(path, List.of(new Schema1To2Step())))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("product 'X'")
                .hasMessageContaining(path.toString());
        assertThat(dir.resolve("backups")).doesNotExist();
    }

    @Test
    void aStoreWithAHigherSchemaVersionIsRefused() {
        Path path = dir.resolve("store");
        openMigrating(path, List.of(new Schema1To2Step()))
                .put(StoreWriteClass.UPDATE, StoreColumnFamily.META, RocksDbStore.META_SCHEMA_VERSION_KEY, new byte[]{0, 0, 0, 3});
        closeAll();

        assertThatThrownBy(() -> openMigrating(path, List.of(new Schema1To2Step())))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("schema version 3")
                .hasMessageContaining(path.toString());
        assertThat(dir.resolve("backups")).doesNotExist();
    }

    @Test
    void aRefusedOpenReleasesTheLock() {
        open(dir).put(StoreWriteClass.UPDATE, StoreColumnFamily.META, RocksDbStore.META_PRODUCT_KEY, bytes("X"));
        closeAll();
        assertThatThrownBy(() -> open(dir)).hasMessageContaining("product 'X'");

        // a second refusal for the same reason, not "lock held by the first attempt"
        assertThatThrownBy(() -> open(dir)).hasMessageContaining("product 'X'");
    }

    @Test
    void writesAreReadableAfterCloseAndReopen() {
        RocksDbStore store = open(dir);
        store.put(StoreWriteClass.NEW_DATA, StoreColumnFamily.CLIENT_SESSION, bytes("c1"), bytes("session"));
        closeAll();

        assertThat(open(dir).get(StoreColumnFamily.CLIENT_SESSION, bytes("c1"))).isEqualTo(bytes("session"));
    }

    @Test
    void deleteRemovesTheKey() {
        RocksDbStore store = open(dir);
        store.put(StoreWriteClass.NEW_DATA, StoreColumnFamily.BLOCKED_CLIENT, bytes("a"), bytes("1"));

        store.delete(StoreWriteClass.DELETE, StoreColumnFamily.BLOCKED_CLIENT, bytes("a"));

        assertThat(store.get(StoreColumnFamily.BLOCKED_CLIENT, bytes("a"))).isNull();
    }

    @Test
    void aWriteBatchAppliesPutsDeletesAndRangeDeletesTogether() {
        RocksDbStore store = open(dir);
        store.put(StoreWriteClass.NEW_DATA, StoreColumnFamily.BLOCKED_CLIENT, bytes("a"), bytes("1"));
        for (String k : List.of("k1", "k2", "k3", "k4")) {
            store.put(StoreWriteClass.NEW_DATA, StoreColumnFamily.RETAINED_MSG, bytes(k), bytes("v"));
        }

        store.write(StoreWriteClass.NEW_DATA, batch -> {
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

        assertThatThrownBy(() -> store.write(StoreWriteClass.NEW_DATA, batch -> {
            batch.put(StoreColumnFamily.BLOCKED_CLIENT, bytes("a"), bytes("1"));
            throw new IllegalArgumentException("boom");
        })).isInstanceOf(IllegalArgumentException.class).hasMessage("boom");

        assertThat(store.get(StoreColumnFamily.BLOCKED_CLIENT, bytes("a"))).isNull();
    }

    @Test
    void aBatchKeptAfterTheWriteEndsCannotBeUsed() {
        RocksDbStore store = open(dir);
        List<StoreBatch> kept = new ArrayList<>();
        store.write(StoreWriteClass.NEW_DATA, kept::add);

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
            store.put(StoreWriteClass.NEW_DATA, StoreColumnFamily.RETAINED_MSG, bytes(k), bytes("v"));
        }
        store.put(StoreWriteClass.NEW_DATA, StoreColumnFamily.CLIENT_SESSION, bytes("z"), bytes("3"));

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
                store.put(StoreWriteClass.NEW_DATA, StoreColumnFamily.DEVICE_MSG, deviceKey(prefix, seq), bytes("p" + prefix + "s" + seq));
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
        store.put(StoreWriteClass.NEW_DATA, StoreColumnFamily.RETAINED_MSG, bytes("a"), bytes("1"));

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
            store.put(StoreWriteClass.NEW_DATA, StoreColumnFamily.CLIENT_SESSION, bytes(String.format("key-%04d", i)), value);
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
        open(dir).put(StoreWriteClass.UPDATE, StoreColumnFamily.META, RocksDbStore.META_SCHEMA_VERSION_KEY, new byte[]{0, 0, 0, 3});
        closeAll();
        addColumnFamily(dir, "future_cf");

        assertThatThrownBy(() -> open(dir))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("schema version 3")
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
        assertThatThrownBy(() -> store.put(StoreWriteClass.NEW_DATA, StoreColumnFamily.META, bytes("k"), bytes("v"))).isInstanceOf(StoreException.class);
        assertThatThrownBy(() -> store.delete(StoreWriteClass.DELETE, StoreColumnFamily.META, bytes("k"))).isInstanceOf(StoreException.class);
        assertThatThrownBy(() -> store.write(StoreWriteClass.NEW_DATA, batch -> batch.put(StoreColumnFamily.META, bytes("k"), bytes("v"))))
                .isInstanceOf(StoreException.class).hasMessageContaining("write a batch");
        assertThatThrownBy(() -> store.scan(StoreColumnFamily.META, null, null, it -> null))
                .isInstanceOf(StoreException.class).hasMessageContaining("scan meta");
        assertThatThrownBy(store::syncWal).isInstanceOf(StoreException.class);
        assertThatThrownBy(store::meta).isInstanceOf(StoreException.class);
        assertThatThrownBy(() -> store.putMetaSynced(bytes("k"), bytes("v")))
                .isInstanceOf(StoreException.class).hasMessageContaining("put to meta");
    }

    @Test
    void aSyncedMetaPutIsReadableAfterCloseAndReopen() {
        RocksDbStore store = open(dir);
        store.putMetaSynced(bytes("marker"), new byte[]{1});
        closeAll();

        assertThat(open(dir).get(StoreColumnFamily.META, bytes("marker"))).isEqualTo(new byte[]{1});
    }

    @Test
    void aNewStoreGetsA64ByteKeyThatSurvivesReopen() {
        byte[] key = open(dir).get(StoreColumnFamily.META, bytes("jwt_signing_key"));

        // the meta key name is on-disk schema: a rename gives every existing install a new key
        assertThat(key).hasSize(64);
        assertThat(key).isNotEqualTo(new byte[64]);
        closeAll();

        assertThat(open(dir).get(StoreColumnFamily.META, bytes("jwt_signing_key"))).isEqualTo(key);
    }

    @Test
    void twoNewStoresGetDifferentKeys(@TempDir Path other) {
        byte[] first = open(dir).get(StoreColumnFamily.META, bytes("jwt_signing_key"));
        byte[] second = open(other).get(StoreColumnFamily.META, bytes("jwt_signing_key"));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void aStoreWithoutAKeyGetsOneOnOpen() throws RocksDBException {
        open(dir);
        closeAll();
        deleteMetaKey(dir, bytes("jwt_signing_key"));

        RocksDbStore store = open(dir);

        assertThat(store.get(StoreColumnFamily.META, bytes("jwt_signing_key"))).hasSize(64);
    }

    @Test
    void anExistingKeyIsNeverReplacedOnOpen() throws RocksDBException {
        open(dir);
        closeAll();
        byte[] chosen = new byte[]{1, 2, 3};
        putMetaKey(dir, bytes("jwt_signing_key"), chosen);

        assertThat(open(dir).get(StoreColumnFamily.META, bytes("jwt_signing_key"))).isEqualTo(chosen);
    }

    private static void deleteMetaKey(Path path, byte[] key) throws RocksDBException {
        withRawMeta(path, (db, meta) -> db.delete(meta, key));
    }

    private static void putMetaKey(Path path, byte[] key, byte[] value) throws RocksDBException {
        withRawMeta(path, (db, meta) -> db.put(meta, key, value));
    }

    private interface RawMetaAction {
        void run(RocksDB db, ColumnFamilyHandle meta) throws RocksDBException;
    }

    private static void withRawMeta(Path path, RawMetaAction action) throws RocksDBException {
        try (DBOptions dbOptions = new DBOptions(); ColumnFamilyOptions options = new ColumnFamilyOptions()) {
            List<ColumnFamilyDescriptor> descriptors = new ArrayList<>();
            descriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, options));
            for (StoreColumnFamily cf : StoreColumnFamily.values()) {
                descriptors.add(new ColumnFamilyDescriptor(bytes(cf.columnFamilyName()), options));
            }
            List<ColumnFamilyHandle> handles = new ArrayList<>();
            try (RocksDB raw = RocksDB.open(dbOptions, path.toString(), descriptors, handles)) {
                action.run(raw, handles.get(StoreColumnFamily.META.ordinal() + 1));
                handles.forEach(ColumnFamilyHandle::close);
            }
        }
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
                            store.put(StoreWriteClass.NEW_DATA, StoreColumnFamily.CLIENT_SESSION, key, bytes("v" + i));
                            store.get(StoreColumnFamily.CLIENT_SESSION, key);
                            store.write(StoreWriteClass.NEW_DATA, batch -> {
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
                            store.delete(StoreWriteClass.DELETE, StoreColumnFamily.CLIENT_SESSION, key);
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

    @Test
    void closeLogsTheDurationAtWarnWhenTheFinalSyncAndCloseTakeOverASecond() {
        AtomicLong nanos = new AtomicLong();
        RocksDbStore store = openWithClock(dir, () -> nanos.getAndAdd(TimeUnit.MILLISECONDS.toNanos(1500)));
        ListAppender<ILoggingEvent> logged = captureStoreLog();
        try {
            store.close();
            store.close();
        } finally {
            releaseStoreLog(logged);
        }

        assertThat(logged.list).filteredOn(event -> event.getLevel() == Level.WARN)
                .singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage()).contains("1500 ms").contains(dir.toString()));
    }

    @Test
    void closeStaysSilentWhenTheFinalSyncAndCloseTakeUnderASecond() {
        AtomicLong nanos = new AtomicLong();
        RocksDbStore store = openWithClock(dir, () -> nanos.getAndAdd(TimeUnit.MILLISECONDS.toNanos(999)));
        ListAppender<ILoggingEvent> logged = captureStoreLog();
        try {
            store.close();
        } finally {
            releaseStoreLog(logged);
        }

        assertThat(logged.list).filteredOn(event -> event.getLevel().isGreaterOrEqual(Level.WARN)).isEmpty();
    }

    private ListAppender<ILoggingEvent> captureStoreLog() {
        Logger logger = (Logger) LoggerFactory.getLogger(RocksDbStore.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setAdditive(false);
        return appender;
    }

    private void releaseStoreLog(ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger(RocksDbStore.class);
        logger.detachAppender(appender);
        logger.setAdditive(true);
    }

    private RocksDbStore openWithClock(Path path, LongSupplier nanoClock) {
        StoreProperties properties = new StoreProperties();
        properties.setPath(path.toString());
        properties.setWalSync("periodic");
        RocksDbStore store = new RocksDbStore(properties, StoreMemoryBudget.forContainerMemory(1L << 30, 0, 0), TestDiskSpaceMonitors.roomy(properties), List.of(), nanoClock);
        opened.add(store);
        store.open();
        return store;
    }

    @Test
    void aNonPositiveBackupsKeepFailsTheOpenNamingTheSetting() {
        for (int keep : new int[]{0, -1}) {
            StoreProperties properties = new StoreProperties();
            properties.setPath(dir.toString());
            properties.getBackups().setKeep(keep);
            RocksDbStore store = new RocksDbStore(properties, StoreMemoryBudget.forContainerMemory(1L << 30, 0, 0),
                    TestDiskSpaceMonitors.roomy(properties), List.of());

            assertThatThrownBy(store::open)
                    .isInstanceOf(StoreException.class)
                    .hasMessageContaining("store.backups.keep");
        }
    }

    private RocksDbStore open(Path path) {
        return open(path, "periodic");
    }

    private RocksDbStore open(Path path, String walSync) {
        StoreProperties properties = new StoreProperties();
        properties.setPath(path.toString());
        properties.setWalSync(walSync);
        RocksDbStore store = new RocksDbStore(properties, StoreMemoryBudget.forContainerMemory(1L << 30, 0, 0), TestDiskSpaceMonitors.roomy(properties), List.of());
        opened.add(store);
        store.open();
        return store;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

}
