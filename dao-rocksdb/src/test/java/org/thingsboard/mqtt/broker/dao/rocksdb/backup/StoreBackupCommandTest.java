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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.thingsboard.mqtt.broker.dao.rocksdb.RocksDbStore;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreColumnFamily;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreMemoryBudget;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreProperties;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreWriteClass;
import org.thingsboard.mqtt.broker.dao.rocksdb.disk.TestDiskSpaceMonitors;
import org.thingsboard.mqtt.broker.dao.rocksdb.migration.StoreMigrationStep;
import org.thingsboard.mqtt.broker.dao.store.StoreException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoreBackupCommandTest {

    private static final String LOCKED_MESSAGE = "The store at %s is in use by a running broker; back it up through GET /api/system/backup";

    @TempDir
    Path dir;

    private final List<RocksDbStore> opened = new ArrayList<>();
    private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
    private final PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

    @AfterEach
    void closeAll() {
        opened.forEach(RocksDbStore::close);
        opened.clear();
    }

    @Test
    void aStoppedStoreIsBackedUpToAnArchiveThatRestores() throws IOException {
        SystemTar.assumeAvailable();
        Path storePath = dir.resolve("data").resolve("db");
        RocksDbStore store = open(storePath);
        store.put(StoreWriteClass.NEW_DATA, StoreColumnFamily.CLIENT_SESSION, bytes("k1"), bytes("v1"));
        store.put(StoreWriteClass.NEW_DATA, StoreColumnFamily.RETAINED_MSG, bytes("k2"), bytes("v2"));
        var meta = store.meta();
        store.close();
        Path out = dir.resolve("backup.tar.gz");

        int exit = StoreBackupCommand.run(new String[]{"--store-path", storePath.toString(), "--out", out.toString()}, err);

        assertThat(exit).as(err()).isZero();
        Path extracted = Files.createDirectory(dir.resolve("restored"));
        SystemTar.extract(out, extracted);
        assertThat(names(extracted)).containsExactlyInAnyOrder("backup-manifest.json", "db");
        RocksDbStore restored = open(extracted.resolve("db"));
        assertThat(restored.get(StoreColumnFamily.CLIENT_SESSION, bytes("k1"))).isEqualTo(bytes("v1"));
        assertThat(restored.get(StoreColumnFamily.RETAINED_MSG, bytes("k2"))).isEqualTo(bytes("v2"));
        assertThat(restored.meta()).isEqualTo(meta);
        // default --backups-path is <store-path>/../backups; the temporary checkpoint is gone, and so is the temp file
        assertThat(names(dir.resolve("data").resolve("backups"))).isEmpty();
        assertThat(names(dir)).doesNotContain("backups").contains("backup.tar.gz").noneMatch(name -> name.endsWith(".tmp"));
    }

    @Test
    void aBackupsPathGivenOnTheCommandLineHoldsTheTemporaryCheckpoint() throws IOException {
        Path storePath = dir.resolve("db");
        open(storePath).close();
        Path backups = dir.resolve("my-backups");

        int exit = StoreBackupCommand.run(new String[]{"--store-path", storePath.toString(),
                "--out", dir.resolve("b.tar.gz").toString(), "--backups-path", backups.toString()}, err);

        assertThat(exit).as(err()).isZero();
        assertThat(backups).isEmptyDirectory();
        assertThat(dir.resolve("backups")).doesNotExist();
    }

    @Test
    void aStoreHeldByARunningBrokerExitsWith2() throws IOException {
        Path storePath = dir.resolve("db");
        RocksDbStore running = open(storePath);
        Path out = dir.resolve("backup.tar.gz");

        int exit = StoreBackupCommand.run(new String[]{"--store-path", storePath.toString(), "--out", out.toString()}, err);

        assertThat(exit).isEqualTo(2);
        assertThat(err()).contains(LOCKED_MESSAGE.formatted(storePath));
        assertThat(out).doesNotExist();
        assertThat(names(dir)).containsExactly("db");
        // the holder is untouched
        running.put(StoreWriteClass.NEW_DATA, StoreColumnFamily.CLIENT_SESSION, bytes("k"), bytes("v"));
    }

    @Test
    void aMissingOutIsAUsageError() {
        int exit = StoreBackupCommand.run(new String[]{"--store-path", dir.resolve("db").toString()}, err);

        assertThat(exit).isEqualTo(1);
        assertThat(err()).contains("Usage:", "--store-path", "--out", "--backups-path");
        assertThat(dir.resolve("db")).doesNotExist();
    }

    @Test
    void badArgumentsAreUsageErrors() {
        Path out = dir.resolve("o.tar.gz");
        String store = dir.resolve("db").toString();
        List<String[]> cases = List.of(
                new String[]{},
                new String[]{"--out", out.toString()},
                new String[]{"--store-path", store, "--out", out.toString(), "--bogus", "x"},
                new String[]{"--store-path", store, "--out", out.toString(), "--out", out.toString()},
                new String[]{"--store-path", store, "--out"},
                new String[]{"--store-path", store, "positional", "--out", out.toString()},
                new String[]{"--store-path", store, "--out", out.toString(), "--backups-path"});
        for (String[] args : cases) {
            errBytes.reset();
            assertThat(StoreBackupCommand.run(args, err)).as(String.join(" ", args)).isEqualTo(1);
            assertThat(err()).as(String.join(" ", args)).contains("Usage:");
        }
        assertThat(names(dir)).isEmpty();
    }

    @Test
    void aMissingStoreIsNotCreated() throws IOException {
        Path storePath = dir.resolve("nothing");
        int exit = StoreBackupCommand.run(new String[]{"--store-path", storePath.toString(),
                "--out", dir.resolve("o.tar.gz").toString()}, err);

        assertThat(exit).isEqualTo(1);
        assertThat(err()).contains("No store at " + storePath);
        assertThat(storePath).doesNotExist();
        assertThat(names(dir)).isEmpty();
    }

    @Test
    void aDirectoryWithoutAStoreIsNotInitialised() throws IOException {
        Path storePath = Files.createDirectory(dir.resolve("empty"));

        int exit = StoreBackupCommand.run(new String[]{"--store-path", storePath.toString(),
                "--out", dir.resolve("o.tar.gz").toString()}, err);

        assertThat(exit).isEqualTo(1);
        assertThat(err()).contains("No store at " + storePath);
        assertThat(storePath).isEmptyDirectory();
        assertThat(names(dir)).containsExactly("empty");
    }

    @Test
    void anExistingOutIsNeverOverwritten() throws IOException {
        Path storePath = dir.resolve("db");
        open(storePath).close();
        Path out = Files.write(dir.resolve("o.tar.gz"), bytes("precious"));

        int exit = StoreBackupCommand.run(new String[]{"--store-path", storePath.toString(), "--out", out.toString()}, err);

        assertThat(exit).isEqualTo(1);
        assertThat(err()).contains(out.toString(), "already exists");
        assertThat(Files.readAllBytes(out)).isEqualTo(bytes("precious"));
        assertThat(names(dir)).containsExactlyInAnyOrder("db", "o.tar.gz");
    }

    @Test
    void aStoreOfAnOlderSchemaIsRefusedBeforeAnythingIsWritten() throws IOException, URISyntaxException {
        Path storePath = copy(fixturePath(), dir.resolve("db"));
        Map<String, byte[]> sstBefore = sstFiles(storePath);
        assertThat(sstBefore).isNotEmpty();
        Path out = dir.resolve("o.tar.gz");

        int exit = StoreBackupCommand.run(new String[]{"--store-path", storePath.toString(), "--out", out.toString()}, err);

        assertThat(exit).isEqualTo(1);
        assertThat(err()).contains("no migration step from schema 1")
                .contains("start the broker of this version once to upgrade it, or back it up with the version that wrote it");
        assertThat(out).doesNotExist();
        assertThat(names(dir)).containsExactlyInAnyOrder("db");
        assertThat(sstFiles(storePath)).containsExactlyInAnyOrderEntriesOf(sstBefore);
        // still schema 1: a plain open without steps refuses it the same way
        assertThatThrownBy(() -> open(storePath)).isInstanceOf(StoreException.class).hasMessageContaining("no migration step from schema 1");
    }

    @Test
    void aStoreOfANewerSchemaIsRefused() throws IOException {
        Path storePath = dir.resolve("db");
        RocksDbStore store = open(storePath);
        store.put(StoreWriteClass.UPDATE, StoreColumnFamily.META, bytes("schema_version"), new byte[]{0, 0, 0, 99});
        store.close();

        int exit = StoreBackupCommand.run(new String[]{"--store-path", storePath.toString(),
                "--out", dir.resolve("o.tar.gz").toString()}, err);

        assertThat(exit).isEqualTo(1);
        assertThat(err()).contains("schema version 99");
        assertThat(names(dir)).containsExactly("db");
    }

    @Test
    void aFailedBackupLeavesNoPartialOut() throws IOException {
        Path storePath = dir.resolve("db");
        open(storePath).close();
        // the backups path is a file: the checkpoint cannot be taken
        Path notADirectory = Files.write(dir.resolve("not-a-directory"), bytes("x"));
        Path out = dir.resolve("o.tar.gz");

        int exit = StoreBackupCommand.run(new String[]{"--store-path", storePath.toString(), "--out", out.toString(),
                "--backups-path", notADirectory.toString()}, err);

        assertThat(exit).isEqualTo(1);
        assertThat(err()).contains("checkpoint");
        assertThat(names(dir)).containsExactlyInAnyOrder("db", "not-a-directory");
    }

    private String err() {
        return errBytes.toString(StandardCharsets.UTF_8);
    }

    private RocksDbStore open(Path path) {
        StoreProperties props = new StoreProperties();
        props.setPath(path.toString());
        props.getBackups().setPath(dir.resolve("test-backups").toString());
        RocksDbStore store = new RocksDbStore(props, StoreMemoryBudget.forContainerMemory(1L << 30, 0, 0),
                TestDiskSpaceMonitors.roomy(props), List.<StoreMigrationStep>of());
        opened.add(store);
        store.open();
        return store;
    }

    private static Map<String, byte[]> sstFiles(Path storePath) throws IOException {
        Map<String, byte[]> files = new TreeMap<>();
        try (Stream<Path> entries = Files.list(storePath)) {
            for (Path file : entries.filter(path -> path.getFileName().toString().endsWith(".sst")).toList()) {
                files.put(file.getFileName().toString(), Files.readAllBytes(file));
            }
        }
        return files;
    }

    private static Path fixturePath() throws URISyntaxException {
        URL url = StoreBackupCommandTest.class.getResource("/fixtures/schema-1/db");
        assertThat(url).as("the schema 1 fixture on the test classpath").isNotNull();
        return Path.of(url.toURI());
    }

    private static Path copy(Path from, Path to) throws IOException {
        Files.createDirectories(to);
        try (Stream<Path> files = Files.list(from)) {
            for (Path file : files.toList()) {
                Files.copy(file, to.resolve(file.getFileName()));
            }
        }
        return to;
    }

    private static List<String> names(Path directory) {
        if (!Files.exists(directory)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.map(path -> path.getFileName().toString()).toList();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

}
