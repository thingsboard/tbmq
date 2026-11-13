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

import org.thingsboard.mqtt.broker.dao.store.KeyValueStore;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * A {@link KeyValueStore} over one column family of the {@link RocksDbStore}. Keys are stored as UTF-8 bytes.
 * {@link #put} and {@link #delete} go through {@link RocksDbStore#put} and {@link RocksDbStore#delete} with the
 * store's write options, and the batch of {@link #retainKeys} through {@link RocksDbStore#write}, so all of them
 * follow the WAL-sync mode.
 */
class RocksDbKeyValueStore implements KeyValueStore {

    private final RocksDbStore store;
    private final StoreColumnFamily columnFamily;

    RocksDbKeyValueStore(RocksDbStore store, StoreColumnFamily columnFamily) {
        this.store = store;
        this.columnFamily = columnFamily;
    }

    @Override
    public void put(String key, byte[] value) {
        store.put(columnFamily, encode(key), value);
    }

    @Override
    public byte[] get(String key) {
        return store.get(columnFamily, encode(key));
    }

    @Override
    public void delete(String key) {
        store.delete(columnFamily, encode(key));
    }

    /**
     * Runs the consumer inside {@link RocksDbStore#scan}, under the store's shared lock: it may read and write the
     * store, but must not close it.
     */
    @Override
    public void forEach(BiConsumer<String, byte[]> consumer) {
        store.scan(columnFamily, null, null, iterator -> {
            for (; iterator.isValid(); iterator.next()) {
                consumer.accept(decode(iterator.key()), iterator.value());
            }
            return null;
        });
    }

    /**
     * Collects the rejected keys during the scan and deletes them afterwards in one batch, so the predicate runs
     * under the shared lock and must not close the store.
     */
    @Override
    public int retainKeys(Predicate<String> keep) {
        List<byte[]> rejected = store.scan(columnFamily, null, null, iterator -> {
            List<byte[]> keys = new ArrayList<>();
            for (; iterator.isValid(); iterator.next()) {
                byte[] key = iterator.key();
                if (!keep.test(decode(key))) {
                    keys.add(key);
                }
            }
            return keys;
        });
        if (!rejected.isEmpty()) {
            store.write(batch -> rejected.forEach(key -> batch.delete(columnFamily, key)));
        }
        return rejected.size();
    }

    private static byte[] encode(String key) {
        return key.getBytes(StandardCharsets.UTF_8);
    }

    private static String decode(byte[] key) {
        return new String(key, StandardCharsets.UTF_8);
    }

}
