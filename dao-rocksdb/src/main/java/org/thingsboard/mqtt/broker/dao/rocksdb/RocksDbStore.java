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

import com.google.common.primitives.Ints;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.BloomFilter;
import org.rocksdb.Cache;
import org.rocksdb.Checkpoint;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.Env;
import org.rocksdb.LRUCache;
import org.rocksdb.Options;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.Slice;
import org.rocksdb.SstFileManager;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteBufferManager;
import org.rocksdb.WriteOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.dao.rocksdb.disk.DiskSpaceMonitor;
import org.thingsboard.mqtt.broker.dao.rocksdb.migration.MigrationContext;
import org.thingsboard.mqtt.broker.dao.rocksdb.migration.StoreMigrationRunner;
import org.thingsboard.mqtt.broker.dao.rocksdb.migration.StoreMigrationStep;
import org.thingsboard.mqtt.broker.dao.store.StoreException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The embedded RocksDB store of the standalone edition: one database under {@code store.path}, one column family
 * per {@link StoreColumnFamily}. Values are opaque bytes. Every {@link RocksDBException} surfaces as a
 * {@link StoreException} that names the failed operation.
 * <p>
 * Column family handles and native batches never leave this class; the only native object a caller sees is the
 * iterator lent to the reader of {@link #scan}, which must not escape it. Every operation, including the whole body
 * of {@link #write} and the reader of {@link #scan}, runs under a shared lock, and {@link #close()} takes the exclusive one: a closing store never frees
 * native memory an operation is using, and an operation on a closed store throws {@link StoreException}.
 */
@Slf4j
@Component
public class RocksDbStore {

    public static final int SCHEMA_VERSION = 2;
    public static final String PRODUCT = "LITE";

    static final byte[] META_SCHEMA_VERSION_KEY = "schema_version".getBytes(StandardCharsets.UTF_8);
    static final byte[] META_PRODUCT_KEY = "product".getBytes(StandardCharsets.UTF_8);
    static final byte[] META_INSTALL_ID_KEY = "install_id".getBytes(StandardCharsets.UTF_8);
    static final byte[] META_JWT_SIGNING_KEY = "jwt_signing_key".getBytes(StandardCharsets.UTF_8);
    static final int JWT_SIGNING_KEY_BYTES = 64;
    // while an upgrade is unfinished: the 8-byte big-endian latest sequence number of the store right after the last
    // write of this key, the directory name (UTF-8) of the checkpoint to resume with, one 0x00 byte and the directory
    // name (UTF-8) of the upgrade's first pre-upgrade checkpoint; removed by the last bump, and by a build that opens the
    // store at its own schema. A checkpoint taken by a start that could not resume holds the record naming itself
    static final byte[] META_UPGRADE_CHECKPOINT_KEY = "upgrade_checkpoint".getBytes(StandardCharsets.UTF_8);
    // reserved: written and deleted in one batch, so it is never stored
    static final byte[] META_RESUME_PROBE_KEY = "resume_probe".getBytes(StandardCharsets.UTF_8);

    private static final long SLOW_CLOSE_MS = 1000;
    private static final String CURRENT_FILE = "CURRENT";
    // what RocksDB keeps of a store besides CURRENT: SST files, numbered WAL files, MANIFEST and OPTIONS files
    private static final Pattern STORE_FILE = Pattern.compile("\\d+\\.(sst|log)|MANIFEST-\\d+|OPTIONS-\\d+");
    private static final int MAX_OPEN_FILES = 256;
    private static final int DEVICE_MSG_KEY_PREFIX_LENGTH = 8;
    private static final double BLOOM_BITS_PER_KEY = 10;

    private static final String OP_GET = "get from";
    private static final String OP_PUT = "put to";
    private static final String OP_DELETE = "delete from";
    private static final String OP_SCAN = "scan";
    private static final String OP_WRITE = "write a batch";
    private static final String OP_SYNC_WAL = "sync the WAL";
    private static final String OP_META = "read the meta";
    private static final String OP_CHECKPOINT = "create a checkpoint";

    private final StoreProperties properties;
    private final StoreMemoryBudget memoryBudget;
    private final DiskSpaceMonitor diskSpaceMonitor;
    private final List<StoreMigrationStep> migrationSteps;
    private final LongSupplier nanoClock;

    private final Map<StoreColumnFamily, ColumnFamilyHandle> handles = new EnumMap<>(StoreColumnFamily.class);
    // the default family and any family this build does not know (a later schema): opened so the meta check can refuse
    private final List<ColumnFamilyHandle> otherHandles = new ArrayList<>();
    // native objects the database depends on, in creation order; closed in reverse once the database is closed
    private final List<AutoCloseable> nativeResources = new ArrayList<>();

    // everything below is guarded by the lock: written under the write lock, read under the read lock
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private WriteOptions writeOptions;
    private StoreMeta meta;
    private RocksDB db;
    private boolean open;

    @Autowired
    public RocksDbStore(StoreProperties properties, StoreMemoryBudget memoryBudget, DiskSpaceMonitor diskSpaceMonitor,
                        List<StoreMigrationStep> migrationSteps) {
        this(properties, memoryBudget, diskSpaceMonitor, migrationSteps, System::nanoTime);
    }

    // the clock is a seam for the test of the slow-close warning
    RocksDbStore(StoreProperties properties, StoreMemoryBudget memoryBudget, DiskSpaceMonitor diskSpaceMonitor,
                 List<StoreMigrationStep> migrationSteps, LongSupplier nanoClock) {
        this.properties = properties;
        this.memoryBudget = memoryBudget;
        this.diskSpaceMonitor = diskSpaceMonitor;
        this.migrationSteps = List.copyOf(migrationSteps);
        this.nanoClock = nanoClock;
    }

    @PostConstruct
    public void open() {
        lock.writeLock().lock();
        try {
            doOpenLocked();
        } finally {
            lock.writeLock().unlock();
        }
        diskSpaceMonitor.onRecovery(this::resumeAfterBackgroundError);
    }

    private void doOpenLocked() {
        String path = properties.getPath();
        if (path == null || path.isBlank()) {
            throw new StoreException("Cannot open the store: store.path is not set");
        }
        if (open) {
            throw new StoreException("The store at " + path + " is already open");
        }
        WalSyncMode walSyncMode = WalSyncMode.parse(properties.getWalSync());
        int keep = properties.getBackups().getKeep();
        if (keep <= 0) {
            throw new StoreException("Cannot open the store at " + path + ": store.backups.keep must be positive, but is " + keep);
        }
        try {
            Files.createDirectories(Path.of(path));
        } catch (IOException e) {
            throw new StoreException("Cannot create the store directory " + path + ": " + e, e);
        }
        // RocksDB takes a directory without CURRENT for no store: it would write a new CURRENT and MANIFEST and, on the
        // next open, delete the files of the store as obsolete
        List<String> storeFiles = storeFilesWithoutCurrent(Path.of(path));
        if (!storeFiles.isEmpty()) {
            throw StoreCorruptedException.missingCurrent(path, properties.getBackups().getPath(), storeFiles);
        }
        try {
            RocksDB.loadLibrary();
            doOpen(path, walSyncMode);
            // published last: the store counts as open only once the meta is loaded and checked
            open = true;
            log.info("Opened the store at {} (block cache {} bytes, write buffer {} bytes, install id {})",
                    path, memoryBudget.blockCacheBytes(), memoryBudget.writeBufferBytes(), meta.installId());
        } catch (RocksDBException e) {
            releaseNativeResources();
            throw refusal(path, e);
        } catch (RuntimeException e) {
            releaseNativeResources();
            // the reads of the meta load and of the migrations come wrapped in a StoreException
            RocksDBException corruption = StoreCorruptedException.corruptionIn(e);
            if (corruption != null) {
                throw StoreCorruptedException.of(path, properties.getBackups().getPath(), corruption, e);
            }
            throw e;
        }
    }

    /**
     * The files of a store in {@code dir} (SST, numbered WAL, MANIFEST and OPTIONS files, sorted by name) when it has
     * no CURRENT file; empty when it has one, holds none of them, or does not exist.
     *
     * @throws StoreException when {@code dir} cannot be listed
     */
    public static List<String> storeFilesWithoutCurrent(Path dir) {
        if (!Files.isDirectory(dir) || Files.exists(dir.resolve(CURRENT_FILE), LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.map(entry -> entry.getFileName().toString())
                    .filter(name -> STORE_FILE.matcher(name).matches())
                    .sorted()
                    .toList();
        } catch (IOException | UncheckedIOException e) {
            throw new StoreException("Cannot list the store directory " + dir + ": " + e, e);
        }
    }

    private StoreException refusal(String path, RocksDBException e) {
        if (StoreCorruptedException.corruptionIn(e) != null) {
            return StoreCorruptedException.of(path, properties.getBackups().getPath(), e, e);
        }
        return new StoreException("Cannot open the store at " + path + ": " + e.getMessage(), e);
    }

    private void doOpen(String path, WalSyncMode walSyncMode) throws RocksDBException {
        Cache blockCache = register(new LRUCache(memoryBudget.blockCacheBytes()));
        WriteBufferManager writeBufferManager = register(new WriteBufferManager(memoryBudget.writeBufferBytes(), blockCache));
        // with an SST file manager RocksDB recovers from an out-of-space background error by itself once space is back
        SstFileManager sstFileManager = register(new SstFileManager(Env.getDefault()));
        StoreBackgroundErrorListener backgroundErrorListener =
                register(new StoreBackgroundErrorListener(diskSpaceMonitor::reportBackgroundError));
        DBOptions dbOptions = register(new DBOptions()
                .setCreateIfMissing(true)
                .setCreateMissingColumnFamilies(true)
                .setMaxOpenFiles(MAX_OPEN_FILES)
                .setWriteBufferManager(writeBufferManager)
                .setSstFileManager(sstFileManager)
                .setListeners(List.of(backgroundErrorListener)));
        BloomFilter deviceMsgFilter = register(new BloomFilter(BLOOM_BITS_PER_KEY));
        writeOptions = register(new WriteOptions().setSync(walSyncMode == WalSyncMode.ALWAYS));

        List<ColumnFamilyDescriptor> descriptors = new ArrayList<>();
        descriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY,
                register(new ColumnFamilyOptions().setTableFormatConfig(tableConfig(blockCache)))));
        for (StoreColumnFamily cf : StoreColumnFamily.values()) {
            ColumnFamilyOptions options = register(new ColumnFamilyOptions());
            if (cf == StoreColumnFamily.DEVICE_MSG) {
                // the first 8 bytes of a device_msg key are the hash of the queue: a prefix scan reads one queue
                options.useFixedLengthPrefixExtractor(DEVICE_MSG_KEY_PREFIX_LENGTH);
                options.setTableFormatConfig(tableConfig(blockCache).setFilterPolicy(deviceMsgFilter).setWholeKeyFiltering(false));
            } else {
                options.setTableFormatConfig(tableConfig(blockCache));
            }
            descriptors.add(new ColumnFamilyDescriptor(cf.columnFamilyNameBytes(), options));
        }
        int known = descriptors.size();
        for (byte[] unknown : unknownColumnFamilies(path)) {
            descriptors.add(new ColumnFamilyDescriptor(unknown, register(new ColumnFamilyOptions())));
        }

        List<ColumnFamilyHandle> opened = new ArrayList<>();
        db = RocksDB.open(dbOptions, path, descriptors, opened);
        otherHandles.add(opened.get(0));
        for (StoreColumnFamily cf : StoreColumnFamily.values()) {
            handles.put(cf, opened.get(cf.ordinal() + 1));
        }
        otherHandles.addAll(opened.subList(known, opened.size()));
        meta = loadOrInitMeta(path);
        StoreProperties.Backups backups = properties.getBackups();
        int schemaVersion = new StoreMigrationRunner(migrationSteps, SCHEMA_VERSION, backups.getPath(), backups.getKeep(),
                Clock.systemUTC()).run(new OpeningStore(path));
        meta = new StoreMeta(schemaVersion, meta.product(), meta.installId());
        // after the migrations: also covers a crash between the first meta write and the key
        ensureJwtSigningKey();
    }

    /**
     * The store while {@link #doOpen} holds the write lock and before it is published as open: the migrations work on
     * the raw database, every write synced.
     */
    private final class OpeningStore implements StoreMigrationRunner.Target, MigrationContext {

        private final String path;

        private OpeningStore(String path) {
            this.path = path;
        }

        @Override
        public String path() {
            return path;
        }

        @Override
        public int schemaVersion() {
            return meta.schemaVersion();
        }

        @Override
        public long latestSequenceNumber() {
            return db.getLatestSequenceNumber();
        }

        @Override
        public void bumpSchemaVersion(int version, String checkpoint, String origin) {
            try (WriteBatch batch = new WriteBatch()) {
                batch.put(handles.get(StoreColumnFamily.META), META_SCHEMA_VERSION_KEY, Ints.toByteArray(version));
                writeWithUpgradeRecord(batch, checkpoint, origin);
            } catch (RocksDBException e) {
                throw failure(OP_WRITE, StoreColumnFamily.META, e);
            }
        }

        @Override
        public void finishUpgrade(int version) {
            try (WriteBatch batch = new WriteBatch(); WriteOptions synced = new WriteOptions().setSync(true)) {
                batch.put(handles.get(StoreColumnFamily.META), META_SCHEMA_VERSION_KEY, Ints.toByteArray(version));
                batch.delete(handles.get(StoreColumnFamily.META), META_UPGRADE_CHECKPOINT_KEY);
                db.write(synced, batch);
            } catch (RocksDBException e) {
                throw failure(OP_WRITE, StoreColumnFamily.META, e);
            }
        }

        @Override
        public byte[] upgradeRecord() {
            return get(StoreColumnFamily.META, META_UPGRADE_CHECKPOINT_KEY);
        }

        @Override
        public void recordUpgradeCheckpoint(String checkpoint, String origin) {
            try (WriteBatch batch = new WriteBatch()) {
                writeWithUpgradeRecord(batch, checkpoint, origin);
            } catch (RocksDBException e) {
                throw failure(OP_PUT, StoreColumnFamily.META, e);
            }
        }

        @Override
        public void removeUpgradeRecord() {
            delete(StoreColumnFamily.META, META_UPGRADE_CHECKPOINT_KEY);
        }

        /**
         * Adds the record of the upgrade with {@code checkpoint} and {@code origin} to {@code batch} and writes the batch
         * with a WAL sync. The record carries the sequence number the store is at right after this write: RocksDB gives
         * every entry of a batch one sequence number, and nothing else writes while the open holds the write lock. A
         * different number afterwards is only logged: the next open then finds the store changed and takes a new
         * checkpoint.
         */
        private void writeWithUpgradeRecord(WriteBatch batch, String checkpoint, String origin) throws RocksDBException {
            long expected = db.getLatestSequenceNumber() + batch.count() + 1;
            batch.put(handles.get(StoreColumnFamily.META), META_UPGRADE_CHECKPOINT_KEY,
                    new StoreMigrationRunner.UpgradeRecord(expected, checkpoint, origin).encode());
            try (WriteOptions synced = new WriteOptions().setSync(true)) {
                db.write(synced, batch);
            }
            long latest = db.getLatestSequenceNumber();
            if (latest != expected) {
                log.debug("The store at {} is at sequence number {} after recording the upgrade checkpoint {} with {}; "
                        + "the next start will take a new checkpoint", path, latest, checkpoint, expected);
            }
        }

        @Override
        public void checkpoint(Path dir) {
            RocksDbStore.this.checkpoint(db, dir);
        }

        @Override
        public MigrationContext context() {
            return this;
        }

        @Override
        public byte[] get(StoreColumnFamily cf, byte[] key) {
            try {
                return db.get(handles.get(cf), key);
            } catch (RocksDBException e) {
                throw failure(OP_GET, cf, e);
            }
        }

        @Override
        public void put(StoreColumnFamily cf, byte[] key, byte[] value) {
            try (WriteOptions synced = new WriteOptions().setSync(true)) {
                db.put(handles.get(cf), synced, key, value);
            } catch (RocksDBException e) {
                throw failure(OP_PUT, cf, e);
            }
        }

        @Override
        public void delete(StoreColumnFamily cf, byte[] key) {
            try (WriteOptions synced = new WriteOptions().setSync(true)) {
                db.delete(handles.get(cf), synced, key);
            } catch (RocksDBException e) {
                throw failure(OP_DELETE, cf, e);
            }
        }

        @Override
        public <T> T scan(StoreColumnFamily cf, byte[] lo, byte[] hi, Function<RocksIterator, T> reader) {
            try {
                return scanRaw(db, cf, lo, hi, reader);
            } catch (RocksDBException e) {
                throw failure(OP_SCAN, cf, e);
            }
        }

        @Override
        public void ensureJwtSigningKey() {
            try {
                RocksDbStore.this.ensureJwtSigningKey();
            } catch (RocksDBException e) {
                throw failure(OP_PUT, StoreColumnFamily.META, e);
            }
        }

    }

    /**
     * Writes the per-install JWT signing key, once: a store that has one keeps it, so tokens survive restarts and a
     * restored backup brings its key with it. The key is never logged.
     */
    private void ensureJwtSigningKey() throws RocksDBException {
        ColumnFamilyHandle metaHandle = handles.get(StoreColumnFamily.META);
        if (db.get(metaHandle, META_JWT_SIGNING_KEY) != null) {
            return;
        }
        byte[] key = new byte[JWT_SIGNING_KEY_BYTES];
        new SecureRandom().nextBytes(key);
        try (WriteOptions synced = new WriteOptions().setSync(true)) {
            db.put(metaHandle, synced, META_JWT_SIGNING_KEY, key);
        }
    }

    /**
     * Families of an existing database that this build does not know. A store written by a later schema has them, and
     * RocksDB refuses to open a database without all its families, which would hide the readable schema refusal.
     */
    private List<byte[]> unknownColumnFamilies(String path) throws RocksDBException {
        if (!Files.exists(Path.of(path, CURRENT_FILE))) {
            return List.of();
        }
        Set<String> known = new HashSet<>(List.of("default"));
        for (StoreColumnFamily cf : StoreColumnFamily.values()) {
            known.add(cf.columnFamilyName());
        }
        List<byte[]> unknown = new ArrayList<>();
        try (Options options = new Options()) {
            for (byte[] name : RocksDB.listColumnFamilies(options, path)) {
                if (!known.contains(new String(name, StandardCharsets.UTF_8))) {
                    unknown.add(name);
                }
            }
        }
        return unknown;
    }

    private static BlockBasedTableConfig tableConfig(Cache blockCache) {
        // index and filter blocks live in the block cache, so the cache bounds the native memory of the store
        return new BlockBasedTableConfig()
                .setBlockCache(blockCache)
                .setCacheIndexAndFilterBlocks(true)
                .setPinL0FilterAndIndexBlocksInCache(true);
    }

    private StoreMeta loadOrInitMeta(String path) throws RocksDBException {
        ColumnFamilyHandle metaHandle = handles.get(StoreColumnFamily.META);
        byte[] versionBytes = db.get(metaHandle, META_SCHEMA_VERSION_KEY);
        if (versionBytes == null) {
            UUID installId = UUID.randomUUID();
            try (WriteBatch batch = new WriteBatch(); WriteOptions synced = new WriteOptions().setSync(true)) {
                batch.put(metaHandle, META_SCHEMA_VERSION_KEY, Ints.toByteArray(SCHEMA_VERSION));
                batch.put(metaHandle, META_PRODUCT_KEY, PRODUCT.getBytes(StandardCharsets.UTF_8));
                batch.put(metaHandle, META_INSTALL_ID_KEY, installId.toString().getBytes(StandardCharsets.UTF_8));
                db.write(synced, batch);
            }
            return new StoreMeta(SCHEMA_VERSION, PRODUCT, installId);
        }
        byte[] productBytes = db.get(metaHandle, META_PRODUCT_KEY);
        String product = productBytes == null ? null : new String(productBytes, StandardCharsets.UTF_8);
        if (!PRODUCT.equals(product)) {
            throw new StoreException("The store at " + path + " belongs to product '" + product + "', expected '" + PRODUCT + "'");
        }
        byte[] installIdBytes = db.get(metaHandle, META_INSTALL_ID_KEY);
        if (versionBytes.length != Integer.BYTES || installIdBytes == null) {
            throw new StoreException("The meta of the store at " + path + " is corrupt");
        }
        int schemaVersion = Ints.fromByteArray(versionBytes);
        if (schemaVersion > SCHEMA_VERSION) {
            throw new StoreException("The store at " + path + " has schema version " + schemaVersion
                    + ", this build supports up to " + SCHEMA_VERSION);
        }
        try {
            return new StoreMeta(schemaVersion, product, UUID.fromString(new String(installIdBytes, StandardCharsets.UTF_8)));
        } catch (IllegalArgumentException e) {
            throw new StoreException("The meta of the store at " + path + " is corrupt: install id is not a UUID", e);
        }
    }

    /**
     * Waits for the running operations, syncs the WAL and releases every native handle. Safe to call more than once.
     */
    @PreDestroy
    public void close() {
        lock.writeLock().lock();
        try {
            boolean wasOpen = open;
            long startNanos = nanoClock.getAsLong();
            if (wasOpen) {
                try {
                    db.syncWal();
                } catch (RocksDBException e) {
                    log.error("Failed to sync the WAL of the store at {} before closing", properties.getPath(), e);
                }
            }
            open = false;
            releaseNativeResources();
            long tookMs = TimeUnit.NANOSECONDS.toMillis(nanoClock.getAsLong() - startNanos);
            if (wasOpen && tookMs > SLOW_CLOSE_MS) {
                // on a slow disk this is the part of the shutdown that can outlast the stop timeout of the container
                log.warn("The final WAL sync and close of the store at {} took {} ms", properties.getPath(), tookMs);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void releaseNativeResources() {
        handles.values().forEach(ColumnFamilyHandle::close);
        handles.clear();
        otherHandles.forEach(ColumnFamilyHandle::close);
        otherHandles.clear();
        if (db != null) {
            db.close();
            db = null;
        }
        for (int i = nativeResources.size() - 1; i >= 0; i--) {
            try {
                nativeResources.get(i).close();
            } catch (Exception e) {
                log.warn("Failed to release a native resource of the store", e);
            }
        }
        nativeResources.clear();
        writeOptions = null;
    }

    private <T extends AutoCloseable> T register(T resource) {
        nativeResources.add(resource);
        return resource;
    }

    /**
     * Collects the writes of {@code body} into one batch and applies it atomically. The whole call runs under the
     * store's shared lock; the batch is closed afterwards and the {@link StoreBatch} stops working. When the body
     * throws, the exception propagates and nothing is written. Syncs the WAL only when {@code store.wal-sync} is
     * {@code always}. The body must not call {@link #close()}.
     * <p>
     * {@code writeClass} says what the batch does to the amount of data, classed as {@link StoreWriteClass} states.
     * Below the free-space watermark a {@link StoreWriteClass#NEW_DATA} batch fails with
     * {@link org.thingsboard.mqtt.broker.dao.store.StoreFullException} before the body runs.
     */
    public void write(StoreWriteClass writeClass, Consumer<StoreBatch> body) {
        refuseNewDataBelowWatermark(writeClass);
        RocksDB database = acquire(OP_WRITE, null);
        try (WriteBatch writeBatch = new WriteBatch()) {
            StoreBatch batch = new StoreBatch(writeBatch, handles);
            try {
                body.accept(batch);
            } finally {
                batch.deactivate();
            }
            if (batch.count() > 0) {
                database.write(writeOptions, writeBatch);
            }
        } catch (RocksDBException e) {
            throw failure(OP_WRITE, null, e);
        } finally {
            release();
        }
    }

    public byte[] get(StoreColumnFamily cf, byte[] key) {
        RocksDB database = acquire(OP_GET, cf);
        try {
            return database.get(handles.get(cf), key);
        } catch (RocksDBException e) {
            throw failure(OP_GET, cf, e);
        } finally {
            release();
        }
    }

    public void put(StoreWriteClass writeClass, StoreColumnFamily cf, byte[] key, byte[] value) {
        refuseNewDataBelowWatermark(writeClass);
        RocksDB database = acquire(OP_PUT, cf);
        try {
            database.put(handles.get(cf), writeOptions, key, value);
        } catch (RocksDBException e) {
            throw failure(OP_PUT, cf, e);
        } finally {
            release();
        }
    }

    /**
     * Puts a key of the {@code meta} column family with a WAL sync, whatever {@code store.wal-sync} says: a meta key
     * records a state that must survive a crash right after it is written.
     */
    void putMetaSynced(byte[] key, byte[] value) {
        StoreColumnFamily cf = StoreColumnFamily.META;
        RocksDB database = acquire(OP_PUT, cf);
        try (WriteOptions synced = new WriteOptions().setSync(true)) {
            database.put(handles.get(cf), synced, key, value);
        } catch (RocksDBException e) {
            throw failure(OP_PUT, cf, e);
        } finally {
            release();
        }
    }

    public void delete(StoreWriteClass writeClass, StoreColumnFamily cf, byte[] key) {
        RocksDB database = acquire(OP_DELETE, cf);
        try {
            database.delete(handles.get(cf), writeOptions, key);
        } catch (RocksDBException e) {
            throw failure(OP_DELETE, cf, e);
        } finally {
            release();
        }
    }

    /**
     * Reads the keys in {@code [lowerInclusive, upperExclusive)} in order. A {@code null} bound is open. The iterator
     * is positioned on the first key of the range and is valid only inside {@code reader}: it must not escape, and
     * the reader must copy what it keeps, and it must not call {@link #close()} (the shared lock cannot be upgraded).
     * The whole call runs under the store's shared lock, so the store cannot close while it reads. A read error
     * anywhere in the range fails the scan with {@link StoreException}, after the reader returns.
     * <p>
     * In {@code device_msg}, whose keys start with an 8-byte queue prefix, the scan uses the prefix Bloom filter when
     * both bounds share one 8-byte prefix and a total-order scan otherwise, so a range that crosses prefixes is read
     * completely.
     */
    public <T> T scan(StoreColumnFamily cf, byte[] lowerInclusive, byte[] upperExclusive, Function<RocksIterator, T> reader) {
        RocksDB database = acquire(OP_SCAN, cf);
        try {
            return scanRaw(database, cf, lowerInclusive, upperExclusive, reader);
        } catch (RocksDBException e) {
            throw failure(OP_SCAN, cf, e);
        } finally {
            release();
        }
    }

    private <T> T scanRaw(RocksDB database, StoreColumnFamily cf, byte[] lowerInclusive, byte[] upperExclusive,
                          Function<RocksIterator, T> reader) throws RocksDBException {
        try (Slice lower = lowerInclusive == null ? null : new Slice(lowerInclusive);
             Slice upper = upperExclusive == null ? null : new Slice(upperExclusive);
             ReadOptions readOptions = scanOptions(cf, lower, upper, lowerInclusive, upperExclusive);
             RocksIterator iterator = database.newIterator(handles.get(cf), readOptions)) {
            if (lowerInclusive == null) {
                iterator.seekToFirst();
            } else {
                iterator.seek(lowerInclusive);
            }
            T result = reader.apply(iterator);
            // an iterator that hits a read error just becomes invalid, which looks like the end of the range
            iterator.status();
            return result;
        }
    }

    private static ReadOptions scanOptions(StoreColumnFamily cf, Slice lower, Slice upper,
                                           byte[] lowerBound, byte[] upperBound) {
        ReadOptions options = new ReadOptions();
        if (lower != null) {
            options.setIterateLowerBound(lower);
        }
        if (upper != null) {
            options.setIterateUpperBound(upper);
        }
        if (cf == StoreColumnFamily.DEVICE_MSG && !shareKeyPrefix(lowerBound, upperBound)) {
            options.setTotalOrderSeek(true);
        }
        return options;
    }

    private static boolean shareKeyPrefix(byte[] a, byte[] b) {
        return a != null && b != null
                && a.length >= DEVICE_MSG_KEY_PREFIX_LENGTH && b.length >= DEVICE_MSG_KEY_PREFIX_LENGTH
                && Arrays.equals(a, 0, DEVICE_MSG_KEY_PREFIX_LENGTH, b, 0, DEVICE_MSG_KEY_PREFIX_LENGTH);
    }

    public void syncWal() {
        RocksDB database = acquire(OP_SYNC_WAL, null);
        try {
            database.syncWal();
        } catch (RocksDBException e) {
            throw failure(OP_SYNC_WAL, null, e);
        } finally {
            release();
        }
    }

    /**
     * Writes a consistent point-in-time copy of the store into {@code target}, which must not exist; its parent
     * directories are created. On the volume of the store a checkpoint is made of hard links and costs no extra space
     * until the store compacts the linked files away. The directory opens as a store.
     */
    public void createCheckpoint(Path target) {
        RocksDB database = acquire(OP_CHECKPOINT, null);
        try {
            checkpoint(database, target);
        } finally {
            release();
        }
    }

    /**
     * Takes the checkpoint of an open {@code database}; the caller holds the lock. Takes the raw handle, so that a
     * caller that is still opening the store can use it before the store is published as open.
     */
    private void checkpoint(RocksDB database, Path target) {
        if (Files.exists(target)) {
            throw new StoreException("Cannot create a checkpoint of the store at " + properties.getPath()
                    + ": the target " + target + " already exists");
        }
        try {
            Path parent = target.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Checkpoint checkpoint = Checkpoint.create(database)) {
                checkpoint.createCheckpoint(target.toAbsolutePath().toString());
            }
        } catch (RocksDBException | IOException e) {
            throw new StoreException("Failed to create a checkpoint of the store at " + properties.getPath()
                    + " in " + target + " (check store.backups.path): " + e, e);
        }
    }

    /**
     * The record of an unfinished upgrade, or {@code null} when there is none or it is malformed. An open store normally
     * holds none: an upgrade finishes before the store is published as open.
     */
    public StoreMigrationRunner.UpgradeRecord upgradeRecord() {
        byte[] stored = get(StoreColumnFamily.META, META_UPGRADE_CHECKPOINT_KEY);
        return stored == null ? null : StoreMigrationRunner.UpgradeRecord.decode(stored);
    }

    public StoreMeta meta() {
        acquire(OP_META, null);
        try {
            return meta;
        } finally {
            release();
        }
    }

    /**
     * Whether every write syncs the WAL ({@code store.wal-sync=always}).
     */
    boolean syncsEveryWrite() {
        acquire(OP_META, null);
        try {
            return writeOptions.sync();
        } finally {
            release();
        }
    }

    private void refuseNewDataBelowWatermark(StoreWriteClass writeClass) {
        if (writeClass == StoreWriteClass.NEW_DATA && diskSpaceMonitor.belowWatermark()) {
            throw diskSpaceMonitor.storeFullException();
        }
    }

    /**
     * Whether the store takes writes again after a background error; the recovery callback of the
     * {@link DiskSpaceMonitor}. Applies one batch to {@code meta} that puts and deletes a reserved key, so nothing is
     * stored. A probe write, because RocksJava 9.10.0 has no {@code DB::Resume()} and its
     * {@code onErrorRecoveryCompleted} fires for failed recoveries too: while RocksDB holds a background error it refuses
     * every write. RocksDB recovers by itself (out of space through the SST file manager, retryable I/O errors through
     * its auto-resume); an error it never recovers from keeps failing the probe and stays degraded until a restart.
     */
    boolean resumeAfterBackgroundError() {
        try {
            write(StoreWriteClass.UPDATE, batch -> {
                batch.put(StoreColumnFamily.META, META_RESUME_PROBE_KEY, new byte[0]);
                batch.delete(StoreColumnFamily.META, META_RESUME_PROBE_KEY);
            });
            return true;
        } catch (StoreException e) {
            log.debug("The store at {} does not take writes yet after a background error", properties.getPath(), e);
            return false;
        }
    }

    /**
     * Takes the shared lock, which the caller releases with {@link #release()} in a {@code finally}. When the store
     * is not open the lock is released here and the operation fails.
     */
    private RocksDB acquire(String operation, StoreColumnFamily cf) {
        lock.readLock().lock();
        if (!open) {
            lock.readLock().unlock();
            throw new StoreException("Cannot " + describe(operation, cf) + ": the store at " + properties.getPath()
                    + " is not open");
        }
        return db;
    }

    private void release() {
        lock.readLock().unlock();
    }

    private StoreException failure(String operation, StoreColumnFamily cf, RocksDBException e) {
        return new StoreException("Failed to " + describe(operation, cf) + " in the store at " + properties.getPath()
                + ": " + e.getMessage(), e);
    }

    private static String describe(String operation, StoreColumnFamily cf) {
        return cf == null ? operation : operation + " " + cf.columnFamilyName();
    }

}
