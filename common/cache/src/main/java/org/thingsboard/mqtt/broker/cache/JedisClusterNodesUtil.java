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
import org.springframework.data.redis.connection.RedisNode;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;

@Slf4j
public class JedisClusterNodesUtil {

    public static RedisNode parseClusterNodeLine(String line) {
        String[] parts = line.split(" ");
        if (parts.length < 8) {
            throw new IllegalArgumentException("Invalid cluster node line format: " + line);
        }
        try {
            // Node ID
            String id = parts[0];

            // Extract host and port from <ip:port@cport>
            String[] hostPort = parts[1].split(BrokerConstants.COLON);
            String host = hostPort[0];
            int port = Integer.parseInt(hostPort[1].split("@")[0]);

            // Flags to determine node type
            String flags = parts[2];
            RedisNode.NodeType type = flags.contains("master") ?
                    RedisNode.NodeType.MASTER : RedisNode.NodeType.REPLICA;

            RedisNode.RedisNodeBuilder redisNodeBuilder = RedisNode.newRedisNode()
                    .listeningAt(host, port)
                    .withId(id)
                    .promotedAs(type);

            String masterId = parts[3];
            boolean masterIdUnknown = "-".equals(masterId);
            if (masterIdUnknown) {
                return redisNodeBuilder.build();
            }
            return redisNodeBuilder.replicaOf(masterId).build();
        } catch (Exception e) {
            throw new RuntimeException("Error parsing cluster node line: " + line, e);
        }
    }

}
