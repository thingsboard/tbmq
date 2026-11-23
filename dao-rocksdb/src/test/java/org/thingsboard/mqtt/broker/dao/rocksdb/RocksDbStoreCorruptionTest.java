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
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.Status;
import org.thingsboard.mqtt.broker.dao.rocksdb.backup.CheckpointInfo;
import org.thingsboard.mqtt.broker.dao.rocksdb.backup.StoreCheckpoints;
import org.thingsboard.mqtt.broker.dao.rocksdb.disk.TestDiskSpaceMonitors;
import org.thingsboard.mqtt.broker.dao.rocksdb.migration.MigrationContext;
import org.thingsboard.mqtt.broker.dao.rocksdb.migration.Schema1To2Step;
import org.thingsboard.mqtt.broker.dao.rocksdb.migration.StoreMigrationRunner;
import org.thingsboard.mqtt.broker.dao.rocksdb.migration.StoreMigrationStep;
import org.thingsboard.mqtt.broker.dao.store.StoreException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class RocksDbStoreCorruptionTest {

    @TempDir
    Path dir;

    private final List<RocksDbStore> opened = new ArrayList<>();

    @AfterEach
    void closeAll() {
        opened.forEach(RocksDbStore::close);
        opened.clear();
    }

    @Test
    void aDamagedManifestIsRefusedWithTheCheckpointsAndTheRestoreSteps() throws Exception {
        Path path = dir.resolve("store");
        RocksDbStore store = open(path, List.of());
        writeSome(store);
        Path checkpoint = StoreCheckpoints.preUpgradeDir(backups(), 1, 2, Clock.systemUTC());
        store.createCheckpoint(checkpoint);
        store.close();
        flushAll(path);
        damageManifest(path);

        Throwable thrown = catchThrowable(() -> open(path, List.of()));

        assertThat(thrown).isInstanceOf(StoreCorruptedException.class);
        // the code RocksDB reports for this damage
        assertThat(thrown.getCause()).isInstanceOfSatisfying(RocksDBException.class,
                e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.Corruption));
        String time = Files.getLastModifiedTime(checkpoint).toInstant().truncatedTo(ChronoUnit.SECONDS).toString();
        assertThat(thrown.getMessage())
                .startsWith("The store at " + path + " is corrupted: ")
                .contains("checksum mismatch. The file ")
                .contains(" may be corrupted. Pre-upgrade")
                .contains("Pre-upgrade checkpoints under " + backups() + ": pre-upgrade-1-2 (" + time + ").")
                .contains("To restore a backup: stop the broker, replace " + path + " with the backup's db directory, start the broker.")
                .contains("A pre-upgrade checkpoint can be restored the same way: stop the broker, replace " + path
                        + " with a copy of the checkpoint directory, start the broker (it upgrades again on start). The checkpoint itself stays untouched. Copying")
                .endsWith("Copying /data while the broker runs does not give a usable backup.")
                .doesNotContain("\n");
        // the checkpoint of the list is the way back
        Path restored = copyDir(checkpoint, dir.resolve("restored"));
        assertThat(open(restored, List.of()).meta().schemaVersion()).isEqualTo(2);
    }

    @Test
    void theCheckpointsAreListedNewestFirstSeparatedByCommas() throws Exception {
        Path path = dir.resolve("store");
        RocksDbStore store = open(path, List.of());
        writeSome(store);
        Path older = backups().resolve("pre-upgrade-1-2");
        Path newer = backups().resolve("pre-upgrade-2-3");
        store.createCheckpoint(older);
        store.createCheckpoint(newer);
        store.close();
        flushAll(path);
        Files.setLastModifiedTime(older, FileTime.from(Instant.parse("2026-01-01T10:00:00Z")));
        Files.setLastModifiedTime(newer, FileTime.from(Instant.parse("2026-02-02T12:30:45Z")));
        damageManifest(path);

        assertThatThrownBy(() -> open(path, List.of()))
                .isInstanceOf(StoreCorruptedException.class)
                .hasMessageContaining("Pre-upgrade checkpoints under " + backups()
                        + ": pre-upgrade-2-3 (2026-02-02T12:30:45Z), pre-upgrade-1-2 (2026-01-01T10:00:00Z). To restore");
    }

    @Test
    void aDamagedStoreWithoutACheckpointSaysSo() throws Exception {
        Path path = dir.resolve("store");
        writeSome(open(path, List.of()));
        closeAll();
        flushAll(path);
        damageManifest(path);

        assertThatThrownBy(() -> open(path, List.of()))
                .isInstanceOf(StoreCorruptedException.class)
                .hasMessageContaining("No pre-upgrade checkpoint under " + backups() + ".")
                .hasMessageContaining("To restore a backup: stop the broker");
    }

    @Test
    void aDamagedStoreWithoutABackupsPathSaysItIsNotSet() throws Exception {
        Path path = dir.resolve("store");
        writeSome(open(path, List.of()));
        closeAll();
        flushAll(path);
        damageManifest(path);
        StoreProperties properties = new StoreProperties();
        properties.setPath(path.toString());

        assertThatThrownBy(() -> newStore(properties, List.of()).open())
                .isInstanceOf(StoreCorruptedException.class)
                .hasMessageContaining("store.backups.path is not set")
                .hasMessageContaining("To restore a backup: stop the broker");
    }

    @Test
    void aBackupsPathThatCannotBeListedNeverHidesTheCorruption() throws Exception {
        Path path = dir.resolve("store");
        writeSome(open(path, List.of()));
        closeAll();
        flushAll(path);
        damageManifest(path);
        // a NUL byte is not a valid path
        StoreProperties properties = new StoreProperties();
        properties.setPath(path.toString());
        properties.getBackups().setPath("bad\0path");

        assertThatThrownBy(() -> newStore(properties, List.of()).open())
                .isInstanceOf(StoreCorruptedException.class)
                .hasMessageStartingWith("The store at " + path + " is corrupted: ")
                .hasMessageContaining("could not be listed")
                .hasMessageContaining("To restore a backup: stop the broker");
    }

    @Test
    void aDamagedSstFileIsRefusedAsCorrupted() throws Exception {
        Path path = dir.resolve("store");
        writeSome(open(path, List.of()));
        closeAll();
        flushAll(path);
        damageEverySstMiddle(path);

        Throwable thrown = catchThrowable(() -> open(path, List.of()));

        assertThat(thrown).isInstanceOf(StoreCorruptedException.class);
        assertThat(thrown.getMessage()).contains("is corrupted").contains("To restore a backup");
    }

    @Test
    void aCorruptionReportedByAReadWhileMigratingIsRefusedAsCorrupted() throws Exception {
        Path path = dir.resolve("store");
        createWithFamilies(path, List.of("meta"));
        // what failure(...) makes of the RocksDBException of a read of a damaged block
        StoreException wrapped = new StoreException("Failed to get entity in the store at " + path + ": bad block",
                new RocksDBException("bad block", new Status(Status.Code.Corruption, Status.SubCode.None, "bad block")));
        StoreMigrationStep step = new StoreMigrationStep() {
            @Override
            public int fromVersion() {
                return 1;
            }

            @Override
            public void apply(MigrationContext ctx) {
                throw wrapped;
            }
        };

        Throwable thrown = catchThrowable(() -> open(path, 3, List.of(step)));

        // the runner wraps the failure of the step in its own message
        assertThat(thrown).isInstanceOf(StoreCorruptedException.class).hasMessageContaining("is corrupted");
        assertThat(thrown.getCause()).hasMessageContaining("Migration from schema 1 to 2 failed").hasCause(wrapped);
        assertThat(thrown.getMessage()).startsWith("The store at " + path + " is corrupted: bad block. ")
                .contains("Pre-upgrade checkpoints under " + backups() + ": pre-upgrade-1-2 (")
                .contains("To restore a backup");
    }

    @Test
    void aStoreWithoutCurrentButWithItsWalIsRefusedOnEveryOpenAndNotTouched() throws Exception {
        Path path = dir.resolve("store");
        writeSome(open(path, List.of()));
        closeAll();
        // the writes are still in the WAL: with it RocksDB's own open fails once, after writing a new CURRENT
        assertThat(fileNames(path)).anyMatch(name -> name.matches("\\d+\\.log"));
        Files.delete(path.resolve("CURRENT"));

        assertRefusedTwiceUntouched(path);
    }

    @Test
    void aStoreWithoutCurrentAndWithoutAWalIsRefusedOnEveryOpenAndNotTouched() throws Exception {
        Path path = dir.resolve("store");
        writeSome(open(path, List.of()));
        closeAll();
        flushAll(path);
        // without a WAL RocksDB's own open succeeds on an empty store and purges the old files
        for (String name : fileNames(path)) {
            if (name.endsWith(".log") && !name.startsWith("LOG")) {
                Files.delete(path.resolve(name));
            }
        }
        assertThat(fileNames(path)).anyMatch(name -> name.endsWith(".sst")).noneMatch(name -> name.matches("\\d+\\.log"));
        Files.delete(path.resolve("CURRENT"));

        assertRefusedTwiceUntouched(path);
    }

    @Test
    void anEmptyDirectoryStillBecomesANewStore() throws Exception {
        Path path = Files.createDirectories(dir.resolve("store"));

        RocksDbStore store = open(path, List.of());

        assertThat(store.meta().schemaVersion()).isEqualTo(RocksDbStore.SCHEMA_VERSION);
        assertThat(path.resolve("CURRENT")).isRegularFile();
    }

    private void assertRefusedTwiceUntouched(Path path) throws IOException {
        Map<String, ByteBuffer> before = snapshot(path);

        // a broker with restart: always opens again right after the refusal
        for (int attempt = 1; attempt <= 2; attempt++) {
            Throwable thrown = catchThrowable(() -> open(path, List.of()));

            assertThat(thrown).as("open %d", attempt).isInstanceOf(StoreCorruptedException.class);
            assertThat(thrown.getMessage()).as("open %d", attempt)
                    .startsWith("The store at " + path + " is corrupted: CURRENT is missing but the directory holds the store's files (")
                    .contains("MANIFEST-")
                    .contains("No pre-upgrade checkpoint under " + backups() + ".")
                    .contains("To restore a backup: stop the broker, replace " + path + " with the backup's db directory, start the broker.");
            assertThat(snapshot(path)).as("open %d", attempt).isEqualTo(before);
        }
    }

    private static List<String> fileNames(Path path) throws IOException {
        try (Stream<Path> entries = Files.list(path)) {
            return entries.map(entry -> entry.getFileName().toString()).sorted().toList();
        }
    }

    @Test
    void aSecondOpenOfAnOpenStoreIsAPlainLockFailure() {
        Path path = dir.resolve("store");
        open(path, List.of());

        Throwable thrown = catchThrowable(() -> open(path, List.of()));

        assertThat(thrown).isInstanceOf(StoreException.class).isNotInstanceOf(StoreCorruptedException.class);
        assertThat(thrown.getMessage()).containsIgnoringCase("lock").doesNotContain("corrupted");
    }

    @Test
    void aReadOnlyStoreDirectoryIsAPlainFailure() throws Exception {
        assumeFalse("root".equals(System.getProperty("user.name")), "root ignores file permissions");
        Path path = dir.resolve("store");
        writeSome(open(path, List.of()));
        closeAll();
        setReadOnly(path);
        try {
            assumeFalse(Files.isWritable(path), "the file system does not enforce the permissions");

            Throwable thrown = catchThrowable(() -> open(path, List.of()));

            assertThat(thrown).isInstanceOf(StoreException.class).isNotInstanceOf(StoreCorruptedException.class);
            assertThat(thrown.getMessage()).startsWith("Cannot open the store at " + path).doesNotContain("corrupted");
        } finally {
            restoreWritable(path);
        }
    }

    @Test
    void aCheckpointTakenDuringAnAbandonedResumeIsSafeToRestore() throws Exception {
        Path path = dir.resolve("store");
        createWithFamilies(path, List.of("meta"));
        assertThatThrownBy(() -> open(path, 3, List.of(writingThenFailingStep()))).hasMessageContaining("boom");
        // the rolled-back older build ignores the record of the upgrade and takes writes
        withRawFamily(path, StoreColumnFamily.CLIENT_SESSION, (db, cf) -> db.put(cf, bytes("c1"), bytes("by the older build")));
        // with store.backups.keep=1 the first checkpoint stays as well
        open(path, 1, List.of(new Schema1To2Step()));
        closeAll();
        Path first = backups().resolve("pre-upgrade-1-2");
        Path fresh = StoreCheckpoints.list(backups()).stream().map(CheckpointInfo::path)
                .filter(checkpoint -> !checkpoint.equals(first)).findFirst().orElseThrow();
        // the checkpoint holds a record that names itself
        long freshSequence = withReadOnly(fresh, (db, handles) -> db.getLatestSequenceNumber());
        assertThat(readOnlyGet(fresh, StoreColumnFamily.META, RocksDbStore.META_UPGRADE_CHECKPOINT_KEY))
                .isEqualTo(new StoreMigrationRunner.UpgradeRecord(freshSequence, fresh.getFileName().toString(),
                        "pre-upgrade-1-2").encode());
        Map<String, ByteBuffer> before = snapshot(fresh);
        List<String> checkpointsBefore = StoreCheckpoints.list(backups()).stream().map(CheckpointInfo::name).sorted().toList();

        // the restore of the message: stop the broker, replace the store with a copy of the checkpoint, start
        Path restored = copyDir(fresh, dir.resolve("restored"));
        RocksDbStore store = open(restored, 1, List.of(new Schema1To2Step()));

        assertThat(store.meta().schemaVersion()).isEqualTo(2);
        assertThat(store.get(StoreColumnFamily.META, RocksDbStore.META_UPGRADE_CHECKPOINT_KEY)).isNull();
        assertThat(store.get(StoreColumnFamily.CLIENT_SESSION, bytes("c1"))).isEqualTo(bytes("by the older build"));
        closeAll();
        // the copy is not in the checkpoint directory: the start resumed with the checkpoint and took no new one
        assertThat(StoreCheckpoints.list(backups()).stream().map(CheckpointInfo::name).sorted().toList())
                .isEqualTo(checkpointsBefore);
        // the checkpoint directory is untouched and still opens at its old schema
        assertThat(fresh).isDirectory();
        assertThat(snapshot(fresh)).isEqualTo(before);
        assertThat(readOnlyGet(fresh, StoreColumnFamily.META, RocksDbStore.META_SCHEMA_VERSION_KEY)).isEqualTo(new byte[]{0, 0, 0, 1});
        assertThat(readOnlyGet(fresh, StoreColumnFamily.CLIENT_SESSION, bytes("c1"))).isEqualTo(bytes("by the older build"));
    }

    private static void writeSome(RocksDbStore store) {
        for (int i = 0; i < 200; i++) {
            store.put(StoreWriteClass.NEW_DATA, StoreColumnFamily.ENTITY, bytes("key-" + i), new byte[100]);
        }
    }

    /**
     * Overwrites 32 bytes in the middle of the MANIFEST of the closed store at {@code path}; RocksDB reports a checksum
     * mismatch as Corruption.
     */
    private static void damageManifest(Path path) throws IOException {
        damageMiddle(only(path, "MANIFEST-"));
    }

    private static void damageEverySstMiddle(Path path) throws IOException {
        for (Path sst : files(path, ".sst")) {
            damageMiddle(sst);
        }
    }

    private static void damageMiddle(Path file) throws IOException {
        byte[] content = Files.readAllBytes(file);
        int from = content.length / 2;
        for (int i = from; i < Math.min(content.length, from + 32); i++) {
            content[i] = (byte) 0xFF;
        }
        Files.write(file, content);
    }

    private static List<Path> files(Path path, String nameContains) throws IOException {
        try (Stream<Path> entries = Files.list(path)) {
            return entries.filter(file -> file.getFileName().toString().contains(nameContains)).sorted().toList();
        }
    }

    private static Path only(Path path, String namePrefix) throws IOException {
        List<Path> found = files(path, namePrefix);
        assertThat(found).hasSize(1);
        return found.get(0);
    }

    private static void setReadOnly(Path path) throws IOException {
        try (Stream<Path> tree = Files.walk(path)) {
            for (Path entry : tree.toList()) {
                Files.setPosixFilePermissions(entry, Files.isDirectory(entry)
                        ? PosixFilePermissions.fromString("r-xr-xr-x") : PosixFilePermissions.fromString("r--r--r--"));
            }
        }
    }

    private static void restoreWritable(Path path) throws IOException {
        try (Stream<Path> tree = Files.walk(path)) {
            for (Path entry : tree.toList()) {
                Files.setPosixFilePermissions(entry, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE));
            }
        }
    }

    private static Path copyDir(Path source, Path target) throws IOException {
        try (Stream<Path> tree = Files.walk(source)) {
            for (Path entry : tree.toList()) {
                Path destination = target.resolve(source.relativize(entry).toString());
                if (Files.isDirectory(entry)) {
                    Files.createDirectories(destination);
                } else {
                    Files.copy(entry, destination);
                }
            }
        }
        return target;
    }

    /**
     * The relative name and the content of every file under {@code directory}.
     */
    private static Map<String, ByteBuffer> snapshot(Path directory) throws IOException {
        Map<String, ByteBuffer> files = new TreeMap<>();
        try (Stream<Path> tree = Files.walk(directory)) {
            for (Path entry : tree.filter(Files::isRegularFile).toList()) {
                files.put(directory.relativize(entry).toString(), ByteBuffer.wrap(Files.readAllBytes(entry)));
            }
        }
        return files;
    }

    private static StoreMigrationStep writingThenFailingStep() {
        return new StoreMigrationStep() {
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
    }

    private Path backups() {
        return dir.resolve("backups");
    }

    private RocksDbStore open(Path path, List<StoreMigrationStep> steps) {
        return open(path, 3, steps);
    }

    private RocksDbStore open(Path path, int keep, List<StoreMigrationStep> steps) {
        StoreProperties properties = new StoreProperties();
        properties.setPath(path.toString());
        properties.getBackups().setPath(backups().toString());
        properties.getBackups().setKeep(keep);
        RocksDbStore store = newStore(properties, steps);
        store.open();
        return store;
    }

    private RocksDbStore newStore(StoreProperties properties, List<StoreMigrationStep> steps) {
        RocksDbStore store = new RocksDbStore(properties, StoreMemoryBudget.forContainerMemory(1L << 30, 0, 0),
                TestDiskSpaceMonitors.roomy(properties), steps);
        opened.add(store);
        return store;
    }

    private static byte[] readOnlyGet(Path path, StoreColumnFamily cf, byte[] key) throws RocksDBException {
        return withReadOnly(path, (db, handles) -> db.get(handles.get(cf.ordinal() + 1), key));
    }

    private interface ReadOnlyAction<T> {
        T run(RocksDB db, List<ColumnFamilyHandle> handles) throws RocksDBException;
    }

    /**
     * Opens the store or checkpoint at {@code path} read-only; the handles are the default family's, then one per
     * {@link StoreColumnFamily} in ordinal order.
     */
    private static <T> T withReadOnly(Path path, ReadOnlyAction<T> action) throws RocksDBException {
        try (DBOptions dbOptions = new DBOptions(); ColumnFamilyOptions options = new ColumnFamilyOptions()) {
            List<ColumnFamilyHandle> handles = new ArrayList<>();
            try (RocksDB raw = RocksDB.openReadOnly(dbOptions, path.toString(), descriptors(options), handles)) {
                try {
                    return action.run(raw, handles);
                } finally {
                    handles.forEach(ColumnFamilyHandle::close);
                }
            }
        }
    }

    private interface RawFamilyAction {
        void run(RocksDB db, ColumnFamilyHandle cf) throws RocksDBException;
    }

    private static void withRawFamily(Path path, StoreColumnFamily cf, RawFamilyAction action) throws RocksDBException {
        try (DBOptions dbOptions = new DBOptions(); ColumnFamilyOptions options = new ColumnFamilyOptions()) {
            List<ColumnFamilyHandle> handles = new ArrayList<>();
            try (RocksDB raw = RocksDB.open(dbOptions, path.toString(), descriptors(options), handles)) {
                try {
                    action.run(raw, handles.get(cf.ordinal() + 1));
                } finally {
                    handles.forEach(ColumnFamilyHandle::close);
                }
            }
        }
    }

    private static List<ColumnFamilyDescriptor> descriptors(ColumnFamilyOptions options) {
        List<ColumnFamilyDescriptor> descriptors = new ArrayList<>();
        descriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, options));
        for (StoreColumnFamily family : StoreColumnFamily.values()) {
            descriptors.add(new ColumnFamilyDescriptor(bytes(family.columnFamilyName()), options));
        }
        return descriptors;
    }

    /**
     * Writes every memtable of the closed store at {@code path} to SST files, through a raw RocksDB handle.
     */
    private static void flushAll(Path path) throws RocksDBException {
        try (DBOptions dbOptions = new DBOptions(); ColumnFamilyOptions options = new ColumnFamilyOptions();
             FlushOptions flushOptions = new FlushOptions().setWaitForFlush(true)) {
            List<ColumnFamilyHandle> handles = new ArrayList<>();
            try (RocksDB raw = RocksDB.open(dbOptions, path.toString(), descriptors(options), handles)) {
                try {
                    raw.flush(flushOptions, handles);
                } finally {
                    handles.forEach(ColumnFamilyHandle::close);
                }
            }
        }
    }

    private static void createWithFamilies(Path path, List<String> families) throws RocksDBException {
        List<ColumnFamilyDescriptor> descriptors = new ArrayList<>();
        descriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY));
        families.forEach(name -> descriptors.add(new ColumnFamilyDescriptor(name.getBytes(StandardCharsets.UTF_8))));
        List<ColumnFamilyHandle> handles = new ArrayList<>();
        try (DBOptions options = new DBOptions().setCreateIfMissing(true).setCreateMissingColumnFamilies(true);
             RocksDB db = RocksDB.open(options, path.toString(), descriptors, handles)) {
            // the meta of a store that Phase 2 had opened once
            try {
                db.put(handles.get(1), RocksDbStore.META_SCHEMA_VERSION_KEY, new byte[]{0, 0, 0, 1});
                db.put(handles.get(1), RocksDbStore.META_PRODUCT_KEY, bytes("LITE"));
                db.put(handles.get(1), RocksDbStore.META_INSTALL_ID_KEY, bytes(UUID.randomUUID().toString()));
            } finally {
                handles.forEach(ColumnFamilyHandle::close);
            }
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

}
