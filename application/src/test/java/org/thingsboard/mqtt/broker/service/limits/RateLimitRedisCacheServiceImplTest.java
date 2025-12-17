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
package org.thingsboard.mqtt.broker.service.limits;

import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.redis.jedis.cas.JedisBasedProxyManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.cache.CacheConstants;
import org.thingsboard.mqtt.broker.cache.CacheProperties;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = RateLimitRedisCacheServiceImpl.class)
@TestPropertySource(properties = {
        "cache.cache-prefix=",
        "mqtt.sessions-limit=5",
        "mqtt.application-clients-limit=5"
})
public class RateLimitRedisCacheServiceImplTest {

    @MockitoBean
    private RedisTemplate<String, Object> redisTemplate;

    @MockitoBean
    private ValueOperations<String, Object> valueOperations;

    @MockitoBean
    private JedisBasedProxyManager<String> jedisBasedProxyManager;

    @MockitoBean
    private BucketProxy bucketProxy;

    @MockitoBean
    private CacheProperties cacheProperties;

    @MockitoSpyBean
    private RateLimitRedisCacheServiceImpl rateLimitRedisCacheService;

    @Before
    public void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(cacheProperties.prefixKey(anyString())).thenAnswer(inv -> inv.getArgument(0));
        rateLimitRedisCacheService.setSessionsLimit(5);
        rateLimitRedisCacheService.setApplicationClientsLimit(5);
        rateLimitRedisCacheService.init();
    }

    @Test
    public void testInitSessionCount() {
        int count = 5;
        when(valueOperations.setIfAbsent(anyString(), anyString())).thenReturn(true);

        rateLimitRedisCacheService.initSessionCount(count);

        verify(valueOperations, times(1)).setIfAbsent(CacheConstants.CLIENT_SESSIONS_LIMIT_CACHE_KEY, Integer.toString(count));
    }

    @Test
    public void testInitSessionCountWhenSessionsLimitIsZero() {
        rateLimitRedisCacheService.setSessionsLimit(0);
        int count = 5;

        rateLimitRedisCacheService.initSessionCount(count);

        verify(valueOperations, never()).setIfAbsent(anyString(), anyString());
    }

    @Test
    public void testIncrementSessionCount() {
        when(valueOperations.increment(anyString())).thenReturn(6L);

        long newCount = rateLimitRedisCacheService.incrementSessionCount();

        assertEquals(6L, newCount);
        verify(valueOperations, times(1)).increment(CacheConstants.CLIENT_SESSIONS_LIMIT_CACHE_KEY);
    }

    @Test
    public void testDecrementSessionCount() {
        rateLimitRedisCacheService.decrementSessionCount();

        verify(valueOperations, times(1)).decrement(CacheConstants.CLIENT_SESSIONS_LIMIT_CACHE_KEY);
    }

    @Test
    public void testDecrementSessionCountWhenSessionsLimitIsZero() {
        rateLimitRedisCacheService.setSessionsLimit(0);

        rateLimitRedisCacheService.decrementSessionCount();

        verify(valueOperations, never()).decrement(anyString());
    }

    @Test
    public void testInitApplicationClientsCount() {
        int count = 5;
        when(valueOperations.setIfAbsent(anyString(), anyString())).thenReturn(true);

        rateLimitRedisCacheService.initApplicationClientsCount(count);

        verify(valueOperations, times(1)).setIfAbsent(CacheConstants.APP_CLIENTS_LIMIT_CACHE_KEY, Integer.toString(count));
    }

    @Test
    public void testInitApplicationClientsCountWhenClientsLimitIsZero() {
        rateLimitRedisCacheService.setApplicationClientsLimit(0);
        int count = 5;

        rateLimitRedisCacheService.initApplicationClientsCount(count);

        verify(valueOperations, never()).setIfAbsent(anyString(), anyString());
    }

    @Test
    public void testIncrementApplicationClientsCount() {
        when(valueOperations.increment(anyString())).thenReturn(6L);

        long newCount = rateLimitRedisCacheService.incrementApplicationClientsCount();

        assertEquals(6L, newCount);
        verify(valueOperations, times(1)).increment(CacheConstants.APP_CLIENTS_LIMIT_CACHE_KEY);
    }

    @Test
    public void testDecrementApplicationClientsCount() {
        rateLimitRedisCacheService.decrementApplicationClientsCount();

        verify(valueOperations, times(1)).decrement(CacheConstants.APP_CLIENTS_LIMIT_CACHE_KEY);
    }

    @Test
    public void testDecrementApplicationClientsCountWhenClientsLimitIsZero() {
        rateLimitRedisCacheService.setApplicationClientsLimit(0);

        rateLimitRedisCacheService.decrementApplicationClientsCount();

        verify(valueOperations, never()).decrement(anyString());
    }

}
