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
package org.thingsboard.mqtt.broker.lite.durability;

import lombok.extern.slf4j.Slf4j;
import org.awaitility.core.ConditionFactory;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The crash guarantee (D15): a broker killed with {@code SIGKILL} under QoS 1 and QoS 2 load loses no acknowledged
 * message for persistent DEVICE subscribers, and only messages in flight at a kill may reach them twice.
 * <p>
 * The broker runs in a JVM of its own ({@link LiteBrokerProcess}) on a store directory and a PostgreSQL container the
 * test owns, and is started again on the same store after each kill. Every client uses MQTT 3.1.1 with a persistent
 * session and keeps its Paho client across the kills, so a reconnect resends what was in flight.
 * <ul>
 * <li>A publisher counts an id as acknowledged when its publish completes: at PUBACK for QoS 1, and for QoS 2 at
 * PUBCOMP, which follows the broker's own acknowledgement, PUBREC. The ids it had sent and not yet seen acknowledged
 * when a kill happened are in flight at that kill.</li>
 * <li>A subscriber acknowledges each message by hand right after recording its id. Before each kill the online
 * subscriber stops acknowledging until it holds {@value #DELIVERY_WINDOW} messages, its ids in flight at that kill.
 * The broker hands a persisted DEVICE client at most one page ({@code store.device-msg.read-page-size}) of messages
 * that are not acknowledged, and frees a place only once it removed the acknowledged message from the store. So when
 * the subscriber holds a full page, every acknowledgement it sent earlier has been stored, and none is still on its
 * way to a broker about to be killed.</li>
 * </ul>
 * The offline subscriber's queue must hold every message of the run, and the store keeps at most 65,535 messages for
 * a client. So each publisher is capped at {@value #PUBLISH_RATE} publishes a second, which keeps the run near 30,000
 * messages: uncapped, the four of them publish tens of thousands a second.
 */
@Slf4j
@Timeout(value = 10, unit = TimeUnit.MINUTES)
class KillNineDurabilityLiteTest {

    private static final String TOPIC_PREFIX = "kill9/";
    private static final String TOPIC_FILTER = "kill9/#";
    private static final int KILLS = 3;
    private static final int ACKNOWLEDGED_PER_ROUND = 5_000;
    private static final int DELIVERY_WINDOW = 256;
    // the store takes no larger limit
    private static final int QUEUE_LIMIT = 65_535;
    private static final Map<String, String> BROKER_PROPERTIES = Map.of(
            "store.device-msg.read-page-size", String.valueOf(DELIVERY_WINDOW),
            "mqtt.persistent-session.device.persisted-messages.limit", String.valueOf(QUEUE_LIMIT));
    private static final int PUBLISH_WINDOW = 100;
    private static final int PUBLISH_RATE = 5_000;
    private static final long PUBLISH_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(1) / PUBLISH_RATE;
    private static final Duration BROKER_START_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final Duration RETRY_INTERVAL = Duration.ofMillis(200);
    // what a kill waits for is polled this often, so that few publishes are added after it happened
    private static final Duration PROMPT_POLL_INTERVAL = Duration.ofMillis(10);
    private static final Duration IDLE = Duration.ofSeconds(5);
    private static final Duration DRAIN_TIMEOUT = Duration.ofMinutes(3);
    private static final Pattern CORRUPTION = Pattern.compile("corrupt", Pattern.CASE_INSENSITIVE);

    private static LiteTestDatabase db;

    @TempDir
    Path tempDir;

    private final int mqttPort = LiteBrokerProcess.freePort();
    private final AtomicLong lastId = new AtomicLong();
    private final Set<Long> acknowledgedIds = ConcurrentHashMap.newKeySet();
    private final List<Publisher> publishers = new ArrayList<>();
    private final List<Subscriber> subscribers = new ArrayList<>();
    private LiteBrokerProcess broker;

    @BeforeAll
    static void startDatabase() {
        db = LiteTestDatabase.start();
    }

    @AfterAll
    static void stopDatabase() {
        if (db != null) {
            db.close();
        }
    }

    @AfterEach
    void stopEverything() throws InterruptedException {
        for (Publisher publisher : publishers) {
            publisher.stopPublishing();
        }
        if (broker != null && broker.isAlive()) {
            broker.kill9();
        }
        publishers.forEach(Client::closeQuietly);
        subscribers.forEach(Client::closeQuietly);
    }

    @Test
    void d15_noAcknowledgedMessageIsLostAcrossThreeKills() throws Exception {
        Path store = tempDir.resolve("store");
        startBroker(store);
        Subscriber online = subscriber("kill9-online");
        Subscriber offline = subscriber("kill9-offline");
        offline.disconnect();
        startPublishers("kill9-pub-");
        List<Client> onlineClients = new ArrayList<>(publishers);
        onlineClients.add(online);

        Set<Long> idsInFlightAtAKill = new HashSet<>();
        // a publish in flight at a kill may be stored again when it is sent again
        long publishesSentAgainAtMost = 0;
        for (int kill = 1; kill <= KILLS; kill++) {
            awaitAcknowledged(acknowledgedIds.size() + ACKNOWLEDGED_PER_ROUND);
            online.holdAcks();
            awaitPromptly("the online subscriber holding a full delivery window").until(() -> online.heldCount() >= DELIVERY_WINDOW);
            assertThat(online.heldCount()).as("messages the online subscriber holds, which the broker sent in one window")
                    .isEqualTo(DELIVERY_WINDOW);
            broker.kill9();
            await("every client disconnected").atMost(TIMEOUT).until(() -> onlineClients.stream().noneMatch(Client::isConnected));
            Set<Long> sentNotAcknowledged = unacknowledgedByPublishers();
            List<Long> held = online.releaseHeld();
            idsInFlightAtAKill.addAll(sentNotAcknowledged);
            idsInFlightAtAKill.addAll(held);
            publishesSentAgainAtMost += sentNotAcknowledged.size();
            log.info("Kill {}: {} ids acknowledged, {} sent and not acknowledged, {} held by the online subscriber",
                    kill, acknowledgedIds.size(), sentNotAcknowledged.size(), held.size());

            startBroker(store);
            connectAll(onlineClients);
        }
        // the last broker takes new publishes as well as the ones sent again
        awaitAcknowledged(acknowledgedIds.size() + ACKNOWLEDGED_PER_ROUND);
        for (Publisher publisher : publishers) {
            publisher.stopPublishing();
        }
        await("every publish acknowledged").atMost(TIMEOUT).until(() -> unacknowledgedByPublishers().isEmpty());
        connectAll(List.of(offline));
        await("both subscribers idle for " + IDLE).atMost(DRAIN_TIMEOUT).pollInterval(Duration.ofMillis(500))
                .until(() -> online.idleFor(IDLE) && offline.idleFor(IDLE));

        Set<Long> acknowledged = Set.copyOf(acknowledgedIds);
        Set<Long> onlineIds = online.receivedIds();
        Set<Long> offlineIds = offline.receivedIds();
        Set<Long> onlineDuplicates = online.duplicates();
        Set<Long> offlineDuplicates = offline.duplicates();
        log.info("{} ids published, {} acknowledged, {} in flight at a kill; the online subscriber received {} ids, {} of them twice "
                        + "or more; the offline subscriber received {} ids, {} of them twice or more", lastId.get(), acknowledged.size(),
                idsInFlightAtAKill.size(), onlineIds.size(), onlineDuplicates.size(), offlineIds.size(), offlineDuplicates.size());
        assertThat(lastId.get() + publishesSentAgainAtMost).as("messages the offline subscriber's queue had to hold, at most")
                .isLessThanOrEqualTo(QUEUE_LIMIT);
        for (Client client : onlineClients) {
            assertThat(client.connectionsLost.get()).as("connections %s lost", client.clientId()).isEqualTo(KILLS);
        }
        assertThat(offline.connectionsLost.get()).as("connections the offline subscriber lost").isZero();
        assertNoIds(difference(acknowledged, onlineIds), "acknowledged ids the online subscriber never received");
        assertNoIds(difference(acknowledged, offlineIds), "acknowledged ids the offline subscriber never received");
        // D15: only a message in flight at a kill may arrive twice
        assertNoIds(difference(onlineDuplicates, idsInFlightAtAKill), "ids the online subscriber received twice that were not in flight at a kill");
        assertNoIds(difference(offlineDuplicates, idsInFlightAtAKill), "ids the offline subscriber received twice that were not in flight at a kill");
    }

    @Test
    void theStoreOpensCleanlyAfterAKill() throws Exception {
        Path store = tempDir.resolve("store");
        startBroker(store);
        subscriber("kill9-store-sub");
        startPublishers("kill9-store-pub-");
        awaitAcknowledged(ACKNOWLEDGED_PER_ROUND);
        broker.kill9();

        startBroker(store);
        List<String> lines = Files.readAllLines(broker.logFile());
        assertThat(lines).as("the store opened by the restarted broker (%s)", broker.logFile())
                .anyMatch(line -> line.contains("Opened the store at " + store));
        assertThat(lines).as("lines of the restarted broker (%s) that report a corruption", broker.logFile())
                .noneMatch(line -> CORRUPTION.matcher(line).find());
    }

    private void startBroker(Path store) {
        broker = LiteBrokerProcess.start(store, db, mqttPort, BROKER_PROPERTIES);
        long startNanos = System.nanoTime();
        broker.awaitMqttPort(BROKER_START_TIMEOUT);
        log.info("The broker opened the MQTT port in {} ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
    }

    /**
     * Two publishers on QoS 1 and two on QoS 2, each on a topic of its own.
     */
    private void startPublishers(String clientIdPrefix) throws MqttException {
        int[] qosLevels = {1, 1, 2, 2};
        for (int i = 0; i < qosLevels.length; i++) {
            publishers.add(new Publisher(clientIdPrefix + i, qosLevels[i]));
        }
        connectAll(publishers);
        publishers.forEach(Publisher::start);
    }

    private Subscriber subscriber(String clientId) throws MqttException {
        Subscriber subscriber = new Subscriber(clientId, mqttPort);
        subscribers.add(subscriber);
        connectAll(List.of(subscriber));
        IMqttToken subscribed = subscriber.client.subscribe(TOPIC_FILTER, 2);
        subscribed.waitForCompletion(TIMEOUT.toMillis());
        assertThat(subscribed.getGrantedQos()).as("QoS granted to %s", clientId).containsExactly(2);
        return subscriber;
    }

    private void awaitAcknowledged(int count) {
        awaitPromptly(count + " ids acknowledged").until(() -> acknowledgedIds.size() >= count);
    }

    private static ConditionFactory awaitPromptly(String alias) {
        return await(alias).atMost(TIMEOUT).pollDelay(Duration.ZERO).pollInterval(PROMPT_POLL_INTERVAL);
    }

    private Set<Long> unacknowledgedByPublishers() {
        Set<Long> ids = new HashSet<>();
        publishers.forEach(publisher -> ids.addAll(publisher.unacknowledged));
        return ids;
    }

    /**
     * Connects the clients at the same time: a Paho connect takes about 300 ms, during which the publishers that are
     * already connected would publish alone.
     */
    private static void connectAll(List<? extends Client> clients) {
        await("clients connected").atMost(TIMEOUT).pollDelay(Duration.ZERO).pollInterval(RETRY_INTERVAL).ignoreExceptions()
                .until(() -> {
                    List<IMqttToken> connects = new ArrayList<>();
                    for (Client client : clients) {
                        if (!client.isConnected()) {
                            connects.add(client.client.connect(client.options));
                        }
                    }
                    for (IMqttToken connect : connects) {
                        connect.waitForCompletion(TIMEOUT.toMillis());
                    }
                    return clients.stream().allMatch(Client::isConnected);
                });
    }

    private static Set<Long> difference(Set<Long> ids, Set<Long> without) {
        Set<Long> difference = new HashSet<>(ids);
        difference.removeAll(without);
        return difference;
    }

    private static void assertNoIds(Set<Long> ids, String description) {
        assertThat(ids).withFailMessage("%s: %d, the first of them %s", description, ids.size(), ids.stream().sorted().limit(20).toList())
                .isEmpty();
    }

    private static byte[] payloadOf(long id) {
        return Long.toString(id).getBytes(StandardCharsets.US_ASCII);
    }

    private static long idOf(MqttMessage message) {
        return Long.parseLong(new String(message.getPayload(), StandardCharsets.US_ASCII));
    }

    /**
     * A Paho client with a persistent MQTT 3.1.1 session, kept across the kills.
     */
    private abstract static class Client implements MqttCallbackExtended {

        final MqttAsyncClient client;
        final MqttConnectOptions options = new MqttConnectOptions();
        final AtomicInteger connectionsLost = new AtomicInteger();

        Client(String clientId, int mqttPort) throws MqttException {
            client = new MqttAsyncClient(LiteBrokerProcess.mqttUri(mqttPort), clientId, new MemoryPersistence());
            client.setCallback(this);
            options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
            options.setCleanSession(false);
        }

        String clientId() {
            return client.getClientId();
        }

        boolean isConnected() {
            return client.isConnected();
        }

        void closeQuietly() {
            try {
                if (client.isConnected()) {
                    client.disconnectForcibly(1000);
                }
                client.close(true);
            } catch (MqttException ignored) {
                // the client is already gone; nothing is left to release
            }
        }

        @Override
        public void connectionLost(Throwable cause) {
            connectionsLost.incrementAndGet();
        }

        @Override
        public void connectComplete(boolean reconnect, String serverURI) {
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) {
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
        }
    }

    /**
     * Publishes ids as fast as the broker acknowledges them, with up to {@value #PUBLISH_WINDOW} publishes not
     * acknowledged, but no more than {@value #PUBLISH_RATE} a second.
     */
    private final class Publisher extends Client {

        private final int qos;
        private final Semaphore window = new Semaphore(PUBLISH_WINDOW);
        private final Set<Long> unacknowledged = ConcurrentHashMap.newKeySet();
        private final Thread thread;
        private volatile boolean publishing = true;

        private Publisher(String clientId, int qos) throws MqttException {
            super(clientId, mqttPort);
            this.qos = qos;
            options.setMaxInflight(PUBLISH_WINDOW);
            this.thread = new Thread(this::publish, clientId);
            thread.setDaemon(true);
        }

        private void start() {
            thread.start();
        }

        private void stopPublishing() throws InterruptedException {
            publishing = false;
            thread.join(TIMEOUT.toMillis());
        }

        private void publish() {
            IMqttActionListener onCompletion = new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken token) {
                    long id = (Long) token.getUserContext();
                    acknowledgedIds.add(id);
                    unacknowledged.remove(id);
                    window.release();
                }

                @Override
                public void onFailure(IMqttToken token, Throwable exception) {
                    // the connection is gone: the client keeps the publish and sends it again once it reconnects
                }
            };
            String topic = TOPIC_PREFIX + clientId();
            long nextPublishNanos = System.nanoTime();
            while (publishing) {
                try {
                    if (!window.tryAcquire(RETRY_INTERVAL.toMillis(), TimeUnit.MILLISECONDS)) {
                        continue;
                    }
                } catch (InterruptedException e) {
                    return;
                }
                if (!client.isConnected()) {
                    window.release();
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
                    continue;
                }
                long now = System.nanoTime();
                if (nextPublishNanos > now) {
                    LockSupport.parkNanos(nextPublishNanos - now);
                }
                // after a reconnect the publisher does not make up for the time it was disconnected
                nextPublishNanos = Math.max(nextPublishNanos, now) + PUBLISH_INTERVAL_NANOS;
                long id = lastId.incrementAndGet();
                unacknowledged.add(id);
                try {
                    client.publish(topic, payloadOf(id), qos, false, id, onCompletion);
                } catch (MqttException e) {
                    // refused before it was queued, so never sent: the connection dropped meanwhile
                    unacknowledged.remove(id);
                    window.release();
                }
            }
        }
    }

    /**
     * A persistent subscriber to every publisher's topic at QoS 2, so QoS 1 publishes reach it at QoS 1 and QoS 2
     * ones at QoS 2. Paho hands it a QoS 2 message at PUBREL, so a QoS 2 message reaches it twice only when the
     * broker sends PUBREL again for a message whose PUBCOMP it never got.
     */
    private static final class Subscriber extends Client {

        // all guarded by this
        private final Map<Long, Integer> receivedCounts = new HashMap<>();
        private final List<Long> held = new ArrayList<>();
        private boolean holdAcks;
        private long lastActivityNanos = System.nanoTime();

        private Subscriber(String clientId, int mqttPort) throws MqttException {
            super(clientId, mqttPort);
            client.setManualAcks(true);
        }

        private void disconnect() throws MqttException {
            client.disconnect().waitForCompletion(TIMEOUT.toMillis());
        }

        @Override
        public synchronized void connectComplete(boolean reconnect, String serverURI) {
            lastActivityNanos = System.nanoTime();
        }

        @Override
        public synchronized void messageArrived(String topic, MqttMessage message) {
            long id = idOf(message);
            receivedCounts.merge(id, 1, Integer::sum);
            lastActivityNanos = System.nanoTime();
            if (holdAcks) {
                held.add(id);
                return;
            }
            try {
                client.messageArrivedComplete(message.getId(), message.getQos());
            } catch (MqttException e) {
                // the acknowledgement never left: the message stays received and not acknowledged
                held.add(id);
            }
        }

        /**
         * From now on, the messages that arrive are recorded and not acknowledged.
         */
        private synchronized void holdAcks() {
            holdAcks = true;
        }

        private synchronized int heldCount() {
            return held.size();
        }

        /**
         * Called once the connection is gone: the broker sends the held messages again on the next connection, where
         * they are acknowledged as they arrive.
         *
         * @return the ids of the messages that were received and not acknowledged
         */
        private synchronized List<Long> releaseHeld() {
            List<Long> ids = List.copyOf(held);
            held.clear();
            holdAcks = false;
            return ids;
        }

        /**
         * @return whether nothing arrived for this long, nor since the client last connected
         */
        private synchronized boolean idleFor(Duration idle) {
            return System.nanoTime() - lastActivityNanos >= idle.toNanos();
        }

        private synchronized Set<Long> receivedIds() {
            return Set.copyOf(receivedCounts.keySet());
        }

        private synchronized Set<Long> duplicates() {
            Set<Long> ids = new HashSet<>();
            receivedCounts.forEach((id, count) -> {
                if (count > 1) {
                    ids.add(id);
                }
            });
            return ids;
        }
    }

}
