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
package org.thingsboard.mqtt.broker.service.mqtt.client.event;

import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;

import java.util.UUID;

@Service
public class ClientSessionEventFactoryImpl implements ClientSessionEventFactory {
    @Override
    public QueueProtos.ClientSessionEventProto createConnectionRequestEventProto(SessionInfo sessionInfo) {
        return QueueProtos.ClientSessionEventProto.newBuilder()
                .setSessionInfo(ProtoConverter.convertToSessionInfoProto(sessionInfo))
                .setEventType(ClientSessionEventType.CONNECTION_REQUEST.toString())
                .build();
    }

    @Override
    public QueueProtos.ClientSessionEventProto createDisconnectedEventProto(ClientInfo clientInfo, UUID sessionId) {
        SessionInfo disconnectSessionInfo = SessionInfo.builder()
                .sessionId(sessionId)
                .clientInfo(clientInfo)
                .serviceId("")
                .build();
        return QueueProtos.ClientSessionEventProto.newBuilder()
                .setSessionInfo(ProtoConverter.convertToSessionInfoProto(disconnectSessionInfo))
                .setEventType(ClientSessionEventType.DISCONNECTED.toString())
                .build();
    }

    @Override
    public QueueProtos.ClientSessionEventProto createTryClearSessionRequestEventProto(SessionInfo sessionInfo) {
        return QueueProtos.ClientSessionEventProto.newBuilder()
                .setSessionInfo(ProtoConverter.convertToSessionInfoProto(sessionInfo))
                .setEventType(ClientSessionEventType.TRY_CLEAR_SESSION_REQUEST.toString())
                .build();
    }

    @Override
    public QueueProtos.ClientSessionEventProto createApplicationTopicRemoveRequestProto(String clientId) {
        return QueueProtos.ClientSessionEventProto.newBuilder()
                .setEventType(ClientSessionEventType.REMOVE_APPLICATION_TOPIC_REQUEST.toString())
                .build();
    }
}
