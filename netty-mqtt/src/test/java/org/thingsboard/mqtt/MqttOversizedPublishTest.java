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

import com.google.common.util.concurrent.Futures;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttPublishVariableHeader;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubAckMessage;
import io.netty.handler.codec.mqtt.MqttSubAckPayload;
import io.netty.handler.codec.mqtt.MqttVersion;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An oversized PUBLISH on the pipeline the client builds for a connection, codec included, with a 64-byte limit.
 */
class MqttOversizedPublishTest {

    static final int LIMIT = 64;

    final List<String> handled = new CopyOnWriteArrayList<>();
    final List<String> tooLarge = new CopyOnWriteArrayList<>();
    EmbeddedChannel channel;
    MqttClientImpl client;

    @AfterEach
    void cleanup() {
        if (channel != null) {
            channel.finishAndReleaseAll();
            channel = null;
        }
    }

    @ParameterizedTest(name = "read in chunks of {0} bytes")
    @ValueSource(ints = {1, 2, 3, 5, 8, 13, 1_000})
    void anOversizedPublishLeavesTheConnectionUpAndThePacketsAroundItStillArrive(int chunkSize) {
        // GIVEN
        channel = newChannel();
        repliesWritten(); // the CONNECT
        ByteBuf bytes = Unpooled.buffer();
        bytes.writeBytes(encode(publish("before", MqttQoS.AT_LEAST_ONCE, 1, "small")));
        bytes.writeBytes(encode(publish("too/large", MqttQoS.AT_LEAST_ONCE, 2, "x".repeat(200))));
        bytes.writeBytes(encode(publish("after", MqttQoS.AT_LEAST_ONCE, 3, "small")));

        // WHEN - the bytes arrive split at every kind of boundary: inside a fixed header, a length, a topic
        writeInChunks(bytes, chunkSize);

        // THEN
        assertThat(handled).containsExactly("before", "after");
        assertThat(repliesWritten()).containsExactly("PUBACK 1", "PUBACK 2", "PUBACK 3");
        assertThat(channel.isOpen()).isTrue();
    }

    static Stream<Arguments> longRemainingLengths() {
        // a 16 KiB payload takes a 3-byte remaining length, a 2 MiB one a 4-byte one; the limit is half the payload
        return Stream.of(16 * 1024, 2 * 1024 * 1024).flatMap(payloadBytes -> IntStream.of(1, 2, 3, 5, 8, 13)
                .mapToObj(chunkSize -> Arguments.of(payloadBytes, payloadBytes / 2, chunkSize)));
    }

    @ParameterizedTest(name = "a {0}-byte payload over a limit of {1}, read in chunks of {2} bytes")
    @MethodSource("longRemainingLengths")
    void anOversizedPublishWithALongRemainingLengthIsSkippedWhereverTheBytesAreSplit(int payloadBytes, int limit, int chunkSize) {
        // GIVEN
        channel = newChannel(limit);
        repliesWritten(); // the CONNECT
        ByteBuf oversized = encode(publish("too/large", MqttQoS.AT_LEAST_ONCE, 2, "x".repeat(payloadBytes)));
        int lengthBytes = 1;
        while ((oversized.getUnsignedByte(lengthBytes) & 0x80) != 0) {
            lengthBytes++;
        }
        assertThat(lengthBytes).isEqualTo(payloadBytes < 2 * 1024 * 1024 ? 3 : 4);
        ByteBuf before = encode(publish("before", MqttQoS.AT_LEAST_ONCE, 1, "small"));
        ByteBuf after = encode(publish("after", MqttQoS.AT_LEAST_ONCE, 3, "small"));

        // WHEN - all of it split, the 2 MiB too: each length byte arrives on its own, or with its neighbours
        ByteBuf bytes = Unpooled.buffer().writeBytes(before).writeBytes(oversized).writeBytes(after);
        before.release();
        oversized.release();
        after.release();
        writeInChunks(bytes, chunkSize);

        // THEN
        assertThat(handled).containsExactly("before", "after");
        assertThat(tooLarge).containsExactly("too/large QoS 1 " + (2 + "too/large".length() + 2 + payloadBytes) + " bytes");
        assertThat(repliesWritten()).containsExactly("PUBACK 1", "PUBACK 2", "PUBACK 3");
        assertThat(channel.isOpen()).isTrue();
    }

