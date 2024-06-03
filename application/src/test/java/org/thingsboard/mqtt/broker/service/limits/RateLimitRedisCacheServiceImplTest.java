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

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.thingsboard.mqtt.broker.cache.CacheConstants;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RateLimitRedisCacheServiceImplTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private RateLimitRedisCacheServiceImpl rateLimitRedisCacheService;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        rateLimitRedisCacheService.setSessionsLimit(5);
        rateLimitRedisCacheService.setApplicationClientsLimit(5);
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
        rateLimitRedisCacheService.setSessionsLimit(5);

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
        rateLimitRedisCacheService.setApplicationClientsLimit(5);

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
