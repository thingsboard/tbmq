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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.core.ConditionTimeoutException;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttActionListener;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClientException;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.thingsboard.mqtt.broker.ThingsboardMqttBrokerApplication;
import org.thingsboard.mqtt.broker.actors.client.service.handlers.MqttPublishHandler;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.stats.StatsType;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgQueueStore;
import org.thingsboard.mqtt.broker.standalone.pipeline.PipelineBudget;
import org.thingsboard.mqtt.broker.standalone.pipeline.StandaloneDeviceMsgQueueConsumer;
import org.thingsboard.mqtt.broker.standalone.pipeline.StandalonePublishMsgQueue;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;

/**
 * Overloads the publish pipeline of a Lite broker and checks what its back-pressure promises: no QoS 1 publish is
 * acknowledged and then lost, a full pipeline refuses QoS 1 with Quota exceeded (0x97) and counts every QoS 0 publish
 * it drops, the pipeline and the heap stay bounded, and the broker resumes the connections it paused without closing
 * any of them for inactivity meanwhile.
 * <p>
 * The pipeline holds at most {@value #MAX_MESSAGES} publishes and every append to the store takes 2 ms longer, so the
 * persist worker of the one persistent subscriber stores at most 8 copies per 2 ms: far fewer than twenty QoS 1
 * publishers and a QoS 0 firehose send.
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ThingsboardMqttBrokerApplication.class, properties = {
        "standalone.pipeline.max-messages=" + OverloadLiteTest.MAX_MESSAGES,
        "standalone.persist.batch-size=8",
        "mqtt.keep-alive.monitoring-delay-ms=200"
})
@ActiveProfiles("test")
@DaoSqlTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class OverloadLiteTest {

    static final int MAX_MESSAGES = 200;
    private static final long APPEND_DELAY_MS = 2;
    private static final int QOS1_PUBLISHERS = 20;
    private static final int QOS1_MSGS_PER_PUBLISHER = 2_000;
    // A QoS 1 publisher sends its next publish as soon as one of its last 10 is answered. With the broker's whole
    // Receive Maximum (1000) unanswered, refusals answer nearly every publish and the load is over in about a second,
    // before the keep-alive could close any connection (1.5 x 2 s); with 10, the pipeline sets the pace and the load
    // lasts several keep-alive periods.
    private static final int QOS1_WINDOW = 10;
    private static final int QOS0_MSGS = 20_000;
    // the QoS 0 publisher waits for every so many socket writes, so that its client's buffer stays small
    private static final int QOS0_WRITE_BATCH = 100;
    private static final int KEEP_ALIVE_SECONDS = 2;
    private static final long SESSION_EXPIRY_SECONDS = 3600;
    private static final long HEAP_BOUND_BYTES = 256L << 20;
    private static final Duration DEPTH_SAMPLE_INTERVAL = Duration.ofMillis(50);
    private static final Duration HEAP_SAMPLE_INTERVAL = Duration.ofSeconds(1);
    private static final int FIRST_ERROR_CODE = 0x80;
    private static final int QUOTA_EXCEEDED = 0x97;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration LOAD_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(60);
    private static final int MAX_MARKERS = 10;
    private static final Duration MARKER_TIMEOUT = Duration.ofSeconds(2);
    // the meter StandalonePublishBackpressure registers; only reported
    private static final String PAUSED_CONNECTIONS_GAUGE = "pausedConnections";

    private static final String SUBSCRIPTION = "overload/#";
    private static final String QOS1_TOPIC_PREFIX = "overload/qos1/";
    private static final String QOS0_TOPIC = "overload/qos0";
    // every QoS 0 id, a marker's too, starts with it
    private static final String QOS0_ID_PREFIX = "q0-";
    private static final String MARKER_ID_PREFIX = QOS0_ID_PREFIX + "marker-";

    @ClassRule
    public static TempStorePathRule store = new TempStorePathRule();

    @MockitoSpyBean
    private DeviceMsgQueueStore deviceMsgQueueStore;
    @Autowired
    private PipelineBudget budget;
    @Autowired
    private StandalonePublishMsgQueue dispatchQueue;
    @Autowired
    private StandaloneDeviceMsgQueueConsumer persistStage;
    @Autowired
    private MqttPublishHandler publishHandler;
    @Autowired
    private MeterRegistry meterRegistry;

    @Value("${listener.tcp.bind_port}")
    private int mqttPort;

    private final List<MqttAsyncClient> clients = new ArrayList<>();
    private final ExecutorService publishing = Executors.newFixedThreadPool(QOS1_PUBLISHERS + 1);
    private final ScheduledExecutorService samplers = Executors.newScheduledThreadPool(2);

    // what the subscriber received; QoS 0 ids start with QOS0_ID_PREFIX
    private final Set<String> subscriberReceivedIds = ConcurrentHashMap.newKeySet();
    // what the publishers got back: a PUBACK with a success code, one with an error code, or no PUBACK at all
    private final Set<String> successfullyAcknowledgedIds = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> refusals = new ConcurrentHashMap<>();
    private final List<String> publishFailures = new CopyOnWriteArrayList<>();
    // the connections of the publishers that ended: closed by the broker, or given up by the client
    private final List<String> brokerDisconnects = new CopyOnWriteArrayList<>();
    private final List<String> clientDisconnects = new CopyOnWriteArrayList<>();
    private final List<String> subscriberDisconnects = new CopyOnWriteArrayList<>();
    // the QoS 0 publishes handed to the client, the markers too
    private final AtomicLong qos0Published = new AtomicLong();

    private final LongAccumulator peakQueued = new LongAccumulator(Math::max, 0);
    private final LongAccumulator peakDispatchQueued = new LongAccumulator(Math::max, 0);
    private final LongAccumulator peakPersistQueued = new LongAccumulator(Math::max, 0);
    private final LongAccumulator peakPausedConnections = new LongAccumulator(Math::max, 0);
    private final LongAccumulator peakHeap = new LongAccumulator(Math::max, 0);
    private final AtomicInteger heapSamples = new AtomicInteger();

    private long peakQueuedMessages;
    private long baselineHeapBytes;
    private long peakHeapUsedAfterGcBytes;
    private double publishPausesCounterDelta;
    private double droppedMsgsCounterDelta;
    private int publishersDisconnectedByBroker;
    private boolean allPublishersFinished;
    private long qos0Sent;
    private long qos0Received;

    @After
    public void stopClients() throws InterruptedException {
        samplers.shutdownNow();
        publishing.shutdownNow();
        samplers.awaitTermination(CONNECT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        publishing.awaitTermination(CONNECT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        // closing a client takes about a second, as Paho waits out its disconnect timeout: they are closed together
        clients.parallelStream().forEach(LiteTestClients::closeQuietly);
        clients.clear();
    }

    @Test
    public void underOverloadNothingAcknowledgedIsLostAndMemoryStaysBounded() throws Exception {
        runLoad();

        Set<String> refusedIds = refusals.keySet();
        // keep-alive did not fire on paused connections, and paused connections resumed: checked first, as the load
        // stops early when a connection is lost, and the checks below then fail for that reason only
        assertThat(publishersDisconnectedByBroker).as("publisher connections the broker closed: %s", brokerDisconnects)
                .isZero();
        assertThat(allPublishersFinished).as("all publishers finished; failed publishes %s, connections given up by "
                + "the client %s", publishFailures, clientDisconnects).isTrue();
        // nothing acknowledged and then dropped
        assertThat(notReceived(successfullyAcknowledgedIds)).as("acknowledged ids the subscriber did not receive")
                .isEmpty();
        assertThat(refusedIds).as("ids refused with an error code")
                .allSatisfy(id -> assertThat(refusalCode(id)).as(id).isEqualTo(QUOTA_EXCEEDED));
        // sampled from PipelineBudget every 50 ms
        assertThat(peakQueuedMessages).as("peak of the publishes in the pipeline").isLessThanOrEqualTo(MAX_MESSAGES);
        assertThat(peakHeapUsedAfterGcBytes - baselineHeapBytes).as("heap growth after GC, in bytes")
                .isLessThan(HEAP_BOUND_BYTES);
        assertThat(publishPausesCounterDelta).as("publishPauses counter delta").isGreaterThan(0);
        // every QoS 0 drop is counted, and so is every refused QoS 1 publish: MqttPublishHandler counts it as dropped,
        // as in the cluster edition
        assertThat(droppedMsgsCounterDelta).as("droppedMsgs counter delta")
                .isEqualTo((double) (qos0Sent - qos0Received + refusedIds.size()));
    }

    private void runLoad() throws Exception {
        long setupStart = System.nanoTime();
        slowDownStoreAppends();
        connectSubscriber();
        List<MqttAsyncClient> qos1Publishers = new ArrayList<>();
        for (int i = 0; i < QOS1_PUBLISHERS; i++) {
            qos1Publishers.add(publisher(publisherId(i)));
        }
        MqttAsyncClient qos0Publisher = publisher("overload-qos0-pub");
        connectAll(qos1Publishers, qos0Publisher);

        Counter droppedMsgs = counter(BrokerConstants.DROPPED_MSGS);
        Counter publishPauses = counter(StatsType.PUBLISH_PAUSES.getPrintName());
        double droppedBefore = droppedMsgs.count();
        double pausesBefore = publishPauses.count();
        baselineHeapBytes = heapUsedAfterGc();
        samplers.scheduleAtFixedRate(this::sampleDepth, 0, DEPTH_SAMPLE_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
        samplers.scheduleWithFixedDelay(this::sampleHeap, HEAP_SAMPLE_INTERVAL.toMillis(),
                HEAP_SAMPLE_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);

        long loadStart = System.nanoTime();
        allPublishersFinished = publishAll(qos1Publishers, qos0Publisher);
        long loadEnd = System.nanoTime();
        sampleHeap();
        long markersSent = allPublishersFinished ? settle(qos0Publisher) : 0;
        long settleEnd = System.nanoTime();

        samplers.shutdownNow();
        assertThat(samplers.awaitTermination(CONNECT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).as("samplers stopped")
                .isTrue();
        peakQueuedMessages = peakQueued.get();
        peakHeapUsedAfterGcBytes = peakHeap.get();
        publishPausesCounterDelta = publishPauses.count() - pausesBefore;
        droppedMsgsCounterDelta = droppedMsgs.count() - droppedBefore;
        publishersDisconnectedByBroker = brokerDisconnects.size();
        qos0Sent = qos0Published.get();
        qos0Received = subscriberReceivedIds.stream().filter(id -> id.startsWith(QOS0_ID_PREFIX)).count();
        logMeasurements(loadStart - setupStart, loadEnd - loadStart, settleEnd - loadEnd, markersSent);
    }

    /**
     * Starts every publisher at once and waits until all of them are done, or until a connection is lost: a closed
     * connection leaves its publisher waiting for answers that never come.
     *
     * @return {@code true} when every publisher sent all its publishes and got every QoS 1 one answered
     */
    private boolean publishAll(List<MqttAsyncClient> qos1Publishers, MqttAsyncClient qos0Publisher) {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> runs = new ArrayList<>();
        for (int i = 0; i < QOS1_PUBLISHERS; i++) {
            MqttAsyncClient publisher = qos1Publishers.get(i);
            String topic = QOS1_TOPIC_PREFIX + i;
            String idPrefix = publisherId(i) + "-";
            runs.add(publishing.submit(() -> publishQos1(publisher, topic, idPrefix, start)));
        }
        runs.add(publishing.submit(() -> publishQos0(qos0Publisher, start)));
        start.countDown();
        waitUntil(() -> runs.stream().allMatch(Future::isDone) || !brokerDisconnects.isEmpty()
                || !clientDisconnects.isEmpty(), LOAD_TIMEOUT);
        // every run is looked at, so that each failure is recorded
        return runs.stream().map(this::finished).reduce(true, Boolean::logicalAnd) && publishFailures.isEmpty();
    }

    /**
     * Waits until the broker is done with the load: the pipeline is empty, every QoS 0 publish arrived or was dropped,
     * every acknowledged publish reached the subscriber, and every drop was counted.
     *
     * @return the number of QoS 0 markers sent
     */
    private long settle(MqttAsyncClient qos0Publisher) throws Exception {
        // the pipeline empties and every connection is read again
        waitUntil(() -> budget.queuedMessages() == 0, DRAIN_TIMEOUT);
        long markersSent = sendMarkersUntilTheLastArrives(qos0Publisher);
        waitUntil(() -> notReceived(successfullyAcknowledgedIds).isEmpty(), DRAIN_TIMEOUT);
        awaitPublishHandlerCallbacks();
        return markersSent;
    }

    private void logMeasurements(long setupNanos, long loadNanos, long settleNanos, long markersSent) {
        log.info("Overload: connecting and the baseline took {} ms, the load {} ms, settling {} ms",
                TimeUnit.NANOSECONDS.toMillis(setupNanos), TimeUnit.NANOSECONDS.toMillis(loadNanos),
                TimeUnit.NANOSECONDS.toMillis(settleNanos));
        log.info("Overload: QoS 1 acknowledged {}, refused {}, failed {}; QoS 0 sent {} ({} markers), received {}; "
                        + "droppedMsgs +{}, publishPauses +{}", successfullyAcknowledgedIds.size(), refusals.size(),
                publishFailures.size(), qos0Sent, markersSent, qos0Received, droppedMsgsCounterDelta,
                publishPausesCounterDelta);
        log.info("Overload: peak queued {} (dispatch {}, persist {}), peak paused connections {}; heap after GC {} MiB "
                        + "at baseline, {} MiB at peak over {} samples", peakQueuedMessages, peakDispatchQueued.get(),
                peakPersistQueued.get(), peakPausedConnections.get(), baselineHeapBytes >> 20,
                peakHeapUsedAfterGcBytes >> 20, heapSamples.get());
        log.info("Overload: publisher connections closed by the broker {}, given up by the clients {}; subscriber "
                + "connection lost {}", brokerDisconnects, clientDisconnects, subscriberDisconnects);
    }

    /**
     * Every append to the store, the one write of a persist batch, takes {@value #APPEND_DELAY_MS} ms longer.
     */
    private void slowDownStoreAppends() {
        doAnswer(invocation -> {
            try {
                Thread.sleep(APPEND_DELAY_MS);
            } catch (InterruptedException e) {
                // the shutdown stops the persist workers: the append goes on without the delay
                Thread.currentThread().interrupt();
            }
            return invocation.callRealMethod();
        }).when(deviceMsgQueueStore).append(anyList());
    }

    private void connectSubscriber() throws MqttException {
        MqttAsyncClient subscriber = new MqttAsyncClient(serverUri(), "overload-sub", new MemoryPersistence());
        clients.add(subscriber);
        subscriber.setCallback(new Callback() {
            @Override
            public void messageArrived(String topic, MqttMessage message) {
                subscriberReceivedIds.add(new String(message.getPayload(), StandardCharsets.UTF_8));
            }

            @Override
            public void disconnected(MqttDisconnectResponse response) {
                subscriberDisconnects.add(String.valueOf(response));
            }
        });
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(false);
        options.setSessionExpiryInterval(SESSION_EXPIRY_SECONDS);
        subscriber.connect(options).waitForCompletion(CONNECT_TIMEOUT.toMillis());
        subscriber.subscribe(new MqttSubscription(SUBSCRIPTION, 1)).waitForCompletion(CONNECT_TIMEOUT.toMillis());
    }

    private MqttAsyncClient publisher(String clientId) throws MqttException {
        MqttAsyncClient publisher = new MqttAsyncClient(serverUri(), clientId, new MemoryPersistence());
        clients.add(publisher);
        publisher.setCallback(new Callback() {
            @Override
            public void disconnected(MqttDisconnectResponse response) {
                recordDisconnect(clientId, response);
            }
        });
        return publisher;
    }

    /**
     * Connects the publishers together: a Paho client takes some 300 ms to connect, as it starts its threads.
     */
    private void connectAll(List<MqttAsyncClient> qos1Publishers, MqttAsyncClient qos0Publisher) throws Exception {
        List<IMqttToken> connects = new ArrayList<>();
        for (MqttAsyncClient publisher : qos1Publishers) {
            connects.add(publisher.connect(publisherOptions()));
        }
        connects.add(qos0Publisher.connect(publisherOptions()));
        for (IMqttToken connect : connects) {
            connect.waitForCompletion(CONNECT_TIMEOUT.toMillis());
        }
    }

    private static MqttConnectionOptions publisherOptions() {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(true);
        options.setKeepAliveInterval(KEEP_ALIVE_SECONDS);
        return options;
    }

    /**
     * Publishes {@value #QOS1_MSGS_PER_PUBLISHER} QoS 1 messages, each as soon as fewer than {@value #QOS1_WINDOW} are
     * unanswered, and records how each one was answered.
     *
     * @return {@code true} once every publish was answered, with a PUBACK or a failure
     */
    private boolean publishQos1(MqttAsyncClient publisher, String topic, String idPrefix, CountDownLatch start)
            throws Exception {
        Semaphore unanswered = new Semaphore(QOS1_WINDOW);
        CountDownLatch answered = new CountDownLatch(QOS1_MSGS_PER_PUBLISHER);
        MqttActionListener onAnswer = new MqttActionListener() {
            @Override
            public void onSuccess(IMqttToken token) {
                String id = (String) token.getUserContext();
                int[] reasonCodes = token.getReasonCodes();
                int reasonCode = reasonCodes == null || reasonCodes.length == 0 ? 0 : reasonCodes[0];
                if (reasonCode < FIRST_ERROR_CODE) {
                    successfullyAcknowledgedIds.add(id);
                } else {
                    refusals.put(id, reasonCode);
                }
                answered.countDown();
                unanswered.release();
            }

            @Override
            public void onFailure(IMqttToken token, Throwable exception) {
                publishFailures.add(token.getUserContext() + ": " + exception);
                answered.countDown();
                unanswered.release();
            }
        };
        start.await();
        for (int i = 0; i < QOS1_MSGS_PER_PUBLISHER; i++) {
            if (!unanswered.tryAcquire(LOAD_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                return false;
            }
            String id = idPrefix + i;
            publisher.publish(topic, payload(id), 1, false, id, onAnswer);
        }
        return answered.await(LOAD_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Publishes {@value #QOS0_MSGS} QoS 0 messages as fast as the client writes them.
     *
     * @return {@code true} once every publish was written to the socket
     */
    private boolean publishQos0(MqttAsyncClient publisher, CountDownLatch start) throws Exception {
        start.await();
        IMqttToken last = null;
        for (int i = 0; i < QOS0_MSGS; i++) {
            last = publisher.publish(QOS0_TOPIC, payload(QOS0_ID_PREFIX + i), 0, false);
            qos0Published.incrementAndGet();
            if ((i + 1) % QOS0_WRITE_BATCH == 0) {
                last.waitForCompletion(LOAD_TIMEOUT.toMillis());
            }
        }
        // the client writes in order: once the last publish is written, all of them are
        last.waitForCompletion(LOAD_TIMEOUT.toMillis());
        return last.isComplete() && last.getException() == null;
    }

    /**
     * The barrier for the QoS 0 count. The broker dispatches the publishes of one topic in order and delivers each of
     * them on the subscriber's connection, or drops and counts it, before the next one; so once a marker sent after the
     * QoS 0 load arrives, every earlier QoS 0 publish has arrived or was dropped. A marker can be dropped too, so a new
     * one goes out until the last one sent arrives. The markers count as QoS 0 publishes, sent and received.
     *
     * @return the number of markers sent
     */
    private long sendMarkersUntilTheLastArrives(MqttAsyncClient qos0Publisher) throws MqttException {
        for (int i = 0; i < MAX_MARKERS; i++) {
            String marker = MARKER_ID_PREFIX + i;
            qos0Publisher.publish(QOS0_TOPIC, payload(marker), 0, false).waitForCompletion(CONNECT_TIMEOUT.toMillis());
            qos0Published.incrementAndGet();
            if (waitUntil(() -> subscriberReceivedIds.contains(marker), MARKER_TIMEOUT)) {
                return i + 1;
            }
        }
        throw new AssertionError("None of " + MAX_MARKERS + " QoS 0 markers reached the subscriber");
    }

    /**
     * Waits until the callback executor of {@link MqttPublishHandler} ran every task submitted to it so far. The drop
     * of a refused QoS 0 publish is counted there, after the refusal, and no packet to a client follows it. The
     * executor is a fixed pool, so the test puts a task of its own on each of its threads: once they all run at the
     * same time, every task submitted before them has finished.
     */
    private void awaitPublishHandlerCallbacks() throws Exception {
        ThreadPoolExecutor executor =
                (ThreadPoolExecutor) ReflectionTestUtils.getField(publishHandler, "callbackProcessor");
        assertThat(executor).as("MqttPublishHandler.callbackProcessor").isNotNull();
        int threads = executor.getMaximumPoolSize();
        CountDownLatch allRunning = new CountDownLatch(threads);
        List<Future<Boolean>> barriers = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            barriers.add(executor.submit(() -> {
                allRunning.countDown();
                return allRunning.await(CONNECT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            }));
        }
        for (Future<Boolean> barrier : barriers) {
            assertThat(barrier.get(CONNECT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).as("all callback threads taken")
                    .isTrue();
        }
    }

    private void sampleDepth() {
        peakQueued.accumulate(budget.queuedMessages());
        peakDispatchQueued.accumulate(dispatchQueue.workers().queued());
        peakPersistQueued.accumulate(persistStage.workers().queued());
        Gauge pausedConnections = meterRegistry.find(PAUSED_CONNECTIONS_GAUGE).gauge();
        if (pausedConnections != null) {
            peakPausedConnections.accumulate((long) pausedConnections.value());
        }
        // a spy records every call for verification; those records are not what the broker holds on to
        clearInvocations(deviceMsgQueueStore);
    }

    private void sampleHeap() {
        peakHeap.accumulate(heapUsedAfterGc());
        heapSamples.incrementAndGet();
    }

    /**
     * The heap used right after a full collection. System.gc() runs one and returns when it is done (the surefire
     * argLine sets neither -XX:+ExplicitGCInvokesConcurrent nor -XX:+DisableExplicitGC), and that collection sets the
     * collection usage of every heap pool. Unlike the usage after a young collection, it leaves out the garbage that a
     * young collection promoted and that no old collection has taken yet, so two measurements differ only by what
     * stayed reachable. The baseline and every sample are taken this way.
     */
    private static long heapUsedAfterGc() {
        System.gc();
        long used = 0;
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == MemoryType.HEAP) {
                MemoryUsage afterGc = pool.getCollectionUsage();
                if (afterGc != null) {
                    used += afterGc.getUsed();
                }
            }
        }
        return used;
    }

    private void recordDisconnect(String clientId, MqttDisconnectResponse response) {
        MqttException exception = response.getException();
        if (exception == null) {
            // the broker sent a DISCONNECT
            brokerDisconnects.add(clientId + ": DISCONNECT 0x" + Integer.toHexString(response.getReturnCode())
                    + " " + response.getReasonString());
        } else if (exception.getReasonCode() == MqttClientException.REASON_CODE_CONNECTION_LOST
                || exception.getReasonCode() == MqttClientException.REASON_CODE_SERVER_DISCONNECTED) {
            // the broker closed the socket
            brokerDisconnects.add(clientId + ": " + exception.getReasonCode() + " " + exception.getMessage());
        } else {
            // the client gave up the connection itself, e.g. no PINGRESP in time
            clientDisconnects.add(clientId + ": " + exception.getReasonCode() + " " + exception.getMessage());
        }
    }

    private Set<String> notReceived(Collection<String> ids) {
        return ids.stream().filter(id -> !subscriberReceivedIds.contains(id)).collect(Collectors.toSet());
    }

    private int refusalCode(String id) {
        return refusals.get(id);
    }

    private Counter counter(String name) {
        Counter counter = meterRegistry.find(name).counter();
        assertThat(counter).as("counter %s", name).isNotNull();
        return counter;
    }

    private boolean finished(Future<Boolean> run) {
        if (!run.isDone()) {
            return false;
        }
        try {
            return run.get();
        } catch (ExecutionException e) {
            publishFailures.add(String.valueOf(e.getCause()));
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static boolean waitUntil(Callable<Boolean> condition, Duration timeout) {
        try {
            await().atMost(timeout).pollInterval(Duration.ofMillis(20)).until(condition);
            return true;
        } catch (ConditionTimeoutException e) {
            return false;
        }
    }

    private static String publisherId(int index) {
        return String.format("overload-pub-%02d", index);
    }

    private static byte[] payload(String id) {
        return id.getBytes(StandardCharsets.UTF_8);
    }

    private String serverUri() {
        return "tcp://localhost:" + mqttPort;
    }

    /**
     * Ignores every event a client does not override.
     */
    private abstract static class Callback implements MqttCallback {

        @Override
        public void disconnected(MqttDisconnectResponse response) {
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

}