    @Test
    void anOversizedPublishIsReportedWithItsTopicQosAndSize() {
        // GIVEN
        channel = newChannel();

        // WHEN
        writeInChunks(encode(publish("too/large", MqttQoS.AT_LEAST_ONCE, 2, "x".repeat(200))), 7);

        // THEN - the remaining length: a 2-byte topic length, the topic, a 2-byte packet id and the payload
        assertThat(tooLarge).containsExactly("too/large QoS 1 " + (2 + "too/large".length() + 2 + 200) + " bytes");
    }

    @Test
    void aPublishAtTheLimitIsHandledAndOneByteOverIsSkipped() {
        // GIVEN - remaining lengths of exactly the limit and one more: the guard and netty's MqttDecoder must draw the
        // line at the same byte, or a PUBLISH between the two fails the decoder and closes the connection
        String topic = "edge";
        int payloadAtLimit = LIMIT - (2 + topic.length() + 2);
        channel = newChannel();
        repliesWritten(); // the CONNECT

        // WHEN
        writeInChunks(encode(publish(topic, MqttQoS.AT_LEAST_ONCE, 1, "x".repeat(payloadAtLimit))), 1_000);
        writeInChunks(encode(publish(topic, MqttQoS.AT_LEAST_ONCE, 2, "x".repeat(payloadAtLimit + 1))), 1_000);

        // THEN
        assertThat(handled).containsExactly(topic);
        assertThat(tooLarge).containsExactly(topic + " QoS 1 " + (LIMIT + 1) + " bytes");
        assertThat(repliesWritten()).containsExactly("PUBACK 1", "PUBACK 2");
        assertThat(channel.isOpen()).isTrue();
    }

    @Test
    void anOversizedQos0PublishIsSkippedAndReportedWithoutAnAck() {
        // GIVEN
        channel = newChannel();
        repliesWritten(); // the CONNECT

        // WHEN
        writeInChunks(encode(publish("too/large", MqttQoS.AT_MOST_ONCE, 0, "x".repeat(200))), 3);
        writeInChunks(encode(publish("after", MqttQoS.AT_MOST_ONCE, 0, "small")), 3);

        // THEN
        assertThat(tooLarge).hasSize(1);
        assertThat(handled).containsExactly("after");
        assertThat(repliesWritten()).isEmpty();
    }

    @Test
    void aResentOversizedQos2PublishIsAnsweredAgainAndReportedOnce() {
        // GIVEN - under MQTT 3.1.1 its PUBREC carries no failure, so a PUBREL follows and a resend may come first
        channel = newChannel();
        repliesWritten(); // the CONNECT
        writeInChunks(encode(publish("too/large", MqttQoS.EXACTLY_ONCE, 7, "x".repeat(200))), 1_000);

        // WHEN
        MqttPublishMessage resend = new MqttPublishMessage(new MqttFixedHeader(MqttMessageType.PUBLISH, true, MqttQoS.EXACTLY_ONCE, false, 0),
                new MqttPublishVariableHeader("too/large", 7), Unpooled.copiedBuffer("x".repeat(200), StandardCharsets.UTF_8));
        writeInChunks(encode(resend), 1_000);
        writeInChunks(encode(new MqttMessage(new MqttFixedHeader(MqttMessageType.PUBREL, false, MqttQoS.AT_LEAST_ONCE, false, 0),
                MqttMessageIdVariableHeader.from(7))), 1_000);

        // THEN
        assertThat(repliesWritten()).containsExactly("PUBREC 7", "PUBREC 7", "PUBCOMP 7");
        assertThat(tooLarge).hasSize(1);
    }

