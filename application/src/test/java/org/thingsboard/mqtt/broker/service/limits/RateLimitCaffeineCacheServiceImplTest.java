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
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.local.LocalBucket;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.thingsboard.mqtt.broker.cache.CacheConstants;

import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        rateLimitCaffeineCacheService.setApplicationClientsLimit(5);

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

    @Test
    public void testInitApplicationClientsCount() {
        int count = 5;
        rateLimitCaffeineCacheService.initApplicationClientsCount(count);

        Long actualCount = (Long) caffeineCache.getIfPresent(CacheConstants.APP_CLIENTS_LIMIT_CACHE_KEY);
        assertEquals(Long.valueOf(count), actualCount);
    }

    @Test
    public void testIncrementApplicationClientsCount() {
        caffeineCache.put(CacheConstants.APP_CLIENTS_LIMIT_CACHE_KEY, 5L);
        long newCount = rateLimitCaffeineCacheService.incrementApplicationClientsCount();

        assertEquals(6L, newCount);
    }

    @Test
    public void testDecrementApplicationClientsCount() {
        caffeineCache.put(CacheConstants.APP_CLIENTS_LIMIT_CACHE_KEY, 5L);
        rateLimitCaffeineCacheService.decrementApplicationClientsCount();

        Long actualCount = (Long) caffeineCache.getIfPresent(CacheConstants.APP_CLIENTS_LIMIT_CACHE_KEY);
        assertEquals(Long.valueOf(4), actualCount);
    }

    @Test
    public void testDecrementApplicationClientsCount_ZeroValue() {
        caffeineCache.put(CacheConstants.APP_CLIENTS_LIMIT_CACHE_KEY, 0L);
        rateLimitCaffeineCacheService.decrementApplicationClientsCount();

        Long actualCount = (Long) caffeineCache.getIfPresent(CacheConstants.APP_CLIENTS_LIMIT_CACHE_KEY);
        assertEquals(Long.valueOf(0), actualCount);
    }

    @Test
    public void testDecrementApplicationClientsCount_NegativeValue() {
        caffeineCache.put(CacheConstants.APP_CLIENTS_LIMIT_CACHE_KEY, -1L);
        rateLimitCaffeineCacheService.decrementApplicationClientsCount();

        Long actualCount = (Long) caffeineCache.getIfPresent(CacheConstants.APP_CLIENTS_LIMIT_CACHE_KEY);
        assertEquals(Long.valueOf(0), actualCount);
    }

    @Test
    public void testTryConsume() {
        // Mocking bucket configuration
        Bandwidth limit = Bandwidth.builder().capacity(10).refillGreedy(10, Duration.ofMinutes(1)).build();
        BucketConfiguration bucketConfig = BucketConfiguration.builder().addLimit(limit).build();

        rateLimitCaffeineCacheService = new RateLimitCaffeineCacheServiceImpl(cacheManager, bucketConfig);

        // Assuming the bucket has 10 tokens initially
        for (int i = 0; i < 10; i++) {
            assertTrue(rateLimitCaffeineCacheService.tryConsume());
        }
        assertFalse(rateLimitCaffeineCacheService.tryConsume());
    }

    @Test
    public void testTryConsume_NoBucketConfig() {
        RateLimitCaffeineCacheServiceImpl rateLimitCaffeineCacheServiceWithoutBucket = new RateLimitCaffeineCacheServiceImpl(cacheManager, null);
        Exception exception = assertThrows(NullPointerException.class, rateLimitCaffeineCacheServiceWithoutBucket::tryConsume);
        assertTrue(exception.getMessage().contains("is null"));
    }

    @Test
    public void testGetLocalBucket_NullConfiguration() {
        LocalBucket result = rateLimitCaffeineCacheService.getLocalBucket(null);
        assertNull(result, "Expected null when bucketConfiguration is null");
    }

    @Test
    public void testGetLocalBucket_WithBandwidths() {
        BucketConfiguration configuration = BucketConfiguration
                .builder()
                .addLimit(Bandwidth
                        .builder()
                        .capacity(1)
                        .refillGreedy(10, Duration.ofMinutes(1))
                        .build())
                .build();

        LocalBucket result = rateLimitCaffeineCacheService.getLocalBucket(configuration);

        assertNotNull(result, "Expected non-null LocalBucket");

        assertEquals(configuration, result.getConfiguration());
    }

}
