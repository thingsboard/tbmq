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
package org.thingsboard.mqtt.broker.adaptor;

import com.google.common.collect.Sets;
import com.google.protobuf.ByteString;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttProperties.UserProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.ConnectionInfo;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.data.PersistedPacketType;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.data.subscription.ClientTopicSubscription;
import org.thingsboard.mqtt.broker.common.data.subscription.IntegrationTopicSubscription;
import org.thingsboard.mqtt.broker.common.data.subscription.SubscriptionOptions;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.gen.queue.BlockedClientProto;
import org.thingsboard.mqtt.broker.gen.queue.BlockedClientProto.Builder;
import org.thingsboard.mqtt.broker.gen.queue.BlockedClientTypeProto;
import org.thingsboard.mqtt.broker.gen.queue.ClientInfoProto;
import org.thingsboard.mqtt.broker.gen.queue.ClientSessionEventResponseProto;
import org.thingsboard.mqtt.broker.gen.queue.ClientSessionInfoProto;
import org.thingsboard.mqtt.broker.gen.queue.ClientSubscriptionsProto;
import org.thingsboard.mqtt.broker.gen.queue.ConnectionInfoProto;
import org.thingsboard.mqtt.broker.gen.queue.DevicePublishMsgProto;
import org.thingsboard.mqtt.broker.gen.queue.DisconnectClientCommandProto;
import org.thingsboard.mqtt.broker.gen.queue.MqttPropertiesProto;
import org.thingsboard.mqtt.broker.gen.queue.PublishMsgProto;
import org.thingsboard.mqtt.broker.gen.queue.RegexMatchTargetProto;
import org.thingsboard.mqtt.broker.gen.queue.RetainHandling;
import org.thingsboard.mqtt.broker.gen.queue.RetainedMsgProto;
import org.thingsboard.mqtt.broker.gen.queue.ServiceInfo;
import org.thingsboard.mqtt.broker.gen.queue.SessionInfoProto;
import org.thingsboard.mqtt.broker.gen.queue.SubscriptionOptionsProto;
import org.thingsboard.mqtt.broker.gen.queue.SubscriptionsSourceProto;
import org.thingsboard.mqtt.broker.gen.queue.TopicSubscriptionProto;
import org.thingsboard.mqtt.broker.gen.queue.UserPropertyProto;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgHeaders;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.BlockedClient;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.ClientIdBlockedClient;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.IpAddressBlockedClient;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.RegexBlockedClient;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.RegexMatchTarget;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.UsernameBlockedClient;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ConnectionResponse;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsg;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;
import org.thingsboard.mqtt.broker.service.subscription.data.SourcedSubscriptions;
import org.thingsboard.mqtt.broker.service.subscription.data.SubscriptionsSource;
import org.thingsboard.mqtt.broker.util.ClientSessionInfoFactory;
import org.thingsboard.mqtt.broker.util.MqttPropertiesUtil;
import org.thingsboard.mqtt.broker.util.MqttQosUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
public class ProtoConverter {

    /**
     * PUBLISH messages conversion
     */

    public static PublishMsgProto convertToPublishMsgProto(SessionInfo sessionInfo, PublishMsg publishMsg) {
        UserProperties userProperties = MqttPropertiesUtil.getUserProperties(publishMsg.getProperties());
        PublishMsgProto.Builder builder = PublishMsgProto.newBuilder()
                .setPacketId(publishMsg.getPacketId())
                .setTopicName(publishMsg.getTopicName())
                .setQos(publishMsg.getQos())
                .setRetain(publishMsg.isRetained())
                .addAllUserProperties(toUserPropertyProtos(userProperties))
                .setClientId(sessionInfo.getClientInfo().getClientId());
        builder.setPayload(publishMsg.getByteBuf() != null ?
                ByteString.copyFrom(publishMsg.getByteBuf().nioBuffer()) :
                ByteString.copyFrom(publishMsg.getPayload()));

        MqttPropertiesProto.Builder mqttPropsProtoBuilder = getMqttPropsProtoBuilder(publishMsg.getProperties());
        if (mqttPropsProtoBuilder != null) {
            builder.setMqttProperties(mqttPropsProtoBuilder);
        }

        return builder.build();
    }

