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

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteBatch;
import org.thingsboard.mqtt.broker.dao.store.StoreException;

import java.util.Map;

/**
 * The writes of one atomic batch, see {@link RocksDbStore#write}. Valid only inside the body that received it and
 * only on the thread running that body: using it afterwards fails with {@link IllegalStateException}.
 */
public final class StoreBatch {

    private final WriteBatch batch;
    private final Map<StoreColumnFamily, ColumnFamilyHandle> handles;
    private volatile boolean active = true;

    StoreBatch(WriteBatch batch, Map<StoreColumnFamily, ColumnFamilyHandle> handles) {
        this.batch = batch;
        this.handles = handles;
    }

    public void put(StoreColumnFamily cf, byte[] key, byte[] value) {
        requireActive();
        try {
            batch.put(handles.get(cf), key, value);
        } catch (RocksDBException e) {
            throw failure("put to", cf, e);
        }
    }

    public void delete(StoreColumnFamily cf, byte[] key) {
        requireActive();
        try {
            batch.delete(handles.get(cf), key);
        } catch (RocksDBException e) {
            throw failure("delete from", cf, e);
        }
    }

    /**
     * Deletes every key in {@code [beginInclusive, endExclusive)}.
     */
    public void deleteRange(StoreColumnFamily cf, byte[] beginInclusive, byte[] endExclusive) {
        requireActive();
        try {
            batch.deleteRange(handles.get(cf), beginInclusive, endExclusive);
        } catch (RocksDBException e) {
            throw failure("delete a range from", cf, e);
        }
    }

    int count() {
        return batch.count();
    }

    void deactivate() {
        active = false;
    }

    private void requireActive() {
        if (!active) {
            throw new IllegalStateException("The batch is used after its write ended");
        }
    }

    private static StoreException failure(String operation, StoreColumnFamily cf, RocksDBException e) {
        return new StoreException("Failed to " + operation + " " + cf.columnFamilyName() + " in a batch: " + e.getMessage(), e);
    }

}
