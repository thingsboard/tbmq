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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.device.cleanup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgCleanUpDao;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceMsgCleanUpService {

    private final DeviceMsgCleanUpDao cleanUpDao;

    @Value("${mqtt.persistent-session.device.persisted-messages.ttl}")
    private long ttl;

    @Value("${mqtt.persistent-session.device.persisted-messages.limit}")
    private int messagesLimit;

    @Scheduled(cron = "${mqtt.persistent-session.device.persisted-messages.clean-up-cron}",
            zone = "${mqtt.persistent-session.device.persisted-messages.clean-up-zone}")
    public void cleanUp() {
        log.info("Starting cleaning up DEVICE publish messages.");

        cleanUpDao.cleanUpByTime(ttl);

        cleanUpDao.cleanUpBySize(messagesLimit);
    }
}
