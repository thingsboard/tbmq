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
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.util.ReflectionTestUtils;
import redis.clients.jedis.JedisClientConfig;

import javax.net.ssl.SSLSocketFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class TBRedisStandaloneConfigurationTest extends AbstractTBRedisConfigurationTest {

    private TBRedisStandaloneConfiguration newStandalone(boolean sslEnabled, String username, String password) {
        TBRedisStandaloneConfiguration config = new TBRedisStandaloneConfiguration(null, lettuceConfig());
        setSslFields(config, sslEnabled);
        ReflectionTestUtils.setField(config, "host", "localhost");
        ReflectionTestUtils.setField(config, "port", 6379);
        ReflectionTestUtils.setField(config, "db", 0);
        ReflectionTestUtils.setField(config, "username", username);
        ReflectionTestUtils.setField(config, "password", password);
        ReflectionTestUtils.setField(config, "useDefaultClientConfig", true);
        ReflectionTestUtils.setField(config, "usePoolConfig", false);
        ReflectionTestUtils.setField(config, "clientName", "standalone");
        ReflectionTestUtils.setField(config, "connectTimeout", 30000L);
        ReflectionTestUtils.setField(config, "readTimeout", 60000L);
        return config;
    }

    @Test
    void givenSslEnabled_whenLoadFactoryCalledTwice_thenSameSslSocketFactoryReused() {
        TBRedisStandaloneConfiguration config = newStandalone(true, "redis-user", "redis-pass");
        SSLSocketFactory first = config.loadFactory().getClientConfiguration().getSslSocketFactory().orElseThrow();
        SSLSocketFactory second = config.loadFactory().getClientConfiguration().getSslSocketFactory().orElseThrow();
        assertThat(first).isSameAs(second);
    }

    @Test
    void givenSslAndCredentials_whenBuildingDataNodeClientConfig_thenTlsAndCredentialsApplied() {
        TBRedisStandaloneConfiguration config = newStandalone(true, "redis-user", "redis-pass");
        JedisClientConfig clientConfig = config.buildDataNodeClientConfig();
        assertTls(clientConfig);
        assertCredentials(clientConfig, "redis-user", "redis-pass");
    }

    @Test
    void givenSslEnabled_whenBuildingRawAndSpringConfigs_thenShareSameSslSocketFactory() {
        TBRedisStandaloneConfiguration config = newStandalone(true, "redis-user", "redis-pass");
        SSLSocketFactory rawFactory = config.buildDataNodeClientConfig().getSslSocketFactory();
        SSLSocketFactory springFactory = config.loadFactory().getClientConfiguration().getSslSocketFactory().orElseThrow();
        assertThat(rawFactory).isSameAs(springFactory);
    }

    @Test
    void givenSslDisabled_whenBuildingDataNodeClientConfig_thenNoTls() {
        TBRedisStandaloneConfiguration config = newStandalone(false, "redis-user", "redis-pass");
        assertNoTls(config.buildDataNodeClientConfig());
    }

    @Test
    void givenNoCredentials_whenBuildingDataNodeClientConfig_thenUserNotSet() {
        TBRedisStandaloneConfiguration config = newStandalone(true, "", "");
        JedisClientConfig clientConfig = config.buildDataNodeClientConfig();
        assertThat(clientConfig.getUser()).isNull();
        assertThat(clientConfig.getPassword()).isNull();
    }

    @Test
    void givenNonDefaultClientConfig_whenBuildingDataNodeClientConfig_thenClientNameAppliedWithTls() {
        TBRedisStandaloneConfiguration config = newStandalone(true, "redis-user", "redis-pass");
        ReflectionTestUtils.setField(config, "useDefaultClientConfig", false);
        JedisClientConfig clientConfig = config.buildDataNodeClientConfig();
        assertThat(clientConfig.getClientName()).isEqualTo("standalone");
        assertThat(clientConfig.getSocketTimeoutMillis()).isEqualTo(60000);
        assertThat(clientConfig.getConnectionTimeoutMillis()).isEqualTo(30000);
        assertTls(clientConfig);
    }

    @Test
    void givenSslEnabled_whenLoadFactory_thenJedisClientConfigurationUsesSsl() {
        TBRedisStandaloneConfiguration config = newStandalone(true, "redis-user", "redis-pass");
        var clientConfiguration = config.loadFactory().getClientConfiguration();
        assertThat(clientConfiguration.isUseSsl()).isTrue();
        assertThat(clientConfiguration.getSslSocketFactory()).isPresent();
    }

    @Test
    void givenSslDisabled_whenLoadFactory_thenJedisClientConfigurationNoSsl() {
        TBRedisStandaloneConfiguration config = newStandalone(false, "", "");
        assertThat(config.loadFactory().getClientConfiguration().isUseSsl()).isFalse();
    }

    @Test
    void givenCredentials_whenGetRedisConfiguration_thenUsernameAndPasswordSet() {
        TBRedisStandaloneConfiguration config = newStandalone(true, "redis-user", "redis-pass");
        RedisStandaloneConfiguration redisConfig = config.getRedisConfiguration();
        assertThat(redisConfig.getUsername()).isEqualTo("redis-user");
        assertThat(redisConfig.getPassword()).isEqualTo(RedisPassword.of("redis-pass"));
    }

    @Test
    void givenSslEnabled_whenLettuceConnectionFactory_thenUsesSslWithOptions() {
        TBRedisStandaloneConfiguration config = newStandalone(true, "redis-user", "redis-pass");
        LettuceConnectionFactory factory = config.lettuceConnectionFactory();
        assertThat(factory.getClientConfiguration().isUseSsl()).isTrue();
        assertThat(factory.getClientConfiguration().getClientOptions()).isPresent();
        assertThat(factory.getClientConfiguration().getClientOptions().get().getSslOptions()).isNotNull();
        assertThat(factory.getClientConfiguration().getClientOptions().get().getSslOptions().getSslProvider())
                .isEqualTo(io.netty.handler.ssl.SslProvider.JDK);
    }
}
