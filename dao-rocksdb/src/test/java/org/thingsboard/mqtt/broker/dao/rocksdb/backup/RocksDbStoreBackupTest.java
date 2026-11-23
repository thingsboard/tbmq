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
package org.thingsboard.mqtt.broker.dao.rocksdb.backup;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.rocksdb.RocksDbStore;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreColumnFamily;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreMemoryBudget;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreProperties;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreWriteClass;
import org.thingsboard.mqtt.broker.dao.rocksdb.disk.TestDiskSpaceMonitors;
import org.thingsboard.mqtt.broker.dao.rocksdb.migration.StoreMigrationRunner;
import org.thingsboard.mqtt.broker.dao.store.BackupInProgressException;
import org.thingsboard.mqtt.broker.dao.store.StoreBackup;
import org.thingsboard.mqtt.broker.dao.store.StoreException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RocksDbStoreBackupTest {

    private static final Instant NOW = Instant.parse("2026-11-23T08:05:09Z");
    private static final String VERSION = "2.5.0-TEST";
    private static final byte[] UPGRADE_RECORD_KEY = "upgrade_checkpoint".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path dir;

    private final List<RocksDbStore> opened = new ArrayList<>();
    private final Map<RocksDbStore, StoreProperties> properties = new IdentityHashMap<>();

    @AfterEach
    void closeAll() {
        opened.forEach(RocksDbStore::close);
        opened.clear();
    }

    @Test
    void theArchiveRestoresToAStoreWithTheSameData() throws IOException {
        SystemTar.assumeAvailable();
        Path backups = dir.resolve("backups");
        RocksDbStore store = open(dir.resolve("db"), backups);
        store.put(StoreWriteClass.NEW_DATA, StoreColumnFamily.CLIENT_SESSION, bytes("k1"), bytes("v1"));
        store.put(StoreWriteClass.NEW_DATA, StoreColumnFamily.RETAINED_MSG, bytes("k2"), bytes("v2"));
        Path archive = dir.resolve("backup.tar.gz");

        try (OutputStream out = Files.newOutputStream(archive)) {
            backup(store).write(out);
        }

        Path extracted = Files.createDirectory(dir.resolve("restored"));
        SystemTar.extract(archive, extracted);
        assertThat(names(extracted)).containsExactlyInAnyOrder(RocksDbStoreBackup.MANIFEST_NAME, "db");
        RocksDbStore restored = open(extracted.resolve("db"), dir.resolve("other-backups"));
        assertThat(restored.get(StoreColumnFamily.CLIENT_SESSION, bytes("k1"))).isEqualTo(bytes("v1"));
        assertThat(restored.get(StoreColumnFamily.RETAINED_MSG, bytes("k2"))).isEqualTo(bytes("v2"));
        assertThat(restored.meta()).isEqualTo(store.meta());
        assertThat(names(backups)).as("the temporary checkpoint is deleted").isEmpty();
    }

    @Test
    void theManifestNamesTheStoreAndNothingElse() throws IOException {
        SystemTar.assumeAvailable();
        RocksDbStore store = open(dir.resolve("db"), dir.resolve("backups"));
        Path archive = dir.resolve("backup.tar.gz");

        try (OutputStream out = Files.newOutputStream(archive)) {
            backup(store).write(out);
        }

        Path extracted = Files.createDirectory(dir.resolve("restored"));
        SystemTar.extract(archive, extracted);
        JsonNode manifest = JacksonUtil.toJsonNode(Files.readString(extracted.resolve(RocksDbStoreBackup.MANIFEST_NAME)));
        List<String> keys = new ArrayList<>();
        manifest.fieldNames().forEachRemaining(keys::add);
        // never the JWT signing key or any other secret of the meta
        assertThat(keys).containsExactlyInAnyOrder("product", "schemaVersion", "installId", "tbmqVersion", "createdAt");
        assertThat(manifest.get("product").asText()).isEqualTo(RocksDbStore.PRODUCT);
        assertThat(manifest.get("schemaVersion").asInt()).isEqualTo(RocksDbStore.SCHEMA_VERSION);
        assertThat(manifest.get("installId").asText()).isEqualTo(store.meta().installId().toString());
        assertThat(manifest.get("tbmqVersion").asText()).isEqualTo(VERSION);
        assertThat(manifest.get("createdAt").asText()).isEqualTo("2026-11-23T08:05:09Z");
    }

    @Test
    void aSecondConcurrentBackupIsRefused() throws Exception {
        RocksDbStore store = open(dir.resolve("db"), dir.resolve("backups"));
        RocksDbStoreBackup backup = backup(store);

        StoreBackup.Reservation first = backup.reserve();

        assertThatThrownBy(backup::reserve).isInstanceOf(BackupInProgressException.class)
                .hasMessageContaining("already in progress");
        assertThatThrownBy(() -> backup.write(new ByteArrayOutputStream())).isInstanceOf(BackupInProgressException.class);
        first.close();
        // released: the next one runs
        backup.write(new ByteArrayOutputStream());
    }

    @Test
    void closingAReservationWhileItWritesDoesNotReleaseTheSlot() throws Exception {
        RocksDbStore store = open(dir.resolve("db"), dir.resolve("backups"));
        RocksDbStoreBackup backup = backup(store);
        CountDownLatch writing = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        OutputStream blocking = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                write(new byte[]{(byte) b}, 0, 1);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                writing.countDown();
                try {
                    resume.await();
                } catch (InterruptedException e) {
                    throw new IOException(e);
                }
            }
        };
        StoreBackup.Reservation reservation = backup.reserve();
        CompletableFuture<Void> written = CompletableFuture.runAsync(() -> {
            try {
                reservation.writeTo(blocking);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        assertThat(writing.await(10, TimeUnit.SECONDS)).isTrue();

        // e.g. the request completes while the body still runs: the body releases the slot when it ends
        reservation.close();
        assertThatThrownBy(backup::reserve).isInstanceOf(BackupInProgressException.class);

        resume.countDown();
        written.get(10, TimeUnit.SECONDS);
        backup.reserve().close();
        assertThatThrownBy(() -> reservation.writeTo(new ByteArrayOutputStream())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theTemporaryCheckpointIsDeletedWhenTheClientDisconnects() throws IOException {
        Path backups = dir.resolve("backups");
        RocksDbStore store = open(dir.resolve("db"), backups);
        byte[] incompressible = new byte[256 * 1024];
        new Random(7).nextBytes(incompressible);
        store.put(StoreWriteClass.NEW_DATA, StoreColumnFamily.CLIENT_SESSION, bytes("big"), incompressible);
        RocksDbStoreBackup backup = backup(store);
        OutputStream clientGoneAfter10Kb = new OutputStream() {
            private long written;

            @Override
            public void write(int b) throws IOException {
                write(new byte[]{(byte) b}, 0, 1);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                written += len;
                if (written > 10 * 1024) {
                    throw new IOException("Connection reset by peer");
                }
            }
        };

        assertThatThrownBy(() -> backup.write(clientGoneAfter10Kb)).isInstanceOf(IOException.class)
                .hasMessage("Connection reset by peer");

        assertThat(names(backups)).as("no temporary checkpoint left").isEmpty();
        // the slot is released
        backup.write(new ByteArrayOutputStream());
    }

    @Test
    void leftoversFromACrashAreDeletedAtStartup() throws IOException {
        Path backups = dir.resolve("backups");
        RocksDbStore store = open(dir.resolve("db"), backups);
        String leftover = "tmp-backup-" + UUID.randomUUID();
        Files.write(Files.createDirectories(backups.resolve(leftover)).resolve("000008.sst"), bytes("x"));
        String staging = "tmp-backup-" + UUID.randomUUID() + ".tmp";
        Files.createDirectories(backups.resolve(staging));
        List<String> kept = List.of("pre-upgrade-1-2", "pre-upgrade-1-2-20261123-080509", "tmp-backup-1", "tmp-backup-",
                "tmp-backup-" + UUID.randomUUID() + "-copy", "my-backup");
        for (String name : kept) {
            Files.createDirectories(backups.resolve(name));
        }

        backup(store).deleteLeftovers();

        assertThat(names(backups)).containsExactlyInAnyOrderElementsOf(kept);
    }

    @Test
    void theCheckpointsOfAnUnfinishedUpgradeSurviveTheCleanup() throws IOException {
        Path backups = dir.resolve("backups");
        RocksDbStore store = open(dir.resolve("db"), backups);
        String checkpoint = "tmp-backup-" + UUID.randomUUID();
        String origin = "tmp-backup-" + UUID.randomUUID();
        Files.createDirectories(backups.resolve(checkpoint));
        Files.createDirectories(backups.resolve(origin));
        store.put(StoreWriteClass.UPDATE, StoreColumnFamily.META, UPGRADE_RECORD_KEY,
                new StoreMigrationRunner.UpgradeRecord(1, checkpoint, origin).encode());

        backup(store).deleteLeftovers();

        assertThat(names(backups)).containsExactlyInAnyOrder(checkpoint, origin);
    }

    @Test
    void aDirectoryHoldingTheStoreSurvivesTheCleanup() throws IOException {
        Path backups = dir.resolve("backups");
        String holder = "tmp-backup-" + UUID.randomUUID();
        RocksDbStore store = open(backups.resolve(holder).resolve("db"), backups);

        backup(store).deleteLeftovers();

        assertThat(names(backups)).containsExactly(holder);
        assertThat(store.get(StoreColumnFamily.META, bytes("nothing"))).isNull();
    }

    @Test
    void aBlankBackupsPathFailsTheBackupAndSkipsTheCleanup() {
        RocksDbStore store = open(dir.resolve("db"), null);
        RocksDbStoreBackup backup = backup(store);

        backup.deleteLeftovers();

        assertThatThrownBy(() -> backup.write(new ByteArrayOutputStream())).isInstanceOf(StoreException.class)
                .hasMessageContaining("store.backups.path");
        assertThatThrownBy(backup::reserve).isInstanceOf(StoreException.class)
                .hasMessageContaining("store.backups.path");
    }

    @Test
    void aMissingBackupsDirectoryIsCreated() throws IOException {
        Path backups = dir.resolve("missing").resolve("backups");
        RocksDbStore store = open(dir.resolve("db"), backups);

        backup(store).deleteLeftovers();
        backup(store).write(new ByteArrayOutputStream());

        assertThat(backups).isEmptyDirectory();
    }

    private RocksDbStoreBackup backup(RocksDbStore store) {
        return new RocksDbStoreBackup(store, propertiesOf(store), VERSION, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private StoreProperties propertiesOf(RocksDbStore store) {
        return properties.get(store);
    }

    private RocksDbStore open(Path path, Path backups) {
        StoreProperties props = new StoreProperties();
        props.setPath(path.toString());
        props.getBackups().setPath(backups == null ? " " : backups.toString());
        RocksDbStore store = new RocksDbStore(props, StoreMemoryBudget.forContainerMemory(1L << 30, 0, 0),
                TestDiskSpaceMonitors.roomy(props), List.of());
        opened.add(store);
        properties.put(store, props);
        store.open();
        return store;
    }

    private static List<String> names(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.map(path -> path.getFileName().toString()).toList();
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

}
