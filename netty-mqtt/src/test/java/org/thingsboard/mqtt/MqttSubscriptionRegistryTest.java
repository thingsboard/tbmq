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
import io.netty.handler.codec.mqtt.MqttQoS;
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
        assertThat(((MqttClientImpl) client).getServerSubscriptions()).containsExactly("sensors/#");

        // WHEN
        proxy.stop(); // drop the connection abruptly

        // THEN
        Awaitility.await("waiting for the channel close to be observed")
                .atMost(Duration.ofSeconds(10L))
                .untilAsserted(() -> assertThat(((MqttClientImpl) client).getServerSubscriptions()).isEmpty());

        assertThat(((MqttClientImpl) client).getSubscriptions())
                .describedAs("the handler registry is client state and must survive a channel close").hasSize(1);
    }

    private MqttHandler record(List<String> served, String name) {
        return (__, payload) -> {
            served.add(name);
            return Futures.immediateVoidFuture();
        };
    }

    private void subscribe(String topicFilter, MqttHandler handler) {
        Future<Void> subscribeFuture = client.on(topicFilter, handler, MqttQoS.AT_LEAST_ONCE);
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
