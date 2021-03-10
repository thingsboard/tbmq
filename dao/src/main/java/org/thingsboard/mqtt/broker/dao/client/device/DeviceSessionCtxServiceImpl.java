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

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class DeviceSessionCtxServiceImpl implements DeviceSessionCtxService {

    @Autowired
    private DeviceSessionCtxDao deviceSessionCtxDao;

    @Override
    public DeviceSessionCtx saveDeviceSessionCtx(DeviceSessionCtx deviceSessionCtx) {
        log.trace("Executing saveDeviceSessionCtx [{}]", deviceSessionCtx);
        validate(deviceSessionCtx);
        return deviceSessionCtxDao.save(deviceSessionCtx);
    }

    @Override
    public void deleteDeviceSessionCtx(String clientId) {
        log.trace("Executing deleteDeviceSessionCtx [{}]", clientId);
        deviceSessionCtxDao.remove(clientId);
    }

    @Override
    public Optional<DeviceSessionCtx> getDeviceSessionCtx(String clientId) {
        log.trace("Executing getDeviceSessionCtx [{}]", clientId);
        return Optional.ofNullable(deviceSessionCtxDao.find(clientId));
    }

    @Override
    public List<DeviceSessionCtx> getAllDeviceSessions() {
        log.trace("Executing getAllDeviceSessions");
        return deviceSessionCtxDao.find();
    }

    private void validate(DeviceSessionCtx deviceSessionCtx) {
        if (StringUtils.isEmpty(deviceSessionCtx.getClientId())) {
            throw new DataValidationException("Client ID should be specified!");
        }
        if (deviceSessionCtx.getPublishedMsgInfos() == null) {
            throw new DataValidationException("Published messages infos should be specified!");
        }
    }
}
