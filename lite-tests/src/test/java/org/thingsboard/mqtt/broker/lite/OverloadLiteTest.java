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

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.net.Socket;
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
 * it drops, the pipeline and the heap stay bounded, and the broker resumes the connections it paused; and, with the
 * store stalled, that the keep-alive closes a dead client on a paused connection but none of the live ones.
 * <p>
 * The pipeline holds at most {@value #MAX_MESSAGES} publishes. In the overload, every append to the store takes 2 ms
 * longer, so the persist worker of the one persistent subscriber stores at most 8 copies per 2 ms: far fewer than
 * twenty QoS 1 publishers and a QoS 0 firehose send.
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
    // Receive Maximum (1000) unanswered, refusals answer nearly every publish and the load is over in about a second;
    // with 10, the pipeline sets the pace, and the load lasts seconds in which the publishers are paused and resumed
    // thousands of times. The keep-alive on paused connections has a test of its own.
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
    // the stall: the filler fills the pipeline, then each live publisher sends its publishes into it
    private static final int STALL_PUBLISHERS = 20;
    private static final int STALL_MSGS_PER_PUBLISHER = 100;
    private static final int STALL_FILLER_MSGS = MAX_MESSAGES + 50;
    private static final Duration STALL_TIMEOUT = Duration.ofSeconds(30);
    private static final int KEEP_ALIVE_TIMEOUT = 0x8D;
    // the keep-alive closes a session after 1.5 times its keep-alive without a packet
    private static final long KEEP_ALIVE_DEADLINE_MS = KEEP_ALIVE_SECONDS * 1500L;
    // the broker times the keep-alive with currentTimeMillis, the test with nanoTime
    private static final long CLOCK_TOLERANCE_MS = 10;
    // scheduling, the disconnect through the client actor and the DISCONNECT on the wire: closes were measured 3.3 s
    // after the PUBACK against a bound of 3.4 s (the 3 s deadline and two 200 ms ticks) without the slack; at 1.5 s the
    // bound is still under a close one
    // deadline (3 s) late
    private static final long CLOSE_SLACK_MS = 1_500;
    // the load lasts seconds, so far more depth samples than this are taken at one every 50 ms
    private static final int MIN_DEPTH_SAMPLES = 50;
    private static final String STALL_TOPIC_PREFIX = "stall/";
    // the meter StandalonePublishBackpressure registers
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
    @Value("${mqtt.keep-alive.monitoring-delay-ms}")
    private long monitoringDelayMs;

    private final List<MqttAsyncClient> clients = new ArrayList<>();
    private final ExecutorService publishing =
            Executors.newFixedThreadPool(Math.max(QOS1_PUBLISHERS, STALL_PUBLISHERS) + 1);
    private final ScheduledExecutorService samplers = Executors.newScheduledThreadPool(2);
    // the stalled store lets the appends through once it is counted down
    private final CountDownLatch appendsReleased = new CountDownLatch(1);
    private SilentClient silentClient;

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
    private final AtomicInteger depthSamples = new AtomicInteger();
    // a sampler that throws is silently not run again by the scheduler: the failures are recorded and asserted
    private final List<String> samplerFailures = new CopyOnWriteArrayList<>();

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
        appendsReleased.countDown();
        if (silentClient != null) {
            silentClient.close();
        }
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
        // the samplers ran throughout, so that the peaks below are not those of a sampler that stopped early
        assertThat(samplerFailures).as("sampler failures").isEmpty();
        assertThat(depthSamples.get()).as("pipeline depth samples").isGreaterThanOrEqualTo(MIN_DEPTH_SAMPLES);
        assertThat(heapSamples.get()).as("heap samples").isGreaterThanOrEqualTo(2);
        // the broker closed no connection, and paused connections resumed: checked first, as the load stops early when
        // a connection is lost, and the checks below then fail for that reason only
        assertThat(publishersDisconnectedByBroker).as("publisher connections the broker closed: %s", brokerDisconnects)
                .isZero();
        assertThat(allPublishersFinished).as("all publishers finished; failed publishes %s, connections given up by "
                + "the client %s", publishFailures, clientDisconnects).isTrue();
        // nothing acknowledged and then dropped
        assertThat(notReceived(successfullyAcknowledgedIds)).as("acknowledged ids the subscriber did not receive")
                .isEmpty();
        // the overload must have refused publishes and dropped QoS 0 ones, or the checks on them prove nothing
        assertThat(refusedIds).as("ids refused with an error code").isNotEmpty();
        assertThat(qos0Sent).as("QoS 0 publishes sent, against received %s", qos0Received).isGreaterThan(qos0Received);
        assertThat(refusedIds).as("ids refused with an error code")
                .allSatisfy(id -> assertThat(refusalCode(id)).as(id).isEqualTo(QUOTA_EXCEEDED));
        // sampled from PipelineBudget every 50 ms
        assertThat(peakQueuedMessages).as("peak of the publishes in the pipeline").isLessThanOrEqualTo(MAX_MESSAGES);
        // The stages' own queues, unbounded (dispatch) or bounded far above the budget (persist), are what the budget
        // keeps small. With the budget's limits set to their maximum, the dispatch queues peaked at 269 publishes, and
        // at 15,686 with the publishers' windows at the broker's Receive Maximum.
        assertThat(peakDispatchQueued.get()).as("peak of the dispatch queues").isLessThanOrEqualTo(MAX_MESSAGES);
        assertThat(peakPersistQueued.get()).as("peak of the persist queues").isLessThanOrEqualTo(MAX_MESSAGES);
        // A sanity bound only: QoS 0 copies are dropped at the subscriber's channel once it is not writable, and the
        // publishers' windows keep at most 200 QoS 1 publishes in the broker, so the heap stays far below the bound
        // with or without the budget (it grew from 72 to 80 MiB with the budget's limits set to their maximum).
        assertThat(peakHeapUsedAfterGcBytes - baselineHeapBytes).as("heap growth after GC, in bytes")
                .isLessThan(HEAP_BOUND_BYTES);
        assertThat(publishPausesCounterDelta).as("publishPauses counter delta").isGreaterThan(0);
        // every QoS 0 drop is counted, and so is every refused QoS 1 publish: MqttPublishHandler counts it as dropped,
        // as in the cluster edition
        assertThat(droppedMsgsCounterDelta).as("droppedMsgs counter delta")
                .isEqualTo((double) (qos0Sent - qos0Received + refusedIds.size()));
    }

    /**
     * [MQTT-3.1.2-22] The keep-alive stays enforced on a paused connection: the broker closes a client that sends
     * nothing for one and a half keep-alive periods, and no other. The store blocks every append, so the pipeline
     * fills up and stays full; every publisher is paused, and stays paused across two keep-alive deadlines, before the
     * store lets the appends through. The live clients keep sending into connections that the broker reads once every
     * half keep-alive only, which must answer their PINGREQs before they give up; the dead one sends one PUBLISH, which
     * finds the pipeline paused and pauses its connection, and then nothing at all.
     */
    @Test
    public void mqtt_3_1_2_22_pausedLiveClientsAreNotClosedAndADeadOneIs() throws Exception {
        blockStoreAppends();
        connectSubscriber("stall-sub", STALL_TOPIC_PREFIX + "#");
        MqttAsyncClient filler = publisher("stall-filler");
        List<MqttAsyncClient> livePublishers = new ArrayList<>();
        for (int i = 0; i < STALL_PUBLISHERS; i++) {
            livePublishers.add(publisher(stallPublisherId(i)));
        }
        List<MqttAsyncClient> liveClients = new ArrayList<>(livePublishers);
        liveClients.add(filler);
        connectAll(liveClients);
        long pausedBefore = pausedConnections();
        long t0 = System.nanoTime();

        // the store keeps the filler's publishes in the pipeline, until it is full
        CountDownLatch now = new CountDownLatch(0);
        List<Future<Boolean>> runs = new ArrayList<>();
        runs.add(publishing.submit(() -> publishQos1(filler, STALL_TOPIC_PREFIX + "filler", "stall-filler-",
                STALL_FILLER_MSGS, STALL_FILLER_MSGS, now)));
        assertThat(waitUntil(() -> budget.paused() && budget.queuedMessages() == MAX_MESSAGES
                && pausedConnections() == pausedBefore + 1, CONNECT_TIMEOUT)).as("pipeline full, filler paused")
                .isTrue();
        long fullAt = System.nanoTime();
        // the first publish of each live publisher finds the pipeline paused and full: it pauses the publisher and is
        // refused, and the publisher sends its next ones into a connection the broker no longer reads
        for (int i = 0; i < STALL_PUBLISHERS; i++) {
            MqttAsyncClient publisher = livePublishers.get(i);
            String topic = STALL_TOPIC_PREFIX + i;
            String idPrefix = stallPublisherId(i) + "-";
            runs.add(publishing.submit(() -> publishQos1(publisher, topic, idPrefix, STALL_MSGS_PER_PUBLISHER,
                    QOS1_WINDOW, now)));
        }
        assertThat(waitUntil(() -> pausedConnections() == pausedBefore + 1 + STALL_PUBLISHERS, CONNECT_TIMEOUT))
                .as("every live publisher paused").isTrue();
        long allPausedAt = System.nanoTime();

        silentClient = SilentClient.connect(mqttPort, "stall-dead", KEEP_ALIVE_SECONDS);
        silentClient.publishOnceAndGoSilent(STALL_TOPIC_PREFIX + "dead", payload("stall-dead-0"));
        assertThat(waitUntil(() -> pausedConnections() == pausedBefore + 2 + STALL_PUBLISHERS, CONNECT_TIMEOUT))
                .as("dead client paused").isTrue();

        // As long as two keep-alive deadlines, each 1.5 keep-alive periods after a packet and seen at the next
        // monitoring tick at the latest, and one more tick. Meanwhile only the keep-alive reads the live clients.
        long stallNanos =
                TimeUnit.MILLISECONDS.toNanos(2 * (KEEP_ALIVE_DEADLINE_MS + monitoringDelayMs) + monitoringDelayMs);
        waitUntil(() -> silentClient.closed() && System.nanoTime() - allPausedAt >= stallNanos, STALL_TIMEOUT);
        // the broker forgets the closed dead client's paused connection as it cleans the session up; the live ones,
        // the filler and the live publishers, stay paused
        waitUntil(() -> pausedConnections() == pausedBefore + 1 + STALL_PUBLISHERS, CONNECT_TIMEOUT);
        long pausedAtRelease = pausedConnections();
        long releasedAt = System.nanoTime();
        appendsReleased.countDown();
        waitUntil(() -> runs.stream().allMatch(Future::isDone) || !brokerDisconnects.isEmpty()
                || !clientDisconnects.isEmpty(), LOAD_TIMEOUT);
        // every run is looked at, so that each failure is recorded
        boolean allLiveFinished = runs.stream().map(this::finished).reduce(true, Boolean::logicalAnd)
                && publishFailures.isEmpty();
        long finishedAt = System.nanoTime();
        // the pipeline drains and every connection that was paused is read again and leaves the paused set
        waitUntil(() -> budget.queuedMessages() == 0 && pausedConnections() == pausedBefore, DRAIN_TIMEOUT);
        long pausedAfterDrain = pausedConnections();

        log.info("Stall: pipeline full at {} ms, every live publisher paused at {} ms; dead client published at {} ms, "
                        + "answered 0x{} at {} ms, closed with 0x{} at {} ms; store released at {} ms with {} "
                        + "connections paused; live clients done at {} ms", millisSince(t0, fullAt),
                millisSince(t0, allPausedAt), millisSince(t0, silentClient.publishedAt()),
                Integer.toHexString(silentClient.pubAckCode()), millisSince(t0, silentClient.answeredAt()),
                Integer.toHexString(silentClient.disconnectCode()), millisSince(t0, silentClient.closedAt()),
                millisSince(t0, releasedAt), pausedAtRelease - pausedBefore, millisSince(t0, finishedAt));
        log.info("Stall: live connections closed by the broker {}, given up by the clients {}; subscriber connection "
                + "lost {}", brokerDisconnects, clientDisconnects, subscriberDisconnects);

        // the live clients: none closed, all still paused when the store let the appends through, all resumed after
        assertThat(brokerDisconnects).as("live client connections the broker closed").isEmpty();
        assertThat(pausedAtRelease - pausedBefore).as("connections still paused when the store let the appends through")
                .isEqualTo(STALL_PUBLISHERS + 1);
        assertThat(pausedAfterDrain - pausedBefore).as("connections still paused after the pipeline drained")
                .isZero();
        assertThat(allLiveFinished).as("every live client finished after the stall; failed publishes %s, connections "
                + "given up by the client %s", publishFailures, clientDisconnects).isTrue();
        // the dead client: refused because the pipeline was full, then closed for its keep-alive
        assertThat(silentClient.pubAckCode()).as("PUBACK reason code to the dead client").isEqualTo(QUOTA_EXCEEDED);
        assertThat(silentClient.disconnectCode()).as("DISCONNECT reason code to the dead client")
                .isEqualTo(KEEP_ALIVE_TIMEOUT);
        // the broker took its last packet after it was sent and before it was answered
        assertThat(millisSince(silentClient.publishedAt(), silentClient.closedAt()))
                .as("ms from the dead client's PUBLISH to its close")
                .isGreaterThanOrEqualTo(KEEP_ALIVE_DEADLINE_MS - CLOCK_TOLERANCE_MS);
        // the tick that finds the deadline passed reads once, and the next one closes
        assertThat(millisSince(silentClient.answeredAt(), silentClient.closedAt()))
                .as("ms from the dead client's PUBACK to its close")
                .isLessThanOrEqualTo(KEEP_ALIVE_DEADLINE_MS + 2 * monitoringDelayMs + CLOSE_SLACK_MS);
    }

    private void runLoad() throws Exception {
        long setupStart = System.nanoTime();
        slowDownStoreAppends();
        connectSubscriber("overload-sub", SUBSCRIPTION);
        List<MqttAsyncClient> qos1Publishers = new ArrayList<>();
        for (int i = 0; i < QOS1_PUBLISHERS; i++) {
            qos1Publishers.add(publisher(publisherId(i)));
        }
        MqttAsyncClient qos0Publisher = publisher("overload-qos0-pub");
        List<MqttAsyncClient> publishers = new ArrayList<>(qos1Publishers);
        publishers.add(qos0Publisher);
        connectAll(publishers);

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
            runs.add(publishing.submit(() -> publishQos1(publisher, topic, idPrefix, QOS1_MSGS_PER_PUBLISHER,
                    QOS1_WINDOW, start)));
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

    /**
     * Every append to the store waits until the test counts {@link #appendsReleased} down.
     */
    private void blockStoreAppends() {
        doAnswer(invocation -> {
            try {
                appendsReleased.await(LOAD_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                // the shutdown stops the persist workers: the append goes on
                Thread.currentThread().interrupt();
            }
            return invocation.callRealMethod();
        }).when(deviceMsgQueueStore).append(anyList());
    }

    private void connectSubscriber(String clientId, String topicFilter) throws MqttException {
        MqttAsyncClient subscriber = new MqttAsyncClient(serverUri(), clientId, new MemoryPersistence());
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
        subscriber.subscribe(new MqttSubscription(topicFilter, 1)).waitForCompletion(CONNECT_TIMEOUT.toMillis());
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
    private static void connectAll(List<MqttAsyncClient> publishers) throws Exception {
        List<IMqttToken> connects = new ArrayList<>();
        for (MqttAsyncClient publisher : publishers) {
            connects.add(publisher.connect(publisherOptions()));
        }
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
     * Publishes {@code count} QoS 1 messages, each as soon as fewer than {@code window} are unanswered, and records how
     * each one was answered.
     *
     * @return {@code true} once every publish was answered, with a PUBACK or a failure
     */
    private boolean publishQos1(MqttAsyncClient publisher, String topic, String idPrefix, int count, int window,
                                CountDownLatch start) throws Exception {
        Semaphore unanswered = new Semaphore(window);
        CountDownLatch answered = new CountDownLatch(count);
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
        for (int i = 0; i < count; i++) {
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
        try {
            peakQueued.accumulate(budget.queuedMessages());
            peakDispatchQueued.accumulate(dispatchQueue.workers().queued());
            peakPersistQueued.accumulate(persistStage.workers().queued());
            peakPausedConnections.accumulate(pausedConnections());
            // a spy records every call for verification; those records are not what the broker holds on to
            clearInvocations(deviceMsgQueueStore);
            depthSamples.incrementAndGet();
        } catch (Throwable t) {
            samplerFailures.add("depth: " + t);
        }
    }

    private void sampleHeap() {
        try {
            peakHeap.accumulate(heapUsedAfterGc());
            heapSamples.incrementAndGet();
        } catch (Throwable t) {
            samplerFailures.add("heap: " + t);
        }
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

    private long pausedConnections() {
        Gauge gauge = meterRegistry.find(PAUSED_CONNECTIONS_GAUGE).gauge();
        return gauge == null ? 0 : (long) gauge.value();
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

    private static String stallPublisherId(int index) {
        return String.format("stall-pub-%02d", index);
    }

    private static long millisSince(long startNanos, long endNanos) {
        return TimeUnit.NANOSECONDS.toMillis(endNanos - startNanos);
    }

    private static byte[] payload(String id) {
        return id.getBytes(StandardCharsets.UTF_8);
    }

    private String serverUri() {
        return "tcp://localhost:" + mqttPort;
    }

    /**
     * A client that connects with MQTT 5, sends one QoS 1 PUBLISH and then never sends a packet again. The test reads
     * what the broker sends it, only to see how its PUBLISH is answered, and when and how the broker closes it.
     */
    private static final class SilentClient implements AutoCloseable {

        private static final int CONNACK = 2;
        private static final int PUBACK = 4;
        private static final int DISCONNECT = 14;
        private static final long NOT_YET = Long.MIN_VALUE;

        private final Socket socket;
        private final InputStream in;
        private final AtomicLong closedAt = new AtomicLong(NOT_YET);
        private volatile long publishedAt = NOT_YET;
        private volatile long answeredAt = NOT_YET;
        private volatile int pubAckCode = -1;
        private volatile int disconnectCode = -1;

        private SilentClient(Socket socket) throws IOException {
            this.socket = socket;
            this.in = new BufferedInputStream(socket.getInputStream());
        }

        static SilentClient connect(int port, String clientId, int keepAliveSeconds) throws IOException {
            Socket socket = new Socket("localhost", port);
            SilentClient client = new SilentClient(socket);
            socket.setSoTimeout((int) CONNECT_TIMEOUT.toMillis());
            client.send(connectPacket(clientId, keepAliveSeconds));
            Packet connAck = client.readPacket();
            assertThat(connAck).as("CONNACK to %s", clientId).isNotNull();
            assertThat(connAck.type()).as("packet type of the CONNACK").isEqualTo(CONNACK);
            assertThat(connAck.body()[1]).as("CONNACK reason code").isZero();
            socket.setSoTimeout(0);
            return client;
        }

        void publishOnceAndGoSilent(String topic, byte[] payload) throws IOException {
            publishedAt = System.nanoTime();
            send(publishPacket(topic, 1, payload));
            Thread reader = new Thread(this::readUntilClosed, "silent-client-reader");
            reader.setDaemon(true);
            reader.start();
        }

        private void readUntilClosed() {
            try {
                for (Packet packet = readPacket(); packet != null; packet = readPacket()) {
                    long now = System.nanoTime();
                    if (packet.type() == PUBACK) {
                        // packet id, then the reason code, which may be left out for Success
                        pubAckCode = packet.body().length > 2 ? packet.body()[2] & 0xFF : 0;
                        answeredAt = now;
                    } else if (packet.type() == DISCONNECT) {
                        disconnectCode = packet.body().length > 0 ? packet.body()[0] & 0xFF : 0;
                        closedAt.compareAndSet(NOT_YET, now);
                    }
                }
            } catch (IOException e) {
                // the connection is gone: the broker closed it, or the test did after the checks
            }
            closedAt.compareAndSet(NOT_YET, System.nanoTime());
        }

        boolean closed() {
            return closedAt.get() != NOT_YET;
        }

        long publishedAt() {
            return publishedAt;
        }

        long answeredAt() {
            return answeredAt;
        }

        long closedAt() {
            return closedAt.get();
        }

        int pubAckCode() {
            return pubAckCode;
        }

        int disconnectCode() {
            return disconnectCode;
        }

        @Override
        public void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
                // nothing is left to release
            }
        }

        private void send(byte[] packet) throws IOException {
            OutputStream out = socket.getOutputStream();
            out.write(packet);
            out.flush();
        }

        /**
         * @return the next packet, or {@code null} when the connection ended
         */
        private Packet readPacket() throws IOException {
            int firstByte = in.read();
            if (firstByte < 0) {
                return null;
            }
            int remainingLength = 0;
            int multiplier = 1;
            int digit;
            do {
                digit = in.read();
                if (digit < 0) {
                    return null;
                }
                remainingLength += (digit & 0x7F) * multiplier;
                multiplier *= 128;
            } while ((digit & 0x80) != 0);
            byte[] body = in.readNBytes(remainingLength);
            return body.length < remainingLength ? null : new Packet(firstByte >> 4, body);
        }

        private static byte[] connectPacket(String clientId, int keepAliveSeconds) {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            writeString(body, "MQTT");
            body.write(5); // protocol version
            body.write(0x02); // Clean Start
            body.write(keepAliveSeconds >> 8);
            body.write(keepAliveSeconds);
            body.write(0); // no properties
            writeString(body, clientId);
            return packet(0x10, body.toByteArray());
        }

        private static byte[] publishPacket(String topic, int packetId, byte[] payload) {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            writeString(body, topic);
            body.write(packetId >> 8);
            body.write(packetId);
            body.write(0); // no properties
            body.writeBytes(payload);
            return packet(0x32, body.toByteArray()); // PUBLISH, QoS 1
        }

        private static byte[] packet(int firstByte, byte[] body) {
            ByteArrayOutputStream packet = new ByteArrayOutputStream();
            packet.write(firstByte);
            int length = body.length;
            do {
                int digit = length % 128;
                length /= 128;
                packet.write(length > 0 ? digit | 0x80 : digit);
            } while (length > 0);
            packet.writeBytes(body);
            return packet.toByteArray();
        }

        private static void writeString(ByteArrayOutputStream out, String value) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            out.write(bytes.length >> 8);
            out.write(bytes.length);
            out.writeBytes(bytes);
        }

        private record Packet(int type, byte[] body) {
        }

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
