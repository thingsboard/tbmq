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

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The native memory the store may use. Defaults derive from the container memory: the block cache is 10% of it
 * (at least 16 MiB, at most 512 MiB) and the memtables are capped at 25% of the block cache.
 * {@code store.memory.*} values above 0 override the derived ones.
 */
@Slf4j
@Component
public final class StoreMemoryBudget {

    static final long MIN_BLOCK_CACHE_BYTES = 16L << 20;
    static final long MAX_BLOCK_CACHE_BYTES = 512L << 20;

    private static final Path CGROUP_MEMORY_MAX = Path.of("/sys/fs/cgroup/memory.max");

    private final long blockCacheBytes;
    private final long writeBufferBytes;

    @Autowired
    public StoreMemoryBudget(StoreProperties properties) {
        this(readContainerMemoryBytes(),
                properties.getMemory().getBlockCacheBytes(),
                properties.getMemory().getWriteBufferBytes());
    }

    private StoreMemoryBudget(long containerBytes, long blockCacheOverride, long writeBufferOverride) {
        this.blockCacheBytes = blockCacheOverride > 0
                ? blockCacheOverride
                : Math.min(MAX_BLOCK_CACHE_BYTES, Math.max(MIN_BLOCK_CACHE_BYTES, containerBytes / 10));
        this.writeBufferBytes = writeBufferOverride > 0 ? writeBufferOverride : this.blockCacheBytes / 4;
    }

    public static StoreMemoryBudget forContainerMemory(long containerBytes,
                                                       long blockCacheOverride,
                                                       long writeBufferOverride) {
        return new StoreMemoryBudget(containerBytes, blockCacheOverride, writeBufferOverride);
    }

    public long blockCacheBytes() {
        return blockCacheBytes;
    }

    public long writeBufferBytes() {
        return writeBufferBytes;
    }

    /**
     * The cgroup limit when there is one, the total physical memory of the machine otherwise.
     */
    static long readContainerMemoryBytes() {
        long physical = physicalMemoryBytes();
        try {
            if (Files.isReadable(CGROUP_MEMORY_MAX)) {
                return parseCgroupMemoryMax(Files.readString(CGROUP_MEMORY_MAX), physical);
            }
        } catch (IOException e) {
            log.debug("Cannot read {}, using the physical memory", CGROUP_MEMORY_MAX, e);
        }
        return physical;
    }

    /**
     * @param content  the content of {@code memory.max}: a byte count or {@code max}
     * @param fallback used when the content is {@code max} or is not a positive number
     */
    static long parseCgroupMemoryMax(String content, long fallback) {
        String value = content == null ? "" : content.trim();
        try {
            long bytes = Long.parseLong(value);
            return bytes > 0 ? bytes : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long physicalMemoryBytes() {
        return ((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalMemorySize();
    }

}
