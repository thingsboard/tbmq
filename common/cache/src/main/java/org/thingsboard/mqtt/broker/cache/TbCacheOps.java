/**
 * Copyright © 2016-2025 The Thingsboard Authors
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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class TbCacheOps {

    public enum Status {
        HIT, CACHED_NULL, ABSENT
    }

    private final CacheNameResolver cacheNameResolver;
    private final ConcurrentMap<String, Cache> caches = new ConcurrentHashMap<>();

    public Cache cache(String cacheName) {
        return caches.computeIfAbsent(cacheName, cacheNameResolver::getCache);
    }

    /**
     * One cache read. Returns status and (if HIT) the value.
     * - ABSENT: no entry
     * - CACHED_NULL: negative cache hit (explicitly cached null)
     * - HIT: value present
     */
    public <T> Lookup<T> lookup(String cacheName, Object key, Class<T> type) {
        Cache.ValueWrapper w = cache(cacheName).get(key);
        if (w == null) {
            return Lookup.absent();
        }
        Object v = w.get();
        if (v == null) {
            return Lookup.cachedNull();
        }
        return Lookup.hit(type.cast(v));
    }

    /** Cache a value (HIT). */
    public void put(String cacheName, Object key, Object value) {
        cache(cacheName).put(key, value);
    }

    /** Cache an explicit "not found" (negative cache). */
    public void putNull(String cacheName, Object key) {
        cache(cacheName).put(key, null);
    }

    public void evictIfPresent(String cacheName, Object key) {
        cache(cacheName).evictIfPresent(key);
    }

    public void invalidate(String cacheName) {
        cache(cacheName).invalidate();
    }

    public void evictIfPresentSafe(String cacheName, Object key) {
        try {
            evictIfPresent(cacheName, key);
        } catch (RuntimeException e) {
            log.warn("Cache evict failed. cache={}, key={}", cacheName, key, e);
        }
    }

    public void invalidateSafe(String cacheName) {
        try {
            invalidate(cacheName);
        } catch (RuntimeException e) {
            log.warn("Cache invalidate failed. cache={}", cacheName, e);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public record Lookup<T>(Status status, T value) {

        private static final Lookup ABSENT = new Lookup<>(Status.ABSENT, null);
        private static final Lookup CACHED_NULL = new Lookup<>(Status.CACHED_NULL, null);

        public static <T> Lookup<T> absent() {
            return (Lookup<T>) ABSENT;
        }

        public static <T> Lookup<T> cachedNull() {
            return (Lookup<T>) CACHED_NULL;
        }

        public static <T> Lookup<T> hit(T value) {
            return new Lookup<>(Status.HIT, value);
        }
    }
}
