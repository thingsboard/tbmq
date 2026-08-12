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

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.TokensInheritanceStrategy;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.redis.jedis.cas.JedisBasedProxyManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.cache.CacheConstants;
import org.thingsboard.mqtt.broker.cache.CacheProperties;
import org.thingsboard.mqtt.broker.config.ClientsLimitProperties;
import org.thingsboard.mqtt.broker.config.DevicePersistedMsgsRateLimitsConfiguration;
import org.thingsboard.mqtt.broker.config.TotalMsgsRateLimitsConfiguration;

@Slf4j
@Service
public class RateLimitRedisCacheServiceImpl implements RateLimitCacheService {

    static final String DEVICE_PERSISTED_MSGS_BUCKET_CONFIGURATION_BEAN = "devicePersistedMsgsBucketConfiguration";
    static final String TOTAL_MSGS_BUCKET_CONFIGURATION_BEAN = "totalMsgsBucketConfiguration";

    private final RedisTemplate<String, Object> redisTemplate;
    private final JedisBasedProxyManager<String> jedisBasedProxyManager;
    private final BucketConfiguration devicePersistedMsgsBucketConfiguration;
    private final BucketConfiguration totalMsgsBucketConfiguration;
    private final CacheProperties cacheProperties;
    private final ClientsLimitProperties clientsLimitProperties;
    private final DevicePersistedMsgsRateLimitsConfiguration devicePersistedMsgsRateLimitsConfiguration;
    private final TotalMsgsRateLimitsConfiguration totalMsgsRateLimitsConfiguration;

    private BucketProxy devicePersistedMsgsBucketProxy;
    private BucketProxy totalMsgsBucketProxy;
    private String clientSessionsLimitCacheKey;
    private String appClientsLimitCacheKey;

    public RateLimitRedisCacheServiceImpl(RedisTemplate<String, Object> redisTemplate,
                                          JedisBasedProxyManager<String> jedisBasedProxyManager,
                                          @Autowired(required = false)
                                          @Qualifier(DEVICE_PERSISTED_MSGS_BUCKET_CONFIGURATION_BEAN)
                                          BucketConfiguration devicePersistedMsgsBucketConfiguration,
                                          @Autowired(required = false)
                                          @Qualifier(TOTAL_MSGS_BUCKET_CONFIGURATION_BEAN)
                                          BucketConfiguration totalMsgsBucketConfiguration,
                                          CacheProperties cacheProperties,
                                          ClientsLimitProperties clientsLimitProperties,
                                          DevicePersistedMsgsRateLimitsConfiguration devicePersistedMsgsRateLimitsConfiguration,
                                          TotalMsgsRateLimitsConfiguration totalMsgsRateLimitsConfiguration) {
        this.redisTemplate = redisTemplate;
        this.jedisBasedProxyManager = jedisBasedProxyManager;
        this.devicePersistedMsgsBucketConfiguration = devicePersistedMsgsBucketConfiguration;
        this.totalMsgsBucketConfiguration = totalMsgsBucketConfiguration;
        this.cacheProperties = cacheProperties;
        this.clientsLimitProperties = clientsLimitProperties;
        this.devicePersistedMsgsRateLimitsConfiguration = devicePersistedMsgsRateLimitsConfiguration;
        this.totalMsgsRateLimitsConfiguration = totalMsgsRateLimitsConfiguration;
    }

    @PostConstruct
    public void init() {
        verifyBucketConfigurationPresent(devicePersistedMsgsRateLimitsConfiguration.isEnabled(),
                devicePersistedMsgsBucketConfiguration,
                "mqtt.rate-limits.device-persisted-messages", DEVICE_PERSISTED_MSGS_BUCKET_CONFIGURATION_BEAN);
        verifyBucketConfigurationPresent(totalMsgsRateLimitsConfiguration.isEnabled(),
                totalMsgsBucketConfiguration,
                "mqtt.rate-limits.total", TOTAL_MSGS_BUCKET_CONFIGURATION_BEAN);

        var devicePersistedMsgsLimitCacheKey = cacheProperties.prefixKey(CacheConstants.DEVICE_PERSISTED_MSGS_LIMIT_CACHE);
        var totalMsgsLimitCacheKey = cacheProperties.prefixKey(CacheConstants.TOTAL_MSGS_LIMIT_CACHE);

        this.devicePersistedMsgsBucketProxy = initBucketProxy(devicePersistedMsgsLimitCacheKey,
                devicePersistedMsgsBucketConfiguration, "device persisted messages");
        this.totalMsgsBucketProxy = initBucketProxy(totalMsgsLimitCacheKey,
                totalMsgsBucketConfiguration, "total messages");

        if (clientsLimitProperties.isSessionsLimitEnabled()) {
            clientSessionsLimitCacheKey = cacheProperties.prefixKey(CacheConstants.CLIENT_SESSIONS_LIMIT_CACHE_KEY);
        }
        if (clientsLimitProperties.isApplicationClientsLimitEnabled()) {
            appClientsLimitCacheKey = cacheProperties.prefixKey(CacheConstants.APP_CLIENTS_LIMIT_CACHE_KEY);
        }
    }

