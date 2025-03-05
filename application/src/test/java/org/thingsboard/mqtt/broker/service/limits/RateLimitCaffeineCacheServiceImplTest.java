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

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.local.LocalBucket;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.testcontainers.shaded.org.awaitility.Awaitility;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class RateLimitCaffeineCacheServiceImplTest {

    @InjectMocks
    private RateLimitLocalCacheServiceImpl rateLimitLocalCacheService;

    private AtomicLong sessionsCounter;
    private AtomicLong applicationClientsCounter;

    AutoCloseable autoCloseable;

    @Before
    public void setUp() {
        autoCloseable = MockitoAnnotations.openMocks(this);

        rateLimitLocalCacheService.setSessionsLimit(5);
        rateLimitLocalCacheService.setApplicationClientsLimit(5);

        rateLimitLocalCacheService.setCachePrefix(BrokerConstants.EMPTY_STR);
        // Initialize the service
        rateLimitLocalCacheService.init();

        sessionsCounter = rateLimitLocalCacheService.getSessionsCounter();
        applicationClientsCounter = rateLimitLocalCacheService.getApplicationClientsCounter();

        assertNotNull(sessionsCounter);
        assertNotNull(applicationClientsCounter);
    }

    @After
    public void tearDown() throws Exception {
        autoCloseable.close();
    }

    @Test
    public void testConcurrentIncrementSessionCount() {
        int threadCount = 2;
        CountDownLatch allThreadsReadyLatch = new CountDownLatch(threadCount);

        int count = 5;
        rateLimitLocalCacheService.initSessionCount(count);
        rateLimitLocalCacheService.initApplicationClientsCount(count);

        ExecutorService executor = null;
        try {
            executor = Executors.newFixedThreadPool(threadCount);
            for (int i = 0; i < 2; i++) {
                executor.submit(() -> {
                    allThreadsReadyLatch.countDown();
                    try {
                        allThreadsReadyLatch.await();
                        rateLimitLocalCacheService.incrementSessionCount();
                        rateLimitLocalCacheService.incrementApplicationClientsCount();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            executor.shutdown();
            // The final value should be 7 since we started with 5 and had 2 increments
            Awaitility.await().atMost(10, TimeUnit.SECONDS).until(executor::isTerminated);
            assertEquals(7, sessionsCounter.get());
            assertEquals(7, applicationClientsCounter.get());
        } finally {
            if (executor != null) {
                executor.shutdownNow();
            }
        }
    }

    @Test
    public void testInitSessionCount() {
        int count = 5;
        rateLimitLocalCacheService.initSessionCount(count);

        assertEquals(count, sessionsCounter.get());

        int newCount = 10;
        rateLimitLocalCacheService.initSessionCount(newCount);

        assertEquals(newCount, sessionsCounter.get());
    }

    @Test
    public void testSetSessionCount() {
        int count = 10;
        rateLimitLocalCacheService.setSessionCount(count);

        assertEquals(count, sessionsCounter.get());

        int newCount = 15;
        rateLimitLocalCacheService.setSessionCount(newCount);

        assertEquals(newCount, sessionsCounter.get());
    }

    @Test
    public void testIncrementSessionCount() {
        sessionsCounter.set(5);
        long newCount = rateLimitLocalCacheService.incrementSessionCount();

        assertEquals(6L, newCount);
        assertEquals(6L, sessionsCounter.get());
    }

    @Test
    public void testDecrementSessionCount() {
        sessionsCounter.set(5);
        rateLimitLocalCacheService.decrementSessionCount();

        assertEquals(4L, sessionsCounter.get());
    }

    @Test
    public void testDecrementSessionCount_ZeroValue() {
        sessionsCounter.set(0);
        rateLimitLocalCacheService.decrementSessionCount();

        assertEquals(0, sessionsCounter.get());
    }

    @Test
    public void testDecrementSessionCount_NegativeValue() {
        sessionsCounter.set(-1);
        rateLimitLocalCacheService.decrementSessionCount();

        assertEquals(0, sessionsCounter.get());
    }

    @Test
    public void testInitApplicationClientsCount() {
        int count = 5;
        rateLimitLocalCacheService.initApplicationClientsCount(count);

        assertEquals(count, applicationClientsCounter.get());
    }

    @Test
    public void testIncrementApplicationClientsCount() {
        applicationClientsCounter.set(5);
        long newCount = rateLimitLocalCacheService.incrementApplicationClientsCount();

        assertEquals(6L, newCount);
        assertEquals(6L, applicationClientsCounter.get());
    }

    @Test
    public void testDecrementApplicationClientsCount() {
        applicationClientsCounter.set(5);
        rateLimitLocalCacheService.decrementApplicationClientsCount();

        assertEquals(4, applicationClientsCounter.get());
    }

    @Test
    public void testDecrementApplicationClientsCount_ZeroValue() {
        applicationClientsCounter.set(0);
        rateLimitLocalCacheService.decrementApplicationClientsCount();

        assertEquals(0, applicationClientsCounter.get());
    }

    @Test
    public void testDecrementApplicationClientsCount_NegativeValue() {
        applicationClientsCounter.set(-1);
        rateLimitLocalCacheService.decrementApplicationClientsCount();

        assertEquals(0, applicationClientsCounter.get());
    }

    @Test
    public void testTryConsumeDevicePersistedMsg() {
        // Mocking bucket configuration
        Bandwidth limit = Bandwidth.builder().capacity(10).refillGreedy(10, Duration.ofMinutes(1)).build();
        BucketConfiguration bucketConfig = BucketConfiguration.builder().addLimit(limit).build();

        rateLimitLocalCacheService = new RateLimitLocalCacheServiceImpl(bucketConfig, null);

        // Assuming the bucket has 10 tokens initially
        for (int i = 0; i < 10; i++) {
            assertTrue(rateLimitLocalCacheService.tryConsumeDevicePersistedMsg());
        }
        assertFalse(rateLimitLocalCacheService.tryConsumeDevicePersistedMsg());
    }

    @Test
    public void testTryConsumeTotalMsg() {
        // Mocking bucket configuration
        Bandwidth limit = Bandwidth.builder().capacity(10).refillGreedy(10, Duration.ofMinutes(1)).build();
        BucketConfiguration bucketConfig = BucketConfiguration.builder().addLimit(limit).build();

        rateLimitLocalCacheService = new RateLimitLocalCacheServiceImpl(null, bucketConfig);

        // Assuming the bucket has 10 tokens initially
        for (int i = 0; i < 10; i++) {
            assertTrue(rateLimitLocalCacheService.tryConsumeTotalMsg());
        }
        assertFalse(rateLimitLocalCacheService.tryConsumeTotalMsg());
    }

    @Test
    public void testTryConsume_NoDevicePersistedMsgsBucketConfig() {
        RateLimitLocalCacheServiceImpl rateLimitCaffeineCacheServiceWithoutBucket = new RateLimitLocalCacheServiceImpl(null, null);
        Exception exception = assertThrows(NullPointerException.class, rateLimitCaffeineCacheServiceWithoutBucket::tryConsumeDevicePersistedMsg);
        assertTrue(exception.getMessage().contains("is null"));
    }

    @Test
    public void testTryConsume_NoTotalMsgsBucketConfig() {
        RateLimitLocalCacheServiceImpl rateLimitCaffeineCacheServiceWithoutBucket = new RateLimitLocalCacheServiceImpl(null, null);
        Exception exception = assertThrows(NullPointerException.class, rateLimitCaffeineCacheServiceWithoutBucket::tryConsumeTotalMsg);
        assertTrue(exception.getMessage().contains("is null"));
    }

    @Test
    public void testGetLocalBucket_NullConfiguration() {
        LocalBucket result = rateLimitLocalCacheService.getLocalBucket(null);
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

        LocalBucket result = rateLimitLocalCacheService.getLocalBucket(configuration);

        assertNotNull(result, "Expected non-null LocalBucket");

        assertEquals(configuration, result.getConfiguration());
    }

}
