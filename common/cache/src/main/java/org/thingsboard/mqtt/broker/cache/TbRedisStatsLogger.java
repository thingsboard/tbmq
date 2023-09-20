/**
 * Copyright © 2016-2023 The Thingsboard Authors
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.data.redis.cache.CacheStatistics;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;

import javax.annotation.Nonnegative;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(prefix = "cache", value = "type", havingValue = "redis")
@Slf4j
@RequiredArgsConstructor
public class TbRedisStatsLogger {

    private final CacheManager cacheManager;

    @Value("${cache.stats.enabled:true}")
    private boolean cacheStatsEnabled;

    @Value("${cache.stats.intervalSec:60}")
    private long cacheStatsInterval;

    private ScheduledExecutorService scheduler = null;

    @PostConstruct
    public void init() {
        if (cacheStatsEnabled) {
            if (log.isDebugEnabled()) {
                log.debug("initializing redis cache stats scheduled job");
            }
            scheduler = Executors.newSingleThreadScheduledExecutor(ThingsBoardThreadFactory.forName("redis-cache-stats"));
            scheduler.scheduleAtFixedRate(this::printCacheStats, cacheStatsInterval, cacheStatsInterval, TimeUnit.SECONDS);
        }
    }

    @PreDestroy
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    private void printCacheStats() {
        for (String cacheName : cacheManager.getCacheNames()) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache instanceof TransactionAwareCacheDecorator transactionAwareCacheDecorator) {
                RedisCache redisCache = (RedisCache) transactionAwareCacheDecorator.getTargetCache();
                CacheStatistics stats = redisCache.getStatistics();

                log.info("Redis [{}]: hit rate [{}] hits [{}] misses [{}] puts [{}] deletes [{}]",
                        cache.getName(), hitRate(stats), stats.getHits(), stats.getMisses(),
                        stats.getPuts(), stats.getDeletes());
                redisCache.clearStatistics();
            }
        }
    }

    @Nonnegative
    public double hitRate(CacheStatistics stats) {
        long requestCount = stats.getGets();
        return (requestCount == 0) ? 1.0 : (double) stats.getHits() / requestCount;
    }

}
