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
package org.thingsboard.mqtt.broker.cache;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {CacheSpecsMap.class, TBRedisCacheConfiguration.class, TBRedisStandaloneConfiguration.class})
@EnableConfigurationProperties
@TestPropertySource(properties = {
        "redis.connection.type=standalone",
        "cache.specs.mqttClientCredentials.timeToLiveInMinutes=1440"
})
@Slf4j
public class TbRedisCacheConfigurationTest {

    @Autowired
    CacheManager cacheManager;

    @Test
    public void verifyRedisCacheManager() {
        assertThat(cacheManager).isInstanceOf(RedisCacheManager.class);
    }

    @Test
    public void givenCacheConfig_whenCacheManagerReady_thenVerifyExistedCachesWithTransactionAwareCacheDecorator() {
        Cache mqttClientCredentialsCache = cacheManager.getCache("mqttClientCredentials");
        assertThat(mqttClientCredentialsCache != null).isEqualTo(true);
        assertThat(mqttClientCredentialsCache).isInstanceOf(TransactionAwareCacheDecorator.class);
    }

    @Test
    public void givenCacheConfig_whenCacheManagerReady_thenVerifyNonExistedCaches() {
        assertThat(cacheManager.getCache("rainbows_and_unicorns")).isNull();
    }
}
