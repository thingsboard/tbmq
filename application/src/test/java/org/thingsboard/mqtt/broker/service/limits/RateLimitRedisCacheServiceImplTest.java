/**
 * Copyright © 2016-2026 The Thingsboard Authors
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
import io.github.bucket4j.TokensInheritanceStrategy;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.redis.jedis.cas.JedisBasedProxyManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.cache.CacheConstants;
import org.thingsboard.mqtt.broker.cache.CacheProperties;
import org.thingsboard.mqtt.broker.config.ClientsLimitProperties;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = RateLimitRedisCacheServiceImpl.class)
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

    @MockitoBean
    private ClientsLimitProperties clientsLimitProperties;

    @MockitoSpyBean
    private RateLimitRedisCacheServiceImpl rateLimitRedisCacheService;

    @Before
    public void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(cacheProperties.prefixKey(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(clientsLimitProperties.isSessionsLimitEnabled()).thenReturn(true);
        when(clientsLimitProperties.isApplicationClientsLimitEnabled()).thenReturn(true);
        when(jedisBasedProxyManager.getProxyConfiguration(anyString())).thenReturn(Optional.empty());
        rateLimitRedisCacheService.init();
    }

    @Test
    public void testInitSessionCount() {
        int count = 5;
        when(valueOperations.setIfAbsent(anyString(), anyString())).thenReturn(true);

        rateLimitRedisCacheService.initSessionCount(count);

        verify(valueOperations).setIfAbsent(CacheConstants.CLIENT_SESSIONS_LIMIT_CACHE_KEY, Integer.toString(count));
    }

    @Test
    public void testInitSessionCountWhenSessionsLimitIsZero() {
        when(clientsLimitProperties.isSessionsLimitDisabled()).thenReturn(true);
        int count = 5;

        rateLimitRedisCacheService.initSessionCount(count);

        verify(valueOperations, never()).setIfAbsent(anyString(), anyString());
    }

    @Test
    public void testIncrementSessionCount() {
        when(valueOperations.increment(anyString())).thenReturn(6L);

        long newCount = rateLimitRedisCacheService.incrementSessionCount();

        assertEquals(6L, newCount);
        verify(valueOperations).increment(CacheConstants.CLIENT_SESSIONS_LIMIT_CACHE_KEY);
    }

    @Test
    public void testDecrementSessionCount() {
        rateLimitRedisCacheService.decrementSessionCount();

        verify(valueOperations).decrement(CacheConstants.CLIENT_SESSIONS_LIMIT_CACHE_KEY);
    }

    @Test
    public void testDecrementSessionCountWhenSessionsLimitIsZero() {
        when(clientsLimitProperties.isSessionsLimitDisabled()).thenReturn(true);

        rateLimitRedisCacheService.decrementSessionCount();

        verify(valueOperations, never()).decrement(anyString());
    }

    @Test
    public void testInitApplicationClientsCount() {
        int count = 5;
        when(valueOperations.setIfAbsent(anyString(), anyString())).thenReturn(true);

        rateLimitRedisCacheService.initApplicationClientsCount(count);

        verify(valueOperations).setIfAbsent(CacheConstants.APP_CLIENTS_LIMIT_CACHE_KEY, Integer.toString(count));
    }

    @Test
    public void testInitApplicationClientsCountWhenClientsLimitIsZero() {
        when(clientsLimitProperties.isApplicationClientsLimitDisabled()).thenReturn(true);
        int count = 5;

        rateLimitRedisCacheService.initApplicationClientsCount(count);

        verify(valueOperations, never()).setIfAbsent(anyString(), anyString());
    }

    @Test
    public void testIncrementApplicationClientsCount() {
        when(valueOperations.increment(anyString())).thenReturn(6L);

        long newCount = rateLimitRedisCacheService.incrementApplicationClientsCount();

        assertEquals(6L, newCount);
        verify(valueOperations).increment(CacheConstants.APP_CLIENTS_LIMIT_CACHE_KEY);
    }

    @Test
    public void testDecrementApplicationClientsCount() {
        rateLimitRedisCacheService.decrementApplicationClientsCount();

        verify(valueOperations).decrement(CacheConstants.APP_CLIENTS_LIMIT_CACHE_KEY);
    }

    @Test
    public void testDecrementApplicationClientsCountWhenClientsLimitIsZero() {
        when(clientsLimitProperties.isApplicationClientsLimitDisabled()).thenReturn(true);

        rateLimitRedisCacheService.decrementApplicationClientsCount();

        verify(valueOperations, never()).decrement(anyString());
    }

    @Test
    public void givenNoStoredConfiguration_whenInit_thenConfigurationIsNotReplaced() {
        clearInvocations(bucketProxy);
        BucketConfiguration totalMsgsBucketConfiguration = mock(BucketConfiguration.class);
        when(jedisBasedProxyManager.getProxy(eq(CacheConstants.TOTAL_MSGS_LIMIT_CACHE), any()))
                .thenReturn(bucketProxy);
        when(jedisBasedProxyManager.getProxyConfiguration(CacheConstants.TOTAL_MSGS_LIMIT_CACHE))
                .thenReturn(Optional.empty());

        new RateLimitRedisCacheServiceImpl(redisTemplate, jedisBasedProxyManager, null,
                totalMsgsBucketConfiguration, cacheProperties, clientsLimitProperties).init();

        verify(bucketProxy, never()).replaceConfiguration(any(), any());
    }

    @Test
    public void givenStoredConfigurationMatches_whenInit_thenConfigurationIsNotReplaced() {
        clearInvocations(bucketProxy);
        BucketConfiguration totalMsgsBucketConfiguration = mock(BucketConfiguration.class);
        when(jedisBasedProxyManager.getProxy(eq(CacheConstants.TOTAL_MSGS_LIMIT_CACHE), any()))
                .thenReturn(bucketProxy);
        BucketConfiguration stored = mock(BucketConfiguration.class);
        when(stored.equalsByContent(totalMsgsBucketConfiguration)).thenReturn(true);
        when(jedisBasedProxyManager.getProxyConfiguration(CacheConstants.TOTAL_MSGS_LIMIT_CACHE))
                .thenReturn(Optional.of(stored));

        new RateLimitRedisCacheServiceImpl(redisTemplate, jedisBasedProxyManager, null,
                totalMsgsBucketConfiguration, cacheProperties, clientsLimitProperties).init();

        verify(bucketProxy, never()).replaceConfiguration(any(), any());
    }

    @Test
    public void givenStoredConfigurationDiffers_whenInit_thenConfigurationIsReplacedProportionally() {
        clearInvocations(bucketProxy);
        BucketConfiguration totalMsgsBucketConfiguration = mock(BucketConfiguration.class);
        when(jedisBasedProxyManager.getProxy(eq(CacheConstants.TOTAL_MSGS_LIMIT_CACHE), any()))
                .thenReturn(bucketProxy);
        BucketConfiguration stored = mock(BucketConfiguration.class);
        when(stored.equalsByContent(totalMsgsBucketConfiguration)).thenReturn(false);
        when(jedisBasedProxyManager.getProxyConfiguration(CacheConstants.TOTAL_MSGS_LIMIT_CACHE))
                .thenReturn(Optional.of(stored));

        new RateLimitRedisCacheServiceImpl(redisTemplate, jedisBasedProxyManager, null,
                totalMsgsBucketConfiguration, cacheProperties, clientsLimitProperties).init();

        verify(bucketProxy).replaceConfiguration(totalMsgsBucketConfiguration, TokensInheritanceStrategy.PROPORTIONALLY);
    }

    @Test
    public void givenDisabledLimit_whenInit_thenBucketProxyIsNotCreated() {
        // devicePersistedMsgsBucketConfiguration is not registered as a bean in this context, so the
        // constructor receives null for it and the device bucket must never be touched.
        verify(jedisBasedProxyManager, never())
                .getProxy(eq(CacheConstants.DEVICE_PERSISTED_MSGS_LIMIT_CACHE), any());
        verify(jedisBasedProxyManager, never())
                .getProxyConfiguration(CacheConstants.DEVICE_PERSISTED_MSGS_LIMIT_CACHE);
    }

}
