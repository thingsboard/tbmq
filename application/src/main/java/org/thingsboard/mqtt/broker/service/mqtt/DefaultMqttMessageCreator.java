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
package org.thingsboard.mqtt.broker.service.mqtt;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.handler.codec.mqtt.MqttConnAckMessage;
import io.netty.handler.codec.mqtt.MqttConnAckVariableHeader;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttPubAckMessage;
import io.netty.handler.codec.mqtt.MqttPubReplyMessageVariableHeader;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttPublishVariableHeader;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttReasonCodeAndPropertiesVariableHeader;
import io.netty.handler.codec.mqtt.MqttReasonCodes;
import io.netty.handler.codec.mqtt.MqttSubAckMessage;
import io.netty.handler.codec.mqtt.MqttSubAckPayload;
import io.netty.handler.codec.mqtt.MqttUnsubAckMessage;
import io.netty.handler.codec.mqtt.MqttUnsubAckPayload;
import jakarta.annotation.PostConstruct;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.actors.client.messages.ConnectionAcceptedMsg;
import org.thingsboard.mqtt.broker.actors.client.state.ClientActorStateInfo;
import org.thingsboard.mqtt.broker.common.data.StringUtils;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos.PublishMsgProto;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsg;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.util.MqttPropertiesUtil;
import org.thingsboard.mqtt.broker.util.MqttReasonCodeUtil;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_ACCEPTED;
import static io.netty.handler.codec.mqtt.MqttMessageType.CONNACK;
import static io.netty.handler.codec.mqtt.MqttMessageType.PINGRESP;
import static io.netty.handler.codec.mqtt.MqttMessageType.PUBACK;
import static io.netty.handler.codec.mqtt.MqttMessageType.PUBCOMP;
import static io.netty.handler.codec.mqtt.MqttMessageType.PUBREC;
import static io.netty.handler.codec.mqtt.MqttMessageType.PUBREL;
import static io.netty.handler.codec.mqtt.MqttMessageType.SUBACK;
import static io.netty.handler.codec.mqtt.MqttMessageType.UNSUBACK;
import static io.netty.handler.codec.mqtt.MqttQoS.AT_LEAST_ONCE;
import static io.netty.handler.codec.mqtt.MqttQoS.AT_MOST_ONCE;
import static org.thingsboard.mqtt.broker.common.util.BrokerConstants.SSL;
import static org.thingsboard.mqtt.broker.common.util.BrokerConstants.TCP;
import static org.thingsboard.mqtt.broker.common.util.BrokerConstants.WS;
import static org.thingsboard.mqtt.broker.common.util.BrokerConstants.WSS;

@Service
public class DefaultMqttMessageCreator implements MqttMessageGenerator {

    private static final ByteBufAllocator ALLOCATOR = new UnpooledByteBufAllocator(false);

    @Value("${listener.tcp.netty.max_payload_size}")
    private int tcpMaxPayloadSize;
    @Value("${listener.ssl.netty.max_payload_size}")
    private int sslMaxPayloadSize;
    @Value("${listener.ws.netty.max_payload_size}")
    private int wsMaxPayloadSize;
    @Value("${listener.wss.netty.max_payload_size}")
    private int wssMaxPayloadSize;

    @Setter
    @Value("${mqtt.response-info:}")
    private String serverResponseInfo;
    @Setter
    @Value("${mqtt.max-in-flight-msgs:65535}")
    private int maxInFlightMessages;

    @PostConstruct
    public void init() {
        if (maxInFlightMessages <= 0) {
            throw new RuntimeException("Receive maximum can not be zero or negative");
        }
    }

    @Override
    public MqttConnAckMessage createMqttConnAckMsg(MqttConnectReturnCode returnCode) {
        return MqttMessageBuilders.connAck().returnCode(returnCode).build();
    }

