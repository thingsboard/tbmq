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
package org.thingsboard.mqtt.broker.service.mqtt.client.blocked;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.BlockedClient;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.ClientIdBlockedClient;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.IpAddressBlockedClient;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.RegexBlockedClient;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.RegexMatchTarget;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.UsernameBlockedClient;

import static org.assertj.core.api.Assertions.assertThat;

class BlockedClientServiceImplTest {


    @Test
    public void test1() {

        ObjectNode objectNode = JacksonUtil.newObjectNode();
        objectNode.put("type", "clientId");
        objectNode.put("clientId", "asd");
        objectNode.put("expirationTime", System.currentTimeMillis());
        objectNode.put("description", "1 2 3 4 5 6 7 8 9");

        BlockedClient blockedClient = JacksonUtil.convertValue(objectNode, BlockedClient.class);
        assertThat(blockedClient instanceof ClientIdBlockedClient).isTrue();

    }

    @Test
    public void test2() {

        ObjectNode objectNode = JacksonUtil.newObjectNode();
        objectNode.put("type", "ipAddress");
        objectNode.put("ipAddress", "one-two-three");
        objectNode.put("expirationTime", System.currentTimeMillis());
        objectNode.put("description", "1 2 3 4 5 6 7 8 9");

        BlockedClient blockedClient = JacksonUtil.convertValue(objectNode, BlockedClient.class);
        assertThat(blockedClient instanceof IpAddressBlockedClient).isTrue();

    }

    @Test
    public void test3() {

        ObjectNode objectNode = JacksonUtil.newObjectNode();
        objectNode.put("type", "username");
        objectNode.put("username", "un");
        objectNode.put("expirationTime", System.currentTimeMillis());
        objectNode.put("description", "1 2 3 4 5 6 7 8 9");

        BlockedClient blockedClient = JacksonUtil.convertValue(objectNode, BlockedClient.class);
        assertThat(blockedClient instanceof UsernameBlockedClient).isTrue();

    }

    @Test
    public void test4() {

        RegexBlockedClient regexBlockedClient1 = new RegexBlockedClient(1, "asd", "one-two-three", RegexMatchTarget.BY_CLIENT_ID);
        RegexBlockedClient regexBlockedClient2 = new RegexBlockedClient(2, "asd123", "one-two-three", RegexMatchTarget.BY_USERNAME);

        assertThat(regexBlockedClient1).isEqualTo(regexBlockedClient2);
    }


}
