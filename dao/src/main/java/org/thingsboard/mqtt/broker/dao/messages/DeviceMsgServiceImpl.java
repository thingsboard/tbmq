/**
 * Copyright © 2016-2023 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.dao.messages;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.data.PersistedPacketType;
import org.thingsboard.mqtt.broker.dao.DbConnectionChecker;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceMsgServiceImpl implements DeviceMsgService {

    private final DeviceMsgDao deviceMsgDao;
    private final DbConnectionChecker dbConnectionChecker;

    @Value("${mqtt.persistent-session.device.persisted-messages.limit:1000}")
    private int messagesLimit;

    @Override
    public void save(List<DevicePublishMsg> devicePublishMessages, boolean failOnConflict) {
        if (log.isTraceEnabled()) {
            log.trace("Saving {} device publish messages, failOnConflict - {}.", devicePublishMessages.size(), failOnConflict);
        }
        deviceMsgDao.save(devicePublishMessages, failOnConflict);
    }

    @Override
    public List<DevicePublishMsg> findPersistedMessages(String clientId) {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Loading persisted messages.", clientId);
        }
        return deviceMsgDao.findPersistedMessages(clientId, messagesLimit);
    }

    @Override
    public List<DevicePublishMsg> findPersistedMessages(String clientId, long fromSerialNumber, long toSerialNumber) {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Loading persisted messages, fromSerialNumber - {}, toSerialNumber - {}.", clientId, fromSerialNumber, toSerialNumber);
        }
        if (fromSerialNumber < 0 || toSerialNumber < 0 || fromSerialNumber > toSerialNumber) {
            throw new RuntimeException("Not valid 'from' and 'to' serial number values");
        }
        if (toSerialNumber - fromSerialNumber > messagesLimit) {
            fromSerialNumber = toSerialNumber - messagesLimit;
        }
        return deviceMsgDao.findPersistedMessagesBySerialNumber(clientId, fromSerialNumber, toSerialNumber);
    }

    @Override
    public void removePersistedMessages(String clientId) {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Removing persisted messages.", clientId);
        }
        try {
            deviceMsgDao.removePersistedMessages(clientId);
        } catch (Exception e) {
            log.warn("[{}] Failed to remove persisted messages.", clientId, e);
        }
    }

    @Override
    public ListenableFuture<Void> tryRemovePersistedMessage(String clientId, int packetId) {
        if (!dbConnectionChecker.isDbConnected()) {
            if (log.isTraceEnabled()) {
                log.trace("[{}] Ignoring remove persisted message request, no DB connection, packetId - {}", clientId, packetId);
            }
            return Futures.immediateFuture(null);
        }
        if (log.isTraceEnabled()) {
            log.trace("[{}] Removing persisted message with packetId {}.", clientId, packetId);
        }
        return deviceMsgDao.removePersistedMessage(clientId, packetId);
    }

    @Override
    public ListenableFuture<Void> tryUpdatePacketReceived(String clientId, int packetId) {
        if (!dbConnectionChecker.isDbConnected()) {
            if (log.isTraceEnabled()) {
                log.trace("[{}] Ignoring update packet request, no DB connection, packetId - {}", clientId, packetId);
            }
            return Futures.immediateFuture(null);
        }
        if (log.isTraceEnabled()) {
            log.trace("[{}] Updating packet type to PUBREL for packetId {}.", clientId, packetId);
        }
        return deviceMsgDao.updatePacketType(clientId, packetId, PersistedPacketType.PUBREL);
    }
}
