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

import org.rocksdb.RocksIterator;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreColumnFamily;

import java.util.function.Function;

/**
 * The store as a {@link StoreMigrationStep} sees it while the store opens: before it serves anything. Every write
 * syncs the WAL; there is no write class and no free-space watermark. The iterator of {@link #scan} is valid only
 * inside {@code reader}, as in {@code RocksDbStore#scan}.
 */
public interface MigrationContext {

    byte[] get(StoreColumnFamily cf, byte[] key);

    void put(StoreColumnFamily cf, byte[] key, byte[] value);

    void delete(StoreColumnFamily cf, byte[] key);

    /**
     * Reads the keys in {@code [lo, hi)} in order; a {@code null} bound is open.
     */
    <T> T scan(StoreColumnFamily cf, byte[] lo, byte[] hi, Function<RocksIterator, T> reader);

    /**
     * Writes the per-install JWT signing key unless the store has one.
     */
    void ensureJwtSigningKey();

}