    @Override
    public MqttConnAckMessage createMqttConnAckMsg(ClientActorStateInfo actorState, ConnectionAcceptedMsg msg) {
        ClientSessionCtx sessionCtx = actorState.getCurrentSessionCtx();
        var sessionPresent = msg.isSessionPresent();
        var assignedClientId = actorState.isClientIdGenerated() ? actorState.getClientId() : null;
        var sessionExpiryInterval = actorState.getCurrentSessionCtx().getSessionInfo().getSessionExpiryInterval();
        var maxTopicAlias = actorState.getCurrentSessionCtx().getTopicAliasCtx().getMaxTopicAlias();
        int requestResponseInfo = MqttPropertiesUtil.getRequestResponseInfoPropertyValue(msg.getProperties());
        String responseInfo = getResponseInfo(requestResponseInfo);

        MqttFixedHeader mqttFixedHeader =
                new MqttFixedHeader(CONNACK, false, AT_MOST_ONCE, false, 0);

        MqttProperties properties = new MqttProperties();
        if (assignedClientId != null) {
            MqttPropertiesUtil.addAssignedClientIdToProps(properties, assignedClientId);
        }
        MqttPropertiesUtil.addKeepAliveTimeToProps(properties, msg.getKeepAliveTimeSeconds());
        MqttPropertiesUtil.addSubsIdentifierAvailableToProps(properties);
        MqttPropertiesUtil.addMaxPacketSizeToProps(properties, getMaxPayloadSizeByProtocol(sessionCtx.getInitializerName()));
        MqttPropertiesUtil.addSessionExpiryIntervalToProps(properties, sessionExpiryInterval);
        MqttPropertiesUtil.addMaxTopicAliasToProps(properties, maxTopicAlias);
        if (responseInfo != null) {
            MqttPropertiesUtil.addResponseInfoToProps(properties, responseInfo);
        }
        MqttPropertiesUtil.addReceiveMaxToProps(properties, maxInFlightMessages);
        MqttPropertiesUtil.addAuthMethodToProps(properties, MqttPropertiesUtil.getAuthenticationMethodValue(msg.getProperties()));
        MqttConnAckVariableHeader mqttConnAckVariableHeader =
                new MqttConnAckVariableHeader(CONNECTION_ACCEPTED, sessionPresent, properties);
        return new MqttConnAckMessage(mqttFixedHeader, mqttConnAckVariableHeader);
    }

    @Override
    public MqttSubAckMessage createSubAckMessage(int msgId, List<MqttReasonCodes.SubAck> codes) {
        MqttFixedHeader mqttFixedHeader =
                new MqttFixedHeader(SUBACK, false, AT_MOST_ONCE, false, 0);
        MqttMessageIdVariableHeader mqttMessageIdVariableHeader = MqttMessageIdVariableHeader.from(msgId);
        List<Integer> reasonCodes = codes.stream().map(code -> MqttReasonCodeUtil.byteToInt(code.byteValue())).collect(Collectors.toList());
        MqttSubAckPayload mqttSubAckPayload = new MqttSubAckPayload(reasonCodes);
        return new MqttSubAckMessage(mqttFixedHeader, mqttMessageIdVariableHeader, mqttSubAckPayload);
    }

    @Override
    public MqttUnsubAckMessage createUnSubAckMessage(int msgId, List<MqttReasonCodes.UnsubAck> codes) {
        MqttFixedHeader mqttFixedHeader =
                new MqttFixedHeader(UNSUBACK, false, AT_MOST_ONCE, false, 0);
        if (codes.stream().anyMatch(Objects::isNull)) {
            return new MqttUnsubAckMessage(mqttFixedHeader, MqttMessageIdVariableHeader.from(msgId));
        } else {
            List<Short> reasonCodes = codes.stream().map(code -> (short) code.byteValue()).collect(Collectors.toList());
            MqttUnsubAckPayload mqttUnsubAckPayload = new MqttUnsubAckPayload(reasonCodes);
            return new MqttUnsubAckMessage(mqttFixedHeader, MqttMessageIdVariableHeader.from(msgId), mqttUnsubAckPayload);
        }
    }

    @Override
    public MqttPubAckMessage createPubAckMsg(int msgId, MqttReasonCodes.PubAck code) {
        MqttFixedHeader mqttFixedHeader =
                new MqttFixedHeader(PUBACK, false, AT_MOST_ONCE, false, 0);
        if (code == null) {
            return new MqttPubAckMessage(mqttFixedHeader, MqttMessageIdVariableHeader.from(msgId));
        } else {
            MqttPubReplyMessageVariableHeader variableHeader = new MqttPubReplyMessageVariableHeader(msgId, code.byteValue(), null);
            return new MqttPubAckMessage(mqttFixedHeader, variableHeader);
        }
    }

    @Override
    public MqttMessage createPubRecMsg(int msgId, MqttReasonCodes.PubRec code) {
        if (code != null) {
            byte reasonCodeValue = code.byteValue();
            return createMqtt5PubReplyMsg(PUBREC, AT_MOST_ONCE, msgId, reasonCodeValue);
        }
        return createMqtt3PubReplyMsg(PUBREC, AT_MOST_ONCE, msgId);
    }

    @Override
    public MqttMessage createPubRelMsg(int msgId, MqttReasonCodes.PubRel code) {
        if (code != null) {
            byte reasonCodeValue = code.byteValue();
            return createMqtt5PubReplyMsg(PUBREL, AT_LEAST_ONCE, msgId, reasonCodeValue);
        }
        return createMqtt3PubReplyMsg(PUBREL, AT_LEAST_ONCE, msgId);
    }

    @Override
    public MqttMessage createPubCompMsg(int msgId, MqttReasonCodes.PubComp code) {
        if (code != null) {
            byte reasonCodeValue = code.byteValue();
            return createMqtt5PubReplyMsg(PUBCOMP, AT_MOST_ONCE, msgId, reasonCodeValue);
        }
        return createMqtt3PubReplyMsg(PUBCOMP, AT_MOST_ONCE, msgId);
    }

