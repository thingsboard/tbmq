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
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttVersion;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.mqttv5.client.IMqttMessageListener;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.service.test.util.TestUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reproduces the QoS 0 publish-then-immediate-disconnect race that caused mosquitto_pub to lose
 * messages. Each publisher uses a raw TCP socket so the PUBLISH and DISCONNECT bytes are written
 * in a single OS-level write, guaranteeing both arrive in the same Netty channelRead and get
 * enqueued on the actor mailbox before the dispatcher starts draining. Before the fix in
 * {@code ClientMqttActorManagerImpl#disconnect}, the DISCONNECT was placed on the high-priority
 * queue and processed before the still-pending PUBLISH, causing the subscriber to miss messages.
 */
@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = Qos0PublishDisconnectRaceIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@DaoSqlTest
@RunWith(SpringRunner.class)
public class Qos0PublishDisconnectRaceIntegrationTestCase extends AbstractPubSubIntegrationTest {

    private static final int ITERATIONS = 20;
    private static final String SUB_CLIENT_ID = "qos0RaceSubClient";
    private static final String PUB_CLIENT_ID_PREFIX = "qos0RacePubClient-";
    private static final String TEST_TOPIC = "qos0/race/topic";

    @Autowired
    private MqttClientCredentialsService credentialsService;

    private MqttClientCredentials subCredentials;
    private final List<MqttClientCredentials> pubCredentialsList = new ArrayList<>(ITERATIONS);

    @Before
    public void beforeTest() throws Exception {
        subCredentials = credentialsService.saveCredentials(
                TestUtils.createDeviceClientCredentialsWithAuth(SUB_CLIENT_ID, List.of(TEST_TOPIC)));
        for (int i = 0; i < ITERATIONS; i++) {
            pubCredentialsList.add(credentialsService.saveCredentials(
                    TestUtils.createDeviceClientCredentialsWithAuth(PUB_CLIENT_ID_PREFIX + i, List.of(TEST_TOPIC))));
        }
        enableBasicProvider();
    }

    @After
    public void clear() {
        credentialsService.deleteCredentials(subCredentials.getId());
        pubCredentialsList.forEach(c -> credentialsService.deleteCredentials(c.getId()));
        pubCredentialsList.clear();
    }

    @Test
    public void givenSubscribedClient_whenPublishersSendQos0AndImmediatelyDisconnect_thenAllMessagesAreDelivered() throws Throwable {
        CountDownLatch latch = new CountDownLatch(ITERATIONS);
        AtomicInteger received = new AtomicInteger();

        MqttClient subClient = new MqttClient(SERVER_URI + mqttPort, SUB_CLIENT_ID, new MemoryPersistence());
        subClient.connect();
        MqttSubscription[] subscriptions = {new MqttSubscription(TEST_TOPIC, 0)};
        IMqttMessageListener[] listeners = {(topic, message) -> {
            received.incrementAndGet();
            latch.countDown();
        }};
        subClient.subscribe(subscriptions, listeners);

        try {
            for (int i = 0; i < ITERATIONS; i++) {
                publishQos0ThenDisconnect(PUB_CLIENT_ID_PREFIX + i, "msg-" + i);
            }

            boolean allReceived = latch.await(15, TimeUnit.SECONDS);
            Assert.assertTrue(
                    "Expected " + ITERATIONS + " messages, got " + received.get(),
                    allReceived);
            Assert.assertEquals(ITERATIONS, received.get());
        } finally {
            TestUtils.disconnectAndCloseClient(subClient);
        }
    }

    /**
     * Opens a raw TCP socket, sends CONNECT, waits for CONNACK, then writes PUBLISH and DISCONNECT
     * in a single {@code write()} call so they land in the broker's channelRead together. The
     * socket is then closed which triggers an additional ON_CHANNEL_CLOSED disconnect path on the
     * broker.
     */
    private void publishQos0ThenDisconnect(String clientId, String payload) throws IOException {
        byte[] connectBytes = encode(MqttMessageBuilders.connect()
                .clientId(clientId)
                .protocolVersion(MqttVersion.MQTT_3_1_1)
                .cleanSession(true)
                .keepAlive(60)
                .build());
        byte[] publishBytes = encode(MqttMessageBuilders.publish()
                .topicName(TEST_TOPIC)
                .qos(MqttQoS.AT_MOST_ONCE)
                .retained(false)
                .payload(Unpooled.wrappedBuffer(payload.getBytes(StandardCharsets.UTF_8)))
                .build());
        byte[] disconnectBytes = encode(MqttMessageBuilders.disconnect().build());

        try (Socket socket = new Socket(LOCALHOST, mqttPort)) {
            socket.setTcpNoDelay(true);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            out.write(connectBytes);
            out.flush();
            awaitConnAckAccepted(in);

            byte[] combined = new byte[publishBytes.length + disconnectBytes.length];
            System.arraycopy(publishBytes, 0, combined, 0, publishBytes.length);
            System.arraycopy(disconnectBytes, 0, combined, publishBytes.length, disconnectBytes.length);
            out.write(combined);
            out.flush();
            // try-with-resources closes the socket → broker observes TCP FIN as ON_CHANNEL_CLOSED.
        }
    }

    private void awaitConnAckAccepted(InputStream in) throws IOException {
        int b0 = in.read();
        Assert.assertEquals("Expected CONNACK fixed header (0x20)", 0x20, b0);
        int remaining = in.read();
        Assert.assertEquals("Expected CONNACK remaining length 2", 2, remaining);
        in.read(); // session-present flags
        int returnCode = in.read();
        Assert.assertEquals(
                "Expected CONNACK with CONNECTION_ACCEPTED",
                MqttConnectReturnCode.CONNECTION_ACCEPTED.byteValue() & 0xff,
                returnCode);
    }

    private byte[] encode(MqttMessage message) {
        EmbeddedChannel channel = new EmbeddedChannel(MqttEncoder.INSTANCE);
        try {
            channel.writeOutbound(message);
            ByteBuf encoded = channel.readOutbound();
            try {
                byte[] bytes = new byte[encoded.readableBytes()];
                encoded.readBytes(bytes);
                return bytes;
            } finally {
                encoded.release();
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
