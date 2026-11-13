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

import java.nio.ByteBuffer;

/**
 * Converts between a {@link DevicePublishMsg} and the opaque body the device queue store keeps. The body is a frame:
 * a format byte (1), a flag byte (1 when a Message Expiry Interval is present, else 0), the interval as 4 bytes big-endian
 * (0 when absent) and the bytes of a {@link DevicePublishMsgProto}. {@code DevicePublishMsgProto} is a cluster wire
 * format and carries no interval, so the frame keeps the interval as the message carried it, for a later delivery to
 * compute the interval left. The packet id, the packet type and the owner of a stored message are not read from the
 * body; they come from the entry header and the queue key.
 */
public final class DeviceMsgBodyCodec {

    private static final byte FORMAT = 1;
    private static final int FRAME_HEADER_SIZE = 6;

    private DeviceMsgBodyCodec() {
    }

    public static NewDeviceMsg toNew(DevicePublishMsg msg) {
        Integer msgExpiryInterval = msg.getMsgExpiryInterval();
        byte[] proto = ProtoConverter.toDevicePublishMsgProto(msg).toByteArray();
        return new NewDeviceMsg(frame(msgExpiryInterval, proto), msg.getTime(), msgExpiryInterval);
    }

    public static DevicePublishMsg toMsg(String queueKey, StoredDeviceMsg stored) {
        ByteBuffer frame = openFrame(stored.body());
        boolean hasInterval = frame.get() == 1;
        int msgExpiryInterval = frame.getInt();
        DevicePublishMsg msg = ProtoConverter.protoToDevicePublishMsg(parse(frame));
        msg.setClientId(queueKey);
        msg.setPacketId(stored.packetId());
        msg.setPacketType(stored.packetType());
        if (hasInterval) {
            msg.setMsgExpiryInterval(msgExpiryInterval);
        }
        return msg;
    }

    /**
     * Lowers the QoS to {@code maxQos} (never raises it) and adds {@code subscriptionId} to the subscription
     * identifiers when it is positive. The frame header is kept as it is.
     */
    public static byte[] withQosAndSubscriptionId(byte[] body, int maxQos, int subscriptionId) {
        ByteBuffer frame = openFrame(body);
        byte[] header = new byte[FRAME_HEADER_SIZE - 1];
        frame.get(header);
        DevicePublishMsgProto.Builder builder = parse(frame).toBuilder();
        builder.setQos(Math.min(maxQos, builder.getQos()));
        if (subscriptionId > 0) {
            builder.getMqttPropertiesBuilder().addSubscriptionIds(subscriptionId);
        }
        byte[] proto = builder.build().toByteArray();
        ByteBuffer result = ByteBuffer.allocate(1 + header.length + proto.length);
        result.put(FORMAT).put(header).put(proto);
        return result.array();
    }

    private static byte[] frame(Integer msgExpiryInterval, byte[] proto) {
        ByteBuffer frame = ByteBuffer.allocate(FRAME_HEADER_SIZE + proto.length);
        frame.put(FORMAT);
        frame.put((byte) (msgExpiryInterval != null ? 1 : 0));
        frame.putInt(msgExpiryInterval != null ? msgExpiryInterval : 0);
        frame.put(proto);
        return frame.array();
    }

    /**
     * @return the frame positioned after the format byte
     */
    private static ByteBuffer openFrame(byte[] body) {
        if (body == null || body.length < FRAME_HEADER_SIZE) {
            throw new IllegalStateException("A stored device message body is shorter than its " + FRAME_HEADER_SIZE + " byte frame header: "
                    + (body == null ? "null" : body.length + " bytes"));
        }
        ByteBuffer frame = ByteBuffer.wrap(body);
        byte format = frame.get();
        if (format != FORMAT) {
            throw new IllegalStateException("A stored device message body has an unknown format " + format + ", expected " + FORMAT);
        }
        return frame;
    }

    private static DevicePublishMsgProto parse(ByteBuffer frame) {
        try {
            return DevicePublishMsgProto.parseFrom(frame);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException("A stored device message body does not hold a DevicePublishMsgProto", e);
        }
    }

}
