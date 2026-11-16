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
package org.thingsboard.mqtt.broker.standalone.pipeline;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;
import org.thingsboard.mqtt.broker.common.util.edition.TbmqEditionResolver;
import org.thingsboard.mqtt.broker.exception.PipelineClosedException;
import org.thingsboard.mqtt.broker.exception.PublishLimitExceededException;
import org.thingsboard.mqtt.broker.gen.queue.PublishMsgProto;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.common.DefaultTbQueueMsgHeaders;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.service.historical.stats.TbMessageStatsReportClient;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.processing.MsgDispatcherService;
import org.thingsboard.mqtt.broker.service.processing.PublishMsgCallback;
import org.thingsboard.mqtt.broker.service.processing.PublishMsgQueuePublisher;
import org.thingsboard.mqtt.broker.service.processing.PublishMsgWithId;
import org.thingsboard.mqtt.broker.service.processing.PublisherIdentity;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class StandalonePublishStageTest {

    private static final long WAIT_SECONDS = 10;
    private static final int MAX_MESSAGES = 1000;
    private static final long UNLIMITED_BYTES = Long.MAX_VALUE / 200;

    private final MsgDispatcherService msgDispatcherService = mock(MsgDispatcherService.class);
    private final TbMessageStatsReportClient statsClient = mock(TbMessageStatsReportClient.class);
    private final StatsFactory statsFactory = mock(StatsFactory.class);
    private final TbQueueCallback queueCallback = mock(TbQueueCallback.class);

    private final Logger consumerLogger = (Logger) LoggerFactory.getLogger(StandalonePublishMsgConsumerService.class);
    private final ListAppender<ILoggingEvent> consumerLog = new ListAppender<>();

    // what the dispatcher was handed, in the order it was handed
    private final BlockingQueue<Dispatched> dispatched = new LinkedBlockingQueue<>();

    private PipelineProperties properties;
    private PipelineBudget budget;
    private StandalonePublishMsgQueue queue;
    private StandalonePublishMsgQueuePublisher publisher;

    record Dispatched(PublishMsgWithId msg, PublishMsgCallback callback, String thread) {
    }

    @BeforeEach
    void setUp() {
        consumerLog.start();
        consumerLogger.addAppender(consumerLog);
        consumerLogger.setAdditive(false);

        doAnswer(inv -> {
            dispatched.add(new Dispatched(inv.getArgument(0), inv.getArgument(1), Thread.currentThread().getName()));
            return null;
        }).when(msgDispatcherService).processPublishMsg(any(), any());

        properties = new PipelineProperties();
        properties.getDispatch().setPartitions(4);
        budget = new PipelineBudget(MAX_MESSAGES, UNLIMITED_BYTES, 80, 50);
        queue = startedQueue();
        publisher = new StandalonePublishMsgQueuePublisher(budget, queue, statsClient);
    }

    @AfterEach
    void tearDown() {
        queue.destroy();
        consumerLogger.detachAppender(consumerLog);
        consumerLogger.setAdditive(true);
        consumerLog.stop();
    }

    @Test
    void theAcknowledgementWaitsForTheDispatcher() throws Exception {
        TbProtoQueueMsg<PublishMsgProto> msg = qos1("t/1");

        publisher.sendMsg(msg, queueCallback);
        Dispatched dispatch = nextDispatch();

        assertThat(dispatch.thread()).startsWith("standalone-dispatch-");
        assertThat(dispatch.msg().getId()).isNotNull();
        assertThat(dispatch.msg().getPublishMsgProto()).isSameAs(msg.getValue());
        assertThat(dispatch.msg().getHeaders()).isSameAs(msg.getHeaders());
        verify(queueCallback, never()).onSuccess(any());
        verify(queueCallback, never()).onFailure(any());
        assertThat(budget.queuedMessages()).isEqualTo(1);
        assertThat(budget.queuedBytes()).isEqualTo(msg.getValue().getPayload().size());

        dispatch.callback().onSuccess();

        verify(queueCallback).onSuccess(null);
        assertThat(budget.queuedMessages()).isZero();
        assertThat(budget.queuedBytes()).isZero();
    }

    @Test
    void aFullPipelineDropsQos0AndRefusesQos1() {
        TbQueueCallback cb0 = mock(TbQueueCallback.class);
        TbQueueCallback cb1 = mock(TbQueueCallback.class);
        TbQueueCallback cb2 = mock(TbQueueCallback.class);
        fillBudget();

        publisher.sendMsg(qos0("t/1"), cb0);
        publisher.sendMsg(qos1("t/1"), cb1);
        publisher.sendMsg(msg("t/1", 2, 3), cb2);

        verify(cb0).onSuccess(null);
        verify(cb0, never()).onFailure(any());
        verify(statsClient, times(1)).reportDroppedMsgs();
        ArgumentCaptor<Throwable> refusal = ArgumentCaptor.forClass(Throwable.class);
        verify(cb1).onFailure(refusal.capture());
        assertThat(refusal.getValue()).isInstanceOf(PublishLimitExceededException.class);
        // a refusal is flow control on the overload path, not an error: it carries no stack trace
        assertThat(refusal.getValue().getStackTrace()).isEmpty();
        verify(cb1, never()).onSuccess(any());
        verify(cb2).onFailure(isA(PublishLimitExceededException.class));
        verify(cb2, never()).onSuccess(any());
        verifyNoInteractions(msgDispatcherService);
        assertThat(budget.queuedMessages()).isEqualTo(MAX_MESSAGES);
    }

    @Test
    void publishesOfOneTopicAreDispatchedInOrder() {
        List<Integer> seen = new CopyOnWriteArrayList<>();
        doAnswer(inv -> {
            PublishMsgWithId msg = inv.getArgument(0);
            seen.add(msg.getPublishMsgProto().getPacketId());
            inv.<PublishMsgCallback>getArgument(1).onSuccess();
            return null;
        }).when(msgDispatcherService).processPublishMsg(any(), any());
        int total = 1000;

        for (int i = 0; i < total; i++) {
            publisher.sendMsg(msg("t/1", 1, i), queueCallback);
        }

        await().atMost(WAIT_SECONDS, TimeUnit.SECONDS).until(() -> seen.size() == total);
        assertThat(seen).containsExactlyElementsOf(IntStream.range(0, total).boxed().toList());
        verify(queueCallback, timeout(TimeUnit.SECONDS.toMillis(WAIT_SECONDS)).times(total)).onSuccess(null);
        await().atMost(WAIT_SECONDS, TimeUnit.SECONDS).until(() -> budget.queuedMessages() == 0);
    }

    @Test
    void everyDispatchedPublishGetsItsOwnIdFromACounterNotFromSecureRandom() throws Exception {
        int total = 100;
        for (int i = 0; i < total; i++) {
            publisher.sendMsg(msg("t/" + (i % 7), 1, i), queueCallback);
        }

        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            ids.add(nextDispatch().msg().getId());
        }

        assertThat(ids).doesNotHaveDuplicates();
        // UUID.randomUUID() takes a lock on a shared SecureRandom for every publish: the ids are one counter instead
        assertThat(ids).extracting(UUID::getMostSignificantBits).containsOnly(ids.get(0).getMostSignificantBits());
        assertThat(ids).extracting(UUID::getLeastSignificantBits).containsExactlyInAnyOrderElementsOf(
                LongStream.range(0, total).boxed().toList());
    }

    @Test
    void aThrowingDispatcherFailsThePublish() throws Exception {
        RuntimeException failure = new RuntimeException("dispatch failed");
        TbQueueCallback nextCallback = mock(TbQueueCallback.class);
        doAnswer(inv -> {
            throw failure;
        }).doAnswer(inv -> {
            inv.<PublishMsgCallback>getArgument(1).onSuccess();
            return null;
        }).when(msgDispatcherService).processPublishMsg(any(), any());

        publisher.sendMsg(qos1("t/1"), queueCallback);
        publisher.sendMsg(qos1("t/1"), nextCallback);

        verify(queueCallback, timeout(TimeUnit.SECONDS.toMillis(WAIT_SECONDS))).onFailure(failure);
        verify(queueCallback, never()).onSuccess(any());
        // the worker keeps serving: the next publish of the same topic is dispatched
        verify(nextCallback, timeout(TimeUnit.SECONDS.toMillis(WAIT_SECONDS))).onSuccess(null);
        assertThat(budget.queuedMessages()).isZero();
        assertThat(consumerLog.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getThrowableProxy().getMessage()).isEqualTo("dispatch failed");
        });
    }

    @Test
    void aDispatcherErrorFailsThePublishAndTheWorkerKeepsServing() {
        StackOverflowError failure = new StackOverflowError("dispatch overflow");
        TbQueueCallback nextCallback = mock(TbQueueCallback.class);
        doAnswer(inv -> {
            throw failure;
        }).doAnswer(inv -> {
            inv.<PublishMsgCallback>getArgument(1).onSuccess();
            return null;
        }).when(msgDispatcherService).processPublishMsg(any(), any());

        publisher.sendMsg(qos1("t/1"), queueCallback);
        publisher.sendMsg(qos1("t/1"), nextCallback);

        verify(queueCallback, timeout(TimeUnit.SECONDS.toMillis(WAIT_SECONDS))).onFailure(failure);
        verify(queueCallback, never()).onSuccess(any());
        // the worker thread survived: the next publish of the same topic, on the same worker, is dispatched
        verify(nextCallback, timeout(TimeUnit.SECONDS.toMillis(WAIT_SECONDS))).onSuccess(null);
        assertThat(budget.queuedMessages()).isZero();
        assertThat(consumerLog.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getThrowableProxy().getMessage()).isEqualTo("dispatch overflow");
        });
    }

    @Test
    void givenDrainedStage_whenSendMsg_thenThePublishFailsAndItsShareIsReleased() {
        assertThat(queue.workers().drain(TimeUnit.SECONDS.toMillis(WAIT_SECONDS))).isTrue();

        publisher.sendMsg(qos1("t/1"), queueCallback);

        ArgumentCaptor<Throwable> failure = ArgumentCaptor.forClass(Throwable.class);
        verify(queueCallback).onFailure(failure.capture());
        assertThat(failure.getValue()).isInstanceOf(PipelineClosedException.class);
        // every publish still on its way fails like this on shutdown: no stack trace to fill in or to log
        assertThat(failure.getValue().getStackTrace()).isEmpty();
        verify(queueCallback, never()).onSuccess(any());
        assertThat(budget.queuedMessages()).isZero();
        assertThat(budget.queuedBytes()).isZero();
        assertThat(consumerLog.list).isEmpty();
    }

    @Test
    void givenStoppedStage_whenSendMsg_thenThePublishFailsAsClosed() {
        queue.destroy();

        publisher.sendMsg(qos1("t/1"), queueCallback);

        verify(queueCallback).onFailure(isA(PipelineClosedException.class));
        verify(queueCallback, never()).onSuccess(any());
        assertThat(budget.queuedMessages()).isZero();
    }

    @Test
    void givenInterruptedCaller_whenSendMsg_thenThePublishFailsAndTheInterruptIsKept() {
        Thread.currentThread().interrupt();
        try {
            publisher.sendMsg(qos1("t/1"), queueCallback);

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
        verify(queueCallback).onFailure(isA(InterruptedException.class));
        verify(queueCallback, never()).onSuccess(any());
        assertThat(budget.queuedMessages()).isZero();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void givenStage_whenInit_thenTheDispatchQueueGaugeReadsTheWaitingPublishes() {
        StandalonePublishMsgQueue notStarted = new StandalonePublishMsgQueue(properties, statsFactory);
        notStarted.init();
        try {
            StandalonePublishMsgQueuePublisher notStartedPublisher = new StandalonePublishMsgQueuePublisher(budget, notStarted, statsClient);
            notStartedPublisher.sendMsg(qos1("t/1"), queueCallback);
            notStartedPublisher.sendMsg(qos1("t/2"), queueCallback);
            notStartedPublisher.sendMsg(qos0("t/3"), queueCallback);

            Object workers = notStarted.workers();
            ArgumentCaptor<ToDoubleFunction> gauge = ArgumentCaptor.forClass(ToDoubleFunction.class);
            verify(statsFactory).createGauge(eq("standaloneDispatchQueued"), eq(workers), gauge.capture());
            assertThat(gauge.getValue().applyAsDouble(workers)).isEqualTo(3.0);
        } finally {
            notStarted.destroy();
        }
    }

    @Test
    void givenADispatcherThatPublishesThroughTheStage_whenTheContextStarts_thenThereIsNoConstructorCycle(@TempDir Path tmp) throws IOException {
        // the condition evaluator captures the class loader when the context is constructed
        var beanFactory = new DefaultListableBeanFactory();
        beanFactory.setBeanClassLoader(liteEditionLoader(tmp));
        // as in Spring Boot, which refuses circular references
        beanFactory.setAllowCircularReferences(false);
        try (var ctx = new AnnotationConfigApplicationContext(beanFactory)) {
            // the dispatcher first: it needs the publisher, which needs the queue; the consumer needs the queue and the dispatcher
            ctx.register(PublishingDispatcher.class, StandalonePublishMsgQueuePublisher.class, StandalonePublishMsgConsumerService.class,
                    StandalonePublishMsgQueue.class, PipelineBudget.class, PipelineProperties.class);
            ctx.registerBean(StatsFactory.class, () -> statsFactory);
            ctx.registerBean(TbMessageStatsReportClient.class, () -> statsClient);
            ctx.refresh();
            ctx.getBean(StandalonePublishMsgConsumerService.class).startConsuming();

            ctx.getBean(PublishingDispatcher.class).publisher.sendMsg(qos1("t/1"), queueCallback);

            verify(queueCallback, timeout(TimeUnit.SECONDS.toMillis(WAIT_SECONDS))).onSuccess(null);
        }
    }

    // like the real dispatcher, it publishes through the stage that dispatches to it
    static class PublishingDispatcher implements MsgDispatcherService {

        private final PublishMsgQueuePublisher publisher;

        PublishingDispatcher(PublishMsgQueuePublisher publisher) {
            this.publisher = publisher;
        }

        @Override
        public void persistPublishMsg(SessionInfo sessionInfo, PublishMsg publishMsg, PublisherIdentity publisherIdentity, TbQueueCallback callback) {
        }

        @Override
        public void persistPublishMsg(String clientId, PublishMsg publishMsg, TbQueueCallback callback) {
        }

        @Override
        public void processPublishMsg(PublishMsgWithId publishMsgWithId, PublishMsgCallback callback) {
            callback.onSuccess();
        }

    }

    // classes come from the test class path, which also has the cluster marker; only a lite marker is visible here
    private static ClassLoader liteEditionLoader(Path dir) throws IOException {
        Path marker = dir.resolve(TbmqEditionResolver.MARKER_RESOURCE);
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, "lite", StandardCharsets.UTF_8);
        URL markerUrl = marker.toUri().toURL();
        return new ClassLoader(StandalonePublishStageTest.class.getClassLoader()) {
            @Override
            public Enumeration<URL> getResources(String name) throws IOException {
                return TbmqEditionResolver.MARKER_RESOURCE.equals(name) ? Collections.enumeration(List.of(markerUrl)) : super.getResources(name);
            }
        };
    }

    private StandalonePublishMsgQueue startedQueue() {
        StandalonePublishMsgQueue publishMsgQueue = new StandalonePublishMsgQueue(properties, statsFactory);
        publishMsgQueue.init();
        new StandalonePublishMsgConsumerService(publishMsgQueue, msgDispatcherService).startConsuming();
        return publishMsgQueue;
    }

    private Dispatched nextDispatch() throws InterruptedException {
        Dispatched dispatch = dispatched.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        assertThat(dispatch).as("a publish handed to the dispatcher").isNotNull();
        return dispatch;
    }

    private void fillBudget() {
        while (budget.tryAcquire(0)) {
            // take every free slot
        }
    }

    private static TbProtoQueueMsg<PublishMsgProto> qos0(String topic) {
        return msg(topic, 0, 1);
    }

    private static TbProtoQueueMsg<PublishMsgProto> qos1(String topic) {
        return msg(topic, 1, 1);
    }

    private static TbProtoQueueMsg<PublishMsgProto> msg(String topic, int qos, int packetId) {
        PublishMsgProto proto = PublishMsgProto.newBuilder()
                .setClientId("publisher")
                .setTopicName(topic)
                .setQos(qos)
                .setPacketId(packetId)
                .setPayload(ByteString.copyFromUtf8("payload-" + packetId))
                .build();
        return new TbProtoQueueMsg<>(topic, proto, new DefaultTbQueueMsgHeaders());
    }

}