    public static PublishMsgProto convertToPublishMsgProto(DevicePublishMsg devicePublishMsg) {
        UserProperties userProperties = MqttPropertiesUtil.getUserProperties(devicePublishMsg.getProperties());
        PublishMsgProto.Builder builder = PublishMsgProto.newBuilder()
                .setPacketId(devicePublishMsg.getPacketId())
                .setTopicName(devicePublishMsg.getTopicName())
                .setQos(devicePublishMsg.getQos())
                .setPayload(ByteString.copyFrom(devicePublishMsg.getPayload()))
                .addAllUserProperties(toUserPropertyProtos(userProperties))
                .setRetain(devicePublishMsg.isRetained());

        MqttPropertiesProto.Builder mqttPropsProtoBuilder = getMqttPropsProtoBuilder(devicePublishMsg.getProperties());
        if (mqttPropsProtoBuilder != null) {
            builder.setMqttProperties(mqttPropsProtoBuilder);
        }

        return builder.build();
    }

    public static PublishMsgProto createReceiverPublishMsg(Subscription subscription, PublishMsgProto publishMsgProto) {
        var minQos = MqttQosUtil.downgradeQos(subscription, publishMsgProto);
        var retain = subscription.getOptions().isRetain(publishMsgProto.getRetain());

        MqttPropertiesProto mqttProperties = updateMqttPropsWithSubsIds(subscription, publishMsgProto);

        return publishMsgProto.toBuilder()
                .setPacketId(0)
                .setQos(minQos)
                .setRetain(retain)
                .setMqttProperties(mqttProperties)
                .build();
    }

    public static PublishMsgProto createReceiverPublishMsg(PublishMsgProto publishMsgProto) {
        return publishMsgProto.toBuilder()
                .setPacketId(0)
                .build();
    }

    public static PublishMsgProto updatePublishMsg(Subscription subscription, PublishMsgProto publishMsgProto) {
        var minQos = MqttQosUtil.downgradeQos(subscription, publishMsgProto);
        var retain = subscription.getOptions().isRetain(publishMsgProto.getRetain());

        if (minQos != publishMsgProto.getQos() || retain != publishMsgProto.getRetain() || subscription.isSubsIdsPresent()) {

            MqttPropertiesProto mqttProperties = updateMqttPropsWithSubsIds(subscription, publishMsgProto);

            return publishMsgProto.toBuilder()
                    .setQos(minQos)
                    .setRetain(retain)
                    .setMqttProperties(mqttProperties)
                    .build();
        } else {
            return publishMsgProto;
        }
    }

    private static MqttPropertiesProto updateMqttPropsWithSubsIds(Subscription subscription, PublishMsgProto publishMsgProto) {
        MqttPropertiesProto mqttProperties = publishMsgProto.getMqttProperties();
        if (subscription.isSubsIdsPresent()) {
            MqttPropertiesProto.Builder mqttPropertiesBuilder = mqttProperties.toBuilder();
            mqttPropertiesBuilder.addAllSubscriptionIds(subscription.getSubscriptionIds());
            mqttProperties = mqttPropertiesBuilder.build();
        }
        return mqttProperties;
    }

    public static PublishMsg convertToPublishMsg(PublishMsgProto msg, int packetId,
                                                 int qos, boolean isDup, int subscriptionId) {
        MqttProperties properties = createMqttPropertiesWithUserPropsIfPresent(msg.getUserPropertiesList());
        if (msg.hasMqttProperties()) {
            addFromProtoToMqttProperties(msg.getMqttProperties(), properties);
        }
        MqttPropertiesUtil.addSubscriptionIdToProps(properties, subscriptionId);
        return PublishMsg.builder()
                .topicName(msg.getTopicName())
                .isRetained(msg.getRetain())
                .payload(msg.getPayload().toByteArray())
                .properties(properties)
                .packetId(packetId)
                .qos(qos)
                .isDup(isDup)
                .build();
    }

