/**
 * Copyright © 2016-2025 The Thingsboard Authors
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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import redis.clients.jedis.ConnectionPoolConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.DefaultJedisClientConfig.Builder;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.UnifiedJedis;

import java.time.Duration;

@Configuration
@ConditionalOnProperty(prefix = "redis.connection", value = "type", havingValue = "standalone")
public class TBRedisStandaloneConfiguration extends TBRedisCacheConfiguration<RedisStandaloneConfiguration> {

    public TBRedisStandaloneConfiguration(CacheProperties cacheProperties, LettuceConfig lettuceConfig) {
        super(cacheProperties, lettuceConfig);
    }

    @Value("${redis.standalone.host:localhost}")
    private String host;

    @Value("${redis.standalone.port:6379}")
    private int port;

    @Value("${redis.standalone.clientName:standalone}")
    private String clientName;

    @Value("${redis.standalone.connectTimeout:30000}")
    private long connectTimeout;

    @Value("${redis.standalone.readTimeout:60000}")
    private long readTimeout;

    @Value("${redis.standalone.useDefaultClientConfig:true}")
    private boolean useDefaultClientConfig;

    @Value("${redis.standalone.usePoolConfig:false}")
    private boolean usePoolConfig;

    @Value("${redis.db:0}")
    private int db;

    @Value("${redis.password:}")
    private String password;

    @Override
    protected UnifiedJedis loadUnifiedJedis() {
        Builder clientConfigBuilder = DefaultJedisClientConfig.builder().database(db);
        if (StringUtils.isNotEmpty(password)) {
            clientConfigBuilder.password(password);
        }
        if (useDefaultClientConfig) {
            return new JedisPooled(new HostAndPort(host, port), clientConfigBuilder.build());
        } else {
            ConnectionPoolConfig poolConfig = usePoolConfig ? buildConnectionPoolConfig() : new ConnectionPoolConfig();
            clientConfigBuilder.clientName(clientName).socketTimeoutMillis((int) readTimeout).connectionTimeoutMillis((int) connectTimeout);
            return new JedisPooled(poolConfig, new HostAndPort(host, port), clientConfigBuilder.build());
        }
    }

    @Override
    protected JedisConnectionFactory loadFactory() {
        return useDefaultClientConfig ?
                new JedisConnectionFactory(getRedisConfiguration()) :
                new JedisConnectionFactory(getRedisConfiguration(), buildClientConfig());
    }

    @Override
    protected boolean useDefaultPoolConfig() {
        return useDefaultClientConfig;
    }

    @Override
    protected RedisStandaloneConfiguration getRedisConfiguration() {
        var standaloneConfiguration = new RedisStandaloneConfiguration();
        standaloneConfiguration.setHostName(host);
        standaloneConfiguration.setPort(port);
        standaloneConfiguration.setDatabase(db);
        standaloneConfiguration.setPassword(password);
        return standaloneConfiguration;
    }

    private JedisClientConfiguration buildClientConfig() {
        if (usePoolConfig) {
            return JedisClientConfiguration.builder()
                    .clientName(clientName)
                    .connectTimeout(Duration.ofMillis(connectTimeout))
                    .readTimeout(Duration.ofMillis(readTimeout))
                    .usePooling().poolConfig(buildPoolConfig())
                    .build();
        } else {
            return JedisClientConfiguration.builder()
                    .clientName(clientName)
                    .connectTimeout(Duration.ofMillis(connectTimeout))
                    .readTimeout(Duration.ofMillis(readTimeout)).build();
        }
    }
}
