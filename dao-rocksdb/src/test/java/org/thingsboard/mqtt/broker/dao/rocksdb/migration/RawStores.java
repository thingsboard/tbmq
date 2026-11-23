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

import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreColumnFamily;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes a closed store directory through a raw RocksDB handle, bypassing {@code RocksDbStore}: to make a
 * store look as an older build left it, and to compare its contents byte by byte.
 */
final class RawStores {

    private RawStores() {
    }

    interface RawAction<T> {
        T run(RocksDB db, Map<StoreColumnFamily, ColumnFamilyHandle> handles) throws RocksDBException;
    }

    static <T> T withRaw(Path path, RawAction<T> action) {
        try (DBOptions dbOptions = new DBOptions(); ColumnFamilyOptions options = new ColumnFamilyOptions()) {
            List<ColumnFamilyDescriptor> descriptors = new ArrayList<>();
            descriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, options));
            for (StoreColumnFamily cf : StoreColumnFamily.values()) {
                descriptors.add(new ColumnFamilyDescriptor(bytes(cf.columnFamilyName()), options));
            }
            List<ColumnFamilyHandle> opened = new ArrayList<>();
            try (RocksDB db = RocksDB.open(dbOptions, path.toString(), descriptors, opened)) {
                try {
                    Map<StoreColumnFamily, ColumnFamilyHandle> handles = new EnumMap<>(StoreColumnFamily.class);
                    for (StoreColumnFamily cf : StoreColumnFamily.values()) {
                        handles.put(cf, opened.get(cf.ordinal() + 1));
                    }
                    return action.run(db, handles);
                } finally {
                    opened.forEach(ColumnFamilyHandle::close);
                }
            }
        } catch (RocksDBException e) {
            throw new IllegalStateException(e);
        }
    }

    static byte[] getMeta(Path path, String key) {
        return withRaw(path, (db, handles) -> db.get(handles.get(StoreColumnFamily.META), bytes(key)));
    }

    static void putMeta(Path path, String key, byte[] value) {
        withRaw(path, (db, handles) -> {
            db.put(handles.get(StoreColumnFamily.META), bytes(key), value);
            return null;
        });
    }

    static void deleteMeta(Path path, String key) {
        withRaw(path, (db, handles) -> {
            db.delete(handles.get(StoreColumnFamily.META), bytes(key));
            return null;
        });
    }

    static void put(Path path, StoreColumnFamily cf, byte[] key, byte[] value) {
        withRaw(path, (db, handles) -> {
            db.put(handles.get(cf), key, value);
            return null;
        });
    }

    /**
     * Every (key, value) pair of every column family, as hex {@code key=value} lines in key order.
     */
    static Map<StoreColumnFamily, List<String>> snapshot(Path path) {
        return withRaw(path, (db, handles) -> {
            Map<StoreColumnFamily, List<String>> snapshot = new EnumMap<>(StoreColumnFamily.class);
            for (StoreColumnFamily cf : StoreColumnFamily.values()) {
                List<String> entries = new ArrayList<>();
                try (RocksIterator it = db.newIterator(handles.get(cf))) {
                    for (it.seekToFirst(); it.isValid(); it.next()) {
                        entries.add(HexFormat.of().formatHex(it.key()) + "=" + HexFormat.of().formatHex(it.value()));
                    }
                    it.status();
                }
                snapshot.put(cf, entries);
            }
            return snapshot;
        });
    }

    static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

}
