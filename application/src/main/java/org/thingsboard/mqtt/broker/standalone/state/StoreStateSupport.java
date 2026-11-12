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

import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.dao.store.KeyValueStore;
import org.thingsboard.mqtt.broker.dao.store.StoreException;
import org.thingsboard.mqtt.broker.exception.QueuePersistenceException;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * The write and load steps shared by the standalone persistence services and consumers of the broker state.
 */
@Slf4j
final class StoreStateSupport {

    private StoreStateSupport() {
    }

    /**
     * Runs the write, then reports the outcome. The callback fires only after the write has finished. A failed write
     * goes to the callback, or is rethrown when there is none, so that a failure is never swallowed.
     */
    static void writeThenCallback(Runnable write, BasicCallback callback, Consumer<StoreException> logFailure) {
        try {
            write.run();
        } catch (StoreException e) {
            logFailure.accept(e);
            if (callback == null) {
                throw e;
            }
            callback.onFailure(e);
            return;
        }
        if (callback != null) {
            callback.onSuccess();
        }
    }

    /**
     * Reads and parses every entry of the store. An entry that cannot be parsed fails the whole load: starting
     * with part of the broker state missing is worse than not starting.
     *
     * @param what the human-readable name of the state (for example "client sessions"), used in messages
     */
    static <T> Map<String, T> loadAll(KeyValueStore store, String what, EntryParser<T> parser) throws QueuePersistenceException {
        Map<String, T> loaded = new HashMap<>();
        AtomicReference<String> unreadableKey = new AtomicReference<>();
        AtomicReference<Exception> unreadableCause = new AtomicReference<>();
        try {
            store.forEach((key, bytes) -> {
                if (unreadableKey.get() != null) {
                    return;
                }
                try {
                    loaded.put(key, parser.parse(bytes));
                } catch (Exception e) {
                    unreadableKey.set(key);
                    unreadableCause.set(e);
                }
            });
        } catch (StoreException e) {
            log.error("Failed to load {}.", what, e);
            throw new QueuePersistenceException("Failed to load " + what, e);
        }
        if (unreadableKey.get() != null) {
            String message = "Failed to load " + what + ": the stored entry with key '" + unreadableKey.get() + "' cannot be read";
            log.error("{}.", message, unreadableCause.get());
            throw new QueuePersistenceException(message, unreadableCause.get());
        }
        return loaded;
    }

    @FunctionalInterface
    interface EntryParser<T> {

        T parse(byte[] bytes) throws Exception;

    }

}
