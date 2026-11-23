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
    private Disk disk = new Disk();
    private ClockGuard clock = new ClockGuard();
    private Backups backups = new Backups();

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

    @Data
    public static class Disk {

        /**
         * Interval between free-space checks of the store volume (in ms).
         */
        private long checkIntervalMs = 10000;
        /**
         * Watermark as a percentage of the volume size; the larger of this and {@link #minFreeBytes} applies.
         */
        private int minFreePercent = 5;
        /**
         * Watermark floor (in bytes).
         */
        private long minFreeBytes = 268435456L;

    }

    @Data
    public static class ClockGuard {

        /**
         * Interval between recordings of the wall-clock time in the store (in ms).
         */
        private long recordIntervalMs = 60000;
        /**
         * Longest postponement of the age-based cleanups after a start with the clock behind the record (in ms of
         * uptime).
         */
        private long maxPostponeMs = 86400000L;

    }

    @Data
    public static class Backups {

        /**
         * Directory of the checkpoints taken before a store upgrade; on the volume of the store, because a checkpoint
         * is made of hard links.
         */
        private String path;
        /**
         * Number of pre-upgrade checkpoints kept; older ones are deleted after a successful upgrade. Must be positive.
         */
        private int keep = 3;

    }

}
