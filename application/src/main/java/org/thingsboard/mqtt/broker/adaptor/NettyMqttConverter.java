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

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.netty.handler.codec.mqtt.MqttPubAckMessage;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttSubscribeMessage;
import io.netty.handler.codec.mqtt.MqttUnsubscribeMessage;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttConnectMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttPingMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttPubAckMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttPubCompMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttPubRecMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttPubRelMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttPublishMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttSubscribeMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttUnsubscribeMsg;
import org.thingsboard.mqtt.broker.constant.BrokerConstants;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.subscription.TopicSubscription;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public class NettyMqttConverter {

    public static MqttConnectMsg createMqttConnectMsg(UUID sessionId, MqttConnectMessage nettyConnectMsg) {
        PublishMsg lastWillPublishMsg = nettyConnectMsg.variableHeader().isWillFlag() ? extractLastWillPublishMsg(nettyConnectMsg) : null;
        return new MqttConnectMsg(sessionId, nettyConnectMsg.payload().clientIdentifier(), nettyConnectMsg.variableHeader().isCleanSession(),
                nettyConnectMsg.variableHeader().keepAliveTimeSeconds(), lastWillPublishMsg);
    }

    public static MqttSubscribeMsg createMqttSubscribeMsg(UUID sessionId, MqttSubscribeMessage nettySubscribeMsg) {
        List<TopicSubscription> topicSubscriptions = nettySubscribeMsg.payload().topicSubscriptions()
                .stream()
                .map(mqttTopicSubscription ->
                        new TopicSubscription(
                                getTopicName(mqttTopicSubscription.topicName()),
                                mqttTopicSubscription.qualityOfService().value(),
                                getShareName(mqttTopicSubscription.topicName())))
                .collect(Collectors.toList());
        return new MqttSubscribeMsg(sessionId, nettySubscribeMsg.variableHeader().messageId(), topicSubscriptions);
    }

    public static String getTopicName(String topicName) {
        int shareNameIndex = getShareNameIndex();
        return isSharedTopic(topicName) ?
                topicName.substring(topicName.indexOf("/", shareNameIndex) + 1) : topicName;
    }

    public static String getShareName(String topicName) {
        try {
            int shareNameIndex = getShareNameIndex();
            return isSharedTopic(topicName) ?
                    topicName.substring(shareNameIndex, topicName.indexOf("/", shareNameIndex)) : null;
        } catch (IndexOutOfBoundsException e) {
            log.error("[{}] Could not extract 'shareName' from shared subscription", topicName, e);
            throw new RuntimeException("Could not extract 'shareName' from shared subscription", e);
        }
    }

    private static int getShareNameIndex() {
        return BrokerConstants.SHARED_SUBSCRIPTION_PREFIX.length();
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

    public static MqttPubAckMsg createMqttPubAckMsg(UUID sessionId, MqttPubAckMessage nettyPubAckMsg) {
        return new MqttPubAckMsg(sessionId, nettyPubAckMsg.variableHeader().messageId());
    }

    public static MqttPubRecMsg createMqttPubRecMsg(UUID sessionId, MqttMessageIdVariableHeader nettyMessageIdVariableHeader) {
        return new MqttPubRecMsg(sessionId, nettyMessageIdVariableHeader.messageId());
    }

    public static MqttPubRelMsg createMqttPubRelMsg(UUID sessionId, MqttMessageIdVariableHeader nettyMessageIdVariableHeader) {
        return new MqttPubRelMsg(sessionId, nettyMessageIdVariableHeader.messageId());
    }

    public static MqttPubCompMsg createMqttPubCompMsg(UUID sessionId, MqttMessageIdVariableHeader nettyMessageIdVariableHeader) {
        return new MqttPubCompMsg(sessionId, nettyMessageIdVariableHeader.messageId());
    }

    public static MqttPingMsg createMqttPingMsg(UUID sessionId) {
        return new MqttPingMsg(sessionId);
    }

    private static PublishMsg extractPublishMsg(MqttPublishMessage mqttPublishMessage) {
        byte[] payloadBytes = toBytes(mqttPublishMessage.payload());
        return PublishMsg.builder()
                .packetId(mqttPublishMessage.variableHeader().packetId())
                .topicName(mqttPublishMessage.variableHeader().topicName())
                .qosLevel(mqttPublishMessage.fixedHeader().qosLevel().value())
                .isRetained(mqttPublishMessage.fixedHeader().isRetain())
                .isDup(mqttPublishMessage.fixedHeader().isDup())
                .payload(payloadBytes)
                .build();
    }

    private static PublishMsg extractLastWillPublishMsg(MqttConnectMessage msg) {
        return PublishMsg.builder()
                .packetId(-1)
                .topicName(msg.payload().willTopic())
                .payload(msg.payload().willMessageInBytes())
                .isRetained(msg.variableHeader().isWillRetain())
                .qosLevel(msg.variableHeader().willQos())
                .build();
    }

    private static byte[] toBytes(ByteBuf inbound) {
        byte[] bytes = new byte[inbound.readableBytes()];
        int readerIndex = inbound.readerIndex();
        inbound.getBytes(readerIndex, bytes);
        return bytes;
    }
}