    @Override
    public MqttPublishMessage createPubMsg(PublishMsg pubMsg) {
        return getMqttPublishMessage(pubMsg.isDup(), pubMsg.getQosLevel(), pubMsg.isRetained(),
                pubMsg.getTopicName(), pubMsg.getPacketId(), pubMsg.getPayload(), pubMsg.getProperties());
    }

    @Override
    public MqttPublishMessage createPubMsg(PublishMsgProto msg, int qos, boolean retain, String topicName, int packetId, MqttProperties properties) {
        return getMqttPublishMessage(false, qos, retain, topicName, packetId, msg.getPayload().toByteArray(), properties);
    }

    @Override
    public MqttPublishMessage createPubRetainMsg(int msgId, RetainedMsg retainedMsg) {
        return getMqttPublishMessage(false, retainedMsg.getQos(), true,
                retainedMsg.getTopic(), msgId, retainedMsg.getPayload(), retainedMsg.getProperties());
    }

    private MqttPublishMessage getMqttPublishMessage(boolean isDup, int qos, boolean isRetain,
                                                     String topic, int packetId, byte[] payloadBytes, MqttProperties properties) {
        MqttFixedHeader mqttFixedHeader =
                new MqttFixedHeader(MqttMessageType.PUBLISH, isDup, MqttQoS.valueOf(qos), isRetain, 0);
        MqttPublishVariableHeader header = new MqttPublishVariableHeader(topic, packetId, properties);
        ByteBuf payload = ALLOCATOR.buffer();
        payload.writeBytes(payloadBytes);
        return new MqttPublishMessage(mqttFixedHeader, header, payload);
    }

    /**
     * Methods for creating MQTT Publish Msg for tests
     */
    public MqttPublishMessage newAtLeastOnceMqttPubMsg(int packetId) {
        return getMqttPublishMessage(false, 1, false, "test/topic", packetId, "payload".getBytes(StandardCharsets.UTF_8), MqttProperties.NO_PROPERTIES);
    }

    public MqttPublishMessage newAtMostOnceMqttPubMsg() {
        return getMqttPublishMessage(false, 0, false, "test/topic", 1, "payload".getBytes(StandardCharsets.UTF_8), MqttProperties.NO_PROPERTIES);
    }

    @Override
    public MqttMessage createPingRespMsg() {
        return new MqttMessage(new MqttFixedHeader(PINGRESP, false, AT_MOST_ONCE, false, 0));
    }

    @Override
    public MqttMessage createDisconnectMsg(MqttReasonCodes.Disconnect code) {
        MqttFixedHeader mqttFixedHeader =
                new MqttFixedHeader(MqttMessageType.DISCONNECT, false, MqttQoS.AT_MOST_ONCE, false, 0);
        MqttReasonCodeAndPropertiesVariableHeader variableHeader =
                new MqttReasonCodeAndPropertiesVariableHeader(code.byteValue(), null);
        return new MqttMessage(mqttFixedHeader, variableHeader);
    }

    @Override
    public MqttMessage createMqttAuthMsg(String authMethod, byte[] authData, MqttReasonCodes.Auth authReasonCode) {
        var properties = new MqttProperties();
        MqttPropertiesUtil.addAuthMethodToProps(properties, authMethod);
        MqttPropertiesUtil.addAuthDataToProps(properties, authData);
        return MqttMessageBuilders.auth().reasonCode(authReasonCode.byteValue()).properties(properties).build();
    }

    private MqttMessage createMqtt3PubReplyMsg(MqttMessageType type, MqttQoS mqttQoS, int msgId) {
        MqttFixedHeader mqttFixedHeader = new MqttFixedHeader(type, false, mqttQoS, false, 0);
        return new MqttMessage(mqttFixedHeader, MqttMessageIdVariableHeader.from(msgId));
    }

    private MqttMessage createMqtt5PubReplyMsg(MqttMessageType type, MqttQoS mqttQoS, int msgId, byte reasonCodeValue) {
        MqttFixedHeader mqttFixedHeader = new MqttFixedHeader(type, false, mqttQoS, false, 0);
        MqttPubReplyMessageVariableHeader variableHeader = new MqttPubReplyMessageVariableHeader(msgId, reasonCodeValue, null);
        return new MqttMessage(mqttFixedHeader, variableHeader);
    }

    String getResponseInfo(int requestResponseInfo) {
        if (requestResponseInfo == 1 && StringUtils.isNotEmpty(serverResponseInfo)) {
            return serverResponseInfo;
        }
        return null;
    }

    private int getMaxPayloadSizeByProtocol(String initializerName) {
        return switch (initializerName) {
            case TCP -> tcpMaxPayloadSize;
            case SSL -> sslMaxPayloadSize;
            case WS -> wsMaxPayloadSize;
            case WSS -> wssMaxPayloadSize;
            default -> throw new RuntimeException("Unexpected value: " + initializerName);
        };
    }

}
