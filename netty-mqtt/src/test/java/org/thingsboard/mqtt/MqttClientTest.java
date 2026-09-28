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
import com.google.common.util.concurrent.ListenableFuture;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.buffer.UnpooledHeapByteBuf;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.util.IllegalReferenceCountException;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.hivemq.HiveMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.thingsboard.mqtt.broker.common.util.AbstractListeningExecutor;
import org.thingsboard.mqtt.broker.common.util.ListeningExecutor;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Testcontainers
class MqttClientTest {

    final int randomPort = 0;

    @Container
    HiveMQContainer broker = new HiveMQContainer(DockerImageName.parse("hivemq/hivemq-ce").withTag("2025.2"));

    MqttTestProxy proxy;

    MqttClient client;

    AbstractListeningExecutor handlerExecutor;

    @BeforeAll
    static void init() {
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);
    }

    @BeforeEach
    void setup() {
        handlerExecutor = new AbstractListeningExecutor() {
            @Override
            protected int getThreadPoolSize() {
                return 1;
            }

            @Override
            protected String getExecutorName() {
                return "MQTT client test handler";
            }
        };
        handlerExecutor.init();
    }

    @AfterEach
    void cleanup() {
        if (client != null) {
            client.disconnect();
            client = null;
        }
        if (proxy != null) {
            proxy.stop();
            proxy = null;
        }
        handlerExecutor.destroy();
        handlerExecutor = null;
    }

    @Test
    void testConnectToBroker() {
        // GIVEN
        var clientConfig = new MqttClientConfig();
        clientConfig.setOwnerId("Test[ConnectToBroker]");
        clientConfig.setClientId("connect");

        client = MqttClient.create(clientConfig, null, handlerExecutor);

        // WHEN
        Promise<MqttConnectResult> connectFuture = client.connect(broker.getHost(), broker.getMqttPort());

        // THEN
        assertThat(connectFuture).isNotNull();

        Awaitility.await("waiting for client to connect")
                .atMost(Duration.ofSeconds(10L))
                .until(connectFuture::isDone);

        assertThat(connectFuture.isSuccess()).isTrue();

        MqttConnectResult actualConnectResult = connectFuture.getNow();
        assertThat(actualConnectResult).isNotNull();
        assertThat(actualConnectResult.isSuccess()).isTrue();
        assertThat(actualConnectResult.getReturnCode()).isEqualTo(MqttConnectReturnCode.CONNECTION_ACCEPTED);

        assertThat(client.isConnected()).isTrue();
    }

    @Test
    void testDisconnectFromBroker() {
        // GIVEN
        var clientConfig = new MqttClientConfig();
        clientConfig.setOwnerId("Test[Disconnect]");
        clientConfig.setClientId("disconnect");

        client = MqttClient.create(clientConfig, null, handlerExecutor);

        connect(broker.getHost(), broker.getMqttPort());

        // WHEN
        client.disconnect();

        // THEN
        Awaitility.await("waiting for client to disconnect")
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(client.isConnected()).isFalse());
    }

    @Test
    void testDisconnectDueToKeepAliveIfNoActivity() {
        // GIVEN
        proxy = MqttTestProxy.builder()
                .localPort(randomPort)
                .brokerHost(broker.getHost())
                .brokerPort(broker.getMqttPort())
                .brokerToClientInterceptor(msg -> msg.fixedHeader().messageType() != MqttMessageType.PINGRESP) // drop all ping responses to simulate broker down
                .build();

        int idleTimeoutSeconds = 2;

        var clientConfig = new MqttClientConfig();
        clientConfig.setOwnerId("Test[KeepAliveDisconnect]");
        clientConfig.setClientId("no-activity-disconnect");
        clientConfig.setTimeoutSeconds(idleTimeoutSeconds);
        clientConfig.setReconnect(false); // disable auto reconnect
        client = MqttClient.create(clientConfig, null, handlerExecutor);

        // WHEN-THEN
        connect(broker.getHost(), proxy.getPort());

        // no activity...

        Awaitility.await("waiting for client to disconnect")
                .pollDelay(Duration.ofSeconds(idleTimeoutSeconds * 2)) // 2 seconds to wait for the first idle event and then 2 seconds for scheduled disconnect to fire
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(client.isConnected()).isFalse());
    }

    @Test
    void testRetransmission() {
        // GIVEN
        proxy = MqttTestProxy.builder()
                .localPort(randomPort)
                .brokerHost(broker.getHost())
                .brokerPort(broker.getMqttPort())
                .brokerToClientInterceptor(msg -> msg.fixedHeader().messageType() != MqttMessageType.PUBACK) // drop all pubacks to allow retransmission to happen
                .build();

        // create client
        var clientConfig = new MqttClientConfig();
        clientConfig.setOwnerId("Test[Retransmission]");
        clientConfig.setClientId("retransmission");
        clientConfig.setRetransmissionConfig(new MqttClientConfig.RetransmissionConfig(1, 1000L, 0d));
        client = MqttClient.create(clientConfig, null, handlerExecutor);

        // connect to a broker
        connect(broker.getHost(), proxy.getPort());

        // subscribe to a topic
        String topic = "test-topic";
        List<ByteBuf> receivedMessages = Collections.synchronizedList(new ArrayList<>(2));
        Future<Void> subscribeFuture = client.on(topic, msg -> {
            receivedMessages.add(msg.payload());
            return Futures.immediateVoidFuture();
        });
        Awaitility.await("waiting for client to subscribe to a topic")
                .atMost(Duration.ofSeconds(10L))
                .until(subscribeFuture::isDone);

        // WHEN
        // publish a message
        ByteBuf message = PooledByteBufAllocator.DEFAULT.buffer().writeBytes("test message".getBytes(StandardCharsets.UTF_8));
        client.publish(topic, message, MqttQoS.AT_LEAST_ONCE);

        // THEN
        // wait enough time so that retransmission happens and stops
        // if retransmission works incorrectly waiting 10 seconds allows for additional retransmissions to happen
        try {
            Awaitility.await("wait up to 10s, stop early if too many messages")
                    .atMost(Duration.ofSeconds(10L))
                    .pollInterval(Duration.ofMillis(100))
                    .until(() -> receivedMessages.size() > 2);
        } catch (ConditionTimeoutException __) {
            // didn't exceed 2 messages
        }

        assertThat(receivedMessages).size().describedAs("incorrect number of messages received, expected 2 (original plus one retransmitted)").isEqualTo(2);
    }

    @Test
    void testQoS0BackPressureStopsReadingWhileHandlerIsStalled() throws Exception {
        // GIVEN
        // counts the PUBLISHes the subscriber hands to its handler executor: that queue is what grows without bound
        // when reads are not paused, whereas the delivery count is capped at one by the single stalled handler thread
        AtomicInteger dispatched = new AtomicInteger();
        ListeningExecutor countingExecutor = new ListeningExecutor() {
            @Override
            public <T> ListenableFuture<T> executeAsync(Callable<T> task) {
                return handlerExecutor.executeAsync(task);
            }

            @Override
            public void execute(Runnable command) {
                dispatched.incrementAndGet();
                handlerExecutor.execute(command);
            }
        };

        var subscriberConfig = new MqttClientConfig();
        subscriberConfig.setOwnerId("Test[QoS0BackPressure]");
        subscriberConfig.setClientId("qos0-bp-sub");
        subscriberConfig.setRetransmissionConfig(new MqttClientConfig.RetransmissionConfig(3, 1000L, 0d));
        // low watermark first: the setters reject a high watermark that is not above the current low one
        subscriberConfig.setBackPressureLowWatermark(2);
        subscriberConfig.setBackPressureHighWatermark(4);
        client = MqttClient.create(subscriberConfig, null, countingExecutor);
        connect(broker.getHost(), broker.getMqttPort());

        String topic = "qos0-backpressure";
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger delivered = new AtomicInteger();
        Future<Void> subscribeFuture = client.on(topic, msg -> {
            delivered.incrementAndGet();
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return Futures.immediateVoidFuture();
        }, MqttQoS.AT_MOST_ONCE);
        Awaitility.await("waiting for subscriber to subscribe")
                .atMost(Duration.ofSeconds(10L))
                .until(subscribeFuture::isSuccess);

        var publisherConfig = new MqttClientConfig();
        publisherConfig.setOwnerId("Test[QoS0BackPressurePub]");
        publisherConfig.setClientId("qos0-bp-pub");
        publisherConfig.setRetransmissionConfig(new MqttClientConfig.RetransmissionConfig(3, 1000L, 0d));
        MqttClient publisher = MqttClient.create(publisherConfig, null, handlerExecutor);
        try {
            Promise<MqttConnectResult> publisherConnect = publisher.connect(broker.getHost(), broker.getMqttPort());
            Awaitility.await("waiting for publisher to connect")
                    .atMost(Duration.ofSeconds(10L))
                    .until(publisherConnect::isSuccess);

            // 16 KiB payloads: netty's adaptive read buffer tops out at 64 KiB, so one socket read decodes only a few
            // PUBLISHes and pausing autoRead takes effect within a handful of messages rather than a whole burst
            int payloadSize = 16 * 1024;
            int burst = 60;
            byte[] bytes = new byte[payloadSize];

            // WHEN
            List<Future<Void>> publishFutures = new ArrayList<>(burst);
            for (int i = 0; i < burst; i++) {
                ByteBuf payload = PooledByteBufAllocator.DEFAULT.buffer(payloadSize).writeBytes(bytes);
                publishFutures.add(publisher.publish(topic, payload, MqttQoS.AT_MOST_ONCE));
            }
            Awaitility.await("waiting for the publisher to write the burst")
                    .atMost(Duration.ofSeconds(10L))
                    .until(() -> publishFutures.stream().allMatch(Future::isDone));
            assertThat(publishFutures).allMatch(Future::isSuccess);

            Awaitility.await("waiting for the first delivery")
                    .atMost(Duration.ofSeconds(10L))
                    .until(() -> delivered.get() > 0);

            // THEN
            // With the handler stalled, reads must pause once high watermark (4) PUBLISHes are in flight. What may still
            // be dispatched after that is what was already read: the rest of the current read buffer (at most 64 KiB,
            // i.e. about 4 PUBLISHes of 16 KiB) plus a partial message held by the decoder, so correct code stays at
            // or below about 9 (4-5 observed). Without back pressure the client reads the whole burst of 60 at line
            // rate (60 observed). 20 separates the two with a wide margin either way.
            int maxDispatchedWhileStalled = 20;
            Awaitility.await("dispatched count must stay bounded while the handler is stalled")
                    .during(Duration.ofSeconds(2))
                    .atMost(Duration.ofSeconds(5))
                    .untilAsserted(() -> assertThat(dispatched.get()).isLessThanOrEqualTo(maxDispatchedWhileStalled));
            int stalledDispatched = dispatched.get();
            int stalledDelivered = delivered.get();
            log.info("QoS 0 back pressure: dispatched {} of {} while stalled, delivered {}", stalledDispatched, burst, stalledDelivered);

            // releasing the handler drains the in-flight PUBLISHes below the low watermark, so reads must resume:
            // more PUBLISHes than were dispatched while stalled get delivered, which only new reads can supply
            release.countDown();
            Awaitility.await("dispatching and delivery must resume once the handler is released")
                    .atMost(Duration.ofSeconds(10L))
                    .untilAsserted(() -> assertThat(delivered.get()).isGreaterThan(stalledDispatched));
            assertThat(dispatched.get()).isGreaterThan(stalledDispatched);
            log.info("QoS 0 back pressure: resumed, dispatched {}, delivered {}", dispatched.get(), delivered.get());
        } finally {
            release.countDown();
            publisher.disconnect();
        }
    }

    @Test
    void testQoS0PublishReleasesPayload() {
        // GIVEN
        var clientConfig = new MqttClientConfig();
        clientConfig.setOwnerId("Test[QoS0PublishRelease]");
        clientConfig.setClientId("qos0-release");
        clientConfig.setRetransmissionConfig(new MqttClientConfig.RetransmissionConfig(3, 5000L, 0d));
        client = MqttClient.create(clientConfig, null, handlerExecutor);
        connect(broker.getHost(), broker.getMqttPort());

        TrackedByteBuf payload = new TrackedByteBuf("qos0 payload");

        // WHEN
        Future<Void> publishFuture = client.publish("qos0-release", payload, MqttQoS.AT_MOST_ONCE);

        // THEN
        Awaitility.await("waiting for the QoS 0 publish to complete")
                .atMost(Duration.ofSeconds(10L))
                .until(publishFuture::isDone);
        assertThat(publishFuture.isSuccess()).isTrue();
        assertPayloadFullyReleased(payload);
    }

    @Test
    void testQoS1PublishReleasesPayload() {
        // GIVEN
        var clientConfig = new MqttClientConfig();
        clientConfig.setOwnerId("Test[QoS1PublishRelease]");
        clientConfig.setClientId("qos1-release");
        clientConfig.setRetransmissionConfig(new MqttClientConfig.RetransmissionConfig(3, 5000L, 0d));
        client = MqttClient.create(clientConfig, null, handlerExecutor);
        connect(broker.getHost(), broker.getMqttPort());

        TrackedByteBuf payload = new TrackedByteBuf("qos1 payload");

        // WHEN
        Future<Void> publishFuture = client.publish("qos1-release", payload, MqttQoS.AT_LEAST_ONCE);

        // THEN
        Awaitility.await("waiting for the QoS 1 publish to be acknowledged")
                .atMost(Duration.ofSeconds(10L))
                .until(publishFuture::isDone);
        assertThat(publishFuture.isSuccess()).isTrue();
        assertPayloadFullyReleased(payload);
    }

    @Test
    void testPublishWithoutChannelReleasesPayload() throws IOException {
        // GIVEN
        // nothing listens on this port, so the connect attempt fails and the client never gets a channel
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        var clientConfig = new MqttClientConfig();
        clientConfig.setOwnerId("Test[PublishNoChannel]");
        clientConfig.setClientId("no-channel-release");
        clientConfig.setReconnect(false);
        clientConfig.setRetransmissionConfig(new MqttClientConfig.RetransmissionConfig(3, 5000L, 0d));
        client = MqttClient.create(clientConfig, null, handlerExecutor);
        client.connect("localhost", closedPort);

        TrackedByteBuf payload = new TrackedByteBuf("never written");

        // WHEN
        Future<Void> publishFuture = client.publish("no-channel", payload, MqttQoS.AT_LEAST_ONCE);

        // THEN
        // nothing was ever written, so neither the caller's reference nor the pending publish's may survive
        assertPayloadFullyReleased(payload);
    }

    @Test
    void testPublishOnClosedChannelReleasesPayload() {
        // GIVEN
        var clientConfig = new MqttClientConfig();
        clientConfig.setOwnerId("Test[PublishClosedChannel]");
        clientConfig.setClientId("closed-channel-release");
        clientConfig.setRetransmissionConfig(new MqttClientConfig.RetransmissionConfig(3, 5000L, 0d));
        client = MqttClient.create(clientConfig, null, handlerExecutor);
        connect(broker.getHost(), broker.getMqttPort());
        client.disconnect();
        Awaitility.await("waiting for client to disconnect")
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(client.isConnected()).isFalse());

        TrackedByteBuf payload = new TrackedByteBuf("closed channel");

        // WHEN
        // the channel is still set but no longer active, so the message never reaches netty
        Future<Void> publishFuture = client.publish("closed-channel", payload, MqttQoS.AT_LEAST_ONCE);

        // THEN
        Awaitility.await("waiting for the publish on a closed channel to fail")
                .atMost(Duration.ofSeconds(5L))
                .until(publishFuture::isDone);
        assertThat(publishFuture.isSuccess()).isFalse();
        assertPayloadFullyReleased(payload);
    }

    @Test
    void testPublishRejectedByEncoderReleasesPayload() {
        // GIVEN
        var clientConfig = new MqttClientConfig();
        clientConfig.setOwnerId("Test[PublishEncoderReject]");
        clientConfig.setClientId("encoder-reject-release");
        clientConfig.setRetransmissionConfig(new MqttClientConfig.RetransmissionConfig(3, 5000L, 0d));
        client = MqttClient.create(clientConfig, null, handlerExecutor);
        connect(broker.getHost(), broker.getMqttPort());

        TrackedByteBuf payload = new TrackedByteBuf("invalid topic");

        // WHEN
        // the encoder rejects a wildcard in a publish topic: the write fails after netty has taken the message
        Future<Void> publishFuture = client.publish("invalid/+/topic", payload, MqttQoS.AT_LEAST_ONCE);

        // THEN
        Awaitility.await("waiting for the rejected publish to fail")
                .atMost(Duration.ofSeconds(5L))
                .until(publishFuture::isDone);
        assertThat(publishFuture.isSuccess()).isFalse();
        assertPayloadFullyReleased(payload);
        assertThat(client.isConnected()).isTrue();
    }

    @Test
    void testPendingQoS1PublishIsReleasedOnceOnChannelClose() {
        // GIVEN
        proxy = MqttTestProxy.builder()
                .localPort(randomPort)
                .brokerHost(broker.getHost())
                .brokerPort(broker.getMqttPort())
                .brokerToClientInterceptor(msg -> msg.fixedHeader().messageType() != MqttMessageType.PUBACK) // keep the publish pending
                .build();

        var clientConfig = new MqttClientConfig();
        clientConfig.setOwnerId("Test[PendingPublishClose]");
        clientConfig.setClientId("pending-close-release");
        clientConfig.setReconnect(false);
        // long enough that no retransmission happens before the connection is dropped
        clientConfig.setRetransmissionConfig(new MqttClientConfig.RetransmissionConfig(3, 30_000L, 0d));
        client = MqttClient.create(clientConfig, null, handlerExecutor);
        connect(broker.getHost(), proxy.getPort());

        TrackedByteBuf payload = new TrackedByteBuf("pending on close");
        Future<Void> publishFuture = client.publish("pending-close", payload, MqttQoS.AT_LEAST_ONCE);

        // the encoder consumed the caller's reference; only the pending publish's reference remains
        Awaitility.await("waiting for the publish to be written")
                .atMost(Duration.ofSeconds(10L))
                .untilAsserted(() -> assertThat(payload.refCnt()).isEqualTo(1));
        assertThat(publishFuture.isDone()).isFalse();

        // WHEN
        proxy.stop(); // drop the connection abruptly while the PUBACK is outstanding

        // THEN
        Awaitility.await("waiting for client to notice the closed connection")
                .atMost(Duration.ofSeconds(10L))
                .untilAsserted(() -> assertThat(client.isConnected()).isFalse());
        assertPayloadFullyReleased(payload);
    }

    private static void assertPayloadFullyReleased(TrackedByteBuf payload) {
        Awaitility.await("waiting for the payload to be fully released")
                .atMost(Duration.ofSeconds(5L))
                .untilAsserted(() -> assertThat(payload.refCnt()).describedAs("payload refCnt").isZero());
        // any late release or retain on the freed buffer would be recorded here rather than only logged
        Awaitility.await("payload must stay released without any over-release")
                .during(Duration.ofMillis(500))
                .atMost(Duration.ofSeconds(2L))
                .untilAsserted(() -> {
                    assertThat(payload.refCnt()).describedAs("payload refCnt").isZero();
                    assertThat(payload.illegalRefCntOps.get()).describedAs("releases/retains on an already freed payload").isZero();
                });
    }

    /**
     * Heap buffer that records every release or retain attempted after it was freed, which netty would otherwise only
     * surface as an {@link IllegalReferenceCountException} logged from whichever listener made the call.
     */
    private static final class TrackedByteBuf extends UnpooledHeapByteBuf {

        private final AtomicInteger illegalRefCntOps = new AtomicInteger();

        private TrackedByteBuf(String content) {
            this(content.getBytes(StandardCharsets.UTF_8));
        }

        private TrackedByteBuf(byte[] bytes) {
            super(UnpooledByteBufAllocator.DEFAULT, bytes, bytes.length);
        }

        @Override
        public boolean release() {
            try {
                return super.release();
            } catch (IllegalReferenceCountException e) {
                illegalRefCntOps.incrementAndGet();
                throw e;
            }
        }

        @Override
        public boolean release(int decrement) {
            try {
                return super.release(decrement);
            } catch (IllegalReferenceCountException e) {
                illegalRefCntOps.incrementAndGet();
                throw e;
            }
        }

        @Override
        public ByteBuf retain() {
            try {
                return super.retain();
            } catch (IllegalReferenceCountException e) {
                illegalRefCntOps.incrementAndGet();
                throw e;
            }
        }

        @Override
        public ByteBuf retain(int increment) {
            try {
                return super.retain(increment);
            } catch (IllegalReferenceCountException e) {
                illegalRefCntOps.incrementAndGet();
                throw e;
            }
        }

    }

    private void connect(String host, int port) {
        Promise<MqttConnectResult> connectFuture = client.connect(host, port);
        Awaitility.await("waiting for client to connect")
                .atMost(Duration.ofSeconds(10L))
                .until(connectFuture::isSuccess);
    }

}
