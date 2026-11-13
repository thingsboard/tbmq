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
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.data.PersistedPacketType;
import org.thingsboard.mqtt.broker.common.data.util.BytesUtil;
import org.thingsboard.mqtt.broker.common.stats.DefaultStatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;
import org.thingsboard.mqtt.broker.dao.messages.AppendResult;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgQueueStore;
import org.thingsboard.mqtt.broker.dao.messages.NewDeviceMsg;
import org.thingsboard.mqtt.broker.dao.messages.QueueAppend;
import org.thingsboard.mqtt.broker.dao.messages.StoredDeviceMsg;
import org.thingsboard.mqtt.broker.dao.store.StoreException;
import org.thingsboard.mqtt.broker.exception.PipelineClosedException;
import org.thingsboard.mqtt.broker.gen.queue.PublishMsgProto;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.common.DefaultTbQueueMsgHeaders;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.device.DeviceActorManager;
import org.thingsboard.mqtt.broker.service.processing.MultiplePublishMsgCallbackWrapper;
import org.thingsboard.mqtt.broker.service.processing.PublishMsgCallback;
import org.thingsboard.mqtt.broker.standalone.device.DeviceMsgBodyCodec;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class StandalonePersistStageTest {

    private static final long WAIT_MS = TimeUnit.SECONDS.toMillis(10);

    private final DeviceMsgQueueStore store = mock(DeviceMsgQueueStore.class);
    private final ClientSessionCache clientSessionCache = mock(ClientSessionCache.class);
    private final DeviceActorManager deviceActorManager = mock(DeviceActorManager.class);
    private final PublishMsgCallback callback = mock(PublishMsgCallback.class);
    private final PublishMsgCallback cb1 = mock(PublishMsgCallback.class);
    private final PublishMsgCallback cb2 = mock(PublishMsgCallback.class);
    private final PublishMsgCallback cb3 = mock(PublishMsgCallback.class);

    private final Logger consumerLogger = (Logger) LoggerFactory.getLogger(StandaloneDeviceMsgQueueConsumer.class);
    private final ListAppender<ILoggingEvent> consumerLog = new ListAppender<>();

    // the threads the store was called on
    private final List<String> appendThreads = new CopyOnWriteArrayList<>();

    private PipelineProperties properties;
    private SimpleMeterRegistry meterRegistry;
    private StandaloneDeviceMsgQueueConsumer consumer;
    private StandaloneDeviceMsgQueuePublisher publisher;

    @BeforeEach
    void setUp() {
        consumerLog.start();
        consumerLogger.addAppender(consumerLog);
        consumerLogger.setAdditive(false);

        when(store.append(any())).thenAnswer(inv -> {
            appendThreads.add(Thread.currentThread().getName());
            return results(inv.getArgument(0));
        });

        properties = new PipelineProperties();
        // one worker: every copy a test submits before the start goes into its first batch
        properties.getPersist().setPartitions(1);
        newStage();
    }

    @AfterEach
    void tearDown() {
        consumer.destroy();
        consumerLogger.detachAppender(consumerLog);
        consumerLogger.setAdditive(true);
        consumerLog.stop();
    }

    @Test
    void aBatchIsOneAppendGroupedByClientInArrivalOrder() {
        submit("c1", msg("m1"), cb1);
        submit("c2", msg("m2"), cb2);
        submit("c1", msg("m3"), cb3);

        runBatch();

        List<QueueAppend> appends = captureSingleAppend();
        assertThat(appends).extracting(QueueAppend::queueKey).containsExactly("c1", "c2");
        assertThat(payloads(appends.get(0))).containsExactly("m1", "m3");
        assertThat(payloads(appends.get(1))).containsExactly("m2");
        assertThat(appendThreads).singleElement().asString().startsWith("standalone-persist-");
    }

    @Test
    void callbacksFireOnlyAfterTheAppendReturned() {
        submit("c1", msg("m1"), cb1);
        submit("c2", msg("m2"), cb2);

        runBatch();

        InOrder inOrder = inOrder(store, cb1, cb2);
        inOrder.verify(store).append(any());
        inOrder.verify(cb1).onSuccess();
        inOrder.verify(cb2).onSuccess();
        verify(cb1, never()).onFailure(any());
        verify(cb2, never()).onFailure(any());
    }

    @Test
    void mqtt_3_3_2_5_anIntervalOfZeroIsNotKeptForAnOfflineSubscriber() {
        givenSession("c1", false);

        submit("c1", withExpiry(0), callback);
        runBatch();

        verify(store, never()).append(any());
        verify(callback).onSuccess();
        verify(callback, never()).onFailure(any());
        verifyNoInteractions(deviceActorManager);
        assertThat(expiredCounter().count()).isEqualTo(1.0);
    }

    @Test
    void anIntervalOfZeroIsStoredForAnOnlineSubscriber() {
        givenSession("c1", true);

        submit("c1", withExpiry(0), callback);
        runBatch();

        assertThat(captureSingleAppend()).singleElement().satisfies(append -> {
            assertThat(append.queueKey()).isEqualTo("c1");
            assertThat(append.messages()).extracting(NewDeviceMsg::msgExpiryIntervalSec).containsExactly(0);
        });
        verify(callback).onSuccess();
        verify(deviceActorManager).notifyPublishMsg(eq("c1"), any());
        assertThat(expiredCounter().count()).isZero();
    }

    @Test
    void anIntervalOfZeroIsNotKeptForAnOfflineGroup() {                                   // a group key never has a session
        submit("ss_g_t", withExpiry(0), callback);
        runBatch();

        verify(store, never()).append(any());
        verify(callback).onSuccess();
        assertThat(expiredCounter().count()).isEqualTo(1.0);
    }

    @Test
    void anIntervalOfZeroIsStoredForAConnectedClientWhoseIdLooksLikeAGroupKey() {
        givenSession("ss_client", true);

        submit("ss_client", withExpiry(0), callback);
        runBatch();

        assertThat(captureSingleAppend()).extracting(QueueAppend::queueKey).containsExactly("ss_client");
        verify(callback).onSuccess();
        assertThat(expiredCounter().count()).isZero();
    }

    @Test
    void aMessageWithoutAnIntervalOrWithAnotherOneIsStoredForAnOfflineSubscriber() {
        givenSession("c1", false);

        submit("c1", msg("m1"), cb1);                                                    // no Message Expiry Interval
        submit("c1", withExpiry(-1), cb2);                                               // 0xFFFFFFFF, the largest unsigned interval
        runBatch();

        assertThat(captureSingleAppend()).singleElement().satisfies(append ->
                assertThat(append.messages()).extracting(NewDeviceMsg::msgExpiryIntervalSec).containsExactly(null, -1));
        verify(cb1).onSuccess();
        verify(cb2).onSuccess();
        verifyNoInteractions(deviceActorManager);
        assertThat(expiredCounter().count()).isZero();
    }

    @Test
    void onlyConnectedClientsAreWokenUp() {
        givenSession("c1", true);
        givenSession("c2", false);

        submit("c1", msg("m1"), cb1);
        submit("c2", msg("m2"), cb2);
        submit("c1", msg("m3"), cb3);
        runBatch();

        ArgumentCaptor<DevicePublishMsg> wakeUp = ArgumentCaptor.forClass(DevicePublishMsg.class);
        verify(deviceActorManager).notifyPublishMsg(eq("c1"), wakeUp.capture());
        verifyNoMoreInteractions(deviceActorManager);
        // the client's last message of the batch
        assertThat(new String(wakeUp.getValue().getPayload(), StandardCharsets.UTF_8)).isEqualTo("m3");
    }

    @Test
    void aStoreErrorFailsTheBatchAndTheWorkerKeepsServing() {                            // Review Focus 5
        givenSession("c1", true);
        doThrow(new StoreException("disk")).doReturn(List.of()).when(store).append(any());
        consumer.startConsuming();

        submit("c1", msg("m1"), cb1);
        verify(cb1, timeout(WAIT_MS)).onFailure(isA(StoreException.class));
        submit("c1", msg("m2"), cb2);
        assertThat(consumer.workers().drain(WAIT_MS)).isTrue();

        verify(cb1, never()).onSuccess();
        verify(cb2).onSuccess();
        verify(cb2, never()).onFailure(any());
        // only the stored batch wakes the client up
        verify(deviceActorManager, times(1)).notifyPublishMsg(eq("c1"), any());
        assertThat(consumerLog.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getThrowableProxy().getMessage()).isEqualTo("disk");
        });
    }

    @Test
    void aStoreErrorFailsOnlyTheCopiesItTriedToStore() {
        givenSession("c2", false);
        doThrow(new StoreException("disk")).when(store).append(any());

        submit("c1", msg("m1"), cb1);
        submit("c2", withExpiry(0), cb2);                                                // expired before the append
        submit("c1", msg("m3"), cb3);
        runBatch();

        verify(cb1).onFailure(isA(StoreException.class));
        verify(cb3).onFailure(isA(StoreException.class));
        verify(cb2).onSuccess();
        verify(cb2, never()).onFailure(any());
        verifyNoInteractions(deviceActorManager);
    }

    @Test
    void aCopyThatCannotBeReadFailsAloneAndTheRestOfTheBatchIsStored() {
        TbProtoQueueMsg<PublishMsgProto> broken = msg("m2");
        broken.getHeaders().put(BrokerConstants.MESSAGE_EXPIRY_INTERVAL, new byte[]{1, 2});  // not a Four Byte Integer

        submit("c1", msg("m1"), cb1);
        submit("c1", broken, cb2);
        submit("c1", msg("m3"), cb3);
        runBatch();

        assertThat(captureSingleAppend()).singleElement().satisfies(append ->
                assertThat(payloads(append)).containsExactly("m1", "m3"));
        verify(cb2).onFailure(any());
        verify(cb2, never()).onSuccess();
        verify(cb1).onSuccess();
        verify(cb3).onSuccess();
        assertThat(consumerLog.list).singleElement().satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.ERROR));
    }

    @Test
    void aThrowingCallbackOrWakeUpLeavesTheRestOfTheBatchServed() {
        givenSession("c1", true);
        givenSession("c2", true);
        doThrow(new IllegalStateException("expired callback")).when(callback).onSuccess();
        doThrow(new IllegalStateException("stored callback")).when(cb1).onSuccess();
        doThrow(new IllegalStateException("wake-up")).when(deviceActorManager).notifyPublishMsg(eq("c1"), any());

        submit("ss_g_t", withExpiry(0), callback);                                      // expired: its callback throws
        submit("c1", msg("m1"), cb1);
        submit("c2", msg("m2"), cb2);
        runBatch();

        assertThat(captureSingleAppend()).extracting(QueueAppend::queueKey).containsExactly("c1", "c2");
        verify(cb2).onSuccess();
        verify(deviceActorManager).notifyPublishMsg(eq("c2"), any());
        assertThat(consumerLog.list).hasSize(3).allSatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.ERROR));
    }

    @Test
    void aThrowingCallbackLeavesTheOtherFailuresDelivered() {
        doThrow(new StoreException("disk")).when(store).append(any());
        doThrow(new IllegalStateException("failure callback")).when(cb1).onFailure(any());

        submit("c1", msg("m1"), cb1);
        submit("c2", msg("m2"), cb2);
        runBatch();

        verify(cb2).onFailure(isA(StoreException.class));
        verify(cb2, never()).onSuccess();
    }

    @Test
    void theCountersAddWhatTheStoreExpiredAndTrimmed() {
        when(store.expiredEntries()).thenReturn(5L);
        when(store.trimmedEntries()).thenReturn(7L);

        submit("ss_g_t", withExpiry(0), callback);
        runBatch();

        assertThat(expiredCounter().count()).isEqualTo(6.0);
        assertThat(meterRegistry.get("trimmedDeviceMsgs").functionCounter().count()).isEqualTo(7.0);
    }

    @Test
    void givenStage_whenInit_thenThePersistQueueGaugeReadsTheWaitingCopies() {
        submit("c1", msg("m1"), cb1);
        submit("c2", msg("m2"), cb2);
        submit("c3", msg("m3"), cb3);

        assertThat(meterRegistry.get("standalonePersistQueued").gauge().value()).isEqualTo(3.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void aBatchHoldsAtMostTheConfiguredBatchSize() {
        properties.getPersist().setBatchSize(2);
        newStage();

        submit("c1", msg("m1"), cb1);
        submit("c1", msg("m2"), cb2);
        submit("c1", msg("m3"), cb3);
        runBatch();

        ArgumentCaptor<List<QueueAppend>> appends = ArgumentCaptor.forClass(List.class);
        verify(store, times(2)).append(appends.capture());
        assertThat(appends.getAllValues()).extracting(batch -> batch.get(0).messages().size()).containsExactly(2, 1);
    }

    @Test
    void aFullPartitionQueueHoldsTheSenderUntilTheWorkerTakesACopy() throws InterruptedException {
        properties.getPersist().setPartitionQueueCapacity(1);
        newStage();
        submit("c1", msg("m1"), cb1);

        Thread sender = Thread.ofPlatform().start(() -> publisher.sendMsg("c1", msg("m2"), cb2));
        await().atMost(WAIT_MS, TimeUnit.MILLISECONDS).until(() -> sender.getState() == Thread.State.TIMED_WAITING);
        assertThat(consumer.workers().queued()).isOne();
        verifyNoInteractions(cb2);

        consumer.startConsuming();
        sender.join(WAIT_MS);

        assertThat(sender.isAlive()).isFalse();
        assertThat(consumer.workers().drain(WAIT_MS)).isTrue();
        verify(cb1).onSuccess();
        verify(cb2).onSuccess();
    }

    @Test
    void givenDrainedStage_whenSendMsg_thenTheCopyFails() {
        consumer.startConsuming();
        assertThat(consumer.workers().drain(WAIT_MS)).isTrue();

        publisher.sendMsg("c1", msg("m1"), callback);

        ArgumentCaptor<Throwable> failure = ArgumentCaptor.forClass(Throwable.class);
        verify(callback).onFailure(failure.capture());
        assertThat(failure.getValue()).isInstanceOf(PipelineClosedException.class);
        assertThat(failure.getValue().getStackTrace()).isEmpty();
        verify(callback, never()).onSuccess();
        verifyNoInteractions(store);
        assertThat(consumerLog.list).isEmpty();
    }

    @Test
    void givenDrainedStage_whenTheCopiesOfADispatchedPublishArrive_thenThePublishFailsOnceAsClosed() {
        consumer.startConsuming();
        assertThat(consumer.workers().drain(WAIT_MS)).isTrue();
        PipelineBudget budget = new PipelineBudget(10, 1000, 80, 50);
        assertThat(budget.tryAcquire(7)).isTrue();
        TbQueueCallback queueCallback = mock(TbQueueCallback.class);
        // what the dispatcher hands the persist stage for a publish with two persistent DEVICE subscribers
        PublishMsgCallback copies = new MultiplePublishMsgCallbackWrapper(2, new CompleteOncePublishCallback(budget, 7, queueCallback));

        publisher.sendMsg("c1", msg("m1"), copies);
        publisher.sendMsg("c2", msg("m1"), copies);

        verify(queueCallback).onFailure(isA(PipelineClosedException.class));
        verify(queueCallback, never()).onSuccess(any());
        assertThat(budget.queuedMessages()).isZero();
    }

    @Test
    void givenStoppedStage_whenSendMsg_thenTheCopyFailsAsClosed() {
        consumer.startConsuming();
        consumer.destroy();

        publisher.sendMsg("c1", msg("m1"), callback);

        verify(callback).onFailure(isA(PipelineClosedException.class));
        verifyNoInteractions(store);
    }

    @Test
    void givenInterruptedCaller_whenSendMsg_thenTheCopyFailsAndTheInterruptIsKept() {
        Thread.currentThread().interrupt();
        try {
            publisher.sendMsg("c1", msg("m1"), callback);

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
        verify(callback).onFailure(isA(InterruptedException.class));
        verify(callback, never()).onSuccess();
        assertThat(consumer.workers().queued()).isZero();
    }

    private void newStage() {
        if (consumer != null) {
            consumer.destroy();
        }
        // a registry of its own: a meter registered again under the same name would stay bound to the old stage
        meterRegistry = new SimpleMeterRegistry();
        StatsFactory statsFactory = new DefaultStatsFactory(meterRegistry);
        consumer = new StandaloneDeviceMsgQueueConsumer(properties, statsFactory, store, clientSessionCache, deviceActorManager);
        consumer.init();
        publisher = new StandaloneDeviceMsgQueuePublisher(consumer);
    }

    private void submit(String clientId, TbProtoQueueMsg<PublishMsgProto> msg, PublishMsgCallback cb) {
        publisher.sendMsg(clientId, msg, cb);
    }

    /**
     * Starts the worker, which takes every copy submitted so far as one batch, and waits until it handled them.
     */
    private void runBatch() {
        consumer.startConsuming();
        assertThat(consumer.workers().drain(WAIT_MS)).isTrue();
    }

    @SuppressWarnings("unchecked")
    private List<QueueAppend> captureSingleAppend() {
        ArgumentCaptor<List<QueueAppend>> appends = ArgumentCaptor.forClass(List.class);
        verify(store).append(appends.capture());
        return appends.getValue();
    }

    private void givenSession(String clientId, boolean connected) {
        when(clientSessionCache.getClientSessionInfo(clientId))
                .thenReturn(ClientSessionInfo.builder().clientId(clientId).connected(connected).build());
    }

    private FunctionCounter expiredCounter() {
        return meterRegistry.get("expiredDeviceMsgs").functionCounter();
    }

    private static List<AppendResult> results(List<QueueAppend> appends) {
        return appends.stream().map(append -> new AppendResult(append.queueKey(), 0, 1, append.messages().size())).toList();
    }

    private static List<String> payloads(QueueAppend append) {
        return append.messages().stream()
                .map(stored -> DeviceMsgBodyCodec.toMsg(append.queueKey(),
                        new StoredDeviceMsg(1, 1, PersistedPacketType.PUBLISH, false, stored.createdTimeMs(), 0, stored.body())))
                .map(msg -> new String(msg.getPayload(), StandardCharsets.UTF_8))
                .toList();
    }

    private static TbProtoQueueMsg<PublishMsgProto> withExpiry(int msgExpiryInterval) {
        TbProtoQueueMsg<PublishMsgProto> msg = msg("expiring");
        msg.getHeaders().put(BrokerConstants.MESSAGE_EXPIRY_INTERVAL, BytesUtil.integerToBytes(msgExpiryInterval));
        return msg;
    }

    private static TbProtoQueueMsg<PublishMsgProto> msg(String payload) {
        PublishMsgProto proto = PublishMsgProto.newBuilder()
                .setClientId("publisher")
                .setTopicName("t/1")
                .setQos(1)
                .setPayload(ByteString.copyFromUtf8(payload))
                .build();
        return new TbProtoQueueMsg<>("subscriber", proto, new DefaultTbQueueMsgHeaders());
    }

}
