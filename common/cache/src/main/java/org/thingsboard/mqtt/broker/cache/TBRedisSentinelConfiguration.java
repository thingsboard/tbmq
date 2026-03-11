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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import redis.clients.jedis.ConnectionPoolConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.DefaultJedisClientConfig.Builder;
import redis.clients.jedis.JedisSentineled;
import redis.clients.jedis.UnifiedJedis;

@Configuration
@ConditionalOnProperty(prefix = "redis.connection", value = "type", havingValue = "sentinel")
public class TBRedisSentinelConfiguration extends TBRedisCacheConfiguration<RedisSentinelConfiguration> {

    public TBRedisSentinelConfiguration(CacheProperties cacheProperties, LettuceConfig lettuceConfig) {
        super(cacheProperties, lettuceConfig);
    }

    @Value("${redis.sentinel.master:}")
    private String master;

    @Value("${redis.sentinel.sentinels:}")
    private String sentinels;

    @Value("${redis.sentinel.password:}")
    private String sentinelPassword;

    @Value("${redis.sentinel.useDefaultPoolConfig:true}")
    private boolean useDefaultPoolConfig;

    @Value("${redis.db:0}")
    private int database;

    @Value("${redis.username:}")
    private String username;

    @Value("${redis.password:}")
    private String password;

    @Override
    protected UnifiedJedis loadUnifiedJedis() {
        Builder masterClientConfigBuilder = DefaultJedisClientConfig.builder().database(database);
        if (StringUtils.isNotEmpty(username)) {
            masterClientConfigBuilder.user(username);
        }
        if (StringUtils.isNotEmpty(password)) {
            masterClientConfigBuilder.password(password);
        }
        if (sslEnabled) {
            masterClientConfigBuilder.ssl(true).sslSocketFactory(createSslSocketFactory());
        }
        Builder sentinelClientConfigBuilder = DefaultJedisClientConfig.builder();
        if (StringUtils.isNotEmpty(sentinelPassword)) {
            sentinelClientConfigBuilder.password(sentinelPassword);
        }
        ConnectionPoolConfig connectionPoolConfig = useDefaultPoolConfig ? new ConnectionPoolConfig() : buildConnectionPoolConfig();
        return new JedisSentineled(master, masterClientConfigBuilder.build(), connectionPoolConfig, toHostAndPort(sentinels), sentinelClientConfigBuilder.build());
    }

    @Override
    protected JedisConnectionFactory loadFactory() {
        return new JedisConnectionFactory(getRedisConfiguration(), buildClientConfig());
    }

    private JedisClientConfiguration buildClientConfig() {
        var builder = JedisClientConfiguration.builder();
        if (!useDefaultPoolConfig) {
            builder.usePooling().poolConfig(buildPoolConfig());
        }
        if (sslEnabled) {
            builder.useSsl().sslSocketFactory(createSslSocketFactory());
        }
        return builder.build();
    }

    @Override
    protected boolean useDefaultPoolConfig() {
        return useDefaultPoolConfig;
    }

    @Override
    protected RedisSentinelConfiguration getRedisConfiguration() {
        var redisSentinelConfiguration = new RedisSentinelConfiguration();
        redisSentinelConfiguration.setMaster(master);
        redisSentinelConfiguration.setSentinels(getNodes(sentinels));
        redisSentinelConfiguration.setSentinelPassword(sentinelPassword);
        redisSentinelConfiguration.setUsername(username);
        redisSentinelConfiguration.setPassword(password);
        redisSentinelConfiguration.setDatabase(database);
        return redisSentinelConfiguration;
    }

}
