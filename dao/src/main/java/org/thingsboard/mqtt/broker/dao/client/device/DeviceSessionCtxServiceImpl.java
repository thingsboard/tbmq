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
package org.thingsboard.mqtt.broker.dao.client.device;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thingsboard.mqtt.broker.common.data.DeviceSessionCtx;
import org.thingsboard.mqtt.broker.dao.exception.DataValidationException;

import java.util.Collection;

@Service
@Slf4j
public class DeviceSessionCtxServiceImpl implements DeviceSessionCtxService {

    @Autowired
    private DeviceSessionCtxDao deviceSessionCtxDao;

    @Override
    public void saveDeviceSessionContexts(Collection<DeviceSessionCtx> deviceSessionContexts) {
        log.trace("Executing saveDeviceSessionContexts [{}]", deviceSessionContexts);
        deviceSessionContexts.forEach(this::validate);
        deviceSessionCtxDao.save(deviceSessionContexts);
    }

    @Override
    public Collection<DeviceSessionCtx> findAllContexts(Collection<String> clientIds) {
        log.trace("Executing findAllContexts [{}]", clientIds);
        return deviceSessionCtxDao.findAll(clientIds);
    }

    @Override
    public void removeDeviceSessionContext(String clientId) {
        log.trace("Executing removeDeviceSessionContext [{}]", clientId);
        deviceSessionCtxDao.removeById(clientId);
    }

    private void validate(DeviceSessionCtx deviceSessionCtx) {
        if (StringUtils.isEmpty(deviceSessionCtx.getClientId())) {
            throw new DataValidationException("Client ID should be specified!");
        }
        if (deviceSessionCtx.getLastSerialNumber() == null) {
            throw new DataValidationException("Last serial number should be specified!");
        }
    }
}
