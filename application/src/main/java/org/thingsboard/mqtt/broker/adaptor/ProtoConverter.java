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
import io.netty.handler.codec.mqtt.MqttProperties.IntegerProperty;
import io.netty.handler.codec.mqtt.MqttProperties.StringProperty;
import io.netty.handler.codec.mqtt.MqttProperties.UserProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.ConnectionInfo;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.data.PersistedPacketType;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.data.subscription.SubscriptionOptions;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.common.util.BrokerConstants;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgHeaders;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ConnectionResponse;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsg;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.util.ClientSessionInfoFactory;
import org.thingsboard.mqtt.broker.util.MqttPropertiesUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public class ProtoConverter {

    /**
     * PUBLISH messages conversion
     */

    public static QueueProtos.PublishMsgProto convertToPublishMsgProto(SessionInfo sessionInfo, PublishMsg publishMsg) {
        UserProperties userProperties = MqttPropertiesUtil.getUserProperties(publishMsg.getProperties());
        QueueProtos.PublishMsgProto.Builder builder = QueueProtos.PublishMsgProto.newBuilder()
                .setPacketId(publishMsg.getPacketId())
                .setTopicName(publishMsg.getTopicName())
                .setQos(publishMsg.getQosLevel())
                .setRetain(publishMsg.isRetained())
                .addAllUserProperties(toUserPropertyProtos(userProperties))
                .setClientId(sessionInfo.getClientInfo().getClientId());
        if (publishMsg.getByteBuf() != null) {
            builder.setPayload(ByteString.copyFrom(publishMsg.getByteBuf().nioBuffer()));
            publishMsg.getByteBuf().release();
        } else {
            builder.setPayload(ByteString.copyFrom(publishMsg.getPayload()));
        }

        QueueProtos.MqttPropertiesProto.Builder mqttPropsProtoBuilder = getMqttPropsProtoBuilder(publishMsg.getProperties());
        if (mqttPropsProtoBuilder != null) {
            builder.setMqttProperties(mqttPropsProtoBuilder);
        }

        return builder.build();
    }

    public static QueueProtos.PublishMsgProto convertToPublishMsgProto(DevicePublishMsg devicePublishMsg) {
        UserProperties userProperties = MqttPropertiesUtil.getUserProperties(devicePublishMsg.getProperties());
        QueueProtos.PublishMsgProto.Builder builder = QueueProtos.PublishMsgProto.newBuilder()
                .setPacketId(devicePublishMsg.getPacketId())
                .setTopicName(devicePublishMsg.getTopic())
                .setQos(devicePublishMsg.getQos())
                .setPayload(ByteString.copyFrom(devicePublishMsg.getPayload()))
                .addAllUserProperties(toUserPropertyProtos(userProperties))
                .setRetain(devicePublishMsg.isRetained());

        QueueProtos.MqttPropertiesProto.Builder mqttPropsProtoBuilder = getMqttPropsProtoBuilder(devicePublishMsg.getProperties());
        if (mqttPropsProtoBuilder != null) {
            builder.setMqttProperties(mqttPropsProtoBuilder);
        }

        return builder.build();
    }

    public static PublishMsg convertToPublishMsg(QueueProtos.PublishMsgProto msg, int packetId,
                                                 int qos, boolean isDup) {
        return convertProtoToPublishMsg(msg, packetId, qos, isDup);
    }

    public static PublishMsg convertToPublishMsg(ClientSessionCtx clientSessionCtx, QueueProtos.PublishMsgProto msg) {
        return convertProtoToPublishMsg(msg, clientSessionCtx.getMsgIdSeq().nextMsgId(), msg.getQos(), false);
    }

    private static PublishMsg convertProtoToPublishMsg(QueueProtos.PublishMsgProto msg, int packetId, int qos, boolean isDup) {
        MqttProperties properties = createMqttPropertiesWithUserPropsIfPresent(msg.getUserPropertiesList());
        if (msg.hasMqttProperties()) {
            addFromProtoToMqttProperties(msg.getMqttProperties(), properties);
        }
        return PublishMsg.builder()
                .topicName(msg.getTopicName())
                .isRetained(msg.getRetain())
                .payload(msg.getPayload().toByteArray())
                .properties(properties)
                .packetId(packetId)
                .qosLevel(qos)
                .isDup(isDup)
                .build();
    }

    public static DevicePublishMsg protoToDevicePublishMsg(String clientId, QueueProtos.PublishMsgProto publishMsgProto, TbQueueMsgHeaders headers) {
        MqttProperties mqttProperties = createMqttPropertiesWithUserPropsIfPresent(publishMsgProto.getUserPropertiesList());
        if (publishMsgProto.hasMqttProperties()) {
            addFromProtoToMqttProperties(publishMsgProto.getMqttProperties(), mqttProperties);
        }

        MqttPropertiesUtil.addMsgExpiryIntervalToProps(mqttProperties, headers);
        return DevicePublishMsg.builder()
                .clientId(clientId)
                .topic(publishMsgProto.getTopicName())
                .qos(publishMsgProto.getQos())
                .payload(publishMsgProto.getPayload().toByteArray())
                .properties(mqttProperties)
                .isRetained(publishMsgProto.getRetain())
                .packetId(BrokerConstants.BLANK_PACKET_ID)
                .serialNumber(BrokerConstants.BLANK_SERIAL_NUMBER)
                .packetType(PersistedPacketType.PUBLISH)
                .time(System.currentTimeMillis())
                .build();
    }

    public static QueueProtos.DevicePublishMsgProto toDevicePublishMsgProto(DevicePublishMsg devicePublishMsg) {
        UserProperties userProperties = MqttPropertiesUtil.getUserProperties(devicePublishMsg.getProperties());
        QueueProtos.DevicePublishMsgProto.Builder builder = QueueProtos.DevicePublishMsgProto.newBuilder()
                .setSerialNumber(devicePublishMsg.getSerialNumber())
                .setTime(devicePublishMsg.getTime())
                .setPacketId(devicePublishMsg.getPacketId())
                .setPayload(ByteString.copyFrom(devicePublishMsg.getPayload()))
                .setQos(devicePublishMsg.getQos())
                .setTopicName(devicePublishMsg.getTopic())
                .setClientId(devicePublishMsg.getClientId())
                .setPacketType(devicePublishMsg.getPacketType().toString())
                .addAllUserProperties(toUserPropertyProtos(userProperties))
                .setRetain(devicePublishMsg.isRetained());

        QueueProtos.MqttPropertiesProto.Builder mqttPropsProtoBuilder = getMqttPropsProtoBuilder(devicePublishMsg.getProperties());
        if (mqttPropsProtoBuilder != null) {
            builder.setMqttProperties(mqttPropsProtoBuilder);
        }

        return builder.build();
    }

    public static DevicePublishMsg protoToDevicePublishMsg(QueueProtos.DevicePublishMsgProto devicePublishMsgProto) {
        MqttProperties properties = createMqttPropertiesWithUserPropsIfPresent(devicePublishMsgProto.getUserPropertiesList());
        if (devicePublishMsgProto.hasMqttProperties()) {
            addFromProtoToMqttProperties(devicePublishMsgProto.getMqttProperties(), properties);
        }

        return DevicePublishMsg.builder()
                .serialNumber(devicePublishMsgProto.getSerialNumber())
                .time(devicePublishMsgProto.getTime())
                .packetId(devicePublishMsgProto.getPacketId())
                .payload(devicePublishMsgProto.getPayload().toByteArray())
                .qos(devicePublishMsgProto.getQos())
                .topic(devicePublishMsgProto.getTopicName())
                .clientId(devicePublishMsgProto.getClientId())
                .packetType(PersistedPacketType.valueOf(devicePublishMsgProto.getPacketType()))
                .properties(properties)
                .isRetained(devicePublishMsgProto.getRetain())
                .build();
    }

    /**
     * Client sessions conversion
     */

    public static ClientSessionInfo convertToClientSessionInfo(QueueProtos.ClientSessionInfoProto clientSessionProto) {
        return ClientSessionInfo.builder()
                .connected(clientSessionProto.getConnected())
                .serviceId(clientSessionProto.getSessionInfo().getServiceInfo().getServiceId())
                .sessionId(new UUID(clientSessionProto.getSessionInfo().getSessionIdMSB(), clientSessionProto.getSessionInfo().getSessionIdLSB()))
                .cleanStart(clientSessionProto.getSessionInfo().getCleanStart())
                .sessionExpiryInterval(clientSessionProto.getSessionInfo().getSessionExpiryInterval())
                .clientId(clientSessionProto.getSessionInfo().getClientInfo().getClientId())
                .type(ClientType.valueOf(clientSessionProto.getSessionInfo().getClientInfo().getClientType()))
                .clientIpAdr(clientSessionProto.getSessionInfo().getClientInfo().getClientIpAdr().toByteArray())
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
        return sessionInfo.getSessionExpiryInterval() == -1 ?
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
                .sessionExpiryInterval(sessionInfoProto.hasSessionExpiryInterval() ? sessionInfoProto.getSessionExpiryInterval() : -1)
                .build();
    }

    public static QueueProtos.ClientInfoProto convertToClientInfoProto(ClientInfo clientInfo) {
        return clientInfo != null ? QueueProtos.ClientInfoProto.newBuilder()
                .setClientId(clientInfo.getClientId())
                .setClientType(clientInfo.getType().toString())
                .setClientIpAdr(ByteString.copyFrom(clientInfo.getClientIpAdr()))
                .build() : QueueProtos.ClientInfoProto.getDefaultInstance();
    }

    public static ClientInfo convertToClientInfo(QueueProtos.ClientInfoProto clientInfoProto) {
        return clientInfoProto != null ? ClientInfo.builder()
                .clientId(clientInfoProto.getClientId())
                .type(ClientType.valueOf(clientInfoProto.getClientType()))
                .clientIpAdr(clientInfoProto.getClientIpAdr().toByteArray())
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

    public static QueueProtos.DisconnectClientCommandProto createDisconnectClientCommandProto(UUID sessionId, boolean newSessionCleanStart) {
        return QueueProtos.DisconnectClientCommandProto.newBuilder()
                .setSessionIdMSB(sessionId.getMostSignificantBits())
                .setSessionIdLSB(sessionId.getLeastSignificantBits())
                .setNewSessionCleanStart(newSessionCleanStart)
                .build();
    }

    public static ConnectionResponse toConnectionResponse(QueueProtos.ClientSessionEventResponseProto clientSessionEventResponseProto) {
        return ConnectionResponse.builder()
                .success(clientSessionEventResponseProto.getSuccess())
                .sessionPresent(clientSessionEventResponseProto.getSessionPresent())
                .build();
    }

    /**
     * Client subscriptions conversion
     */

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

    public static Set<TopicSubscription> convertProtoToClientSubscriptions(QueueProtos.ClientSubscriptionsProto clientSubscriptionsProto) {
        return clientSubscriptionsProto.getSubscriptionsList().stream()
                .map(topicSubscriptionProto -> TopicSubscription.builder()
                        .qos(topicSubscriptionProto.getQos())
                        .topicFilter(topicSubscriptionProto.getTopic())
                        .shareName(topicSubscriptionProto.hasShareName() ? topicSubscriptionProto.getShareName() : null)
                        .options(createSubscriptionOptions(topicSubscriptionProto))
                        .build())
                .collect(Collectors.toSet());
    }

    private static SubscriptionOptions createSubscriptionOptions(QueueProtos.TopicSubscriptionProto topicSubscriptionProto) {
        return new SubscriptionOptions(
                topicSubscriptionProto.getOptions().getNoLocal(),
                topicSubscriptionProto.getOptions().getRetainAsPublish(),
                getRetainHandling(topicSubscriptionProto));
    }

    private static SubscriptionOptions.RetainHandlingPolicy getRetainHandling(QueueProtos.TopicSubscriptionProto topicSubscriptionProto) {
        return SubscriptionOptions.RetainHandlingPolicy.valueOf(topicSubscriptionProto.getOptions().getRetainHandling().getNumber());
    }

    /**
     * Retained messages conversion
     */

    public static RetainedMsg convertProtoToRetainedMsg(QueueProtos.RetainedMsgProto retainedMsgProto) {
        MqttProperties properties = createMqttPropertiesWithUserPropsIfPresent(retainedMsgProto.getUserPropertiesList());
        if (retainedMsgProto.hasMqttProperties()) {
            addFromProtoToMqttProperties(retainedMsgProto.getMqttProperties(), properties);
        }
        return new RetainedMsg(
                retainedMsgProto.getTopic(),
                retainedMsgProto.getPayload().toByteArray(),
                retainedMsgProto.getQos(),
                properties,
                retainedMsgProto.getCreatedTime()
        );
    }

    public static QueueProtos.RetainedMsgProto convertToRetainedMsgProto(RetainedMsg retainedMsg) {
        QueueProtos.RetainedMsgProto.Builder builder = QueueProtos.RetainedMsgProto.newBuilder()
                .setPayload(ByteString.copyFrom(retainedMsg.getPayload()))
                .setQos(retainedMsg.getQosLevel())
                .setTopic(retainedMsg.getTopic())
                .addAllUserProperties(getUserPropertyProtos(retainedMsg.getProperties()))
                .setCreatedTime(retainedMsg.getCreatedTime());
        QueueProtos.MqttPropertiesProto.Builder mqttPropsProtoBuilder = getMqttPropsProtoBuilder(retainedMsg.getProperties());
        if (mqttPropsProtoBuilder != null) {
            builder.setMqttProperties(mqttPropsProtoBuilder);
        }
        return builder.build();
    }

    /**
     * MQTT properties conversion
     */

    private static List<QueueProtos.UserPropertyProto> getUserPropertyProtos(MqttProperties properties) {
        if (properties != null) {
            UserProperties userProperties = MqttPropertiesUtil.getUserProperties(properties);
            return toUserPropertyProtos(userProperties);
        }
        return Collections.emptyList();
    }

    public static MqttProperties createMqttPropertiesWithUserPropsIfPresent(List<QueueProtos.UserPropertyProto> userPropertiesList) {
        MqttProperties mqttProperties = new MqttProperties();
        if (CollectionUtils.isEmpty(userPropertiesList)) {
            return mqttProperties;
        }
        UserProperties userProperties = protoToUserProperties(userPropertiesList);
        mqttProperties.add(userProperties);
        return mqttProperties;
    }

    private static UserProperties protoToUserProperties(List<QueueProtos.UserPropertyProto> userPropertiesList) {
        UserProperties userProperties = new UserProperties();
        for (QueueProtos.UserPropertyProto userPropertyProto : userPropertiesList) {
            userProperties.add(userPropertyProto.getKey(), userPropertyProto.getValue());
        }
        return userProperties;
    }

    private static List<QueueProtos.UserPropertyProto> toUserPropertyProtos(UserProperties userProperties) {
        if (userProperties != null) {
            List<QueueProtos.UserPropertyProto> result = new ArrayList<>(userProperties.value().size());
            for (MqttProperties.StringPair stringPair : userProperties.value()) {
                result.add(buildUserPropertyProto(stringPair));
            }
            return result;
        }
        return Collections.emptyList();
    }

    private static QueueProtos.UserPropertyProto buildUserPropertyProto(MqttProperties.StringPair stringPair) {
        return QueueProtos.UserPropertyProto.newBuilder().setKey(stringPair.key).setValue(stringPair.value).build();
    }

    static QueueProtos.MqttPropertiesProto.Builder getMqttPropsProtoBuilder(MqttProperties properties) {
        Integer payloadFormatIndicator = getPayloadFormatIndicatorFromMqttProperties(properties);
        String contentType = getContentTypeFromMqttProperties(properties);
        String responseTopic = MqttPropertiesUtil.getResponseTopicValue(properties);
        byte[] correlationData = MqttPropertiesUtil.getCorrelationDataValue(properties);
        if (payloadFormatIndicator != null || contentType != null || responseTopic != null || correlationData != null) {
            QueueProtos.MqttPropertiesProto.Builder mqttPropertiesBuilder = QueueProtos.MqttPropertiesProto.newBuilder();
            if (payloadFormatIndicator != null) {
                mqttPropertiesBuilder.setPayloadFormatIndicator(payloadFormatIndicator);
            }
            if (contentType != null) {
                mqttPropertiesBuilder.setContentType(contentType);
            }
            if (responseTopic != null) {
                mqttPropertiesBuilder.setResponseTopic(responseTopic);
            }
            if (correlationData != null) {
                mqttPropertiesBuilder.setCorrelationData(ByteString.copyFrom(correlationData));
            }
            return mqttPropertiesBuilder;
        }
        return null;
    }

    private static Integer getPayloadFormatIndicatorFromMqttProperties(MqttProperties mqttProperties) {
        IntegerProperty payloadFormatProperty = MqttPropertiesUtil.getPayloadFormatProperty(mqttProperties);
        return payloadFormatProperty == null ? null : payloadFormatProperty.value();
    }

    private static String getContentTypeFromMqttProperties(MqttProperties mqttProperties) {
        StringProperty contentTypeProperty = MqttPropertiesUtil.getContentTypeProperty(mqttProperties);
        return contentTypeProperty == null ? null : contentTypeProperty.value();
    }

    public static void addFromProtoToMqttProperties(QueueProtos.MqttPropertiesProto mqttPropertiesProto, MqttProperties properties) {
        if (mqttPropertiesProto.hasPayloadFormatIndicator()) {
            MqttPropertiesUtil.addPayloadFormatIndicatorToProps(properties, mqttPropertiesProto.getPayloadFormatIndicator());
        }
        if (mqttPropertiesProto.hasContentType()) {
            MqttPropertiesUtil.addContentTypeToProps(properties, mqttPropertiesProto.getContentType());
        }
        if (mqttPropertiesProto.hasResponseTopic()) {
            MqttPropertiesUtil.addResponseTopicToProps(properties, mqttPropertiesProto.getResponseTopic());
        }
        if (mqttPropertiesProto.hasCorrelationData()) {
            MqttPropertiesUtil.addCorrelationDataToProps(properties, mqttPropertiesProto.getCorrelationData().toByteArray());
        }
    }

    /**
     * Helper methods
     */

    public static String getClientId(QueueProtos.PublishMsgProto publishMsgProto) {
        return publishMsgProto != null ? publishMsgProto.getClientId() : null;
    }
}
