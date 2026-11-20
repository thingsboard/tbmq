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
package org.thingsboard.mqtt.broker.lite.spec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.mqtt.MqttConnAckMessage;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttPubReplyMessageVariableHeader;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttReasonCodeAndPropertiesVariableHeader;
import io.netty.handler.codec.mqtt.MqttSubAckMessage;
import io.netty.handler.codec.mqtt.MqttVersion;
import io.netty.util.ReferenceCountUtil;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * A minimal MQTT client over a plain socket for the conformance cases. It acknowledges nothing by itself, so a test
 * decides which PUBACK, PUBREC, PUBREL or PUBCOMP it sends and which it withholds, and it shows every packet the broker
 * sends with its DUP flag, packet id and Message Expiry Interval. The encoder and the decoder share one channel, so the
 * decoder knows the protocol version from the encoded CONNECT. One thread uses a client at a time.
 */
public final class RawMqttClient implements AutoCloseable {

    /**
     * Keeps an MQTT 5 session after the connection closes: long enough for any test, short enough to clean itself up.
     */
    private static final int SESSION_EXPIRY_INTERVAL_SEC = 300;

    private static final int KEEP_ALIVE_SEC = 60;
    private static final Duration ACK_TIMEOUT = Duration.ofSeconds(10);

    private final String clientId;
    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final EmbeddedChannel codec = new EmbeddedChannel(MqttEncoder.INSTANCE, new MqttDecoder());
    private final byte[] readBuffer = new byte[4096];
    private boolean sessionPresent;

    private RawMqttClient(String clientId, int port) throws IOException {
        this.clientId = clientId;
        socket = new Socket("localhost", port);
        socket.setTcpNoDelay(true);
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }

    /**
     * Connects with Clean Start 0 and, on MQTT 5, a Session Expiry Interval of {@value #SESSION_EXPIRY_INTERVAL_SEC} s,
     * so the session outlives the connection.
     */
    public static RawMqttClient connectPersistent(int port, String clientId, MqttVersion version) throws IOException {
        return connect(port, clientId, version, false, SESSION_EXPIRY_INTERVAL_SEC);
    }

    /**
     * Connects an MQTT 5 client with Clean Start 0 and the given Session Expiry Interval in seconds.
     */
    public static RawMqttClient connectPersistent(int port, String clientId, int sessionExpiryIntervalSec) throws IOException {
        return connect(port, clientId, MqttVersion.MQTT_5, false, sessionExpiryIntervalSec);
    }

    /**
     * Connects with Clean Start 1 and no session after the connection.
     */
    public static RawMqttClient connectClean(int port, String clientId, MqttVersion version) throws IOException {
        return connect(port, clientId, version, true, 0);
    }

    /**
     * Sends a CONNECT, with a username and password when {@code username} is not {@code null}, and returns the return
     * code of the CONNACK, whatever it is; the connection is closed afterwards.
     */
    public static MqttConnectReturnCode connectForReturnCode(int port, String clientId, MqttVersion version, boolean cleanStart,
                                                             int sessionExpiryIntervalSec, String username, String password) throws IOException {
        try (RawMqttClient client = new RawMqttClient(clientId, port)) {
            MqttMessageBuilders.ConnectBuilder connect = connectBuilder(clientId, version, cleanStart, sessionExpiryIntervalSec);
            if (username != null) {
                connect.username(username).password(password.getBytes(StandardCharsets.UTF_8));
            }
            client.send(connect.build());
            MqttMessage connAck = client.nextMessage(ACK_TIMEOUT);
            try {
                if (!(connAck instanceof MqttConnAckMessage ack)) {
                    throw new AssertionError("[" + clientId + "] Expected a CONNACK, got " + connAck);
                }
                return ack.variableHeader().connectReturnCode();
            } finally {
                ReferenceCountUtil.release(connAck);
            }
        }
    }

    private static MqttMessageBuilders.ConnectBuilder connectBuilder(String clientId, MqttVersion version, boolean cleanStart,
                                                                     int sessionExpiryIntervalSec) {
        MqttProperties properties = new MqttProperties();
        if (version == MqttVersion.MQTT_5 && !cleanStart) {
            properties.add(new MqttProperties.IntegerProperty(
                    MqttProperties.MqttPropertyType.SESSION_EXPIRY_INTERVAL.value(), sessionExpiryIntervalSec));
        }
        return MqttMessageBuilders.connect()
                .clientId(clientId)
                .protocolVersion(version)
                .cleanSession(cleanStart)
                .keepAlive(KEEP_ALIVE_SEC)
                .properties(properties);
    }

