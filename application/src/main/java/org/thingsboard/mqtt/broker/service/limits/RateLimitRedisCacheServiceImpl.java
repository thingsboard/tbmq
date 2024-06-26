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
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.cache.CacheConstants;

@Service
@ConditionalOnProperty(prefix = "cache", value = "type", havingValue = "redis")
@Slf4j
public class RateLimitRedisCacheServiceImpl implements RateLimitCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final BucketProxy devicePersistedMsgsBucketProxy;
    private final BucketProxy totalMsgsBucketProxy;

    @Value("${mqtt.sessions-limit:0}")
    @Setter
    private int sessionsLimit;
    @Value("${mqtt.application-clients-limit:0}")
    @Setter
    private int applicationClientsLimit;

    public RateLimitRedisCacheServiceImpl(RedisTemplate<String, Object> redisTemplate,
                                          JedisBasedProxyManager<String> jedisBasedProxyManager,
                                          @Autowired(required = false) BucketConfiguration devicePersistedMsgsBucketConfiguration,
                                          @Autowired(required = false) BucketConfiguration totalMsgsBucketConfiguration) {
        this.redisTemplate = redisTemplate;
        this.devicePersistedMsgsBucketProxy = devicePersistedMsgsBucketConfiguration == null ? null :
                jedisBasedProxyManager.getProxy(CacheConstants.DEVICE_PERSISTED_MSGS_LIMIT_CACHE, () -> devicePersistedMsgsBucketConfiguration);
        this.totalMsgsBucketProxy = totalMsgsBucketConfiguration == null ? null :
                jedisBasedProxyManager.getProxy(CacheConstants.TOTAL_MSGS_LIMIT_CACHE, () -> totalMsgsBucketConfiguration);
    }

    @Override
    public void initSessionCount(int count) {
        if (sessionsLimit <= 0) {
            return;
        }
        boolean initialized = initCacheWithCount(CacheConstants.CLIENT_SESSIONS_LIMIT_CACHE_KEY, count);
        if (initialized) {
            log.info("Client session limit cache is initialized with count {}", count);
        }
    }

    @Override
    public void initApplicationClientsCount(int count) {
        if (applicationClientsLimit <= 0) {
            return;
        }
        boolean initialized = initCacheWithCount(CacheConstants.APP_CLIENTS_LIMIT_CACHE_KEY, count);
        if (initialized) {
            log.info("Application clients limit cache is initialized with count {}", count);
        }
    }

    @Override
    public long incrementSessionCount() {
        log.debug("Incrementing session count");
        return increment(CacheConstants.CLIENT_SESSIONS_LIMIT_CACHE_KEY);
    }

    @Override
    public long incrementApplicationClientsCount() {
        log.debug("Incrementing Application clients count");
        return increment(CacheConstants.APP_CLIENTS_LIMIT_CACHE_KEY);
    }

    @Override
    public void decrementSessionCount() {
        if (sessionsLimit <= 0) {
            return;
        }
        log.debug("Decrementing session count");
        decrement(CacheConstants.CLIENT_SESSIONS_LIMIT_CACHE_KEY);
    }

    @Override
    public void decrementApplicationClientsCount() {
        if (applicationClientsLimit <= 0) {
            return;
        }
        log.debug("Decrementing Application clients count");
        decrement(CacheConstants.APP_CLIENTS_LIMIT_CACHE_KEY);
    }

    /**
     * This method is used when device persisted messages rate limits are enabled, so bucketProxy can not be null here
     */
    @Override
    public boolean tryConsumeDevicePersistedMsg() {
        return devicePersistedMsgsBucketProxy.tryConsume(1);
    }

    /**
     * This method is used when total messages rate limits are enabled, so bucketProxy can not be null here
     */
    @Override
    public boolean tryConsumeTotalMsg() {
        return totalMsgsBucketProxy.tryConsume(1);
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

