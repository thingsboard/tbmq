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
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttConnectPayload;
import io.netty.handler.codec.mqtt.MqttConnectVariableHeader;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageIdAndPropertiesVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttPubReplyMessageVariableHeader;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttPublishVariableHeader;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttReasonCodeAndPropertiesVariableHeader;
import io.netty.handler.codec.mqtt.MqttReasonCodes;
import io.netty.handler.codec.mqtt.MqttSubscribeMessage;
import io.netty.handler.codec.mqtt.MqttSubscribePayload;
import io.netty.handler.codec.mqtt.MqttTopicSubscription;
import io.netty.handler.codec.mqtt.MqttUnsubscribeMessage;
import io.netty.handler.codec.mqtt.MqttUnsubscribePayload;
import io.netty.handler.codec.mqtt.MqttVersion;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;
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
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.data.subscription.SubscriptionOptions;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.common.util.BrokerConstants;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;

import java.util.List;
import java.util.UUID;

public class NettyMqttConverterTest {

    static final String SHARED_SUBSCRIBER_GROUP_TOPIC_NAME_SUFFIX = "shared-subscriber-group/main/+/temp";
    static final String SHARED_SUBSCRIBER_GROUP_TOPIC_NAME = BrokerConstants.SHARED_SUBSCRIPTION_PREFIX + SHARED_SUBSCRIBER_GROUP_TOPIC_NAME_SUFFIX;

    @Test
    public void testGetShareNameFromSharedSubscription() {
        String shareName = NettyMqttConverter.getShareName(SHARED_SUBSCRIBER_GROUP_TOPIC_NAME);
        Assert.assertEquals("shared-subscriber-group", shareName);
    }

    @Test
    public void testGetShareName() {
        String shareName = NettyMqttConverter.getShareName(SHARED_SUBSCRIBER_GROUP_TOPIC_NAME_SUFFIX);
        Assert.assertNull(shareName);
    }

    @Test
    public void testGetShareName1() {
        String shareName = NettyMqttConverter.getShareName(BrokerConstants.SHARED_SUBSCRIPTION_PREFIX + "/topic");
        Assert.assertNotNull(shareName);
        Assert.assertTrue(shareName.isEmpty());
    }

    @Test(expected = RuntimeException.class)
    public void testGetShareName2() {
        NettyMqttConverter.getShareName(BrokerConstants.SHARED_SUBSCRIPTION_PREFIX + "topic");
    }

    @Test
    public void testGetTopicFilterFromSharedSubscription() {
        String topicFilter = NettyMqttConverter.getTopicFilter(SHARED_SUBSCRIBER_GROUP_TOPIC_NAME);
        Assert.assertEquals("main/+/temp", topicFilter);
    }

    @Test
    public void testGetTopicFilter() {
        String topicFilter = NettyMqttConverter.getTopicFilter(SHARED_SUBSCRIBER_GROUP_TOPIC_NAME_SUFFIX);
        Assert.assertEquals(SHARED_SUBSCRIBER_GROUP_TOPIC_NAME_SUFFIX, topicFilter);
    }

    @Test
    public void testCreateMqttConnectMsg() {
        UUID sessionId = UUID.randomUUID();
        MqttConnectMessage connectMessage = getMqttConnectMessage();

        MqttConnectMsg mqttConnectMsg = NettyMqttConverter.createMqttConnectMsg(sessionId, connectMessage);

        Assert.assertEquals(sessionId, mqttConnectMsg.getSessionId());
        Assert.assertEquals("clientId", mqttConnectMsg.getClientIdentifier());
        Assert.assertTrue(mqttConnectMsg.isCleanStart());
        Assert.assertEquals(60, mqttConnectMsg.getKeepAliveTimeSeconds());
        Assert.assertNotNull(mqttConnectMsg.getLastWillMsg());
        Assert.assertEquals("willTopic", mqttConnectMsg.getLastWillMsg().getTopicName());
        Assert.assertEquals(MqttProperties.NO_PROPERTIES, mqttConnectMsg.getProperties());
    }

