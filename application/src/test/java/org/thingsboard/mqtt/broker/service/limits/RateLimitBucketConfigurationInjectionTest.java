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
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.redis.jedis.cas.JedisBasedProxyManager;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.thingsboard.mqtt.broker.cache.CacheConstants;
import org.thingsboard.mqtt.broker.cache.CacheProperties;
import org.thingsboard.mqtt.broker.config.ClientsLimitProperties;
import org.thingsboard.mqtt.broker.config.DevicePersistedMsgsRateLimitsConfiguration;
import org.thingsboard.mqtt.broker.config.TotalMsgsRateLimitsConfiguration;

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
 * <p>
 * The contract under test is therefore a bean-name-to-{@code @Qualifier} one, which means the bean names have to be
 * the production ones: {@link TotalMsgsRateLimitsConfiguration} and {@link DevicePersistedMsgsRateLimitsConfiguration}
 * are registered here as-is and switched on through the same {@code mqtt.rate-limits.*} properties an operator sets,
 * rather than being restated by local stand-ins. Renaming a production {@code @Bean} method has to break this test,
 * not slip past it - which is also why each assertion compares against the bean looked up BY NAME instead of just
 * checking the capacity that reached the parameter.
 * <p>
 * Both configuration classes are always registered, exactly as component scanning would have them in production; it
 * is only their {@code @Conditional} {@code @Bean} methods that the properties below switch on and off.
 */
public class RateLimitBucketConfigurationInjectionTest {

    private static final String TOTAL_MSGS_BUCKET_CONFIGURATION_BEAN = "totalMsgsBucketConfiguration";
    private static final String DEVICE_PERSISTED_MSGS_BUCKET_CONFIGURATION_BEAN = "devicePersistedMsgsBucketConfiguration";

    private static final String[] TOTAL_MSGS_LIMIT_ENABLED = {
            "mqtt.rate-limits.total.enabled=true",
            "mqtt.rate-limits.total.config=1000:1,50000:60"
    };
    private static final String[] DEVICE_PERSISTED_MSGS_LIMIT_ENABLED = {
            "mqtt.rate-limits.device-persisted-messages.enabled=true",
            "mqtt.rate-limits.device-persisted-messages.config=100:1,1000:60"
    };

    @Test
    public void givenOnlyTotalMsgsLimitEnabled_whenInit_thenDeviceBucketIsNeverTouched() {
        try (AnnotationConfigApplicationContext ctx = newContext(TOTAL_MSGS_LIMIT_ENABLED)) {
            JedisBasedProxyManager<String> proxyManager = proxyManagerOf(ctx);

            verify(proxyManager, never()).getProxy(eq(CacheConstants.DEVICE_PERSISTED_MSGS_LIMIT_CACHE), any());
            verify(proxyManager, never()).getProxyConfiguration(CacheConstants.DEVICE_PERSISTED_MSGS_LIMIT_CACHE);

            assertThat(configurationPassedTo(proxyManager, CacheConstants.TOTAL_MSGS_LIMIT_CACHE))
                    .isSameAs(bucketConfigurationBean(ctx, TOTAL_MSGS_BUCKET_CONFIGURATION_BEAN));
        }
    }

    @Test
    public void givenOnlyDevicePersistedMsgsLimitEnabled_whenInit_thenTotalBucketIsNeverTouched() {
        try (AnnotationConfigApplicationContext ctx = newContext(DEVICE_PERSISTED_MSGS_LIMIT_ENABLED)) {
            JedisBasedProxyManager<String> proxyManager = proxyManagerOf(ctx);

            verify(proxyManager, never()).getProxy(eq(CacheConstants.TOTAL_MSGS_LIMIT_CACHE), any());
            verify(proxyManager, never()).getProxyConfiguration(CacheConstants.TOTAL_MSGS_LIMIT_CACHE);

            assertThat(configurationPassedTo(proxyManager, CacheConstants.DEVICE_PERSISTED_MSGS_LIMIT_CACHE))
                    .isSameAs(bucketConfigurationBean(ctx, DEVICE_PERSISTED_MSGS_BUCKET_CONFIGURATION_BEAN));
        }
    }

    @Test
    public void givenBothLimitsEnabled_whenInit_thenEachBucketGetsItsOwnConfiguration() {
        try (AnnotationConfigApplicationContext ctx =
                     newContext(TOTAL_MSGS_LIMIT_ENABLED, DEVICE_PERSISTED_MSGS_LIMIT_ENABLED)) {
            JedisBasedProxyManager<String> proxyManager = proxyManagerOf(ctx);

            assertThat(configurationPassedTo(proxyManager, CacheConstants.TOTAL_MSGS_LIMIT_CACHE))
                    .isSameAs(bucketConfigurationBean(ctx, TOTAL_MSGS_BUCKET_CONFIGURATION_BEAN));
            assertThat(configurationPassedTo(proxyManager, CacheConstants.DEVICE_PERSISTED_MSGS_LIMIT_CACHE))
                    .isSameAs(bucketConfigurationBean(ctx, DEVICE_PERSISTED_MSGS_BUCKET_CONFIGURATION_BEAN));
        }
    }

    private static AnnotationConfigApplicationContext newContext(String[]... enabledLimitProperties) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        for (String[] properties : enabledLimitProperties) {
            TestPropertyValues.of(properties).applyTo(ctx);
        }
        ctx.register(MockedInfrastructure.class,
                TotalMsgsRateLimitsConfiguration.class,
                DevicePersistedMsgsRateLimitsConfiguration.class,
                RateLimitRedisCacheServiceImpl.class);
        ctx.refresh();
        return ctx;
    }

    @SuppressWarnings("unchecked")
    private static JedisBasedProxyManager<String> proxyManagerOf(AnnotationConfigApplicationContext ctx) {
        return ctx.getBean(JedisBasedProxyManager.class);
    }

    /**
     * Fails with {@code NoSuchBeanDefinitionException} if the production {@code @Bean} method is ever renamed away
     * from the name the constructor {@code @Qualifier} asks for - which is the drift this test exists to catch.
     */
    private static BucketConfiguration bucketConfigurationBean(AnnotationConfigApplicationContext ctx, String beanName) {
        return ctx.getBean(beanName, BucketConfiguration.class);
    }

    private static BucketConfiguration configurationPassedTo(JedisBasedProxyManager<String> proxyManager, String key) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Supplier<BucketConfiguration>> captor = ArgumentCaptor.forClass(Supplier.class);
        verify(proxyManager).getProxy(eq(key), captor.capture());
        return captor.getValue().get();
    }

    // Deliberately NOT annotated with @Configuration: AbstractPubSubIntegrationTest scans the whole
    // org.thingsboard.mqtt.broker package, and a stereotyped nested class here would be swept into every integration
    // test context and override the real beans. Without the stereotype it is only picked up by the explicit
    // register() call above, in Spring's "lite" @Bean mode. @EnableConfigurationProperties is not a stereotype, and
    // is what binds mqtt.rate-limits.* onto the two registered configuration classes in a plain context.
    @EnableConfigurationProperties
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
