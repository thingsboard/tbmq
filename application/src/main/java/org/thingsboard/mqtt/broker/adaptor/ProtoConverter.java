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
package org.thingsboard.mqtt.broker.adaptor;

import com.google.protobuf.ByteString;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;

@Slf4j
public class ProtoConverter {
    public static QueueProtos.PublishMsgProto convertToPublishProtoMessage(QueueProtos.SessionInfoProto sessionInfoProto, MqttPublishMessage mqttPublishMessage) {
        byte[] bytes = toBytes(mqttPublishMessage.payload());
        return QueueProtos.PublishMsgProto.newBuilder()
                .setPacketId(mqttPublishMessage.variableHeader().packetId())
                .setTopicName(mqttPublishMessage.variableHeader().topicName())
                .setQos(mqttPublishMessage.fixedHeader().qosLevel().value())
                .setRetain(mqttPublishMessage.fixedHeader().isRetain())
                .setDuplicate(mqttPublishMessage.fixedHeader().isDup())
                .setPayload(ByteString.copyFrom(bytes))
                .setSessionInfo(sessionInfoProto)
                .build();
    }

    private static byte[] toBytes(ByteBuf inbound) {
        byte[] bytes = new byte[inbound.readableBytes()];
        int readerIndex = inbound.readerIndex();
        inbound.getBytes(readerIndex, bytes);
        return bytes;
    }
}
