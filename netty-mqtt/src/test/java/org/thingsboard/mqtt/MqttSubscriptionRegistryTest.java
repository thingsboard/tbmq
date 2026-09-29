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
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubAckMessage;
import io.netty.handler.codec.mqtt.MqttSubAckPayload;
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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Testcontainers
class MqttSubscriptionRegistryTest {

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
                return "MQTT subscription registry test handler";
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
    void aPublishMatchingSeveralFiltersReachesOnlyTheFirstRegisteredOne() {
        // GIVEN
        var clientConfig = new MqttClientConfig();
        clientConfig.setOwnerId("Test[FirstRegisteredFilterServes]");
        clientConfig.setClientId("first-wins");
        clientConfig.setRetransmissionConfig(new MqttClientConfig.RetransmissionConfig(3, 5000L, 0d));

        client = MqttClient.create(clientConfig, null, handlerExecutor);
        connect(broker.getHost(), broker.getMqttPort());

        List<String> served = Collections.synchronizedList(new ArrayList<>(2));

        subscribe("sensors/#", record(served, "first"));
        subscribe("sensors/+", record(served, "second"));

        // WHEN
        publish("sensors/a");

        // THEN
        awaitDeliveries(served);

        assertThat(served).isNotEmpty();
        Awaitility.await("holding the assertion over a quiet period")
                .during(Duration.ofMillis(500))
                .atMost(Duration.ofSeconds(10L))
                .untilAsserted(() -> assertThat(served).containsOnly("first"));
    }

    @Test
    void theRegistrationOrderDecidesWhichFilterServes() {
        // GIVEN
        var clientConfig = new MqttClientConfig();
        clientConfig.setOwnerId("Test[RegistrationOrderDecides]");
        clientConfig.setClientId("order-decides");
        clientConfig.setRetransmissionConfig(new MqttClientConfig.RetransmissionConfig(3, 5000L, 0d));

        client = MqttClient.create(clientConfig, null, handlerExecutor);
        connect(broker.getHost(), broker.getMqttPort());

        List<String> served = Collections.synchronizedList(new ArrayList<>(2));

        // the very same two filters, registered in the opposite order
        subscribe("sensors/+", record(served, "second"));
        subscribe("sensors/#", record(served, "first"));

        // WHEN
        publish("sensors/a");

        // THEN
        awaitDeliveries(served);

        assertThat(served).isNotEmpty();
        Awaitility.await("holding the assertion over a quiet period")
                .during(Duration.ofMillis(500))
                .atMost(Duration.ofSeconds(10L))
                .untilAsserted(() -> assertThat(served).containsOnly("second"));
    }

    @Test
    void registeringTheSameFilterAndHandlerTwiceRegistersItOnce() {
        // GIVEN
        var clientConfig = new MqttClientConfig();
        clientConfig.setOwnerId("Test[DuplicateRegistration]");
        clientConfig.setClientId("dup-register");
        clientConfig.setRetransmissionConfig(new MqttClientConfig.RetransmissionConfig(3, 5000L, 0d));

        client = MqttClient.create(clientConfig, null, handlerExecutor);
        connect(broker.getHost(), broker.getMqttPort());

        List<String> served = Collections.synchronizedList(new ArrayList<>(2));
        MqttHandler handler = record(served, "only");

        // WHEN
        subscribe("sensors/a", handler);
        subscribe("sensors/a", handler);

        // THEN
        assertThat(((MqttClientImpl) client).getSubscriptions()).hasSize(1);
    }

