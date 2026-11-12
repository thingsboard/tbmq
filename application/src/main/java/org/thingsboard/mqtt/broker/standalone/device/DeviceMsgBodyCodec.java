/**
 * Copyright © 2016-2026 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.standalone.device;

import com.google.protobuf.InvalidProtocolBufferException;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.dao.messages.NewDeviceMsg;
import org.thingsboard.mqtt.broker.dao.messages.StoredDeviceMsg;
import org.thingsboard.mqtt.broker.gen.queue.DevicePublishMsgProto;

/**
 * Converts between a {@link DevicePublishMsg} and the opaque body the device queue store keeps: the bytes of a
 * {@link DevicePublishMsgProto}. The packet id, the packet type and the owner of a stored message are not read from
 * the body; they come from the entry header and the queue key. The Message Expiry Interval is written to the body
 * as the message carried it, so a later delivery can compute the interval left.
 */
public final class DeviceMsgBodyCodec {

    private DeviceMsgBodyCodec() {
    }

    public static NewDeviceMsg toNew(DevicePublishMsg msg) {
        DevicePublishMsgProto.Builder body = ProtoConverter.toDevicePublishMsgProto(msg).toBuilder();
        Integer msgExpiryInterval = msg.getMsgExpiryInterval();
        if (msgExpiryInterval != null) {
            body.setMsgExpiryInterval(msgExpiryInterval);
        }
        return new NewDeviceMsg(body.build().toByteArray(), msg.getTime(), msgExpiryInterval);
    }

    public static DevicePublishMsg toMsg(String queueKey, StoredDeviceMsg stored) {
        DevicePublishMsgProto body = parse(stored.body());
        DevicePublishMsg msg = ProtoConverter.protoToDevicePublishMsg(body);
        msg.setClientId(queueKey);
        msg.setPacketId(stored.packetId());
        msg.setPacketType(stored.packetType());
        if (body.hasMsgExpiryInterval()) {
            msg.setMsgExpiryInterval(body.getMsgExpiryInterval());
        }
        return msg;
    }

    /**
     * Lowers the QoS to {@code maxQos} (never raises it) and adds {@code subscriptionId} to the subscription
     * identifiers when it is positive.
     */
    public static byte[] withQosAndSubscriptionId(byte[] body, int maxQos, int subscriptionId) {
        DevicePublishMsgProto.Builder builder = parse(body).toBuilder();
        builder.setQos(Math.min(maxQos, builder.getQos()));
        if (subscriptionId > 0) {
            builder.getMqttPropertiesBuilder().addSubscriptionIds(subscriptionId);
        }
        return builder.build().toByteArray();
    }

    private static DevicePublishMsgProto parse(byte[] body) {
        try {
            return DevicePublishMsgProto.parseFrom(body);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException("A stored device message body is not a DevicePublishMsgProto", e);
        }
    }

}
