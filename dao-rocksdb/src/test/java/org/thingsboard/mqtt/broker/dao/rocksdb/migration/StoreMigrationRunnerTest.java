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
import org.thingsboard.mqtt.broker.dao.rocksdb.backup.CheckpointInfo;
import org.thingsboard.mqtt.broker.dao.rocksdb.backup.StoreCheckpoints;
import org.thingsboard.mqtt.broker.dao.store.StoreException;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
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
        assertThat(store.recordedCheckpoint()).isNull();
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
        // the failed step records the checkpoint again, with the sequence number after its writes
        assertThat(store.events).containsExactly("checkpoint pre-upgrade-1-3", "mark pre-upgrade-1-3", "step 1",
                "mark pre-upgrade-1-3");
        assertThat(store.get(StoreColumnFamily.META, RawStores.bytes("step-1-wrote"))).isEqualTo(new byte[]{1});
        assertThat(store.recordedCheckpoint()).isEqualTo("pre-upgrade-1-3");
        store.events.clear();

        assertThat(runner.run(store)).isEqualTo(3);

        // the rerun resumes with the checkpoint of the failed run
        assertThat(store.events).containsExactly("step 1", "bump 2", "step 2", "finish 3");
        assertThat(store.recordedCheckpoint()).isNull();
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
            assertThat(store.recordedCheckpoint()).isEqualTo("pre-upgrade-1-2");
        }

        assertThat(runner.run(store)).isEqualTo(2);

        assertThat(store.checkpoints).hasSize(1);
        Checkpoint checkpoint = store.checkpoints.get(0);
        assertThat(checkpoint.dir()).isEqualTo(backups().resolve("pre-upgrade-1-2"));
        assertThat(checkpoint.version()).isEqualTo(1);
        // the data before any step and no record of the upgrade
        assertThat(checkpoint.data()).containsOnlyKeys(key(StoreColumnFamily.CLIENT_SESSION, RawStores.bytes("c1")));
        assertThat(StoreCheckpoints.list(backups())).extracting(info -> info.path()).containsExactly(checkpoint.dir());
        assertThat(store.recordedCheckpoint()).isNull();
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
            store.recordUpgradeCheckpoint(recorded, recorded);
            store.events.clear();

            runner(List.of(new RecordingStep(1, store)), 2).run(store);

            Path taken = backups().resolve("pre-upgrade-1-2");
            assertThat(store.events).containsExactly("mark pre-upgrade-1-2", "checkpoint pre-upgrade-1-2", "step 1", "finish 2");
            // the new checkpoint holds the record naming itself instead of the stale one
            assertThat(store.checkpoints).singleElement().satisfies(checkpoint -> {
                assertThat(checkpoint.data()).containsOnlyKeys(key(StoreColumnFamily.META, UPGRADE_CHECKPOINT));
                assertHoldsItsOwnRecord(checkpoint, "pre-upgrade-1-2");
            });
            assertThat(store.recordedCheckpoint()).isNull();
            assertThat(logged.list).filteredOn(event -> event.getLevel() == Level.WARN)
                    .singleElement()
                    .satisfies(event -> assertThat(event.getFormattedMessage()).contains(recorded).contains(store.path()));
            assertThat(taken).isDirectory();
            StoreCheckpoints.list(backups()).forEach(info -> deleteDir(info.path()));
        }
    }

    @Test
    void aStoreWrittenByAnotherBuildSinceTheFailedUpgradeGetsANewCheckpointAndKeepsTheFirst() {
        FakeStore store = new FakeStore(1);
        List<StoreMigrationStep> steps = List.of(new FailingOnceStep(1, store));
        assertThatThrownBy(() -> runner(steps, 2).run(store)).isInstanceOf(StoreException.class);
        // the rolled-back older build ignores the record and takes writes
        store.put(StoreColumnFamily.CLIENT_SESSION, RawStores.bytes("by-the-older-build"), new byte[]{1});
        store.events.clear();
        logged.list.clear();

        assertThat(runner(steps, 2, 1).run(store)).isEqualTo(2);

        Path first = backups().resolve("pre-upgrade-1-2");
        Path fresh = backups().resolve("pre-upgrade-1-2-20261123-101530");
        assertThat(store.events).containsExactly("mark " + fresh.getFileName() + " from pre-upgrade-1-2",
                "checkpoint " + fresh.getFileName(), "step 1", "finish 2");
        // the new checkpoint holds the older build's write, and the record naming itself
        assertThat(store.checkpoints.get(1).data()).containsKey(key(StoreColumnFamily.CLIENT_SESSION, RawStores.bytes("by-the-older-build")));
        assertHoldsItsOwnRecord(store.checkpoints.get(1), "pre-upgrade-1-2");
        // with store.backups.keep=1 both stay: the first one until the upgrade finishes, and the one of this run
        assertThat(checkpointDirs()).containsExactlyInAnyOrder(first, fresh);
        assertThat(store.recordedCheckpoint()).isNull();
        assertThat(logged.list).filteredOn(event -> event.getLevel() == Level.WARN)
                .singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage()).contains("was written since its unfinished upgrade")
                        .contains(store.path()).contains("the first pre-upgrade checkpoint " + first + " is kept"));
    }

    @Test
    void aProcessKilledInTheMiddleOfAStepTakesANewCheckpointOnTheNextStartAndKeepsTheFirst() {
        FakeStore store = new FakeStore(1);
        KilledStep step = new KilledStep(1, store, 1);
        startKilled(store, List.of(step), 2);
        // the step wrote after the record, and the record was not written again
        assertThat(store.recordedCheckpoint()).isEqualTo("pre-upgrade-1-2");
        assertThat(store.version).isEqualTo(1);
        store.events.clear();

        assertThat(runner(List.of(step), 2, 1).run(store)).isEqualTo(2);

        Path first = backups().resolve("pre-upgrade-1-2");
        Path fresh = backups().resolve("pre-upgrade-1-2-20261123-101530");
        assertThat(store.events).containsExactly("mark " + fresh.getFileName() + " from pre-upgrade-1-2",
                "checkpoint " + fresh.getFileName(), "step 1", "finish 2");
        assertThat(checkpointDirs()).containsExactlyInAnyOrder(first, fresh);
        assertThat(logged.list).filteredOn(event -> event.getLevel() == Level.WARN)
                .singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage()).contains("interrupted mid-step")
                        .contains("the first pre-upgrade checkpoint " + first + " is kept"));
    }

    @Test
    void aStartKilledWhileTakingTheNewCheckpointKeepsTheFirstOneOnTheNextStart() {
        FakeStore store = new FakeStore(1);
        KilledStep step = new KilledStep(1, store, 1);
        startKilled(store, List.of(step), 2);
        store.killAtCheckpoint = CheckpointKill.DURING;
        startKilled(store, List.of(step), 2);
        Path first = backups().resolve("pre-upgrade-1-2");
        Path fresh = backups().resolve("pre-upgrade-1-2-20261123-101530");
        // the record names the new checkpoint, which was never completed
        assertThat(store.recordedCheckpoint()).isEqualTo(fresh.getFileName().toString());
        assertThat(fresh).doesNotExist();
        store.events.clear();
        logged.list.clear();

        assertThat(runner(List.of(step), 2, 1).run(store)).isEqualTo(2);

        // the name is free again, and the first checkpoint is still the origin
        assertThat(store.events).containsExactly("mark " + fresh.getFileName() + " from pre-upgrade-1-2",
                "checkpoint " + fresh.getFileName(), "step 1", "finish 2");
        assertThat(checkpointDirs()).containsExactlyInAnyOrder(first, fresh);
        assertThat(logged.list).filteredOn(event -> event.getLevel() == Level.WARN)
                .singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage()).contains("is gone")
                        .contains("the first pre-upgrade checkpoint " + first + " is kept"));
    }

    @Test
    void aStartKilledRightAfterTheNewCheckpointResumesWithItOnTheNextStart() {
        FakeStore store = new FakeStore(1);
        KilledStep step = new KilledStep(1, store, 1);
        startKilled(store, List.of(step), 2);
        store.killAtCheckpoint = CheckpointKill.AFTER;
        startKilled(store, List.of(step), 2);
        Path first = backups().resolve("pre-upgrade-1-2");
        Path fresh = backups().resolve("pre-upgrade-1-2-20261123-101530");
        store.events.clear();
        logged.list.clear();

        assertThat(runner(List.of(step), 2, 1).run(store)).isEqualTo(2);

        // no third checkpoint, and the first one is kept until the upgrade finishes
        assertThat(store.events).containsExactly("step 1", "finish 2");
        assertThat(store.checkpoints).extracting(Checkpoint::dir).containsExactly(first, fresh);
        assertHoldsItsOwnRecord(store.checkpoints.get(1), "pre-upgrade-1-2");
        assertThat(checkpointDirs()).containsExactlyInAnyOrder(first, fresh);
        assertThat(logged.list).filteredOn(event -> event.getLevel() == Level.WARN).isEmpty();
        assertThat(logged.list).filteredOn(event -> event.getLevel() == Level.INFO)
                .anySatisfy(event -> assertThat(event.getFormattedMessage()).contains("Resuming the unfinished upgrade")
                        .contains(fresh.toString()));
    }

    @Test
    void theFirstCheckpointOutlivesAnyNumberOfStartsKilledMidStep() {
        for (int keep : new int[]{1, 3}) {
            Path backups = dir.resolve("backups-keep-" + keep);
            FakeStore store = new FakeStore(1);
            store.put(StoreColumnFamily.CLIENT_SESSION, RawStores.bytes("c1"), RawStores.bytes("session"));
            KilledStep step = new KilledStep(1, store, 3);
            for (int start = 0; start < 3; start++) {
                startKilled(store, List.of(step), 2, backups);
            }

            assertThat(new StoreMigrationRunner(List.of(step), 2, backups.toString(), keep, CLOCK).run(store)).isEqualTo(2);

            assertThat(store.checkpoints).hasSize(4);
            Checkpoint first = store.checkpoints.get(0);
            Path run = store.checkpoints.get(3).dir();
            assertThat(first.dir()).isEqualTo(backups.resolve("pre-upgrade-1-2"));
            // the store before any step
            assertThat(first.version()).isEqualTo(1);
            assertThat(first.data()).containsOnlyKeys(key(StoreColumnFamily.CLIENT_SESSION, RawStores.bytes("c1")));
            assertThat(StoreCheckpoints.list(backups)).extracting(CheckpointInfo::path)
                    .as("store.backups.keep=%d", keep).contains(first.dir(), run).hasSize(keep == 1 ? 2 : 3);
            // every start after the first carried it forward
            assertThat(store.events).filteredOn(event -> event.startsWith("mark "))
                    .hasSize(4)
                    .allSatisfy(event -> assertThat(event).matches("mark pre-upgrade-1-2|mark .* from pre-upgrade-1-2"));
        }
    }

    @Test
    void anUpgradeKilledInItsSecondStepKeepsItsFirstCheckpointAtTheOldestSchema() {
        FakeStore store = new FakeStore(1);
        List<StoreMigrationStep> steps = List.of(new RecordingStep(1, store), new KilledStep(2, store, 1));
        startKilled(store, steps, 3);
        assertThat(store.version).isEqualTo(2);

        assertThat(runner(steps, 3, 1).run(store)).isEqualTo(3);

        Path first = backups().resolve("pre-upgrade-1-3");
        Path fresh = backups().resolve("pre-upgrade-2-3");
        assertThat(store.checkpoints).extracting(Checkpoint::dir).containsExactly(first, fresh);
        // the first one opens with the schema 1 build, for a rollback
        assertThat(store.checkpoints.get(0).version()).isEqualTo(1);
        assertThat(checkpointDirs()).containsExactlyInAnyOrder(first, fresh);
    }

    @Test
    void aFirstCheckpointDeletedByHandIsReplacedByTheNewCheckpoint() {
        FakeStore store = new FakeStore(1);
        KilledStep step = new KilledStep(1, store, 2);
        startKilled(store, List.of(step), 2);
        startKilled(store, List.of(step), 2);
        Path first = backups().resolve("pre-upgrade-1-2");
        Path second = backups().resolve("pre-upgrade-1-2-20261123-101530");
        assertThat(store.recordedCheckpoint()).isEqualTo(second.getFileName().toString());
        deleteDir(first);
        store.events.clear();
        logged.list.clear();

        assertThat(runner(List.of(step), 2, 1).run(store)).isEqualTo(2);

        // the new checkpoint takes the free name and is the first one now; the second is an ordinary checkpoint
        assertThat(store.events).containsExactly("mark pre-upgrade-1-2", "checkpoint pre-upgrade-1-2", "step 1", "finish 2");
        assertThat(checkpointDirs()).containsExactly(first);
        assertThat(logged.list).filteredOn(event -> event.getLevel() == Level.WARN)
                .singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage()).contains("interrupted mid-step")
                        .contains("is gone, so the new checkpoint is kept as the first one"));
    }

    @Test
    void aFirstCheckpointTheStoreRunsFromIsNotCarriedAsTheOrigin() throws Exception {
        Path running = Files.createDirectories(backups().resolve("pre-upgrade-1-2"));
        Path resume = Files.createDirectories(backups().resolve("pre-upgrade-1-2-20260101-000000"));
        FakeStore store = new FakeStore(1);
        store.path = running.toString();
        store.recordUpgradeCheckpoint(resume.getFileName().toString(), running.getFileName().toString());
        store.events.clear();

        assertThatThrownBy(() -> runner(List.of(new FailingOnceStep(1, store)), 2).run(store)).isInstanceOf(StoreException.class);

        // resumed, and recorded again with the resumed checkpoint as the origin
        assertThat(store.events).containsExactly("step 1", "mark " + resume.getFileName());
    }

    @Test
    void aStepThatThrowsAnErrorRecordsTheUpgradeAgainAndTheNextStartResumes() {
        FakeStore store = new FakeStore(1);
        AssertionError error = new AssertionError("boom");
        AtomicInteger runs = new AtomicInteger();
        StoreMigrationStep failingOnce = new RecordingStep(1, store) {
            @Override
            public void apply(MigrationContext ctx) {
                super.apply(ctx);
                if (runs.incrementAndGet() == 1) {
                    throw error;
                }
            }
        };
        StoreMigrationRunner runner = runner(List.of(failingOnce), 2);

        assertThatThrownBy(() -> runner.run(store)).isSameAs(error);

        assertThat(store.events).containsExactly("checkpoint pre-upgrade-1-2", "mark pre-upgrade-1-2", "step 1", "mark pre-upgrade-1-2");
        store.events.clear();

        assertThat(runner.run(store)).isEqualTo(2);

        assertThat(store.events).containsExactly("step 1", "finish 2");
        assertThat(store.checkpoints).extracting(Checkpoint::dir).containsExactly(backups().resolve("pre-upgrade-1-2"));
        assertThat(logged.list).filteredOn(event -> event.getLevel() == Level.INFO
                && event.getFormattedMessage().contains("Resuming the unfinished upgrade")).hasSize(1);
    }

    @Test
    void anErrorStaysTheSameInstanceWhenRecordingAgainFails() {
        AssertionError error = new AssertionError("boom");
        StoreException other = new StoreException("disk full");
        // a JVM can throw one preallocated OutOfMemoryError again, and a throwable cannot suppress itself
        for (Throwable recordFailure : List.<Throwable>of(error, other)) {
            FakeStore store = new FakeStore(1);
            StoreMigrationStep failing = new RecordingStep(1, store) {
                @Override
                public void apply(MigrationContext ctx) {
                    super.apply(ctx);
                    store.recordFailure = recordFailure;
                    throw error;
                }
            };

            assertThatThrownBy(() -> runner(List.of(failing), 2).run(store)).isSameAs(error);

            if (recordFailure == error) {
                assertThat(error.getSuppressed()).isEmpty();
            } else {
                assertThat(error.getSuppressed()).containsExactly(other);
            }
            StoreCheckpoints.list(backups()).forEach(info -> deleteDir(info.path()));
        }
    }

    @Test
    void aFailedRecordAfterAFailedStepIsSuppressedAndTheNextStartTakesANewCheckpoint() {
        FakeStore store = new FakeStore(1);
        StoreException recordFailure = new StoreException("disk full");
        AtomicInteger runs = new AtomicInteger();
        StoreMigrationStep failingOnce = new RecordingStep(1, store) {
            @Override
            public void apply(MigrationContext ctx) {
                super.apply(ctx);
                if (runs.incrementAndGet() == 1) {
                    store.recordFailure = recordFailure;
                    throw new IllegalStateException("boom");
                }
            }
        };
        StoreMigrationRunner runner = runner(List.of(failingOnce), 2);

        assertThatThrownBy(() -> runner.run(store))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("Migration from schema 1 to 2 failed: boom")
                .satisfies(e -> assertThat(e.getSuppressed()).containsExactly(recordFailure));
        store.recordFailure = null;
        store.events.clear();
        logged.list.clear();

        assertThat(runner.run(store)).isEqualTo(2);

        assertThat(store.checkpoints).hasSize(2);
        assertThat(logged.list).filteredOn(event -> event.getLevel() == Level.WARN)
                .singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage()).contains("was written since its unfinished upgrade"));
    }

    @Test
    void aStartAfterAnIntermediateBumpResumesWithTheCheckpoint() {
        FakeStore store = new FakeStore(1);
        AtomicInteger runs = new AtomicInteger();
        StoreMigrationStep killedBeforeWriting = new RecordingStep(2, store) {
            @Override
            public void apply(MigrationContext ctx) {
                if (runs.incrementAndGet() == 1) {
                    store.kill();
                }
                super.apply(ctx);
            }
        };
        List<StoreMigrationStep> steps = List.of(new RecordingStep(1, store), killedBeforeWriting);
        startKilled(store, steps, 3);
        assertThat(store.version).isEqualTo(2);
        store.events.clear();

        assertThat(runner(steps, 3).run(store)).isEqualTo(3);

        // the bump recorded the checkpoint with its own sequence number
        assertThat(store.events).containsExactly("step 2", "finish 3");
        assertThat(store.checkpoints).extracting(Checkpoint::dir).containsExactly(backups().resolve("pre-upgrade-1-3"));
    }

    @Test
    void aRecordLeftOnACurrentStoreIsRemovedWithoutACheckpoint() {
        FakeStore store = new FakeStore(2);
        // a later build's unfinished upgrade, then a rollback to this build
        store.recordUpgradeCheckpoint("pre-upgrade-2-3", "pre-upgrade-2-3");
        store.events.clear();

        int version = new StoreMigrationRunner(List.of(new RecordingStep(1, store)), 2, null, 3, CLOCK).run(store);

        assertThat(version).isEqualTo(2);
        assertThat(store.events).containsExactly("unmark");
        assertThat(store.upgradeRecord()).isNull();
        assertThat(store.checkpoints).isEmpty();
        assertThat(logged.list).filteredOn(event -> event.getLevel().isGreaterOrEqual(Level.WARN)).isEmpty();
        assertThat(logged.list).filteredOn(event -> event.getLevel() == Level.INFO)
                .singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage()).contains("Removed the record")
                        .contains(store.path()).contains("pre-upgrade-2-3"));
    }

    @Test
    void aMalformedRecordIsReplacedByANewCheckpointWithAWarning() {
        byte[] withoutSeparator = new StoreMigrationRunner.UpgradeRecord(1, "pre-upgrade-1-2", "x").encode();
        withoutSeparator = Arrays.copyOf(withoutSeparator, withoutSeparator.length - 2);
        for (byte[] recorded : List.of(new byte[0], new byte[Long.BYTES], RawStores.bytes("short"), withoutSeparator)) {
            logged.list.clear();
            FakeStore store = new FakeStore(1);
            store.put(StoreColumnFamily.META, UPGRADE_CHECKPOINT, recorded);

            runner(List.of(new RecordingStep(1, store)), 2).run(store);

            assertThat(store.events).containsExactly("mark pre-upgrade-1-2", "checkpoint pre-upgrade-1-2", "step 1", "finish 2");
            assertThat(store.checkpoints).singleElement().satisfies(checkpoint -> {
                assertThat(checkpoint.data()).containsOnlyKeys(key(StoreColumnFamily.META, UPGRADE_CHECKPOINT));
                assertHoldsItsOwnRecord(checkpoint, "pre-upgrade-1-2");
            });
            assertThat(logged.list).filteredOn(event -> event.getLevel() == Level.WARN)
                    .singleElement()
                    .satisfies(event -> assertThat(event.getFormattedMessage()).contains("malformed").contains(store.path()));
            StoreCheckpoints.list(backups()).forEach(info -> deleteDir(info.path()));
        }
    }

    @Test
    void theRecordLayoutIsTheSequenceNumberTheCheckpointASeparatorAndTheOrigin() {
        StoreMigrationRunner.UpgradeRecord record = new StoreMigrationRunner.UpgradeRecord(0x0102030405060708L,
                "pre-upgrade-1-2-20261123-101530", "pre-upgrade-1-2");

        byte[] encoded = record.encode();

        assertThat(encoded).isEqualTo(concat(new byte[]{1, 2, 3, 4, 5, 6, 7, 8}, RawStores.bytes("pre-upgrade-1-2-20261123-101530"),
                new byte[]{0}, RawStores.bytes("pre-upgrade-1-2")));
        assertThat(StoreMigrationRunner.UpgradeRecord.decode(encoded)).isEqualTo(record);
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
        return runner(steps, targetVersion, 3);
    }

    private StoreMigrationRunner runner(List<StoreMigrationStep> steps, int targetVersion, int keep) {
        return new StoreMigrationRunner(steps, targetVersion, backups().toString(), keep, CLOCK);
    }

    private void startKilled(FakeStore store, List<StoreMigrationStep> steps, int targetVersion) {
        startKilled(store, steps, targetVersion, backups());
    }

    /**
     * A start that one of {@code steps} kills with {@link FakeStore#kill()}: the runner sees no throwable, nothing it
     * writes after the kill is kept, and the restarted store is as the kill left it. The retention of the killed start
     * deletes nothing, as a killed process does not get there.
     */
    private void startKilled(FakeStore store, List<StoreMigrationStep> steps, int targetVersion, Path backups) {
        new StoreMigrationRunner(steps, targetVersion, backups.toString(), Integer.MAX_VALUE, CLOCK).run(store);
        assertThat(store.dead).as("killed by a step").isTrue();
        store.dead = false;
    }

    private Path backups() {
        return dir.resolve("backups");
    }

    private List<Path> checkpointDirs() {
        return StoreCheckpoints.list(backups()).stream().map(CheckpointInfo::path).toList();
    }

    /**
     * {@code checkpoint} holds the record of the upgrade naming itself, with {@code origin}, at its own sequence number.
     */
    private static void assertHoldsItsOwnRecord(Checkpoint checkpoint, String origin) {
        assertThat(checkpoint.record()).isEqualTo(new StoreMigrationRunner.UpgradeRecord(checkpoint.sequence(),
                checkpoint.dir().getFileName().toString(), origin));
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
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

    /**
     * A recording step that writes and then throws on its first run.
     */
    private static class FailingOnceStep extends RecordingStep {

        private final AtomicInteger runs = new AtomicInteger();

        FailingOnceStep(int fromVersion, FakeStore store) {
            super(fromVersion, store);
        }

        @Override
        public void apply(MigrationContext ctx) {
            super.apply(ctx);
            if (runs.incrementAndGet() == 1) {
                throw new IllegalStateException("boom");
            }
        }

    }

    /**
     * A recording step whose first {@code kills} runs kill the process right after the step's write.
     */
    private static class KilledStep extends RecordingStep {

        private final FakeStore store;
        private int kills;

        KilledStep(int fromVersion, FakeStore store, int kills) {
            super(fromVersion, store);
            this.store = store;
            this.kills = kills;
        }

        @Override
        public void apply(MigrationContext ctx) {
            super.apply(ctx);
            if (kills > 0) {
                kills--;
                store.kill();
            }
        }

    }

    /**
     * A checkpoint the fake took: its directory, and the version, sequence number and data of the store then.
     */
    private record Checkpoint(Path dir, int version, long sequence, Map<String, byte[]> data) {

        StoreMigrationRunner.UpgradeRecord record() {
            byte[] value = data.get(key(StoreColumnFamily.META, UPGRADE_CHECKPOINT));
            return value == null ? null : StoreMigrationRunner.UpgradeRecord.decode(value);
        }

    }

    /**
     * Where a checkpoint kills the process: while it is taken, leaving only RocksDB's staging directory, or right after.
     */
    private enum CheckpointKill {
        DURING, AFTER
    }

    /**
     * A store in memory: its version, its data, a sequence number that every write advances, a log of the runner's
     * calls and a copy of the data at each checkpoint. A killed store drops every write until it is restarted.
     */
    private class FakeStore implements StoreMigrationRunner.Target, MigrationContext {

        private final List<String> events = new ArrayList<>();
        private final List<Checkpoint> checkpoints = new ArrayList<>();
        private final Map<String, byte[]> data = new HashMap<>();
        private int version;
        private long sequence;
        private String path = dir.resolve("store").toString();
        private boolean dead;
        private CheckpointKill killAtCheckpoint;
        private RuntimeException checkpointFailure;
        private Throwable recordFailure;

        FakeStore(int version) {
            this.version = version;
        }

        /**
         * The process stops here: what was written stays, and nothing written afterwards is kept.
         */
        void kill() {
            dead = true;
        }

        @Override
        public String path() {
            return path;
        }

        @Override
        public int schemaVersion() {
            return version;
        }

        @Override
        public long latestSequenceNumber() {
            return sequence;
        }

        @Override
        public void bumpSchemaVersion(int version, String checkpoint, String origin) {
            if (dead) {
                return;
            }
            events.add("bump " + version);
            this.version = version;
            sequence += 2;
            data.put(key(StoreColumnFamily.META, UPGRADE_CHECKPOINT),
                    new StoreMigrationRunner.UpgradeRecord(sequence, checkpoint, origin).encode());
        }

        @Override
        public void finishUpgrade(int version) {
            if (dead) {
                return;
            }
            events.add("finish " + version);
            this.version = version;
            sequence += 2;
            data.remove(key(StoreColumnFamily.META, UPGRADE_CHECKPOINT));
        }

        @Override
        public byte[] upgradeRecord() {
            return data.get(key(StoreColumnFamily.META, UPGRADE_CHECKPOINT));
        }

        @Override
        public void recordUpgradeCheckpoint(String checkpoint, String origin) {
            if (dead) {
                return;
            }
            if (recordFailure instanceof RuntimeException e) {
                throw e;
            }
            if (recordFailure instanceof Error e) {
                throw e;
            }
            events.add("mark " + checkpoint + (origin.equals(checkpoint) ? "" : " from " + origin));
            sequence++;
            data.put(key(StoreColumnFamily.META, UPGRADE_CHECKPOINT),
                    new StoreMigrationRunner.UpgradeRecord(sequence, checkpoint, origin).encode());
        }

        @Override
        public void removeUpgradeRecord() {
            if (dead) {
                return;
            }
            events.add("unmark");
            sequence++;
            data.remove(key(StoreColumnFamily.META, UPGRADE_CHECKPOINT));
        }

        String recordedCheckpoint() {
            byte[] record = upgradeRecord();
            return record == null ? null : StoreMigrationRunner.UpgradeRecord.decode(record).checkpoint();
        }

        @Override
        public void checkpoint(Path target) {
            assertThat(dead).as("a killed process takes no checkpoint").isFalse();
            if (checkpointFailure != null) {
                throw checkpointFailure;
            }
            CheckpointKill kill = killAtCheckpoint;
            killAtCheckpoint = null;
            try {
                if (kill == CheckpointKill.DURING) {
                    Files.createDirectories(target.resolveSibling(target.getFileName() + ".tmp"));
                    kill();
                    return;
                }
                Files.createDirectories(target);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            events.add("checkpoint " + target.getFileName());
            checkpoints.add(new Checkpoint(target, version, sequence, new HashMap<>(data)));
            if (kill == CheckpointKill.AFTER) {
                kill();
            }
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
            if (dead) {
                return;
            }
            sequence++;
            data.put(key(cf, key), value);
        }

        @Override
        public void delete(StoreColumnFamily cf, byte[] key) {
            if (dead) {
                return;
            }
            sequence++;
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
