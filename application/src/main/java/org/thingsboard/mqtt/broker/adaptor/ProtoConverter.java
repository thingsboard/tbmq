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
import org.thingsboard.mqtt.broker.common.data.ConnectionInfo;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.data.PersistedPacketType;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.service.mqtt.ClientSession;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ConnectionResponse;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionInfo;
import org.thingsboard.mqtt.broker.service.subscription.TopicSubscription;

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
    public static QueueProtos.PublishMsgProto convertToPublishProtoMessage(DevicePublishMsg devicePublishMsg) {
        return QueueProtos.PublishMsgProto.newBuilder()
                .setPacketId(devicePublishMsg.getPacketId())
                .setTopicName(devicePublishMsg.getTopic())
                .setQos(devicePublishMsg.getQos())
                .setPayload(ByteString.copyFrom(devicePublishMsg.getPayload()))
                .build();
    }
    public static String getClientId(QueueProtos.PublishMsgProto publishMsgProto) {
        return publishMsgProto != null && publishMsgProto.getSessionInfo() != null && publishMsgProto.getSessionInfo().getClientInfo() != null ?
                publishMsgProto.getSessionInfo().getClientInfo().getClientId() : null;
    }

    public static PublishMsg convertToPublishMsg(QueueProtos.PublishMsgProto publishMsgProto) {
        return PublishMsg.builder()
                .packetId(publishMsgProto.getPacketId())
                .topicName(publishMsgProto.getTopicName())
                .qosLevel(publishMsgProto.getQos())
                .isRetained(publishMsgProto.getRetain())
                .payload(publishMsgProto.getPayload().toByteArray())
                .build();
    }

    public static ClientSessionInfo convertToClientSessionInfo(QueueProtos.ClientSessionInfoProto clientSessionProto) {
        ClientSession clientSession = ClientSession.builder()
                .connected(clientSessionProto.getConnected())
                .sessionInfo(convertToSessionInfo(clientSessionProto.getSessionInfo()))
                .build();
        return new ClientSessionInfo(clientSession, clientSessionProto.getLastUpdatedTime());
    }

    public static QueueProtos.ClientSessionInfoProto convertToClientSessionInfoProto(ClientSessionInfo clientSessionInfo) {
        ClientSession clientSession = clientSessionInfo.getClientSession();
        return QueueProtos.ClientSessionInfoProto.newBuilder()
                .setConnected(clientSession.isConnected())
                .setSessionInfo(convertToSessionInfoProto(clientSession.getSessionInfo()))
                .setLastUpdatedTime(clientSessionInfo.getLastUpdateTime())
                .build();
    }

    public static QueueProtos.SessionInfoProto convertToSessionInfoProto(SessionInfo sessionInfo) {
        return QueueProtos.SessionInfoProto.newBuilder()
                .setServiceInfo(QueueProtos.ServiceInfo.newBuilder().setServiceId(sessionInfo.getServiceId()).build())
                .setSessionIdMSB(sessionInfo.getSessionId().getMostSignificantBits())
                .setSessionIdLSB(sessionInfo.getSessionId().getLeastSignificantBits())
                .setPersistent(sessionInfo.isPersistent())
                .setClientInfo(convertToClientInfoProto(sessionInfo.getClientInfo()))
                .setConnectionInfo(convertToConnectionInfoProto(sessionInfo.getConnectionInfo()))
                .build();
    }

    public static SessionInfo convertToSessionInfo(QueueProtos.SessionInfoProto sessionInfoProto) {
        return SessionInfo.builder()
                .serviceId(sessionInfoProto.getServiceInfo() != null ? sessionInfoProto.getServiceInfo().getServiceId() : null)
                .sessionId(new UUID(sessionInfoProto.getSessionIdMSB(), sessionInfoProto.getSessionIdLSB()))
                .persistent(sessionInfoProto.getPersistent())
                .clientInfo(convertToClientInfo(sessionInfoProto.getClientInfo()))
                .connectionInfo(convertToConnectionInfo(sessionInfoProto.getConnectionInfo()))
                .build();
    }

    public static QueueProtos.ClientInfoProto convertToClientInfoProto(ClientInfo clientInfo) {
        return clientInfo != null ? QueueProtos.ClientInfoProto.newBuilder()
                .setClientId(clientInfo.getClientId())
                .setClientType(clientInfo.getType().toString())
                .build() : QueueProtos.ClientInfoProto.getDefaultInstance();
    }

    public static QueueProtos.ConnectionInfoProto convertToConnectionInfoProto(ConnectionInfo connectionInfo) {
        return connectionInfo != null ? QueueProtos.ConnectionInfoProto.newBuilder()
                .setConnectedAt(connectionInfo.getConnectedAt())
                .setDisconnectedAt(connectionInfo.getDisconnectedAt())
                .setKeepAlive(connectionInfo.getKeepAlive())
                .build() : QueueProtos.ConnectionInfoProto.getDefaultInstance();
    }

    public static ClientInfo convertToClientInfo(QueueProtos.ClientInfoProto clientInfoProto) {
        return clientInfoProto != null ? ClientInfo.builder()
                .clientId(clientInfoProto.getClientId())
                .type(ClientType.valueOf(clientInfoProto.getClientType()))
                .build() : null;
    }

    private static ConnectionInfo convertToConnectionInfo(QueueProtos.ConnectionInfoProto connectionInfoProto) {
        return connectionInfoProto != null ? ConnectionInfo.builder()
                .connectedAt(connectionInfoProto.getConnectedAt())
                .disconnectedAt(connectionInfoProto.getDisconnectedAt())
                .keepAlive(connectionInfoProto.getKeepAlive())
                .build() : null;
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

    public static QueueProtos.DisconnectClientCommandProto createDisconnectClientCommandProto(UUID sessionId) {
       return QueueProtos.DisconnectClientCommandProto.newBuilder()
               .setSessionIdMSB(sessionId.getMostSignificantBits())
               .setSessionIdLSB(sessionId.getLeastSignificantBits())
               .build();
    }

    public static DevicePublishMsg toDevicePublishMsg(String clientId, QueueProtos.PublishMsgProto devicePublishMsgProto) {
        return DevicePublishMsg.builder()
                .clientId(clientId)
                .topic(devicePublishMsgProto.getTopicName())
                .qos(devicePublishMsgProto.getQos())
                .payload(devicePublishMsgProto.getPayload().toByteArray())
                .build();
    }

    public static QueueProtos.DevicePublishMsgProto toDevicePublishMsgProto(DevicePublishMsg devicePublishMsg) {
        return QueueProtos.DevicePublishMsgProto.newBuilder()
                .setSerialNumber(devicePublishMsg.getSerialNumber())
                .setTime(devicePublishMsg.getTime())
                .setPacketId(devicePublishMsg.getPacketId())
                .setPayload(ByteString.copyFrom(devicePublishMsg.getPayload()))
                .setQos(devicePublishMsg.getQos())
                .setTopicName(devicePublishMsg.getTopic())
                .setClientId(devicePublishMsg.getClientId())
                .setPacketType(devicePublishMsg.getPacketType().toString())
                .build();
    }

    public static DevicePublishMsg toDevicePublishMsg(QueueProtos.DevicePublishMsgProto devicePublishMsgProto) {
        return DevicePublishMsg.builder()
                .serialNumber(devicePublishMsgProto.getSerialNumber())
                .time(devicePublishMsgProto.getTime())
                .packetId(devicePublishMsgProto.getPacketId())
                .payload(devicePublishMsgProto.getPayload().toByteArray())
                .qos(devicePublishMsgProto.getQos())
                .topic(devicePublishMsgProto.getTopicName())
                .clientId(devicePublishMsgProto.getClientId())
                .packetType(PersistedPacketType.valueOf(devicePublishMsgProto.getPacketType()))
                .build();
    }

    public static ConnectionResponse toConnectionResponse(QueueProtos.ClientSessionEventResponseProto clientSessionEventResponseProto) {
        return ConnectionResponse.builder()
                .success(clientSessionEventResponseProto.getSuccess())
                .isPrevSessionPersistent(clientSessionEventResponseProto.getWasPrevSessionPersistent())
                .build();
    }
}
