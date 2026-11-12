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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StoreMemoryBudgetTest {

    private static final long MIB = 1L << 20;
    private static final long GIB = 1L << 30;

    @Test
    void twoGibibytesGivesA204MebibyteCacheAndA51MebibyteWriteBuffer() {
        var b = StoreMemoryBudget.forContainerMemory(2 * GIB, 0, 0);

        assertThat(b.blockCacheBytes()).isEqualTo((2 * GIB) / 10);
        assertThat(b.writeBufferBytes()).isEqualTo((2 * GIB) / 40);
    }

    @Test
    void theCacheIsClampedTo16MebibytesAnd512Mebibytes() {
        assertThat(StoreMemoryBudget.forContainerMemory(64 * MIB, 0, 0).blockCacheBytes()).isEqualTo(16 * MIB);
        assertThat(StoreMemoryBudget.forContainerMemory(16 * GIB, 0, 0).blockCacheBytes()).isEqualTo(512 * MIB);
    }

    @Test
    void explicitSettingsWin() {
        var b = StoreMemoryBudget.forContainerMemory(2 * GIB, 100 * MIB, 10 * MIB);

        assertThat(b.blockCacheBytes()).isEqualTo(100 * MIB);
        assertThat(b.writeBufferBytes()).isEqualTo(10 * MIB);
    }

    @Test
    void anExplicitCacheIsNotClampedAndTheDerivedWriteBufferFollowsIt() {
        var b = StoreMemoryBudget.forContainerMemory(2 * GIB, 8 * MIB, 0);

        assertThat(b.blockCacheBytes()).isEqualTo(8 * MIB);
        assertThat(b.writeBufferBytes()).isEqualTo(2 * MIB);
    }

    @Test
    void cgroupMemoryMaxIsParsedAndMaxFallsBack() {
        assertThat(StoreMemoryBudget.parseCgroupMemoryMax("2147483648\n", 7)).isEqualTo(2 * GIB);
        assertThat(StoreMemoryBudget.parseCgroupMemoryMax("max\n", 7)).isEqualTo(7);
        assertThat(StoreMemoryBudget.parseCgroupMemoryMax("", 7)).isEqualTo(7);
        assertThat(StoreMemoryBudget.parseCgroupMemoryMax(null, 7)).isEqualTo(7);
    }

}
