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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.common.stats.DefaultStatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsType;
import org.thingsboard.mqtt.broker.dao.store.StoreMemoryUsage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RocksDbStoreMemoryGaugesTest {

    private MeterRegistry registry;
    private RocksDbStore store;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        store = mock(RocksDbStore.class);
        when(store.memoryUsage()).thenReturn(new StoreMemoryUsage(1, 2, 3, 4));
        new RocksDbStoreMemoryGauges(new DefaultStatsFactory(registry), store).registerGauges();
    }

    @Test
    void eachGaugeReadsItsFieldOfTheMemoryUsageAtScrapeTime() {
        assertThat(gauge(StatsType.STORE_BLOCK_CACHE_USAGE)).isEqualTo(1);
        assertThat(gauge(StatsType.STORE_BLOCK_CACHE_PINNED_USAGE)).isEqualTo(2);
        assertThat(gauge(StatsType.STORE_MEMTABLES_SIZE)).isEqualTo(3);
        assertThat(gauge(StatsType.STORE_TABLE_READERS_MEM)).isEqualTo(4);

        when(store.memoryUsage()).thenReturn(new StoreMemoryUsage(10, 20, 30, 40));

        assertThat(gauge(StatsType.STORE_BLOCK_CACHE_USAGE)).isEqualTo(10);
        assertThat(gauge(StatsType.STORE_BLOCK_CACHE_PINNED_USAGE)).isEqualTo(20);
        assertThat(gauge(StatsType.STORE_MEMTABLES_SIZE)).isEqualTo(30);
        assertThat(gauge(StatsType.STORE_TABLE_READERS_MEM)).isEqualTo(40);
    }

    @Test
    void theGaugesAreNamedAfterTheStatsTypes() {
        assertThat(StatsType.STORE_BLOCK_CACHE_USAGE.getPrintName()).isEqualTo("storeBlockCacheUsage");
        assertThat(StatsType.STORE_BLOCK_CACHE_PINNED_USAGE.getPrintName()).isEqualTo("storeBlockCachePinnedUsage");
        assertThat(StatsType.STORE_MEMTABLES_SIZE.getPrintName()).isEqualTo("storeMemtablesSize");
        assertThat(StatsType.STORE_TABLE_READERS_MEM.getPrintName()).isEqualTo("storeTableReadersMem");
    }

    private double gauge(StatsType type) {
        return registry.get(type.getPrintName()).gauge().value();
    }

}
