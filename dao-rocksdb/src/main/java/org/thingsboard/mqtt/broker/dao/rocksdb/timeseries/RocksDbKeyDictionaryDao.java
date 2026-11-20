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
package org.thingsboard.mqtt.broker.dao.rocksdb.timeseries;

import com.google.common.primitives.Ints;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.dao.dictionary.KeyDictionaryDao;
import org.thingsboard.mqtt.broker.dao.rocksdb.RocksDbStore;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreColumnFamily;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreWriteClass;
import org.thingsboard.mqtt.broker.dao.rocksdb.entity.EntityIndex;
import org.thingsboard.mqtt.broker.dao.rocksdb.entity.EntityKeys;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The time series key dictionary of the standalone edition: one key string to one key id. An entry is written straight
 * into {@code entity_index}, not through the entity store: the key is {@link EntityKeys#uniqueIndexKey} of
 * {@link EntityIndex#TS_KEY_DICTIONARY} and the key string, the value is the id as 4 big-endian bytes.
 * <p>
 * Every entry is read into two maps when the DAO is created, and the maps answer every lookup. A new key gets the
 * highest id so far plus one, under the lock of the DAO, which is the only writer of the entries, so an id is never
 * reused. The first id is 1, as the serial of the SQL table starts.
 */
@Component
@StandaloneComponent
public class RocksDbKeyDictionaryDao implements KeyDictionaryDao {

    private static final StoreColumnFamily FAMILY = StoreColumnFamily.ENTITY_INDEX;
    // the index code and the 4-byte length of the key string
    private static final int KEY_STRING_OFFSET = 1 + Integer.BYTES;

    private final RocksDbStore store;
    private final ConcurrentMap<String, Integer> idsByKey = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, String> keysById = new ConcurrentHashMap<>();
    private final Object writeLock = new Object();
    // guarded by writeLock
    private int maxId;

    public RocksDbKeyDictionaryDao(RocksDbStore store) {
        this.store = store;
        byte[] lower = new byte[]{EntityIndex.TS_KEY_DICTIONARY.code()};
        store.scan(FAMILY, lower, EntityKeys.upperBound(lower), iterator -> {
            for (; iterator.isValid(); iterator.next()) {
                byte[] entryKey = iterator.key();
                String key = new String(Arrays.copyOfRange(entryKey, KEY_STRING_OFFSET, entryKey.length), StandardCharsets.UTF_8);
                track(key, Ints.fromByteArray(iterator.value()));
            }
            return null;
        });
    }

    @Override
    public Integer getOrSaveKeyId(String strKey) {
        Integer keyId = idsByKey.get(strKey);
        if (keyId != null) {
            return keyId;
        }
        synchronized (writeLock) {
            keyId = idsByKey.get(strKey);
            if (keyId != null) {
                return keyId;
            }
            int newId = maxId + 1;
            store.put(StoreWriteClass.NEW_DATA, FAMILY, EntityKeys.uniqueIndexKey(EntityIndex.TS_KEY_DICTIONARY, strKey),
                    Ints.toByteArray(newId));
            track(strKey, newId);
            return newId;
        }
    }

    @Override
    public String getKey(Integer keyId) {
        return keysById.get(keyId);
    }

    @Override
    public Integer getKeyId(String key) {
        return idsByKey.get(key);
    }

    private void track(String key, int id) {
        keysById.put(id, key);
        idsByKey.put(key, id);
        maxId = Math.max(maxId, id);
    }

}
