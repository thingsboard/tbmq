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

import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.dao.store.StoreSeedState;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * The seed marker in the {@code meta} column family of the {@link RocksDbStore}: key {@code seeded}, value the one
 * byte {@code 1}, written with a WAL sync whatever {@code store.wal-sync} says.
 */
@Component
@StandaloneComponent
public class RocksDbStoreSeedState implements StoreSeedState {

    static final byte[] SEEDED_KEY = "seeded".getBytes(StandardCharsets.UTF_8);
    static final byte[] SEEDED_VALUE = {1};

    private final RocksDbStore store;
    private volatile boolean seededInThisStart;

    public RocksDbStoreSeedState(RocksDbStore store) {
        this.store = store;
    }

    @Override
    public boolean isSeeded() {
        return Arrays.equals(store.get(StoreColumnFamily.META, SEEDED_KEY), SEEDED_VALUE);
    }

    @Override
    public void markSeeded() {
        store.putMetaSynced(SEEDED_KEY, SEEDED_VALUE);
        seededInThisStart = true;
    }

    @Override
    public boolean seededInThisStart() {
        return seededInThisStart;
    }

}
