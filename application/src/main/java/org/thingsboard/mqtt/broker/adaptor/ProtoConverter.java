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
package org.thingsboard.mqtt.broker.adaptor;

import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.service.mqtt.ClientSession;
import org.thingsboard.mqtt.broker.service.mqtt.LastPublishCtx;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.mqtt.TopicSubscription;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEvent;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventType;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public class ProtoConverter {
    public static QueueProtos.PublishMsgProto convertToPublishProtoMessage(SessionInfo sessionInfo, PublishMsg publishMsg) {
        QueueProtos.SessionInfoProto sessionInfoProto = convertToSessionInfoProto(sessionInfo);
        return QueueProtos.PublishMsgProto.newBuilder()
                .setPacketId(publishMsg.getPacketId())
                .setTopicName(publishMsg.getTopicName())
                .setQos(publishMsg.getQosLevel())
                .setRetain(publishMsg.isRetained())
                .setPayload(ByteString.copyFrom(publishMsg.getPayload()))
                .setSessionInfo(sessionInfoProto)
                .build();
    }

    public static ClientSession convertToClientSession(QueueProtos.ClientSessionProto clientSessionProto) {
        return ClientSession.builder()
                .connected(clientSessionProto.getConnected())
                .sessionInfo(convertToSessionInfo(clientSessionProto.getSessionInfo()))
                .build();
    }

    public static QueueProtos.ClientSessionProto convertToClientSessionProto(ClientSession clientSession) {
        return QueueProtos.ClientSessionProto.newBuilder()
                .setConnected(clientSession.isConnected())
                .setSessionInfo(convertToSessionInfoProto(clientSession.getSessionInfo()))
                .build();
    }

    public static QueueProtos.SessionInfoProto convertToSessionInfoProto(SessionInfo sessionInfo) {
        ClientInfo clientInfo = sessionInfo.getClientInfo();
        return QueueProtos.SessionInfoProto.newBuilder()
                .setSessionIdMSB(sessionInfo.getSessionId().getMostSignificantBits())
                .setSessionIdLSB(sessionInfo.getSessionId().getLeastSignificantBits())
                .setPersistent(sessionInfo.isPersistent())
                .setClientInfo(convertToClientInfoProto(clientInfo))
                .build();
    }

    public static SessionInfo convertToSessionInfo(QueueProtos.SessionInfoProto sessionInfoProto) {
        QueueProtos.ClientInfoProto clientInfoProto = sessionInfoProto.getClientInfo();
        return SessionInfo.builder()
                .sessionId(new UUID(sessionInfoProto.getSessionIdMSB(), sessionInfoProto.getSessionIdLSB()))
                .persistent(sessionInfoProto.getPersistent())
                .clientInfo(convertToClientInfo(clientInfoProto))
                .build();
    }

    public static QueueProtos.ClientInfoProto convertToClientInfoProto(ClientInfo clientInfo) {
        return clientInfo != null ? QueueProtos.ClientInfoProto.newBuilder()
                .setClientId(clientInfo.getClientId())
                .setClientType(clientInfo.getType().toString())
                .build() : null;
    }

    public static ClientInfo convertToClientInfo(QueueProtos.ClientInfoProto clientInfoProto) {
        return ClientInfo.builder()
                .clientId(clientInfoProto.getClientId())
                .type(ClientType.valueOf(clientInfoProto.getClientType()))
                .build();
    }

    public static QueueProtos.ClientSubscriptionsProto convertToClientSubscriptionsProto(Collection<TopicSubscription> topicSubscriptions) {
        List<QueueProtos.TopicSubscriptionProto> topicSubscriptionsProto = topicSubscriptions.stream()
                .map(topicSubscription -> QueueProtos.TopicSubscriptionProto.newBuilder()
                        .setQos(topicSubscription.getQos())
                        .setTopic(topicSubscription.getTopic())
                        .build())
                .collect(Collectors.toList());
        return QueueProtos.ClientSubscriptionsProto.newBuilder().addAllSubscriptions(topicSubscriptionsProto).build();
    }

    public static Set<TopicSubscription> convertToClientSubscriptions(QueueProtos.ClientSubscriptionsProto clientSubscriptionsProto) {
        return clientSubscriptionsProto.getSubscriptionsList().stream()
                .map(topicSubscriptionProto -> TopicSubscription.builder()
                        .qos(topicSubscriptionProto.getQos())
                        .topic(topicSubscriptionProto.getTopic())
                        .build())
                .collect(Collectors.toSet());
    }

    public static LastPublishCtx convertToLastPublishCtx(QueueProtos.LastPublishCtxProto publishCtxProto) {
        return new LastPublishCtx(publishCtxProto.getPacketId());
    }

    public static QueueProtos.LastPublishCtxProto createLastPublishCtxProto(int packetId) {
        return QueueProtos.LastPublishCtxProto.newBuilder()
                .setPacketId(packetId)
                .build();
    }

    public static QueueProtos.ClientSessionEventProto convertToClientSessionEventProto(ClientSessionEvent clientSessionEvent) {
        ClientInfo clientInfo = clientSessionEvent.getClientInfo();
        QueueProtos.ClientInfoProto clientInfoProto = clientInfo != null ? QueueProtos.ClientInfoProto.newBuilder()
                .setClientId(clientInfo.getClientId())
                .setClientType(clientInfo.getType().toString())
                .build() : null;
        return QueueProtos.ClientSessionEventProto.newBuilder()
                .setEventType(clientSessionEvent.getEventType().toString())
                .setSessionIdMSB(clientSessionEvent.getSessionId().getMostSignificantBits())
                .setSessionIdLSB(clientSessionEvent.getSessionId().getLeastSignificantBits())
                .setClientInfo(clientInfoProto)
                .setPersistent(clientSessionEvent.isPersistent())
                .build();
    }

    public static ClientSessionEvent convertToClientSessionEvent(QueueProtos.ClientSessionEventProto clientSessionEventProto) {
        QueueProtos.ClientInfoProto clientInfoProto = clientSessionEventProto.getClientInfo();
        ClientInfo clientInfo = clientInfoProto != null ? ClientInfo.builder()
                .clientId(clientInfoProto.getClientId())
                .type(ClientType.valueOf(clientInfoProto.getClientType()))
                .build() : null;
        return ClientSessionEvent.builder()
                .eventType(ClientSessionEventType.valueOf(clientSessionEventProto.getEventType()))
                .sessionId(new UUID(clientSessionEventProto.getSessionIdMSB(), clientSessionEventProto.getSessionIdLSB()))
                .clientInfo(clientInfo)
                .persistent(clientSessionEventProto.getPersistent())
                .build();
    }

    public static QueueProtos.DisconnectClientCommandProto createDisconnectClientCommandProto(UUID sessionId) {
       return QueueProtos.DisconnectClientCommandProto.newBuilder()
               .setSessionIdMSB(sessionId.getMostSignificantBits())
               .setSessionIdLSB(sessionId.getLeastSignificantBits())
               .build();
    }
}
