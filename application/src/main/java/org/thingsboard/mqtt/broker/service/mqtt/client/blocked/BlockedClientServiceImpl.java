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

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.BlockedClient;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.BlockedClientType;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.RegexBlockedClient;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class BlockedClientServiceImpl implements BlockedClientService {

    private final ServiceInfoProvider serviceInfoProvider;
    private final Map<BlockedClientType, Map<String, BlockedClient>> blockedClientMap;
    private final Set<RegexBlockedClient> regexBlockedClients;

    public BlockedClientServiceImpl(ServiceInfoProvider serviceInfoProvider) {
        this.serviceInfoProvider = serviceInfoProvider;

        EnumMap<BlockedClientType, Map<String, BlockedClient>> temp = new EnumMap<>(BlockedClientType.class);
        for (var type : BlockedClientType.values()) {
            temp.put(type, new ConcurrentHashMap<>());
        }
        this.blockedClientMap = Collections.unmodifiableMap(temp);

        this.regexBlockedClients = ConcurrentHashMap.newKeySet();
    }

    @Override
    public void init(Map<String, BlockedClient> initBlockedClientMap) {

    }

    @Override
    public void startListening(BlockedClientConsumerService consumerService) {
        consumerService.listen(this::processRetainedMsgUpdate);
    }

    @Override
    public void addBlockedClientAndPersist(String topic, BlockedClient blockedClient) {

    }

    @Override
    public void addBlockedClientAndPersist(String topic, BlockedClient blockedClient, BasicCallback callback) {

    }

    @Override
    public void addBlockedClient(String topic, BlockedClient blockedClient) {

    }

    @Override
    public void removeBlockedClientAndPersist(String topic) {

    }

    @Override
    public void removeBlockedClientAndPersist(String topic, BasicCallback callback) {

    }

    @Override
    public void removeBlockedClient(String topic) {

    }

    @Override
    public List<BlockedClient> getBlockedClients() {
        return List.of();
    }

    @Override
    public List<BlockedClient> getRegexBlockedClients() {
        return List.of();
    }

    @Override
    public boolean isBlocked(String clientId, String username, String ipAddress) {
        if (clientId != null && blockedClientMap.get(BlockedClientType.CLIENT_ID).containsKey(clientId)) return true;
        if (username != null && blockedClientMap.get(BlockedClientType.USERNAME).containsKey(username)) return true;
        if (ipAddress != null && blockedClientMap.get(BlockedClientType.IP_ADDRESS).containsKey(ipAddress)) return true;

        for (RegexBlockedClient r : regexBlockedClients) {
            switch (r.getRegexMatchTarget()) {
                case BY_CLIENT_ID -> {
                    if (clientId != null && r.matches(clientId)) return true;
                }
                case BY_USERNAME -> {
                    if (username != null && r.matches(username)) return true;
                }
                case BY_IP_ADDRESS -> {
                    if (ipAddress != null && r.matches(ipAddress)) return true;
                }
            }
        }
        return false;
    }

    private void processRetainedMsgUpdate(String clientId, String serviceId, BlockedClient blockedClient) {
        if (serviceInfoProvider.getServiceId().equals(serviceId)) {
            log.trace("[{}] Blocked client was already processed", clientId);
            return;
        }
        if (blockedClient == null) {
            log.trace("[{}][{}] Clearing blocked client", serviceId, clientId);
//            blockedClientMap.remove(clientId);
        } else {
            log.trace("[{}][{}] Saving blocked client", serviceId, clientId);
//            blockedClientMap.put(clientId, blockedClient);
        }
    }
}