    @Test
    void reRegisteringAFilterWithANewHandlerReplacesItInPlace() {
        // GIVEN
        var clientConfig = new MqttClientConfig();
        clientConfig.setOwnerId("Test[ReRegistrationReplacesInPlace]");
        clientConfig.setClientId("re-register");
        clientConfig.setRetransmissionConfig(new MqttClientConfig.RetransmissionConfig(3, 5000L, 0d));

        client = MqttClient.create(clientConfig, null, handlerExecutor);
        connect(broker.getHost(), broker.getMqttPort());

        List<String> served = Collections.synchronizedList(new ArrayList<>(2));
        MqttHandler handlerA = record(served, "A");
        MqttHandler handlerB = record(served, "B");
        MqttHandler handlerC = record(served, "C");

        subscribe("sensors/a", handlerA);
        subscribe("sensors/b", handlerB);

        // WHEN - a caller re-registering after a reconnect passes a fresh handler instance
        subscribe("sensors/a", handlerC);

        // THEN
        List<MqttSubscription> subscriptions = ((MqttClientImpl) client).getSubscriptions();
        assertThat(subscriptions).hasSize(2);
        assertThat(subscriptions.get(0).getTopic()).isEqualTo("sensors/a");
        assertThat(subscriptions.get(0).getHandler()).isSameAs(handlerC);
        assertThat(subscriptions.get(1).getTopic()).isEqualTo("sensors/b");

        publish("sensors/a");

        Awaitility.await("waiting for the message to be served")
                .atMost(Duration.ofSeconds(10L))
                .until(() -> !served.isEmpty());
        Awaitility.await("holding the assertion over a quiet period")
                .during(Duration.ofMillis(500))
                .atMost(Duration.ofSeconds(10L))
                .untilAsserted(() -> assertThat(served).containsOnly("C"));
    }

    @Test
    void offForOneTopicLeavesTheSameHandlersOtherTopicsRemovable() {
        // GIVEN
        var clientConfig = new MqttClientConfig();
        clientConfig.setOwnerId("Test[OffLeavesOtherTopicsRemovable]");
        clientConfig.setClientId("off-other-topics");
        clientConfig.setRetransmissionConfig(new MqttClientConfig.RetransmissionConfig(3, 5000L, 0d));

        client = MqttClient.create(clientConfig, null, handlerExecutor);
        connect(broker.getHost(), broker.getMqttPort());

        List<String> served = Collections.synchronizedList(new ArrayList<>(1));
        MqttHandler handler = record(served, "h");

        subscribe("sensors/a", handler);
        subscribe("sensors/b", handler);
        assertThat(((MqttClientImpl) client).getSubscriptions()).hasSize(2);

        // WHEN
        unsubscribe("sensors/a", handler);
        unsubscribe("sensors/b", handler);

        // THEN
        assertThat(((MqttClientImpl) client).getSubscriptions()).isEmpty();
    }

    @Test
    void theHandlerRegistrySurvivesAChannelCloseButTheServerSubscriptionsDoNot() {
        // GIVEN
        proxy = MqttTestProxy.builder()
                .localPort(randomPort)
                .brokerHost(broker.getHost())
                .brokerPort(broker.getMqttPort())
                .build();

        var clientConfig = new MqttClientConfig();
        clientConfig.setOwnerId("Test[HandlerRegistrySurvivesClose]");
        clientConfig.setClientId("survive-close");
        clientConfig.setRetransmissionConfig(new MqttClientConfig.RetransmissionConfig(3, 5000L, 0d));
        clientConfig.setReconnect(false); // no reconnect, so the registry state after the close stays observable

        client = MqttClient.create(clientConfig, null, handlerExecutor);
        connect(broker.getHost(), proxy.getPort());

        List<String> served = Collections.synchronizedList(new ArrayList<>(1));
        subscribe("sensors/#", record(served, "first"));

        assertThat(((MqttClientImpl) client).getSubscriptions()).hasSize(1);
        assertThat(((MqttClientImpl) client).getServerSubscriptions()).containsOnlyKeys("sensors/#");

        // WHEN
        proxy.stop(); // drop the connection abruptly

        // THEN
        Awaitility.await("waiting for the channel close to be observed")
                .atMost(Duration.ofSeconds(10L))
                .untilAsserted(() -> assertThat(((MqttClientImpl) client).getServerSubscriptions()).isEmpty());

        assertThat(((MqttClientImpl) client).getSubscriptions())
                .describedAs("the handler registry is client state and must survive a channel close").hasSize(1);
    }

