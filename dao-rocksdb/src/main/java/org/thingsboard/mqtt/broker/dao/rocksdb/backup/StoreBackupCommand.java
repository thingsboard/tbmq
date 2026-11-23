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

import org.rocksdb.RocksDBException;
import org.rocksdb.Status;
import org.thingsboard.mqtt.broker.dao.rocksdb.RocksDbStore;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreMemoryBudget;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreProperties;
import org.thingsboard.mqtt.broker.dao.rocksdb.disk.DiskSpaceMonitor;
import org.thingsboard.mqtt.broker.dao.rocksdb.disk.FileStoreFreeSpaceProbe;
import org.thingsboard.mqtt.broker.dao.store.StoreException;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Backs up the store of a stopped broker from the command line: writes the same {@code .tar.gz} archive as
 * {@code GET /api/system/backup}. Spring is not involved; the store is opened directly, so RocksDB's own lock tells a
 * running broker from a stopped one.
 * <p>
 * Exit codes: 0 the archive is written, 1 bad arguments or any other failure, 2 the store is held by a running broker.
 * The command never changes the store beyond what an open of a store of the current schema does: a missing store is
 * not created, and a store of an older schema is refused, because only the broker upgrades a store. A store held by a
 * running broker is refused, but RocksDB's refused open still rotates the broker's info log: it renames {@code LOG} to
 * {@code LOG.old.<timestamp>}, where the broker goes on logging, and starts a new {@code LOG}; nothing else in the
 * store is written.
 * <p>
 * The archive is written to a temporary file next to {@code --out}, synced, and only then moved to {@code --out}.
 */
public final class StoreBackupCommand {

    static final int EXIT_OK = 0;
    static final int EXIT_FAILURE = 1;
    static final int EXIT_IN_USE = 2;

    static final String USAGE = "Usage: StoreBackupCommand --store-path <dir> --out <file.tar.gz> [--backups-path <dir>]"
            + " (--backups-path defaults to <store-path>/../backups)";

    private static final String STORE_PATH = "--store-path";
    private static final String OUT = "--out";
    private static final String BACKUPS_PATH = "--backups-path";
    private static final Set<String> OPTIONS = Set.of(STORE_PATH, OUT, BACKUPS_PATH);
    private static final String UNKNOWN_VERSION = "unknown";
    // the lock RocksDB takes on <store>/LOCK is a file lock; a second open fails with an IO error that names the file
    // as "<store>/LOCK: <reason>"
    private static final String LOCK_FILE = "LOCK";
    private static final String NO_MIGRATION_STEP = "no migration step from schema";

