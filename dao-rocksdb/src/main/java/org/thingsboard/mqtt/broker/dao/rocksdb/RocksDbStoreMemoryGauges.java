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

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsType;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;

/**
 * Exposes the memory figures of the {@link RocksDbStore} as gauges, each read from {@link RocksDbStore#memoryUsage()}
 * on scrape. The store bean is the state object of the gauges: the registry holds it weakly, and the store is a
 * singleton that outlives it.
 */
@Component
@StandaloneComponent
public class RocksDbStoreMemoryGauges {

    private final StatsFactory statsFactory;
    private final RocksDbStore store;

    @Autowired
    public RocksDbStoreMemoryGauges(StatsFactory statsFactory, RocksDbStore store) {
        this.statsFactory = statsFactory;
        this.store = store;
    }

    @PostConstruct
    public void registerGauges() {
        statsFactory.createGauge(StatsType.STORE_BLOCK_CACHE_USAGE.getPrintName(), store,
                s -> s.memoryUsage().blockCacheUsage());
        statsFactory.createGauge(StatsType.STORE_BLOCK_CACHE_PINNED_USAGE.getPrintName(), store,
                s -> s.memoryUsage().blockCachePinnedUsage());
        statsFactory.createGauge(StatsType.STORE_MEMTABLES_SIZE.getPrintName(), store,
                s -> s.memoryUsage().memtablesSize());
        statsFactory.createGauge(StatsType.STORE_TABLE_READERS_MEM.getPrintName(), store,
                s -> s.memoryUsage().tableReadersMem());
    }

}
