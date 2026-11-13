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
package org.thingsboard.mqtt.broker.standalone.cache;

import org.springframework.cache.Cache;
import org.springframework.cache.support.SimpleValueWrapper;
import org.thingsboard.mqtt.broker.dao.store.KeyValueStore;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

/**
 * A Spring {@link Cache} of {@code String} keys and values over a {@link KeyValueStore}, so entries survive a restart
 * and are never evicted. Several caches can share one store: an entry is stored under
 * {@code <cache name> + U+0000 + <key>}, with a tag byte before the UTF-8 value ({@code 0} = cached null, {@code 1} = value).
 */
public class StoreBackedCache implements Cache {

    static final char SEPARATOR = '\u0000';

    private static final byte NULL_TAG = 0;
    private static final byte VALUE_TAG = 1;
    private static final byte[] NULL_ENTRY = {NULL_TAG};

    private final String name;
    private final String keyPrefix;
    private final KeyValueStore store;

    public StoreBackedCache(String name, KeyValueStore store) {
        this.name = name;
        this.keyPrefix = name + SEPARATOR;
        this.store = store;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object getNativeCache() {
        return store;
    }

    @Override
    public ValueWrapper get(Object key) {
        byte[] entry = store.get(storeKey(key));
        return entry == null ? null : new SimpleValueWrapper(decode(entry));
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
        ValueWrapper wrapper = get(key);
        Object value = wrapper == null ? null : wrapper.get();
        if (value != null && !type.isInstance(value)) {
            throw new IllegalStateException("Cached value is not of required type [" + type.getName() + "]: " + value);
        }
        return type.cast(value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Callable<T> valueLoader) {
        ValueWrapper wrapper = get(key);
        if (wrapper != null) {
            return (T) wrapper.get();
        }
        T value;
        try {
            value = valueLoader.call();
        } catch (Exception e) {
            throw new ValueRetrievalException(key, valueLoader, e);
        }
        put(key, value);
        return value;
    }

    @Override
    public void put(Object key, Object value) {
        store.put(storeKey(key), encode(value));
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        String storeKey = storeKey(key);
        byte[] entry = encode(value);
        synchronized (this) {
            byte[] existing = store.get(storeKey);
            if (existing != null) {
                return new SimpleValueWrapper(decode(existing));
            }
            store.put(storeKey, entry);
            return null;
        }
    }

    @Override
    public void evict(Object key) {
        store.delete(storeKey(key));
    }

    @Override
    public boolean evictIfPresent(Object key) {
        String storeKey = storeKey(key);
        boolean present = store.get(storeKey) != null;
        store.delete(storeKey);
        return present;
    }

    @Override
    public void clear() {
        invalidate();
    }

    @Override
    public boolean invalidate() {
        return store.retainKeys(storeKey -> !storeKey.startsWith(keyPrefix)) > 0;
    }

    private String storeKey(Object key) {
        if (!(key instanceof String)) {
            throw new IllegalArgumentException("Cache '" + name + "' supports String keys only, got "
                    + (key == null ? "null" : key.getClass().getName()));
        }
        return keyPrefix + key;
    }

    private byte[] encode(Object value) {
        if (value == null) {
            return NULL_ENTRY;
        }
        if (!(value instanceof String string)) {
            throw new IllegalArgumentException("Cache '" + name + "' supports String values only, got " + value.getClass().getName());
        }
        byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
        byte[] entry = new byte[bytes.length + 1];
        entry[0] = VALUE_TAG;
        System.arraycopy(bytes, 0, entry, 1, bytes.length);
        return entry;
    }

    private static String decode(byte[] entry) {
        return entry[0] == NULL_TAG ? null : new String(entry, 1, entry.length - 1, StandardCharsets.UTF_8);
    }

}
