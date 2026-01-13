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
import org.springframework.data.redis.connection.RedisNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatException;

class JedisClusterNodesUtilTest {

    @Test
    void testParseClusterNodeLineMasterNode() {
        String line = "e91061e872bf29860c974fe719865a022f3ef7be 172.21.0.10:6379@16379 master - 0 1732720493433 1 connected 0-5460";

        RedisNode redisNode = JedisClusterNodesUtil.parseClusterNodeLine(line);

        assertThat(redisNode).isNotNull();
        assertThat(redisNode.getId()).isEqualTo("e91061e872bf29860c974fe719865a022f3ef7be");
        assertThat(redisNode.getHost()).isEqualTo("172.21.0.10");
        assertThat(redisNode.getPort()).isEqualTo(6379);
        assertThat(redisNode.isMaster()).isTrue();
        assertThat(redisNode.getMasterId()).isNull();
    }

    @Test
    void testParseClusterNodeLineReplicaNode() {
        String line = "d704a7a52d73ee3f969728b9cc7eae61f06f992f 172.21.0.6:6379@16379 slave e91061e872bf29860c974fe719865a022f3ef7be 0 1732720492429 1 connected";

        RedisNode redisNode = JedisClusterNodesUtil.parseClusterNodeLine(line);

        assertThat(redisNode).isNotNull();
        assertThat(redisNode.getId()).isEqualTo("d704a7a52d73ee3f969728b9cc7eae61f06f992f");
        assertThat(redisNode.getHost()).isEqualTo("172.21.0.6");
        assertThat(redisNode.getPort()).isEqualTo(6379);
        assertThat(redisNode.isReplica()).isTrue();
        assertThat(redisNode.getMasterId()).isNotNull().isEqualTo("e91061e872bf29860c974fe719865a022f3ef7be");
    }

    @Test
    void testParseClusterNodeLineWithHostname() {
        String line = "07c37dfeb235213a872192d90877d0cd55635b91 127.0.0.1:30004@31004,hostname4 slave e7d1eecce10fd6bb5eb35b9f99a514335d9ba9ca 0 1426238317239 4 connected";

        RedisNode redisNode = JedisClusterNodesUtil.parseClusterNodeLine(line);

        assertThat(redisNode).isNotNull();
        assertThat(redisNode.getId()).isEqualTo("07c37dfeb235213a872192d90877d0cd55635b91");
        assertThat(redisNode.getHost()).isEqualTo("127.0.0.1");
        assertThat(redisNode.getPort()).isEqualTo(30004);
        assertThat(redisNode.isReplica()).isTrue();
        assertThat(redisNode.getMasterId()).isNotNull().isEqualTo("e7d1eecce10fd6bb5eb35b9f99a514335d9ba9ca");
    }

    @Test
    void testParseClusterNodeLineWithSlots() {
        // Arrange
        String line = "67ed2db8d677e59ec4a4cefb06858cf2a1a89fa1 127.0.0.1:30002@31002 master - 0 1426238316232 2 connected 5461-10922";

        // Act
        RedisNode redisNode = JedisClusterNodesUtil.parseClusterNodeLine(line);

        // Assert
        assertThat(redisNode).isNotNull();
        assertThat(redisNode.getId()).isEqualTo("67ed2db8d677e59ec4a4cefb06858cf2a1a89fa1");
        assertThat(redisNode.getHost()).isEqualTo("127.0.0.1");
        assertThat(redisNode.getPort()).isEqualTo(30002);
        assertThat(redisNode.isMaster()).isTrue();
        assertThat(redisNode.getMasterId()).isNull();
    }

    @Test
    void testParseClusterNodeLineWithMultipleFlags() {
        // Arrange
        String line = "e7d1eecce10fd6bb5eb35b9f99a514335d9ba9ca 127.0.0.1:30001@31001,hostname1 myself,master - 0 0 1 connected 0-5460";

        // Act
        RedisNode redisNode = JedisClusterNodesUtil.parseClusterNodeLine(line);

        // Assert
        assertThat(redisNode).isNotNull();
        assertThat(redisNode.getId()).isEqualTo("e7d1eecce10fd6bb5eb35b9f99a514335d9ba9ca");
        assertThat(redisNode.getHost()).isEqualTo("127.0.0.1");
        assertThat(redisNode.getPort()).isEqualTo(30001);
        assertThat(redisNode.isMaster()).isTrue();
        assertThat(redisNode.getMasterId()).isNull();
    }

    @Test
    void testParseClusterNodeLineIncompleteFields() {
        String line = "e91061e872bf29860c974fe719865a022f3ef7be 172.21.0.10:6379@16379 master -";

        assertThatException().isThrownBy(() -> JedisClusterNodesUtil.parseClusterNodeLine(line))
                .isInstanceOf(IllegalArgumentException.class).withMessage("Invalid cluster node line format: " + line);
    }
}
