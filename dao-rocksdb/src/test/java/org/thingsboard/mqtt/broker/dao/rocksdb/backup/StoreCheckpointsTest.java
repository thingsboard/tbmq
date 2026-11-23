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
import org.thingsboard.mqtt.broker.dao.store.StoreException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoreCheckpointsTest {

    private static final Instant NOW = Instant.parse("2026-11-23T08:05:09Z");

    @TempDir
    Path dir;

    private final List<RocksDbStore> opened = new ArrayList<>();

    @AfterEach
    void closeAll() {
        opened.forEach(RocksDbStore::close);
        opened.clear();
    }

    @Test
    void aCheckpointOpensAsAStoreHoldingTheSameData() {
        RocksDbStore store = open(dir.resolve("db"));
        store.put(StoreWriteClass.NEW_DATA, StoreColumnFamily.CLIENT_SESSION, bytes("k"), bytes("v"));
        Path target = dir.resolve("backups").resolve("pre-upgrade-1-2");

        store.createCheckpoint(target);
        // a write after the checkpoint is not in it
        store.put(StoreWriteClass.NEW_DATA, StoreColumnFamily.CLIENT_SESSION, bytes("later"), bytes("x"));

        RocksDbStore restored = open(target);
        assertThat(restored.get(StoreColumnFamily.CLIENT_SESSION, bytes("k"))).isEqualTo(bytes("v"));
        assertThat(restored.get(StoreColumnFamily.CLIENT_SESSION, bytes("later"))).isNull();
        assertThat(restored.meta()).isEqualTo(store.meta());
    }

    @Test
    void aCheckpointIntoAnExistingDirectoryFailsNamingIt() throws IOException {
        RocksDbStore store = open(dir.resolve("db"));
        Path target = Files.createDirectories(dir.resolve("backups").resolve("pre-upgrade-1-2"));

        assertThatThrownBy(() -> store.createCheckpoint(target))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining(target.toString());
    }

    @Test
    void aCheckpointOfAClosedStoreFails() {
        RocksDbStore store = open(dir.resolve("db"));
        store.close();

        assertThatThrownBy(() -> store.createCheckpoint(dir.resolve("cp"))).isInstanceOf(StoreException.class);
    }

    @Test
    void preUpgradeDirNamesTheVersionsAndOnlyComputesThePath() {
        Path backups = dir.resolve("backups");

        Path path = StoreCheckpoints.preUpgradeDir(backups, 1, 2, clock());

        assertThat(path).isEqualTo(backups.resolve("pre-upgrade-1-2"));
        assertThat(path).doesNotExist();
    }

    @Test
    void aSecondCheckpointWithTheSameVersionsGetsATimestampSuffix() throws IOException {
        Path backups = dir.resolve("backups");
        Files.createDirectories(StoreCheckpoints.preUpgradeDir(backups, 1, 2, clock()));

        Path second = StoreCheckpoints.preUpgradeDir(backups, 1, 2, clock());

        assertThat(second).isEqualTo(backups.resolve("pre-upgrade-1-2-20261123-080509"));
    }

    @Test
    void theTimestampSuffixIsInUtc() throws IOException {
        Path backups = dir.resolve("backups");
        Files.createDirectories(backups.resolve("pre-upgrade-1-2"));
        Clock offsetClock = Clock.fixed(NOW, ZoneOffset.ofHours(5));

        assertThat(StoreCheckpoints.preUpgradeDir(backups, 1, 2, offsetClock).getFileName().toString())
                .isEqualTo("pre-upgrade-1-2-20261123-080509");
    }

    @Test
    void listReturnsThePreUpgradeDirectoriesNewestFirstWithTheirModificationTime() throws IOException {
        Path backups = Files.createDirectories(dir.resolve("backups"));
        Path a = checkpointDir(backups, "pre-upgrade-1-2", 1000);
        Path b = checkpointDir(backups, "pre-upgrade-2-3", 3000);
        Path c = checkpointDir(backups, "pre-upgrade-3-4", 2000);
        Files.createDirectories(backups.resolve("manual-copy"));
        Files.writeString(backups.resolve("pre-upgrade-notes.txt"), "x");

        List<CheckpointInfo> list = StoreCheckpoints.list(backups);

        assertThat(list).containsExactly(
                new CheckpointInfo("pre-upgrade-2-3", Instant.ofEpochSecond(3000), b),
                new CheckpointInfo("pre-upgrade-3-4", Instant.ofEpochSecond(2000), c),
                new CheckpointInfo("pre-upgrade-1-2", Instant.ofEpochSecond(1000), a));
    }

    @Test
    void listOfAMissingDirectoryIsEmpty() {
        assertThat(StoreCheckpoints.list(dir.resolve("missing"))).isEmpty();
    }

    @Test
    void retainNewestKeepsTheNewestAndLeavesOtherEntriesAlone() throws IOException {
        Path backups = Files.createDirectories(dir.resolve("backups"));
        for (int i = 1; i <= 5; i++) {
            checkpointDir(backups, "pre-upgrade-" + i + "-" + (i + 1), i * 1000L);
        }
        Path manual = Files.createDirectories(backups.resolve("manual-copy"));
        Path file = Files.writeString(backups.resolve("pre-upgrade-notes.txt"), "x");

        StoreCheckpoints.retainNewest(backups, 3);

        assertThat(StoreCheckpoints.list(backups)).extracting(CheckpointInfo::name)
                .containsExactly("pre-upgrade-5-6", "pre-upgrade-4-5", "pre-upgrade-3-4");
        assertThat(backups.resolve("pre-upgrade-1-2")).doesNotExist();
        assertThat(backups.resolve("pre-upgrade-2-3")).doesNotExist();
        assertThat(manual).isDirectory();
        assertThat(file).isRegularFile();
    }

    @Test
    void retainNewestDeletesADirectoryWithItsContent() throws IOException {
        Path backups = Files.createDirectories(dir.resolve("backups"));
        Path old = Files.createDirectories(backups.resolve("pre-upgrade-1-2"));
        Files.createDirectories(old.resolve("nested"));
        Files.writeString(old.resolve("nested").resolve("file"), "x");
        // set after the content is written, which moves the modification time of the directory
        Files.setLastModifiedTime(old, FileTime.from(Instant.ofEpochSecond(1000)));
        checkpointDir(backups, "pre-upgrade-2-3", 2000);

        StoreCheckpoints.retainNewest(backups, 1);

        assertThat(old).doesNotExist();
    }

    @Test
    void retainNewestBreaksTiesByNameDescending() throws IOException {
        Path backups = Files.createDirectories(dir.resolve("backups"));
        checkpointDir(backups, "pre-upgrade-1-2", 1000);
        checkpointDir(backups, "pre-upgrade-2-3", 1000);

        StoreCheckpoints.retainNewest(backups, 1);

        assertThat(StoreCheckpoints.list(backups)).extracting(CheckpointInfo::name).containsExactly("pre-upgrade-2-3");
    }

    @Test
    void retainNewestKeepsEverythingWhenThereAreNotMoreThanKeepAndToleratesAMissingDirectory() throws IOException {
        Path backups = Files.createDirectories(dir.resolve("backups"));
        checkpointDir(backups, "pre-upgrade-1-2", 1000);

        StoreCheckpoints.retainNewest(backups, 3);
        StoreCheckpoints.retainNewest(dir.resolve("missing"), 3);

        assertThat(StoreCheckpoints.list(backups)).hasSize(1);
    }

    private static Path checkpointDir(Path backups, String name, long modifiedEpochSecond) throws IOException {
        Path path = Files.createDirectories(backups.resolve(name));
        Files.setLastModifiedTime(path, FileTime.from(Instant.ofEpochSecond(modifiedEpochSecond)));
        return path;
    }

    private static Clock clock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    private RocksDbStore open(Path path) {
        StoreProperties properties = new StoreProperties();
        properties.setPath(path.toString());
        RocksDbStore store = new RocksDbStore(properties, StoreMemoryBudget.forContainerMemory(1L << 30, 0, 0),
                TestDiskSpaceMonitors.roomy(properties), List.of());
        opened.add(store);
        store.open();
        return store;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

}