    private @NotNull MqttConnectMessage getMqttConnectMessage() {
        MqttConnectVariableHeader connectVariableHeader = new MqttConnectVariableHeader(
                MqttVersion.MQTT_3_1_1.protocolName(),
                MqttVersion.MQTT_3_1_1.protocolLevel(),
                true, false, false, 0, true, true, 60, MqttProperties.NO_PROPERTIES);
        MqttConnectPayload connectPayload = new MqttConnectPayload("clientId", MqttProperties.NO_PROPERTIES,
                "willTopic", BrokerConstants.DUMMY_PAYLOAD, "un", null);
        return new MqttConnectMessage(
                new MqttFixedHeader(MqttMessageType.CONNECT, false, MqttQoS.AT_MOST_ONCE, false, 0),
                connectVariableHeader,
                connectPayload
        );
    }

    @Test
    public void testCreateMqttSubscribeMsg() {
        UUID sessionId = UUID.randomUUID();
        MqttSubscribeMessage subscribeMessage = getMqttSubscribeMessage();

        MqttSubscribeMsg mqttSubscribeMsg = NettyMqttConverter.createMqttSubscribeMsg(sessionId, subscribeMessage);
        Assert.assertNotNull(mqttSubscribeMsg);

        Assert.assertEquals(sessionId, mqttSubscribeMsg.getSessionId());
        Assert.assertEquals(1, mqttSubscribeMsg.getMessageId());
        List<TopicSubscription> topicSubscriptions = mqttSubscribeMsg.getTopicSubscriptions();
        Assert.assertEquals(1, topicSubscriptions.size());
        TopicSubscription topicSubscription = topicSubscriptions.get(0);
        Assert.assertEquals("topic", topicSubscription.getTopicFilter());
        Assert.assertEquals(MqttQoS.AT_LEAST_ONCE.value(), topicSubscription.getQos());
        Assert.assertNull(topicSubscription.getShareName());
        Assert.assertEquals(SubscriptionOptions.newInstance(), topicSubscription.getOptions());
    }

    private @NotNull MqttSubscribeMessage getMqttSubscribeMessage() {
        return getMqttSubscribeMessage(MqttProperties.NO_PROPERTIES);
    }

    @Test
    public void testCreateMqttSubscribeMsgWithInvalidSubscriptionId() {
        UUID sessionId = UUID.randomUUID();
        MqttSubscribeMessage subscribeMessage = getMqttSubscribeMessageWithInvalidSubscriptionId(0);

        MqttSubscribeMsg mqttSubscribeMsg = NettyMqttConverter.createMqttSubscribeMsg(sessionId, subscribeMessage);
        Assert.assertNull(mqttSubscribeMsg);
    }

    @Test
    public void testCreateMqttSubscribeMsgWithInvalidSubscriptionIdAboveMaxValue() {
        UUID sessionId = UUID.randomUUID();
        MqttSubscribeMessage subscribeMessage = getMqttSubscribeMessageWithInvalidSubscriptionId(BrokerConstants.SUBSCRIPTION_ID_MAXIMUM + 1);

        MqttSubscribeMsg mqttSubscribeMsg = NettyMqttConverter.createMqttSubscribeMsg(sessionId, subscribeMessage);
        Assert.assertNull(mqttSubscribeMsg);
    }

    private @NotNull MqttSubscribeMessage getMqttSubscribeMessageWithInvalidSubscriptionId(int value) {
        MqttProperties mqttProperties = new MqttProperties();
        mqttProperties.add(new MqttProperties.IntegerProperty(MqttProperties.MqttPropertyType.SUBSCRIPTION_IDENTIFIER.value(), value));
        return getMqttSubscribeMessage(mqttProperties);
    }

    private @NotNull MqttSubscribeMessage getMqttSubscribeMessage(MqttProperties mqttProperties) {
        MqttMessageIdAndPropertiesVariableHeader variableHeader = new MqttMessageIdAndPropertiesVariableHeader(1, mqttProperties);
        MqttSubscribePayload payload = new MqttSubscribePayload(List.of(new MqttTopicSubscription("topic", MqttQoS.AT_LEAST_ONCE)));
        return new MqttSubscribeMessage(
                new MqttFixedHeader(MqttMessageType.SUBSCRIBE, false, MqttQoS.AT_LEAST_ONCE, false, 0),
                variableHeader,
                payload
        );
    }

