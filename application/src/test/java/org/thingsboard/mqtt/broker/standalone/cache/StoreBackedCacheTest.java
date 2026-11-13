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
package org.thingsboard.mqtt.broker.standalone.cache;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.thingsboard.mqtt.broker.standalone.state.InMemoryKeyValueStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoreBackedCacheTest {

    private final InMemoryKeyValueStore store = new InMemoryKeyValueStore();
    private final StoreBackedCache cache = new StoreBackedCache("p:clientSessionCredentials", store);

    @Test
    void valuesSurviveANewCacheInstanceOverTheSameStore() {
        cache.put("c1", "value");
        cache.put("c2", null);

        StoreBackedCache reopened = new StoreBackedCache("p:clientSessionCredentials", store);

        assertThat(reopened.get("c1").get()).isEqualTo("value");
        Cache.ValueWrapper cachedNull = reopened.get("c2");
        assertThat(cachedNull).isNotNull();
        assertThat(cachedNull.get()).isNull();
        assertThat(reopened.get("c3")).isNull();
    }

    @Test
    void twoCachesOverOneStoreDoNotSeeEachOther() {
        StoreBackedCache other = new StoreBackedCache("p:clientMqttVersion", store);
        cache.put("c1", "a");
        other.put("c1", "b");

        assertThat(cache.get("c1").get()).isEqualTo("a");
        assertThat(other.get("c1").get()).isEqualTo("b");

        cache.clear();

        assertThat(cache.get("c1")).isNull();
        assertThat(other.get("c1").get()).isEqualTo("b");
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void aCacheNameThatPrefixesAnotherDoesNotClearIt() {
        StoreBackedCache longer = new StoreBackedCache("p:clientSessionCredentials2", store);
        cache.put("c1", "a");
        longer.put("c1", "b");

        assertThat(cache.invalidate()).isTrue();

        assertThat(longer.get("c1").get()).isEqualTo("b");
        assertThat(cache.invalidate()).isFalse();
    }

    @Test
    void theStoreKeyIsTheCacheNameThenNulThenTheKey() {
        cache.put("c1", "v");

        assertThat(store.snapshot()).containsOnlyKeys("p:clientSessionCredentials\u0000c1");
        assertThat(store.get("p:clientSessionCredentials\u0000c1")).containsExactly(1, 'v');
        cache.put("c2", null);
        assertThat(store.get("p:clientSessionCredentials\u0000c2")).containsExactly(0);
    }

    @Test
    void aNonStringValueIsRejected() {
        assertThatThrownBy(() -> cache.put("c1", 42)).isInstanceOf(IllegalArgumentException.class);
        assertThat(store.size()).isZero();
    }

    @Test
    void aNonStringKeyIsRejected() {
        assertThatThrownBy(() -> cache.put(42, "v")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cache.get(42)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cache.evictIfPresent(42)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void evictIfPresentReportsWhetherTheKeyWasThere() {
        cache.put("c1", "v");

        assertThat(cache.evictIfPresent("c1")).isTrue();
        assertThat(cache.evictIfPresent("c1")).isFalse();
        assertThat(cache.get("c1")).isNull();
    }

    @Test
    void putIfAbsentKeepsTheExistingValue() {
        cache.put("c1", "first");

        Cache.ValueWrapper existing = cache.putIfAbsent("c1", "second");

        assertThat(existing.get()).isEqualTo("first");
        assertThat(cache.get("c1").get()).isEqualTo("first");
        assertThat(cache.putIfAbsent("c2", "x")).isNull();
        assertThat(cache.get("c2").get()).isEqualTo("x");
    }

    @Test
    void getWithTypeAndLoader() {
        cache.put("c1", "v");

        assertThat(cache.get("c1", String.class)).isEqualTo("v");
        assertThat(cache.get("missing", String.class)).isNull();
        assertThatThrownBy(() -> cache.get("c1", Integer.class)).isInstanceOf(IllegalStateException.class);
        assertThat(cache.get("loaded", () -> "from-loader")).isEqualTo("from-loader");
        assertThat(cache.get("loaded").get()).isEqualTo("from-loader");
    }

    @Test
    void getWithANullTypeSkipsTheTypeCheck() {
        cache.put("c1", "value");

        assertThat(cache.get("c1", (Class<String>) null)).isEqualTo("value");
        assertThat(cache.get("missing", (Class<String>) null)).isNull();
    }

}
