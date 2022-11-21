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
package org.thingsboard.mqtt.broker.service.mqtt;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.handler.codec.mqtt.MqttConnAckMessage;
import io.netty.handler.codec.mqtt.MqttConnAckVariableHeader;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttPubAckMessage;
import io.netty.handler.codec.mqtt.MqttPubReplyMessageVariableHeader;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttPublishVariableHeader;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttReasonCodeAndPropertiesVariableHeader;
import io.netty.handler.codec.mqtt.MqttSubAckMessage;
import io.netty.handler.codec.mqtt.MqttSubAckPayload;
import io.netty.handler.codec.mqtt.MqttUnsubAckMessage;
import io.netty.handler.codec.mqtt.MqttUnsubAckPayload;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsg;
import org.thingsboard.mqtt.broker.util.MqttReasonCode;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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

@Service
public class DefaultMqttMessageCreator implements MqttMessageGenerator {

    private static final ByteBufAllocator ALLOCATOR = new UnpooledByteBufAllocator(false);

    @Value("${listener.tcp.netty.max_payload_size}")
    private int tcpMaxPayloadSize;
    @Value("${listener.ssl.netty.max_payload_size}")
    private int sslMaxPayloadSize;

    @Override
    public MqttConnAckMessage createMqttConnAckMsg(MqttConnectReturnCode returnCode) {
        MqttFixedHeader mqttFixedHeader =
                new MqttFixedHeader(CONNACK, false, AT_MOST_ONCE, false, 0);
        MqttConnAckVariableHeader mqttConnAckVariableHeader =
                new MqttConnAckVariableHeader(returnCode, false);
        return new MqttConnAckMessage(mqttFixedHeader, mqttConnAckVariableHeader);
    }

    @Override
    public MqttConnAckMessage createMqttConnAckMsg(MqttConnectReturnCode returnCode, boolean sessionPresent,
                                                   String assignedClientId, int keepAliveTimeSeconds,
                                                   int sessionExpiryInterval) {
        MqttFixedHeader mqttFixedHeader =
                new MqttFixedHeader(CONNACK, false, AT_MOST_ONCE, false, 0);

        MqttProperties properties = new MqttProperties();
        if (assignedClientId != null) {
            properties.add(new MqttProperties.StringProperty(
                    MqttProperties.MqttPropertyType.ASSIGNED_CLIENT_IDENTIFIER.value(),
                    assignedClientId)
            );
        }
        properties.add(new MqttProperties.IntegerProperty(
                MqttProperties.MqttPropertyType.SERVER_KEEP_ALIVE.value(),
                keepAliveTimeSeconds)
        );
        properties.add(new MqttProperties.IntegerProperty(
                MqttProperties.MqttPropertyType.SUBSCRIPTION_IDENTIFIER_AVAILABLE.value(),
                0) // TODO: 14/10/2022 after impl MQTT 5 SubscriptionId feature change this to 1 or remove completely
        );
        properties.add(new MqttProperties.IntegerProperty(
                MqttProperties.MqttPropertyType.MAXIMUM_PACKET_SIZE.value(),
                Math.min(tcpMaxPayloadSize, sslMaxPayloadSize))
        );
        properties.add(new MqttProperties.IntegerProperty(
                MqttProperties.MqttPropertyType.SESSION_EXPIRY_INTERVAL.value(),
                sessionExpiryInterval)
        );

        MqttConnAckVariableHeader mqttConnAckVariableHeader =
                new MqttConnAckVariableHeader(returnCode, sessionPresent, properties);
        return new MqttConnAckMessage(mqttFixedHeader, mqttConnAckVariableHeader);
    }

    @Override
    public MqttUnsubAckMessage createUnSubAckMessage(int msgId, List<MqttReasonCode> codes) {
        MqttFixedHeader mqttFixedHeader =
                new MqttFixedHeader(UNSUBACK, false, AT_MOST_ONCE, false, 0);
        if (codes.stream().anyMatch(Objects::isNull)) {
            return new MqttUnsubAckMessage(mqttFixedHeader, MqttMessageIdVariableHeader.from(msgId));
        } else {
            List<Short> reasonCodes = codes.stream().map(code -> (short) code.value()).collect(Collectors.toList());
            MqttUnsubAckPayload mqttUnsubAckPayload = new MqttUnsubAckPayload(reasonCodes);
            return new MqttUnsubAckMessage(mqttFixedHeader, MqttMessageIdVariableHeader.from(msgId), mqttUnsubAckPayload);
        }
    }

