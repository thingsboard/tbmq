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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class StoreCheckpointsTest {

    private static final Instant NOW = Instant.parse("2026-11-23T08:05:09Z");

    @TempDir
    Path dir;

    private final List<RocksDbStore> opened = new ArrayList<>();
    // the retention's log, kept out of the build output
    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    @BeforeEach
    void captureLog() {
        logged.start();
        Logger logger = (Logger) LoggerFactory.getLogger(StoreCheckpoints.class);
        logger.addAppender(logged);
        logger.setAdditive(false);
    }

    @AfterEach
    void closeAll() {
        opened.forEach(RocksDbStore::close);
        opened.clear();
    }

    @AfterEach
    void releaseLog() {
        Logger logger = (Logger) LoggerFactory.getLogger(StoreCheckpoints.class);
        logger.detachAppender(logged);
        logger.setAdditive(true);
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
    void aCheckpointThatCannotBeWrittenFailsNamingTheCauseAndTheSetting() throws IOException {
        RocksDbStore store = open(dir.resolve("db"));
        Path backups = Files.createDirectories(dir.resolve("backups"));
        assertThat(backups.toFile().setWritable(false)).isTrue();
        try {
            assumeFalse(Files.isWritable(backups), "running as a user that ignores directory permissions");
            Path target = backups.resolve("nested").resolve("pre-upgrade-1-2");

            assertThatThrownBy(() -> store.createCheckpoint(target))
                    .isInstanceOf(StoreException.class)
                    .hasMessageContaining(target.toString())
                    .hasMessageContaining("store.backups.path")
                    .hasMessageContaining("AccessDeniedException");
        } finally {
            backups.toFile().setWritable(true);
        }
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
    void aTakenTimestampedNameGetsTheFirstFreeNumericSuffix() throws IOException {
        Path backups = dir.resolve("backups");
        Files.createDirectories(backups.resolve("pre-upgrade-1-2"));
        Files.createDirectories(backups.resolve("pre-upgrade-1-2-20261123-080509"));

        Path third = StoreCheckpoints.preUpgradeDir(backups, 1, 2, clock());
        assertThat(third).isEqualTo(backups.resolve("pre-upgrade-1-2-20261123-080509-2"));
        Files.createDirectories(third);

        assertThat(StoreCheckpoints.preUpgradeDir(backups, 1, 2, clock()))
                .isEqualTo(backups.resolve("pre-upgrade-1-2-20261123-080509-3"));
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
    void onlyTheNamesPreUpgradeDirGivesAreCheckpoints() throws IOException {
        Path backups = Files.createDirectories(dir.resolve("backups"));
        checkpointDir(backups, "pre-upgrade-1-2", 1000);
        checkpointDir(backups, "pre-upgrade-1-2-20261123-080509", 2000);
        checkpointDir(backups, "pre-upgrade-1-2-20261123-080509-2", 3000);
        // the staging directory RocksDB leaves behind when a checkpoint is interrupted, and lookalikes
        for (String name : List.of("pre-upgrade-1-2.tmp", "pre-upgrade-1-2-20261123-080509.tmp", "pre-upgrade-x-2",
                "pre-upgrade-1-2-old", "pre-upgrade-1-2-2026-080509", "pre-upgrade-1-2-20261123-080509-")) {
            checkpointDir(backups, name, 1);
        }

        assertThat(StoreCheckpoints.list(backups)).extracting(CheckpointInfo::name).containsExactly(
                "pre-upgrade-1-2-20261123-080509-2", "pre-upgrade-1-2-20261123-080509", "pre-upgrade-1-2");
    }

    @Test
    void retainNewestNeverDeletesAStagingDirectory() throws IOException {
        Path backups = Files.createDirectories(dir.resolve("backups"));
        Path staging = checkpointDir(backups, "pre-upgrade-1-2.tmp", 1);
        checkpointDir(backups, "pre-upgrade-2-3", 2000);
        checkpointDir(backups, "pre-upgrade-3-4", 3000);

        StoreCheckpoints.retainNewest(backups, 1);

        assertThat(staging).isDirectory();
        assertThat(backups.resolve("pre-upgrade-2-3")).doesNotExist();
        assertThat(backups.resolve("pre-upgrade-3-4")).isDirectory();
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

    @Test
    void retainNewestNeverDeletesTheProtectedCheckpointEvenWhenItLooksOldest() throws IOException {
        Path backups = Files.createDirectories(dir.resolve("backups"));
        // taken with a clock that was behind: it looks older than every other checkpoint
        Path current = checkpointDir(backups, "pre-upgrade-4-5", 500);
        checkpointDir(backups, "pre-upgrade-1-2", 1000);
        checkpointDir(backups, "pre-upgrade-2-3", 2000);
        checkpointDir(backups, "pre-upgrade-3-4", 3000);

        StoreCheckpoints.retainNewest(backups, 2, current);

        assertThat(StoreCheckpoints.list(backups)).extracting(CheckpointInfo::name)
                .containsExactly("pre-upgrade-3-4", "pre-upgrade-4-5");
    }

    @Test
    void retainNewestWithKeepOneKeepsOnlyTheProtectedCheckpoint() throws IOException {
        Path backups = Files.createDirectories(dir.resolve("backups"));
        Path current = checkpointDir(backups, "pre-upgrade-2-3", 500);
        checkpointDir(backups, "pre-upgrade-1-2", 1000);

        // a relative, unnormalized path names the same directory
        StoreCheckpoints.retainNewest(backups, 1, dir.resolve("backups/./pre-upgrade-2-3"));

        assertThat(StoreCheckpoints.list(backups)).extracting(CheckpointInfo::path).containsExactly(current);
    }

    @Test
    void retainNewestWithAProtectedPathThatIsNoCheckpointKeepsTheNewest() throws IOException {
        Path backups = Files.createDirectories(dir.resolve("backups"));
        checkpointDir(backups, "pre-upgrade-1-2", 1000);
        checkpointDir(backups, "pre-upgrade-2-3", 2000);

        StoreCheckpoints.retainNewest(backups, 1, backups.resolve("pre-upgrade-9-10"));

        assertThat(StoreCheckpoints.list(backups)).extracting(CheckpointInfo::name).containsExactly("pre-upgrade-2-3");
    }

    @Test
    void retainNewestNeverDeletesTheCheckpointTheStoreRunsFrom() throws IOException {
        Path backups = Files.createDirectories(dir.resolve("backups"));
        // the store was started from a restored checkpoint and upgraded again
        Path running = checkpointDir(backups, "pre-upgrade-1-2", 500);
        Path current = checkpointDir(backups, "pre-upgrade-1-2-20261123-080509", 1000);
        Path other = checkpointDir(backups, "pre-upgrade-2-3", 2000);

        Path store = dir.resolve("backups/../backups/pre-upgrade-1-2");
        StoreCheckpoints.retainNewest(backups, 1, current, store);

        assertThat(StoreCheckpoints.list(backups)).extracting(CheckpointInfo::path).containsExactly(current, running);
        assertThat(other).doesNotExist();
        assertKeptWithAWarning(running, store);
    }

    @Test
    void retainNewestNeverDeletesACheckpointThatHoldsTheStore() throws IOException {
        Path backups = Files.createDirectories(dir.resolve("backups"));
        Path holding = checkpointDir(backups, "pre-upgrade-1-2", 500);
        Path store = Files.createDirectories(holding.resolve("db"));
        Files.setLastModifiedTime(holding, FileTime.from(Instant.ofEpochSecond(500)));
        Path current = checkpointDir(backups, "pre-upgrade-1-2-20261123-080509", 1000);
        checkpointDir(backups, "pre-upgrade-2-3", 2000);

        StoreCheckpoints.retainNewest(backups, 1, current, store);

        assertThat(StoreCheckpoints.list(backups)).extracting(CheckpointInfo::path).containsExactly(current, holding);
        assertKeptWithAWarning(holding, store);
    }

    @Test
    void retainNewestKeepsTheCheckpointTheStoreRunsFromThroughASymbolicLink() throws IOException {
        Path backups = Files.createDirectories(dir.resolve("backups"));
        Path running = checkpointDir(backups, "pre-upgrade-1-2", 500);
        Path current = checkpointDir(backups, "pre-upgrade-1-2-20261123-080509", 1000);
        Path link;
        try {
            link = Files.createSymbolicLink(dir.resolve("store"), running);
        } catch (UnsupportedOperationException | IOException e) {
            assumeFalse(true, "no symbolic links: " + e);
            return;
        }

        StoreCheckpoints.retainNewest(backups, 1, current, link);

        assertThat(StoreCheckpoints.list(backups)).extracting(CheckpointInfo::path).containsExactly(current, running);
        assertKeptWithAWarning(running, link);
    }

    @Test
    void retainNewestStillKeepsTheNewestWhenTheStoreRunsFromElsewhere() throws IOException {
        Path backups = Files.createDirectories(dir.resolve("backups"));
        Path current = checkpointDir(backups, "pre-upgrade-1-2", 500);
        Path newest = checkpointDir(backups, "pre-upgrade-0-1", 2000);
        checkpointDir(backups, "pre-upgrade-2-3", 1000);
        // a sibling whose name starts with a checkpoint's name is not inside it
        Path store = Files.createDirectories(dir.resolve("backups").resolve("pre-upgrade-2-3-db"));

        StoreCheckpoints.retainNewest(backups, 2, current, store);

        assertThat(StoreCheckpoints.list(backups)).extracting(CheckpointInfo::path).containsExactly(newest, current);
        assertThat(logged.list).filteredOn(event -> event.getLevel() == Level.WARN).isEmpty();
    }

    /**
     * The retention logged once, at WARN, that it kept {@code checkpoint} because the store at {@code store} runs
     * from it.
     */
    private void assertKeptWithAWarning(Path checkpoint, Path store) {
        assertThat(logged.list).filteredOn(event -> event.getLevel() == Level.WARN)
                .singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage())
                        .isEqualTo("Kept the checkpoint " + checkpoint + " because the store at " + store + " runs from it"));
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
