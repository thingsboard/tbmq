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
package org.thingsboard.mqtt.broker.cache;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import redis.clients.jedis.JedisClientConfig;

import javax.net.ssl.SSLSocketFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class TBRedisSentinelConfigurationTest extends AbstractTBRedisConfigurationTest {

    private TBRedisSentinelConfiguration newSentinel(boolean sslEnabled, String username, String password,
                                                     String sentinelUser, String sentinelPass) {
        TBRedisSentinelConfiguration config = new TBRedisSentinelConfiguration(null, lettuceConfig());
        setSslFields(config, sslEnabled);
        ReflectionTestUtils.setField(config, "master", "mymaster");
        ReflectionTestUtils.setField(config, "sentinels", "localhost:26379");
        ReflectionTestUtils.setField(config, "sentinelUsername", sentinelUser);
        ReflectionTestUtils.setField(config, "sentinelPassword", sentinelPass);
        ReflectionTestUtils.setField(config, "useDefaultPoolConfig", true);
        ReflectionTestUtils.setField(config, "database", 0);
        ReflectionTestUtils.setField(config, "username", username);
        ReflectionTestUtils.setField(config, "password", password);
        return config;
    }

    @Test
    void givenSslAndCredentials_whenBuildingDataNodeClientConfig_thenTlsAndCredentialsApplied() {
        TBRedisSentinelConfiguration config = newSentinel(true, "redis-user", "redis-pass", "sentinel-user", "sentinel-pass");
        JedisClientConfig clientConfig = config.buildDataNodeClientConfig();
        assertTls(clientConfig);
        assertCredentials(clientConfig, "redis-user", "redis-pass");
    }

    @Test
    void givenSslEnabled_whenBuildingSentinelClientConfig_thenTlsAndSentinelCredentialsApplied() {
        TBRedisSentinelConfiguration config = newSentinel(true, "redis-user", "redis-pass", "sentinel-user", "sentinel-pass");
        JedisClientConfig sentinelConfig = config.buildSentinelClientConfig();
        assertTls(sentinelConfig);
        assertCredentials(sentinelConfig, "sentinel-user", "sentinel-pass");
    }

    @Test
    void givenSslDisabled_whenBuildingConfigs_thenNoTls() {
        TBRedisSentinelConfiguration config = newSentinel(false, "redis-user", "redis-pass", "sentinel-user", "sentinel-pass");
        assertNoTls(config.buildDataNodeClientConfig());
        assertNoTls(config.buildSentinelClientConfig());
    }

    @Test
    void givenNoCredentials_whenBuildingConfigs_thenUserNotSet() {
        TBRedisSentinelConfiguration config = newSentinel(true, "", "", "", "");
        JedisClientConfig dataNodeConfig = config.buildDataNodeClientConfig();
        assertThat(dataNodeConfig.getUser()).isNull();
        assertThat(dataNodeConfig.getPassword()).isNull();
        JedisClientConfig sentinelConfig = config.buildSentinelClientConfig();
        assertThat(sentinelConfig.getUser()).isNull();
        assertThat(sentinelConfig.getPassword()).isNull();
    }

    @Test
    void givenSslEnabled_whenBuildingAllJedisConfigs_thenShareSameSslSocketFactory() {
        TBRedisSentinelConfiguration config = newSentinel(true, "redis-user", "redis-pass", "sentinel-user", "sentinel-pass");
        SSLSocketFactory master = config.buildDataNodeClientConfig().getSslSocketFactory();
        SSLSocketFactory sentinel = config.buildSentinelClientConfig().getSslSocketFactory();
        SSLSocketFactory spring = config.loadFactory().getClientConfiguration().getSslSocketFactory().orElseThrow();
        assertThat(master).isSameAs(sentinel).isSameAs(spring);
    }

    @Test
    void givenSslEnabled_whenLoadFactory_thenJedisClientConfigurationUsesSsl() {
        TBRedisSentinelConfiguration config = newSentinel(true, "redis-user", "redis-pass", "sentinel-user", "sentinel-pass");
        var clientConfiguration = config.loadFactory().getClientConfiguration();
        assertThat(clientConfiguration.isUseSsl()).isTrue();
        assertThat(clientConfiguration.getSslSocketFactory()).isPresent();
    }

    @Test
    void givenCredentials_whenGetRedisConfiguration_thenAllCredentialsSet() {
        TBRedisSentinelConfiguration config = newSentinel(true, "redis-user", "redis-pass", "sentinel-user", "sentinel-pass");
        RedisSentinelConfiguration redisConfig = config.getRedisConfiguration();
        assertThat(redisConfig.getUsername()).isEqualTo("redis-user");
        assertThat(redisConfig.getPassword()).isEqualTo(RedisPassword.of("redis-pass"));
        assertThat(redisConfig.getSentinelUsername()).isEqualTo("sentinel-user");
        assertThat(redisConfig.getSentinelPassword()).isEqualTo(RedisPassword.of("sentinel-pass"));
    }

    @Test
    void givenSslEnabled_whenLettuceConnectionFactory_thenUsesSsl() {
        assertLettuceUsesSsl(newSentinel(true, "redis-user", "redis-pass", "sentinel-user", "sentinel-pass").lettuceConnectionFactory());
    }
}
