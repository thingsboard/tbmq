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

import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.dao.store.StoreException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * The checkpoints taken before a store upgrade, kept as {@code pre-upgrade-*} directories under
 * {@code store.backups.path}. Works on the file system only; it never opens a store. Other entries of the directory
 * are never touched.
 */
@Slf4j
public final class StoreCheckpoints {

    static final String PRE_UPGRADE_PREFIX = "pre-upgrade-";

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
    // newest first by modification time; equal times fall back to the name, descending
    private static final Comparator<CheckpointInfo> NEWEST_FIRST = Comparator.comparing(CheckpointInfo::createdAt)
            .thenComparing(CheckpointInfo::name).reversed();

    private StoreCheckpoints() {
    }

    /**
     * The directory for a checkpoint taken before upgrading from schema {@code from} to {@code to}:
     * {@code pre-upgrade-<from>-<to>}, or {@code pre-upgrade-<from>-<to>-<yyyyMMdd-HHmmss>} (UTC) when that one is
     * taken. Only computes the path.
     */
    public static Path preUpgradeDir(Path backupsPath, int from, int to, Clock clock) {
        String name = PRE_UPGRADE_PREFIX + from + "-" + to;
        Path path = backupsPath.resolve(name);
        if (Files.exists(path)) {
            path = backupsPath.resolve(name + "-" + TIMESTAMP.format(clock.instant()));
        }
        return path;
    }

    /**
     * The {@code pre-upgrade-*} directories of {@code backupsPath}, newest first. A missing directory has none.
     */
    public static List<CheckpointInfo> list(Path backupsPath) {
        if (!Files.isDirectory(backupsPath)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(backupsPath)) {
            return entries
                    .filter(path -> path.getFileName().toString().startsWith(PRE_UPGRADE_PREFIX))
                    .filter(Files::isDirectory)
                    .map(StoreCheckpoints::describe)
                    .sorted(NEWEST_FIRST)
                    .toList();
        } catch (IOException e) {
            throw new StoreException("Cannot list the checkpoints in " + backupsPath + ": " + e, e);
        }
    }

    /**
     * Deletes all but the {@code keep} newest {@code pre-upgrade-*} directories of {@code backupsPath}.
     */
    public static void retainNewest(Path backupsPath, int keep) {
        List<CheckpointInfo> checkpoints = list(backupsPath);
        int kept = Math.min(Math.max(keep, 0), checkpoints.size());
        for (CheckpointInfo checkpoint : checkpoints.subList(kept, checkpoints.size())) {
            deleteRecursively(checkpoint.path());
            log.info("Deleted the old checkpoint {}", checkpoint.path());
        }
    }

    private static CheckpointInfo describe(Path path) {
        try {
            return new CheckpointInfo(path.getFileName().toString(), Files.getLastModifiedTime(path).toInstant(), path);
        } catch (IOException e) {
            throw new StoreException("Cannot read the modification time of the checkpoint " + path + ": " + e, e);
        }
    }

    private static void deleteRecursively(Path directory) {
        try (Stream<Path> tree = Files.walk(directory)) {
            for (Path path : tree.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException | UncheckedIOException e) {
            throw new StoreException("Cannot delete the checkpoint " + directory + ": " + e, e);
        }
    }

}
