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
package org.thingsboard.mqtt.broker.service.install.update;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Set;

import static org.thingsboard.mqtt.broker.cache.CacheConstants.BASIC_CREDENTIALS_PASSWORD_CACHE;
import static org.thingsboard.mqtt.broker.cache.CacheConstants.MQTT_CLIENT_CREDENTIALS_CACHE;
import static org.thingsboard.mqtt.broker.cache.CacheConstants.SSL_REGEX_BASED_CREDENTIALS_CACHE;

@RequiredArgsConstructor
@Service
@Profile("install")
@Slf4j
public class DefaultCacheCleanupService implements CacheCleanupService {

    private final CacheManager cacheManager;

    @Value("${cache.cache-prefix:}")
    private String cachePrefix;

    /**
     * Cleanup caches that can not deserialize anymore due to schema upgrade or data update using sql scripts.
     * Refer to SqlDatabaseUpgradeService and /data/upgrade/*.sql
     * to discover what tables were changed
     *
     * IMPORTANT NOTE: We are using Redis to persist messages for DEVICE persisted clients,
     * so calling flushAll may lead to data loss. Ensure proper evaluation and backups before performing this operation.
     */
    @Override
    public void clearCache() throws Exception {
        log.info("Clearing cache ...");

        clearCachesByNames(
                Set.of(MQTT_CLIENT_CREDENTIALS_CACHE, BASIC_CREDENTIALS_PASSWORD_CACHE, SSL_REGEX_BASED_CREDENTIALS_CACHE)
        );

        log.info("Successfully cleared cache!");
    }

    /**
     * Clear all caches managed by the CacheManager.
     *
     * NOTE: The `cacheManager.getCacheNames()` method returns cache names with prefixes already applied.
     * This method directly clears all caches.
     */
    void clearAllCaches() {
        cacheManager.getCacheNames().forEach(this::clearCacheByName);
    }

    /**
     * Clear specific caches by their base names.
     *
     * This method allows clearing only a specific set of caches. The base cache names
     * are provided in the input set, and the cache prefix is automatically applied.
     *
     * Example of usage:
     * <pre>
     *     Set<String> cacheNamesToFlush = Set.of(
     *         MQTT_CLIENT_CREDENTIALS_CACHE,
     *         BASIC_CREDENTIALS_PASSWORD_CACHE,
     *         SSL_REGEX_BASED_CREDENTIALS_CACHE
     *     );
     *     clearCachesByNames(cacheNamesToFlush);
     * </pre>
     *
     * @param cacheNamesToFlush Set of base cache names to be flushed.
     */
    private void clearCachesByNames(Set<String> cacheNamesToFlush) {
        cacheNamesToFlush.stream().map(cacheName -> cachePrefix + cacheName)
                .forEach(fullCacheName -> clearCacheByName(fullCacheName, false));
    }

    /**
     * Clear a cache by its full name.
     *
     * @param cacheName The full name of the cache (including prefix) to be cleared.
     * @throws NullPointerException if the cache does not exist and npeOnMissingCache is true.
     */
    private void clearCacheByName(final String cacheName) {
        clearCacheByName(cacheName, true);
    }

    /**
     * Clear a cache by its full name, with optional behavior for missing caches.
     *
     * @param cacheName          The full name of the cache (including prefix).
     * @param npeOnMissingCache  Whether to throw an exception if the cache does not exist.
     */
    private void clearCacheByName(final String cacheName, boolean npeOnMissingCache) {
        log.info("Clearing cache [{}]", cacheName);
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
            return;
        }
        if (npeOnMissingCache) {
            throw new NullPointerException("Cache does not exist for name " + cacheName);
        }
    }

}
