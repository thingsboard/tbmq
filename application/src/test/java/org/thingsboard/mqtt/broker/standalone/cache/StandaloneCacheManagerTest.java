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
package org.thingsboard.mqtt.broker.standalone.cache;

import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCache;
import org.thingsboard.mqtt.broker.cache.CacheNameResolver;
import org.thingsboard.mqtt.broker.cache.CacheProperties;
import org.thingsboard.mqtt.broker.cache.CacheSpecs;
import org.thingsboard.mqtt.broker.cache.CacheStats;
import org.thingsboard.mqtt.broker.cache.TbCacheOps;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.standalone.state.InMemoryKeyValueStore;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StandaloneCacheManagerTest {

    private static final String PREFIX = "p:";

    private final AtomicLong nanos = new AtomicLong();
    private final Ticker ticker = nanos::get;
    private final InMemoryKeyValueStore store = new InMemoryKeyValueStore();

    private CacheProperties properties;
    private StandaloneCacheManager manager;
    private TbCacheOps ops;

    @BeforeEach
    void setUp() {
        properties = properties(0);
        manager = StandaloneCacheManager.create(properties, store, 5, ticker);
        ops = new TbCacheOps(new CacheNameResolver(manager, properties));
    }

    @Test
    void aNameWithoutASpecReturnsNull() {
        assertThat(manager.getCache("nope")).isNull();
        assertThat(manager.getCache("mqttClientCredentials")).isNull(); // the raw name is not registered, only the prefixed one
    }

    @Test
    void allFiveSpecsAreServedUnderTheirPrefixedNames() {
        assertThat(manager.getCacheNames()).hasSize(5).allMatch(name -> name.startsWith(PREFIX));
        for (String name : manager.getCacheNames()) {
            assertThat(manager.getCache(name).getName()).isEqualTo(name);
        }
    }

    @Test
    void onlyTheTwoSessionAttributeCachesAreStoreBacked() {
        assertThat(manager.getCache(PREFIX + "clientSessionCredentials")).isInstanceOf(StoreBackedCache.class);
        assertThat(manager.getCache(PREFIX + "clientMqttVersion")).isInstanceOf(StoreBackedCache.class);
        assertThat(manager.getCache(PREFIX + "mqttClientCredentials")).isInstanceOf(CaffeineCache.class);
        assertThat(manager.getCache(PREFIX + "basicCredentialsPassword")).isInstanceOf(CaffeineCache.class);
        assertThat(manager.getCache(PREFIX + "sslRegexBasedCredentials")).isInstanceOf(CaffeineCache.class);
    }

    @Test
    void withoutACachePrefixTheRawNamesAreUsed() {
        CacheProperties noPrefix = properties(0);
        noPrefix.setCachePrefix(null);
        StandaloneCacheManager unprefixed = StandaloneCacheManager.create(noPrefix, store, 5, ticker);

        assertThat(unprefixed.getCacheNames()).contains("mqttClientCredentials", "clientMqttVersion");
        assertThat(unprefixed.getCache("clientMqttVersion")).isInstanceOf(StoreBackedCache.class);
    }

    @Test
    void anEntryExpiresAfterItsTtlAndAZeroTtlNeverExpires() {
        Cache basic = manager.getCache(PREFIX + "basicCredentialsPassword"); // 1 minute
        Cache ssl = manager.getCache(PREFIX + "sslRegexBasedCredentials"); // 0 = no expiry
        basic.put("k", "v");
        ssl.put("k", "v");

        nanos.addAndGet(Duration.ofSeconds(59).toNanos());
        assertThat(basic.get("k")).isNotNull();

        nanos.addAndGet(Duration.ofSeconds(2).toNanos());
        assertThat(basic.get("k")).isNull();
        assertThat(ssl.get("k")).isNotNull();

        nanos.addAndGet(Duration.ofDays(3650).toNanos());
        assertThat(ssl.get("k")).isNotNull();
    }

    @Test
    void aTtlInMinutesIsHonoured() {
        Cache credentials = manager.getCache(PREFIX + "mqttClientCredentials"); // 1440 minutes
        credentials.put("k", "v");

        nanos.addAndGet(Duration.ofMinutes(1439).toNanos());
        assertThat(credentials.get("k")).isNotNull();

        nanos.addAndGet(Duration.ofMinutes(2).toNanos());
        assertThat(credentials.get("k")).isNull();
    }

    @Test
    void aCachedNullIsDistinctFromAMiss() {
        ops.putNull("mqttClientCredentials", "id-1");

        assertThat(ops.lookup("mqttClientCredentials", "id-1", MqttClientCredentials.class).status())
                .isEqualTo(TbCacheOps.Status.CACHED_NULL);
        assertThat(ops.lookup("mqttClientCredentials", "id-2", MqttClientCredentials.class).status())
                .isEqualTo(TbCacheOps.Status.ABSENT);
    }

    @Test
    void aCachedNullIsDistinctFromAMissInTheStoreBackedCaches() {
        ops.putNull("clientSessionCredentials", "c1");

        assertThat(ops.lookup("clientSessionCredentials", "c1", String.class).status())
                .isEqualTo(TbCacheOps.Status.CACHED_NULL);
        assertThat(ops.lookup("clientSessionCredentials", "c2", String.class).status())
                .isEqualTo(TbCacheOps.Status.ABSENT);
    }

    @Test
    void anEnumKeyWorks() {
        ops.put("sslRegexBasedCredentials", ClientCredentialsType.X_509, "creds");

        assertThat(ops.lookup("sslRegexBasedCredentials", ClientCredentialsType.X_509, String.class).value()).isEqualTo("creds");
        assertThat(ops.lookup("sslRegexBasedCredentials", ClientCredentialsType.SCRAM, String.class).status())
                .isEqualTo(TbCacheOps.Status.ABSENT);
    }

    @Test
    void theSizeLimitAppliesToCaffeineCachesOnly() {
        for (int i = 0; i < 50; i++) {
            ops.put("mqttClientCredentials", "id-" + i, "v");
            ops.put("clientMqttVersion", "id-" + i, "5");
        }
        ((CaffeineCache) manager.getCache(PREFIX + "mqttClientCredentials")).getNativeCache().cleanUp();

        assertThat(((CaffeineCache) manager.getCache(PREFIX + "mqttClientCredentials")).getNativeCache().estimatedSize())
                .isLessThanOrEqualTo(5);
        assertThat(store.size()).isEqualTo(50);
    }

    @Test
    void sessionAttributesSurviveANewManagerOverTheSameStore() {
        ops.put("clientSessionCredentials", "c1", "creds");
        ops.put("clientMqttVersion", "c1", "5");
        ops.put("mqttClientCredentials", "c1", "creds");

        StandaloneCacheManager restarted = StandaloneCacheManager.create(properties, store, 5, ticker);
        TbCacheOps restartedOps = new TbCacheOps(new CacheNameResolver(restarted, properties));

        assertThat(restartedOps.lookup("clientSessionCredentials", "c1", String.class).value()).isEqualTo("creds");
        assertThat(restartedOps.lookup("clientMqttVersion", "c1", String.class).value()).isEqualTo("5");
        assertThat(restartedOps.lookup("mqttClientCredentials", "c1", String.class).status()).isEqualTo(TbCacheOps.Status.ABSENT);
    }

    @Test
    void aSpecWithoutATtlNeverExpires() {
        CacheProperties noTtl = properties(0);
        noTtl.getSpecs().put("sslRegexBasedCredentials", new CacheSpecs());
        StandaloneCacheManager m = StandaloneCacheManager.create(noTtl, store, 5, ticker);
        Cache ssl = m.getCache(PREFIX + "sslRegexBasedCredentials");
        ssl.put("k", "v");

        nanos.addAndGet(Duration.ofDays(3650).toNanos());

        assertThat(ssl.get("k")).isNotNull();
    }

    @Test
    void withStatsEnabledAHitIsCountedByTheNativeCache() {
        CacheProperties withStats = properties(0);
        withStats.setStats(stats(true));
        StandaloneCacheManager m = StandaloneCacheManager.create(withStats, store, 5, ticker);
        CaffeineCache cache = (CaffeineCache) m.getCache(PREFIX + "mqttClientCredentials");
        cache.put("k", "v");

        cache.get("k");

        assertThat(cache.getNativeCache().stats().hitCount()).isEqualTo(1);
    }

    @Test
    void withStatsUnsetOrDisabledTheNativeCacheRecordsNothing() {
        CacheProperties disabled = properties(0);
        disabled.setStats(stats(false));
        for (CacheProperties p : new CacheProperties[]{properties(0), disabled}) {
            StandaloneCacheManager m = StandaloneCacheManager.create(p, store, 5, ticker);
            CaffeineCache cache = (CaffeineCache) m.getCache(PREFIX + "mqttClientCredentials");
            cache.put("k", "v");

            cache.get("k");

            assertThat(cache.getNativeCache().stats().hitCount()).isZero();
        }
    }

    @Test
    void aMaxEntriesOfZeroOrLessIsRejected() {
        assertThatThrownBy(() -> StandaloneCacheManager.create(properties, store, 0, ticker))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("standalone.cache.max-entries")
                .hasMessageContaining("0");
        assertThatThrownBy(() -> StandaloneCacheManager.create(properties, store, -7, ticker))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("standalone.cache.max-entries")
                .hasMessageContaining("-7");
    }

    private static CacheStats stats(boolean enabled) {
        CacheStats stats = new CacheStats();
        stats.setEnabled(enabled);
        return stats;
    }

    private static CacheProperties properties(int sslTtl) {
        Map<String, CacheSpecs> specs = new LinkedHashMap<>();
        specs.put("mqttClientCredentials", spec(1440));
        specs.put("basicCredentialsPassword", spec(1));
        specs.put("sslRegexBasedCredentials", spec(sslTtl));
        specs.put("clientSessionCredentials", spec(0));
        specs.put("clientMqttVersion", spec(0));
        CacheProperties properties = new CacheProperties();
        properties.setCachePrefix(PREFIX);
        properties.setSpecs(specs);
        return properties;
    }

    private static CacheSpecs spec(int ttlMinutes) {
        CacheSpecs spec = new CacheSpecs();
        spec.setTimeToLiveInMinutes(ttlMinutes);
        return spec;
    }

}
