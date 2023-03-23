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
package org.thingsboard.mqtt.broker.adaptor;

import com.google.protobuf.ByteString;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttProperties.UserProperties;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.ConnectionInfo;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.data.PersistedPacketType;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ConnectionResponse;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsg;
import org.thingsboard.mqtt.broker.service.subscription.SubscriptionOptions;
import org.thingsboard.mqtt.broker.service.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.util.ClientSessionInfoFactory;

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
                .setRetain(devicePublishMsg.isRetained())
                .build();
    }

    public static String getClientId(QueueProtos.PublishMsgProto publishMsgProto) {
        return publishMsgProto != null ? publishMsgProto.getSessionInfo().getClientInfo().getClientId() : null;
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
        return ClientSessionInfo.builder()
                .connected(clientSessionProto.getConnected())
                .serviceId(clientSessionProto.getSessionInfo().getServiceInfo().getServiceId())
                .sessionId(new UUID(clientSessionProto.getSessionInfo().getSessionIdMSB(), clientSessionProto.getSessionInfo().getSessionIdLSB()))
                .cleanStart(clientSessionProto.getSessionInfo().getCleanStart())
                .sessionExpiryInterval(clientSessionProto.getSessionInfo().getSessionExpiryInterval())
                .clientId(clientSessionProto.getSessionInfo().getClientInfo().getClientId())
                .type(ClientType.valueOf(clientSessionProto.getSessionInfo().getClientInfo().getClientType()))
                .clientIpAdr(clientSessionProto.getSessionInfo().getClientInfo().getClientIpAdr())
                .connectedAt(clientSessionProto.getSessionInfo().getConnectionInfo().getConnectedAt())
                .disconnectedAt(clientSessionProto.getSessionInfo().getConnectionInfo().getDisconnectedAt())
                .keepAlive(clientSessionProto.getSessionInfo().getConnectionInfo().getKeepAlive())
                .build();
    }

    public static QueueProtos.ClientSessionInfoProto convertToClientSessionInfoProto(ClientSessionInfo clientSessionInfo) {
        return QueueProtos.ClientSessionInfoProto.newBuilder()
                .setConnected(clientSessionInfo.isConnected())
                .setSessionInfo(convertToSessionInfoProto(ClientSessionInfoFactory.clientSessionInfoToSessionInfo(clientSessionInfo)))
                .build();
    }

    public static QueueProtos.SessionInfoProto convertToSessionInfoProto(SessionInfo sessionInfo) {
        return sessionInfo.getSessionExpiryInterval() == null ?
                getSessionInfoProto(sessionInfo) : getSessionInfoProtoWithSessionExpiryInterval(sessionInfo);
    }

    private static QueueProtos.SessionInfoProto getSessionInfoProto(SessionInfo sessionInfo) {
        return QueueProtos.SessionInfoProto.newBuilder()
                .setServiceInfo(QueueProtos.ServiceInfo.newBuilder().setServiceId(sessionInfo.getServiceId()).build())
                .setSessionIdMSB(sessionInfo.getSessionId().getMostSignificantBits())
                .setSessionIdLSB(sessionInfo.getSessionId().getLeastSignificantBits())
                .setCleanStart(sessionInfo.isCleanStart())
                .setClientInfo(convertToClientInfoProto(sessionInfo.getClientInfo()))
                .setConnectionInfo(convertToConnectionInfoProto(sessionInfo.getConnectionInfo()))
                .build();
    }

    private static QueueProtos.SessionInfoProto getSessionInfoProtoWithSessionExpiryInterval(SessionInfo sessionInfo) {
        return QueueProtos.SessionInfoProto.newBuilder()
                .setServiceInfo(QueueProtos.ServiceInfo.newBuilder().setServiceId(sessionInfo.getServiceId()).build())
                .setSessionIdMSB(sessionInfo.getSessionId().getMostSignificantBits())
                .setSessionIdLSB(sessionInfo.getSessionId().getLeastSignificantBits())
                .setCleanStart(sessionInfo.isCleanStart())
                .setClientInfo(convertToClientInfoProto(sessionInfo.getClientInfo()))
                .setConnectionInfo(convertToConnectionInfoProto(sessionInfo.getConnectionInfo()))
                .setSessionExpiryInterval(sessionInfo.getSessionExpiryInterval())
                .build();
    }

    public static SessionInfo convertToSessionInfo(QueueProtos.SessionInfoProto sessionInfoProto) {
        return SessionInfo.builder()
                .serviceId(sessionInfoProto.getServiceInfo().getServiceId())
                .sessionId(new UUID(sessionInfoProto.getSessionIdMSB(), sessionInfoProto.getSessionIdLSB()))
                .cleanStart(sessionInfoProto.getCleanStart())
                .clientInfo(convertToClientInfo(sessionInfoProto.getClientInfo()))
                .connectionInfo(convertToConnectionInfo(sessionInfoProto.getConnectionInfo()))
                .sessionExpiryInterval(sessionInfoProto.hasSessionExpiryInterval() ? sessionInfoProto.getSessionExpiryInterval() : null)
                .build();
    }

    public static QueueProtos.ClientInfoProto convertToClientInfoProto(ClientInfo clientInfo) {
        return clientInfo != null ? QueueProtos.ClientInfoProto.newBuilder()
                .setClientId(clientInfo.getClientId())
                .setClientType(clientInfo.getType().toString())
                .setClientIpAdr(clientInfo.getClientIpAdr())
                .build() : QueueProtos.ClientInfoProto.getDefaultInstance();
    }

    public static ClientInfo convertToClientInfo(QueueProtos.ClientInfoProto clientInfoProto) {
        return clientInfoProto != null ? ClientInfo.builder()
                .clientId(clientInfoProto.getClientId())
                .type(ClientType.valueOf(clientInfoProto.getClientType()))
                .clientIpAdr(clientInfoProto.getClientIpAdr())
                .build() : null;
    }

    public static QueueProtos.ConnectionInfoProto convertToConnectionInfoProto(ConnectionInfo connectionInfo) {
        return connectionInfo != null ? QueueProtos.ConnectionInfoProto.newBuilder()
                .setConnectedAt(connectionInfo.getConnectedAt())
                .setDisconnectedAt(connectionInfo.getDisconnectedAt())
                .setKeepAlive(connectionInfo.getKeepAlive())
                .build() : QueueProtos.ConnectionInfoProto.getDefaultInstance();
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
                .map(topicSubscription -> topicSubscription.getShareName() == null ?
                        getTopicSubscriptionProto(topicSubscription) :
                        getTopicSubscriptionProtoWithShareName(topicSubscription))
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

    private static QueueProtos.TopicSubscriptionProto getTopicSubscriptionProto(TopicSubscription topicSubscription) {
        return QueueProtos.TopicSubscriptionProto.newBuilder()
                .setQos(topicSubscription.getQos())
                .setTopic(topicSubscription.getTopicFilter())
                .setOptions(prepareOptionsProto(topicSubscription))
                .build();
    }

    private static QueueProtos.TopicSubscriptionProto getTopicSubscriptionProtoWithShareName(TopicSubscription topicSubscription) {
        return QueueProtos.TopicSubscriptionProto.newBuilder()
                .setQos(topicSubscription.getQos())
                .setTopic(topicSubscription.getTopicFilter())
                .setShareName(topicSubscription.getShareName())
                .setOptions(prepareOptionsProto(topicSubscription))
                .build();
    }

    public static Set<TopicSubscription> convertToClientSubscriptions(QueueProtos.ClientSubscriptionsProto clientSubscriptionsProto) {
        return clientSubscriptionsProto.getSubscriptionsList().stream()
                .map(topicSubscriptionProto -> TopicSubscription.builder()
                        .qos(topicSubscriptionProto.getQos())
                        .topicFilter(topicSubscriptionProto.getTopic())
                        .shareName(topicSubscriptionProto.hasShareName() ? topicSubscriptionProto.getShareName() : null)
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

    public static QueueProtos.DisconnectClientCommandProto createDisconnectClientCommandProto(UUID sessionId, boolean newSessionCleanStart) {
        return QueueProtos.DisconnectClientCommandProto.newBuilder()
                .setSessionIdMSB(sessionId.getMostSignificantBits())
                .setSessionIdLSB(sessionId.getLeastSignificantBits())
                .setNewSessionCleanStart(newSessionCleanStart)
                .build();
    }

    public static DevicePublishMsg toDevicePublishMsg(String clientId, QueueProtos.PublishMsgProto publishMsgProto) {
        return DevicePublishMsg.builder()
                .clientId(clientId)
                .topic(publishMsgProto.getTopicName())
                .qos(publishMsgProto.getQos())
                .payload(publishMsgProto.getPayload().toByteArray())
                .properties(createMqttProperties(publishMsgProto.getUserPropertiesList()))
                .isRetained(publishMsgProto.getRetain())
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
                .setRetain(devicePublishMsg.isRetained())
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
                .isRetained(devicePublishMsgProto.getRetain())
                .build();
    }

    public static ConnectionResponse toConnectionResponse(QueueProtos.ClientSessionEventResponseProto clientSessionEventResponseProto) {
        return ConnectionResponse.builder()
                .success(clientSessionEventResponseProto.getSuccess())
                .sessionPresent(clientSessionEventResponseProto.getSessionPresent())
                .build();
    }

    public static QueueProtos.RetainedMsgProto convertToRetainedMsgProto(RetainedMsg retainedMsg) {
        List<QueueProtos.UserPropertyProto> userPropertyProtos = getUserPropertyProtos(retainedMsg);
        return QueueProtos.RetainedMsgProto.newBuilder()
                .setPayload(ByteString.copyFrom(retainedMsg.getPayload()))
                .setQos(retainedMsg.getQosLevel())
                .setTopic(retainedMsg.getTopic())
                .addAllUserProperties(userPropertyProtos)
                .setCreatedTime(retainedMsg.getCreatedTime())
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
                createMqttProperties(retainedMsgProto.getUserPropertiesList()),
                retainedMsgProto.getCreatedTime()
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
