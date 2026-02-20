/**
 * Copyright © 2016-2026 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.entity.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.service.entity.AbstractTbEntityService;
import org.thingsboard.mqtt.broker.service.system.SystemInfoService;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultTbAppService extends AbstractTbEntityService implements TbAppService {

    private final TbQueueAdmin tbQueueAdmin;
    private final SystemInfoService systemInfoService;

    @Override
    public void deleteKafkaConsumerGroup(String groupId, User currentUser) throws Exception {
        tbQueueAdmin.deleteConsumerGroup(groupId);
    }

    @Override
    public void deleteServiceInfo(String serviceId, User currentUser) throws ThingsboardException {
        systemInfoService.removeServiceInfo(serviceId);
    }
}