    @Test
    void anOversizedPacketOfAnotherTypeStillClosesTheConnection() {
        // GIVEN - a SUBACK with 70 return codes: a server that sends one over the limit is broken
        channel = newChannel();
        MqttSubAckMessage subAck = new MqttSubAckMessage(new MqttFixedHeader(MqttMessageType.SUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                MqttMessageIdVariableHeader.from(1), new MqttSubAckPayload(new int[70]));

        // WHEN
        writeInChunks(encode(subAck), 1_000);

        // THEN
        assertThat(channel.isOpen()).isFalse();
    }

    /** The client's pipeline for one connection, as MqttChannelInitializer builds it, keep-alive off. */
    private EmbeddedChannel newChannel() {
        return newChannel(LIMIT);
    }

    /** The same, with the given limit. */
    private EmbeddedChannel newChannel(int limit) {
        MqttClientConfig clientConfig = MqttChannelHandlerTest.testConfig(MqttVersion.MQTT_3_1_1);
        clientConfig.setMaxBytesInMessage(limit);
        clientConfig.setTimeoutSeconds(0);
        client = new MqttClientImpl(clientConfig, msg -> {
            handled.add(msg.variableHeader().topicName());
            return Futures.immediateVoidFuture();
        }, MqttChannelHandlerTest.DIRECT_EXECUTOR);
        client.setCallback(new MqttClientCallback() {
            @Override
            public void connectionLost(Throwable cause) {
            }

            @Override
            public void onSuccessfulReconnect() {
            }

            @Override
            public void onPublishTooLarge(String topic, MqttQoS qos, int remainingLength) {
                tooLarge.add(topic + " QoS " + qos.value() + " " + remainingLength + " bytes");
            }
        });
        EmbeddedChannel embeddedChannel = new EmbeddedChannel(
                client.new MqttChannelInitializer(ImmediateEventExecutor.INSTANCE.newPromise(), "localhost", 1883, null));
        client.setEventLoop(embeddedChannel.eventLoop());
        return embeddedChannel;
    }

    private void writeInChunks(ByteBuf bytes, int chunkSize) {
        while (bytes.isReadable()) {
            channel.writeInbound(bytes.readRetainedSlice(Math.min(chunkSize, bytes.readableBytes())));
        }
        bytes.release();
    }

    private static MqttPublishMessage publish(String topic, MqttQoS qos, int packetId, String payload) {
        return new MqttPublishMessage(new MqttFixedHeader(MqttMessageType.PUBLISH, false, qos, false, 0),
                new MqttPublishVariableHeader(topic, packetId), Unpooled.copiedBuffer(payload, StandardCharsets.UTF_8));
    }

    static ByteBuf encode(MqttMessage message) {
        EmbeddedChannel encoder = new EmbeddedChannel(MqttEncoder.INSTANCE);
        encoder.writeOutbound(message);
        ByteBuf bytes = Unpooled.buffer();
        for (ByteBuf part; (part = encoder.readOutbound()) != null; ) {
            bytes.writeBytes(part);
            part.release();
        }
        encoder.finishAndReleaseAll();
        return bytes;
    }

    /** The PUBACK, PUBREC and PUBCOMP the client has written so far, decoded, as type and packet id; drains them. */
    private List<String> repliesWritten() {
        EmbeddedChannel decoder = new EmbeddedChannel(new MqttDecoder());
        for (Object out; (out = channel.readOutbound()) != null; ) {
            decoder.writeInbound(out);
        }
        List<String> replies = new ArrayList<>();
        for (Object in; (in = decoder.readInbound()) != null; ) {
            MqttMessage message = (MqttMessage) in;
            MqttMessageType type = message.fixedHeader().messageType();
            if (type == MqttMessageType.PUBACK || type == MqttMessageType.PUBREC || type == MqttMessageType.PUBCOMP) {
                replies.add(type + " " + ((MqttMessageIdVariableHeader) message.variableHeader()).messageId());
            }
            ReferenceCountUtil.release(in);
        }
        decoder.finishAndReleaseAll();
        return replies;
    }
}
