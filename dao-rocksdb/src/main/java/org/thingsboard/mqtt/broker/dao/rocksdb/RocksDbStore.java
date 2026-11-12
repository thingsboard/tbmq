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
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.LRUCache;
import org.rocksdb.Options;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.Slice;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteBufferManager;
import org.rocksdb.WriteOptions;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.dao.store.StoreException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The embedded RocksDB store of the standalone edition: one database under {@code store.path}, one column family
 * per {@link StoreColumnFamily}. Values are opaque bytes. Every {@link RocksDBException} surfaces as a
 * {@link StoreException} that names the failed operation.
 * <p>
 * No native handle leaves this class. Every operation, including the whole body of {@link #write} and the reader of
 * {@link #scan}, runs under a shared lock, and {@link #close()} takes the exclusive one: a closing store never frees
 * native memory an operation is using, and an operation on a closed store throws {@link StoreException}.
 */
@Slf4j
@Component
public class RocksDbStore {

    public static final int SCHEMA_VERSION = 1;
    public static final String PRODUCT = "LITE";

    static final byte[] META_SCHEMA_VERSION_KEY = "schema_version".getBytes(StandardCharsets.UTF_8);
    static final byte[] META_PRODUCT_KEY = "product".getBytes(StandardCharsets.UTF_8);
    static final byte[] META_INSTALL_ID_KEY = "install_id".getBytes(StandardCharsets.UTF_8);

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

    private final StoreProperties properties;
    private final StoreMemoryBudget memoryBudget;

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

    public RocksDbStore(StoreProperties properties, StoreMemoryBudget memoryBudget) {
        this.properties = properties;
        this.memoryBudget = memoryBudget;
    }

    @PostConstruct
    public void open() {
        lock.writeLock().lock();
        try {
            doOpenLocked();
        } finally {
            lock.writeLock().unlock();
        }
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
        try {
            Files.createDirectories(Path.of(path));
        } catch (IOException e) {
            throw new StoreException("Cannot create the store directory " + path + ": " + e, e);
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
            throw new StoreException("Cannot open the store at " + path + ": " + e.getMessage(), e);
        } catch (RuntimeException e) {
            releaseNativeResources();
            throw e;
        }
    }

    private void doOpen(String path, WalSyncMode walSyncMode) throws RocksDBException {
        Cache blockCache = register(new LRUCache(memoryBudget.blockCacheBytes()));
        WriteBufferManager writeBufferManager = register(new WriteBufferManager(memoryBudget.writeBufferBytes(), blockCache));
        DBOptions dbOptions = register(new DBOptions()
                .setCreateIfMissing(true)
                .setCreateMissingColumnFamilies(true)
                .setMaxOpenFiles(MAX_OPEN_FILES)
                .setWriteBufferManager(writeBufferManager));
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
    }

    /**
     * Families of an existing database that this build does not know. A store written by a later schema has them, and
     * RocksDB refuses to open a database without all its families, which would hide the readable schema refusal.
     */
    private List<byte[]> unknownColumnFamilies(String path) throws RocksDBException {
        if (!Files.exists(Path.of(path, "CURRENT"))) {
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
            if (open) {
                try {
                    db.syncWal();
                } catch (RocksDBException e) {
                    log.error("Failed to sync the WAL of the store at {} before closing", properties.getPath(), e);
                }
            }
            open = false;
            releaseNativeResources();
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
     */
    public void write(Consumer<StoreBatch> body) {
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

    public void put(StoreColumnFamily cf, byte[] key, byte[] value) {
        RocksDB database = acquire(OP_PUT, cf);
        try {
            database.put(handles.get(cf), writeOptions, key, value);
        } catch (RocksDBException e) {
            throw failure(OP_PUT, cf, e);
        } finally {
            release();
        }
    }

    public void delete(StoreColumnFamily cf, byte[] key) {
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
     * the reader must copy what it keeps. The whole call runs under the store's shared lock, so the store cannot
     * close while it reads.
     * <p>
     * In {@code device_msg}, whose keys start with an 8-byte queue prefix, the scan uses the prefix Bloom filter when
     * both bounds share one 8-byte prefix and a total-order scan otherwise, so a range that crosses prefixes is read
     * completely.
     */
    public <T> T scan(StoreColumnFamily cf, byte[] lowerInclusive, byte[] upperExclusive, Function<RocksIterator, T> reader) {
        RocksDB database = acquire(OP_SCAN, cf);
        try (Slice lower = lowerInclusive == null ? null : new Slice(lowerInclusive);
             Slice upper = upperExclusive == null ? null : new Slice(upperExclusive);
             ReadOptions readOptions = scanOptions(cf, lower, upper, lowerInclusive, upperExclusive);
             RocksIterator iterator = database.newIterator(handles.get(cf), readOptions)) {
            if (lowerInclusive == null) {
                iterator.seekToFirst();
            } else {
                iterator.seek(lowerInclusive);
            }
            return reader.apply(iterator);
        } finally {
            release();
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
