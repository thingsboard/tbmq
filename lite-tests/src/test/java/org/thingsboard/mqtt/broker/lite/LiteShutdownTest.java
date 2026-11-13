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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.mqttv5.client.IMqttMessageListener;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttActionListener;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
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
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.slf4j.LoggerFactory;
import org.thingsboard.mqtt.broker.ThingsboardMqttBrokerApplication;
import org.thingsboard.mqtt.broker.actors.client.service.handlers.MqttPublishHandler;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.service.mqtt.will.DefaultLastWillService;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Closes a Lite context while clients are connected and publishing.
 * <p>
 * Each publisher keeps a window of QoS 1 publishes in flight, and one more client sends QoS 0 publishes as fast as it
 * can, so publishes are still on their way to the pipeline when it drains. Those must stay unanswered: no PUBACK with
 * an error code, and no WARN from the publish handler.
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
    private static final int PUBLISH_WINDOW = 50;
    // QoS 0 publishes the firehose sends before the context is closed, and how often it waits for its socket writes
    private static final long FIREHOSE_MSGS_BEFORE_CLOSE = 20_000;
    private static final int FIREHOSE_BATCH = 100;
    private static final String WILL_TOPIC = "shutdown-will/gone";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final long MAX_CLOSE_MS = 8000;

    @ClassRule
    public static TempStorePathRule store = new TempStorePathRule();

    @Autowired
    private ConfigurableApplicationContext context;

    @Autowired
    private MeterRegistry meterRegistry;

    @Value("${listener.tcp.bind_port}")
    private int mqttPort;

    private final List<Object> clients = new ArrayList<>();
    private final ExecutorService publishing = Executors.newFixedThreadPool(PUBLISHERS + 1);
    private volatile boolean publish = true;

    // PUBACK reason codes of 0x80 or above the publishers got, and how each publisher's connection ended
    private final List<String> errorAcks = new CopyOnWriteArrayList<>();
    private final List<String> publisherDisconnects = new CopyOnWriteArrayList<>();

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
            receivedBySubscriber.add(cleanSubscriber("shutdown-sub-" + i, "shutdown/#"));
        }
        for (int i = 0; i < PERSISTENT_SUBSCRIBERS; i++) {
            receivedBySubscriber.add(persistentSubscriber("shutdown-persistent-sub-" + i));
        }
        AtomicInteger willsReceived = cleanSubscriber("shutdown-will-sub", WILL_TOPIC);
        clientWithWill("shutdown-will-client");
        for (int i = 0; i < PUBLISHERS; i++) {
            startPublisher("shutdown-pub-" + i, "shutdown/" + i);
        }
        await().atMost(TIMEOUT).until(() -> receivedBySubscriber.stream().allMatch(received -> received.get() > 0));
        // no one subscribes to its topic: its publishes cost the broker little, and a backlog of them waits for the actor
        AtomicLong firehoseSent = startFirehose("shutdown-firehose", "shutdown-firehose/unsubscribed");
        await().atMost(TIMEOUT).until(() -> firehoseSent.get() >= FIREHOSE_MSGS_BEFORE_CLOSE);

        ListAppender<ILoggingEvent> publishHandlerLog = capture(MqttPublishHandler.class, null);
        // TRACE shows whether the will was removed, scheduled, acknowledged or failed
        ListAppender<ILoggingEvent> lastWillLog = capture(DefaultLastWillService.class, Level.TRACE);
        Counter droppedMsgs = meterRegistry.find(BrokerConstants.DROPPED_MSGS).counter();
        double droppedBefore = droppedMsgs == null ? 0 : droppedMsgs.count();
        long elapsedMs;
        try {
            long t0 = System.nanoTime();
            context.close();
            elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
        } finally {
            release(MqttPublishHandler.class, publishHandlerLog);
            release(DefaultLastWillService.class, lastWillLog);
        }

        log.info("context.close() with {} connected clients, {} of them publishing, took {} ms",
                CLEAN_SUBSCRIBERS + PERSISTENT_SUBSCRIBERS + PUBLISHERS + 3, PUBLISHERS + 1, elapsedMs);
        log.info("Publisher connections ended: {}", publisherDisconnects);
        log.info("Last Will at shutdown: received by its subscriber {} time(s); droppedMsgs grew by {} during close; "
                        + "last will service log: {}", willsReceived.get(),
                droppedMsgs == null ? "n/a" : droppedMsgs.count() - droppedBefore, describe(lastWillLog));
        assertThat(elapsedMs).as("context.close() in ms").isLessThan(MAX_CLOSE_MS);
        assertThat(errorAcks).as("PUBACKs with a reason code of 0x80 or above").isEmpty();
        assertThat(publishHandlerLog.list).as("WARN or ERROR from the publish handler during close")
                .noneMatch(event -> event.getLevel().isGreaterOrEqual(Level.WARN));
    }

    private AtomicInteger cleanSubscriber(String clientId, String topicFilter) throws Exception {
        AtomicInteger received = new AtomicInteger();
        MqttClient client = new MqttClient(serverUri(), clientId, new MemoryPersistence());
        clients.add(client);
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(true);
        client.connect(options);
        client.subscribe(new MqttSubscription[]{new MqttSubscription(topicFilter, 1)},
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

    private void clientWithWill(String clientId) throws Exception {
        MqttClient client = new MqttClient(serverUri(), clientId, new MemoryPersistence());
        clients.add(client);
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(true);
        options.setWill(WILL_TOPIC, new MqttMessage(clientId.getBytes(StandardCharsets.UTF_8), 1, false, new MqttProperties()));
        client.connect(options);
    }

    /**
     * Publishes QoS 1 messages with up to {@value #PUBLISH_WINDOW} of them in flight, and records every PUBACK that
     * carries a reason code of 0x80 or above.
     */
    private void startPublisher(String clientId, String topic) throws Exception {
        MqttAsyncClient client = new MqttAsyncClient(serverUri(), clientId, new MemoryPersistence());
        clients.add(client);
        client.setCallback(new PublisherCallback(clientId));
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(true);
        client.connect(options).waitForCompletion(TIMEOUT.toMillis());
        Semaphore window = new Semaphore(PUBLISH_WINDOW);
        MqttActionListener onAck = new MqttActionListener() {
            @Override
            public void onSuccess(IMqttToken token) {
                int[] reasonCodes = token.getReasonCodes();
                if (reasonCodes != null) {
                    for (int reasonCode : reasonCodes) {
                        if (reasonCode >= 0x80) {
                            errorAcks.add(clientId + ": 0x" + Integer.toHexString(reasonCode));
                        }
                    }
                }
                window.release();
            }

            @Override
            public void onFailure(IMqttToken token, Throwable exception) {
                window.release();
            }
        };
        publishing.submit(() -> {
            byte[] payload = clientId.getBytes(StandardCharsets.UTF_8);
            try {
                while (publish) {
                    // an unanswered publish keeps its permit: once the broker is gone, the window stays full
                    if (!window.tryAcquire(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                        return;
                    }
                    client.publish(topic, payload, 1, false, null, onAck);
                }
            } catch (Exception e) {
                // the broker closed the connection
            }
        });
    }

    /**
     * Sends QoS 0 publishes as fast as the client can write them, waiting for the socket write of every
     * {@value #FIREHOSE_BATCH}th one so the client's own buffer stays small.
     */
    private AtomicLong startFirehose(String clientId, String topic) throws Exception {
        AtomicLong sent = new AtomicLong();
        MqttAsyncClient client = new MqttAsyncClient(serverUri(), clientId, new MemoryPersistence());
        clients.add(client);
        client.setCallback(new PublisherCallback(clientId));
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(true);
        client.connect(options).waitForCompletion(TIMEOUT.toMillis());
        publishing.submit(() -> {
            byte[] payload = clientId.getBytes(StandardCharsets.UTF_8);
            try {
                while (publish) {
                    IMqttToken token = client.publish(topic, payload, 0, false);
                    if (sent.incrementAndGet() % FIREHOSE_BATCH == 0) {
                        token.waitForCompletion(TIMEOUT.toMillis());
                    }
                }
            } catch (Exception e) {
                // the broker closed the connection
            }
        });
        return sent;
    }

    private ListAppender<ILoggingEvent> capture(Class<?> loggerClass, Level level) {
        Logger logger = (Logger) LoggerFactory.getLogger(loggerClass);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        if (level != null) {
            logger.setLevel(level);
            // the extra lines go to the report below, not to the console
            logger.setAdditive(false);
        }
        return appender;
    }

    private static void release(Class<?> loggerClass, ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger(loggerClass);
        logger.detachAppender(appender);
        logger.setLevel(null);
        logger.setAdditive(true);
        appender.stop();
    }

    private static List<String> describe(ListAppender<ILoggingEvent> appender) {
        return appender.list.stream()
                .map(event -> event.getLevel() + " " + event.getFormattedMessage()
                        + (event.getThrowableProxy() == null ? "" : " (" + event.getThrowableProxy().getClassName()
                        + ": " + event.getThrowableProxy().getMessage() + ", "
                        + event.getThrowableProxy().getStackTraceElementProxyArray().length + " stack frames)"))
                .toList();
    }

    private final class PublisherCallback implements org.eclipse.paho.mqttv5.client.MqttCallback {

        private final String clientId;

        private PublisherCallback(String clientId) {
            this.clientId = clientId;
        }

        @Override
        public void disconnected(MqttDisconnectResponse disconnectResponse) {
            publisherDisconnects.add(clientId + ": 0x" + Integer.toHexString(disconnectResponse.getReturnCode())
                    + (disconnectResponse.getException() == null ? "" : " " + disconnectResponse.getException().getMessage()));
        }

        @Override
        public void mqttErrorOccurred(MqttException exception) {
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) {
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

    private String serverUri() {
        return "tcp://localhost:" + mqttPort;
    }

}
