/**
 * Copyright © 2016-2024 The Thingsboard Authors
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

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageIdAndPropertiesVariableHeader;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttPubReplyMessageVariableHeader;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttReasonCodeAndPropertiesVariableHeader;
import io.netty.handler.codec.mqtt.MqttReasonCodes;
import io.netty.handler.codec.mqtt.MqttSubscribeMessage;
import io.netty.handler.codec.mqtt.MqttUnsubscribeMessage;
import io.netty.handler.codec.mqtt.MqttVersion;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttConnectMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttDisconnectMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttPingMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttPubAckMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttPubCompMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttPubRecMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttPubRelMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttPublishMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttSubscribeMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttUnsubscribeMsg;
import org.thingsboard.mqtt.broker.common.data.subscription.SubscriptionOptions;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.common.util.BrokerConstants;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;
import org.thingsboard.mqtt.broker.util.MqttPropertiesUtil;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public class NettyMqttConverter {

    public static MqttConnectMsg createMqttConnectMsg(UUID sessionId, MqttConnectMessage nettyConnectMsg) {
        return new MqttConnectMsg(
                sessionId,
                nettyConnectMsg.payload().clientIdentifier(),
                nettyConnectMsg.variableHeader().isCleanSession(),
                nettyConnectMsg.variableHeader().keepAliveTimeSeconds(),
                nettyConnectMsg.variableHeader().isWillFlag() ? extractLastWillPublishMsg(nettyConnectMsg) : null,
                nettyConnectMsg.variableHeader().properties());
    }

    public static MqttSubscribeMsg createMqttSubscribeMsg(UUID sessionId, MqttSubscribeMessage nettySubscribeMsg) {
        MqttMessageIdAndPropertiesVariableHeader mqttMessageIdVariableHeader = nettySubscribeMsg.idAndPropertiesVariableHeader();
        int messageId = mqttMessageIdVariableHeader.messageId();
        MqttProperties properties = mqttMessageIdVariableHeader.properties();
        int subscriptionIdValue = MqttPropertiesUtil.getSubscriptionIdValue(properties);

        List<TopicSubscription> topicSubscriptions = nettySubscribeMsg.payload().topicSubscriptions()
                .stream()
                .map(mqttTopicSubscription ->
                        new TopicSubscription(
                                getTopicFilter(mqttTopicSubscription.topicFilter()),
                                mqttTopicSubscription.qualityOfService().value(),
                                getShareName(mqttTopicSubscription.topicFilter()),
                                SubscriptionOptions.newInstance(mqttTopicSubscription.option()),
                                subscriptionIdValue))
                .collect(Collectors.toList());
        return new MqttSubscribeMsg(sessionId, messageId, topicSubscriptions, properties);
    }

    public static String getTopicFilter(String topicFilter) {
        return isSharedTopic(topicFilter) ?
                topicFilter.substring(topicFilter.indexOf(BrokerConstants.TOPIC_DELIMITER_STR, BrokerConstants.SHARE_NAME_IDX) + 1) : topicFilter;
    }

    public static String getShareName(String topicFilter) {
        try {
            return isSharedTopic(topicFilter) ?
                    topicFilter.substring(BrokerConstants.SHARE_NAME_IDX, topicFilter.indexOf(BrokerConstants.TOPIC_DELIMITER_STR, BrokerConstants.SHARE_NAME_IDX)) : null;
        } catch (IndexOutOfBoundsException e) {
            log.error("[{}] Could not extract 'shareName' from shared subscription", topicFilter, e);
            throw new RuntimeException("Could not extract 'shareName' from shared subscription", e);
        }
    }

    public static boolean isSharedTopic(String topicName) {
        return topicName.startsWith(BrokerConstants.SHARED_SUBSCRIPTION_PREFIX);
    }

    public static MqttUnsubscribeMsg createMqttUnsubscribeMsg(UUID sessionId, MqttUnsubscribeMessage nettyUnsubscribeMsg) {
        return new MqttUnsubscribeMsg(sessionId, nettyUnsubscribeMsg.variableHeader().messageId(), nettyUnsubscribeMsg.payload().topics());
    }

    public static MqttPublishMsg createMqttPublishMsg(UUID sessionId, MqttPublishMessage nettyPublishMsg) {
        return new MqttPublishMsg(sessionId, extractPublishMsg(nettyPublishMsg));
    }

    public static MqttPubAckMsg createMqttPubAckMsg(UUID sessionId, MqttPubReplyMessageVariableHeader variableHeader) {
        MqttReasonCodes.PubAck pubAckReasonCode = MqttReasonCodes.PubAck.valueOf(variableHeader.reasonCode());
        return new MqttPubAckMsg(sessionId, variableHeader.messageId(), pubAckReasonCode);
    }

    public static MqttPubRecMsg createMqttPubRecMsg(UUID sessionId, MqttPubReplyMessageVariableHeader variableHeader) {
        MqttReasonCodes.PubRec pubRecReasonCode = MqttReasonCodes.PubRec.valueOf(variableHeader.reasonCode());
        return new MqttPubRecMsg(sessionId, variableHeader.messageId(), variableHeader.properties(), pubRecReasonCode);
    }

    public static MqttPubRelMsg createMqttPubRelMsg(UUID sessionId, MqttPubReplyMessageVariableHeader variableHeader) {
        MqttReasonCodes.PubRel pubRelReasonCode = MqttReasonCodes.PubRel.valueOf(variableHeader.reasonCode());
        return new MqttPubRelMsg(sessionId, variableHeader.messageId(), variableHeader.properties(), pubRelReasonCode);
    }

    public static MqttPubCompMsg createMqttPubCompMsg(UUID sessionId, MqttPubReplyMessageVariableHeader variableHeader) {
        MqttReasonCodes.PubComp pubCompReasonCode = MqttReasonCodes.PubComp.valueOf(variableHeader.reasonCode());
        return new MqttPubCompMsg(sessionId, variableHeader.messageId(), variableHeader.properties(), pubCompReasonCode);
    }

    public static MqttDisconnectMsg createMqttDisconnectMsg(ClientSessionCtx ctx, MqttMessage msg) {
        MqttProperties properties = MqttProperties.NO_PROPERTIES;
        if (MqttVersion.MQTT_5 == ctx.getMqttVersion()) {
            var variableHeader = (MqttReasonCodeAndPropertiesVariableHeader) msg.variableHeader();

            properties = variableHeader.properties();
            int sessionExpiryInterval = getSessionExpiryInterval(properties);
            if (ctx.getSessionInfo().getSessionExpiryInterval() == 0 && sessionExpiryInterval != 0) {
                // It is a Protocol Error to set a non-zero Session Expiry Interval in the DISCONNECT packet sent by the Client
                // if the Session Expiry Interval in the CONNECT packet was zero
                return new MqttDisconnectMsg(ctx.getSessionId(), getDisconnectReason(DisconnectReasonType.ON_PROTOCOL_ERROR));
            }

            var reasonCode = variableHeader.reasonCode();
            if (MqttReasonCodes.Disconnect.DISCONNECT_WITH_WILL_MESSAGE.byteValue() == reasonCode) {
                return new MqttDisconnectMsg(ctx.getSessionId(), getDisconnectReason(DisconnectReasonType.ON_DISCONNECT_AND_WILL_MSG), properties);
            }
        }
        return new MqttDisconnectMsg(ctx.getSessionId(), getDisconnectReason(DisconnectReasonType.ON_DISCONNECT_MSG), properties);
    }

    private static int getSessionExpiryInterval(MqttProperties properties) {
        MqttProperties.IntegerProperty property = MqttPropertiesUtil.getSessionExpiryIntervalProperty(properties);
        if (property != null) {
            return property.value();
        }
        return 0;
    }

    private static DisconnectReason getDisconnectReason(DisconnectReasonType reasonType) {
        return new DisconnectReason(reasonType);
    }

    public static MqttPingMsg createMqttPingMsg(UUID sessionId) {
        return new MqttPingMsg(sessionId);
    }

    private static PublishMsg extractPublishMsg(MqttPublishMessage mqttPublishMessage) {
        ByteBuf byteBuf = mqttPublishMessage.payload().retain();
        return PublishMsg.builder()
                .packetId(mqttPublishMessage.variableHeader().packetId())
                .topicName(mqttPublishMessage.variableHeader().topicName())
                .qosLevel(mqttPublishMessage.fixedHeader().qosLevel().value())
                .isRetained(mqttPublishMessage.fixedHeader().isRetain())
                .isDup(mqttPublishMessage.fixedHeader().isDup())
                .byteBuf(byteBuf)
                .properties(mqttPublishMessage.variableHeader().properties())
                .build();
    }

    private static PublishMsg extractLastWillPublishMsg(MqttConnectMessage msg) {
        return PublishMsg.builder()
                .packetId(-1)
                .topicName(msg.payload().willTopic())
                .payload(msg.payload().willMessageInBytes())
                .isRetained(msg.variableHeader().isWillRetain())
                .isDup(false)
                .qosLevel(msg.variableHeader().willQos())
                .properties(msg.payload().willProperties())
                .build();
    }

    public static byte[] toBytes(ByteBuf inbound) {
        byte[] bytes = new byte[inbound.readableBytes()];
        int readerIndex = inbound.readerIndex();
        inbound.getBytes(readerIndex, bytes);
        return bytes;
    }

}
