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

import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.dao.rocksdb.backup.StoreCheckpoints;
import org.thingsboard.mqtt.broker.dao.store.StoreException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Brings a store from its stored schema version up to the version of this build. A store already at that version is
 * left alone, but for a record of an unfinished upgrade to a later schema, which is removed. An older one gets one
 * checkpoint {@code pre-upgrade-<from>-<to>} under {@code store.backups.path} before any step runs, and the name of that
 * checkpoint is recorded in the store's meta; then each step runs in version order and the stored version is bumped
 * after each, so a store from several versions back runs every step and a crash before a bump reruns only that step.
 * The last bump removes the record in the same write. After it the oldest checkpoints beyond {@code store.backups.keep}
 * are deleted; the checkpoint of this run, the first checkpoint of the upgrade and a checkpoint directory the store runs
 * from are always kept.
 * <p>
 * An upgrade that failed or crashed leaves the record behind, and the next open resumes with the recorded checkpoint
 * instead of taking a new one: the store may already hold some of a step's writes by then, so only the first
 * checkpoint is a copy of the store before the upgrade, and repeated failed starts add no checkpoints. It resumes only
 * when the store is provably unchanged since the record was last written: the record carries the latest sequence
 * number of the store, which every write advances, as of its own write (right after the checkpoint, with every
 * intermediate bump, and when a step throws), and the store must still be at it. So a step that throws resumes with
 * the first checkpoint on the next start, while a process killed in the middle of a step takes a new checkpoint on the
 * next start: it cannot prove the store unchanged. A new checkpoint is also taken when another build wrote to the store
 * since (an older build ignores the record), when the recorded checkpoint is gone and when the record is malformed.
 * <p>
 * The record also names the origin of the upgrade, its first pre-upgrade checkpoint, and a start that takes a new
 * checkpoint carries the origin forward while it is still there. Retention keeps the origin and the checkpoint of the
 * run whatever {@code store.backups.keep} says, so the only copy of the store from before the upgrade outlives any
 * number of starts that could not resume; a checkpoint taken by one of those starts is an ordinary checkpoint once a
 * later start replaces it.
 * <p>
 * A step outside the schemas this build migrates, two steps from one schema, a missing step, a missing backups path,
 * a failed checkpoint and a failed step stop the open; the store stays at the last version reached. A failed deletion
 * of old checkpoints is only logged: the store is migrated by then.
 */
@Slf4j
public class StoreMigrationRunner {

    /**
     * The store being opened, as the runner needs it.
     */
    public interface Target {

        /**
         * The store directory, for messages.
         */
        String path();

        int schemaVersion();

        /**
         * The latest sequence number of the store: every write advances it, so an equal number means an unchanged
         * store.
         */
        long latestSequenceNumber();

        /**
         * Writes {@code version} as the stored schema version and the record of the upgrade in progress, as
         * {@link #recordUpgradeCheckpoint} does, in one write with a WAL sync.
         */
        void bumpSchemaVersion(int version, String checkpoint, String origin);

        /**
         * Writes {@code version} as the stored schema version and removes the record of the upgrade, in one write with
         * a WAL sync.
         */
        void finishUpgrade(int version);

        /**
         * The stored record of an unfinished upgrade, as {@link UpgradeRecord#encode()} lays it out, or {@code null}.
         */
        byte[] upgradeRecord();

        /**
         * Records {@code checkpoint} as the checkpoint to resume the upgrade in progress with and {@code origin} as its
         * first pre-upgrade checkpoint, with the latest sequence number of the store right after this write, with a
         * WAL sync.
         */
        void recordUpgradeCheckpoint(String checkpoint, String origin);

        /**
         * Removes the record of an unfinished upgrade, with a WAL sync.
         */
        void removeUpgradeRecord();

        /**
         * Writes a checkpoint of the store into {@code dir}, which does not exist.
         */
        void checkpoint(Path dir);

        MigrationContext context();

    }

    /**
     * The record of an unfinished upgrade: the latest sequence number of the store right after the record was written,
     * the directory name of the checkpoint to resume the upgrade with, and the directory name of the upgrade's origin,
     * its first pre-upgrade checkpoint, which is the same name while the upgrade has only one. Stored as the 8-byte
     * big-endian sequence number, the UTF-8 checkpoint name, one 0x00 byte and the UTF-8 origin name.
     */
    public record UpgradeRecord(long sequenceNumber, String checkpoint, String origin) {

        private static final byte SEPARATOR = 0;

        public byte[] encode() {
            byte[] name = checkpoint.getBytes(StandardCharsets.UTF_8);
            byte[] originName = origin.getBytes(StandardCharsets.UTF_8);
            return ByteBuffer.allocate(Long.BYTES + name.length + 1 + originName.length)
                    .putLong(sequenceNumber).put(name).put(SEPARATOR).put(originName).array();
        }

