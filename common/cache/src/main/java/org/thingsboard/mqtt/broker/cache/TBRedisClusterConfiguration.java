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

import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import redis.clients.jedis.ConnectionPoolConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.UnifiedJedis;

import java.util.concurrent.atomic.AtomicBoolean;

@Configuration
@ConditionalOnProperty(prefix = "redis.connection", value = "type", havingValue = "cluster")
public class TBRedisClusterConfiguration extends TBRedisCacheConfiguration<RedisClusterConfiguration> {

    public TBRedisClusterConfiguration(CacheSpecsMap cacheSpecsMap) {
        super(cacheSpecsMap);
    }

    @Value("${redis.cluster.nodes:}")
    private String clusterNodes;

    @Value("${redis.cluster.maxRedirects:12}")
    private int maxRedirects;

    @Value("${redis.cluster.useDefaultPoolConfig:true}")
    private boolean useDefaultPoolConfig;

    @Value("${redis.password:}")
    private String password;

    @Value("${lettuce.auto-flush:true}")
    private boolean autoFlush;

    @Bean(destroyMethod = "") // The destroy method is set to nothing on purpose due to the presence of addShutdownHook
    public StatefulRedisClusterConnection<String, String> statefulRedisClusterConnection(LettuceConnectionFactory lettuceConnectionFactory) {
        RedisClusterClient client = (RedisClusterClient) lettuceConnectionFactory.getNativeClient();

        if (client == null) {
            throw new IllegalStateException("Failed to initiate Redis lettuce cluster client!");
        }

        StatefulRedisClusterConnection<String, String> connection = client.connect();
        connection.setAutoFlushCommands(autoFlush);

        AtomicBoolean isClosed = new AtomicBoolean(false);

        // Add a custom shutdown hook to ensure single closure
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (isClosed.compareAndSet(false, true) && connection.isOpen()) {
                connection.close();
            }
        }));

        return connection;
    }

    @Override
    protected UnifiedJedis loadUnifiedJedis() {
        DefaultJedisClientConfig jedisClientConfig = DefaultJedisClientConfig.builder().password(password).build();
        ConnectionPoolConfig poolConfig = useDefaultPoolConfig ? new ConnectionPoolConfig() : buildConnectionPoolConfig();
        return new JedisCluster(toHostAndPort(clusterNodes), jedisClientConfig, JedisCluster.DEFAULT_MAX_ATTEMPTS, poolConfig);
    }

    @Override
    protected JedisConnectionFactory loadFactory() {
        return useDefaultPoolConfig ?
                new JedisConnectionFactory(getRedisConfiguration()) :
                new JedisConnectionFactory(getRedisConfiguration(), buildPoolConfig());
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
        clusterConfiguration.setPassword(password);
        return clusterConfiguration;
    }

}
