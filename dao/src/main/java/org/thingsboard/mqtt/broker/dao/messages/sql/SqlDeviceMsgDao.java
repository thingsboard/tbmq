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
package org.thingsboard.mqtt.broker.dao.messages.sql;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.data.PersistedPacketType;
import org.thingsboard.mqtt.broker.dao.DaoUtil;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgDao;
import org.thingsboard.mqtt.broker.dao.messages.InsertDeviceMsgRepository;
import org.thingsboard.mqtt.broker.dao.model.sql.DevicePublishMsgEntity;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class SqlDeviceMsgDao implements DeviceMsgDao {

    private final InsertDeviceMsgRepository insertDeviceMsgRepository;
    private final DeviceMsgRepository deviceMsgRepository;

    @Override
    public void save(List<DevicePublishMsg> devicePublishMessages) {
        log.trace("Saving device publish messages: {}", devicePublishMessages);
        List<DevicePublishMsgEntity> entities = devicePublishMessages.stream().map(DevicePublishMsgEntity::new).collect(Collectors.toList());
        insertDeviceMsgRepository.insert(entities);
    }

    @Override
    public List<DevicePublishMsg> findPersistedMessages(String clientId, int messageLimit) {
        log.trace("Finding device publish messages, clientId - {}, limit - {}", clientId, messageLimit);
        List<DevicePublishMsgEntity> devicePublishMsgs = deviceMsgRepository.findByClientIdReversed(clientId, messageLimit).stream()
                .sorted(Comparator.comparingLong(DevicePublishMsgEntity::getSerialNumber))
                .collect(Collectors.toList());
        return DaoUtil.convertDataList(devicePublishMsgs);
    }

    @Override
    public void removePersistedMessages(String clientId) {
        log.debug("Removing device publish messages, clientId - {}", clientId);
        deviceMsgRepository.removeAllByClientId(clientId);
    }

    @Override
    public void removePersistedMessage(String clientId, int packetId) {
        log.trace("Removing device publish message, clientId - {}, packetId - {}", clientId, packetId);
        deviceMsgRepository.removeAllByClientIdAndPacketId(clientId, packetId);
    }

    @Override
    public void updatePacketType(String clientId, int packetId, PersistedPacketType packetType) {
        log.trace("Updating packet type for device publish message, clientId - {}, packetId - {}, packetType - {}.", clientId, packetId, packetType);
        int rowsUpdated = insertDeviceMsgRepository.updatePacketType(clientId, packetId, packetType);
        if (rowsUpdated != 1) {
            log.warn("While trying to update packet type {} rows were affected instead of 1 row. ClientId - {}, packetId - {}, packetType - {}.",
                    rowsUpdated, clientId, packetId, packetType);
        }
    }
}
