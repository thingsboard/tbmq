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
package org.thingsboard.mqtt.broker.dao.ws;

import org.thingsboard.mqtt.broker.common.data.dto.WebSocketConnectionDto;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketConnection;

import java.util.Optional;
import java.util.UUID;

public interface WebSocketConnectionService {

    WebSocketConnection saveWebSocketConnection(WebSocketConnection connection);

    PageData<WebSocketConnectionDto> getWebSocketConnections(PageLink pageLink);

    Optional<WebSocketConnection> getWebSocketConnectionById(UUID id);

    WebSocketConnection findWebSocketConnectionByName(String name);

    boolean deleteWebSocketConnection(UUID id);

}
