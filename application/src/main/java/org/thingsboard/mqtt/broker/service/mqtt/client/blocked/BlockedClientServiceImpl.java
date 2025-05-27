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

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.dto.BlockedClientDto;
import org.thingsboard.mqtt.broker.exception.DataValidationException;
import org.thingsboard.mqtt.broker.gen.queue.BlockedClientProto;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.constants.QueueConstants;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.consumer.BlockedClientConsumerService;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.BlockedClient;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.BlockedClientResult;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.BlockedClientType;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.RegexBlockedClient;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.producer.BlockedClientProducerService;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.util.BlockedClientKeyUtil;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.thingsboard.mqtt.broker.common.data.util.CallbackUtil.createCallback;
import static org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.BlockedClientResult.blocked;
import static org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.BlockedClientResult.notBlocked;

@Slf4j
@Service
public class BlockedClientServiceImpl implements BlockedClientService {

    private final BlockedClientProducerService blockedClientProducerService;
    private final ServiceInfoProvider serviceInfoProvider;
    private final Map<BlockedClientType, Map<String, BlockedClient>> blockedClientMap;

    @Getter
    @Setter
    @Value("${mqtt.blocked-client.cleanup.ttl:10080}")
    private int blockedClientCleanupTtl;

    public BlockedClientServiceImpl(BlockedClientProducerService blockedClientProducerService,
                                    ServiceInfoProvider serviceInfoProvider) {
        this.blockedClientProducerService = blockedClientProducerService;
        this.serviceInfoProvider = serviceInfoProvider;

        EnumMap<BlockedClientType, Map<String, BlockedClient>> temp = new EnumMap<>(BlockedClientType.class);
        for (var type : BlockedClientType.values()) {
            temp.put(type, new ConcurrentHashMap<>());
        }
        this.blockedClientMap = Collections.unmodifiableMap(temp);
    }

    @Override
    public void init(Map<String, BlockedClient> initBlockedClientMap) {
        initBlockedClientMap.forEach((key, client) -> blockedClientMap.get(client.getType()).put(key, client));
    }

    @Override
    public void startListening(BlockedClientConsumerService consumerService) {
        consumerService.listen(this::processBlockedClientUpdate);
    }

    @Override
    public BlockedClientDto addBlockedClientAndPersist(BlockedClient blockedClient) {
        log.trace("[{}] Executing addBlockedClientAndPersist", blockedClient);
        addBlockedClient(blockedClient);

        BlockedClientProto blockedClientProto = ProtoConverter.convertToBlockedClientProto(blockedClient);
        BasicCallback callback = createCallback(
                () -> log.trace("[{}] Persisted blocked client", blockedClient),
                t -> log.warn("[{}] Failed to persist blocked client", blockedClient, t));
        blockedClientProducerService.persistBlockedClient(blockedClient.getKey(), blockedClientProto, callback);

        return BlockedClientDto.newInstance(blockedClient);
    }

    @Override
    public void addBlockedClient(BlockedClient blockedClient) {
        validate(blockedClient);
        blockedClientMap.get(blockedClient.getType()).put(blockedClient.getKey(), blockedClient);
    }

    @Override
    public void removeBlockedClientAndPersist(BlockedClient blockedClient) {
        log.trace("Executing removeBlockedClientAndPersist {}", blockedClient);
        removeBlockedClient(blockedClient);

        BasicCallback callback = createCallback(
                () -> log.trace("Persisted removed blocked client {}", blockedClient),
                t -> log.warn("Failed to persist removed blocked client {}", blockedClient, t));
        blockedClientProducerService.persistBlockedClient(blockedClient.getKey(), QueueConstants.EMPTY_BLOCKED_CLIENT_PROTO, callback);
    }

    @Override
    public void removeBlockedClient(BlockedClient blockedClient) {
        log.trace("Executing removeBlockedClient {}", blockedClient);
        removeBlockedClient(blockedClient.getType(), blockedClient.getKey());
    }

    @Override
    public void removeBlockedClient(BlockedClientType type, String key) {
        blockedClientMap.get(type).remove(key);
    }

    @Override
    public Map<BlockedClientType, Map<String, BlockedClient>> getBlockedClients() {
        return new HashMap<>(blockedClientMap);
    }