        /**
         * The record stored as {@code value}, or {@code null} when it is too short to hold a sequence number or has no
         * separator after it.
         */
        public static UpgradeRecord decode(byte[] value) {
            if (value.length <= Long.BYTES) {
                return null;
            }
            int separator = Long.BYTES;
            while (separator < value.length && value[separator] != SEPARATOR) {
                separator++;
            }
            if (separator == value.length) {
                return null;
            }
            return new UpgradeRecord(ByteBuffer.wrap(value).getLong(),
                    new String(value, Long.BYTES, separator - Long.BYTES, StandardCharsets.UTF_8),
                    new String(value, separator + 1, value.length - separator - 1, StandardCharsets.UTF_8));
        }

    }

    /**
     * How a start goes on with an upgrade: the checkpoint to resume with, or {@code null} to take a new one, and the
     * origin of the upgrade, or {@code null} when the new checkpoint is the origin.
     */
    private record Checkpoints(Path resume, Path origin) {
    }

    private final List<StoreMigrationStep> steps;
    private final int targetVersion;
    private final String backupsPath;
    private final int keep;
    private final Clock clock;

    public StoreMigrationRunner(List<StoreMigrationStep> steps, int targetVersion, String backupsPath, int keep, Clock clock) {
        this.steps = List.copyOf(steps);
        this.targetVersion = targetVersion;
        this.backupsPath = backupsPath;
        this.keep = keep;
        this.clock = clock;
    }

    /**
     * Migrates {@code target} and returns the schema version it is at afterwards.
     */
    public int run(Target target) {
        Map<Integer, StoreMigrationStep> byVersion = stepsByVersion(target);
        int from = target.schemaVersion();
        if (from >= targetVersion) {
            removeLeftoverRecord(target, from);
            return from;
        }
        for (int version = from; version < targetVersion; version++) {
            if (!byVersion.containsKey(version)) {
                throw new StoreException("Cannot migrate the store at " + target.path() + " from schema " + from + " to "
                        + targetVersion + ": no migration step from schema " + version);
            }
        }
        if (backupsPath == null || backupsPath.isBlank()) {
            throw new StoreException("Cannot migrate the store at " + target.path() + " from schema " + from + " to "
                    + targetVersion + ": store.backups.path is not set");
        }
        log.info("Migrating the store at {} from schema {} to schema {}", target.path(), from, targetVersion);
        Path backups = Path.of(backupsPath);
        Checkpoints checkpoints = checkpoints(target, backups);
        Path checkpoint = checkpoints.resume();
        Path origin = checkpoints.origin();
        if (checkpoint == null) {
            checkpoint = StoreCheckpoints.preUpgradeDir(backups, from, targetVersion, clock);
            target.checkpoint(checkpoint);
            if (origin == null) {
                origin = checkpoint;
            }
            target.recordUpgradeCheckpoint(checkpoint.getFileName().toString(), origin.getFileName().toString());
            log.info("Took the pre-upgrade checkpoint {}", checkpoint);
        }
        String checkpointName = checkpoint.getFileName().toString();
        String originName = origin.getFileName().toString();
        MigrationContext context = target.context();
        for (int version = from; version < targetVersion; version++) {
            try {
                byVersion.get(version).apply(context);
            } catch (RuntimeException e) {
                StoreException failure = new StoreException("Migration from schema " + version + " to " + (version + 1)
                        + " failed: " + e.getMessage(), e);
                recordAgain(target, checkpointName, originName, failure);
                throw failure;
            }
            if (version + 1 < targetVersion) {
                target.bumpSchemaVersion(version + 1, checkpointName, originName);
            } else {
                target.finishUpgrade(version + 1);
            }
            log.info("Migrated the store at {} from schema {} to {}", target.path(), version, version + 1);
        }
        try {
            StoreCheckpoints.retainNewest(backups, keep, List.of(checkpoint, origin), Path.of(target.path()));
        } catch (RuntimeException e) {
            log.warn("The store at {} is migrated to schema {}, but the old checkpoints in {} (store.backups.path) "
                    + "could not be deleted", target.path(), targetVersion, backups, e);
        }
        return targetVersion;
    }

    /**
     * Records the upgrade again after its step failed with {@code failure}, with the sequence number after the step's
     * writes, so that the next start resumes with {@code checkpoint}; a failure of that write is added to
     * {@code failure} as suppressed, and the next start then takes a new checkpoint.
     */
    private static void recordAgain(Target target, String checkpoint, String origin, Throwable failure) {
        try {
            target.recordUpgradeCheckpoint(checkpoint, origin);
        } catch (RuntimeException e) {
            failure.addSuppressed(e);
        }
    }

