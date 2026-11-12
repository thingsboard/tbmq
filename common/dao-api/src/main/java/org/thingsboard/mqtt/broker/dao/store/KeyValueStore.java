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
package org.thingsboard.mqtt.broker.dao.store;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * A synchronous string-keyed store of opaque byte values: one column family of the embedded broker store. Every
 * failure, and every call on a closed store, throws {@link StoreException}.
 */
public interface KeyValueStore {

    void put(String key, byte[] value);

    /**
     * @return the value of {@code key}, or {@code null} when it is absent
     */
    byte[] get(String key);

    void delete(String key);

    /**
     * Visits every entry, in the store's byte order of the UTF-8 key. The consumer must not close the store.
     */
    void forEach(BiConsumer<String, byte[]> consumer);

    /**
     * Deletes every entry whose key the predicate rejects. The keys are judged in a scan and the deletes are then
     * applied in one atomic batch, so a key written in between can be deleted too: meant for startup and cleanup,
     * not for a store with concurrent writers. When the predicate throws, nothing is deleted.
     *
     * @return the number of deleted entries
     */
    int retainKeys(Predicate<String> keep);

}
