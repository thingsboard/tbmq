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

import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.dao.rocksdb.RocksDbStore;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreMeta;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreProperties;
import org.thingsboard.mqtt.broker.dao.rocksdb.migration.StoreMigrationRunner;
import org.thingsboard.mqtt.broker.dao.store.BackupInProgressException;
import org.thingsboard.mqtt.broker.dao.store.StoreBackup;
import org.thingsboard.mqtt.broker.dao.store.StoreException;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Backs up the store as a {@code .tar.gz} archive: a {@value #MANIFEST_NAME} that names the store, then every file of
 * a checkpoint of the store under {@code db/}. {@link #reserve()} takes the checkpoint into
 * {@code <store.backups.path>/tmp-backup-<uuid>}; it is deleted once the archive is written or fails, also when the
 * output stream fails because the client is gone, or when the reservation is closed without writing.
 * The extracted {@code db} directory opens as a store. One backup runs at a time.
 * <p>
 * Usable without Spring: the second constructor takes everything the backup needs.
 */
@Slf4j
@Component
@StandaloneComponent
public class RocksDbStoreBackup implements StoreBackup {

    static final String MANIFEST_NAME = "backup-manifest.json";
    static final String DB_PREFIX = "db/";
    static final String TMP_PREFIX = "tmp-backup-";
    // tmp-backup-<uuid>, and the .tmp staging directory RocksDB takes a checkpoint into
    private static final Pattern TMP_NAME = Pattern.compile(Pattern.quote(TMP_PREFIX)
            + "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(\\.tmp)?");
    private static final String STAGING_SUFFIX = ".tmp";

    private final RocksDbStore store;
    private final StoreProperties properties;
    private final String tbmqVersion;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean();

    @Autowired
    public RocksDbStoreBackup(RocksDbStore store, StoreProperties properties, @Value("${app.version:unknown}") String tbmqVersion) {
        this(store, properties, tbmqVersion, Clock.systemUTC());
    }

    /**
     * @param store       the open store
     * @param properties  its settings: {@code store.path} and {@code store.backups.path}
     * @param tbmqVersion the version of the broker, written to the manifest
     * @param clock       the time written to the manifest
     */
    public RocksDbStoreBackup(RocksDbStore store, StoreProperties properties, String tbmqVersion, Clock clock) {
        this.store = store;
        this.properties = properties;
        this.tbmqVersion = tbmqVersion;
        this.clock = clock;
    }

    /**
     * Deletes the temporary checkpoints of the backups a crash interrupted: the entries of {@code store.backups.path}
     * named exactly {@code tmp-backup-<uuid>} or {@code tmp-backup-<uuid>.tmp}. A directory that the record of an
     * unfinished upgrade names, or that holds {@code store.path}, is kept with a warning. A failure is logged, never
     * thrown: it does not stop the broker.
     */
    @PostConstruct
    public void deleteLeftovers() {
        String configured = properties.getBackups().getPath();
        if (configured == null || configured.isBlank()) {
            log.debug("store.backups.path is not set: no temporary backup checkpoints to delete");
            return;
        }
        Path backups = Path.of(configured);
        if (!Files.isDirectory(backups)) {
            return;
        }
        List<Path> leftovers;
        try (Stream<Path> entries = Files.list(backups)) {
            leftovers = entries.filter(path -> isTemporaryName(path.getFileName().toString())).toList();
        } catch (IOException | UncheckedIOException e) {
            log.warn("Cannot list {} for temporary backup checkpoints: {}", backups, e.toString());
            return;
        }
        if (leftovers.isEmpty()) {
            return;
        }
        Set<String> upgradeDirs = upgradeDirs();
        if (upgradeDirs == null) {
            return;
        }
        Path storePath = Path.of(properties.getPath());
        for (Path leftover : leftovers) {
            if (upgradeDirs.contains(leftover.getFileName().toString())) {
                log.warn("Kept {}: the record of an unfinished store upgrade names it", leftover);
            } else if (StoreCheckpoints.holds(leftover, storePath)) {
                log.warn("Kept {}: it holds the store at {} (store.path)", leftover, storePath);
            } else {
                try {
                    deleteRecursively(leftover);
                    log.info("Deleted {}, the temporary checkpoint of a backup that did not finish", leftover);
                } catch (IOException | UncheckedIOException e) {
                    log.warn("Failed to delete {}, the temporary checkpoint of a backup that did not finish: {}", leftover, e.toString());
                }
            }
        }
    }

    static boolean isTemporaryName(String name) {
        return TMP_NAME.matcher(name).matches() && !StoreCheckpoints.isCheckpointName(name);
    }

    /**
     * Takes the slot and the checkpoint, so that a failure of either surfaces before anything is written; the
     * reservation owns the checkpoint and deletes it when it is written with or closed.
     */
    @Override
    public Reservation reserve() {
        Path backups = backupsPath();
        if (!running.compareAndSet(false, true)) {
            throw new BackupInProgressException("A backup of the store is already in progress; try again once it finishes");
        }
        Path checkpoint = backups.resolve(TMP_PREFIX + UUID.randomUUID());
        try {
            StoreMeta meta = store.meta();
            Instant createdAt = clock.instant();
            store.createCheckpoint(checkpoint);
            List<Path> files = checkpointFiles(checkpoint);
            return new SlotReservation(checkpoint, manifest(meta, createdAt), files);
        } catch (IOException | RuntimeException e) {
            deleteCheckpoint(checkpoint);
            running.set(false);
            if (e instanceof IOException io) {
                throw new StoreException("Cannot back up the store at " + properties.getPath() + ": " + io, io);
            }
            throw (RuntimeException) e;
        }
    }

    private Path backupsPath() {
        String configured = properties.getBackups().getPath();
        if (configured == null || configured.isBlank()) {
            throw new StoreException("Cannot back up the store at " + properties.getPath() + ": store.backups.path is not set");
        }
        return Path.of(configured);
    }

    private void writeArchive(byte[] manifest, List<Path> files, OutputStream out) throws IOException {
        long startNs = System.nanoTime();
        try (TarGzWriter tar = new TarGzWriter(out)) {
            tar.addBytes(MANIFEST_NAME, manifest);
            for (Path file : files) {
                tar.addFile(DB_PREFIX + file.getFileName(), file);
            }
            tar.finish();
        }
        log.info("Backed up the store at {}: {} files in {} ms", properties.getPath(), files.size(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs));
    }

    private static void deleteCheckpoint(Path checkpoint) {
        deleteQuietly(checkpoint);
        deleteQuietly(checkpoint.resolveSibling(checkpoint.getFileName() + STAGING_SUFFIX));
    }

    private byte[] manifest(StoreMeta meta, Instant createdAt) {
        // the identity of the store only: never the JWT signing key or another secret of the meta
        ObjectNode manifest = JacksonUtil.newObjectNode();
        manifest.put("product", meta.product());
        manifest.put("schemaVersion", meta.schemaVersion());
        manifest.put("installId", meta.installId().toString());
        manifest.put("tbmqVersion", tbmqVersion);
        manifest.put("createdAt", DateTimeFormatter.ISO_INSTANT.format(createdAt));
        return JacksonUtil.toString(manifest).getBytes(StandardCharsets.UTF_8);
    }

    private List<Path> checkpointFiles(Path checkpoint) throws IOException {
        List<Path> files;
        try (Stream<Path> entries = Files.list(checkpoint)) {
            files = entries.sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
        }
        for (Path file : files) {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                throw new StoreException("Cannot back up the store at " + properties.getPath() + ": its checkpoint holds "
                        + file.getFileName() + ", which is not a regular file");
            }
        }
        return files;
    }

    /**
     * The directory names the record of an unfinished upgrade holds, or {@code null} when the record cannot be read.
     */
    private Set<String> upgradeDirs() {
        try {
            StoreMigrationRunner.UpgradeRecord record = store.upgradeRecord();
            // the checkpoint and the origin are the same directory while the upgrade has only one
            return record == null ? Set.of() : Set.copyOf(List.of(record.checkpoint(), record.origin()));
        } catch (StoreException e) {
            // unreadable: no leftover can be told apart from a directory the upgrade needs, so none is deleted
            log.warn("Cannot read the record of an unfinished store upgrade: {}", e.getMessage());
            return null;
        }
    }

    private static void deleteQuietly(Path directory) {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try {
            deleteRecursively(directory);
        } catch (IOException | UncheckedIOException e) {
            log.warn("Failed to delete the temporary backup checkpoint {}; the next start deletes it: {}", directory, e.toString());
        }
    }

    private static void deleteRecursively(Path directory) throws IOException {
        try (Stream<Path> tree = Files.walk(directory)) {
            for (Path path : tree.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    /**
     * The slot of one backup and its checkpoint: both released by the end of {@link #writeTo}, or by {@link #close()}
     * when nothing was written.
     */
    private final class SlotReservation implements Reservation {

        private static final int RESERVED = 0;
        private static final int WRITING = 1;
        private static final int RELEASED = 2;

        private final AtomicInteger state = new AtomicInteger(RESERVED);
        private final Path checkpoint;
        private final byte[] manifest;
        private final List<Path> files;

        private SlotReservation(Path checkpoint, byte[] manifest, List<Path> files) {
            this.checkpoint = checkpoint;
            this.manifest = manifest;
            this.files = files;
        }

        @Override
        public void writeTo(OutputStream out) throws IOException {
            if (!state.compareAndSet(RESERVED, WRITING)) {
                throw new IllegalStateException("This backup reservation was already used or released");
            }
            try {
                writeArchive(manifest, files, out);
            } finally {
                release();
            }
        }

        @Override
        public void close() {
            if (state.compareAndSet(RESERVED, WRITING)) {
                release();
            }
        }

        private void release() {
            try {
                deleteCheckpoint(checkpoint);
            } finally {
                state.set(RELEASED);
                running.set(false);
            }
        }

    }

}