    public static DevicePublishMsg protoToDevicePublishMsg(String clientId, PublishMsgProto publishMsgProto, TbQueueMsgHeaders headers) {
        MqttProperties mqttProperties = createMqttPropertiesWithUserPropsIfPresent(publishMsgProto.getUserPropertiesList());
        if (publishMsgProto.hasMqttProperties()) {
            addFromProtoToMqttProperties(publishMsgProto.getMqttProperties(), mqttProperties);
        }

        MqttPropertiesUtil.addMsgExpiryIntervalToProps(mqttProperties, headers);
        return DevicePublishMsg.builder()
                .clientId(clientId)
                .topicName(publishMsgProto.getTopicName())
                .qos(publishMsgProto.getQos())
                .payload(publishMsgProto.getPayload().toByteArray())
                .properties(mqttProperties)
                .isRetained(publishMsgProto.getRetain())
                .packetId(BrokerConstants.BLANK_PACKET_ID)
                .packetType(PersistedPacketType.PUBLISH)
                .time(System.currentTimeMillis())
                .build();
    }

    public static DevicePublishMsgProto toDevicePublishMsgProto(DevicePublishMsg devicePublishMsg) {
        UserProperties userProperties = MqttPropertiesUtil.getUserProperties(devicePublishMsg.getProperties());
        DevicePublishMsgProto.Builder builder = DevicePublishMsgProto.newBuilder()
                .setTime(devicePublishMsg.getTime())
                .setPacketId(devicePublishMsg.getPacketId())
                .setPayload(ByteString.copyFrom(devicePublishMsg.getPayload()))
                .setQos(devicePublishMsg.getQos())
                .setTopicName(devicePublishMsg.getTopicName())
                .setClientId(devicePublishMsg.getClientId())
                .setPacketType(devicePublishMsg.getPacketType().toString())
                .addAllUserProperties(toUserPropertyProtos(userProperties))
                .setRetain(devicePublishMsg.isRetained());

        MqttPropertiesProto.Builder mqttPropsProtoBuilder = getMqttPropsProtoBuilder(devicePublishMsg.getProperties());
        if (mqttPropsProtoBuilder != null) {
            builder.setMqttProperties(mqttPropsProtoBuilder);
        }

        return builder.build();
    }

    public static DevicePublishMsg protoToDevicePublishMsg(DevicePublishMsgProto devicePublishMsgProto) {
        MqttProperties properties = createMqttPropertiesWithUserPropsIfPresent(devicePublishMsgProto.getUserPropertiesList());
        if (devicePublishMsgProto.hasMqttProperties()) {
            addFromProtoToMqttProperties(devicePublishMsgProto.getMqttProperties(), properties);
        }

        return DevicePublishMsg.builder()
                .time(devicePublishMsgProto.getTime())
                .packetId(devicePublishMsgProto.getPacketId())
                .payload(devicePublishMsgProto.getPayload().toByteArray())
                .qos(devicePublishMsgProto.getQos())
                .topicName(devicePublishMsgProto.getTopicName())
                .clientId(devicePublishMsgProto.getClientId())
                .packetType(PersistedPacketType.valueOf(devicePublishMsgProto.getPacketType()))
                .properties(properties)
                .isRetained(devicePublishMsgProto.getRetain())
                .build();
    }

    /**
     * Client sessions conversion
     */

