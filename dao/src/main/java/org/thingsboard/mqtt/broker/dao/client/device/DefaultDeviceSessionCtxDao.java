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

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.DeviceSessionCtx;
import org.thingsboard.mqtt.broker.dao.DaoUtil;
import org.thingsboard.mqtt.broker.dao.model.DeviceSessionCtxEntity;

import java.util.List;

@Slf4j
@Component
public class DefaultDeviceSessionCtxDao implements DeviceSessionCtxDao {
    @Autowired
    private DeviceSessionCtxRepository deviceSessionCtxRepository;

    @Override
    public DeviceSessionCtx save(DeviceSessionCtx deviceSessionCtx) {
        return DaoUtil.getData(deviceSessionCtxRepository.save(new DeviceSessionCtxEntity(deviceSessionCtx)));
    }

    @Override
    public DeviceSessionCtx find(String clientId) {
        return DaoUtil.getData(deviceSessionCtxRepository.findByClientId(clientId));
    }

    @Override
    public List<DeviceSessionCtx> find() {
        List<DeviceSessionCtxEntity> entities = Lists.newArrayList(deviceSessionCtxRepository.findAll());
        return DaoUtil.convertDataList(entities);
    }

    @Override
    public void remove(String clientId) {
        deviceSessionCtxRepository.deleteById(clientId);
    }
}
