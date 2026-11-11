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
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.test.util.ReflectionTestUtils;
import redis.clients.jedis.JedisClientConfig;

import javax.net.ssl.SSLSocketFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class TBRedisClusterConfigurationTest extends AbstractTBRedisConfigurationTest {

    private TBRedisClusterConfiguration newCluster(boolean sslEnabled, String username, String password) {
        TBRedisClusterConfiguration config = new TBRedisClusterConfiguration(null, lettuceConfig());
        setSslFields(config, sslEnabled);
        ReflectionTestUtils.setField(config, "clusterNodes", "localhost:6379");
        ReflectionTestUtils.setField(config, "maxRedirects", 12);
        ReflectionTestUtils.setField(config, "useDefaultPoolConfig", true);
        ReflectionTestUtils.setField(config, "username", username);
        ReflectionTestUtils.setField(config, "password", password);
        return config;
    }

    @Test
    void givenSslAndCredentials_whenBuildingDataNodeClientConfig_thenTlsAndCredentialsApplied() {
        TBRedisClusterConfiguration config = newCluster(true, "redis-user", "redis-pass");
        JedisClientConfig clientConfig = config.buildDataNodeClientConfig();
        assertTls(clientConfig);
        assertCredentials(clientConfig, "redis-user", "redis-pass");
    }

    @Test
    void givenSslDisabled_whenBuildingDataNodeClientConfig_thenNoTls() {
        TBRedisClusterConfiguration config = newCluster(false, "redis-user", "redis-pass");
        assertNoTls(config.buildDataNodeClientConfig());
    }

    @Test
    void givenNoCredentials_whenBuildingDataNodeClientConfig_thenUserNotSet() {
        TBRedisClusterConfiguration config = newCluster(true, "", "");
        JedisClientConfig clientConfig = config.buildDataNodeClientConfig();
        assertThat(clientConfig.getUser()).isNull();
        assertThat(clientConfig.getPassword()).isNull();
    }

    @Test
    void givenSslEnabled_whenBuildingRawAndSpringConfigs_thenShareSameSslSocketFactory() {
        TBRedisClusterConfiguration config = newCluster(true, "redis-user", "redis-pass");
        SSLSocketFactory rawFactory = config.buildDataNodeClientConfig().getSslSocketFactory();
        SSLSocketFactory springFactory = config.loadFactory().getClientConfiguration().getSslSocketFactory().orElseThrow();
        assertThat(rawFactory).isSameAs(springFactory);
    }

    @Test
    void givenSslEnabled_whenLoadFactory_thenJedisClientConfigurationUsesSsl() {
        TBRedisClusterConfiguration config = newCluster(true, "redis-user", "redis-pass");
        var clientConfiguration = config.loadFactory().getClientConfiguration();
        assertThat(clientConfiguration.isUseSsl()).isTrue();
        assertThat(clientConfiguration.getSslSocketFactory()).isPresent();
    }

    @Test
    void givenCredentials_whenGetRedisConfiguration_thenUsernameAndPasswordSet() {
        TBRedisClusterConfiguration config = newCluster(true, "redis-user", "redis-pass");
        RedisClusterConfiguration redisConfig = config.getRedisConfiguration();
        assertThat(redisConfig.getUsername()).isEqualTo("redis-user");
        assertThat(redisConfig.getPassword()).isEqualTo(RedisPassword.of("redis-pass"));
    }

    @Test
    void givenSslEnabled_whenLettuceConnectionFactory_thenUsesSsl() {
        assertLettuceUsesSsl(newCluster(true, "redis-user", "redis-pass").lettuceConnectionFactory());
    }
}
