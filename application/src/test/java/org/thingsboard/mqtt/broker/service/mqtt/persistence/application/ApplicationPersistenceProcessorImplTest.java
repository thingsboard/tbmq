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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.application;

import com.google.common.collect.Sets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.thingsboard.mqtt.broker.gen.queue.PublishMsgProto;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.ApplicationPersistenceMsgQueueFactory;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMsgDeliveryService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.data.ApplicationMainProcessingState;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.data.ApplicationSharedSubscriptionCtx;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.data.ApplicationSharedSubscriptionJob;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.delivery.AppMsgDeliveryStrategy;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing.ApplicationMsgAcknowledgeStrategyFactory;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing.ApplicationPackProcessingCtx;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing.ApplicationPersistedMsgCtxService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing.ApplicationSubmitStrategyFactory;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.topic.ApplicationTopicService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.util.ApplicationClientHelperService;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ApplicationPersistenceProcessorImplTest {

    @Mock ApplicationMsgAcknowledgeStrategyFactory acknowledgeStrategyFactory;
    @Mock ApplicationSubmitStrategyFactory submitStrategyFactory;
    @Mock ApplicationPersistenceMsgQueueFactory applicationPersistenceMsgQueueFactory;
    @Mock MqttMsgDeliveryService mqttMsgDeliveryService;
    @Mock TbQueueAdmin queueAdmin;
    @Mock StatsManager statsManager;
    @Mock ApplicationPersistedMsgCtxService unacknowledgedPersistedMsgCtxService;
    @Mock ClientMqttActorManager clientMqttActorManager;
    @Mock ServiceInfoProvider serviceInfoProvider;
    @Mock ClientLogger clientLogger;
    @Mock ApplicationTopicService applicationTopicService;
    @Mock ApplicationClientHelperService appClientHelperService;
    @Mock AppMsgDeliveryStrategy appMsgDeliveryStrategy;

    @InjectMocks
    ApplicationPersistenceProcessorImpl processor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(processor, "pollDuration", 100L);
        ReflectionTestUtils.setField(processor, "packProcessingTimeout", 2000L);
        ReflectionTestUtils.setField(processor, "validateSharedTopicFilter", true);
    }

    @Test
    void isProcessorActive_whenThreadInterrupted_returnsFalseAndPreservesInterruptFlag() {
        Thread.currentThread().interrupt();
        try {
            boolean active = invokeIsProcessorActive();
            assertThat(active).isFalse();
            assertThat(Thread.currentThread().isInterrupted())
                    .as("isProcessorActive() must not clear the thread interrupt flag")
                    .isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void isProcessorActive_whenNotStoppedAndNotInterrupted_returnsTrue() {
        assertThat(invokeIsProcessorActive()).isTrue();
    }

    @Test
    void isProcessorActive_whenStoppedFlagSet_returnsFalse() {
        ReflectionTestUtils.setField(processor, "stopped", true);
        assertThat(invokeIsProcessorActive()).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void destroy_closesSharedSubscriptionConsumers() {
        ExecutorService mockExecutor = mock(ExecutorService.class);
        ReflectionTestUtils.setField(processor, "persistedMessageConsumerExecutor", mockExecutor);
        ReflectionTestUtils.setField(processor, "sharedSubscriptionConsumerExecutor", mockExecutor);

        TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishMsgProto>> consumer1 =
                mock(TbQueueControlledOffsetConsumer.class);
        TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishMsgProto>> consumer2 =
                mock(TbQueueControlledOffsetConsumer.class);

        ConcurrentMap<TopicSharedSubscription, TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishMsgProto>>>
                innerMap = new ConcurrentHashMap<>();
        innerMap.put(subscription("shared/topic/1"), consumer1);
        innerMap.put(subscription("shared/topic/2"), consumer2);
        sharedSubscriptionConsumers().put("client1", innerMap);

        processor.destroy();

        // OLD code would fail here; NEW code closes both consumers
        verify(consumer1).unsubscribeAndClose();
        verify(consumer2).unsubscribeAndClose();
    }

    @Test
    void destroy_cancelsFuturesInProcessingJobs() {
        ExecutorService mockExecutor = mock(ExecutorService.class);
        ReflectionTestUtils.setField(processor, "persistedMessageConsumerExecutor", mockExecutor);
        ReflectionTestUtils.setField(processor, "sharedSubscriptionConsumerExecutor", mockExecutor);

        Future<?> sharedFuture = mock(Future.class);
        ApplicationSharedSubscriptionJob job =
                new ApplicationSharedSubscriptionJob(subscription("shared/topic"), sharedFuture, false);
        sharedProcessingJobs().put("client1", new ArrayList<>(List.of(job)));

        Future<?> mainFuture = mock(Future.class);
        mainProcessingStates().put("client1", new ApplicationMainProcessingState(null, mainFuture));

        processor.destroy();

        verify(sharedFuture).cancel(false);
        verify(mainFuture).cancel(false);
    }

    @Test
    void processPubAck_whenMainContextHandlesPacket_doesNotQuerySharedContexts() {
        ApplicationPackProcessingCtx mainCtx = spy(new ApplicationPackProcessingCtx("client1"));
        doReturn(true).when(mainCtx).onPubAck(42);
        mainPackProcessingCtxMap().put("client1", mainCtx);

        processor.processPubAck("client1", 42);

        verify(mainCtx).onPubAck(42);
        assertThat(sharedPackProcessingCtxMap()).doesNotContainKey("client1");
    }

    @Test
    void processPubAck_whenNoMainContext_queriesSharedContexts() {
        ApplicationPackProcessingCtx sharedCtx = spy(new ApplicationPackProcessingCtx("client1"));
        doReturn(true).when(sharedCtx).onPubAck(99);

        Set<ApplicationSharedSubscriptionCtx> contexts = Sets.newConcurrentHashSet();
        contexts.add(new ApplicationSharedSubscriptionCtx(subscription("shared/topic"), sharedCtx));
        sharedPackProcessingCtxMap().put("client1", contexts);

        processor.processPubAck("client1", 99);

        verify(sharedCtx).onPubAck(99);
    }

    @Test
    void processPubAck_whenMainContextDoesNotHandlePacket_fallsBackToSharedContexts() {
        ApplicationPackProcessingCtx mainCtx = spy(new ApplicationPackProcessingCtx("client1"));
        doReturn(false).when(mainCtx).onPubAck(42);
        mainPackProcessingCtxMap().put("client1", mainCtx);

        ApplicationPackProcessingCtx sharedCtx = spy(new ApplicationPackProcessingCtx("client1"));
        doReturn(true).when(sharedCtx).onPubAck(42);
        Set<ApplicationSharedSubscriptionCtx> contexts = Sets.newConcurrentHashSet();
        contexts.add(new ApplicationSharedSubscriptionCtx(subscription("shared/topic"), sharedCtx));
        sharedPackProcessingCtxMap().put("client1", contexts);

        processor.processPubAck("client1", 42);

        verify(mainCtx).onPubAck(42);
        verify(sharedCtx).onPubAck(42);
    }

    @Test
    void processPubAck_whenFirstSharedContextHandlesPacket_doesNotQueryRemainingSharedContexts() {
        ApplicationPackProcessingCtx sharedCtx1 = spy(new ApplicationPackProcessingCtx("client1"));
        doReturn(true).when(sharedCtx1).onPubAck(42);
        ApplicationPackProcessingCtx sharedCtx2 = spy(new ApplicationPackProcessingCtx("client1"));

        // Put both in the set — ctx1 handles it, ctx2 must not be queried
        Set<ApplicationSharedSubscriptionCtx> contexts = Sets.newConcurrentHashSet();
        contexts.add(new ApplicationSharedSubscriptionCtx(subscription("shared/a"), sharedCtx1));
        contexts.add(new ApplicationSharedSubscriptionCtx(subscription("shared/b"), sharedCtx2));
        sharedPackProcessingCtxMap().put("client1", contexts);

        processor.processPubAck("client1", 42);

        verify(sharedCtx1).onPubAck(42);
        // sharedCtx2 is never queried once ctx1 handles the packet
        verify(sharedCtx2, never()).onPubAck(42);
    }

    @Test
    void processPubComp_whenMainContextHandlesPacket_doesNotQuerySharedContexts() {
        ApplicationPackProcessingCtx mainCtx = spy(new ApplicationPackProcessingCtx("client1"));
        doReturn(true).when(mainCtx).onPubComp(7);
        mainPackProcessingCtxMap().put("client1", mainCtx);

        processor.processPubComp("client1", 7);

        verify(mainCtx).onPubComp(7);
        assertThat(sharedPackProcessingCtxMap()).doesNotContainKey("client1");
    }

    @Test
    void processPubComp_whenMainContextDoesNotHandlePacket_fallsBackToSharedContexts() {
        ApplicationPackProcessingCtx mainCtx = spy(new ApplicationPackProcessingCtx("client1"));
        doReturn(false).when(mainCtx).onPubComp(7);
        mainPackProcessingCtxMap().put("client1", mainCtx);

        ApplicationPackProcessingCtx sharedCtx = spy(new ApplicationPackProcessingCtx("client1"));
        doReturn(true).when(sharedCtx).onPubComp(7);
        Set<ApplicationSharedSubscriptionCtx> contexts = Sets.newConcurrentHashSet();
        contexts.add(new ApplicationSharedSubscriptionCtx(subscription("shared/topic"), sharedCtx));
        sharedPackProcessingCtxMap().put("client1", contexts);

        processor.processPubComp("client1", 7);

        verify(mainCtx).onPubComp(7);
        verify(sharedCtx).onPubComp(7);
    }

    @Test
    void processPubComp_whenNoMainContext_queriesSharedContexts() {
        ApplicationPackProcessingCtx sharedCtx = spy(new ApplicationPackProcessingCtx("client1"));
        doReturn(true).when(sharedCtx).onPubComp(7);

        Set<ApplicationSharedSubscriptionCtx> contexts = Sets.newConcurrentHashSet();
        contexts.add(new ApplicationSharedSubscriptionCtx(subscription("shared/topic"), sharedCtx));
        sharedPackProcessingCtxMap().put("client1", contexts);

        processor.processPubComp("client1", 7);

        verify(sharedCtx).onPubComp(7);
    }

    @Test
    @SuppressWarnings("unchecked")
    void stopProcessingSharedSubscriptions_closesOnlyMatchingConsumers() {
        TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishMsgProto>> consumerToClose =
                mock(TbQueueControlledOffsetConsumer.class);
        TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishMsgProto>> consumerToKeep =
                mock(TbQueueControlledOffsetConsumer.class);

        TopicSharedSubscription subToRemove = subscription("remove/me");
        TopicSharedSubscription subToKeep = subscription("keep/me");

        ConcurrentMap<TopicSharedSubscription, TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishMsgProto>>>
                innerMap = new ConcurrentHashMap<>();
        innerMap.put(subToRemove, consumerToClose);
        innerMap.put(subToKeep, consumerToKeep);
        sharedSubscriptionConsumers().put("client1", innerMap);

        processor.stopProcessingSharedSubscriptions(clientSessionCtx("client1"), Set.of(subToRemove));

        verify(consumerToClose).unsubscribeAndClose();
        verify(consumerToKeep, never()).unsubscribeAndClose();
        assertThat(innerMap)
                .containsKey(subToKeep)
                .doesNotContainKey(subToRemove);
    }

    @Test
    void stopProcessingSharedSubscriptions_withEmptySubscriptionsSet_doesNothing() {
        assertThatNoException().isThrownBy(() ->
                processor.stopProcessingSharedSubscriptions(clientSessionCtx("client1"), Set.of()));
    }

    @Test
    void startProcessingSharedSubscriptions_withEmptySubscriptionsSet_doesNothing() {
        assertThatNoException().isThrownBy(() ->
                processor.startProcessingSharedSubscriptions(clientSessionCtx("client1"), Set.of()));
        assertThat(sharedProcessingJobs()).doesNotContainKey("client1");
    }

    @Test
    void stopProcessingPersistedMessages_cancelsFutureAndClearsApplicationProcessorStats() {
        Future<?> mockFuture = mock(Future.class);
        mainProcessingStates().put("client1", new ApplicationMainProcessingState(null, mockFuture));

        processor.stopProcessingPersistedMessages("client1");

        verify(mockFuture).cancel(false);
        verify(statsManager).clearApplicationProcessorStats("client1");
    }

    @Test
    void stopProcessingPersistedMessages_interruptsSharedSubscriptionJobs() {
        Future<?> sharedFuture = mock(Future.class);
        ApplicationSharedSubscriptionJob job =
                new ApplicationSharedSubscriptionJob(subscription("shared/topic"), sharedFuture, false);
        sharedProcessingJobs().put("client1", new ArrayList<>(List.of(job)));

        Future<?> mainFuture = mock(Future.class);
        mainProcessingStates().put("client1", new ApplicationMainProcessingState(null, mainFuture));

        processor.stopProcessingPersistedMessages("client1");

        assertThat(job.isInterrupted()).isTrue();
        verify(sharedFuture).cancel(false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void stopProcessingPersistedMessages_closesSharedSubscriptionConsumers() {
        TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishMsgProto>> consumer =
                mock(TbQueueControlledOffsetConsumer.class);
        ConcurrentMap<TopicSharedSubscription, TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishMsgProto>>>
                innerMap = new ConcurrentHashMap<>();
        innerMap.put(subscription("shared/topic"), consumer);
        sharedSubscriptionConsumers().put("client1", innerMap);

        Future<?> mainFuture = mock(Future.class);
        mainProcessingStates().put("client1", new ApplicationMainProcessingState(null, mainFuture));

        processor.stopProcessingPersistedMessages("client1");

        verify(consumer).unsubscribeAndClose();
        assertThat(sharedSubscriptionConsumers()).doesNotContainKey("client1");
    }

    @Test
    void stopProcessingPersistedMessages_removesClientFromPersistedMsgCtxMap() {
        Future<?> mainFuture = mock(Future.class);
        mainProcessingStates().put("client1", new ApplicationMainProcessingState(null, mainFuture));

        processor.stopProcessingPersistedMessages("client1");

        @SuppressWarnings("unchecked")
        ConcurrentMap<String, ?> persistedMsgCtxMap =
                (ConcurrentMap<String, ?>) ReflectionTestUtils.getField(processor, "persistedMsgCtxMap");
        assertThat(persistedMsgCtxMap).doesNotContainKey("client1");
    }

    @Test
    void cancelAndCollectJobs_returnsOnlyJobsMatchingSubscriptions() {
        Set<TopicSharedSubscription> subscriptions = Set.of(
                subscription("test/1"),
                subscription("test/3")
        );
        ApplicationSharedSubscriptionJob job1 = jobWithFuture("test/1");
        ApplicationSharedSubscriptionJob job2 = jobWithFuture("test/2"); // not in subscriptions
        ApplicationSharedSubscriptionJob job3 = jobWithFuture("test/3");
        List<ApplicationSharedSubscriptionJob> jobs = new ArrayList<>(List.of(job1, job2, job3));

        List<ApplicationSharedSubscriptionJob> cancelled =
                processor.cancelAndCollectJobs(subscriptions, "client1", jobs);

        assertThat(cancelled).hasSize(2).contains(job1, job3).doesNotContain(job2);
        assertThat(job1.isInterrupted()).isTrue();
        assertThat(job3.isInterrupted()).isTrue();
        assertThat(job2.isInterrupted()).isFalse();
    }

    @Test
    void cancelAndCollectJobs_withNullFutureOnJob_doesNotThrowNpe() {
        ApplicationSharedSubscriptionJob jobWithNullFuture =
                new ApplicationSharedSubscriptionJob(subscription("test/1"), null, false);
        List<ApplicationSharedSubscriptionJob> jobs = new ArrayList<>(List.of(jobWithNullFuture));

        assertThatNoException()
                .as("cancelJob() must not NPE when job.getFuture() is null")
                .isThrownBy(() ->
                        processor.cancelAndCollectJobs(Set.of(subscription("test/1")), "client1", jobs));

        assertThat(jobWithNullFuture.isInterrupted()).isTrue();
    }

    @Test
    void cancelAndCollectJobs_clearsSharedStatsForEachCancelledJob() {
        Set<TopicSharedSubscription> subscriptions = Set.of(
                subscription("test/1"),
                subscription("test/2")
        );
        List<ApplicationSharedSubscriptionJob> jobs = new ArrayList<>(List.of(
                jobWithFuture("test/1"),
                jobWithFuture("test/2"),
                jobWithFuture("test/3") // not in subscriptions — must NOT be cleared
        ));

        processor.cancelAndCollectJobs(subscriptions, "client1", jobs);

        verify(statsManager, times(2))
                .clearSharedApplicationProcessorStats(eq("client1"), any(TopicSharedSubscription.class));
        verify(statsManager, never())
                .clearSharedApplicationProcessorStats(eq("client1"), eq(subscription("test/3")));
    }

    @Test
    void cancelAndCollectJobs_withNoMatchingSubscriptions_returnsEmptyList() {
        List<ApplicationSharedSubscriptionJob> jobs = new ArrayList<>(List.of(
                jobWithFuture("test/1"),
                jobWithFuture("test/2")
        ));

        List<ApplicationSharedSubscriptionJob> cancelled =
                processor.cancelAndCollectJobs(Set.of(subscription("test/99")), "client1", jobs);

        assertThat(cancelled).isEmpty();
        jobs.forEach(j -> assertThat(j.isInterrupted()).isFalse());
    }

    // ===== Helpers =====

    private boolean invokeIsProcessorActive() {
        try {
            var method = ApplicationPersistenceProcessorImpl.class.getDeclaredMethod("isProcessorActive");
            method.setAccessible(true);
            return (Boolean) method.invoke(processor);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private ConcurrentMap<String, ApplicationPackProcessingCtx> mainPackProcessingCtxMap() {
        return (ConcurrentMap<String, ApplicationPackProcessingCtx>)
                ReflectionTestUtils.getField(processor, "mainPackProcessingCtxMap");
    }

    @SuppressWarnings("unchecked")
    private ConcurrentMap<String, Set<ApplicationSharedSubscriptionCtx>> sharedPackProcessingCtxMap() {
        return (ConcurrentMap<String, Set<ApplicationSharedSubscriptionCtx>>)
                ReflectionTestUtils.getField(processor, "sharedPackProcessingCtxMap");
    }

    @SuppressWarnings("unchecked")
    private ConcurrentMap<String, ConcurrentMap<TopicSharedSubscription,
            TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishMsgProto>>>> sharedSubscriptionConsumers() {
        return (ConcurrentMap<String, ConcurrentMap<TopicSharedSubscription,
                TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishMsgProto>>>>)
                ReflectionTestUtils.getField(processor, "sharedSubscriptionConsumers");
    }

    @SuppressWarnings("unchecked")
    private ConcurrentMap<String, List<ApplicationSharedSubscriptionJob>> sharedProcessingJobs() {
        return (ConcurrentMap<String, List<ApplicationSharedSubscriptionJob>>)
                ReflectionTestUtils.getField(processor, "sharedProcessingJobs");
    }

    @SuppressWarnings("unchecked")
    private ConcurrentMap<String, ApplicationMainProcessingState> mainProcessingStates() {
        return (ConcurrentMap<String, ApplicationMainProcessingState>)
                ReflectionTestUtils.getField(processor, "mainProcessingStates");
    }

    private TopicSharedSubscription subscription(String topicFilter) {
        return new TopicSharedSubscription(topicFilter, null);
    }

    private ApplicationSharedSubscriptionJob jobWithFuture(String topicFilter) {
        return new ApplicationSharedSubscriptionJob(subscription(topicFilter), mock(Future.class), false);
    }

    private ClientSessionCtx clientSessionCtx(String clientId) {
        ClientSessionCtx ctx = mock(ClientSessionCtx.class);
        lenient().when(ctx.getClientId()).thenReturn(clientId);
        return ctx;
    }
}