    @Test
    void aRefusedSubscriptionFailsItsFutureAndRegistersNothing() {
        // GIVEN - the proxy turns the broker's grant into a refusal, so the refusal does not depend on broker policy
        proxy = MqttTestProxy.builder()
                .localPort(randomPort)
                .brokerHost(broker.getHost())
                .brokerPort(broker.getMqttPort())
                .brokerToClientRewriter(msg -> withGrantedCode(msg, MqttQoS.FAILURE.value()))
                .build();

        var clientConfig = new MqttClientConfig();
        clientConfig.setOwnerId("Test[RefusedSubscription]");
        clientConfig.setClientId("sub-refused");
        clientConfig.setRetransmissionConfig(new MqttClientConfig.RetransmissionConfig(3, 5000L, 0d));

        client = MqttClient.create(clientConfig, null, handlerExecutor);
        connect(broker.getHost(), proxy.getPort());

        // WHEN
        Future<MqttQoS> subscribeFuture = client.on("sensors/refused", record(new ArrayList<>(), "refused"), MqttQoS.AT_LEAST_ONCE);
        awaitDone(subscribeFuture);

        // THEN
        assertThat(subscribeFuture.isSuccess()).describedAs("a refused subscription must not succeed").isFalse();
        assertThat(subscribeFuture.cause())
                .isInstanceOf(MqttSubscriptionFailedException.class)
                .hasMessageContaining("sensors/refused");
        assertThat(((MqttClientImpl) client).getSubscriptions())
                .noneMatch(s -> s.getTopic().equals("sensors/refused"));
        assertThat(((MqttClientImpl) client).getServerSubscriptions()).doesNotContainKey("sensors/refused");
    }

    @Test
    void aGrantedSubscriptionCompletesWithTheGrantedQos() {
        // GIVEN
        var clientConfig = new MqttClientConfig();
        clientConfig.setOwnerId("Test[GrantedSubscription]");
        clientConfig.setClientId("sub-granted");
        clientConfig.setRetransmissionConfig(new MqttClientConfig.RetransmissionConfig(3, 5000L, 0d));

        client = MqttClient.create(clientConfig, null, handlerExecutor);
        connect(broker.getHost(), broker.getMqttPort());

        // WHEN
        Future<MqttQoS> subscribeFuture = client.on("sensors/granted", record(new ArrayList<>(), "granted"), MqttQoS.EXACTLY_ONCE);
        awaitDone(subscribeFuture);

        // THEN
        assertThat(subscribeFuture.isSuccess()).isTrue();
        assertThat(subscribeFuture.getNow()).isEqualTo(MqttQoS.EXACTLY_ONCE);
    }

    @Test
    void aDowngradedSubscriptionCompletesWithTheDowngradedQos() {
        // GIVEN - the proxy downgrades whatever the broker grants to QoS 1
        proxy = MqttTestProxy.builder()
                .localPort(randomPort)
                .brokerHost(broker.getHost())
                .brokerPort(broker.getMqttPort())
                .brokerToClientRewriter(msg -> withGrantedCode(msg, MqttQoS.AT_LEAST_ONCE.value()))
                .build();

        var clientConfig = new MqttClientConfig();
        clientConfig.setOwnerId("Test[DowngradedSubscription]");
        clientConfig.setClientId("sub-downgraded");
        clientConfig.setRetransmissionConfig(new MqttClientConfig.RetransmissionConfig(3, 5000L, 0d));

        client = MqttClient.create(clientConfig, null, handlerExecutor);
        connect(broker.getHost(), proxy.getPort());

        // WHEN
        Future<MqttQoS> subscribeFuture = client.on("sensors/downgraded", record(new ArrayList<>(), "downgraded"), MqttQoS.EXACTLY_ONCE);
        awaitDone(subscribeFuture);

        // THEN
        assertThat(subscribeFuture.isSuccess()).isTrue();
        assertThat(subscribeFuture.getNow()).isEqualTo(MqttQoS.AT_LEAST_ONCE);
    }

