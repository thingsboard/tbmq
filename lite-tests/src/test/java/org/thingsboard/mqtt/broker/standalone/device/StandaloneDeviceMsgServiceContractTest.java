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
package org.thingsboard.mqtt.broker.standalone.device;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.thingsboard.mqtt.broker.dao.rocksdb.RocksDbStore;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreMemoryBudget;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreProperties;
import org.thingsboard.mqtt.broker.dao.rocksdb.devicemsg.RocksDbDeviceMsgQueueStore;
import org.thingsboard.mqtt.broker.dao.service.DeviceMsgServiceContract;

import java.nio.file.Path;
import java.time.Clock;

/**
 * Runs the checks the Valkey implementation passes in DeviceMsgServiceTest against the store-backed service.
 */
class StandaloneDeviceMsgServiceContractTest {

    private static final int LIMIT = 10;

    @TempDir
    Path dir;

    @Test
    void theStoreBackedServicePassesTheContract() {
        StoreProperties properties = new StoreProperties();
        properties.setPath(dir.toString());
        RocksDbStore rocksDb = new RocksDbStore(properties, StoreMemoryBudget.forContainerMemory(1L << 30, 0, 0));
        rocksDb.open();
        try {
            var service = new StandaloneDeviceMsgService(new RocksDbDeviceMsgQueueStore(rocksDb, LIMIT, 600, Clock.systemUTC()));

            new DeviceMsgServiceContract(service, LIMIT).runAll();
        } finally {
            rocksDb.close();
        }
    }

}
