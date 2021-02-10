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
import org.thingsboard.mqtt.broker.service.mqtt.PacketIdAndOffset;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.mqtt.TopicSubscription;

import java.util.List;
import java.util.Set;
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

    public static QueueProtos.SessionInfoProto convertToSessionInfoProto(SessionInfo sessionInfo) {
        ClientInfo clientInfo = sessionInfo.getClientInfo();
        QueueProtos.SessionInfoProto.Builder builder = QueueProtos.SessionInfoProto.newBuilder();
        builder
                .setSessionIdMSB(sessionInfo.getSessionId().getMostSignificantBits())
                .setSessionIdLSB(sessionInfo.getSessionId().getLeastSignificantBits())
                .setPersistent(sessionInfo.isPersistent())
                .setClientId(clientInfo.getClientId());
        return builder.build();
    }

    public static ClientSession convertToClientSession(QueueProtos.ClientSessionProto clientSessionProto) {
        Set<TopicSubscription> topicSubscriptions = clientSessionProto.getSubscriptionsList().stream()
                .map(topicSubscriptionProto -> TopicSubscription.builder()
                        .qos(topicSubscriptionProto.getQos())
                        .topic(topicSubscriptionProto.getTopic())
                        .build())
                .collect(Collectors.toSet());
        ClientInfo clientInfo = ClientInfo.builder()
                .clientId(clientSessionProto.getClientInfo().getClientId())
                .type(ClientType.valueOf(clientSessionProto.getClientInfo().getClientType()))
                .build();
        return ClientSession.builder()
                .connected(clientSessionProto.getConnected())
                .clientInfo(clientInfo)
                .persistent(clientSessionProto.getPersistent())
                .topicSubscriptions(topicSubscriptions)
                .build();
    }

    public static QueueProtos.ClientSessionProto convertToClientSessionProto(ClientSession clientSession) {
        List<QueueProtos.TopicSubscriptionProto> topicSubscriptionsProto = clientSession.getTopicSubscriptions().stream()
                .map(topicSubscription -> QueueProtos.TopicSubscriptionProto.newBuilder()
                        .setQos(topicSubscription.getQos())
                        .setTopic(topicSubscription.getTopic())
                        .build())
                .collect(Collectors.toList());
        QueueProtos.ClientInfoProto clientInfoProto = QueueProtos.ClientInfoProto.newBuilder()
                .setClientId(clientSession.getClientInfo().getClientId())
                .setClientType(clientSession.getClientInfo().getType().toString())
                .build();
        return QueueProtos.ClientSessionProto.newBuilder()
                .setConnected(clientSession.isConnected())
                .setPersistent(clientSession.isPersistent())
                .setClientInfo(clientInfoProto)
                .addAllSubscriptions(topicSubscriptionsProto)
                .build();
    }

    public static LastPublishCtx convertToLastPublishCtx(QueueProtos.LastPublishCtxProto publishCtxProto) {
        return new LastPublishCtx(publishCtxProto.getPacketId());
    }

    public static QueueProtos.LastPublishCtxProto createLastPublishCtxProto(int packetId) {
        return QueueProtos.LastPublishCtxProto.newBuilder()
                .setPacketId(packetId)
                .build();
    }
}