    @Test
    void reSubscribingAnAlreadySubscribedFilterYieldsTheQosOriginallyGranted() {
        // GIVEN - the proxy downgrades whatever the broker grants to QoS 1, and counts the SUBACKs it relays
        AtomicInteger subAcks = new AtomicInteger();
        proxy = MqttTestProxy.builder()
                .localPort(randomPort)
                .brokerHost(broker.getHost())
                .brokerPort(broker.getMqttPort())
                .brokerToClientRewriter(msg -> {
                    if (msg.fixedHeader().messageType() == MqttMessageType.SUBACK) {
                        subAcks.incrementAndGet();
                    }
                    return withGrantedCode(msg, MqttQoS.AT_LEAST_ONCE.value());
                })
                .build();

        var clientConfig = new MqttClientConfig();
        clientConfig.setOwnerId("Test[FastPathGrantedQos]");
        clientConfig.setClientId("sub-fast-path");
        clientConfig.setRetransmissionConfig(new MqttClientConfig.RetransmissionConfig(3, 5000L, 0d));

        client = MqttClient.create(clientConfig, null, handlerExecutor);
        connect(broker.getHost(), proxy.getPort());

        Future<MqttQoS> first = client.on("sensors/fast", record(new ArrayList<>(), "first"), MqttQoS.EXACTLY_ONCE);
        awaitDone(first);
        assertThat(first.getNow()).isEqualTo(MqttQoS.AT_LEAST_ONCE);

        // WHEN - the filter is already subscribed on the server, so no SUBSCRIBE is sent
        Future<MqttQoS> second = client.on("sensors/fast", record(new ArrayList<>(), "second"), MqttQoS.EXACTLY_ONCE);
        awaitDone(second);

        // THEN
        assertThat(second.isSuccess()).isTrue();
        assertThat(second.getNow()).describedAs("the QoS the server granted, not the one requested now")
                .isEqualTo(MqttQoS.AT_LEAST_ONCE);
        assertThat(subAcks).describedAs("the second on() must take the already-subscribed path").hasValue(1);
    }

    @Test
    void theLastHandlerGivenForAFilterInFlightWins() {
        // GIVEN - the proxy withholds the SUBACK, so every on() below finds the SUBSCRIBE in flight
        BlockingQueue<MqttMessage> heldSubAcks = new LinkedBlockingQueue<>();
        proxy = MqttTestProxy.builder()
                .localPort(randomPort)
                .brokerHost(broker.getHost())
                .brokerPort(broker.getMqttPort())
                .brokerToClientRewriter(msg -> {
                    if (msg.fixedHeader().messageType() == MqttMessageType.SUBACK) {
                        heldSubAcks.add(msg); // a SUBACK holds no reference-counted content
                        return null;
                    }
                    return msg;
                })
                .build();

        var clientConfig = new MqttClientConfig();
        clientConfig.setOwnerId("Test[InFlightLastHandlerWins]");
        clientConfig.setClientId("sub-inflight-last-wins");
        clientConfig.setRetransmissionConfig(new MqttClientConfig.RetransmissionConfig(3, 5000L, 0d));

        client = MqttClient.create(clientConfig, null, handlerExecutor);
        connect(broker.getHost(), proxy.getPort());

        List<String> served = Collections.synchronizedList(new ArrayList<>(1));
        MqttHandler handlerA = record(served, "A");
        MqttHandler handlerB = record(served, "B");
        MqttHandler handlerC = record(served, "C");

        // three distinct handlers, so that neither the first nor a middle one can pass for the last
        Future<MqttQoS> first = client.on("sensors/inflight", handlerA, MqttQoS.AT_LEAST_ONCE);
        Future<MqttQoS> second = client.on("sensors/inflight", handlerB, MqttQoS.AT_LEAST_ONCE);
        Future<MqttQoS> third = client.on("sensors/inflight", handlerC, MqttQoS.AT_LEAST_ONCE);

        assertThat(second).describedAs("an on() for a filter in flight shares its future").isSameAs(first);
        assertThat(third).isSameAs(first);

        Awaitility.await("waiting for the proxy to withhold the SUBACK")
                .atMost(Duration.ofSeconds(10L))
                .until(() -> !heldSubAcks.isEmpty());
        assertThat(first.isDone()).isFalse();

        // WHEN
        proxy.sendToClient(heldSubAcks.poll());
        awaitDone(first);

        // THEN
        assertThat(first.isSuccess()).isTrue();
        List<MqttSubscription> subscriptions = ((MqttClientImpl) client).getSubscriptions();
        assertThat(subscriptions).hasSize(1);
        assertThat(subscriptions.get(0).getHandler()).describedAs("the handler given last must win").isSameAs(handlerC);

        publish("sensors/inflight");

        Awaitility.await("waiting for the message to be served")
                .atMost(Duration.ofSeconds(10L))
                .until(() -> !served.isEmpty());
        Awaitility.await("holding the assertion over a quiet period")
                .during(Duration.ofMillis(500))
                .atMost(Duration.ofSeconds(10L))
                .untilAsserted(() -> assertThat(served).containsOnly("C"));
    }

