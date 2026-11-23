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
package org.thingsboard.mqtt.broker.dao.rocksdb;

import org.rocksdb.RocksDBException;
import org.rocksdb.Status;
import org.thingsboard.mqtt.broker.dao.rocksdb.backup.CheckpointInfo;
import org.thingsboard.mqtt.broker.dao.rocksdb.backup.StoreCheckpoints;
import org.thingsboard.mqtt.broker.dao.store.StoreException;

import java.nio.file.Path;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Thrown when the store cannot open because RocksDB reports its files as corrupted (a damaged MANIFEST, CURRENT or SST
 * file), or because the store directory holds the files of a store but no CURRENT file, which RocksDB would take for
 * no store and replace with an empty one. A lock, permission or I/O failure is a plain {@link StoreException}, not this
 * one.
 * <p>
 * The message names the store, the RocksDB message, the pre-upgrade checkpoints under {@code store.backups.path}
 * (newest first) and how to restore. To restore a backup: stop the broker, replace the store directory with the
 * backup's {@code db} directory, start the broker. A pre-upgrade checkpoint is restored the same way, from a copy of
 * the checkpoint directory; the broker upgrades it again on start. Copying a checkpoint into the store path is safe
 * even when the checkpoint holds the record of an unfinished upgrade. Such a record names the checkpoint itself, at
 * the sequence number of the copy, but the copy is not in the checkpoint directory, so the start resumes the upgrade
 * with the original checkpoint under {@code store.backups.path} and takes no new one. The upgrade writes only to the
 * copy; resuming with the checkpoint and the retention of old checkpoints never change or delete it (retention
 * protects the checkpoint and the origin of the upgrade). The checkpoint stays as it was and can be copied again.
 * Copying the store directory while the broker runs does not give a usable backup.
 */
public class StoreCorruptedException extends StoreException {

    private static final int SHOWN_FILES = 5;

    StoreCorruptedException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * The {@link RocksDBException} of {@code failure} or of its cause chain whose status is a corruption, or
     * {@code null} when there is none.
     */
    static RocksDBException corruptionIn(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable t = failure; t != null && seen.add(t); t = t.getCause()) {
            if (t instanceof RocksDBException e && e.getStatus() != null && e.getStatus().getCode() == Status.Code.Corruption) {
                return e;
            }
        }
        return null;
    }

    /**
     * The exception for the store at {@code path} that RocksDB reported as corrupted with {@code corruption}; {@code
     * cause} is the failure the open ended with. Lists the checkpoints under {@code backupsPath} (blank: not set); a
     * failure to list them is appended and never hides the corruption.
     */
    static StoreCorruptedException of(String path, String backupsPath, RocksDBException corruption, Throwable cause) {
        return of(path, backupsPath, sentence(corruption.getMessage()), cause);
    }

    /**
     * The exception for the store at {@code path} whose CURRENT file is missing while the directory holds the files
     * {@code storeFiles} of a store.
     */
    static StoreCorruptedException missingCurrent(String path, String backupsPath, List<String> storeFiles) {
        String shown = String.join(", ", storeFiles.subList(0, Math.min(storeFiles.size(), SHOWN_FILES)))
                + (storeFiles.size() > SHOWN_FILES ? ", ..." : "");
        return of(path, backupsPath, "CURRENT is missing but the directory holds the store's files (" + shown + ")", null);
    }

    private static StoreCorruptedException of(String path, String backupsPath, String detail, Throwable cause) {
        StringBuilder message = new StringBuilder("The store at ").append(path).append(" is corrupted: ")
                .append(detail).append(". ");
        appendCheckpoints(message, backupsPath);
        message.append(" To restore a backup: stop the broker, replace ").append(path)
                .append(" with the backup's db directory, start the broker.")
                .append(" A pre-upgrade checkpoint can be restored the same way: stop the broker, replace ").append(path)
                .append(" with a copy of the checkpoint directory, start the broker (it upgrades again on start).")
                .append(" The checkpoint itself stays untouched.")
                .append(" Copying ").append(path).append(" while the broker runs does not give a usable backup.");
        return new StoreCorruptedException(message.toString(), cause);
    }

    private static void appendCheckpoints(StringBuilder message, String backupsPath) {
        if (backupsPath == null || backupsPath.isBlank()) {
            message.append("No pre-upgrade checkpoint can be listed: store.backups.path is not set.");
            return;
        }
        try {
            List<CheckpointInfo> checkpoints = StoreCheckpoints.list(Path.of(backupsPath));
            if (checkpoints.isEmpty()) {
                message.append("No pre-upgrade checkpoint under ").append(backupsPath).append('.');
                return;
            }
            message.append("Pre-upgrade checkpoints under ").append(backupsPath).append(": ");
            for (int i = 0; i < checkpoints.size(); i++) {
                CheckpointInfo checkpoint = checkpoints.get(i);
                message.append(i == 0 ? "" : ", ").append(checkpoint.name()).append(" (")
                        .append(checkpoint.createdAt().truncatedTo(ChronoUnit.SECONDS)).append(')');
            }
            message.append('.');
        } catch (RuntimeException e) {
            message.append("The pre-upgrade checkpoints under ").append(backupsPath).append(" could not be listed: ")
                    .append(sentence(e.getMessage())).append('.');
        }
    }

    /**
     * {@code text} on one line, without the full stop it ends with; its line breaks and runs of spaces (how RocksDB joins its parts) become ". " (or just a space after
     * a line that already ends with a full stop, colon or comma).
     */
    private static String sentence(String text) {
        if (text == null || text.isBlank()) {
            return "no details";
        }
        StringBuilder line = new StringBuilder();
        for (String part : text.strip().split("\\R|\\s{2,}")) {
            String segment = part.strip().replaceAll("\\s+", " ");
            if (segment.isEmpty()) {
                continue;
            }
            if (!line.isEmpty()) {
                char last = line.charAt(line.length() - 1);
                line.append(last == '.' || last == ':' || last == ',' ? " " : ". ");
            }
            line.append(segment);
        }
        return line.length() > 0 && line.charAt(line.length() - 1) == '.' ? line.substring(0, line.length() - 1) : line.toString();
    }

}
