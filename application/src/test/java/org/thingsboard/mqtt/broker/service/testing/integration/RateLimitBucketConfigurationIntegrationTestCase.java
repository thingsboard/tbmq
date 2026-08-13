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
import org.thingsboard.mqtt.broker.config.DevicePersistedMsgsRateLimitsConfiguration;
import org.thingsboard.mqtt.broker.config.TotalMsgsRateLimitsConfiguration;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.service.limits.RateLimitRedisCacheServiceImpl;

import java.time.Duration;
import java.util.Arrays;
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

    private static final long SMALL_BURST_CAPACITY = 1000L;
    private static final long SMALL_SUSTAINED_CAPACITY = 50000L;
    private static final long LARGE_BURST_CAPACITY = 2000L;
    private static final long LARGE_SUSTAINED_CAPACITY = 100000L;

    private static final Duration REFILL_PERIOD_PER_TOKEN = Duration.ofHours(1);

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
        // PROPORTIONALLY preserves the fill ratio - but only because this configuration has a SINGLE bandwidth, so
        // bucket4j can pair the old bandwidth with the new one. See the two-bandwidth test below for the general case.
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

    @Test
    public void givenExistingTwoBandwidthBucket_whenConfiguredCapacityChanges_thenTokensAreReInitializedToFull() {
        String prefix = "itest-" + UUID.randomUUID() + "-";
        String key = prefix + CacheConstants.TOTAL_MSGS_LIMIT_CACHE;

        // mqtt.rate-limits.total.config accepts a comma-separated list, so a deployment may well be running two
        // bandwidths even though the shipped default is a single one - and that is the shape whose reconfiguration
        // behaves surprisingly, which is what this pins.
        initServiceWith(prefix, twoBandwidthConfiguration(SMALL_BURST_CAPACITY, SMALL_SUSTAINED_CAPACITY));
        BucketProxy proxy = jedisBasedProxyManager.getProxy(key,
                () -> twoBandwidthConfiguration(SMALL_BURST_CAPACITY, SMALL_SUSTAINED_CAPACITY));
        assertThat(proxy.tryConsume(SMALL_BURST_CAPACITY / 2)).isTrue();
        assertThat(storedCapacities(key)).containsExactly(SMALL_BURST_CAPACITY, SMALL_SUSTAINED_CAPACITY);
        assertThat(availableTokensPerBandwidth(key, SMALL_BURST_CAPACITY, SMALL_SUSTAINED_CAPACITY))
                .containsExactly(SMALL_BURST_CAPACITY / 2, SMALL_SUSTAINED_CAPACITY - SMALL_BURST_CAPACITY / 2);

        // The operator edits the yml and restarts: same key, both capacities doubled.
        initServiceWith(prefix, twoBandwidthConfiguration(LARGE_BURST_CAPACITY, LARGE_SUSTAINED_CAPACITY));

        assertThat(storedCapacities(key)).containsExactly(LARGE_BURST_CAPACITY, LARGE_SUSTAINED_CAPACITY);
        // PROPORTIONALLY does NOT preserve the fill ratio here. BucketState64BitsInteger#replaceConfiguration pairs
        // an old bandwidth with a new one only when the new bandwidth carries a non-null id, or when BOTH
        // configurations have fewer than two null-id bandwidths. AbstractMsgsRateLimitsConfiguration builds
        // bandwidths without ids, so with two of them nothing matches and every bandwidth is re-initialized to
        // calculateInitialTokens(...) - i.e. back to full capacity. A reconfiguration therefore hands the whole
        // cluster a fresh budget instead of carrying the spent tokens over.
        assertThat(availableTokensPerBandwidth(key, LARGE_BURST_CAPACITY, LARGE_SUSTAINED_CAPACITY))
                .containsExactly(LARGE_BURST_CAPACITY, LARGE_SUSTAINED_CAPACITY);
    }

    private void initServiceWith(String cachePrefix, BucketConfiguration totalMsgsConfiguration) {
        CacheProperties cacheProperties = new CacheProperties();
        cacheProperties.setCachePrefix(cachePrefix);
        // The configuration is handed in directly rather than taken from the properties beans, so both limits read as
        // disabled here; that is what keeps init()'s enabled-but-unconfigured guard out of the way.
        new RateLimitRedisCacheServiceImpl(redisTemplate, jedisBasedProxyManager, null,
                totalMsgsConfiguration, cacheProperties, clientsLimitProperties,
                new DevicePersistedMsgsRateLimitsConfiguration(), new TotalMsgsRateLimitsConfiguration()).init();
    }

    private long storedCapacity(String key) {
        return jedisBasedProxyManager.getProxyConfiguration(key).orElseThrow().getBandwidths()[0].getCapacity();
    }

    private long[] storedCapacities(String key) {
        return Arrays.stream(jedisBasedProxyManager.getProxyConfiguration(key).orElseThrow().getBandwidths())
                .mapToLong(Bandwidth::getCapacity)
                .toArray();
    }

    private long[] availableTokensPerBandwidth(String key, long burstCapacity, long sustainedCapacity) {
        return jedisBasedProxyManager.getProxy(key, () -> twoBandwidthConfiguration(burstCapacity, sustainedCapacity))
                .asVerbose().getAvailableTokens().getDiagnostics().getAvailableTokensPerEachBandwidth();
    }

    private static BucketConfiguration bucketConfiguration(long capacity) {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillGreedy(capacity, refillPeriodFor(capacity))
                        .build())
                .build();
    }

    private static BucketConfiguration twoBandwidthConfiguration(long burstCapacity, long sustainedCapacity) {
        // Models the SHAPE of the shipped default ("<burst>:1,<sustained>:60") - a small burst bandwidth plus a large
        // sustained one - but with far longer periods than the literal 1s/60s. With the real periods a greedy refill
        // regenerates the whole bucket within a second, so any token assertion would be meaningless; what this test
        // pins is bandwidth MATCHING, which the period does not affect. Neither bandwidth gets an id, exactly like
        // AbstractMsgsRateLimitsConfiguration builds them.
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(burstCapacity)
                        .refillGreedy(burstCapacity, refillPeriodFor(burstCapacity))
                        .build())
                .addLimit(Bandwidth.builder()
                        .capacity(sustainedCapacity)
                        .refillGreedy(sustainedCapacity, refillPeriodFor(sustainedCapacity))
                        .build())
                .build();
    }

    /**
     * A greedy refill regenerates one token every {@code refillPeriod / capacity}, so a fixed period would make a
     * large bandwidth tick far faster than a small one - with a shared 48-hour period the 50000-token sustained
     * bandwidth gained a token every 3.5s, which is well inside the stall budget of a CI box running Kafka, Postgres
     * and Valkey containers, and enough to break the exact per-bandwidth token assertions in this class. Scaling the
     * period to the capacity instead pins every bandwidth at one token per {@link #REFILL_PERIOD_PER_TOKEN}, whatever
     * the capacities are, so no bandwidth can gain a token mid-test.
     */
    private static Duration refillPeriodFor(long capacity) {
        return REFILL_PERIOD_PER_TOKEN.multipliedBy(capacity);
    }
}
