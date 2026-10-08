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
package org.thingsboard.mqtt.broker.service.testing.integration;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPubReplyMessageVariableHeader;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttReasonCodes;
import io.netty.handler.codec.mqtt.MqttVersion;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A PUBLISH whose Kafka write fails must not hold back the responses to the PUBLISH packets received after it.
 * The write failure is forced by a producer max.request.size below the size of one of the published messages.
 */
@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = PersistFailurePubResponseIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "queue.kafka.msg-all.additional-producer-config=max.request.size:20000"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class PersistFailurePubResponseIntegrationTestCase extends AbstractPubSubIntegrationTest {

    private static final String TOPIC = "persist/failure";
    private static final byte[] TOO_LARGE_FOR_KAFKA_PAYLOAD = new byte[30_000];
    private static final long PUB_RESPONSE_TIMEOUT_MS = 10_000;
    private static final int UNSPECIFIED_ERROR = 0x80;
    private static final int SUCCESS = 0x00;

    private MqttAsyncClient pubClient;
    private MqttClient subClient;

    @After
    public void clear() throws Exception {
        if (pubClient != null) {
            if (pubClient.isConnected()) {
                // no quiesce: on a failure there are in-flight msgs that would never complete
                pubClient.disconnect(0).waitForCompletion(PUB_RESPONSE_TIMEOUT_MS);
            }
            pubClient.close();
        }
        if (subClient != null) {
            if (subClient.isConnected()) {
                subClient.disconnect();
            }
            subClient.close();
        }
    }

    @Test
    public void givenMqtt5Qos1MsgFailedToPersist_whenPublishMoreMsgs_thenTheirPubAcksAreNotHeldBack() throws Throwable {
        verifyPubResponsesAfterFailedPersist("persist_failure_qos1", 1);
    }

    @Test
    public void givenMqtt5Qos2MsgFailedToPersist_whenPublishMoreMsgs_thenTheirPubRecsAreNotHeldBack() throws Throwable {
        verifyPubResponsesAfterFailedPersist("persist_failure_qos2", 2);
    }

    @Test
    public void givenMqtt5Qos2MsgFailedToPersist_whenClientReusesItsPacketId_thenNewMsgIsDelivered() throws Throwable {
        List<String> received = new CopyOnWriteArrayList<>();
        // MQTT 3.1.1 subscriber: Paho v5 subscribe with a msg listener fails inside the client (1.2.5)
        subClient = new MqttClient(SERVER_URI + mqttPort, "persist_failure_reuse_sub", new org.eclipse.paho.client.mqttv3.persist.MemoryPersistence());
        subClient.connect();
        subClient.subscribe(TOPIC, 0, (topic, msg) -> received.add(new String(msg.getPayload(), StandardCharsets.UTF_8)));

        // Paho picks packet ids itself, so a raw client is needed to reuse the id of the failed msg
        try (RawMqtt5Client rawClient = new RawMqtt5Client("persist_failure_reuse_pub")) {
            int packetId = 1;
            rawClient.send(qos2Publish(packetId, TOO_LARGE_FOR_KAFKA_PAYLOAD));
            assertPubRec(rawClient.receive(), packetId, MqttReasonCodes.PubRec.UNSPECIFIED_ERROR);

            // a failure PUBREC ends the QoS 2 flow and frees the packet id (MQTT 5, 4.3.3)
            rawClient.send(qos2Publish(packetId, "reused".getBytes(StandardCharsets.UTF_8)));
            // before the fix the reused msg was taken for a duplicate still being persisted and silently dropped
            assertPubRec(rawClient.receive(), packetId, MqttReasonCodes.PubRec.SUCCESS);
            rawClient.send(pubRel(packetId));
            // 0x92 (Packet Identifier not found) here would mean the awaiting PUBREL state of the reused id is wrong
            assertPubComp(rawClient.receive(), packetId, MqttReasonCodes.PubComp.SUCCESS);
        }

        Awaitility.await("the msg published with the reused packet id is delivered")
                .atMost(PUB_RESPONSE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .until(() -> received.contains("reused"));
    }

    private MqttMessage qos2Publish(int packetId, byte[] payload) {
        return MqttMessageBuilders.publish()
                .topicName(TOPIC)
                .qos(MqttQoS.EXACTLY_ONCE)
                .messageId(packetId)
                .payload(Unpooled.wrappedBuffer(payload))
                .build();
    }

    private MqttMessage pubRel(int packetId) {
        return new MqttMessage(new MqttFixedHeader(MqttMessageType.PUBREL, false, MqttQoS.AT_LEAST_ONCE, false, 0),
                MqttMessageIdVariableHeader.from(packetId));
    }

    private void assertPubRec(MqttMessage msg, int packetId, MqttReasonCodes.PubRec reasonCode) {
        assertPubReply(msg, MqttMessageType.PUBREC, packetId, reasonCode.byteValue());
    }

    private void assertPubComp(MqttMessage msg, int packetId, MqttReasonCodes.PubComp reasonCode) {
        assertPubReply(msg, MqttMessageType.PUBCOMP, packetId, reasonCode.byteValue());
    }

    private void assertPubReply(MqttMessage msg, MqttMessageType type, int packetId, byte reasonCode) {
        assertThat(msg.fixedHeader().messageType()).isEqualTo(type);
        MqttPubReplyMessageVariableHeader pubReply = (MqttPubReplyMessageVariableHeader) msg.variableHeader();
        assertThat(pubReply.messageId()).isEqualTo(packetId);
        assertThat(pubReply.reasonCode()).isEqualTo(reasonCode);
    }

    private void verifyPubResponsesAfterFailedPersist(String clientId, int qos) throws MqttException {
        pubClient = new MqttAsyncClient(SERVER_URI + mqttPort, clientId, new MemoryPersistence());
        pubClient.connect(new MqttConnectionOptions()).waitForCompletion(PUB_RESPONSE_TIMEOUT_MS);

        IMqttToken failedToken = pubClient.publish(TOPIC, TOO_LARGE_FOR_KAFKA_PAYLOAD, qos, false);
        failedToken.waitForCompletion(PUB_RESPONSE_TIMEOUT_MS);
        // first code is the PUBACK/PUBREC one; for QoS 2 Paho still sends PUBREL after a failure PUBREC and appends the PUBCOMP code
        assertThat(failedToken.getReasonCodes()[0]).isEqualTo(UNSPECIFIED_ERROR);

        List<IMqttToken> tokens = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            tokens.add(pubClient.publish(TOPIC, ("data_" + i).getBytes(), qos, false));
        }
        for (IMqttToken token : tokens) {
            // before the fix this timed out: the failed msg stayed at the head of the ordered response queue
            token.waitForCompletion(PUB_RESPONSE_TIMEOUT_MS);
            assertThat(token.getReasonCodes()).containsOnly(SUCCESS);
        }
        assertThat(pubClient.isConnected()).isTrue();
    }

    /**
     * Minimal MQTT 5 client over a plain socket. The encoder and the decoder share one channel, so the decoder
     * knows the protocol version from the encoded CONNECT.
     */
    private class RawMqtt5Client implements AutoCloseable {

        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;
        private final EmbeddedChannel codec = new EmbeddedChannel(MqttEncoder.INSTANCE, new MqttDecoder());

        RawMqtt5Client(String clientId) throws IOException {
            socket = new Socket(LOCALHOST, mqttPort);
            socket.setTcpNoDelay(true);
            socket.setSoTimeout((int) PUB_RESPONSE_TIMEOUT_MS);
            in = socket.getInputStream();
            out = socket.getOutputStream();
            send(MqttMessageBuilders.connect()
                    .clientId(clientId)
                    .protocolVersion(MqttVersion.MQTT_5)
                    .cleanSession(true)
                    .keepAlive(60)
                    .build());
            assertThat(receive().fixedHeader().messageType()).isEqualTo(MqttMessageType.CONNACK);
        }

        void send(MqttMessage msg) throws IOException {
            codec.writeOutbound(msg);
            ByteBuf encoded = codec.readOutbound();
            try {
                byte[] bytes = new byte[encoded.readableBytes()];
                encoded.readBytes(bytes);
                out.write(bytes);
                out.flush();
            } finally {
                encoded.release();
            }
        }

        // fails with SocketTimeoutException when the broker doesn't respond in time
        MqttMessage receive() throws IOException {
            byte[] buf = new byte[1024];
            MqttMessage msg;
            while ((msg = codec.readInbound()) == null) {
                int read = in.read(buf);
                if (read < 0) {
                    throw new IOException("Connection closed by the broker");
                }
                codec.writeInbound(Unpooled.copiedBuffer(buf, 0, read));
            }
            return msg;
        }

        @Override
        public void close() throws IOException {
            codec.finishAndReleaseAll();
            socket.close();
        }
    }
}
