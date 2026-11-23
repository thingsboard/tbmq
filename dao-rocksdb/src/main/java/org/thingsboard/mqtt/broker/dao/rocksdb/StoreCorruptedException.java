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
import java.util.List;

/**
 * Thrown when the store cannot open because RocksDB reports its files as corrupted (a damaged MANIFEST, CURRENT or SST
 * file). A lock, permission or I/O failure is a plain {@link StoreException}, not this one.
 * <p>
 * The message names the store, the RocksDB message, the pre-upgrade checkpoints under {@code store.backups.path}
 * (newest first) and how to restore. To restore a backup: stop the broker, replace the store directory with the
 * backup's {@code db} directory, start the broker. A pre-upgrade checkpoint is restored the same way, from a copy of
 * the checkpoint directory; the broker upgrades it again on start. Copying a checkpoint into the store path is safe
 * even when the checkpoint holds the record of an unfinished upgrade: that record names the checkpoint itself, so the
 * start takes a new checkpoint before it migrates and the checkpoint directory under {@code store.backups.path} stays
 * untouched. Copying {@code /data} while the broker runs does not give a usable backup.
 */
public class StoreCorruptedException extends StoreException {

    StoreCorruptedException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * The {@link RocksDBException} of {@code failure} or of its cause chain whose status is a corruption, or
     * {@code null} when there is none.
     */
    static RocksDBException corruptionIn(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t instanceof RocksDBException e && e.getStatus() != null && e.getStatus().getCode() == Status.Code.Corruption) {
                return e;
            }
            if (t.getCause() == t) {
                break;
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
        StringBuilder message = new StringBuilder("The store at ").append(path).append(" is corrupted: ")
                .append(sentence(corruption.getMessage())).append(". ");
        appendCheckpoints(message, backupsPath);
        message.append(" To restore a backup: stop the broker, replace ").append(path)
                .append(" with the backup's db directory, start the broker.")
                .append(" A pre-upgrade checkpoint can be restored the same way: stop the broker, replace ").append(path)
                .append(" with a copy of the checkpoint directory, start the broker (it upgrades again on start).")
                .append(" Copying /data while the broker runs does not give a usable backup.");
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
     * {@code text} on one line, without the full stop it ends with.
     */
    private static String sentence(String text) {
        String line = text == null ? "no details" : text.strip().replaceAll("\\s+", " ");
        return line.endsWith(".") ? line.substring(0, line.length() - 1) : line;
    }

}
