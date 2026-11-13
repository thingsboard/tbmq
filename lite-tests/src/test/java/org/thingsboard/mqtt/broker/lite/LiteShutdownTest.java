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

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.mqttv5.client.IMqttMessageListener;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
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
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.thingsboard.mqtt.broker.ThingsboardMqttBrokerApplication;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Closes a Lite context while clients are connected and publishing.
 * <p>
 * The test closes the context itself, so it runs only the listeners that inject it and close it after the class:
 * the default ones that reset mocks or publish test events look the context up after each test method and fail on a
 * closed one.
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ThingsboardMqttBrokerApplication.class)
@ActiveProfiles("test")
@DaoSqlTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestExecutionListeners({DependencyInjectionTestExecutionListener.class, DirtiesContextTestExecutionListener.class})
public class LiteShutdownTest {

    private static final int CLEAN_SUBSCRIBERS = 10;
    private static final int PERSISTENT_SUBSCRIBERS = 5;
    private static final int PUBLISHERS = 5;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final long MAX_CLOSE_MS = 8000;

    @ClassRule
    public static TempStorePathRule store = new TempStorePathRule();

    @Autowired
    private ConfigurableApplicationContext context;

    @Value("${listener.tcp.bind_port}")
    private int mqttPort;

    private final List<Object> clients = new ArrayList<>();
    private final ExecutorService publishing = Executors.newFixedThreadPool(PUBLISHERS);
    private volatile boolean publish = true;

    @After
    public void stopClients() throws InterruptedException {
        publish = false;
        publishing.shutdownNow();
        publishing.awaitTermination(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        clients.forEach(LiteTestClients::closeQuietly);
        clients.clear();
    }

    @Test
    public void closingTheContextWithConnectedClientsTakesLessThanEightSeconds() throws Exception {
        List<AtomicInteger> receivedBySubscriber = new ArrayList<>();
        for (int i = 0; i < CLEAN_SUBSCRIBERS; i++) {
            receivedBySubscriber.add(cleanSubscriber("shutdown-sub-" + i));
        }
        for (int i = 0; i < PERSISTENT_SUBSCRIBERS; i++) {
            receivedBySubscriber.add(persistentSubscriber("shutdown-persistent-sub-" + i));
        }
        for (int i = 0; i < PUBLISHERS; i++) {
            startPublisher("shutdown-pub-" + i, "shutdown/" + i);
        }
        await().atMost(TIMEOUT).until(() -> receivedBySubscriber.stream().allMatch(received -> received.get() > 0));

        long t0 = System.nanoTime();
        context.close();
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);

        log.info("context.close() with {} connected clients, {} of them publishing, took {} ms",
                CLEAN_SUBSCRIBERS + PERSISTENT_SUBSCRIBERS + PUBLISHERS, PUBLISHERS, elapsedMs);
        assertThat(elapsedMs).as("context.close() in ms").isLessThan(MAX_CLOSE_MS);
    }

    private AtomicInteger cleanSubscriber(String clientId) throws Exception {
        AtomicInteger received = new AtomicInteger();
        MqttClient client = new MqttClient(serverUri(), clientId, new MemoryPersistence());
        clients.add(client);
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(true);
        client.connect(options);
        client.subscribe(new MqttSubscription[]{new MqttSubscription("shutdown/#", 1)},
                new IMqttMessageListener[]{(topic, message) -> received.incrementAndGet()});
        return received;
    }

    private AtomicInteger persistentSubscriber(String clientId) throws Exception {
        AtomicInteger received = new AtomicInteger();
        org.eclipse.paho.client.mqttv3.MqttClient client =
                new org.eclipse.paho.client.mqttv3.MqttClient(serverUri(), clientId, new org.eclipse.paho.client.mqttv3.persist.MemoryPersistence());
        clients.add(client);
        client.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
            }

            @Override
            public void messageArrived(String topic, org.eclipse.paho.client.mqttv3.MqttMessage message) {
                received.incrementAndGet();
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
            }
        });
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(false);
        client.connect(options);
        client.subscribe("shutdown/#", 1);
        return received;
    }

    private void startPublisher(String clientId, String topic) throws Exception {
        MqttClient client = new MqttClient(serverUri(), clientId, new MemoryPersistence());
        clients.add(client);
        client.setTimeToWait(TIMEOUT.toMillis());
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(true);
        client.connect(options);
        publishing.submit(() -> {
            byte[] payload = clientId.getBytes(StandardCharsets.UTF_8);
            try {
                while (publish) {
                    client.publish(topic, payload, 1, false);
                }
            } catch (Exception e) {
                // the broker closed the connection
            }
        });
    }

    private String serverUri() {
        return "tcp://localhost:" + mqttPort;
    }

}
