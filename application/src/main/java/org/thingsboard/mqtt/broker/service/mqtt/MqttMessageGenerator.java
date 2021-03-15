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
package org.thingsboard.mqtt.broker.service.mqtt;

import io.netty.handler.codec.mqtt.MqttConnAckMessage;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttPubAckMessage;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubAckMessage;

import java.util.List;

public interface MqttMessageGenerator {
    MqttConnAckMessage createMqttConnAckMsg(MqttConnectReturnCode returnCode, boolean sessionPresent);

    MqttMessage createUnSubAckMessage(int msgId);

    MqttSubAckMessage createSubAckMessage(int msgId, List<Integer> grantedQoSList);

    MqttPubAckMessage createPubAckMsg(int requestId);

    MqttPublishMessage createPubMsg(int packetId, String topic, MqttQoS mqttQoS, boolean isDup, byte[] payloadBytes);

    MqttMessage createPingRespMsg();
}
