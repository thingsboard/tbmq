/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.mqtt.client.event;

import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;

import java.util.UUID;

public interface ClientSessionEventFactory {

    QueueProtos.ClientSessionEventProto createConnectionRequestEventProto(SessionInfo sessionInfo);

    QueueProtos.ClientSessionEventProto createDisconnectedEventProto(ClientInfo clientInfo, UUID sessionId,
                                                                     int sessionExpiryInterval);

    QueueProtos.ClientSessionEventProto createClearSessionRequestEventProto(SessionInfo sessionInfo);

    QueueProtos.ClientSessionEventProto createClearSessionRequestEventProto(ClientSessionInfo clientSessionInfo);

    QueueProtos.ClientSessionEventProto createApplicationTopicRemoveRequestEventProto(String clientId);
}
