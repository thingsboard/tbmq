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
package org.thingsboard.mqtt.broker.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisServiceRegistryStoreTest {

    private static final String KEY = "tbmq:service:registry";

    private HashOperations<String, Object, Object> hashOps;
    private CacheProperties cacheProperties;
    private RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        hashOps = mock(HashOperations.class);
        redisTemplate = mock(RedisTemplate.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        cacheProperties = new CacheProperties();
        cacheProperties.setCachePrefix("");
    }

    private RedisServiceRegistryStore newStore() {
        RedisServiceRegistryStore store = new RedisServiceRegistryStore(redisTemplate, cacheProperties);
        store.init();
        return store;
    }

    @Test
    void putWritesFieldToRegistryHash() {
        newStore().put("svc1", "TBMQ");

        verify(hashOps).put(KEY, "svc1", "TBMQ");
    }

    @Test
    void entriesStringifiesKeysAndValues() {
        when(hashOps.entries(KEY)).thenReturn(Map.of("svc1", "TBMQ"));

        assertThat(newStore().entries()).containsExactly(entry("svc1", "TBMQ"));
    }

    @Test
    void entriesIsEmptyWhenHashReplyIsNull() {
        when(hashOps.entries(KEY)).thenReturn(null);

        assertThat(newStore().entries()).isEmpty();
    }

    @Test
    void deleteReturnsNumberOfRemovedFields() {
        when(hashOps.delete(KEY, "svc3")).thenReturn(1L);

        assertThat(newStore().delete("svc3")).isEqualTo(1L);
    }

    @Test
    void deleteIsZeroWhenReplyIsNull() {
        when(hashOps.delete(KEY, "svc3")).thenReturn(null);

        assertThat(newStore().delete("svc3")).isZero();
    }

    @Test
    void getIsEmptyWhenFieldIsAbsent() {
        when(hashOps.get(KEY, "svc9")).thenReturn(null);

        assertThat(newStore().get("svc9")).isEmpty();
    }

    @Test
    void getReturnsStringifiedValue() {
        when(hashOps.get(KEY, "svc1")).thenReturn("TBMQ");

        assertThat(newStore().get("svc1")).contains("TBMQ");
    }

    @Test
    void keyIncludesCachePrefix() {
        cacheProperties.setCachePrefix("pfx:");

        newStore().put("svc1", "TBMQ");

        verify(hashOps).put("pfx:" + KEY, "svc1", "TBMQ");
    }
}