    private static RawMqttClient connect(int port, String clientId, MqttVersion version, boolean cleanStart,
                                         int sessionExpiryIntervalSec) throws IOException {
        RawMqttClient client = new RawMqttClient(clientId, port);
        try {
            client.send(connectBuilder(clientId, version, cleanStart, sessionExpiryIntervalSec).build());
            MqttMessage connAck = client.nextMessage(ACK_TIMEOUT);
            if (!(connAck instanceof MqttConnAckMessage accepted)
                    || accepted.variableHeader().connectReturnCode() != MqttConnectReturnCode.CONNECTION_ACCEPTED) {
                throw new AssertionError("[" + clientId + "] Expected an accepting CONNACK, got " + connAck);
            }
            client.sessionPresent = accepted.variableHeader().isSessionPresent();
            return client;
        } catch (IOException | RuntimeException | Error e) {
            client.close();
            throw e;
        }
    }

    public boolean sessionPresent() {
        return sessionPresent;
    }

    /**
     * Subscribes and checks that the broker granted the QoS asked for.
     */
    public void subscribe(String topicFilter, int qos) throws IOException {
        int packetId = 1;
        send(MqttMessageBuilders.subscribe()
                .messageId(packetId)
                .addSubscription(MqttQoS.valueOf(qos), topicFilter)
                .build());
        Packet subAck = next(ACK_TIMEOUT);
        if (subAck.type() != MqttMessageType.SUBACK || subAck.packetId() != packetId || subAck.reasonCode() != qos) {
            throw new AssertionError("[" + clientId + "] Expected a SUBACK granting QoS " + qos + " to " + topicFilter + ", got " + subAck);
        }
    }

    /**
     * Sends a PUBLISH and returns at once; a QoS 1 or 2 publish is answered by a PUBACK or a PUBREC the test reads.
     *
     * @param msgExpiryInterval the Message Expiry Interval in seconds, or {@code null} to send none
     */
    public void publish(String topic, int qos, int packetId, String payload, Integer msgExpiryInterval) throws IOException {
        MqttProperties properties = new MqttProperties();
        if (msgExpiryInterval != null) {
            properties.add(new MqttProperties.IntegerProperty(
                    MqttProperties.MqttPropertyType.PUBLICATION_EXPIRY_INTERVAL.value(), msgExpiryInterval));
        }
        send(MqttMessageBuilders.publish()
                .topicName(topic)
                .qos(MqttQoS.valueOf(qos))
                .messageId(packetId)
                .retained(false)
                .payload(Unpooled.copiedBuffer(payload, StandardCharsets.UTF_8))
                .properties(properties)
                .build());
    }

    /**
     * Publishes at QoS 1 and returns the PUBACK's reason code.
     */
    public int publishQos1(String topic, int packetId, String payload, Integer msgExpiryInterval) throws IOException {
        publish(topic, 1, packetId, payload, msgExpiryInterval);
        return expect(MqttMessageType.PUBACK, packetId).reasonCode();
    }

    /**
     * Publishes at QoS 2 and returns the PUBREC's reason code; a successful PUBREC is followed by PUBREL and PUBCOMP,
     * which completes the flow.
     */
    public int publishQos2(String topic, int packetId, String payload, Integer msgExpiryInterval) throws IOException {
        publish(topic, 2, packetId, payload, msgExpiryInterval);
        Packet pubRec = expect(MqttMessageType.PUBREC, packetId);
        if (pubRec.reasonCode() == 0) {
            pubRel(packetId);
            expect(MqttMessageType.PUBCOMP, packetId);
        }
        return pubRec.reasonCode();
    }

    public void pubAck(int packetId) throws IOException {
        sendPubReply(MqttMessageType.PUBACK, MqttQoS.AT_MOST_ONCE, packetId);
    }

    void pubRec(int packetId) throws IOException {
        sendPubReply(MqttMessageType.PUBREC, MqttQoS.AT_MOST_ONCE, packetId);
    }

    void pubRel(int packetId) throws IOException {
        // [MQTT-3.6.1-1] the fixed header flags of a PUBREL are 0010
        sendPubReply(MqttMessageType.PUBREL, MqttQoS.AT_LEAST_ONCE, packetId);
    }

    void pubComp(int packetId) throws IOException {
        sendPubReply(MqttMessageType.PUBCOMP, MqttQoS.AT_MOST_ONCE, packetId);
    }

    /**
     * @return the next packet, which must be of {@code type} with {@code packetId}
     */
    public Packet expect(MqttMessageType type, int packetId) throws IOException {
        Packet packet = next(ACK_TIMEOUT);
        if (packet.type() != type || packet.packetId() != packetId) {
            throw new AssertionError("[" + clientId + "] Expected " + type + " " + packetId + ", got " + packet);
        }
        return packet;
    }

    /**
     * @return the next packet the broker sends; fails when none arrives within {@code timeout}
     * @throws EOFException when the broker closed the connection first
     */
    public Packet next(Duration timeout) throws IOException {
        return poll(timeout).orElseThrow(() ->
                new AssertionError("[" + clientId + "] No packet from the broker within " + timeout.toMillis() + " ms"));
    }