    /**
     * Removes the record that an unfinished upgrade to a later schema left in a store at schema {@code version}, which
     * this build runs: this build writes to the store, so the later build must not resume with that checkpoint. The
     * checkpoints themselves stay as ordinary checkpoints.
     */
    private static void removeLeftoverRecord(Target target, int version) {
        byte[] stored = target.upgradeRecord();
        if (stored == null) {
            return;
        }
        UpgradeRecord record = UpgradeRecord.decode(stored);
        target.removeUpgradeRecord();
        log.info("Removed the record of an unfinished upgrade of the store at {} to a later schema (pre-upgrade "
                        + "checkpoint {}): the store is at schema {}; the checkpoint stays as an ordinary checkpoint",
                target.path(), record == null ? "unknown" : record.checkpoint(), version);
    }

    /**
     * How this start goes on with an unfinished upgrade of {@code target}. It resumes with the checkpoint the record
     * names when that is still there and the store is at the sequence number the record carries. Otherwise it takes a
     * new checkpoint, and the record is removed first, so that the new checkpoint does not hold it. The origin is the
     * one the record names when that is still there.
     */
    private static Checkpoints checkpoints(Target target, Path backups) {
        byte[] stored = target.upgradeRecord();
        if (stored == null) {
            return new Checkpoints(null, null);
        }
        UpgradeRecord record = UpgradeRecord.decode(stored);
        Path origin = null;
        if (record == null) {
            log.warn("The record of the unfinished upgrade of the store at {} is malformed ({} bytes); taking a new "
                    + "checkpoint, which may hold writes of the failed upgrade", target.path(), stored.length);
        } else if (!StoreCheckpoints.isCheckpointName(record.checkpoint())) {
            log.warn("The unfinished upgrade of the store at {} recorded {} as its pre-upgrade checkpoint, which is not "
                    + "a checkpoint name; taking a new checkpoint, which may hold writes of the failed upgrade",
                    target.path(), record.checkpoint());
        } else {
            origin = existingCheckpoint(backups, record.origin());
            Path checkpoint = backups.resolve(record.checkpoint());
            if (!Files.isDirectory(checkpoint)) {
                log.warn("The pre-upgrade checkpoint {} of the unfinished upgrade of the store at {} is gone from {} "
                        + "(store.backups.path); taking a new checkpoint, which may hold writes of the failed upgrade; {}",
                        record.checkpoint(), target.path(), backups, originFate(origin, record));
            } else {
                long latest = target.latestSequenceNumber();
                if (latest == record.sequenceNumber()) {
                    log.info("Resuming the unfinished upgrade of the store at {} with its pre-upgrade checkpoint {}",
                            target.path(), checkpoint);
                    return new Checkpoints(checkpoint, origin == null ? checkpoint : origin);
                }
                log.warn("The store at {} was written since its unfinished upgrade (another build ran on it, or the "
                        + "upgrade was interrupted mid-step): it is at sequence number {}, the record of the upgrade at {}; "
                        + "taking a new checkpoint instead of resuming with {}, which holds those writes and may hold "
                        + "writes of the failed upgrade; {}",
                        target.path(), latest, record.sequenceNumber(), checkpoint, originFate(origin, record));
            }
        }
        target.removeUpgradeRecord();
        return new Checkpoints(null, origin);
    }

    /**
     * The checkpoint directory {@code name} under {@code backups}, or {@code null} when it is not a checkpoint name or
     * the directory is gone.
     */
    private static Path existingCheckpoint(Path backups, String name) {
        if (!StoreCheckpoints.isCheckpointName(name)) {
            return null;
        }
        Path path = backups.resolve(name);
        return Files.isDirectory(path) ? path : null;
    }

    /**
     * What becomes of the origin of an upgrade that a start cannot resume, for its warning.
     */
    private static String originFate(Path origin, UpgradeRecord record) {
        if (origin != null) {
            return "the first pre-upgrade checkpoint " + origin + " is kept until the upgrade finishes";
        }
        return "the first pre-upgrade checkpoint " + record.origin() + " is gone, so the new checkpoint is kept as the "
                + "first one until the upgrade finishes";
    }

    private Map<Integer, StoreMigrationStep> stepsByVersion(Target target) {
        Map<Integer, StoreMigrationStep> byVersion = new TreeMap<>();
        for (StoreMigrationStep step : steps) {
            // a step from the current schema or later means a new step without a new schema version
            if (step.fromVersion() < 1 || step.fromVersion() >= targetVersion) {
                throw new StoreException("Cannot open the store at " + target.path() + ": the migration step "
                        + step.getClass().getName() + " is from schema " + step.fromVersion() + ", but the schema of this build is "
                        + targetVersion + ", so its steps are from schemas 1 to " + (targetVersion - 1));
            }
            StoreMigrationStep other = byVersion.putIfAbsent(step.fromVersion(), step);
            if (other != null) {
                throw new StoreException("Cannot open the store at " + target.path() + ": two migration steps from schema "
                        + step.fromVersion() + " (" + other.getClass().getName() + ", " + step.getClass().getName() + ")");
            }
        }
        return byVersion;
    }

}
