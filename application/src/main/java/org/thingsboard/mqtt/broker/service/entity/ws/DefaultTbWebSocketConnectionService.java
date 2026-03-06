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
package org.thingsboard.mqtt.broker.service.entity.ws;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketConnection;
import org.thingsboard.mqtt.broker.dao.ws.WebSocketConnectionService;
import org.thingsboard.mqtt.broker.service.entity.AbstractTbEntityService;
import org.thingsboard.mqtt.broker.service.mqtt.client.cleanup.ClientSessionCleanUpService;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultTbWebSocketConnectionService extends AbstractTbEntityService implements TbWebSocketConnectionService {

    private final WebSocketConnectionService webSocketConnectionService;
    private final ClientSessionCleanUpService clientSessionCleanUpService;

    @Override
    public WebSocketConnection save(WebSocketConnection connection, User currentUser) {
        return webSocketConnectionService.saveWebSocketConnection(connection);
    }

    @Override
    public void delete(WebSocketConnection connection, User currentUser) {
        clientSessionCleanUpService.disconnectClientSession(connection.getConfiguration().getClientId());
        webSocketConnectionService.deleteWebSocketConnection(connection.getId());
    }

}