    /**
     * @return the next packet the broker sends, or empty when none arrives within {@code timeout}
     * @throws EOFException when the broker closed the connection first
     */
    public Optional<Packet> poll(Duration timeout) throws IOException {
        MqttMessage msg = pollMessage(timeout);
        if (msg == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Packet.of(msg));
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    /**
     * Sends a DISCONNECT with reason code 0, which keeps the session, and closes the socket.
     */
    public void disconnect() throws IOException {
        try {
            send(MqttMessageBuilders.disconnect().build());
        } finally {
            close();
        }
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // the socket is gone either way
        }
        try {
            codec.finishAndReleaseAll();
        } catch (RuntimeException ignored) {
            // a decoder failure was already reported to the reader, if any
        }
    }

    private void sendPubReply(MqttMessageType type, MqttQoS fixedHeaderQos, int packetId) throws IOException {
        send(new MqttMessage(new MqttFixedHeader(type, false, fixedHeaderQos, false, 0),
                new MqttPubReplyMessageVariableHeader(packetId, MqttPubReplyMessageVariableHeader.REASON_CODE_OK,
                        MqttProperties.NO_PROPERTIES)));
    }

    private void send(MqttMessage msg) throws IOException {
        codec.writeOutbound(msg);
        ByteBuf encoded;
        while ((encoded = codec.readOutbound()) != null) {
            try {
                byte[] bytes = new byte[encoded.readableBytes()];
                encoded.readBytes(bytes);
                out.write(bytes);
            } finally {
                encoded.release();
            }
        }
        out.flush();
    }

    private MqttMessage nextMessage(Duration timeout) throws IOException {
        MqttMessage msg = pollMessage(timeout);
        if (msg == null) {
            throw new AssertionError("[" + clientId + "] No packet from the broker within " + timeout.toMillis() + " ms");
        }
        return msg;
    }

    /**
     * @return the next decoded message, or {@code null} when none arrives within {@code timeout}; the caller releases it
     */
    private MqttMessage pollMessage(Duration timeout) throws IOException {
        long deadline = System.nanoTime() + timeout.toNanos();
        MqttMessage msg;
        while ((msg = codec.readInbound()) == null) {
            long remainingMs = Duration.ofNanos(deadline - System.nanoTime()).toMillis();
            if (remainingMs <= 0) {
                return null;
            }
            int read;
            try {
                socket.setSoTimeout((int) remainingMs);
                read = in.read(readBuffer);
            } catch (SocketTimeoutException e) {
                return null;
            } catch (SocketException e) {
                throw closed(e);
            }
            if (read < 0) {
                throw closed(null);
            }
            codec.writeInbound(Unpooled.copiedBuffer(readBuffer, 0, read));
        }
        if (msg.decoderResult().isFailure()) {
            ReferenceCountUtil.release(msg);
            throw new IOException("[" + clientId + "] Failed to decode a packet from the broker", msg.decoderResult().cause());
        }
        return msg;
    }

    private EOFException closed(SocketException cause) {
        EOFException e = new EOFException("[" + clientId + "] The broker closed the connection");
        if (cause != null) {
            e.initCause(cause);
        }
        return e;
    }

    /**
     * A packet from the broker. {@code reasonCode} is the reason code of a PUBACK, PUBREC, PUBREL, PUBCOMP or DISCONNECT,
     * or the first granted QoS of a SUBACK. {@code msgExpiryInterval} is the Message Expiry Interval of a PUBLISH,
     * {@code null} when it has none.
     */
    public record Packet(MqttMessageType type, int packetId, boolean dup, int qos, String topic, String payload,
                  Integer msgExpiryInterval, int reasonCode) {

        static Packet of(MqttMessage msg) {
            MqttFixedHeader header = msg.fixedHeader();
            if (msg instanceof MqttPublishMessage publish) {
                MqttProperties.MqttProperty<?> interval = publish.variableHeader().properties()
                        .getProperty(MqttProperties.MqttPropertyType.PUBLICATION_EXPIRY_INTERVAL.value());
                return new Packet(MqttMessageType.PUBLISH, publish.variableHeader().packetId(), header.isDup(),
                        header.qosLevel().value(), publish.variableHeader().topicName(),
                        publish.payload().toString(StandardCharsets.UTF_8),
                        interval == null ? null : (Integer) interval.value(), 0);
            }
            if (msg instanceof MqttSubAckMessage subAck) {
                return new Packet(MqttMessageType.SUBACK, subAck.variableHeader().messageId(), false, 0, null, null, null,
                        subAck.payload().reasonCodes().get(0));
            }
            if (msg.variableHeader() instanceof MqttPubReplyMessageVariableHeader reply) {
                return new Packet(header.messageType(), reply.messageId(), false, 0, null, null, null,
                        Byte.toUnsignedInt(reply.reasonCode()));
            }
            if (msg.variableHeader() instanceof MqttReasonCodeAndPropertiesVariableHeader reason) {
                return new Packet(header.messageType(), 0, false, 0, null, null, null, Byte.toUnsignedInt(reason.reasonCode()));
            }
            return new Packet(header.messageType(), 0, false, 0, null, null, null, 0);
        }

    }

}
