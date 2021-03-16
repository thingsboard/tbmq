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
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceMsgSerialNumberServiceImpl implements DeviceMsgSerialNumberService {

    // TODO: cache last serial number for client, if none - get from DB
    private final DeviceSessionCtxService deviceSessionCtxService;

    @Override
    public Map<String, Long> getLastSerialNumbers(Collection<String> clientIds) {
        return deviceSessionCtxService.findAllContexts(clientIds).stream()
                .collect(Collectors.toMap(DeviceSessionCtx::getClientId, DeviceSessionCtx::getLastSerialNumber));
    }

    @Override
    public void saveLastSerialNumbers(Map<String, Long> clientsLastSerialNumbers) {
        List<DeviceSessionCtx> deviceSessionContexts = clientsLastSerialNumbers.entrySet().stream()
                .map(entry -> DeviceSessionCtx.builder()
                        .clientId(entry.getKey())
                        .lastSerialNumber(entry.getValue())
                        .build())
                .collect(Collectors.toList());
        deviceSessionCtxService.saveDeviceSessionContexts(deviceSessionContexts);
    }
}
