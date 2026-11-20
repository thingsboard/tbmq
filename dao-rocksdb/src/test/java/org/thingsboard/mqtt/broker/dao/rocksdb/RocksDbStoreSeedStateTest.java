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

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RocksDbStoreSeedStateTest {

    @TempDir
    Path dir;

    private final List<RocksDbStore> opened = new ArrayList<>();

    @AfterEach
    void closeAll() {
        opened.forEach(RocksDbStore::close);
        opened.clear();
    }

    @Test
    void aNewStoreIsNotSeeded() {
        RocksDbStoreSeedState state = new RocksDbStoreSeedState(open());

        assertThat(state.isSeeded()).isFalse();
        assertThat(state.seededInThisStart()).isFalse();
    }

    @Test
    void theMarkerIsTheOneByteOneUnderTheSeededKeyOfMeta() {
        RocksDbStore store = open();
        RocksDbStoreSeedState state = new RocksDbStoreSeedState(store);

        state.markSeeded();

        // the on-disk marker: changing it re-seeds every existing store
        assertThat(store.get(StoreColumnFamily.META, "seeded".getBytes(StandardCharsets.UTF_8))).isEqualTo(new byte[]{1});
        assertThat(state.isSeeded()).isTrue();
        assertThat(state.seededInThisStart()).isTrue();
    }

    @Test
    void aRestartReadsTheMarkerButDoesNotCountAsTheSeedingStart() {
        new RocksDbStoreSeedState(open()).markSeeded();
        closeAll();

        RocksDbStoreSeedState afterRestart = new RocksDbStoreSeedState(open());

        assertThat(afterRestart.isSeeded()).isTrue();
        assertThat(afterRestart.seededInThisStart()).isFalse();
    }

    @Test
    void aSecondMarkIsHarmless() {
        RocksDbStore store = open();
        RocksDbStoreSeedState state = new RocksDbStoreSeedState(store);

        state.markSeeded();
        state.markSeeded();

        assertThat(state.isSeeded()).isTrue();
        assertThat(store.get(StoreColumnFamily.META, "seeded".getBytes(StandardCharsets.UTF_8))).isEqualTo(new byte[]{1});
    }

    private RocksDbStore open() {
        StoreProperties properties = new StoreProperties();
        properties.setPath(dir.toString());
        properties.setWalSync("periodic");
        RocksDbStore store = new RocksDbStore(properties, StoreMemoryBudget.forContainerMemory(1L << 30, 0, 0));
        opened.add(store);
        store.open();
        return store;
    }

}
