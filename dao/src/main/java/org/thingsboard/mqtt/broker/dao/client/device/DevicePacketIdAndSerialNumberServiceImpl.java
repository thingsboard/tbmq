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
package org.thingsboard.mqtt.broker.dao.client.device;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.cache.CacheConstants;
import org.thingsboard.mqtt.broker.common.data.DeviceSessionCtx;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DevicePacketIdAndSerialNumberServiceImpl implements DevicePacketIdAndSerialNumberService {

    private final DeviceSessionCtxService deviceSessionCtxService;
    private final CacheManager cacheManager;

    @Override
    public Map<String, PacketIdAndSerialNumber> getLastPacketIdAndSerialNumber(Set<String> clientIds) {
        if (log.isDebugEnabled()) {
            log.debug("Trying to find PacketIdAndSerialNumbers for clients: {}", clientIds);
        }
        Map<String, PacketIdAndSerialNumber> result = Maps.newHashMap();
        Cache cache = getCache();

        Set<String> clientIdsFromDb = Sets.newHashSet();
        clientIds.forEach(clientId -> {
            PacketIdAndSerialNumber fromCache = cache.get(clientId, PacketIdAndSerialNumber.class);
            if (fromCache != null) {
                result.put(clientId, fromCache);
            } else {
                clientIdsFromDb.add(clientId);
            }
        });

        if (!clientIdsFromDb.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("Did not find in cache, getting PacketIdAndSerialNumbers from DB for: {}", clientIdsFromDb);
            }
            Collection<DeviceSessionCtx> deviceSessionsFromDb = findSessionsByClientIds(clientIdsFromDb);
            result.putAll(updateCacheAndToMap(deviceSessionsFromDb, cache));
        }

        return result;
    }

    private Map<String, PacketIdAndSerialNumber> updateCacheAndToMap(Collection<DeviceSessionCtx> deviceSessions, Cache cache) {
        return deviceSessions.stream()
                .peek(deviceSessionCtx ->
                        cache.putIfAbsent(deviceSessionCtx.getClientId(), newPacketIdAndSerialNumber(deviceSessionCtx)))
                .collect(Collectors.toMap(
                        DeviceSessionCtx::getClientId,
                        this::newPacketIdAndSerialNumber)
                );
    }

    private PacketIdAndSerialNumber newPacketIdAndSerialNumber(DeviceSessionCtx deviceSessionCtx) {
        return new PacketIdAndSerialNumber(
                new AtomicInteger(deviceSessionCtx.getLastPacketId()),
                new AtomicLong(deviceSessionCtx.getLastSerialNumber())
        );
    }

    private Collection<DeviceSessionCtx> findSessionsByClientIds(Set<String> clientIds) {
        if (log.isTraceEnabled()) {
            log.trace("Processing findSessionsByClientIds: {}", clientIds);
        }
        return deviceSessionCtxService.findAllContexts(clientIds);
    }

    @Override
    public void saveLastSerialNumbers(Map<String, PacketIdAndSerialNumber> clientsLastPacketIdAndSerialNumbers) {
        if (log.isTraceEnabled()) {
            log.trace("Processing saveLastSerialNumbers for clients: {}", clientsLastPacketIdAndSerialNumbers.keySet());
        }
        List<DeviceSessionCtx> deviceSessionContexts = clientsLastPacketIdAndSerialNumbers.entrySet().stream()
                .map(this::buildDeviceSessionCtx)
                .collect(Collectors.toList());
        deviceSessionCtxService.saveDeviceSessionContexts(deviceSessionContexts);

        Cache cache = getCache();
        deviceSessionContexts.forEach(deviceSessionCtx -> cache.put(deviceSessionCtx.getClientId(), newPacketIdAndSerialNumber(deviceSessionCtx)));
    }

    private DeviceSessionCtx buildDeviceSessionCtx(Map.Entry<String, PacketIdAndSerialNumber> entry) {
        return DeviceSessionCtx.builder()
                .clientId(entry.getKey())
                .lastUpdatedTime(System.currentTimeMillis())
                .lastSerialNumber(entry.getValue().getSerialNumber().get())
                .lastPacketId(entry.getValue().getPacketId().get())
                .build();
    }

    private Cache getCache() {
        return cacheManager.getCache(CacheConstants.PACKET_ID_AND_SERIAL_NUMBER_CACHE);
    }
}