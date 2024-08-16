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

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.redis.jedis.cas.JedisBasedProxyManager;
import jakarta.annotation.PostConstruct;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.cache.CacheConstants;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "service", value = "singleton-mode", havingValue = "false")
public class RateLimitRedisCacheServiceImpl extends AbstractRateLimitCacheService implements RateLimitCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final JedisBasedProxyManager<String> jedisBasedProxyManager;
    private final BucketConfiguration devicePersistedMsgsBucketConfiguration;
    private final BucketConfiguration totalMsgsBucketConfiguration;

    @Value("${mqtt.sessions-limit:0}")
    @Setter
    private int sessionsLimit;
    @Value("${mqtt.application-clients-limit:0}")
    @Setter
    private int applicationClientsLimit;

    private BucketProxy devicePersistedMsgsBucketProxy;
    private BucketProxy totalMsgsBucketProxy;
    private String clientSessionsLimitCacheKey;
    private String appClientsLimitCacheKey;

    public RateLimitRedisCacheServiceImpl(RedisTemplate<String, Object> redisTemplate,
                                          JedisBasedProxyManager<String> jedisBasedProxyManager,
                                          @Autowired(required = false) BucketConfiguration devicePersistedMsgsBucketConfiguration,
                                          @Autowired(required = false) BucketConfiguration totalMsgsBucketConfiguration) {
        this.redisTemplate = redisTemplate;
        this.jedisBasedProxyManager = jedisBasedProxyManager;
        this.devicePersistedMsgsBucketConfiguration = devicePersistedMsgsBucketConfiguration;
        this.totalMsgsBucketConfiguration = totalMsgsBucketConfiguration;
    }

    @PostConstruct
    public void init() {
        var devicePersistedMsgsLimitCacheKey = cachePrefix + CacheConstants.DEVICE_PERSISTED_MSGS_LIMIT_CACHE;
        var totalMsgsLimitCacheKey = cachePrefix + CacheConstants.TOTAL_MSGS_LIMIT_CACHE;

        this.devicePersistedMsgsBucketProxy = devicePersistedMsgsBucketConfiguration == null ? null :
                jedisBasedProxyManager.getProxy(devicePersistedMsgsLimitCacheKey, () -> devicePersistedMsgsBucketConfiguration);
        this.totalMsgsBucketProxy = totalMsgsBucketConfiguration == null ? null :
                jedisBasedProxyManager.getProxy(totalMsgsLimitCacheKey, () -> totalMsgsBucketConfiguration);

        if (sessionsLimit > 0) {
            clientSessionsLimitCacheKey = cachePrefix + CacheConstants.CLIENT_SESSIONS_LIMIT_CACHE_KEY;
        }
        if (applicationClientsLimit > 0) {
            appClientsLimitCacheKey = cachePrefix + CacheConstants.APP_CLIENTS_LIMIT_CACHE_KEY;
        }
    }

    @Override
    public void initSessionCount(int count) {
        if (sessionsLimit <= 0) {
            return;
        }
        boolean initialized = initCacheWithCount(clientSessionsLimitCacheKey, count);
        if (initialized) {
            log.info("Client session limit cache is initialized with count {}", count);
        }
    }

    @Override
    public void setSessionCount(int count) {
        if (sessionsLimit <= 0) {
            return;
        }
        log.debug("Set session limit cache to {}", count);
        redisTemplate.opsForValue().set(clientSessionsLimitCacheKey, Integer.toString(count));
    }

    @Override
    public void initApplicationClientsCount(int count) {
        if (applicationClientsLimit <= 0) {
            return;
        }
        boolean initialized = initCacheWithCount(appClientsLimitCacheKey, count);
        if (initialized) {
            log.info("Application clients limit cache is initialized with count {}", count);
        }
    }

    @Override
    public long incrementSessionCount() {
        log.debug("Incrementing session count");
        return increment(clientSessionsLimitCacheKey);
    }

    @Override
    public long incrementApplicationClientsCount() {
        log.debug("Incrementing Application clients count");
        return increment(appClientsLimitCacheKey);
    }

    @Override
    public void decrementSessionCount() {
        if (sessionsLimit <= 0) {
            return;
        }
        log.debug("Decrementing session count");
        decrement(clientSessionsLimitCacheKey);
    }

    @Override
    public void decrementApplicationClientsCount() {
        if (applicationClientsLimit <= 0) {
            return;
        }
        log.debug("Decrementing Application clients count");
        decrement(appClientsLimitCacheKey);
    }

    /**
     * This method is used when device persisted messages rate limits are enabled, so bucketProxy can not be null here
     */
    @Override
    public boolean tryConsumeDevicePersistedMsg() {
        return devicePersistedMsgsBucketProxy.tryConsume(1);
    }

    @Override
    public long tryConsumeAsMuchAsPossibleDevicePersistedMsgs(long limit) {
        return devicePersistedMsgsBucketProxy.tryConsumeAsMuchAsPossible(limit);
    }

    /**
     * This method is used when total messages rate limits are enabled, so bucketProxy can not be null here
     */
    @Override
    public boolean tryConsumeTotalMsg() {
        return totalMsgsBucketProxy.tryConsume(1);
    }

    @Override
    public long tryConsumeAsMuchAsPossibleTotalMsgs(long limit) {
        return totalMsgsBucketProxy.tryConsumeAsMuchAsPossible(limit);
    }

    private Boolean initCacheWithCount(String key, int count) {
        return redisTemplate.opsForValue().setIfAbsent(key, Integer.toString(count));
    }

    private Long increment(String key) {
        return redisTemplate.opsForValue().increment(key);
    }

    private void decrement(String key) {
        redisTemplate.opsForValue().decrement(key);
    }

}

