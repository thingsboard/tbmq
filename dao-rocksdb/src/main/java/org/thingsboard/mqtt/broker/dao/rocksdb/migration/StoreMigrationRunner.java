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

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Brings a store from its stored schema version up to the version of this build. A store already at that version is
 * left alone. An older one gets one checkpoint {@code pre-upgrade-<from>-<to>} under {@code store.backups.path} before
 * any step runs; then each step runs in version order and the stored version is bumped after each, so a store from
 * several versions back runs every step and a crash before a bump reruns only that step. After the last bump the
 * oldest checkpoints beyond {@code store.backups.keep} are deleted; the checkpoint of this run is always kept.
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
         * Writes {@code version} as the stored schema version, with a WAL sync.
         */
        void bumpSchemaVersion(int version);

        /**
         * Writes a checkpoint of the store into {@code dir}, which does not exist.
         */
        void checkpoint(Path dir);

        MigrationContext context();

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
        Path checkpoint = StoreCheckpoints.preUpgradeDir(backups, from, targetVersion, clock);
        target.checkpoint(checkpoint);
        log.info("Took the pre-upgrade checkpoint {}", checkpoint);
        MigrationContext context = target.context();
        for (int version = from; version < targetVersion; version++) {
            try {
                byVersion.get(version).apply(context);
            } catch (RuntimeException e) {
                throw new StoreException("Migration from schema " + version + " to " + (version + 1) + " failed: "
                        + e.getMessage(), e);
            }
            target.bumpSchemaVersion(version + 1);
            log.info("Migrated the store at {} from schema {} to {}", target.path(), version, version + 1);
        }
        try {
            StoreCheckpoints.retainNewest(backups, keep, checkpoint);
        } catch (RuntimeException e) {
            log.warn("The store at {} is migrated to schema {}, but the old checkpoints in {} (store.backups.path) "
                    + "could not be deleted", target.path(), targetVersion, backups, e);
        }
        return targetVersion;
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
