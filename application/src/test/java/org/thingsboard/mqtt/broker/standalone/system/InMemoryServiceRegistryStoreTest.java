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
package org.thingsboard.mqtt.broker.standalone.system;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryServiceRegistryStoreTest {

    private final InMemoryServiceRegistryStore store = new InMemoryServiceRegistryStore();

    @Test
    void givenEmptyStore_whenGetAndEntries_thenEmpty() {
        assertThat(store.get("a")).isEmpty();
        assertThat(store.entries()).isEmpty();
    }

    @Test
    void givenPut_whenGet_thenValueReturned() {
        store.put("a", "tbmq");
        store.put("a", "tbmq-2");

        assertThat(store.get("a")).contains("tbmq-2");
        assertThat(store.entries()).isEqualTo(Map.of("a", "tbmq-2"));
    }

    @Test
    void givenEntriesCopy_whenStoreChanges_thenCopyIsUnaffected() {
        store.put("a", "tbmq");
        Map<String, String> copy = store.entries();

        store.put("b", "tbmq");

        assertThat(copy).containsOnlyKeys("a");
    }

    @Test
    void givenTwoFieldsOneExisting_whenDelete_thenReturnsOne() {
        store.put("a", "tbmq");

        assertThat(store.delete("a", "missing")).isEqualTo(1);
        assertThat(store.entries()).isEmpty();
    }

    @Test
    void givenNoFields_whenDelete_thenReturnsZero() {
        store.put("a", "tbmq");

        assertThat(store.delete()).isZero();
        assertThat(store.entries()).hasSize(1);
    }

}
