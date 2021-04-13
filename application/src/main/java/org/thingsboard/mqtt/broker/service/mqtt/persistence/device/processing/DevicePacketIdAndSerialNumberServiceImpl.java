/**
 * Copyright © 2016-2020 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.device.processing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.DeviceSessionCtx;
import org.thingsboard.mqtt.broker.dao.client.device.DeviceSessionCtxService;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DevicePacketIdAndSerialNumberServiceImpl implements DevicePacketIdAndSerialNumberService {

    // TODO: cache last serial number for client, if none - get from DB
    private final DeviceSessionCtxService deviceSessionCtxService;

    @Override
    public Map<String, PacketIdAndSerialNumber> getLastPacketIdAndSerialNumber(Collection<String> clientIds) {
        return deviceSessionCtxService.findAllContexts(clientIds).stream()
                .collect(Collectors.toMap(DeviceSessionCtx::getClientId,
                        deviceSessionCtx -> new PacketIdAndSerialNumber(new AtomicInteger(deviceSessionCtx.getLastPacketId()),
                                new AtomicLong(deviceSessionCtx.getLastSerialNumber()))));
    }

    @Override
    public void saveLastSerialNumbers(Map<String, PacketIdAndSerialNumber> clientsLastPacketIdAndSerialNumbers) {
        List<DeviceSessionCtx> deviceSessionContexts = clientsLastPacketIdAndSerialNumbers.entrySet().stream()
                .map(entry -> DeviceSessionCtx.builder()
                        .clientId(entry.getKey())
                        .lastUpdatedTime(System.currentTimeMillis())
                        .lastSerialNumber(entry.getValue().getSerialNumber().get())
                        .lastPacketId(entry.getValue().getPacketId().get())
                        .build())
                .collect(Collectors.toList());
        deviceSessionCtxService.saveDeviceSessionContexts(deviceSessionContexts);
    }
}
