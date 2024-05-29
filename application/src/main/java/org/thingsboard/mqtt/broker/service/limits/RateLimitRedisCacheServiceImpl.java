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

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.cache.CacheConstants;

/**
 * should not be null since not used in pipeline / transaction.
 */
@Service
@ConditionalOnProperty(prefix = "cache", value = "type", havingValue = "redis")
@RequiredArgsConstructor
@Slf4j
public class RateLimitRedisCacheServiceImpl implements RateLimitCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${mqtt.sessions-limit:0}")
    @Setter
    private int sessionsLimit;

    @Override
    public void initSessionCount(int count) {
        if (sessionsLimit <= 0) {
            return;
        }
        ValueOperations<String, Object> valueOps = redisTemplate.opsForValue();
        boolean initialized = valueOps.setIfAbsent(CacheConstants.CLIENT_SESSIONS_LIMIT_CACHE_KEY, Integer.toString(count));
        if (initialized) {
            log.info("Session count initialized to {}", count);
        }
    }

    @Override
    public long incrementSessionCount() {
        log.debug("Incrementing session count");
        ValueOperations<String, Object> valueOps = redisTemplate.opsForValue();
        return valueOps.increment(CacheConstants.CLIENT_SESSIONS_LIMIT_CACHE_KEY);
    }

    @Override
    public void decrementSessionCount() {
        if (sessionsLimit <= 0) {
            return;
        }
        log.debug("Decrementing session count");
        ValueOperations<String, Object> valueOps = redisTemplate.opsForValue();
        valueOps.decrement(CacheConstants.CLIENT_SESSIONS_LIMIT_CACHE_KEY);
    }

}

