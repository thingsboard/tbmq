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

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.redis.jedis.cas.JedisBasedProxyManager;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.thingsboard.mqtt.broker.cache.CacheConstants;
import org.thingsboard.mqtt.broker.cache.CacheProperties;
import org.thingsboard.mqtt.broker.config.ClientsLimitProperties;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Both {@code BucketConfiguration} beans are optional: each is registered only when its limit is enabled. When
 * exactly one of them is enabled there is a single candidate of the type, so the two constructor parameters must be
 * qualified by bean name - otherwise Spring injects the one surviving bean into BOTH parameters and the disabled
 * limit's Redis key gets read and rewritten with the other limit's numbers.
 */
public class RateLimitBucketConfigurationInjectionTest {

    private static final long TOTAL_CAPACITY = 50_000L;
    private static final long DEVICE_CAPACITY = 1_000L;

    @Test
    public void givenOnlyTotalMsgsLimitEnabled_whenInit_thenDeviceBucketIsNeverTouched() {
        try (AnnotationConfigApplicationContext ctx = newContext(TotalMsgsLimitBean.class)) {
            JedisBasedProxyManager<String> proxyManager = proxyManagerOf(ctx);

            verify(proxyManager, never()).getProxy(eq(CacheConstants.DEVICE_PERSISTED_MSGS_LIMIT_CACHE), any());
            verify(proxyManager, never()).getProxyConfiguration(CacheConstants.DEVICE_PERSISTED_MSGS_LIMIT_CACHE);

            assertThat(capacityOfConfigurationPassedTo(proxyManager, CacheConstants.TOTAL_MSGS_LIMIT_CACHE))
                    .isEqualTo(TOTAL_CAPACITY);
        }
    }

    @Test
    public void givenOnlyDevicePersistedMsgsLimitEnabled_whenInit_thenTotalBucketIsNeverTouched() {
        try (AnnotationConfigApplicationContext ctx = newContext(DevicePersistedMsgsLimitBean.class)) {
            JedisBasedProxyManager<String> proxyManager = proxyManagerOf(ctx);

            verify(proxyManager, never()).getProxy(eq(CacheConstants.TOTAL_MSGS_LIMIT_CACHE), any());
            verify(proxyManager, never()).getProxyConfiguration(CacheConstants.TOTAL_MSGS_LIMIT_CACHE);

            assertThat(capacityOfConfigurationPassedTo(proxyManager, CacheConstants.DEVICE_PERSISTED_MSGS_LIMIT_CACHE))
                    .isEqualTo(DEVICE_CAPACITY);
        }
    }

    @Test
    public void givenBothLimitsEnabled_whenInit_thenEachBucketGetsItsOwnConfiguration() {
        try (AnnotationConfigApplicationContext ctx =
                     newContext(TotalMsgsLimitBean.class, DevicePersistedMsgsLimitBean.class)) {
            JedisBasedProxyManager<String> proxyManager = proxyManagerOf(ctx);

            assertThat(capacityOfConfigurationPassedTo(proxyManager, CacheConstants.TOTAL_MSGS_LIMIT_CACHE))
                    .isEqualTo(TOTAL_CAPACITY);
            assertThat(capacityOfConfigurationPassedTo(proxyManager, CacheConstants.DEVICE_PERSISTED_MSGS_LIMIT_CACHE))
                    .isEqualTo(DEVICE_CAPACITY);
        }
    }

    private static AnnotationConfigApplicationContext newContext(Class<?>... enabledLimitConfigurations) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.register(MockedInfrastructure.class, RateLimitRedisCacheServiceImpl.class);
        ctx.register(enabledLimitConfigurations);
        ctx.refresh();
        return ctx;
    }

    @SuppressWarnings("unchecked")
    private static JedisBasedProxyManager<String> proxyManagerOf(AnnotationConfigApplicationContext ctx) {
        return ctx.getBean(JedisBasedProxyManager.class);
    }

    private static long capacityOfConfigurationPassedTo(JedisBasedProxyManager<String> proxyManager, String key) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Supplier<BucketConfiguration>> captor = ArgumentCaptor.forClass(Supplier.class);
        verify(proxyManager).getProxy(eq(key), captor.capture());
        return captor.getValue().get().getBandwidths()[0].getCapacity();
    }

    private static BucketConfiguration bucketConfiguration(long capacity) {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillGreedy(capacity, Duration.ofSeconds(1))
                        .build())
                .build();
    }

    // These bean holders are deliberately NOT annotated with @Configuration: AbstractPubSubIntegrationTest scans
    // the whole org.thingsboard.mqtt.broker package, and a stereotyped nested class here would be swept into every
    // integration test context and override the real beans. Without the stereotype they are only picked up by the
    // explicit register() call above, in Spring's "lite" @Bean mode.
    static class TotalMsgsLimitBean {

        // The method name is the bean name, and it is what the constructor @Qualifier asks for. Keep it in
        // sync with TotalMsgsRateLimitsConfiguration#totalMsgsBucketConfiguration.
        @Bean
        public BucketConfiguration totalMsgsBucketConfiguration() {
            return bucketConfiguration(TOTAL_CAPACITY);
        }
    }

    static class DevicePersistedMsgsLimitBean {

        // Keep in sync with DevicePersistedMsgsRateLimitsConfiguration#devicePersistedMsgsBucketConfiguration.
        @Bean
        public BucketConfiguration devicePersistedMsgsBucketConfiguration() {
            return bucketConfiguration(DEVICE_CAPACITY);
        }
    }

    static class MockedInfrastructure {

        // The mocks have to be usable from the service's @PostConstruct, which runs while the context refreshes,
        // so they are stubbed here rather than in a @Before method.
        @Bean
        @SuppressWarnings("unchecked")
        public JedisBasedProxyManager<String> jedisBasedProxyManager() {
            JedisBasedProxyManager<String> proxyManager = mock(JedisBasedProxyManager.class);
            when(proxyManager.getProxy(anyString(), any())).thenReturn(mock(BucketProxy.class));
            when(proxyManager.getProxyConfiguration(anyString())).thenReturn(Optional.empty());
            return proxyManager;
        }

        @Bean
        public CacheProperties cacheProperties() {
            CacheProperties cacheProperties = mock(CacheProperties.class);
            when(cacheProperties.prefixKey(anyString())).thenAnswer(inv -> inv.getArgument(0));
            return cacheProperties;
        }

        @Bean
        @SuppressWarnings("unchecked")
        public RedisTemplate<String, Object> redisTemplate() {
            return mock(RedisTemplate.class);
        }

        @Bean
        public ClientsLimitProperties clientsLimitProperties() {
            return mock(ClientsLimitProperties.class);
        }
    }
}