    @Test
    public void testCreateMqttUnsubscribeMsg() {
        UUID sessionId = UUID.randomUUID();
        MqttMessageIdAndPropertiesVariableHeader variableHeader = new MqttMessageIdAndPropertiesVariableHeader(1, MqttProperties.NO_PROPERTIES);
        MqttUnsubscribePayload payload = new MqttUnsubscribePayload(List.of("topic"));
        MqttUnsubscribeMessage unsubscribeMessage = new MqttUnsubscribeMessage(
                new MqttFixedHeader(MqttMessageType.UNSUBSCRIBE, false, MqttQoS.AT_MOST_ONCE, false, 0),
                variableHeader,
                payload
        );

        MqttUnsubscribeMsg mqttUnsubscribeMsg = NettyMqttConverter.createMqttUnsubscribeMsg(sessionId, unsubscribeMessage);

        Assert.assertEquals(sessionId, mqttUnsubscribeMsg.getSessionId());
        Assert.assertEquals(1, mqttUnsubscribeMsg.getMessageId());
        Assert.assertEquals(List.of("topic"), mqttUnsubscribeMsg.getTopics());
    }

    @Test
    public void testCreateMqttPublishMsg() {
        UUID sessionId = UUID.randomUUID();
        MqttPublishVariableHeader variableHeader = new MqttPublishVariableHeader("topic", 1, MqttProperties.NO_PROPERTIES);
        ByteBuf payload = Unpooled.buffer();
        MqttPublishMessage publishMessage = new MqttPublishMessage(
                new MqttFixedHeader(MqttMessageType.PUBLISH, false, MqttQoS.AT_LEAST_ONCE, false, 0),
                variableHeader,
                payload
        );

        MqttPublishMsg mqttPublishMsg = NettyMqttConverter.createMqttPublishMsg(sessionId, publishMessage);

        Assert.assertEquals(sessionId, mqttPublishMsg.getSessionId());
        PublishMsg publishMsg = mqttPublishMsg.getPublishMsg();
        Assert.assertEquals(1, publishMsg.getPacketId());
        Assert.assertEquals("topic", publishMsg.getTopicName());
        Assert.assertEquals(MqttQoS.AT_LEAST_ONCE.value(), publishMsg.getQosLevel());
        Assert.assertFalse(publishMsg.isRetained());
        Assert.assertFalse(publishMsg.isDup());
    }

    @Test
    public void testCreateMqttPubAckMsg() {
        UUID sessionId = UUID.randomUUID();
        MqttPubReplyMessageVariableHeader variableHeader = new MqttPubReplyMessageVariableHeader(1, (byte) 0, MqttProperties.NO_PROPERTIES);

        MqttPubAckMsg mqttPubAckMsg = NettyMqttConverter.createMqttPubAckMsg(sessionId, variableHeader);

        Assert.assertEquals(sessionId, mqttPubAckMsg.getSessionId());
        Assert.assertEquals(1, mqttPubAckMsg.getMessageId());
        Assert.assertEquals(MqttReasonCodes.PubAck.SUCCESS, mqttPubAckMsg.getReasonCode());
    }

    @Test
    public void testCreateMqttPubRecMsg() {
        UUID sessionId = UUID.randomUUID();
        MqttPubReplyMessageVariableHeader variableHeader = new MqttPubReplyMessageVariableHeader(1, (byte) 0, MqttProperties.NO_PROPERTIES);

        MqttPubRecMsg mqttPubRecMsg = NettyMqttConverter.createMqttPubRecMsg(sessionId, variableHeader);

        Assert.assertEquals(sessionId, mqttPubRecMsg.getSessionId());
        Assert.assertEquals(1, mqttPubRecMsg.getMessageId());
        Assert.assertEquals(MqttReasonCodes.PubRec.SUCCESS, mqttPubRecMsg.getReasonCode());
    }

    @Test
    public void testCreateMqttPubRelMsg() {
        UUID sessionId = UUID.randomUUID();
        MqttPubReplyMessageVariableHeader variableHeader = new MqttPubReplyMessageVariableHeader(1, (byte) 0, MqttProperties.NO_PROPERTIES);

        MqttPubRelMsg mqttPubRelMsg = NettyMqttConverter.createMqttPubRelMsg(sessionId, variableHeader);

        Assert.assertEquals(sessionId, mqttPubRelMsg.getSessionId());
        Assert.assertEquals(1, mqttPubRelMsg.getMessageId());
        Assert.assertEquals(MqttReasonCodes.PubRel.SUCCESS, mqttPubRelMsg.getReasonCode());
    }

