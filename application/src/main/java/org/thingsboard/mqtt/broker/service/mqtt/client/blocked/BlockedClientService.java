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

import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.BlockedClient;

import java.util.List;
import java.util.Map;

public interface BlockedClientService {

    void init(Map<String, BlockedClient> blockedClientMap);

    void startListening(BlockedClientConsumerService blockedClientConsumerService);

    void addBlockedClientAndPersist(String topic, BlockedClient blockedClient);

    void addBlockedClientAndPersist(String topic, BlockedClient blockedClient, BasicCallback callback);

    void addBlockedClient(String topic, BlockedClient blockedClient);

    void removeBlockedClientAndPersist(String topic);

    void removeBlockedClientAndPersist(String topic, BasicCallback callback);

    void removeBlockedClient(String topic);

    List<BlockedClient> getBlockedClients();

    List<BlockedClient> getRegexBlockedClients();

    boolean isBlocked(String clientId, String username, String ipAddress);
}
