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
package org.thingsboard.mqtt.broker.service.testing.integration;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.redis.jedis.cas.JedisBasedProxyManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.cache.CacheConstants;
import org.thingsboard.mqtt.broker.cache.CacheProperties;
import org.thingsboard.mqtt.broker.config.ClientsLimitProperties;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.service.limits.RateLimitRedisCacheServiceImpl;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = RateLimitBucketConfigurationIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@DaoSqlTest
@RunWith(SpringRunner.class)
public class RateLimitBucketConfigurationIntegrationTestCase extends AbstractPubSubIntegrationTest {

    private static final long SMALL_CAPACITY = 1000L;
    private static final long LARGE_CAPACITY = 20000L;

    @Autowired
    private JedisBasedProxyManager<String> jedisBasedProxyManager;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private ClientsLimitProperties clientsLimitProperties;

    @Test
    public void givenExistingBucket_whenConfiguredCapacityChanges_thenStoredConfigurationIsReplacedProportionally() {
        String prefix = "itest-" + UUID.randomUUID() + "-";
        String key = prefix + CacheConstants.TOTAL_MSGS_LIMIT_CACHE;

        // A broker boots with the smaller limit, and half the burst allowance is spent.
        initServiceWith(prefix, bucketConfiguration(SMALL_CAPACITY));
        BucketProxy proxy = jedisBasedProxyManager.getProxy(key, () -> bucketConfiguration(SMALL_CAPACITY));
        assertThat(proxy.tryConsume(SMALL_CAPACITY / 2)).isTrue();
        assertThat(storedCapacity(key)).isEqualTo(SMALL_CAPACITY);

        // The operator edits the yml and restarts: same key, larger configuration.
        initServiceWith(prefix, bucketConfiguration(LARGE_CAPACITY));

        assertThat(storedCapacity(key)).isEqualTo(LARGE_CAPACITY);
        // PROPORTIONALLY preserves the fill ratio: half of the small bucket becomes half of the large one.
        assertThat(jedisBasedProxyManager.getProxy(key, () -> bucketConfiguration(LARGE_CAPACITY)).getAvailableTokens())
                .isBetween(LARGE_CAPACITY / 2 - 10, LARGE_CAPACITY / 2 + 10);
    }

    @Test
    public void givenExistingBucket_whenConfigurationIsUnchanged_thenTokensAreNotRestored() {
        String prefix = "itest-" + UUID.randomUUID() + "-";
        String key = prefix + CacheConstants.TOTAL_MSGS_LIMIT_CACHE;

        initServiceWith(prefix, bucketConfiguration(SMALL_CAPACITY));
        BucketProxy proxy = jedisBasedProxyManager.getProxy(key, () -> bucketConfiguration(SMALL_CAPACITY));
        assertThat(proxy.tryConsume(SMALL_CAPACITY / 2)).isTrue();

        // Re-applying an unchanged configuration must not hand back the spent tokens: otherwise every node
        // restart would refill the shared cluster budget.
        initServiceWith(prefix, bucketConfiguration(SMALL_CAPACITY));

        assertThat(storedCapacity(key)).isEqualTo(SMALL_CAPACITY);
        assertThat(jedisBasedProxyManager.getProxy(key, () -> bucketConfiguration(SMALL_CAPACITY)).getAvailableTokens())
                .isBetween(SMALL_CAPACITY / 2 - 10, SMALL_CAPACITY / 2 + 10);
    }

    private void initServiceWith(String cachePrefix, BucketConfiguration totalMsgsConfiguration) {
        CacheProperties cacheProperties = new CacheProperties();
        cacheProperties.setCachePrefix(cachePrefix);
        new RateLimitRedisCacheServiceImpl(redisTemplate, jedisBasedProxyManager, null,
                totalMsgsConfiguration, cacheProperties, clientsLimitProperties).init();
    }

    private long storedCapacity(String key) {
        return jedisBasedProxyManager.getProxyConfiguration(key).orElseThrow().getBandwidths()[0].getCapacity();
    }

    private static BucketConfiguration bucketConfiguration(long capacity) {
        // A 24-hour refill period (~0.23 tokens/s) keeps the token count stable for the duration of the test.
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillGreedy(capacity, Duration.ofHours(24))
                        .build())
                .build();
    }
}
