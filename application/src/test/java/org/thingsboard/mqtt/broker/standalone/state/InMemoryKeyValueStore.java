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
package org.thingsboard.mqtt.broker.standalone.state;

import org.thingsboard.mqtt.broker.dao.store.KeyValueStore;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * A {@link KeyValueStore} over a {@link TreeMap}, for tests of the standalone state services. Not thread-safe
 * beyond method-level synchronization; deliberately non-final so tests can spy on it.
 */
public class InMemoryKeyValueStore implements KeyValueStore {

    private final TreeMap<String, byte[]> entries = new TreeMap<>();

    @Override
    public synchronized void put(String key, byte[] value) {
        entries.put(key, value);
    }

    @Override
    public synchronized byte[] get(String key) {
        return entries.get(key);
    }

    @Override
    public synchronized void delete(String key) {
        entries.remove(key);
    }

    @Override
    public synchronized void forEach(BiConsumer<String, byte[]> consumer) {
        new ArrayList<>(entries.entrySet()).forEach(e -> consumer.accept(e.getKey(), e.getValue()));
    }

    @Override
    public synchronized int retainKeys(Predicate<String> keep) {
        int before = entries.size();
        entries.keySet().removeIf(key -> !keep.test(key));
        return before - entries.size();
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized Map<String, byte[]> snapshot() {
        return new TreeMap<>(entries);
    }

}
