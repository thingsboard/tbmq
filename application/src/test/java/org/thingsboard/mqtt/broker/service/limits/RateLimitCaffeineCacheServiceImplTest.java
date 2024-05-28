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
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.thingsboard.mqtt.broker.cache.CacheConstants;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

public class RateLimitCaffeineCacheServiceImplTest {

    @Mock
    private CacheManager cacheManager;

    @InjectMocks
    private RateLimitCaffeineCacheServiceImpl rateLimitCaffeineCacheService;

    private Cache<Object, Object> caffeineCache;
    private org.springframework.cache.Cache mockCache;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        // Create a Caffeine cache
        caffeineCache = Caffeine.newBuilder().build();
        mockCache = new CaffeineCache(CacheConstants.CLIENT_SESSIONS_LIMIT_CACHE, caffeineCache);

        // Mock the cache manager to return the Caffeine cache
        when(cacheManager.getCache(any())).thenReturn(mockCache);

        rateLimitCaffeineCacheService.setSessionsLimit(5);

        // Initialize the service
        rateLimitCaffeineCacheService.init();
    }

    @Test
    public void testInitSessionCount() {
        int count = 5;
        rateLimitCaffeineCacheService.initSessionCount(count);

        Long actualCount = (Long) caffeineCache.getIfPresent(CacheConstants.CLIENT_SESSIONS_LIMIT_CACHE_KEY);
        assertEquals(Long.valueOf(count), actualCount);
    }

    @Test
    public void testIncrementSessionCount() {
        caffeineCache.put(CacheConstants.CLIENT_SESSIONS_LIMIT_CACHE_KEY, 5L);
        long newCount = rateLimitCaffeineCacheService.incrementSessionCount();

        assertEquals(6L, newCount);
    }

    @Test
    public void testDecrementSessionCount() {
        caffeineCache.put(CacheConstants.CLIENT_SESSIONS_LIMIT_CACHE_KEY, 5L);
        rateLimitCaffeineCacheService.decrementSessionCount();

        Long actualCount = (Long) caffeineCache.getIfPresent(CacheConstants.CLIENT_SESSIONS_LIMIT_CACHE_KEY);
        assertEquals(Long.valueOf(4), actualCount);
    }

    @Test
    public void testDecrementSessionCount_ZeroValue() {
        caffeineCache.put(CacheConstants.CLIENT_SESSIONS_LIMIT_CACHE_KEY, 0L);
        rateLimitCaffeineCacheService.decrementSessionCount();

        Long actualCount = (Long) caffeineCache.getIfPresent(CacheConstants.CLIENT_SESSIONS_LIMIT_CACHE_KEY);
        assertEquals(Long.valueOf(0), actualCount);
    }

    @Test
    public void testDecrementSessionCount_NegativeValue() {
        caffeineCache.put(CacheConstants.CLIENT_SESSIONS_LIMIT_CACHE_KEY, -1L);
        rateLimitCaffeineCacheService.decrementSessionCount();

        Long actualCount = (Long) caffeineCache.getIfPresent(CacheConstants.CLIENT_SESSIONS_LIMIT_CACHE_KEY);
        assertEquals(Long.valueOf(0), actualCount);
    }

}
