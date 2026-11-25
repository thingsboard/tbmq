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
package org.thingsboard.mqtt.broker.dao.store;

/**
 * The memory the store holds, in bytes, as RocksDB reports it. The figures overlap and must not be summed: the
 * block cache also holds the write-buffer (memtable) reservation and the cached index and filter blocks.
 *
 * @param blockCacheUsage       the bytes in the block cache, including the memtable reservation and the cached index and filter blocks
 * @param blockCachePinnedUsage the bytes of the block cache that are pinned and cannot be evicted
 * @param memtablesSize         the size of all memtables, active and immutable
 * @param tableReadersMem       the estimated memory of the readers of the open SST files, without the index and filter blocks held in the block cache
 */
public record StoreMemoryUsage(long blockCacheUsage, long blockCachePinnedUsage, long memtablesSize, long tableReadersMem) {

    public static final StoreMemoryUsage ZERO = new StoreMemoryUsage(0, 0, 0, 0);

}
