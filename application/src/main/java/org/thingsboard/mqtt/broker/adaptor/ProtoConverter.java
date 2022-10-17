/**
 * Copyright © 2016-2022 The Thingsboard Authors
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
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttProperties.UserProperties;
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
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsg;
import org.thingsboard.mqtt.broker.service.subscription.SubscriptionOptions;
import org.thingsboard.mqtt.broker.service.subscription.TopicSubscription;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public class ProtoConverter {

    public static QueueProtos.PublishMsgProto convertToPublishProtoMessage(SessionInfo sessionInfo, PublishMsg publishMsg) {
        QueueProtos.SessionInfoProto sessionInfoProto = convertToSessionInfoProto(sessionInfo);
        UserProperties userProperties = getUserProperties(publishMsg.getProperties());
        List<QueueProtos.UserPropertyProto> userPropertyProtos = toUserPropertyProtos(userProperties);
        return QueueProtos.PublishMsgProto.newBuilder()
                .setPacketId(publishMsg.getPacketId())
                .setTopicName(publishMsg.getTopicName())
                .setQos(publishMsg.getQosLevel())
                .setRetain(publishMsg.isRetained())
                .setPayload(ByteString.copyFrom(publishMsg.getPayload()))
                .addAllUserProperties(userPropertyProtos)
                .setSessionInfo(sessionInfoProto)
                .build();
    }

    private static List<QueueProtos.UserPropertyProto> toUserPropertyProtos(UserProperties userProperties) {
        if (userProperties != null) {
            return userProperties.value().stream().map(ProtoConverter::buildUserPropertyProto).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private static QueueProtos.UserPropertyProto buildUserPropertyProto(MqttProperties.StringPair stringPair) {
        return QueueProtos.UserPropertyProto.newBuilder().setKey(stringPair.key).setValue(stringPair.value).build();
    }

    private static UserProperties getUserProperties(MqttProperties properties) {
        return (UserProperties) properties.getProperty(MqttProperties.MqttPropertyType.USER_PROPERTY.value());
    }

    public static QueueProtos.PublishMsgProto convertToPublishProtoMessage(DevicePublishMsg devicePublishMsg) {
        UserProperties userProperties = getUserProperties(devicePublishMsg.getProperties());
        List<QueueProtos.UserPropertyProto> userPropertyProtos = toUserPropertyProtos(userProperties);
        return QueueProtos.PublishMsgProto.newBuilder()
                .setPacketId(devicePublishMsg.getPacketId())
                .setTopicName(devicePublishMsg.getTopic())
                .setQos(devicePublishMsg.getQos())
                .setPayload(ByteString.copyFrom(devicePublishMsg.getPayload()))
                .addAllUserProperties(userPropertyProtos)
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
                .properties(createMqttProperties(publishMsgProto.getUserPropertiesList()))
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
                        .setOptions(prepareOptionsProto(topicSubscription))
                        .build())
                .collect(Collectors.toList());
        return QueueProtos.ClientSubscriptionsProto.newBuilder().addAllSubscriptions(topicSubscriptionsProto).build();
    }

    private static QueueProtos.SubscriptionOptionsProto prepareOptionsProto(TopicSubscription topicSubscription) {
        return QueueProtos.SubscriptionOptionsProto.newBuilder()
                .setNoLocal(topicSubscription.getOptions().isNoLocal())
                .setRetainAsPublish(topicSubscription.getOptions().isRetainAsPublish())
                .setRetainHandling(getRetainHandling(topicSubscription))
                .build();
    }

    private static QueueProtos.RetainHandling getRetainHandling(TopicSubscription topicSubscription) {
        return QueueProtos.RetainHandling.forNumber(topicSubscription.getOptions().getRetainHandling().value());
    }

    public static Set<TopicSubscription> convertToClientSubscriptions(QueueProtos.ClientSubscriptionsProto clientSubscriptionsProto) {
        return clientSubscriptionsProto.getSubscriptionsList().stream()
                .map(topicSubscriptionProto -> TopicSubscription.builder()
                        .qos(topicSubscriptionProto.getQos())
                        .topic(topicSubscriptionProto.getTopic())
                        .options(createOptions(topicSubscriptionProto))
                        .build())
                .collect(Collectors.toSet());
    }

    private static SubscriptionOptions createOptions(QueueProtos.TopicSubscriptionProto topicSubscriptionProto) {
        return new SubscriptionOptions(
                topicSubscriptionProto.getOptions().getNoLocal(),
                topicSubscriptionProto.getOptions().getRetainAsPublish(),
                getRetainHandling(topicSubscriptionProto));
    }

    private static SubscriptionOptions.RetainHandlingPolicy getRetainHandling(QueueProtos.TopicSubscriptionProto topicSubscriptionProto) {
        return SubscriptionOptions.RetainHandlingPolicy.valueOf(topicSubscriptionProto.getOptions().getRetainHandling().getNumber());
    }

    public static QueueProtos.DisconnectClientCommandProto createDisconnectClientCommandProto(UUID sessionId, boolean isNewSessionPersistent) {
        return QueueProtos.DisconnectClientCommandProto.newBuilder()
                .setSessionIdMSB(sessionId.getMostSignificantBits())
                .setSessionIdLSB(sessionId.getLeastSignificantBits())
                .setIsNewSessionPersistent(isNewSessionPersistent)
                .build();
    }

    public static DevicePublishMsg toDevicePublishMsg(String clientId, QueueProtos.PublishMsgProto publishMsgProto) {
        return DevicePublishMsg.builder()
                .clientId(clientId)
                .topic(publishMsgProto.getTopicName())
                .qos(publishMsgProto.getQos())
                .payload(publishMsgProto.getPayload().toByteArray())
                .properties(createMqttProperties(publishMsgProto.getUserPropertiesList()))
                .build();
    }

    public static QueueProtos.DevicePublishMsgProto toDevicePublishMsgProto(DevicePublishMsg devicePublishMsg) {
        UserProperties userProperties = getUserProperties(devicePublishMsg.getProperties());
        List<QueueProtos.UserPropertyProto> userPropertyProtos = toUserPropertyProtos(userProperties);
        return QueueProtos.DevicePublishMsgProto.newBuilder()
                .setSerialNumber(devicePublishMsg.getSerialNumber())
                .setTime(devicePublishMsg.getTime())
                .setPacketId(devicePublishMsg.getPacketId())
                .setPayload(ByteString.copyFrom(devicePublishMsg.getPayload()))
                .setQos(devicePublishMsg.getQos())
                .setTopicName(devicePublishMsg.getTopic())
                .setClientId(devicePublishMsg.getClientId())
                .setPacketType(devicePublishMsg.getPacketType().toString())
                .addAllUserProperties(userPropertyProtos)
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
                .properties(createMqttProperties(devicePublishMsgProto.getUserPropertiesList()))
                .build();
    }

    public static ConnectionResponse toConnectionResponse(QueueProtos.ClientSessionEventResponseProto clientSessionEventResponseProto) {
        return ConnectionResponse.builder()
                .success(clientSessionEventResponseProto.getSuccess())
                .isPrevSessionPersistent(clientSessionEventResponseProto.getWasPrevSessionPersistent())
                .build();
    }

    public static QueueProtos.RetainedMsgProto convertToRetainedMsgProto(RetainedMsg retainedMsg) {
        List<QueueProtos.UserPropertyProto> userPropertyProtos = getUserPropertyProtos(retainedMsg);
        return QueueProtos.RetainedMsgProto.newBuilder()
                .setPayload(ByteString.copyFrom(retainedMsg.getPayload()))
                .setQos(retainedMsg.getQosLevel())
                .setTopic(retainedMsg.getTopic())
                .addAllUserProperties(userPropertyProtos)
                .build();
    }

    private static List<QueueProtos.UserPropertyProto> getUserPropertyProtos(RetainedMsg retainedMsg) {
        MqttProperties properties = retainedMsg.getProperties();
        if (properties != null && !properties.isEmpty()) {
            UserProperties userProperties = getUserProperties(properties);
            return toUserPropertyProtos(userProperties);
        }
        return Collections.emptyList();
    }

    public static RetainedMsg convertToRetainedMsg(QueueProtos.RetainedMsgProto retainedMsgProto) {
        return new RetainedMsg(
                retainedMsgProto.getTopic(),
                retainedMsgProto.getPayload().toByteArray(),
                retainedMsgProto.getQos(),
                createMqttProperties(retainedMsgProto.getUserPropertiesList())
        );
    }

    public static MqttProperties createMqttProperties(List<QueueProtos.UserPropertyProto> userPropertiesList) {
        MqttProperties mqttProperties = new MqttProperties();
        UserProperties userProperties = createUserProperties(userPropertiesList);
        mqttProperties.add(userProperties);
        return mqttProperties;
    }

    private static UserProperties createUserProperties(List<QueueProtos.UserPropertyProto> userPropertiesList) {
        UserProperties userProperties = new UserProperties();
        userPropertiesList.forEach(userPropertyProto -> userProperties.add(userPropertyProto.getKey(), userPropertyProto.getValue()));
        return userProperties;
    }
}
