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
package org.thingsboard.mqtt;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttQoS;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Keeps an oversized PUBLISH away from {@link io.netty.handler.codec.mqtt.MqttDecoder}, which fails on a packet over its
 * limit and then discards every byte until the connection closes.
 * <p>
 * Sits in front of the decoder, with the same limit on the remaining length. A packet within the limit passes through
 * unchanged, byte for byte. Of an oversized PUBLISH only the topic and packet id are read; the rest is skipped as it
 * arrives, never buffered, and an {@link MqttOversizedPublish} takes the packet's place in the pipeline, in order with
 * the packets around it. Any other oversized packet, and a malformed fixed header, pass through for the decoder to fail
 * on: a server that sends one is broken.
 */
final class MqttOversizedPublishGuard extends ByteToMessageDecoder {

    private enum State { FRAME_START, PASS, PUBLISH_HEADER, SKIP, PASS_ALL }

    private final int maxBytesInMessage;
    private State state = State.FRAME_START;
    /** Bytes of the current packet still to pass on (PASS) or to skip (SKIP). */
    private long remaining;
    /** Of the oversized PUBLISH being skipped. */
    private int fixedHeaderBytes;
    private int remainingLength;
    private MqttQoS qos;
    private String topic;
    private int packetId;

    MqttOversizedPublishGuard(int maxBytesInMessage) {
        this.maxBytesInMessage = maxBytesInMessage;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        while (true) {
            switch (state) {
                case FRAME_START -> {
                    if (!readFixedHeader(in)) {
                        return;
                    }
                }
                case PASS -> {
                    if (!in.isReadable()) {
                        return;
                    }
                    int n = (int) Math.min(in.readableBytes(), remaining);
                    out.add(in.readRetainedSlice(n));
                    remaining -= n;
                    if (remaining == 0) {
                        state = State.FRAME_START;
                    }
                }
                case PUBLISH_HEADER -> {
                    if (!readPublishHeader(in)) {
                        return;
                    }
                }
                case SKIP -> {
                    if (remaining > 0) {
                        if (!in.isReadable()) {
                            return;
                        }
                        int n = (int) Math.min(in.readableBytes(), remaining);
                        in.skipBytes(n);
                        remaining -= n;
                    }
                    if (remaining == 0) {
                        out.add(new MqttOversizedPublish(topic, qos, packetId, remainingLength));
                        topic = null;
                        state = State.FRAME_START;
                    }
                }
                case PASS_ALL -> {
                    if (!in.isReadable()) {
                        return;
                    }
                    out.add(in.readRetainedSlice(in.readableBytes()));
                }
            }
        }
    }

    /**
     * Reads the fixed header of the next packet once all of it has arrived - its first byte and the one to four bytes
     * of its remaining length - and decides what to do with the packet. Consumes nothing; returns false while bytes are
     * missing.
     */
    private boolean readFixedHeader(ByteBuf in) {
        int start = in.readerIndex();
        if (in.readableBytes() < 2) {
            return false;
        }
        int firstByte = in.getUnsignedByte(start);
        int length = 0;
        int multiplier = 1;
        int lengthBytes = 0;
        int digit;
        do {
            if (lengthBytes == 4) {
                // a fifth length byte is malformed: pass everything on, for the decoder to fail on
                state = State.PASS_ALL;
                return true;
            }
            if (in.readableBytes() < 2 + lengthBytes) {
                return false;
            }
            digit = in.getUnsignedByte(start + 1 + lengthBytes);
            length += (digit & 0x7F) * multiplier;
            multiplier *= 128;
            lengthBytes++;
        } while ((digit & 0x80) != 0);

        int qosBits = (firstByte & 0x06) >> 1;
        if ((firstByte >> 4) == MqttMessageType.PUBLISH.value() && length > maxBytesInMessage && qosBits != 3) {
            this.fixedHeaderBytes = 1 + lengthBytes;
            this.remainingLength = length;
            this.qos = MqttQoS.valueOf(qosBits);
            state = State.PUBLISH_HEADER;
        } else {
            remaining = 1L + lengthBytes + length;
            state = State.PASS;
        }
        return true;
    }

    /**
     * Reads the topic and packet id of the oversized PUBLISH once they have arrived, and skips its fixed header and them.
     * Returns false while bytes are missing.
     */
    private boolean readPublishHeader(ByteBuf in) {
        int start = in.readerIndex();
        if (in.readableBytes() < fixedHeaderBytes + 2) {
            return false;
        }
        int topicLength = in.getUnsignedShort(start + fixedHeaderBytes);
        int headerBytes = 2 + topicLength + (qos == MqttQoS.AT_MOST_ONCE ? 0 : 2);
        if (headerBytes > remainingLength) {
            // the packet cannot hold its own variable header: malformed, for the decoder to fail on
            state = State.PASS_ALL;
            return true;
        }
        if (in.readableBytes() < fixedHeaderBytes + headerBytes) {
            return false;
        }
        topic = in.toString(start + fixedHeaderBytes + 2, topicLength, StandardCharsets.UTF_8);
        packetId = qos == MqttQoS.AT_MOST_ONCE ? -1 : in.getUnsignedShort(start + fixedHeaderBytes + 2 + topicLength);
        in.skipBytes(fixedHeaderBytes + headerBytes);
        remaining = (long) remainingLength - headerBytes;
        state = State.SKIP;
        return true;
    }
}