    private StoreBackupCommand() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.err));
    }

    /**
     * @return the exit code; messages go to {@code err}
     */
    static int run(String[] args, PrintStream err) {
        Map<String, String> options = parse(args, err);
        if (options == null) {
            return EXIT_FAILURE;
        }
        Path storePath = Path.of(options.get(STORE_PATH)).toAbsolutePath().normalize();
        Path out = Path.of(options.get(OUT)).toAbsolutePath().normalize();
        Path backups = options.containsKey(BACKUPS_PATH)
                ? Path.of(options.get(BACKUPS_PATH)).toAbsolutePath().normalize()
                : storePath.resolveSibling("backups").normalize();
        // RocksDbStore.open creates a store where there is none; a directory that holds a store's files without its
        // CURRENT file is a damaged store, which the open refuses as corrupted without touching it
        try {
            if (!Files.isRegularFile(storePath.resolve("CURRENT")) && RocksDbStore.storeFilesWithoutCurrent(storePath).isEmpty()) {
                err.println("No store at " + storePath);
                return EXIT_FAILURE;
            }
        } catch (StoreException e) {
            err.println("Error: " + e.getMessage());
            return EXIT_FAILURE;
        }
        if (Files.exists(out)) {
            err.println("Cannot write " + out + ": it already exists");
            return EXIT_FAILURE;
        }
        Path outDir = out.getParent();
        if (outDir == null || !Files.exists(outDir)) {
            err.println("Cannot write " + out + ": the directory " + outDir + " does not exist");
            return EXIT_FAILURE;
        }
        if (!Files.isDirectory(outDir)) {
            err.println("Cannot write " + out + ": " + outDir + " is not a directory");
            return EXIT_FAILURE;
        }
        RocksDbStore store = null;
        try {
            StoreProperties properties = new StoreProperties();
            properties.setPath(storePath.toString());
            properties.getBackups().setPath(backups.toString());
            // a short-lived command: the smallest block cache; the monitor is never started, nothing here writes data
            store = new RocksDbStore(properties, StoreMemoryBudget.forContainerMemory(0, 0, 0),
                    new DiskSpaceMonitor(properties, new FileStoreFreeSpaceProbe()), List.of());
            try {
                store.open();
            } catch (StoreException e) {
                return openFailed(storePath, e, err);
            }
            return backUp(store, properties, out, err);
        } catch (IOException | StoreException e) {
            err.println("Error: " + e.getMessage());
            return EXIT_FAILURE;
        } catch (RuntimeException e) {
            err.println("Unexpected error: " + e);
            e.printStackTrace(err);
            return EXIT_FAILURE;
        } finally {
            if (store != null) {
                store.close();
            }
        }
    }

    private static int backUp(RocksDbStore store, StoreProperties properties, Path out, PrintStream err) throws IOException {
        String version = RocksDbStoreBackup.class.getPackage().getImplementationVersion();
        RocksDbStoreBackup backup = new RocksDbStoreBackup(store, properties, version == null ? UNKNOWN_VERSION : version,
                Clock.systemUTC());
        // next to --out, so that the move into place stays on one volume; only a complete archive ever gets the name
        Path temp = Files.createTempFile(out.getParent(), out.getFileName().toString() + ".", ".tmp");
        boolean moved = false;
        try {
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE);
                 OutputStream stream = Channels.newOutputStream(channel)) {
                backup.write(stream);
                // on disk before it gets the name: a crash never leaves a named archive with missing content
                channel.force(true);
            }
            Files.move(temp, out);
            moved = true;
            syncDirectory(out.getParent());
        } finally {
            if (!moved) {
                Files.deleteIfExists(temp);
            }
        }
        return EXIT_OK;
    }

    /**
     * Makes the move into {@code directory} durable where the platform can sync a directory; where it cannot open one,
     * the archive's own content is still synced.
     */
    private static void syncDirectory(Path directory) {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException | UnsupportedOperationException e) {
            // e.g. Windows refuses to open a directory as a channel
        }
    }

    private static int openFailed(Path storePath, StoreException e, PrintStream err) {
        if (isLocked(e, storePath)) {
            err.println("The store at " + storePath + " is in use by a running broker; back it up through GET /api/system/backup");
            return EXIT_IN_USE;
        }
        err.println("Error: " + e.getMessage());
        if (e.getMessage() != null && e.getMessage().contains(NO_MIGRATION_STEP)) {
            err.println("Only the broker upgrades a store: start the broker of this version once to upgrade it, "
                    + "or back it up with the version that wrote it");
        }
        return EXIT_FAILURE;
    }

    /**
     * RocksDB refuses to open a store another process, or another open in this process, holds: an IO error whose
     * message names the store's lock file, {@code <storePath>/LOCK:}. Anything else, such as a corrupt store or an IO
     * error on another file, is not "in use".
     */
    static boolean isLocked(Throwable failure, Path storePath) {
        String lockFile = storePath.resolve(LOCK_FILE) + ":";
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof RocksDBException rocks && rocks.getStatus() != null
                    && rocks.getStatus().getCode() == Status.Code.IOError
                    && rocks.getMessage() != null && rocks.getMessage().contains(lockFile)) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    private static Map<String, String> parse(String[] args, PrintStream err) {
        Map<String, String> options = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String name = args[i];
            if (!OPTIONS.contains(name)) {
                return usageError("Unknown argument: " + name, err);
            }
            if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                return usageError("Missing value for " + name, err);
            }
            if (options.put(name, args[++i]) != null) {
                return usageError("Duplicate argument: " + name, err);
            }
        }
        for (String required : List.of(STORE_PATH, OUT)) {
            if (!options.containsKey(required)) {
                return usageError("Missing argument: " + required, err);
            }
        }
        return options;
    }

    private static Map<String, String> usageError(String message, PrintStream err) {
        err.println(message);
        err.println(USAGE);
        return null;
    }

}
