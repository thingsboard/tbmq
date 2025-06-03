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

import org.thingsboard.mqtt.broker.dto.BlockedClientDto;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.consumer.BlockedClientConsumerService;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.BlockedClient;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.BlockedClientResult;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.BlockedClientType;

import java.util.Map;

public interface BlockedClientService {

    void init(Map<String, BlockedClient> blockedClientMap);

    void startListening(BlockedClientConsumerService blockedClientConsumerService);

    BlockedClientDto addBlockedClientAndPersist(BlockedClient blockedClient);

    void addBlockedClient(BlockedClient blockedClient);

    void addBlockedClient(String key, BlockedClient blockedClient);

    void removeBlockedClientAndPersist(BlockedClient blockedClient);

    void removeBlockedClient(BlockedClient blockedClient);

    void removeBlockedClient(BlockedClientType type, String key);

    Map<BlockedClientType, Map<String, BlockedClient>> getBlockedClients();

    BlockedClient getBlockedClient(BlockedClientType type, String key);

    BlockedClientResult checkBlocked(String clientId, String username, String ipAddress);

    int getBlockedClientCleanupTtl();
}