    @Test
    public void testCreateMqttPubCompMsg() {
        UUID sessionId = UUID.randomUUID();
        MqttPubReplyMessageVariableHeader variableHeader = new MqttPubReplyMessageVariableHeader(1, (byte) 0, MqttProperties.NO_PROPERTIES);

        MqttPubCompMsg mqttPubCompMsg = NettyMqttConverter.createMqttPubCompMsg(sessionId, variableHeader);

        Assert.assertEquals(sessionId, mqttPubCompMsg.getSessionId());
        Assert.assertEquals(1, mqttPubCompMsg.getMessageId());
        Assert.assertEquals(MqttReasonCodes.PubComp.SUCCESS, mqttPubCompMsg.getReasonCode());
    }

    @Test
    public void testCreateMqttDisconnectMsg() {
        UUID sessionId = UUID.randomUUID();
        ClientSessionCtx ctx = new ClientSessionCtx(null, sessionId, null, "");
        ctx.setMqttVersion(MqttVersion.MQTT_5);
        ctx.setSessionInfo(SessionInfo.builder().sessionExpiryInterval(0).build());

        MqttReasonCodeAndPropertiesVariableHeader variableHeader = new MqttReasonCodeAndPropertiesVariableHeader(
                MqttReasonCodes.Disconnect.DISCONNECT_WITH_WILL_MESSAGE.byteValue(),
                MqttProperties.NO_PROPERTIES
        );
        MqttMessage msg = new MqttMessage(new MqttFixedHeader(MqttMessageType.DISCONNECT, false, MqttQoS.AT_MOST_ONCE, false, 0), variableHeader);

        MqttDisconnectMsg mqttDisconnectMsg = NettyMqttConverter.createMqttDisconnectMsg(ctx, msg);

        Assert.assertEquals(sessionId, mqttDisconnectMsg.getSessionId());
        Assert.assertEquals(DisconnectReasonType.ON_DISCONNECT_AND_WILL_MSG, mqttDisconnectMsg.getReason().getType());
    }

    @Test
    public void testCreateMqtt3DisconnectMsg() {
        UUID sessionId = UUID.randomUUID();
        ClientSessionCtx ctx = new ClientSessionCtx(null, sessionId, null, "");
        ctx.setMqttVersion(MqttVersion.MQTT_3_1_1);
        ctx.setSessionInfo(SessionInfo.builder().sessionExpiryInterval(0).build());

        MqttReasonCodeAndPropertiesVariableHeader variableHeader = new MqttReasonCodeAndPropertiesVariableHeader(
                MqttReasonCodes.Disconnect.NORMAL_DISCONNECT.byteValue(),
                MqttProperties.NO_PROPERTIES
        );
        MqttMessage msg = new MqttMessage(new MqttFixedHeader(MqttMessageType.DISCONNECT, false, MqttQoS.AT_MOST_ONCE, false, 0), variableHeader);

        MqttDisconnectMsg mqttDisconnectMsg = NettyMqttConverter.createMqttDisconnectMsg(ctx, msg);

        Assert.assertEquals(sessionId, mqttDisconnectMsg.getSessionId());
        Assert.assertEquals(DisconnectReasonType.ON_DISCONNECT_MSG, mqttDisconnectMsg.getReason().getType());
    }

    @Test
    public void testCreateMqttPingMsg() {
        UUID sessionId = UUID.randomUUID();
        MqttPingMsg mqttPingMsg = NettyMqttConverter.createMqttPingMsg(sessionId);

        Assert.assertEquals(sessionId, mqttPingMsg.getSessionId());
    }

    @Test
    public void testToBytes() {
        ByteBuf inbound = Unpooled.copiedBuffer("test".getBytes());
        byte[] bytes = NettyMqttConverter.toBytes(inbound);

        Assert.assertArrayEquals("test".getBytes(), bytes);
    }
}