    public static ClientSessionInfo convertToClientSessionInfo(ClientSessionInfoProto clientSessionProto) {
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

    public static ClientSessionInfoProto convertToClientSessionInfoProto(ClientSessionInfo clientSessionInfo) {
        return ClientSessionInfoProto.newBuilder()
                .setConnected(clientSessionInfo.isConnected())
                .setSessionInfo(convertToSessionInfoProto(ClientSessionInfoFactory.clientSessionInfoToSessionInfo(clientSessionInfo)))
                .build();
    }

    public static SessionInfoProto convertToSessionInfoProto(SessionInfo sessionInfo) {
        return sessionInfo.getSessionExpiryInterval() == -1 ?
                getSessionInfoProto(sessionInfo) : getSessionInfoProtoWithSessionExpiryInterval(sessionInfo);
    }

    public static SessionInfoProto convertToSessionInfoProto(ClientSessionInfo clientSessionInfo) {
        return clientSessionInfo.getSessionExpiryInterval() == -1 ?
                getSessionInfoProto(clientSessionInfo) : getSessionInfoProtoWithSessionExpiryInterval(clientSessionInfo);
    }

    private static SessionInfoProto getSessionInfoProto(SessionInfo sessionInfo) {
        return SessionInfoProto.newBuilder()
                .setServiceInfo(ServiceInfo.newBuilder().setServiceId(sessionInfo.getServiceId()).build())
                .setSessionIdMSB(sessionInfo.getSessionId().getMostSignificantBits())
                .setSessionIdLSB(sessionInfo.getSessionId().getLeastSignificantBits())
                .setCleanStart(sessionInfo.isCleanStart())
                .setClientInfo(convertToClientInfoProto(sessionInfo.getClientInfo()))
                .setConnectionInfo(convertToConnectionInfoProto(sessionInfo.getConnectionInfo()))
                .build();
    }

    private static SessionInfoProto getSessionInfoProto(ClientSessionInfo clientSessionInfo) {
        return SessionInfoProto.newBuilder()
                .setServiceInfo(ServiceInfo.newBuilder().setServiceId(clientSessionInfo.getServiceId()).build())
                .setSessionIdMSB(clientSessionInfo.getSessionId().getMostSignificantBits())
                .setSessionIdLSB(clientSessionInfo.getSessionId().getLeastSignificantBits())
                .setCleanStart(clientSessionInfo.isCleanStart())
                .setClientInfo(convertToClientInfoProto(clientSessionInfo))
                .setConnectionInfo(convertToConnectionInfoProto(clientSessionInfo))
                .build();
    }

    private static SessionInfoProto getSessionInfoProtoWithSessionExpiryInterval(SessionInfo sessionInfo) {
        return SessionInfoProto.newBuilder()
                .setServiceInfo(ServiceInfo.newBuilder().setServiceId(sessionInfo.getServiceId()).build())
                .setSessionIdMSB(sessionInfo.getSessionId().getMostSignificantBits())
                .setSessionIdLSB(sessionInfo.getSessionId().getLeastSignificantBits())
                .setCleanStart(sessionInfo.isCleanStart())
                .setClientInfo(convertToClientInfoProto(sessionInfo.getClientInfo()))
                .setConnectionInfo(convertToConnectionInfoProto(sessionInfo.getConnectionInfo()))
                .setSessionExpiryInterval(sessionInfo.getSessionExpiryInterval())
                .build();
    }

    private static SessionInfoProto getSessionInfoProtoWithSessionExpiryInterval(ClientSessionInfo clientSessionInfo) {
        return SessionInfoProto.newBuilder()
                .setServiceInfo(ServiceInfo.newBuilder().setServiceId(clientSessionInfo.getServiceId()).build())
                .setSessionIdMSB(clientSessionInfo.getSessionId().getMostSignificantBits())
                .setSessionIdLSB(clientSessionInfo.getSessionId().getLeastSignificantBits())
                .setCleanStart(clientSessionInfo.isCleanStart())
                .setClientInfo(convertToClientInfoProto(clientSessionInfo))
                .setConnectionInfo(convertToConnectionInfoProto(clientSessionInfo))
                .setSessionExpiryInterval(clientSessionInfo.getSessionExpiryInterval())
                .build();
    }

    public static SessionInfo convertToSessionInfo(SessionInfoProto sessionInfoProto) {
        return SessionInfo.builder()
                .serviceId(sessionInfoProto.getServiceInfo().getServiceId())
                .sessionId(new UUID(sessionInfoProto.getSessionIdMSB(), sessionInfoProto.getSessionIdLSB()))
                .cleanStart(sessionInfoProto.getCleanStart())
                .clientInfo(convertToClientInfo(sessionInfoProto.getClientInfo()))
                .connectionInfo(convertToConnectionInfo(sessionInfoProto.getConnectionInfo()))
                .sessionExpiryInterval(sessionInfoProto.hasSessionExpiryInterval() ? sessionInfoProto.getSessionExpiryInterval() : -1)
                .build();
    }

    public static ClientInfoProto convertToClientInfoProto(ClientInfo clientInfo) {
        return clientInfo != null ? ClientInfoProto.newBuilder()
                .setClientId(clientInfo.getClientId())
                .setClientType(clientInfo.getType().toString())
                .setClientIpAdr(ByteString.copyFrom(clientInfo.getClientIpAdr()))
                .build() : ClientInfoProto.getDefaultInstance();
    }

    public static ClientInfoProto convertToClientInfoProto(ClientSessionInfo clientSessionInfo) {
        return clientSessionInfo != null ? ClientInfoProto.newBuilder()
                .setClientId(clientSessionInfo.getClientId())
                .setClientType(clientSessionInfo.getType().toString())
                .setClientIpAdr(ByteString.copyFrom(clientSessionInfo.getClientIpAdr()))
                .build() : ClientInfoProto.getDefaultInstance();
    }

    public static ClientInfo convertToClientInfo(ClientInfoProto clientInfoProto) {
        return clientInfoProto != null ? ClientInfo.builder()
                .clientId(clientInfoProto.getClientId())
                .type(ClientType.valueOf(clientInfoProto.getClientType()))
                .clientIpAdr(clientInfoProto.getClientIpAdr().toByteArray())
                .build() : null;
    }

    public static ConnectionInfoProto convertToConnectionInfoProto(ConnectionInfo connectionInfo) {
        return connectionInfo != null ? ConnectionInfoProto.newBuilder()
                .setConnectedAt(connectionInfo.getConnectedAt())
                .setDisconnectedAt(connectionInfo.getDisconnectedAt())
                .setKeepAlive(connectionInfo.getKeepAlive())
                .build() : ConnectionInfoProto.getDefaultInstance();
    }

    public static ConnectionInfoProto convertToConnectionInfoProto(ClientSessionInfo clientSessionInfo) {
        return clientSessionInfo != null ? ConnectionInfoProto.newBuilder()
                .setConnectedAt(clientSessionInfo.getConnectedAt())
                .setDisconnectedAt(clientSessionInfo.getDisconnectedAt())
                .setKeepAlive(clientSessionInfo.getKeepAlive())
                .build() : ConnectionInfoProto.getDefaultInstance();
    }


    private static ConnectionInfo convertToConnectionInfo(ConnectionInfoProto connectionInfoProto) {
        return connectionInfoProto != null ? ConnectionInfo.builder()
                .connectedAt(connectionInfoProto.getConnectedAt())
                .disconnectedAt(connectionInfoProto.getDisconnectedAt())
                .keepAlive(connectionInfoProto.getKeepAlive())
                .build() : null;
    }

    public static DisconnectClientCommandProto createDisconnectClientCommandProto(UUID sessionId,
                                                                                  boolean newSessionCleanStart,
                                                                                  String reasonType) {
        return DisconnectClientCommandProto.newBuilder()
                .setSessionIdMSB(sessionId.getMostSignificantBits())
                .setSessionIdLSB(sessionId.getLeastSignificantBits())
                .setNewSessionCleanStart(newSessionCleanStart)
                .setReasonType(reasonType)
                .build();
    }

    public static ConnectionResponse toConnectionResponse(ClientSessionEventResponseProto clientSessionEventResponseProto) {
        return ConnectionResponse.builder()
                .success(clientSessionEventResponseProto.getSuccess())
                .sessionPresent(clientSessionEventResponseProto.getSessionPresent())
                .build();
    }

    /**
     * Client subscriptions conversion
     */

    public static ClientSubscriptionsProto convertToClientSubscriptionsProto(Collection<TopicSubscription> topicSubscriptions) {
        List<TopicSubscriptionProto> topicSubscriptionsProto = new ArrayList<>(topicSubscriptions.size());
        SubscriptionsSourceProto source = SubscriptionsSourceProto.MQTT_CLIENT;
        for (TopicSubscription topicSubscription : topicSubscriptions) {
            if (topicSubscription instanceof IntegrationTopicSubscription) {
                source = SubscriptionsSourceProto.INTEGRATION;
            }
            topicSubscriptionsProto.add(topicSubscription.getShareName() == null ?
                    getTopicSubscriptionProto(topicSubscription) :
                    getTopicSubscriptionProtoWithShareName(topicSubscription));
        }
        return ClientSubscriptionsProto.newBuilder().addAllSubscriptions(topicSubscriptionsProto).setSource(source).build();
    }

    private static SubscriptionOptionsProto prepareOptionsProto(TopicSubscription topicSubscription) {
        if (topicSubscription.getOptions() == null) {
            return SubscriptionOptionsProto.getDefaultInstance();
        }
        return SubscriptionOptionsProto.newBuilder()
                .setNoLocal(topicSubscription.getOptions().isNoLocal())
                .setRetainAsPublish(topicSubscription.getOptions().isRetainAsPublish())
                .setRetainHandling(getRetainHandling(topicSubscription))
                .build();
    }

    private static RetainHandling getRetainHandling(TopicSubscription topicSubscription) {
        return RetainHandling.forNumber(topicSubscription.getOptions().getRetainHandling().value());
    }

    private static TopicSubscriptionProto getTopicSubscriptionProto(TopicSubscription topicSubscription) {
        TopicSubscriptionProto.Builder builder = TopicSubscriptionProto.newBuilder()
                .setQos(topicSubscription.getQos())
                .setTopic(topicSubscription.getTopicFilter())
                .setOptions(prepareOptionsProto(topicSubscription));
        setSubscriptionIdIfPresent(topicSubscription, builder);
        return builder.build();
    }

    private static TopicSubscriptionProto getTopicSubscriptionProtoWithShareName(TopicSubscription topicSubscription) {
        TopicSubscriptionProto.Builder builder = TopicSubscriptionProto.newBuilder()
                .setQos(topicSubscription.getQos())
                .setTopic(topicSubscription.getTopicFilter())
                .setShareName(topicSubscription.getShareName())
                .setOptions(prepareOptionsProto(topicSubscription));
        setSubscriptionIdIfPresent(topicSubscription, builder);
        return builder.build();
    }

    private static void setSubscriptionIdIfPresent(TopicSubscription topicSubscription, TopicSubscriptionProto.Builder builder) {
        if (topicSubscription.getSubscriptionId() != -1) {
            builder.setSubscriptionId(topicSubscription.getSubscriptionId());
        }
    }

    public static SourcedSubscriptions convertProtoToClientSubscriptions(ClientSubscriptionsProto clientSubscriptionsProto) {
        SubscriptionsSource source = getSubscriptionsSource(clientSubscriptionsProto);
        Set<TopicSubscription> subscriptions = Sets.newHashSetWithExpectedSize(clientSubscriptionsProto.getSubscriptionsCount());
        for (TopicSubscriptionProto topicSubscriptionProto : clientSubscriptionsProto.getSubscriptionsList()) {
            TopicSubscription subscription;
            if (SubscriptionsSource.INTEGRATION.equals(source)) {
                subscription = new IntegrationTopicSubscription(topicSubscriptionProto.getTopic());
            } else {
                subscription = ClientTopicSubscription.builder()
                        .qos(topicSubscriptionProto.getQos())
                        .topicFilter(topicSubscriptionProto.getTopic())
                        .shareName(topicSubscriptionProto.hasShareName() ? topicSubscriptionProto.getShareName() : null)
                        .options(createSubscriptionOptions(topicSubscriptionProto))
                        .subscriptionId(topicSubscriptionProto.hasSubscriptionId() ? topicSubscriptionProto.getSubscriptionId() : -1)
                        .build();
            }
            subscriptions.add(subscription);
        }
        return new SourcedSubscriptions(source, subscriptions);
    }

    private static SubscriptionsSource getSubscriptionsSource(ClientSubscriptionsProto clientSubscriptionsProto) {
        var proto = clientSubscriptionsProto.hasSource() ? clientSubscriptionsProto.getSource() : SubscriptionsSourceProto.MQTT_CLIENT;
        return SubscriptionsSource.valueOf(proto.name());
    }

    private static SubscriptionOptions createSubscriptionOptions(TopicSubscriptionProto topicSubscriptionProto) {
        return new SubscriptionOptions(
                topicSubscriptionProto.getOptions().getNoLocal(),
                topicSubscriptionProto.getOptions().getRetainAsPublish(),
                getRetainHandling(topicSubscriptionProto));
    }

    private static SubscriptionOptions.RetainHandlingPolicy getRetainHandling(TopicSubscriptionProto topicSubscriptionProto) {
        return SubscriptionOptions.RetainHandlingPolicy.valueOf(topicSubscriptionProto.getOptions().getRetainHandling().getNumber());
    }

    /**
     * Retained messages conversion
     */

    public static RetainedMsg convertProtoToRetainedMsg(RetainedMsgProto retainedMsgProto) {
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

    public static RetainedMsgProto convertToRetainedMsgProto(RetainedMsg retainedMsg) {
        RetainedMsgProto.Builder builder = RetainedMsgProto.newBuilder()
                .setPayload(ByteString.copyFrom(retainedMsg.getPayload()))
                .setQos(retainedMsg.getQos())
                .setTopic(retainedMsg.getTopic())
                .addAllUserProperties(getUserPropertyProtos(retainedMsg.getProperties()))
                .setCreatedTime(retainedMsg.getCreatedTime());
        MqttPropertiesProto.Builder mqttPropsProtoBuilder = getMqttPropsProtoBuilder(retainedMsg.getProperties());
        if (mqttPropsProtoBuilder != null) {
            builder.setMqttProperties(mqttPropsProtoBuilder);
        }
        return builder.build();
    }

    /**
     * MQTT properties conversion
     */

    private static List<UserPropertyProto> getUserPropertyProtos(MqttProperties properties) {
        if (properties != null) {
            UserProperties userProperties = MqttPropertiesUtil.getUserProperties(properties);
            return toUserPropertyProtos(userProperties);
        }
        return Collections.emptyList();
    }

    public static MqttProperties createMqttPropertiesWithUserPropsIfPresent(List<UserPropertyProto> userPropertiesList) {
        MqttProperties mqttProperties = new MqttProperties();
        if (CollectionUtils.isEmpty(userPropertiesList)) {
            return mqttProperties;
        }
        UserProperties userProperties = protoToUserProperties(userPropertiesList);
        mqttProperties.add(userProperties);
        return mqttProperties;
    }

    private static UserProperties protoToUserProperties(List<UserPropertyProto> userPropertiesList) {
        UserProperties userProperties = new UserProperties();
        for (UserPropertyProto userPropertyProto : userPropertiesList) {
            userProperties.add(userPropertyProto.getKey(), userPropertyProto.getValue());
        }
        return userProperties;
    }

    private static List<UserPropertyProto> toUserPropertyProtos(UserProperties userProperties) {
        if (userProperties != null) {
            List<UserPropertyProto> result = new ArrayList<>(userProperties.value().size());
            for (MqttProperties.StringPair stringPair : userProperties.value()) {
                result.add(buildUserPropertyProto(stringPair));
            }
            return result;
        }
        return Collections.emptyList();
    }

    private static UserPropertyProto buildUserPropertyProto(MqttProperties.StringPair stringPair) {
        return UserPropertyProto.newBuilder().setKey(stringPair.key).setValue(stringPair.value).build();
    }

    static MqttPropertiesProto.Builder getMqttPropsProtoBuilder(MqttProperties properties) {
        Integer payloadFormatIndicator = MqttPropertiesUtil.getPayloadFormatIndicatorValue(properties);
        String contentType = MqttPropertiesUtil.getContentTypeValue(properties);
        String responseTopic = MqttPropertiesUtil.getResponseTopicValue(properties);
        byte[] correlationData = MqttPropertiesUtil.getCorrelationDataValue(properties);
        List<Integer> subscriptionIds = MqttPropertiesUtil.getSubscriptionIds(properties);
        if (payloadFormatIndicator != null || contentType != null || responseTopic != null || correlationData != null || !subscriptionIds.isEmpty()) {
            MqttPropertiesProto.Builder mqttPropertiesBuilder = MqttPropertiesProto.newBuilder();
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
            if (!subscriptionIds.isEmpty()) {
                mqttPropertiesBuilder.addAllSubscriptionIds(subscriptionIds);
            }
            return mqttPropertiesBuilder;
        }
        return null;
    }

    public static void addFromProtoToMqttProperties(MqttPropertiesProto mqttPropertiesProto, MqttProperties properties) {
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
        if (mqttPropertiesProto.getSubscriptionIdsCount() > 0) {
            mqttPropertiesProto.getSubscriptionIdsList().forEach(subId -> MqttPropertiesUtil.addSubscriptionIdToProps(properties, subId));
        }
    }

    /**
     * Helper methods
     */

    public static String getClientId(PublishMsgProto publishMsgProto) {
        return publishMsgProto != null ? publishMsgProto.getClientId() : null;
    }

    /**
     * Blocked client
     */

    public static BlockedClientProto convertToBlockedClientProto(BlockedClient blockedClient) {
        Builder builder = BlockedClientProto.newBuilder()
                .setType(BlockedClientTypeProto.valueOf(blockedClient.getType().name()))
                .setExpirationTime(blockedClient.getExpirationTime())
                .setValue(blockedClient.getValue());
        if (blockedClient.getDescription() != null) {
            builder.setDescription(blockedClient.getDescription());
        }
        if (blockedClient.getRegexMatchTarget() != null) {
            builder.setRegexMatchTarget(RegexMatchTargetProto.valueOf(blockedClient.getRegexMatchTarget().name()));
        }
        return builder.build();
    }

    public static BlockedClient convertProtoToBlockedClient(BlockedClientProto blockedClientProto) {
        return switch (blockedClientProto.getType()) {
            case CLIENT_ID ->
                    new ClientIdBlockedClient(blockedClientProto.getExpirationTime(), getDescription(blockedClientProto), blockedClientProto.getValue());
            case USERNAME ->
                    new UsernameBlockedClient(blockedClientProto.getExpirationTime(), getDescription(blockedClientProto), blockedClientProto.getValue());
            case IP_ADDRESS ->
                    new IpAddressBlockedClient(blockedClientProto.getExpirationTime(), getDescription(blockedClientProto), blockedClientProto.getValue());
            case REGEX ->
                    new RegexBlockedClient(blockedClientProto.getExpirationTime(), getDescription(blockedClientProto), blockedClientProto.getValue(), getMatchTarget(blockedClientProto));
            default ->
                    throw new IllegalArgumentException("Unsupported blocked client type: " + blockedClientProto.getType());
        };
    }

    private static String getDescription(BlockedClientProto blockedClientProto) {
        return blockedClientProto.hasDescription() ? blockedClientProto.getDescription() : null;
    }

    private static RegexMatchTarget getMatchTarget(BlockedClientProto blockedClientProto) {
        return RegexMatchTarget.valueOf(blockedClientProto.getRegexMatchTarget().name());
    }

}
