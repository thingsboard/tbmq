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

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "store")
public class StoreProperties {

    /**
     * Directory of the RocksDB store.
     */
    private String path;
    /**
     * {@code periodic} syncs the WAL every {@link #walSyncIntervalMs}; {@code always} syncs on every write.
     */
    private String walSync = "periodic";
    private long walSyncIntervalMs = 1000;
    private Memory memory = new Memory();

    @Data
    public static class Memory {

        /**
         * Block cache size (in bytes); 0 derives it from the container memory.
         */
        private long blockCacheBytes;
        /**
         * Memtable budget (in bytes); 0 derives it from the block cache.
         */
        private long writeBufferBytes;

    }

}
