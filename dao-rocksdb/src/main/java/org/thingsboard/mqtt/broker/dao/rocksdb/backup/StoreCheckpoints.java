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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The checkpoints taken before a store upgrade, kept as {@code pre-upgrade-*} directories under
 * {@code store.backups.path}. Works on the file system only; it never opens a store. Only the directories named
 * exactly as {@link #preUpgradeDir} names them are checkpoints; other entries of the directory, such as the
 * {@code .tmp} staging directory a crash during a checkpoint leaves behind, are never touched.
 */
@Slf4j
public final class StoreCheckpoints {

    static final String PRE_UPGRADE_PREFIX = "pre-upgrade-";
    // pre-upgrade-<from>-<to>, optionally -<yyyyMMdd-HHmmss>, optionally -<n> when that one is taken as well
    private static final Pattern CHECKPOINT_NAME = Pattern.compile(
            Pattern.quote(PRE_UPGRADE_PREFIX) + "\\d+-\\d+(-\\d{8}-\\d{6}(-\\d+)?)?");

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
    // newest first by modification time; equal times fall back to the name, descending
    private static final Comparator<CheckpointInfo> NEWEST_FIRST = Comparator.comparing(CheckpointInfo::createdAt)
            .thenComparing(CheckpointInfo::name).reversed();

    private StoreCheckpoints() {
    }

    /**
     * The directory for a checkpoint taken before upgrading from schema {@code from} to {@code to}:
     * {@code pre-upgrade-<from>-<to>}, or {@code pre-upgrade-<from>-<to>-<yyyyMMdd-HHmmss>} (UTC) when that one is
     * taken, or that name with the first free suffix {@code -2}, {@code -3}, ... when that one is taken as well. Only
     * computes the path.
     */
    public static Path preUpgradeDir(Path backupsPath, int from, int to, Clock clock) {
        String name = PRE_UPGRADE_PREFIX + from + "-" + to;
        Path path = backupsPath.resolve(name);
        if (!Files.exists(path)) {
            return path;
        }
        String timestamped = name + "-" + TIMESTAMP.format(clock.instant());
        path = backupsPath.resolve(timestamped);
        for (int suffix = 2; Files.exists(path); suffix++) {
            path = backupsPath.resolve(timestamped + "-" + suffix);
        }
        return path;
    }

    /**
     * Whether {@code name} is the name of a checkpoint directory, as {@link #preUpgradeDir} names them.
     */
    public static boolean isCheckpointName(String name) {
        return CHECKPOINT_NAME.matcher(name).matches();
    }

    /**
     * The checkpoint directories of {@code backupsPath}, newest first. A missing directory has none.
     */
    public static List<CheckpointInfo> list(Path backupsPath) {
        if (!Files.isDirectory(backupsPath)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(backupsPath)) {
            return entries
                    .filter(path -> isCheckpointName(path.getFileName().toString()))
                    .filter(Files::isDirectory)
                    .map(StoreCheckpoints::describe)
                    .sorted(NEWEST_FIRST)
                    .toList();
        } catch (IOException e) {
            throw new StoreException("Cannot list the checkpoints in " + backupsPath + ": " + e, e);
        }
    }

    /**
     * Deletes all but the {@code keep} newest checkpoint directories of {@code backupsPath}.
     */
    public static void retainNewest(Path backupsPath, int keep) {
        retainNewest(backupsPath, keep, null);
    }

    /**
     * Deletes all but {@code keep} checkpoint directories of {@code backupsPath}: {@code protect}, when it is one of
     * them, and the newest of the others. {@code protect} is never deleted, so the checkpoint just taken survives a
     * clock that was behind when it was taken or a modification time of an older one moved forward.
     */
    public static void retainNewest(Path backupsPath, int keep, Path protect) {
        List<CheckpointInfo> others = new ArrayList<>(list(backupsPath));
        int kept = Math.max(keep, 0);
        if (protect != null && others.removeIf(checkpoint -> isSameDir(checkpoint.path(), protect))) {
            kept = Math.max(kept - 1, 0);
        }
        kept = Math.min(kept, others.size());
        for (CheckpointInfo checkpoint : others.subList(kept, others.size())) {
            deleteRecursively(checkpoint.path());
            log.info("Deleted the old checkpoint {}", checkpoint.path());
        }
    }

    private static boolean isSameDir(Path a, Path b) {
        return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
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
