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
 * The value stored for a retained message: one version byte, a four-byte message expiry interval (-1 when the
 * message has none) and then the {@link RetainedMsgProto}. The cluster edition carries the interval in a Kafka
 * header; the store has no headers, so the interval travels in front of the proto.
 */
final class RetainedMsgEnvelope {

    static final byte VERSION = 1;

    private static final int NO_INTERVAL = -1;
    private static final int HEADER_SIZE = Byte.BYTES + Integer.BYTES;

    private RetainedMsgEnvelope() {
    }

    static byte[] encode(Integer messageExpiryInterval, RetainedMsgProto proto) {
        byte[] protoBytes = proto.toByteArray();
        return ByteBuffer.allocate(HEADER_SIZE + protoBytes.length)
                .put(VERSION)
                .putInt(messageExpiryInterval == null ? NO_INTERVAL : messageExpiryInterval)
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
        int interval = buffer.getInt();
        RetainedMsgProto proto = RetainedMsgProto.parseFrom(buffer);
        return new Decoded(interval == NO_INTERVAL ? null : interval, proto);
    }

    /**
     * @param messageExpiryInterval the interval in seconds, or null when the message was stored without one
     */
    record Decoded(Integer messageExpiryInterval, RetainedMsgProto proto) {
    }

}
