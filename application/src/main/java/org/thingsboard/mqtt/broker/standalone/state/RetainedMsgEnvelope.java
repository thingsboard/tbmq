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
package org.thingsboard.mqtt.broker.standalone.state;

import com.google.protobuf.InvalidProtocolBufferException;
import org.thingsboard.mqtt.broker.gen.queue.RetainedMsgProto;

import java.nio.ByteBuffer;

/**
 * The value stored for a retained message: one version byte, one flags byte, a four-byte message expiry interval and
 * then the {@link RetainedMsgProto}. The cluster edition carries the interval in a Kafka header; the store has no
 * headers, so the interval travels in front of the proto.
 * <p>
 * Bit 0 of the flags says that the message has an interval. The interval is then stored exactly as given, whatever
 * its value: the Message Expiry Interval is an unsigned 32-bit integer that Netty reads as an {@code int}, so
 * 0xFFFFFFFF arrives as -1 and must come back as -1, as the cluster edition does, and no {@code int} can stand for
 * "no interval". Without the flag the interval field is 0 and is ignored on read. Any other flag bit is rejected.
 */
final class RetainedMsgEnvelope {

    static final byte VERSION = 1;

    private static final byte FLAG_HAS_INTERVAL = 1;
    private static final int HEADER_SIZE = Byte.BYTES + Byte.BYTES + Integer.BYTES;

    private RetainedMsgEnvelope() {
    }

    static byte[] encode(Integer messageExpiryInterval, RetainedMsgProto proto) {
        byte[] protoBytes = proto.toByteArray();
        return ByteBuffer.allocate(HEADER_SIZE + protoBytes.length)
                .put(VERSION)
                .put(messageExpiryInterval == null ? 0 : FLAG_HAS_INTERVAL)
                .putInt(messageExpiryInterval == null ? 0 : messageExpiryInterval)
                .put(protoBytes)
                .array();
    }

    static Decoded decode(byte[] value) throws InvalidProtocolBufferException {
        if (value.length < HEADER_SIZE) {
            throw new IllegalArgumentException("Retained message value is too short: " + value.length + " bytes, at least " + HEADER_SIZE + " expected");
        }
        ByteBuffer buffer = ByteBuffer.wrap(value);
        byte version = buffer.get();
        if (version != VERSION) {
            throw new IllegalArgumentException("Unknown retained message value version " + version + ", only version " + VERSION + " is supported");
        }
        byte flags = buffer.get();
        if ((flags & ~FLAG_HAS_INTERVAL) != 0) {
            throw new IllegalArgumentException("Unknown retained message value flags " + (flags & 0xFF) + ", only bit 0 is supported");
        }
        int interval = buffer.getInt();
        RetainedMsgProto proto = RetainedMsgProto.parseFrom(buffer);
        return new Decoded((flags & FLAG_HAS_INTERVAL) != 0 ? interval : null, proto);
    }

    /**
     * @param messageExpiryInterval the interval in seconds, or null when the message was stored without one
     */
    record Decoded(Integer messageExpiryInterval, RetainedMsgProto proto) {
    }

}
