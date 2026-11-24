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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.thingsboard.mqtt.broker.dao.rocksdb.disk.TestDiskSpaceMonitors;
import org.thingsboard.mqtt.broker.dao.store.StoreMemoryUsage;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class RocksDbStoreMemoryUsageTest {

    private static final int KEYS = 1_000;

    @TempDir
    Path dir;

    private RocksDbStore store;

    @AfterEach
    void closeStore() {
        if (store != null) {
            store.close();
        }
    }

    @Test
    void anOpenedStoreWithWritesReportsItsMemtables() {
        store = newStore();
        store.open();
        putKeys();

        StoreMemoryUsage usage = store.memoryUsage();

        assertThat(usage.memtablesSize()).isPositive();
        assertThat(usage.blockCacheUsage()).isNotNegative();
        assertThat(usage.blockCachePinnedUsage()).isNotNegative();
        assertThat(usage.tableReadersMem()).isNotNegative();
    }

    @Test
    void afterTheDataIsInSstFilesAndReadTheBlockCacheIsUsed() {
        store = newStore();
        store.open();
        putKeys();
        // the memtables of the closed store are replayed from the WAL into SST files on the next open
        store.close();
        store = newStore();
        store.open();
        for (int i = 0; i < KEYS; i++) {
            assertThat(store.get(StoreColumnFamily.META, key(i))).isNotNull();
        }

        StoreMemoryUsage usage = store.memoryUsage();

        assertThat(usage.blockCacheUsage()).isPositive();
        assertThat(usage.tableReadersMem()).isNotNegative();
    }

    @Test
    void aStoreThatIsNotOpenYetReportsZeros() {
        store = newStore();

        assertThatCode(store::memoryUsage).doesNotThrowAnyException();
        assertThat(store.memoryUsage()).isEqualTo(new StoreMemoryUsage(0, 0, 0, 0));
    }

    @Test
    void aClosedStoreReportsZerosWithoutTouchingTheReleasedHandles() {
        store = newStore();
        store.open();
        putKeys();
        store.close();

        assertThatCode(store::memoryUsage).doesNotThrowAnyException();
        assertThat(store.memoryUsage()).isEqualTo(new StoreMemoryUsage(0, 0, 0, 0));
    }

    private void putKeys() {
        for (int i = 0; i < KEYS; i++) {
            store.put(StoreWriteClass.NEW_DATA, StoreColumnFamily.META, key(i), ("value-" + i).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static byte[] key(int i) {
        return ("key-" + i).getBytes(StandardCharsets.UTF_8);
    }

    private RocksDbStore newStore() {
        StoreProperties properties = new StoreProperties();
        properties.setPath(dir.toString());
        properties.setWalSync("periodic");
        return new RocksDbStore(properties, StoreMemoryBudget.forContainerMemory(1L << 30, 0, 0),
                TestDiskSpaceMonitors.roomy(properties), List.of());
    }

}