    /**
     * {@code msg} unchanged unless it is a SUBACK, else a SUBACK for the same packet id granting {@code code}.
     */
    private static MqttMessage withGrantedCode(MqttMessage msg, int code) {
        if (msg.fixedHeader().messageType() != MqttMessageType.SUBACK) {
            return msg;
        }
        MqttSubAckMessage subAck = (MqttSubAckMessage) msg;
        return new MqttSubAckMessage(subAck.fixedHeader(), subAck.variableHeader(), new MqttSubAckPayload(code));
    }

    private static void awaitDone(Future<?> future) {
        Awaitility.await("waiting for the subscribe to complete")
                .atMost(Duration.ofSeconds(10L))
                .until(future::isDone);
    }

    private MqttHandler record(List<String> served, String name) {
        return msg -> {
            served.add(name);
            return Futures.immediateVoidFuture();
        };
    }

    private void subscribe(String topicFilter, MqttHandler handler) {
        Future<MqttQoS> subscribeFuture = client.on(topicFilter, handler, MqttQoS.AT_LEAST_ONCE);
        Awaitility.await("waiting for client to subscribe to " + topicFilter)
                .atMost(Duration.ofSeconds(10L))
                .until(subscribeFuture::isDone);
        assertThat(subscribeFuture.isSuccess()).isTrue();
    }

    private void unsubscribe(String topicFilter, MqttHandler handler) {
        Future<Void> unsubscribeFuture = client.off(topicFilter, handler);
        Awaitility.await("waiting for client to unsubscribe from " + topicFilter)
                .atMost(Duration.ofSeconds(10L))
                .until(unsubscribeFuture::isDone);
        assertThat(unsubscribeFuture.isSuccess()).isTrue();
    }

    private void publish(String topic) {
        ByteBuf message = PooledByteBufAllocator.DEFAULT.buffer().writeBytes("test message".getBytes(StandardCharsets.UTF_8));
        client.publish(topic, message, MqttQoS.AT_LEAST_ONCE);
    }

    /**
     * A broker may send one PUBLISH per matching subscription, so a correct client still records more than one
     * delivery here. Wait for a second record so that a wrongly invoked second handler has time to show up,
     * but do not require it.
     */
    private void awaitDeliveries(List<String> served) {
        try {
            Awaitility.await("waiting for the message to be served")
                    .atMost(Duration.ofSeconds(10L))
                    .pollInterval(Duration.ofMillis(100))
                    .until(() -> served.size() > 1);
        } catch (ConditionTimeoutException __) {
            // only one delivery happened, which is fine
        }
        assertThat(served).isNotEmpty();
    }

    private void connect(String host, int port) {
        Promise<MqttConnectResult> connectFuture = client.connect(host, port);
        Awaitility.await("waiting for client to connect")
                .atMost(Duration.ofSeconds(10L))
                .until(connectFuture::isSuccess);
    }

}
