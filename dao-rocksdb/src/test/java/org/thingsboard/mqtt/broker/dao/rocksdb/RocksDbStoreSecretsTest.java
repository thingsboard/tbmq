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

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RocksDbStoreSecretsTest {

    @TempDir
    Path dir;

    private RocksDbStore store;

    @AfterEach
    void closeStore() {
        store.close();
    }

    @Test
    void theJwtSigningKeyIsTheMetaKeyOfTheStoreAndStaysTheSame() {
        StoreProperties properties = new StoreProperties();
        properties.setPath(dir.toString());
        properties.setWalSync("periodic");
        store = new RocksDbStore(properties, StoreMemoryBudget.forContainerMemory(1L << 30, 0, 0), TestDiskSpaceMonitors.roomy(properties));
        store.open();
        RocksDbStoreSecrets secrets = new RocksDbStoreSecrets(store);

        assertThat(secrets.jwtSigningKey()).hasSize(64)
                .isEqualTo(store.get(StoreColumnFamily.META, "jwt_signing_key".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(secrets.jwtSigningKey());
    }

}