    /**
     * A limit's enabled flag and its {@link BucketConfiguration} bean are two independent derivations of the same
     * {@code mqtt.rate-limits.*.enabled} property: the flag comes from the properties bean, the configuration from a
     * {@code @Conditional @Bean} that reaches the constructor BY NAME through {@code @Qualifier}. Nothing forces the
     * two to agree - renaming the bean method being the obvious way to break it - and the consequence is not graceful
     * degradation: every caller passes its {@code isXxxLimitEnabled()} guard and then meters against a null bucket,
     * which is an NPE per published message on the hot path. Refuse to start instead.
     */
    private static void verifyBucketConfigurationPresent(boolean limitEnabled, BucketConfiguration configuration,
                                                         String propertyPrefix, String expectedBeanName) {
        if (limitEnabled && configuration == null) {
            throw new IllegalStateException(propertyPrefix + ".enabled is true but no BucketConfiguration was injected;"
                    + " expected a bean named '" + expectedBeanName + "'");
        }
    }

    private BucketProxy initBucketProxy(String key, BucketConfiguration configuration, String name) {
        if (configuration == null) {
            // The limit is disabled, so there is no configuration to compare against. Any existing bucket is
            // deliberately left in place: removing it would let a single misconfigured node wipe the bucket the
            // rest of the cluster is metering against.
            log.info("[{}] The {} rate limit is disabled, any bucket already stored under this key is left untouched", key, name);
            return null;
        }
        BucketProxy proxy = jedisBasedProxyManager.getProxy(key, () -> configuration);
        // getProxy() consults the supplier ONLY when the key is absent, and the bucket key carries no TTL, so an
        // existing bucket keeps the configuration it was first created with forever. Without the replacement
        // below, editing mqtt.rate-limits.* has no effect on any deployment that has already run once.
        jedisBasedProxyManager.getProxyConfiguration(key)
                .filter(stored -> !stored.equalsByContent(configuration))
                .ifPresent(stored -> {
                    proxy.replaceConfiguration(configuration, TokensInheritanceStrategy.PROPORTIONALLY);
                    log.info("[{}] Replaced stale {} rate limit configuration {} with {}", key, name, stored, configuration);
                });
        // This is what the node asked for, not necessarily what is stored: in a cluster whose nodes disagree the
        // last writer wins, so re-reading the key here would still not give a durable answer.
        log.info("[{}] Configured {} rate limit configuration: {}", key, name, configuration);
        return proxy;
    }

    @Override
    public void initSessionCount(int count) {
        if (clientsLimitProperties.isSessionsLimitDisabled()) {
            return;
        }
        boolean initialized = initCacheWithCount(clientSessionsLimitCacheKey, count);
        if (initialized) {
            log.info("Client session limit cache is initialized with count {}", count);
            return;
        }
        reInitCacheWithCount(clientSessionsLimitCacheKey, count);
        log.info("Client session limit cache is re-initialized with count {}", count);
    }

    @Override
    public void initApplicationClientsCount(int count) {
        if (clientsLimitProperties.isApplicationClientsLimitDisabled()) {
            return;
        }
        boolean initialized = initCacheWithCount(appClientsLimitCacheKey, count);
        if (initialized) {
            log.info("Application clients limit cache is initialized with count {}", count);
        }
    }

    @Override
    public long incrementSessionCount() {
        log.debug("Incrementing session count");
        return increment(clientSessionsLimitCacheKey);
    }

    @Override
    public long incrementApplicationClientsCount() {
        log.debug("Incrementing Application clients count");
        return increment(appClientsLimitCacheKey);
    }

    @Override
    public void decrementSessionCount() {
        if (clientsLimitProperties.isSessionsLimitDisabled()) {
            return;
        }
        log.debug("Decrementing session count");
        decrement(clientSessionsLimitCacheKey);
    }

    @Override
    public void decrementApplicationClientsCount() {
        if (clientsLimitProperties.isApplicationClientsLimitDisabled()) {
            return;
        }
        log.debug("Decrementing Application clients count");
        decrement(appClientsLimitCacheKey);
    }

    @Override
    public long tryConsumeDevicePersistedMsgs(long limit) {
        return tryConsume(devicePersistedMsgsBucketProxy, limit);
    }

    @Override
    public long tryConsumeTotalMsgs(long limit) {
        return tryConsume(totalMsgsBucketProxy, limit);
    }

    private long tryConsume(Bucket bucket, long limit) {
        if (limit <= 0) {
            return 0;
        }
        return bucket.tryConsumeAsMuchAsPossible(limit);
    }

    private Boolean initCacheWithCount(String key, int count) {
        return redisTemplate.opsForValue().setIfAbsent(key, Integer.toString(count));
    }

    private void reInitCacheWithCount(String key, int count) {
        redisTemplate.opsForValue().set(key, Integer.toString(count));
    }

    private Long increment(String key) {
        return redisTemplate.opsForValue().increment(key);
    }

    private void decrement(String key) {
        redisTemplate.opsForValue().decrement(key);
    }

}

