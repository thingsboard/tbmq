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
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The embedded RocksDB store of the standalone edition: one database under {@code store.path}, one column family
 * per {@link StoreColumnFamily}. Values are opaque bytes. Every {@link RocksDBException} surfaces as a
 * {@link StoreException} that names the failed operation.
 */
@Slf4j
@Component
public class RocksDbStore {

    public static final int SCHEMA_VERSION = 1;
    public static final String PRODUCT = "LITE";

    static final byte[] META_SCHEMA_VERSION_KEY = "schema_version".getBytes(StandardCharsets.UTF_8);
    static final byte[] META_PRODUCT_KEY = "product".getBytes(StandardCharsets.UTF_8);
    static final byte[] META_INSTALL_ID_KEY = "install_id".getBytes(StandardCharsets.UTF_8);

    private static final String WAL_SYNC_ALWAYS = "always";
    private static final int MAX_OPEN_FILES = 256;
    private static final int DEVICE_MSG_KEY_PREFIX_LENGTH = 8;
    private static final double BLOOM_BITS_PER_KEY = 10;

    private final StoreProperties properties;
    private final StoreMemoryBudget memoryBudget;

    private final Map<StoreColumnFamily, ColumnFamilyHandle> handles = new EnumMap<>(StoreColumnFamily.class);
    // native objects the database depends on, in creation order; closed in reverse once the database is closed
    private final List<AutoCloseable> nativeResources = new ArrayList<>();

    private ColumnFamilyHandle defaultHandle;
    private WriteOptions writeOptions;
    private StoreMeta meta;
    private volatile RocksDB db;

    public RocksDbStore(StoreProperties properties, StoreMemoryBudget memoryBudget) {
        this.properties = properties;
        this.memoryBudget = memoryBudget;
    }

    @PostConstruct
    public synchronized void open() {
        String path = properties.getPath();
        if (path == null || path.isBlank()) {
            throw new StoreException("Cannot open the store: store.path is not set");
        }
        if (db != null) {
            throw new StoreException("The store at " + path + " is already open");
        }
        try {
            Files.createDirectories(Path.of(path));
        } catch (IOException e) {
            throw new StoreException("Cannot create the store directory " + path + ": " + e, e);
        }
        try {
            RocksDB.loadLibrary();
            doOpen(path);
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

    private void doOpen(String path) throws RocksDBException {
        Cache blockCache = register(new LRUCache(memoryBudget.blockCacheBytes()));
        WriteBufferManager writeBufferManager = register(new WriteBufferManager(memoryBudget.writeBufferBytes(), blockCache));
        DBOptions dbOptions = register(new DBOptions()
                .setCreateIfMissing(true)
                .setCreateMissingColumnFamilies(true)
                .setMaxOpenFiles(MAX_OPEN_FILES)
                .setWriteBufferManager(writeBufferManager));
        BloomFilter deviceMsgFilter = register(new BloomFilter(BLOOM_BITS_PER_KEY));
        writeOptions = register(new WriteOptions().setSync(WAL_SYNC_ALWAYS.equals(properties.getWalSync())));

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

        List<ColumnFamilyHandle> opened = new ArrayList<>();
        db = RocksDB.open(dbOptions, path, descriptors, opened);
        defaultHandle = opened.get(0);
        for (StoreColumnFamily cf : StoreColumnFamily.values()) {
            handles.put(cf, opened.get(cf.ordinal() + 1));
        }
        meta = loadOrInitMeta(path);
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
     * Syncs the WAL and releases every native handle. Safe to call more than once.
     */
    @PreDestroy
    public synchronized void close() {
        if (db != null) {
            try {
                db.syncWal();
            } catch (RocksDBException e) {
                log.error("Failed to sync the WAL of the store at {} before closing", properties.getPath(), e);
            }
        }
        releaseNativeResources();
    }

    private void releaseNativeResources() {
        for (ColumnFamilyHandle handle : handles.values()) {
            handle.close();
        }
        handles.clear();
        if (defaultHandle != null) {
            defaultHandle.close();
            defaultHandle = null;
        }
        RocksDB database = db;
        db = null;
        if (database != null) {
            database.close();
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
     * Applies the batch atomically. Syncs the WAL only when {@code store.wal-sync} is {@code always}.
     */
    public void write(WriteBatch batch) {
        try {
            database().write(writeOptions, batch);
        } catch (RocksDBException e) {
            throw failure("write a batch", e);
        }
    }

    public byte[] get(StoreColumnFamily cf, byte[] key) {
        try {
            return database().get(handle(cf), key);
        } catch (RocksDBException e) {
            throw failure("get from " + cf.columnFamilyName(), e);
        }
    }

    public void put(StoreColumnFamily cf, byte[] key, byte[] value) {
        try {
            database().put(handle(cf), writeOptions, key, value);
        } catch (RocksDBException e) {
            throw failure("put to " + cf.columnFamilyName(), e);
        }
    }

    public void delete(StoreColumnFamily cf, byte[] key) {
        try {
            database().delete(handle(cf), writeOptions, key);
        } catch (RocksDBException e) {
            throw failure("delete from " + cf.columnFamilyName(), e);
        }
    }

    /**
     * The caller closes the iterator.
     */
    public RocksIterator newIterator(StoreColumnFamily cf) {
        return database().newIterator(handle(cf));
    }

    public ColumnFamilyHandle handle(StoreColumnFamily cf) {
        database();
        return handles.get(cf);
    }

    public void syncWal() {
        try {
            database().syncWal();
        } catch (RocksDBException e) {
            throw failure("sync the WAL", e);
        }
    }

    public StoreMeta meta() {
        database();
        return meta;
    }

    private RocksDB database() {
        RocksDB database = db;
        if (database == null) {
            throw new StoreException("The store at " + properties.getPath() + " is not open");
        }
        return database;
    }

    private StoreException failure(String operation, RocksDBException e) {
        return new StoreException("Failed to " + operation + " in the store at " + properties.getPath() + ": " + e.getMessage(), e);
    }

}
