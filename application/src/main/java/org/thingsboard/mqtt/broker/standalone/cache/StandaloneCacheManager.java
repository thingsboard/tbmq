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

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.thingsboard.mqtt.broker.cache.CacheConstants;
import org.thingsboard.mqtt.broker.cache.CacheProperties;
import org.thingsboard.mqtt.broker.cache.CacheSpecs;
import org.thingsboard.mqtt.broker.dao.store.KeyValueStore;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The {@link CacheManager} of the standalone edition. It serves the caches of {@link CacheProperties} under their
 * prefixed names, and returns {@code null} for any other name, like the Redis manager of the cluster edition.
 * The session attribute caches are {@link StoreBackedCache}s over {@code sessionAttributes}, so they survive a restart
 * and are never evicted; every other cache is a {@link CaffeineCache} that allows null values, holds at most
 * {@code maxEntries} entries, and expires its entries after the TTL of its spec when that is above 0.
 */
public class StandaloneCacheManager implements CacheManager {

    private static final Set<String> STORE_BACKED_CACHES = Set.of(
            CacheConstants.CLIENT_SESSION_CREDENTIALS_CACHE,
            CacheConstants.CLIENT_MQTT_VERSION_CACHE);

    private final Map<String, Cache> caches;

    private StandaloneCacheManager(Map<String, Cache> caches) {
        this.caches = caches;
    }

    public static StandaloneCacheManager create(CacheProperties properties, KeyValueStore sessionAttributes,
                                                long maxEntries, Ticker ticker) {
        Map<String, Cache> caches = new LinkedHashMap<>();
        properties.getSpecs().forEach((rawName, spec) -> {
            String name = properties.prefixKey(rawName);
            caches.put(name, STORE_BACKED_CACHES.contains(rawName)
                    ? new StoreBackedCache(name, sessionAttributes)
                    : newCaffeineCache(name, spec, maxEntries, ticker));
        });
        return new StandaloneCacheManager(Collections.unmodifiableMap(caches));
    }

    private static CaffeineCache newCaffeineCache(String name, CacheSpecs spec, long maxEntries, Ticker ticker) {
        Caffeine<Object, Object> builder = Caffeine.newBuilder().maximumSize(maxEntries).ticker(ticker);
        Integer ttlInMinutes = spec == null ? null : spec.getTimeToLiveInMinutes();
        if (ttlInMinutes != null && ttlInMinutes > 0) {
            builder.expireAfterWrite(Duration.ofMinutes(ttlInMinutes));
        }
        return new CaffeineCache(name, builder.build(), true);
    }

    @Override
    public Cache getCache(String name) {
        return caches.get(name);
    }

    @Override
    public Collection<String> getCacheNames() {
        return caches.keySet();
    }

}
