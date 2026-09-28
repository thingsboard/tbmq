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

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.buffer.UnpooledHeapByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testcontainers.hivemq.HiveMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.thingsboard.mqtt.broker.common.util.AbstractListeningExecutor;
import org.thingsboard.mqtt.broker.common.util.ListeningExecutor;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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

    // the client's loop group when a test sets one itself; the client never shuts its group down
    EventLoopGroup clientEventLoop;

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
        if (clientEventLoop != null) {
            clientEventLoop.shutdownGracefully();
            clientEventLoop = null;
        }
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
        proxy = proxyDropping(MqttMessageType.PINGRESP); // drop all ping responses to simulate broker down

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
        proxy = proxyDropping(MqttMessageType.PUBACK); // drop all pubacks to allow retransmission to happen

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
        Future<MqttQoS> subscribeFuture = client.on(topic, msg -> {
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
        Future<MqttQoS> subscribeFuture = client.on(topic, msg -> {
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
        int closedPort = closedPort();
        var clientConfig = newConfig("Test[PublishNoChannel]", "no-channel-release");
        client = MqttClient.create(clientConfig, null, handlerExecutor);
        client.connect("localhost", closedPort);

        TrackedByteBuf payload = new TrackedByteBuf("never written");

        // WHEN
        Future<Void> publishFuture = client.publish("no-channel", payload, MqttQoS.AT_LEAST_ONCE);

        // THEN
        // nothing was ever written, so neither the caller's reference nor the pending publish's may survive
        assertPayloadFullyReleased(payload);
        Awaitility.await("waiting for the unsendable publish to fail")
                .atMost(Duration.ofSeconds(5L))
                .until(publishFuture::isDone);
        assertThat(publishFuture.isSuccess()).isFalse();
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
        proxy = proxyDropping(MqttMessageType.PUBACK); // keep the publish pending

        var clientConfig = newConfig("Test[PendingPublishClose]", "pending-close-release");
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
        Awaitility.await("waiting for the in-flight publish to fail")
                .atMost(Duration.ofSeconds(5L))
                .until(publishFuture::isDone);
        assertThat(publishFuture.cause()).isInstanceOf(ChannelClosedException.class);
    }

    @Test
    void testPublishRetransmissionRunsOnChannelEventLoop() {
        // GIVEN
        proxy = proxyDropping(MqttMessageType.PUBACK); // force a retransmission

        var clientConfig = newConfig("Test[RetransmissionLoop]", "retransmission-loop");
        clientConfig.setRetransmissionConfig(new MqttClientConfig.RetransmissionConfig(1, 500L, 0d));
        client = MqttClient.create(clientConfig, null, handlerExecutor);
        Promise<MqttConnectResult> connectFuture = client.connect(broker.getHost(), proxy.getPort());
        Awaitility.await("waiting for client to connect")
                .atMost(Duration.ofSeconds(10L))
                .until(connectFuture::isSuccess);
        EventLoop channelLoop = connectFuture.getNow().getCloseFuture().channel().eventLoop();

        TrackedByteBuf payload = new TrackedByteBuf("retransmitted");
        Thread publisher = Thread.currentThread();

        // WHEN
        client.publish("retransmission-loop", payload, MqttQoS.AT_LEAST_ONCE);

        // THEN
        // publish() takes the pending publish's reference on the caller's thread; every other retain is a retransmission
        Awaitility.await("waiting for the publish to be retransmitted")
                .atMost(Duration.ofSeconds(10L))
                .until(() -> payload.retainThreads.stream().anyMatch(t -> t != publisher));
        // PUBACK, PUBCOMP, the write listener and the close cleanup all release on the channel's loop: a retransmission
        // that retains anywhere else can pass its cancelled check and then retain a payload those paths just freed
        assertThat(payload.retainThreads.stream().filter(t -> t != publisher).toList())
                .describedAs("threads that retained the payload for a retransmission")
                .isNotEmpty()
                .allSatisfy(t -> assertThat(channelLoop.inEventLoop(t)).describedAs("%s is the channel's event loop", t).isTrue());
    }

    @Test
    void testPublishFirstWrittenByConnackResendIsRetransmitted() {
        // GIVEN
        var clientConfig = newConfig("Test[ConnackResendRetransmission]", "connack-resend-retrans");
        clientConfig.setRetransmissionConfig(new MqttClientConfig.RetransmissionConfig(1, 1000L, 0d));
        client = MqttClient.create(clientConfig, null, handlerExecutor);

        TrackedByteBuf payload = new TrackedByteBuf("resent on connack");
        MqttPendingPublish pendingPublish = registerUnsentPublish("connack-resend", payload, MqttQoS.AT_LEAST_ONCE);
        int messageId = pendingPublish.getMessageId();

        AtomicInteger pubacks = new AtomicInteger();
        proxy = MqttTestProxy.builder()
                .localPort(randomPort)
                .brokerHost(broker.getHost())
                .brokerPort(broker.getMqttPort())
                .brokerToClientInterceptor(msg -> {
                    if (msg.fixedHeader().messageType() != MqttMessageType.PUBACK) {
                        return true;
                    }
                    // the broker acknowledges every copy it receives, so each dropped PUBACK counts one delivery
                    if (((MqttMessageIdVariableHeader) msg.variableHeader()).messageId() == messageId) {
                        pubacks.incrementAndGet();
                    }
                    return false;
                })
                .build();

        // WHEN
        connect(broker.getHost(), proxy.getPort());

        // THEN
        try {
            Awaitility.await("wait up to 6s, stop early if too many deliveries")
                    .atMost(Duration.ofSeconds(6L))
                    .pollInterval(Duration.ofMillis(100))
                    .until(() -> pubacks.get() > 2);
        } catch (ConditionTimeoutException __) {
            // didn't exceed 2 deliveries
        }
        assertThat(pubacks.get()).describedAs("deliveries of the publish, expected 2 (the CONNACK resend plus one retransmission)").isEqualTo(2);
        Awaitility.await("waiting for the retransmissions to run out")
                .atMost(Duration.ofSeconds(5L))
                .until(pendingPublish.getFuture()::isDone);
        assertThat(pendingPublish.getFuture().cause()).isInstanceOf(MaxRetransmissionsReachedException.class);
        assertPayloadFullyReleased(payload);
    }

    @ParameterizedTest
    @EnumSource(value = MqttQoS.class, names = {"AT_MOST_ONCE", "AT_LEAST_ONCE", "EXACTLY_ONCE"})
    void testPublishFirstWrittenByConnackResendAndRejectedByEncoderFails(MqttQoS qos) {
        // GIVEN
        var clientConfig = newConfig("Test[ConnackResendEncoderReject]", "connack-resend-reject-" + qos.value());
        client = MqttClient.create(clientConfig, null, handlerExecutor);

        TrackedByteBuf payload = new TrackedByteBuf("invalid topic, resent on connack");
        // the encoder rejects a wildcard in a publish topic: the CONNACK resend's write fails, but the channel stays open
        MqttPendingPublish pendingPublish = registerUnsentPublish("invalid/+/topic", payload, qos);

        // WHEN
        connect(broker.getHost(), broker.getMqttPort());

        // THEN
        Awaitility.await("waiting for the rejected CONNACK resend to fail its publish")
                .atMost(Duration.ofSeconds(5L))
                .until(pendingPublish.getFuture()::isDone);
        assertThat(pendingPublish.getFuture().isSuccess()).describedAs("publish future succeeded").isFalse();
        assertPayloadFullyReleased(payload);
        assertThat(((MqttClientImpl) client).getPendingPublishes()).doesNotContainKey(pendingPublish.getMessageId());
        assertThat(client.isConnected()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = MqttMessageType.class, names = {"PUBREC", "PUBCOMP"})
    void testPendingQoS2PublishFailsOnChannelClose(MqttMessageType withheldAck) {
        // GIVEN
        // dropping PUBREC holds the publish before the broker received it, dropping PUBCOMP holds it after PUBREL
        CountDownLatch ackDropped = new CountDownLatch(1);
        proxy = proxyDropping(withheldAck, ackDropped);

        var clientConfig = newConfig("Test[PendingQoS2Close]", "qos2-close-" + withheldAck.name().toLowerCase());
        // long enough that no retransmission happens before the connection is dropped
        clientConfig.setRetransmissionConfig(new MqttClientConfig.RetransmissionConfig(3, 30_000L, 0d));
        client = MqttClient.create(clientConfig, null, handlerExecutor);
        connect(broker.getHost(), proxy.getPort());

        TrackedByteBuf payload = new TrackedByteBuf("qos2 pending on close");
        Future<Void> publishFuture = client.publish("qos2-close", payload, MqttQoS.EXACTLY_ONCE);
        awaitLatch(ackDropped, "waiting for the " + withheldAck + " to be dropped");
        assertThat(publishFuture.isDone()).isFalse();

        // WHEN
        proxy.stop();

        // THEN
        Awaitility.await("waiting for the in-flight QoS 2 publish to fail")
                .atMost(Duration.ofSeconds(10L))
                .until(publishFuture::isDone);
        assertThat(publishFuture.cause()).isInstanceOf(ChannelClosedException.class);
        assertPayloadFullyReleased(payload);
    }

    @Test
    void testConnectToRefusedPortFailsWithConnectException() throws IOException {
        // GIVEN
        var clientConfig = newConfig("Test[ConnectRefused]", "connect-refused");
        client = MqttClient.create(clientConfig, null, handlerExecutor);

        // WHEN
        // nothing listens on this port, so the TCP connect is refused at once
        Promise<MqttConnectResult> connectFuture = client.connect("127.0.0.1", closedPort());

        // THEN
        // a refusal takes milliseconds; the keep-alive (60 s) is the only timeout that would otherwise end the wait
        Awaitility.await("waiting for the refused connect to fail")
                .atMost(Duration.ofSeconds(5L))
                .until(connectFuture::isDone);
        assertThat(connectFuture.isSuccess()).isFalse();
        assertThat(Throwables.getRootCause(connectFuture.cause())).isInstanceOf(ConnectException.class);
    }

    @Test
    void testTlsConnectToPlainPortFailsWithSslException() throws SSLException {
        // GIVEN
        // trust anything, so the failure can only be the TLS handshake meeting a plain MQTT listener
        SslContext sslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
        var clientConfig = new MqttClientConfig(sslContext);
        clientConfig.setOwnerId("Test[TlsToPlainPort]");
        clientConfig.setClientId("tls-to-plain");
        clientConfig.setReconnect(false);
        clientConfig.setRetransmissionConfig(new MqttClientConfig.RetransmissionConfig(3, 5000L, 0d));
        client = MqttClient.create(clientConfig, null, handlerExecutor);

        // WHEN
        Promise<MqttConnectResult> connectFuture = client.connect(broker.getHost(), broker.getMqttPort());

        // THEN
        // past SslHandler's own 10 s handshake timeout, so the await cannot race it
        Awaitility.await("waiting for the TLS connect to a plain port to fail")
                .atMost(Duration.ofSeconds(15L))
                .until(connectFuture::isDone);
        assertThat(connectFuture.isSuccess()).isFalse();
        assertThat(Throwables.getRootCause(connectFuture.cause())).isInstanceOf(SSLException.class);
    }

    @Test
    void testConnectFailsWhenChannelClosesBeforeConnack() {
        // GIVEN
        CountDownLatch connackDropped = new CountDownLatch(1);
        proxy = proxyDropping(MqttMessageType.CONNACK, connackDropped);

        var clientConfig = newConfig("Test[CloseBeforeConnack]", "close-before-connack");
        client = MqttClient.create(clientConfig, null, handlerExecutor);

        Promise<MqttConnectResult> connectFuture = client.connect(broker.getHost(), proxy.getPort());
        awaitLatch(connackDropped, "waiting for the CONNACK to be dropped");
        assertThat(connectFuture.isDone()).isFalse();

        // WHEN
        proxy.stop();

        // THEN
        Awaitility.await("waiting for the connect to fail")
                .atMost(Duration.ofSeconds(5L))
                .until(connectFuture::isDone);
        assertThat(connectFuture.cause()).isInstanceOf(ChannelClosedException.class);
    }

    @Test
    void testConnectFailsWhenDisconnectedBeforeTcpConnectCompletes() {
        // GIVEN
        var clientConfig = new MqttClientConfig();
        clientConfig.setOwnerId("Test[DisconnectBeforeConnect]");
        clientConfig.setClientId("disconnect-first");
        clientConfig.setRetransmissionConfig(new MqttClientConfig.RetransmissionConfig(3, 5000L, 0d));
        client = MqttClient.create(clientConfig, null, handlerExecutor);
        clientEventLoop = new NioEventLoopGroup(1);
        client.setEventLoop(clientEventLoop);

        // hold the client's only loop, so the TCP connect cannot complete before disconnect() runs
        CountDownLatch loopHeld = new CountDownLatch(1);
        clientEventLoop.execute(() -> {
            try {
                loopHeld.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        Promise<MqttConnectResult> connectFuture = client.connect(broker.getHost(), broker.getMqttPort());

        // WHEN
        client.disconnect();
        loopHeld.countDown();

        // THEN
        // the TCP connect then completes into a disconnected client, which closes the channel before any CONNACK
        Awaitility.await("waiting for the connect to fail")
                .atMost(Duration.ofSeconds(5L))
                .until(connectFuture::isDone);
        assertThat(connectFuture.cause()).isInstanceOf(ChannelClosedException.class);
        assertThat(client.isConnected()).isFalse();
    }

    @Test
    void testConnackRefusingWithMqtt5ReasonCodeCompletesConnect() {
        // GIVEN
        // the proxy turns the broker's acceptance into a refusal whose code only MQTT 5 defines
        proxy = MqttTestProxy.builder()
                .localPort(randomPort)
                .brokerHost(broker.getHost())
                .brokerPort(broker.getMqttPort())
                .brokerToClientRewriter(msg -> msg.fixedHeader().messageType() != MqttMessageType.CONNACK ? msg
                        : MqttMessageBuilders.connAck().returnCode(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED_5).build())
                .build();

        var clientConfig = newConfig("Test[Mqtt5Refusal]", "mqtt5-refusal");
        client = MqttClient.create(clientConfig, null, handlerExecutor);

        // WHEN
        Promise<MqttConnectResult> connectFuture = client.connect(broker.getHost(), proxy.getPort());

        // THEN
        // a refusal completes the future with an unsuccessful result, as the MQTT 3.1.1 refusals do
        Awaitility.await("waiting for the refused connect to complete")
                .atMost(Duration.ofSeconds(5L))
                .until(connectFuture::isDone);
        assertThat(connectFuture.isSuccess()).isTrue();
        assertThat(connectFuture.getNow().isSuccess()).isFalse();
        assertThat(connectFuture.getNow().getReturnCode()).isEqualTo(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED_5);
        Awaitility.await("waiting for the refused connection to close")
                .atMost(Duration.ofSeconds(5L))
                .until(() -> connectFuture.getNow().getCloseFuture().isDone());
    }

    @Test
    void testPubackListenerThatPublishesInlineCompletesBothPublishes() {
        // GIVEN
        // PUBACKs are withheld while the flag is set; the trigger's is then released by hand once its listener is added
        AtomicReference<MqttMessage> withheldPuback = new AtomicReference<>();
        AtomicBoolean withholdPubacks = new AtomicBoolean(true);
        proxy = MqttTestProxy.builder()
                .localPort(randomPort)
                .brokerHost(broker.getHost())
                .brokerPort(broker.getMqttPort())
                .brokerToClientRewriter(msg -> {
                    if (msg.fixedHeader().messageType() != MqttMessageType.PUBACK || !withholdPubacks.get()) {
                        return msg;
                    }
                    withheldPuback.set(msg);
                    return null;
                })
                .build();

        var clientConfig = newConfig("Test[PubackInlinePublish]", "puback-inline-publish");
        // long enough that no retransmission happens during the test
        clientConfig.setRetransmissionConfig(new MqttClientConfig.RetransmissionConfig(3, 30_000L, 0d));
        client = MqttClient.create(clientConfig, null, handlerExecutor);
        // one loop: the publish promises notify on the loop that handles PUBACK, so a listener runs inline in it
        clientEventLoop = new NioEventLoopGroup(1);
        client.setEventLoop(clientEventLoop);
        connect(broker.getHost(), proxy.getPort());
        Map<Integer, MqttPendingPublish> pendingPublishes = ((MqttClientImpl) client).getPendingPublishes();

        TrackedByteBuf triggerPayload = new TrackedByteBuf("trigger");
        Future<Void> trigger = client.publish("puback-inline/trigger", triggerPayload, MqttQoS.AT_LEAST_ONCE);
        int triggerId = pendingPublishes.entrySet().stream()
                .filter(e -> e.getValue().getFuture() == trigger)
                .findFirst().orElseThrow().getKey();
        Awaitility.await("waiting for the trigger's PUBACK to be withheld")
                .atMost(Duration.ofSeconds(10L))
                .until(() -> withheldPuback.get() != null);
        withholdPubacks.set(false);

        // a listener running while its entry is still pending runs inside the PUBACK's computation on the map: its
        // publish then updates the map from inside that computation, which ConcurrentHashMap forbids
        AtomicReference<Thread> listenerThread = new AtomicReference<>();
        AtomicBoolean pendingWhenListenerRan = new AtomicBoolean(true);
        AtomicReference<Future<Void>> second = new AtomicReference<>();
        trigger.addListener(f -> {
            listenerThread.set(Thread.currentThread());
            pendingWhenListenerRan.set(pendingPublishes.containsKey(triggerId));
            second.set(client.publish("puback-inline/second", new TrackedByteBuf("second"), MqttQoS.AT_LEAST_ONCE));
        });

        // WHEN
        proxy.sendToClient(withheldPuback.get());

        // THEN
        Awaitility.await("waiting for the trigger to be acknowledged")
                .atMost(Duration.ofSeconds(10L))
                .until(trigger::isDone);
        assertThat(trigger.isSuccess()).isTrue();
        assertThat(clientEventLoop.next().inEventLoop(listenerThread.get()))
                .describedAs("the listener ran inline on the loop that handled the PUBACK").isTrue();
        assertThat(pendingWhenListenerRan.get())
                .describedAs("the acknowledged publish was still pending when its listener ran").isFalse();
        Awaitility.await("waiting for the listener's publish to be acknowledged")
                .atMost(Duration.ofSeconds(10L))
                .until(() -> second.get() != null && second.get().isDone());
        assertThat(second.get().isSuccess()).describedAs("the listener's publish succeeded").isTrue();
        assertThat(pendingPublishes).describedAs("pending publishes after the trigger's PUBACK").doesNotContainKey(triggerId);
        assertPayloadFullyReleased(triggerPayload);
    }

    @Test
    void testInFlightSubscribeFailsOnChannelClose() {
        // GIVEN
        CountDownLatch subackDropped = new CountDownLatch(1);
        proxy = proxyDropping(MqttMessageType.SUBACK, subackDropped);

        var clientConfig = newConfig("Test[SubscribeClose]", "subscribe-close");
        clientConfig.setRetransmissionConfig(new MqttClientConfig.RetransmissionConfig(3, 30_000L, 0d));
        client = MqttClient.create(clientConfig, null, handlerExecutor);
        connect(broker.getHost(), proxy.getPort());

        Future<MqttQoS> subscribeFuture = client.on("subscribe-close", msg -> Futures.immediateVoidFuture(), MqttQoS.AT_LEAST_ONCE);
        awaitLatch(subackDropped, "waiting for the SUBACK to be dropped");
        assertThat(subscribeFuture.isDone()).isFalse();

        // WHEN
        proxy.stop();

        // THEN
        Awaitility.await("waiting for the in-flight subscribe to fail")
                .atMost(Duration.ofSeconds(10L))
                .until(subscribeFuture::isDone);
        assertThat(subscribeFuture.cause()).isInstanceOf(ChannelClosedException.class);
    }

    @Test
    void testInFlightUnsubscribeFailsOnChannelClose() {
        // GIVEN
        CountDownLatch unsubackDropped = new CountDownLatch(1);
        proxy = proxyDropping(MqttMessageType.UNSUBACK, unsubackDropped);

        var clientConfig = newConfig("Test[UnsubscribeClose]", "unsubscribe-close");
        clientConfig.setRetransmissionConfig(new MqttClientConfig.RetransmissionConfig(3, 30_000L, 0d));
        client = MqttClient.create(clientConfig, null, handlerExecutor);
        connect(broker.getHost(), proxy.getPort());

        String topic = "unsubscribe-close";
        Future<MqttQoS> subscribeFuture = client.on(topic, msg -> Futures.immediateVoidFuture(), MqttQoS.AT_LEAST_ONCE);
        Awaitility.await("waiting for the subscribe to be granted")
                .atMost(Duration.ofSeconds(10L))
                .until(subscribeFuture::isSuccess);
        Future<Void> unsubscribeFuture = client.off(topic);
        awaitLatch(unsubackDropped, "waiting for the UNSUBACK to be dropped");
        assertThat(unsubscribeFuture.isDone()).isFalse();

        // WHEN
        proxy.stop();

        // THEN
        Awaitility.await("waiting for the in-flight unsubscribe to fail")
                .atMost(Duration.ofSeconds(10L))
                .until(unsubscribeFuture::isDone);
        assertThat(unsubscribeFuture.cause()).isInstanceOf(ChannelClosedException.class);
    }

    @Test
    void testSubscribeBeforeConnectFailsWhenConnectFails() throws IOException {
        // GIVEN
        var clientConfig = newConfig("Test[SubscribeBeforeFailedConnect]", "sub-before-failed-conn");
        client = MqttClient.create(clientConfig, null, handlerExecutor);
        // the client creates its loop group on the first connect; a subscription made before it needs one given
        clientEventLoop = new NioEventLoopGroup(1);
        client.setEventLoop(clientEventLoop);
        // with no channel yet, the SUBSCRIBE waits for the CONNACK
        Future<MqttQoS> subscribeFuture = client.on("sub-before-failed-connect", msg -> Futures.immediateVoidFuture(), MqttQoS.AT_LEAST_ONCE);

        // WHEN
        // refused, and with reconnect off no CONNACK will ever come
        client.connect("127.0.0.1", closedPort());

        // THEN
        Awaitility.await("waiting for the waiting subscribe to fail")
                .atMost(Duration.ofSeconds(5L))
                .until(subscribeFuture::isDone);
        assertThat(subscribeFuture.cause()).isInstanceOf(ChannelClosedException.class);
        assertThat(Throwables.getRootCause(subscribeFuture.cause())).isInstanceOf(ConnectException.class);
    }

    @Test
    void testSubscribeBeforeConnectFailsOnDisconnect() {
        // GIVEN
        var clientConfig = new MqttClientConfig();
        clientConfig.setOwnerId("Test[SubscribeThenDisconnect]");
        clientConfig.setClientId("sub-then-disconnect");
        clientConfig.setRetransmissionConfig(new MqttClientConfig.RetransmissionConfig(3, 5000L, 0d));
        client = MqttClient.create(clientConfig, null, handlerExecutor);
        clientEventLoop = new NioEventLoopGroup(1);
        client.setEventLoop(clientEventLoop);
        Future<MqttQoS> subscribeFuture = client.on("sub-then-disconnect", msg -> Futures.immediateVoidFuture(), MqttQoS.AT_LEAST_ONCE);

        // WHEN
        client.disconnect();

        // THEN
        Awaitility.await("waiting for the waiting subscribe to fail")
                .atMost(Duration.ofSeconds(5L))
                .until(subscribeFuture::isDone);
        assertThat(subscribeFuture.cause()).isInstanceOf(ChannelClosedException.class);
    }

    @Test
    void testSubscribeAfterDisconnectFails() {
        // GIVEN
        var clientConfig = new MqttClientConfig();
        clientConfig.setOwnerId("Test[SubscribeAfterDisconnect]");
        clientConfig.setClientId("sub-after-disconnect");
        clientConfig.setRetransmissionConfig(new MqttClientConfig.RetransmissionConfig(3, 5000L, 0d));
        client = MqttClient.create(clientConfig, null, handlerExecutor);
        clientEventLoop = new NioEventLoopGroup(1);
        client.setEventLoop(clientEventLoop);
        client.disconnect();

        // WHEN
        // a disconnected client never connects again, so no CONNACK will come to send this SUBSCRIBE
        Future<MqttQoS> subscribeFuture = client.on("sub-after-disconnect", msg -> Futures.immediateVoidFuture(), MqttQoS.AT_LEAST_ONCE);

        // THEN
        Awaitility.await("waiting for the subscribe to fail")
                .atMost(Duration.ofSeconds(5L))
                .until(subscribeFuture::isDone);
        assertThat(subscribeFuture.cause()).isInstanceOf(ChannelClosedException.class);
    }

    @Test
    void testSubscribeWhileReconnectIsPendingFailsOnDisconnect() {
        // GIVEN
        proxy = MqttTestProxy.builder()
                .localPort(randomPort)
                .brokerHost(broker.getHost())
                .brokerPort(broker.getMqttPort())
                .build();

        var clientConfig = new MqttClientConfig();
        clientConfig.setOwnerId("Test[SubscribeReconnectPendingDisconnect]");
        clientConfig.setClientId("sub-reconnect-pending");
        // reconnect stays on, a minute out: meanwhile the client still holds the channel that closed
        clientConfig.setReconnectDelay(60);
        clientConfig.setRetransmissionConfig(new MqttClientConfig.RetransmissionConfig(3, 30_000L, 0d));
        client = MqttClient.create(clientConfig, null, handlerExecutor);
        clientEventLoop = new NioEventLoopGroup(1);
        client.setEventLoop(clientEventLoop);
        Promise<MqttConnectResult> connectFuture = client.connect(broker.getHost(), proxy.getPort());
        Awaitility.await("waiting for client to connect")
                .atMost(Duration.ofSeconds(10L))
                .until(connectFuture::isSuccess);
        // notified after the client's own close listener, which was added first
        CountDownLatch closeCleanedUp = new CountDownLatch(1);
        connectFuture.getNow().getCloseFuture().addListener(f -> closeCleanedUp.countDown());
        proxy.stop();
        awaitLatch(closeCleanedUp, "waiting for the close cleanup to run");

        // made after the close cleanup, so only disconnect() is left to fail it
        Future<MqttQoS> subscribeFuture = client.on("sub-reconnect-pending", msg -> Futures.immediateVoidFuture(), MqttQoS.AT_LEAST_ONCE);
        assertThat(subscribeFuture.isDone()).isFalse();

        // WHEN
        client.disconnect();
        // cancels the scheduled reconnect and retransmission: nothing else is left to complete the subscribe
        clientEventLoop.shutdownGracefully();

        // THEN
        Awaitility.await("waiting for the subscribe to fail")
                .atMost(Duration.ofSeconds(5L))
                .until(subscribeFuture::isDone);
        assertThat(subscribeFuture.cause()).isInstanceOf(ChannelClosedException.class);
    }

    @Test
    void testSubscribeDuringReconnectDelayIsSentOnReconnect() {
        // GIVEN
        proxy = MqttTestProxy.builder()
                .localPort(randomPort)
                .brokerHost(broker.getHost())
                .brokerPort(broker.getMqttPort())
                .build();
        int proxyPort = proxy.getPort();

        var clientConfig = new MqttClientConfig();
        clientConfig.setOwnerId("Test[SubscribeDuringReconnect]");
        clientConfig.setClientId("sub-during-reconnect");
        // reconnect stays on (1 s); the retransmission, 30 s out, cannot be what sends the SUBSCRIBE within the bound below
        clientConfig.setRetransmissionConfig(new MqttClientConfig.RetransmissionConfig(3, 30_000L, 0d));
        client = MqttClient.create(clientConfig, null, handlerExecutor);
        Promise<MqttConnectResult> connectFuture = client.connect(broker.getHost(), proxyPort);
        Awaitility.await("waiting for client to connect")
                .atMost(Duration.ofSeconds(10L))
                .until(connectFuture::isSuccess);
        // notified after the client's own close listener, which was added first
        CountDownLatch closeCleanedUp = new CountDownLatch(1);
        connectFuture.getNow().getCloseFuture().addListener(f -> closeCleanedUp.countDown());
        proxy.stop();
        awaitLatch(closeCleanedUp, "waiting for the close cleanup to run");

        // WHEN
        // made during the reconnect delay, while the client still holds the channel that closed
        String topic = "sub-during-reconnect";
        CountDownLatch delivered = new CountDownLatch(1);
        Future<MqttQoS> subscribeFuture = client.on(topic, msg -> {
            delivered.countDown();
            return Futures.immediateVoidFuture();
        }, MqttQoS.AT_LEAST_ONCE);
        assertThat(subscribeFuture.isDone()).isFalse();
        // back on the same port before the reconnect fires, so the reconnect succeeds
        proxy = MqttTestProxy.builder()
                .localPort(proxyPort)
                .brokerHost(broker.getHost())
                .brokerPort(broker.getMqttPort())
                .build();

        // THEN
        // the CONNACK resend is what sends it: the bound is well short of the retransmission delay
        Awaitility.await("waiting for the subscribe to be granted on the new connection")
                .atMost(Duration.ofSeconds(10L))
                .until(subscribeFuture::isDone);
        assertThat(subscribeFuture.isSuccess()).describedAs("subscribe granted, cause %s", subscribeFuture.cause()).isTrue();
        assertThat(subscribeFuture.getNow()).isEqualTo(MqttQoS.AT_LEAST_ONCE);
        ByteBuf message = PooledByteBufAllocator.DEFAULT.buffer().writeBytes("after reconnect".getBytes(StandardCharsets.UTF_8));
        Future<Void> publishFuture = client.publish(topic, message, MqttQoS.AT_LEAST_ONCE);
        awaitLatch(delivered, "waiting for the publish to reach the subscription's handler");
        Awaitility.await("waiting for the publish to be acknowledged")
                .atMost(Duration.ofSeconds(10L))
                .until(publishFuture::isDone);
        assertThat(publishFuture.isSuccess()).describedAs("publish acknowledged, cause %s", publishFuture.cause()).isTrue();
    }

    @Test
    void testResubscribeFromCloseFailureListenerSurvivesTheCleanup() {
        // GIVEN
        AtomicBoolean dropSubacks = new AtomicBoolean(true);
        CountDownLatch subacksDropped = new CountDownLatch(2);
        proxy = MqttTestProxy.builder()
                .localPort(randomPort)
                .brokerHost(broker.getHost())
                .brokerPort(broker.getMqttPort())
                .brokerToClientInterceptor(msg -> {
                    if (msg.fixedHeader().messageType() != MqttMessageType.SUBACK || !dropSubacks.get()) {
                        return true;
                    }
                    subacksDropped.countDown();
                    return false;
                })
                .build();

        var clientConfig = new MqttClientConfig();
        clientConfig.setOwnerId("Test[ResubscribeOnClose]");
        clientConfig.setClientId("resubscribe-on-close");
        // reconnect stays on (1 s), and its CONNACK resend sends the re-subscribe; the retransmission, 30 s out, cannot
        // be what sends it within the bound below
        clientConfig.setRetransmissionConfig(new MqttClientConfig.RetransmissionConfig(3, 30_000L, 0d));
        client = MqttClient.create(clientConfig, null, handlerExecutor);
        // one loop: the subscribe promises notify on the loop that runs the close cleanup, so a listener runs inline in it
        clientEventLoop = new NioEventLoopGroup(1);
        client.setEventLoop(clientEventLoop);
        Promise<MqttConnectResult> connectFuture = client.connect(broker.getHost(), proxy.getPort());
        Awaitility.await("waiting for client to connect")
                .atMost(Duration.ofSeconds(10L))
                .until(connectFuture::isSuccess);
        MqttClientImpl impl = (MqttClientImpl) client;

        // two in flight, so the close sweep has an entry still to visit after the one whose listener re-subscribes
        String topic = "resubscribe-on-close";
        Future<MqttQoS> first = client.on(topic, msg -> Futures.immediateVoidFuture(), MqttQoS.AT_LEAST_ONCE);
        client.on(topic + "/other", msg -> Futures.immediateVoidFuture(), MqttQoS.AT_LEAST_ONCE);
        awaitLatch(subacksDropped, "waiting for both SUBACKs to be dropped");
        AtomicReference<Future<MqttQoS>> resubscribe = new AtomicReference<>();
        first.addListener(f -> {
            if (!f.isSuccess()) {
                resubscribe.set(client.on(topic, msg -> Futures.immediateVoidFuture(), MqttQoS.AT_LEAST_ONCE));
            }
        });

        // notified after the client's own close listener, which was added first: this records the state it left
        Channel channel = connectFuture.getNow().getCloseFuture().channel();
        AtomicBoolean resubscribeDone = new AtomicBoolean(true);
        AtomicBoolean topicPending = new AtomicBoolean();
        AtomicBoolean entryPending = new AtomicBoolean();
        CountDownLatch closeCleanedUp = new CountDownLatch(1);
        channel.closeFuture().addListener(f -> {
            Future<MqttQoS> resubscribed = resubscribe.get();
            if (resubscribed != null) {
                resubscribeDone.set(resubscribed.isDone());
                topicPending.set(impl.getPendingSubscribeTopics().contains(topic));
                entryPending.set(impl.getPendingSubscriptions().values().stream().anyMatch(p -> p.getFuture() == resubscribed));
            }
            closeCleanedUp.countDown();
        });
        dropSubacks.set(false);

        // WHEN
        channel.close();

        // THEN
        awaitLatch(closeCleanedUp, "waiting for the close cleanup to run");
        assertThat(first.cause()).isInstanceOf(ChannelClosedException.class);
        assertThat(resubscribe.get()).describedAs("the listener re-subscribed during the close cleanup").isNotNull();
        assertThat(resubscribeDone.get()).describedAs("re-subscribe completed by the cleanup it was made in").isFalse();
        assertThat(topicPending.get()).describedAs("re-subscribed topic pending after the cleanup").isTrue();
        assertThat(entryPending.get()).describedAs("re-subscribe entry pending after the cleanup").isTrue();
        Awaitility.await("waiting for the re-subscribe to be granted on the new connection")
                .atMost(Duration.ofSeconds(10L))
                .until(resubscribe.get()::isDone);
        assertThat(resubscribe.get().isSuccess()).describedAs("re-subscribe granted, cause %s", resubscribe.get().cause()).isTrue();
        assertThat(impl.getServerSubscriptions()).containsKey(topic);
    }

    /**
     * The client config the tests share: reconnect off, and retransmission slow enough not to fire unless a test waits.
     */
    private static MqttClientConfig newConfig(String ownerId, String clientId) {
        var clientConfig = new MqttClientConfig();
        clientConfig.setOwnerId(ownerId);
        clientConfig.setClientId(clientId);
        clientConfig.setReconnect(false);
        clientConfig.setRetransmissionConfig(new MqttClientConfig.RetransmissionConfig(3, 5000L, 0d));
        return clientConfig;
    }

    /**
     * A proxy to the broker that drops every message of {@code type} on its way to the client, counting each drop down
     * on {@code dropped}.
     */
    private MqttTestProxy proxyDropping(MqttMessageType type, CountDownLatch dropped) {
        return MqttTestProxy.builder()
                .localPort(randomPort)
                .brokerHost(broker.getHost())
                .brokerPort(broker.getMqttPort())
                .brokerToClientInterceptor(msg -> {
                    if (msg.fixedHeader().messageType() != type) {
                        return true;
                    }
                    dropped.countDown();
                    return false;
                })
                .build();
    }

    private MqttTestProxy proxyDropping(MqttMessageType type) {
        return proxyDropping(type, new CountDownLatch(0));
    }

    /**
     * A local port nothing listens on: bound, then closed again.
     */
    private static int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void awaitLatch(CountDownLatch latch, String description) {
        Awaitility.await(description)
                .atMost(Duration.ofSeconds(10L))
                .until(() -> latch.getCount() == 0);
    }

    /**
     * Leaves a publish in the state {@link MqttClientImpl#publish} holds between registering its entry and claiming the
     * first write. No public call can hold that window open, so the entry is registered directly - through the
     * production code, so the test runs the real entry - and the CONNACK resend is the one to claim it.
     */
    private MqttPendingPublish registerUnsentPublish(String topic, ByteBuf payload, MqttQoS qos) {
        // the client creates its loop group on the first connect; give it one now, which that connect then reuses
        clientEventLoop = new NioEventLoopGroup(1);
        client.setEventLoop(clientEventLoop);
        return ((MqttClientImpl) client).registerPendingPublish(topic, payload, qos, false);
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
        private final List<Thread> retainThreads = new CopyOnWriteArrayList<>();

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
            retainThreads.add(Thread.currentThread());
            try {
                return super.retain();
            } catch (IllegalReferenceCountException e) {
                illegalRefCntOps.incrementAndGet();
                throw e;
            }
        }

        @Override
        public ByteBuf retain(int increment) {
            retainThreads.add(Thread.currentThread());
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
