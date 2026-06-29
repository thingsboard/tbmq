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
import org.mockito.MockedConstruction;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisNode;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.test.util.ReflectionTestUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisClientConfig;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

/**
 * Regression guard: the Jedis cluster topology refresher must open its per-node probe connection
 * using the same data-node client config as the cache (TLS + ACL username/password). Previously it
 * used a raw {@code new Jedis(host, port)} + password-only {@code auth()}, which cannot reach a
 * TLS-only node and ignores the ACL username.
 */
class JedisClusterTopologyRefresherTest extends AbstractTBRedisConfigurationTest {

    private static final String CLUSTER_NODES_LINE = "id1 127.0.0.1:6379@16379 master - 0 0 1 connected 0-16383";

    private TBRedisClusterConfiguration clusterConfiguration(boolean sslEnabled) {
        TBRedisClusterConfiguration config = new TBRedisClusterConfiguration(null, lettuceConfig());
        setSslFields(config, sslEnabled);
        ReflectionTestUtils.setField(config, "clusterNodes", "127.0.0.1:6379");
        ReflectionTestUtils.setField(config, "maxRedirects", 12);
        ReflectionTestUtils.setField(config, "useDefaultPoolConfig", true);
        ReflectionTestUtils.setField(config, "username", "tbmq-user");
        ReflectionTestUtils.setField(config, "password", "tbmq-pass");
        return config;
    }

    private JedisConnectionFactory clusterAwareFactory() {
        JedisConnectionFactory factory = mock(JedisConnectionFactory.class);
        when(factory.isRedisClusterAware()).thenReturn(true);
        RedisClusterConfiguration nodes = new RedisClusterConfiguration();
        nodes.addClusterNode(new RedisNode("127.0.0.1", 6379));
        when(factory.getClusterConfiguration()).thenReturn(nodes);
        return factory;
    }

    @Test
    void givenSslEnabled_whenRefreshTopology_thenProbeConnectionUsesTlsAndAclUser() {
        JedisClusterTopologyRefresher refresher =
                new JedisClusterTopologyRefresher(clusterAwareFactory(), clusterConfiguration(true));

        List<List<?>> constructorArgs = new ArrayList<>();
        try (MockedConstruction<Jedis> mocked = mockConstruction(Jedis.class, (jedis, context) -> {
            constructorArgs.add(context.arguments());
            when(jedis.clusterNodes()).thenReturn(CLUSTER_NODES_LINE);
        })) {
            refresher.refreshTopology();
        }

        assertThat(constructorArgs).hasSize(1);
        Object clientConfigArg = constructorArgs.get(0).get(1);
        assertThat(clientConfigArg).isInstanceOf(JedisClientConfig.class);
        JedisClientConfig clientConfig = (JedisClientConfig) clientConfigArg;
        assertThat(clientConfig.isSsl()).isTrue();
        assertThat(clientConfig.getUser()).isEqualTo("tbmq-user");
    }
}
