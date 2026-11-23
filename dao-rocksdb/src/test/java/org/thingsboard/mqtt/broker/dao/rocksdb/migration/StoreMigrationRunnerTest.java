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
package org.thingsboard.mqtt.broker.dao.rocksdb.migration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.RocksIterator;
import org.slf4j.LoggerFactory;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreColumnFamily;
import org.thingsboard.mqtt.broker.dao.rocksdb.backup.StoreCheckpoints;
import org.thingsboard.mqtt.broker.dao.store.StoreException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class StoreMigrationRunnerTest {

    private static final List<Class<?>> LOGGED = List.of(StoreMigrationRunner.class, StoreCheckpoints.class);
    private static final byte[] UPGRADE_CHECKPOINT = RawStores.bytes("upgrade_checkpoint");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-23T10:15:30Z"), ZoneOffset.UTC);

    @TempDir
    Path dir;

    // the runner's log and the checkpoint retention's, kept out of the build output
    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    @BeforeEach
    void captureLog() {
        logged.start();
        for (Class<?> source : LOGGED) {
            Logger logger = (Logger) LoggerFactory.getLogger(source);
            logger.addAppender(logged);
            logger.setAdditive(false);
        }
    }

    @AfterEach
    void releaseLog() {
        for (Class<?> source : LOGGED) {
            Logger logger = (Logger) LoggerFactory.getLogger(source);
            logger.detachAppender(logged);
            logger.setAdditive(true);
        }
    }

    @Test
    void aStoreTwoVersionsBackRunsBothStepsInOrderAndBumpsAfterEach() {
        FakeStore store = new FakeStore(1);

        int version = runner(List.of(new RecordingStep(2, store), new RecordingStep(1, store)), 3).run(store);

        assertThat(version).isEqualTo(3);
        assertThat(store.version).isEqualTo(3);
        assertThat(store.events).containsExactly("checkpoint pre-upgrade-1-3", "mark pre-upgrade-1-3", "step 1", "bump 2",
                "step 2", "finish 3");
        assertThat(store.upgradeCheckpoint()).isNull();
    }

    @Test
    void aCrashAfterAStepAndBeforeItsBumpRerunsTheStep() {
        FakeStore store = new FakeStore(1);
        AtomicInteger runs = new AtomicInteger();
        StoreMigrationStep crashingOnce = new RecordingStep(1, store) {
            @Override
            public void apply(MigrationContext ctx) {
                super.apply(ctx);
                ctx.put(StoreColumnFamily.META, RawStores.bytes("step-1-wrote"), new byte[]{1});
                if (runs.incrementAndGet() == 1) {
                    throw new IllegalStateException("boom");
                }
            }
        };
        StoreMigrationRunner runner = runner(List.of(crashingOnce, new RecordingStep(2, store)), 3);

        assertThatThrownBy(() -> runner.run(store))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("Migration from schema 1 to 2 failed: boom");
        assertThat(store.version).isEqualTo(1);
        assertThat(store.events).containsExactly("checkpoint pre-upgrade-1-3", "mark pre-upgrade-1-3", "step 1");
        assertThat(store.get(StoreColumnFamily.META, RawStores.bytes("step-1-wrote"))).isEqualTo(new byte[]{1});
        assertThat(store.upgradeCheckpoint()).isEqualTo("pre-upgrade-1-3");
        store.events.clear();

        assertThat(runner.run(store)).isEqualTo(3);

        // the rerun resumes with the checkpoint of the failed run
        assertThat(store.events).containsExactly("step 1", "bump 2", "step 2", "finish 3");
        assertThat(store.upgradeCheckpoint()).isNull();
    }

    @Test
    void failedStartsKeepTheOneCheckpointTakenBeforeAnyStepWrote() {
        FakeStore store = new FakeStore(1);
        store.put(StoreColumnFamily.CLIENT_SESSION, RawStores.bytes("c1"), RawStores.bytes("session"));
        AtomicInteger runs = new AtomicInteger();
        StoreMigrationStep failingThrice = new RecordingStep(1, store) {
            @Override
            public void apply(MigrationContext ctx) {
                super.apply(ctx);
                ctx.put(StoreColumnFamily.CLIENT_SESSION, RawStores.bytes("partly-migrated-" + runs.get()), new byte[]{1});
                if (runs.incrementAndGet() <= 3) {
                    throw new IllegalStateException("boom");
                }
            }
        };
        StoreMigrationRunner runner = runner(List.of(failingThrice), 2);
        for (int start = 0; start < 3; start++) {
            assertThatThrownBy(() -> runner.run(store)).isInstanceOf(StoreException.class);
            assertThat(store.upgradeCheckpoint()).isEqualTo("pre-upgrade-1-2");
        }

        assertThat(runner.run(store)).isEqualTo(2);

        assertThat(store.checkpoints).hasSize(1);
        Checkpoint checkpoint = store.checkpoints.get(0);
        assertThat(checkpoint.dir()).isEqualTo(backups().resolve("pre-upgrade-1-2"));
        assertThat(checkpoint.version()).isEqualTo(1);
        // the data before any step and no record of the upgrade
        assertThat(checkpoint.data()).containsOnlyKeys(key(StoreColumnFamily.CLIENT_SESSION, RawStores.bytes("c1")));
        assertThat(StoreCheckpoints.list(backups())).extracting(info -> info.path()).containsExactly(checkpoint.dir());
        assertThat(store.upgradeCheckpoint()).isNull();
        assertThat(store.version).isEqualTo(2);
        assertThat(logged.list).filteredOn(event -> event.getLevel() == Level.INFO
                        && event.getFormattedMessage().contains("Resuming the unfinished upgrade"))
                .hasSize(3)
                .allSatisfy(event -> assertThat(event.getFormattedMessage()).contains(checkpoint.dir().toString()));
    }

    @Test
    void aRecordedCheckpointThatIsGoneIsReplacedByANewOneWithAWarning() {
        for (String recorded : List.of("pre-upgrade-1-2-20260101-000000", "../store")) {
            logged.list.clear();
            FakeStore store = new FakeStore(1);
            store.recordUpgradeCheckpoint(recorded);
            store.events.clear();

            runner(List.of(new RecordingStep(1, store)), 2).run(store);

            Path taken = backups().resolve("pre-upgrade-1-2");
            assertThat(store.events).containsExactly("unmark", "checkpoint pre-upgrade-1-2", "mark pre-upgrade-1-2", "step 1", "finish 2");
            // the new checkpoint does not hold the stale record
            assertThat(store.checkpoints).singleElement().satisfies(checkpoint -> assertThat(checkpoint.data()).isEmpty());
            assertThat(store.upgradeCheckpoint()).isNull();
            assertThat(logged.list).filteredOn(event -> event.getLevel() == Level.WARN)
                    .singleElement()
                    .satisfies(event -> assertThat(event.getFormattedMessage()).contains(recorded).contains(store.path()));
            assertThat(taken).isDirectory();
            StoreCheckpoints.list(backups()).forEach(info -> deleteDir(info.path()));
        }
    }

    @Test
    void oneCheckpointIsTakenBeforeTheFirstStep() {
        FakeStore store = new FakeStore(1);
        store.put(StoreColumnFamily.CLIENT_SESSION, RawStores.bytes("c1"), RawStores.bytes("session"));

        runner(List.of(new RecordingStep(1, store), new RecordingStep(2, store)), 3).run(store);

        assertThat(store.checkpoints).hasSize(1);
        Checkpoint checkpoint = store.checkpoints.get(0);
        assertThat(checkpoint.dir()).isEqualTo(backups().resolve("pre-upgrade-1-3")).isDirectory();
        assertThat(checkpoint.version()).isEqualTo(1);
        // the version-1 data and none of the steps' writes
        assertThat(checkpoint.data()).containsOnlyKeys(key(StoreColumnFamily.CLIENT_SESSION, RawStores.bytes("c1")));
        assertThat(store.data).containsKeys(key(StoreColumnFamily.META, RawStores.bytes("step-1")),
                key(StoreColumnFamily.META, RawStores.bytes("step-2")));
    }

    @Test
    void aMissingStepFailsStartupNamingTheVersion() {
        FakeStore store = new FakeStore(1);

        assertThatThrownBy(() -> runner(List.of(new RecordingStep(1, store)), 3).run(store))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("no migration step from schema 2");
        assertThat(store.events).isEmpty();
        assertThat(store.version).isEqualTo(1);
    }

    @Test
    void aCurrentStoreTakesNoCheckpoint() {
        FakeStore store = new FakeStore(3);

        int version = new StoreMigrationRunner(List.of(new RecordingStep(1, store), new RecordingStep(2, store)), 3, null, 3, CLOCK)
                .run(store);

        assertThat(version).isEqualTo(3);
        assertThat(store.events).isEmpty();
    }

    @Test
    void twoStepsFromOneVersionFailStartup() {
        FakeStore store = new FakeStore(1);

        assertThatThrownBy(() -> runner(List.of(new RecordingStep(1, store), new RecordingStep(1, store)), 2).run(store))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("from schema 1");
        assertThat(store.events).isEmpty();
    }

    @Test
    void aStepFromTheCurrentSchemaOrLaterFailsStartupEvenOnACurrentStore() {
        for (int fromVersion : new int[]{2, 3}) {
            FakeStore store = new FakeStore(2);

            assertThatThrownBy(() -> runner(List.of(new RecordingStep(1, store), new RecordingStep(fromVersion, store)), 2).run(store))
                    .isInstanceOf(StoreException.class)
                    .hasMessageContaining(RecordingStep.class.getName())
                    .hasMessageContaining("from schema " + fromVersion);
            assertThat(store.events).isEmpty();
        }
    }

    @Test
    void aStepFromBelowSchemaOneFailsStartup() {
        FakeStore store = new FakeStore(1);

        assertThatThrownBy(() -> runner(List.of(new RecordingStep(0, store), new RecordingStep(1, store)), 2).run(store))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("from schema 0");
        assertThat(store.events).isEmpty();
    }

    @Test
    void aMigrationWithoutABackupsPathFailsNamingTheSetting() {
        for (String backupsPath : new String[]{null, " "}) {
            FakeStore store = new FakeStore(1);

            assertThatThrownBy(() -> new StoreMigrationRunner(List.of(new RecordingStep(1, store)), 2, backupsPath, 3, CLOCK).run(store))
                    .isInstanceOf(StoreException.class)
                    .hasMessageContaining("store.backups.path")
                    .hasMessageContaining(store.path());
            assertThat(store.events).isEmpty();
            assertThat(store.version).isEqualTo(1);
        }
    }

    @Test
    void aFailedCheckpointFailsStartupBeforeAnyStep() {
        FakeStore store = new FakeStore(1);
        store.checkpointFailure = new StoreException("disk full");

        assertThatThrownBy(() -> runner(List.of(new RecordingStep(1, store)), 2).run(store))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("disk full");
        assertThat(store.events).isEmpty();
        assertThat(store.version).isEqualTo(1);
    }

    @Test
    void theOldestCheckpointsBeyondKeepAreDeletedAfterTheLastBump() throws Exception {
        Path old = Files.createDirectories(backups().resolve("pre-upgrade-0-1"));
        Files.setLastModifiedTime(old, FileTime.from(Instant.parse("2026-01-01T00:00:00Z")));
        FakeStore store = new FakeStore(1);

        new StoreMigrationRunner(List.of(new RecordingStep(1, store)), 2, backups().toString(), 1, CLOCK).run(store);

        assertThat(old).doesNotExist();
        assertThat(backups().resolve("pre-upgrade-1-2")).isDirectory();
    }

    @Test
    void theCheckpointOfThisRunIsKeptEvenWhenItLooksOlderThanTheOthers() throws Exception {
        // a newer modification time than the one the checkpoint of this run gets
        Path other = Files.createDirectories(backups().resolve("pre-upgrade-0-1"));
        Files.setLastModifiedTime(other, FileTime.from(Instant.now().plusSeconds(365L * 24 * 3600)));
        FakeStore store = new FakeStore(1);

        new StoreMigrationRunner(List.of(new RecordingStep(1, store)), 2, backups().toString(), 1, CLOCK).run(store);

        assertThat(backups().resolve("pre-upgrade-1-2")).isDirectory();
        assertThat(other).doesNotExist();
    }

    @Test
    void aFailedRetentionAfterTheLastBumpIsLoggedAndStartupContinues() throws Exception {
        Path old = Files.createDirectories(backups().resolve("pre-upgrade-0-1"));
        Path locked = Files.createDirectories(old.resolve("locked"));
        Files.writeString(locked.resolve("CURRENT"), "x");
        Files.setLastModifiedTime(old, FileTime.from(Instant.parse("2026-01-01T00:00:00Z")));
        assertThat(locked.toFile().setWritable(false)).isTrue();
        try {
            assumeFalse(Files.isWritable(locked), "running as a user that ignores directory permissions");
            FakeStore store = new FakeStore(1);

            int version = new StoreMigrationRunner(List.of(new RecordingStep(1, store)), 2, backups().toString(), 1, CLOCK).run(store);

            assertThat(version).isEqualTo(2);
            assertThat(store.version).isEqualTo(2);
            assertThat(logged.list).filteredOn(event -> event.getLevel() == Level.WARN)
                    .singleElement()
                    .satisfies(event -> assertThat(event.getFormattedMessage()).contains(backups().toString()));
        } finally {
            locked.toFile().setWritable(true);
        }
    }

    @Test
    void theStartTheCheckpointAndEveryStepAreLoggedAtInfo() {
        FakeStore store = new FakeStore(1);

        runner(List.of(new RecordingStep(1, store), new RecordingStep(2, store)), 3).run(store);

        List<String> info = logged.list.stream()
                .filter(event -> event.getLevel() == Level.INFO && event.getLoggerName().equals(StoreMigrationRunner.class.getName()))
                .map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(info).anySatisfy(message -> assertThat(message).contains(store.path()).contains("schema 1").contains("schema 3"));
        assertThat(info).anySatisfy(message -> assertThat(message).contains(backups().resolve("pre-upgrade-1-3").toString()));
        assertThat(info).anySatisfy(message -> assertThat(message).contains("schema 1 to 2"));
        assertThat(info).anySatisfy(message -> assertThat(message).contains("schema 2 to 3"));
    }

    private static void deleteDir(Path path) {
        try {
            Files.delete(path);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private StoreMigrationRunner runner(List<StoreMigrationStep> steps, int targetVersion) {
        return new StoreMigrationRunner(steps, targetVersion, backups().toString(), 3, CLOCK);
    }

    private Path backups() {
        return dir.resolve("backups");
    }

    private static String key(StoreColumnFamily cf, byte[] key) {
        return cf.columnFamilyName() + "/" + HexFormat.of().formatHex(key);
    }

    /**
     * A step that records its run in the store's event log and writes a {@code step-<from>} key to {@code meta}.
     */
    private static class RecordingStep implements StoreMigrationStep {

        private final int fromVersion;
        private final FakeStore store;

        RecordingStep(int fromVersion, FakeStore store) {
            this.fromVersion = fromVersion;
            this.store = store;
        }

        @Override
        public int fromVersion() {
            return fromVersion;
        }

        @Override
        public void apply(MigrationContext ctx) {
            store.events.add("step " + fromVersion);
            ctx.put(StoreColumnFamily.META, RawStores.bytes("step-" + fromVersion), new byte[]{1});
        }

    }

    private record Checkpoint(Path dir, int version, Map<String, byte[]> data) {
    }

    /**
     * A store in memory: its version, its data, a log of the runner's calls and a copy of the data at each checkpoint.
     */
    private class FakeStore implements StoreMigrationRunner.Target, MigrationContext {

        private final List<String> events = new ArrayList<>();
        private final List<Checkpoint> checkpoints = new ArrayList<>();
        private final Map<String, byte[]> data = new HashMap<>();
        private int version;
        private RuntimeException checkpointFailure;

        FakeStore(int version) {
            this.version = version;
        }

        @Override
        public String path() {
            return dir.resolve("store").toString();
        }

        @Override
        public int schemaVersion() {
            return version;
        }

        @Override
        public void bumpSchemaVersion(int version) {
            events.add("bump " + version);
            this.version = version;
        }

        @Override
        public void finishUpgrade(int version) {
            events.add("finish " + version);
            this.version = version;
            data.remove(key(StoreColumnFamily.META, UPGRADE_CHECKPOINT));
        }

        @Override
        public String upgradeCheckpoint() {
            byte[] name = data.get(key(StoreColumnFamily.META, UPGRADE_CHECKPOINT));
            return name == null ? null : new String(name, StandardCharsets.UTF_8);
        }

        @Override
        public void recordUpgradeCheckpoint(String name) {
            if (name == null) {
                events.add("unmark");
                data.remove(key(StoreColumnFamily.META, UPGRADE_CHECKPOINT));
            } else {
                events.add("mark " + name);
                data.put(key(StoreColumnFamily.META, UPGRADE_CHECKPOINT), name.getBytes(StandardCharsets.UTF_8));
            }
        }

        @Override
        public void checkpoint(Path target) {
            if (checkpointFailure != null) {
                throw checkpointFailure;
            }
            try {
                Files.createDirectories(target);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            events.add("checkpoint " + target.getFileName());
            checkpoints.add(new Checkpoint(target, version, new HashMap<>(data)));
        }

        @Override
        public MigrationContext context() {
            return this;
        }

        @Override
        public byte[] get(StoreColumnFamily cf, byte[] key) {
            return data.get(key(cf, key));
        }

        @Override
        public void put(StoreColumnFamily cf, byte[] key, byte[] value) {
            data.put(key(cf, key), value);
        }

        @Override
        public void delete(StoreColumnFamily cf, byte[] key) {
            data.remove(key(cf, key));
        }

        @Override
        public <T> T scan(StoreColumnFamily cf, byte[] lo, byte[] hi, Function<RocksIterator, T> reader) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void ensureJwtSigningKey() {
            events.add("ensure key");
        }

    }

}
