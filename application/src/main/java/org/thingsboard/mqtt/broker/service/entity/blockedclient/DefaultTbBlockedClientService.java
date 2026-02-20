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
package org.thingsboard.mqtt.broker.service.entity.blockedclient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.dto.BlockedClientDto;
import org.thingsboard.mqtt.broker.service.entity.AbstractTbEntityService;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.BlockedClientService;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.BlockedClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultTbBlockedClientService extends AbstractTbEntityService implements TbBlockedClientService {

    private final BlockedClientService blockedClientService;

    @Override
    public BlockedClientDto save(BlockedClient blockedClient, User currentUser) {
        return blockedClientService.addBlockedClientAndPersist(blockedClient);
    }

    @Override
    public void delete(BlockedClient blockedClient, User currentUser) {
        blockedClientService.removeBlockedClientAndPersist(blockedClient);
    }

}
