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

import io.netty.handler.codec.mqtt.MqttConnAckMessage;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttPubAckMessage;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttReasonCodes;
import io.netty.handler.codec.mqtt.MqttSubAckMessage;
import org.thingsboard.mqtt.broker.actors.client.messages.ConnectionAcceptedMsg;
import org.thingsboard.mqtt.broker.actors.client.state.ClientActorStateInfo;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos.PublishMsgProto;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsg;

import java.util.List;

public interface MqttMessageGenerator {

    MqttConnAckMessage createMqttConnAckMsg(MqttConnectReturnCode returnCode);

    MqttConnAckMessage createMqttConnAckMsg(ClientActorStateInfo actorState, ConnectionAcceptedMsg msg);

    MqttSubAckMessage createSubAckMessage(int msgId, List<MqttReasonCodes.SubAck> codes);

    MqttMessage createUnSubAckMessage(int msgId, List<MqttReasonCodes.UnsubAck> codes);

    MqttPubAckMessage createPubAckMsg(int msgId, MqttReasonCodes.PubAck code);

    MqttMessage createPubRecMsg(int msgId, MqttReasonCodes.PubRec code);

    MqttMessage createPubRelMsg(int msgId, MqttReasonCodes.PubRel code);

    MqttMessage createPubCompMsg(int msgId, MqttReasonCodes.PubComp code);

    MqttPublishMessage createPubMsg(PublishMsg pubMsg);

    MqttPublishMessage createPubMsg(PublishMsgProto publishMsgProto, int qos, boolean retain, String topicName, int packetId, MqttProperties properties);

    MqttPublishMessage createPubRetainMsg(int msgId, RetainedMsg retainedMsg);

    MqttMessage createPingRespMsg();

    MqttMessage createDisconnectMsg(MqttReasonCodes.Disconnect code);

    MqttMessage createMqttAuthMsg(String authMethod, byte[] authData, MqttReasonCodes.Auth authReasonCode);
}
