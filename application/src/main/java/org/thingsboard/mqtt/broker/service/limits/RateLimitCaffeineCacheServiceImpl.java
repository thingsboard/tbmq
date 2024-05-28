/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.limits;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.cache.CacheConstants;

import javax.annotation.PostConstruct;

@Service
@ConditionalOnProperty(prefix = "cache", value = "type", havingValue = "caffeine")
@RequiredArgsConstructor
@Slf4j
public class RateLimitCaffeineCacheServiceImpl implements RateLimitCacheService {

    private final CacheManager cacheManager;

    private Cache<String, Long> clientSessionsLimitCache;

    @Value("${mqtt.sessions-limit:0}")
    @Setter
    private int sessionsLimit;

    @PostConstruct
    public void init() {
        clientSessionsLimitCache = (Cache<String, Long>) cacheManager.getCache(CacheConstants.CLIENT_SESSIONS_LIMIT_CACHE).getNativeCache();
    }

    @Override
    public void initSessionCount(int count) {
        if (sessionsLimit <= 0) {
            return;
        }
        log.debug("Initializing session count");
        clientSessionsLimitCache.asMap().putIfAbsent(CacheConstants.CLIENT_SESSIONS_LIMIT_CACHE_KEY, (long) count);
    }

    @Override
    public long incrementSessionCount() {
        log.debug("Incrementing session count");
        return clientSessionsLimitCache.asMap().compute(CacheConstants.CLIENT_SESSIONS_LIMIT_CACHE_KEY, (k, v) -> (v == null ? 1L : v + 1));
    }

    @Override
    public void decrementSessionCount() {
        if (sessionsLimit <= 0) {
            return;
        }
        log.debug("Decrementing session count");
        clientSessionsLimitCache.asMap().computeIfPresent(CacheConstants.CLIENT_SESSIONS_LIMIT_CACHE_KEY, (k, v) -> v > 0 ? v - 1 : 0);
    }

}
