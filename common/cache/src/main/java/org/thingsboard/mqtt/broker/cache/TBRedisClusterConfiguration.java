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

import io.lettuce.core.ClientOptions;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import redis.clients.jedis.ConnectionPoolConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.DefaultJedisClientConfig.Builder;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.UnifiedJedis;

import java.time.Duration;

@Configuration
@ConditionalOnProperty(prefix = "redis.connection", value = "type", havingValue = "cluster")
public class TBRedisClusterConfiguration extends TBRedisCacheConfiguration<RedisClusterConfiguration> {

    public TBRedisClusterConfiguration(CacheProperties cacheProperties, LettuceConfig lettuceConfig) {
        super(cacheProperties, lettuceConfig);
    }

    @Value("${redis.cluster.nodes:}")
    private String clusterNodes;

    @Value("${redis.cluster.maxRedirects:12}")
    private int maxRedirects;

    @Value("${redis.cluster.useDefaultPoolConfig:true}")
    private boolean useDefaultPoolConfig;

    @Value("${redis.username:}")
    private String username;

    @Value("${redis.password:}")
    private String password;

    @Override
    protected UnifiedJedis loadUnifiedJedis() {
        Builder clientConfigBuilder = DefaultJedisClientConfig.builder();
        if (StringUtils.isNotEmpty(username)) {
            clientConfigBuilder.user(username);
        }
        if (StringUtils.isNotEmpty(password)) {
            clientConfigBuilder.password(password);
        }
        if (sslEnabled) {
            clientConfigBuilder.ssl(true).sslSocketFactory(createSslSocketFactory());
        }
        ConnectionPoolConfig poolConfig = useDefaultPoolConfig ? new ConnectionPoolConfig() : buildConnectionPoolConfig();
        return new JedisCluster(toHostAndPort(clusterNodes), clientConfigBuilder.build(), JedisCluster.DEFAULT_MAX_ATTEMPTS, poolConfig);
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
    protected RedisClusterConfiguration getRedisConfiguration() {
        var clusterConfiguration = new RedisClusterConfiguration();
        clusterConfiguration.setClusterNodes(getNodes(clusterNodes));
        clusterConfiguration.setMaxRedirects(maxRedirects);
        clusterConfiguration.setUsername(username);
        clusterConfiguration.setPassword(password);
        return clusterConfiguration;
    }

    @Override
    protected ClientOptions getLettuceClientOptions() {
        ClusterTopologyRefreshOptions topologyRefreshOptions = ClusterTopologyRefreshOptions.builder()
                .enablePeriodicRefresh(lettuceConfig.getCluster().getTopologyRefresh().isEnabled())
                .refreshPeriod(Duration.ofSeconds(lettuceConfig.getCluster().getTopologyRefresh().getPeriod()))
                .enableAllAdaptiveRefreshTriggers()
                .build();

        ClusterClientOptions.Builder builder = ClusterClientOptions
                .builder()
                .timeoutOptions(TimeoutOptions.enabled())
                .topologyRefreshOptions(topologyRefreshOptions);
        if (sslEnabled) {
            builder.sslOptions(createLettuceSslOptions());
        }
        return builder.build();
    }

}