    @Override
    public MqttSubAckMessage createSubAckMessage(int msgId, List<MqttReasonCode> codes) {
        MqttFixedHeader mqttFixedHeader =
                new MqttFixedHeader(SUBACK, false, AT_MOST_ONCE, false, 0);
        MqttMessageIdVariableHeader mqttMessageIdVariableHeader = MqttMessageIdVariableHeader.from(msgId);
        List<Integer> reasonCodes = codes.stream().map(code -> (int) code.value()).collect(Collectors.toList());
        MqttSubAckPayload mqttSubAckPayload = new MqttSubAckPayload(reasonCodes);
        return new MqttSubAckMessage(mqttFixedHeader, mqttMessageIdVariableHeader, mqttSubAckPayload);
    }

    @Override
    public MqttPubAckMessage createPubAckMsg(int msgId, MqttReasonCode code) {
        MqttFixedHeader mqttFixedHeader =
                new MqttFixedHeader(PUBACK, false, AT_MOST_ONCE, false, 0);
        if (code == null) {
            return new MqttPubAckMessage(mqttFixedHeader, MqttMessageIdVariableHeader.from(msgId));
        } else {
            MqttPubReplyMessageVariableHeader variableHeader = new MqttPubReplyMessageVariableHeader(msgId, code.value(), null);
            return new MqttPubAckMessage(mqttFixedHeader, variableHeader);
        }
    }

    @Override
    public MqttPublishMessage createPubMsg(PublishMsg pubMsg) {
        return getMqttPublishMessage(pubMsg.isDup(), pubMsg.getQosLevel(), pubMsg.isRetained(),
                pubMsg.getTopicName(), pubMsg.getPacketId(), pubMsg.getPayload(), pubMsg.getProperties());
    }

    @Override
    public MqttPublishMessage createPubRetainMsg(int msgId, RetainedMsg retainedMsg) {
        return getMqttPublishMessage(false, retainedMsg.getQosLevel(), true,
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

    @Override
    public MqttMessage createPingRespMsg() {
        return new MqttMessage(new MqttFixedHeader(PINGRESP, false, AT_MOST_ONCE, false, 0));
    }

    @Override
    public MqttMessage createPubRecMsg(int msgId, MqttReasonCode code) {
        return createPubReplyMsg(PUBREC, AT_MOST_ONCE, msgId, code);
    }

    @Override
    public MqttMessage createPubRelMsg(int msgId, MqttReasonCode code) {
        return createPubReplyMsg(PUBREL, AT_LEAST_ONCE, msgId, code);
    }

    @Override
    public MqttMessage createPubCompMsg(int msgId, MqttReasonCode code) {
        return createPubReplyMsg(PUBCOMP, AT_MOST_ONCE, msgId, code);
    }

    @Override
    public MqttMessage createDisconnectMsg(MqttReasonCode code) {
        MqttFixedHeader mqttFixedHeader =
                new MqttFixedHeader(MqttMessageType.DISCONNECT, false, MqttQoS.AT_MOST_ONCE, false, 0);
        MqttReasonCodeAndPropertiesVariableHeader variableHeader =
                new MqttReasonCodeAndPropertiesVariableHeader(code.value(), null);
        return new MqttMessage(mqttFixedHeader, variableHeader);
    }

    private MqttMessage createPubReplyMsg(MqttMessageType type, MqttQoS mqttQoS, int msgId, MqttReasonCode code) {
        MqttFixedHeader mqttFixedHeader = new MqttFixedHeader(type, false, mqttQoS, false, 0);
        if (code == null) {
            return new MqttMessage(mqttFixedHeader, MqttMessageIdVariableHeader.from(msgId));
        } else {
            MqttPubReplyMessageVariableHeader variableHeader = new MqttPubReplyMessageVariableHeader(msgId, code.value(), null);
            return new MqttMessage(mqttFixedHeader, variableHeader);
        }
    }
}
