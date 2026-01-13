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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisNode;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

// TODO: replace jedis from TBMQ implementation and use only lettuce.
@Slf4j
@Component
@Profile("!install")
@RequiredArgsConstructor
@ConditionalOnExpression("'cluster'.equals('${redis.connection.type}') and 'true'.equals('${jedis.cluster.topology-refresh.enabled}')")
public class JedisClusterTopologyRefresher {

    private final JedisConnectionFactory factory;

    @Scheduled(initialDelayString = "${jedis.cluster.topology-refresh.period}", fixedDelayString = "${jedis.cluster.topology-refresh.period}", timeUnit = TimeUnit.SECONDS)
    public void refreshTopology() {
        if (!factory.isRedisClusterAware()) {
            log.trace("Redis cluster configuration is not set!");
            return;
        }
        log.trace("Redis cluster refresh topology is starting!");
        try {
            RedisClusterConfiguration clusterConfig = factory.getClusterConfiguration();
            Set<RedisNode> currentNodes = clusterConfig.getClusterNodes();
            log.debug("Current Redis cluster nodes: {}", currentNodes);

            for (RedisNode node : currentNodes) {
                if (!node.hasValidHost()) {
                    log.debug("Skip Redis node with invalid host: {}", node);
                    continue;
                }
                if (node.getPort() == null) {
                    log.debug("Skip Redis node with null port: {}", node);
                    continue;
                }
                try (Jedis jedis = new Jedis(node.getHost(), node.getPort())) {
                    if (factory.getPassword() != null) {
                        jedis.auth(factory.getPassword());
                    }
                    Set<RedisNode> redisNodes = getRedisNodes(node, jedis);
                    if (currentNodes.equals(redisNodes)) {
                        log.debug("Redis cluster topology is up to date!");
                        break;
                    }
                    clusterConfig.setClusterNodes(redisNodes);
                    log.debug("Successfully updated Redis cluster topology, nodes: {}", redisNodes);
                    break;
                } catch (Exception e) {
                    log.warn("Failed to refresh cluster topology using node: {}", node.getHost(), e);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to refresh cluster topology", e);
        }
        log.trace("Redis cluster refresh topology has finished!");
    }

    private Set<RedisNode> getRedisNodes(RedisNode node, Jedis jedis) {
        String clusterNodes = jedis.clusterNodes();
        log.trace("Caller Redis node: {}:{} CLUSTER NODES output:{}{}", node.getHost(), node.getPort(), System.lineSeparator(), clusterNodes);
        // Split the clusterNodes string into individual lines and parse each line
        // Each line is composed of the following fields:
        // <id> <ip:port@cport[,hostname]> <flags> <master> <ping-sent> <pong-recv> <config-epoch> <link-state> <slot> <slot> ... <slot>
        return Arrays.stream(clusterNodes.split(System.lineSeparator()))
                .map(JedisClusterNodesUtil::parseClusterNodeLine)
                .collect(Collectors.toSet());
    }

}
