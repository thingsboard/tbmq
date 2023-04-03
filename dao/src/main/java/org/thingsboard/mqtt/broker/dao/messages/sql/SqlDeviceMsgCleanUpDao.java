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
package org.thingsboard.mqtt.broker.dao.messages.sql;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.dao.client.device.DeviceSessionCtxRepository;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgCleanUpDao;
import org.thingsboard.mqtt.broker.dao.model.DeviceSessionCtxEntity;
import org.thingsboard.mqtt.broker.dao.model.sql.DevicePublishMsgEntity;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class SqlDeviceMsgCleanUpDao implements DeviceMsgCleanUpDao {
    private final DeviceMsgRepository deviceMsgRepository;
    private final DeviceSessionCtxRepository deviceSessionCtxRepository;

    @Value("${mqtt.persistent-session.device.persisted-messages.clean-up.session-ctx-page-size:1000}")
    private int sessionCtxPageSize;

    @Override
    public void cleanUpByTime(long ttl) {
        if (log.isTraceEnabled()) {
            log.trace("Cleaning up device publish messages for TTL {} seconds.", ttl);
        }
        long earliestAcceptableTime = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(ttl);
        int removed = deviceMsgRepository.removeAllByTimeLessThan(earliestAcceptableTime);
        log.info("Cleared {} publish messages older than {}.", removed, earliestAcceptableTime);
    }

    @Override
    public void cleanUpBySize(int maxPersistedMessages) {
        if (maxPersistedMessages <= 0) {
            log.error("Only positive numbers are allowed.");
            return;
        }
        if (log.isTraceEnabled()) {
            log.trace("Cleaning up device publish messages to match max size {}.", maxPersistedMessages);
        }

        Page<DeviceSessionCtxEntity> deviceSessionCtxEntities;
        int pageCounter = 0;
        do {
            deviceSessionCtxEntities = deviceSessionCtxRepository.findAll(PageRequest.of(pageCounter++, sessionCtxPageSize));
            for (DeviceSessionCtxEntity deviceSessionCtxEntity : deviceSessionCtxEntities) {
                String clientId = deviceSessionCtxEntity.getClientId();
                DevicePublishMsgEntity earliestPersistedMsg = deviceMsgRepository.findEntityByClientIdAfterOffset(clientId, maxPersistedMessages - 1);
                if (earliestPersistedMsg == null) {
                    if (log.isTraceEnabled()) {
                        log.trace("[{}] No messages to clean up.", clientId);
                    }
                } else {
                    if (log.isTraceEnabled()) {
                        log.trace("[{}] Clearing messages with serial number less than {}.", clientId, earliestPersistedMsg.getSerialNumber());
                    }
                    int removed = deviceMsgRepository.removeAllByClientIdAndSerialNumberLessThan(clientId, earliestPersistedMsg.getSerialNumber());
                    if (log.isDebugEnabled()) {
                        log.debug("[{}] Removed {} messages.", clientId, removed);
                    }
                }
            }
        } while (!deviceSessionCtxEntities.isLast());

    }
}