    @Override
    public BlockedClient getBlockedClient(BlockedClientType type, String key) {
        return blockedClientMap.get(type).get(key);
    }

    @Override
    public BlockedClientResult checkBlocked(String clientId, String username, String ipAddress) {
        BlockedClient blocked = findExactMatch(BlockedClientType.CLIENT_ID, clientId);
        if (blocked != null) {
            return blocked(blocked);
        }

        blocked = findExactMatch(BlockedClientType.USERNAME, username);
        if (blocked != null) {
            return blocked(blocked);
        }

        blocked = findExactMatch(BlockedClientType.IP_ADDRESS, ipAddress);
        if (blocked != null) {
            return blocked(blocked);
        }

        var regexMap = blockedClientMap.get(BlockedClientType.REGEX);
        if (regexMap.isEmpty()) {
            return notBlocked();
        }

        for (var blockedClient : regexMap.values()) {
            if (!(blockedClient instanceof RegexBlockedClient rbc)) {
                continue;
            }

            String value = switch (rbc.getRegexMatchTarget()) {
                case BY_CLIENT_ID -> clientId;
                case BY_USERNAME -> username;
                case BY_IP_ADDRESS -> ipAddress;
            };
            if (value == null) {
                continue;
            }
            if (isRegexBasedBlocked(value, rbc)) {
                return blocked(rbc);
            }
        }
        return notBlocked();
    }

    private BlockedClient findExactMatch(BlockedClientType type, String value) {
        if (value == null) {
            return null;
        }

        var map = blockedClientMap.get(type);
        if (map.isEmpty()) {
            return null;
        }

        BlockedClient client = map.get(BlockedClientKeyUtil.generateKey(type, value));
        return (client != null && !client.isExpired()) ? client : null;
    }

    private boolean isRegexBasedBlocked(String value, RegexBlockedClient regexBlockedClient) {
        try {
            return !regexBlockedClient.isExpired() && regexBlockedClient.matches(value);
        } catch (Exception e) {
            log.warn("[{}] Failed to match regex blocked client {}", value, regexBlockedClient);
            return false;
        }
    }

    void processBlockedClientUpdate(String key, String serviceId, BlockedClient blockedClient) {
        if (serviceInfoProvider.getServiceId().equals(serviceId)) {
            log.trace("[{}] Blocked client was already processed", key);
            return;
        }
        if (blockedClient == null) {
            log.trace("[{}][{}] Clearing blocked client", serviceId, key);
            removeBlockedClient(BlockedClientKeyUtil.extractTypeFromKey(key), key);
        } else {
            log.trace("[{}][{}] Saving blocked client", serviceId, key);
            addBlockedClient(blockedClient);
        }
    }

    private void validate(BlockedClient blockedClient) {
        if (blockedClient.getType() == null) {
            throw new DataValidationException("Blocked client type should be specified");
        }
        if (StringUtils.isEmpty(blockedClient.getValue())) {
            throw new DataValidationException("Blocked client value should be specified");
        }
        if (BlockedClientType.REGEX.equals(blockedClient.getType()) && blockedClient.getRegexMatchTarget() == null) {
            throw new DataValidationException("Blocked client regex match target should be specified for REGEX type");
        }
    }

    @Scheduled(fixedRateString = "${mqtt.blocked-client.cleanup.period}", timeUnit = TimeUnit.MINUTES)
    public void cleanUp() {
        log.info("Starting cleaning up expired blocked clients");
        for (BlockedClientType type : BlockedClientType.values()) {
            blockedClientMap.get(type).values().forEach(value -> {
                if (value.isExpired() && getBlockedClientRemovalTs(value) <= System.currentTimeMillis()) {
                    log.info("Removing expired blocked client by ttl [{}][{} minutes]", value, blockedClientCleanupTtl);
                    removeBlockedClientAndPersist(value);
                }
            });
        }
        log.info("Cleanup of expired blocked clients is finished");
    }

    private long getBlockedClientRemovalTs(BlockedClient value) {
        return value.getExpirationTime() + TimeUnit.MINUTES.toMillis(blockedClientCleanupTtl);
    }

}
