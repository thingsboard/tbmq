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
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.local.LocalBucket;
import io.github.bucket4j.local.LocalBucketBuilder;
import jakarta.annotation.PostConstruct;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.cache.CacheConstants;

@Service
@ConditionalOnProperty(prefix = "cache", value = "type", havingValue = "caffeine")
@Slf4j
public class RateLimitCaffeineCacheServiceImpl extends AbstractRateLimitCacheService implements RateLimitCacheService {

    private final CacheManager cacheManager;
    private final LocalBucket devicePersistedMsgsBucket;
    private final LocalBucket totalMsgsBucket;

    private Cache<String, Long> clientSessionsLimitCache;
    private Cache<String, Long> applicationClientsLimitCache;

    @Value("${mqtt.sessions-limit:0}")
    @Setter
    private int sessionsLimit;
    @Value("${mqtt.application-clients-limit:0}")
    @Setter
    private int applicationClientsLimit;

    private String clientSessionsLimitCacheKey;
    private String appClientsLimitCacheKey;

    public RateLimitCaffeineCacheServiceImpl(CacheManager cacheManager,
                                             @Autowired(required = false) BucketConfiguration devicePersistedMsgsBucketConfiguration,
                                             @Autowired(required = false) BucketConfiguration totalMsgsBucketConfiguration) {
        this.cacheManager = cacheManager;
        this.devicePersistedMsgsBucket = getLocalBucket(devicePersistedMsgsBucketConfiguration);
        this.totalMsgsBucket = getLocalBucket(totalMsgsBucketConfiguration);
    }

    @PostConstruct
    public void init() {
        if (sessionsLimit > 0) {
            clientSessionsLimitCache = getNativeCache(CacheConstants.CLIENT_SESSIONS_LIMIT_CACHE);
            clientSessionsLimitCacheKey = cachePrefix + CacheConstants.CLIENT_SESSIONS_LIMIT_CACHE_KEY;
        }
        if (applicationClientsLimit > 0) {
            applicationClientsLimitCache = getNativeCache(CacheConstants.APP_CLIENTS_LIMIT_CACHE);
            appClientsLimitCacheKey = cachePrefix + CacheConstants.APP_CLIENTS_LIMIT_CACHE_KEY;
        }
    }

    @Override
    public void initSessionCount(int count) {
        if (sessionsLimit <= 0) {
            return;
        }
        log.info("Initializing client session limit cache with count {}", count);
        initCacheWithCount(clientSessionsLimitCache, clientSessionsLimitCacheKey, count);
    }

    @Override
    public void setSessionCount(int count) {
        if (sessionsLimit <= 0) {
            return;
        }
        log.debug("Set session limit cache to {}", count);
        clientSessionsLimitCache.asMap().put(clientSessionsLimitCacheKey, (long) count);
    }

    @Override
    public void initApplicationClientsCount(int count) {
        if (applicationClientsLimit <= 0) {
            return;
        }
        log.info("Initializing application clients limit cache with count {}", count);
        initCacheWithCount(applicationClientsLimitCache, appClientsLimitCacheKey, count);
    }

    @Override
    public long incrementSessionCount() {
        log.debug("Incrementing session count");
        return increment(clientSessionsLimitCache, clientSessionsLimitCacheKey);
    }

    @Override
    public long incrementApplicationClientsCount() {
        log.debug("Incrementing Application clients count");
        return increment(applicationClientsLimitCache, appClientsLimitCacheKey);
    }

    @Override
    public void decrementSessionCount() {
        if (sessionsLimit <= 0) {
            return;
        }
        log.debug("Decrementing session count");
        decrement(clientSessionsLimitCache, clientSessionsLimitCacheKey);
    }

    @Override
    public void decrementApplicationClientsCount() {
        if (applicationClientsLimit <= 0) {
            return;
        }
        log.debug("Decrementing Application clients count");
        decrement(applicationClientsLimitCache, appClientsLimitCacheKey);
    }

    @Override
    public boolean tryConsumeDevicePersistedMsg() {
        return devicePersistedMsgsBucket.tryConsume(1);
    }

    @Override
    public long tryConsumeAsMuchAsPossibleDevicePersistedMsgs(long limit) {
        return devicePersistedMsgsBucket.tryConsumeAsMuchAsPossible(limit);
    }

    @Override
    public boolean tryConsumeTotalMsg() {
        return totalMsgsBucket.tryConsume(1);
    }

    @Override
    public long tryConsumeAsMuchAsPossibleTotalMsgs(long limit) {
        return totalMsgsBucket.tryConsumeAsMuchAsPossible(limit);
    }

    private Cache<String, Long> getNativeCache(String name) {
        return (Cache<String, Long>) cacheManager.getCache(name).getNativeCache();
    }

    private void initCacheWithCount(Cache<String, Long> cache, String key, int count) {
        cache.asMap().putIfAbsent(key, (long) count);
    }

    private Long increment(Cache<String, Long> cache, String key) {
        return cache.asMap().compute(key, (k, v) -> (v == null ? 1L : v + 1));
    }

    private void decrement(Cache<String, Long> cache, String key) {
        cache.asMap().computeIfPresent(key, (k, v) -> v > 0 ? v - 1 : 0);
    }

    LocalBucket getLocalBucket(BucketConfiguration bucketConfiguration) {
        if (bucketConfiguration != null) {
            LocalBucketBuilder builder = Bucket.builder();
            for (var bandwidth : bucketConfiguration.getBandwidths()) {
                builder.addLimit(bandwidth);
            }
            return builder.build();
        }
        return null;
    }
}
