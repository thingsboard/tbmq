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
package org.thingsboard.mqtt.broker.lite;

import org.eclipse.paho.mqttv5.client.IMqttMessageListener;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.thingsboard.mqtt.broker.ThingsboardMqttBrokerApplication;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * [MQTT-3.1.2-8]: the Will Message of a client that is connected when the broker shuts down is published after its
 * connection closes, not dropped. The broker disconnects such a client before its publish pipeline drains, so the will
 * reaches a subscriber that is still connected, and the queue of an offline persistent subscriber before the store
 * closes.
 * <p>
 * Each test closes the context itself, so the class runs only the listeners that inject the context and close it
 * after each test method: the default ones look the context up after each test method and fail on a closed one.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ThingsboardMqttBrokerApplication.class)
@ActiveProfiles("test")
@DaoSqlTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestExecutionListeners({DependencyInjectionTestExecutionListener.class, DirtiesContextTestExecutionListener.class})
public class LiteShutdownWillTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final long SESSION_EXPIRY_SECONDS = 3600;
    private static final int SERVER_SHUTTING_DOWN = 0x8B;

    @ClassRule
    public static TempStorePathRule store = new TempStorePathRule();

    @Autowired
    private ConfigurableApplicationContext context;

    @Value("${listener.tcp.bind_port}")
    private int mqttPort;

    private final List<Object> clients = new ArrayList<>();

    @After
    public void closeClients() {
        clients.forEach(LiteTestClients::closeQuietly);
        clients.clear();
    }

    @Test
    public void mqtt_3_1_2_8_aSubscriberConnectedUntilTheDrainReceivesTheWillOfAClientConnectedAtShutdown() throws Exception {
        String topic = "shutdown-will/online";
        List<MqttMessage> received = new CopyOnWriteArrayList<>();
        MqttClient subscriber = v5Client("will-online-sub");
        subscriber.connect(cleanStart());
        subscriber.subscribe(new MqttSubscription[]{new MqttSubscription(topic, 1)},
                new IMqttMessageListener[]{(t, message) -> received.add(message)});
        RecordingCallback willClientCallback = new RecordingCallback();
        MqttClient willClient = v5Client("will-online-client");
        willClient.setCallback(willClientCallback);
        willClient.connect(withWill(cleanStart(), topic, "online-will"));

        context.close();

        await().atMost(TIMEOUT).until(() -> !received.isEmpty());
        assertThat(received).singleElement().satisfies(message -> {
            assertThat(message.getQos()).isEqualTo(1);
            assertThat(new String(message.getPayload(), StandardCharsets.UTF_8)).isEqualTo("online-will");
        });
        // the broker closed the connection of the will's client with a DISCONNECT of its own
        await().atMost(TIMEOUT).until(() -> willClientCallback.disconnectCode.get() != -1);
        assertThat(willClientCallback.disconnectCode.get()).isEqualTo(SERVER_SHUTTING_DOWN);
    }

    @Test
    public void mqtt_3_1_2_8_aQos1WillToAnOfflinePersistentSubscriberIsInItsQueueAfterTheRestart() throws Exception {
        String topic = "shutdown-will/offline";
        String subscriberId = "will-offline-sub";
        MqttClient subscriber = v5Client(subscriberId);
        subscriber.connect(persistentSession());
        subscriber.subscribe(topic, 1);
        subscriber.disconnect();
        ClientSessionCache sessions = context.getBean(ClientSessionCache.class);
        await().atMost(TIMEOUT).until(() -> isDisconnected(sessions, subscriberId));
        MqttClient willClient = v5Client("will-offline-client");
        willClient.connect(withWill(cleanStart(), topic, "offline-will"));

        context.close();
        restart();

        RecordingCallback returningSubscriberCallback = new RecordingCallback();
        MqttClient returningSubscriber = v5Client(subscriberId);
        returningSubscriber.setCallback(returningSubscriberCallback);
        returningSubscriber.connect(persistentSession());

        await().atMost(TIMEOUT).until(() -> !returningSubscriberCallback.received.isEmpty());
        assertThat(returningSubscriberCallback.received).singleElement().satisfies(message -> {
            assertThat(message.getQos()).isEqualTo(1);
            assertThat(new String(message.getPayload(), StandardCharsets.UTF_8)).isEqualTo("offline-will");
        });
    }

    /**
     * Starts the context of this class again, on the same store. The test framework still caches the closed context,
     * so it is evicted first; the new one takes its place and is the one closed after the test method.
     */
    private void restart() {
        TestContext testContext = new TestContextManager(getClass()).getTestContext();
        testContext.markApplicationContextDirty(DirtiesContext.HierarchyMode.EXHAUSTIVE);
        testContext.getApplicationContext();
    }

    private static boolean isDisconnected(ClientSessionCache sessions, String clientId) {
        ClientSessionInfo info = sessions.getClientSessionInfo(clientId);
        return info != null && !info.isConnected();
    }

    private MqttClient v5Client(String clientId) throws MqttException {
        MqttClient client = new MqttClient("tcp://localhost:" + mqttPort, clientId, new MemoryPersistence());
        clients.add(client);
        return client;
    }

    private static MqttConnectionOptions cleanStart() {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(true);
        return options;
    }

    private static MqttConnectionOptions persistentSession() {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(false);
        options.setSessionExpiryInterval(SESSION_EXPIRY_SECONDS);
        return options;
    }

    private static MqttConnectionOptions withWill(MqttConnectionOptions options, String topic, String payload) {
        options.setWill(topic, new MqttMessage(payload.getBytes(StandardCharsets.UTF_8), 1, false, new MqttProperties()));
        return options;
    }

    /**
     * Records the messages that arrive outside of a subscribe call and the reason code of a DISCONNECT from the broker.
     */
    private static final class RecordingCallback implements MqttCallback {

        private final List<MqttMessage> received = new CopyOnWriteArrayList<>();
        private final AtomicInteger disconnectCode = new AtomicInteger(-1);

        @Override
        public void messageArrived(String topic, MqttMessage message) {
            received.add(message);
        }

        @Override
        public void disconnected(MqttDisconnectResponse disconnectResponse) {
            disconnectCode.set(disconnectResponse.getReturnCode());
        }

        @Override
        public void mqttErrorOccurred(MqttException exception) {
        }

        @Override
        public void deliveryComplete(IMqttToken token) {
        }

        @Override
        public void connectComplete(boolean reconnect, String serverURI) {
        }

        @Override
        public void authPacketArrived(int reasonCode, MqttProperties properties) {
        }

    }

}
