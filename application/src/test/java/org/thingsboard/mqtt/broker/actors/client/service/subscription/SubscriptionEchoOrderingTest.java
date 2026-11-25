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
package org.thingsboard.mqtt.broker.actors.client.service.subscription;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.thingsboard.mqtt.broker.actors.client.messages.SubscriptionChangedEventMsg;
import org.thingsboard.mqtt.broker.common.data.subscription.ClientTopicSubscription;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.service.stats.timer.SubscriptionTimerStats;
import org.thingsboard.mqtt.broker.service.subscription.*;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptionCacheService;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptionProcessor;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Replays durable Kafka order after local writes have already updated routing. */
public class SubscriptionEchoOrderingTest {
    private static final String CLIENT = "5151df62-2081-537c-a0b3-cc55a09526ee";
    private static final String TOPIC = "devices/" + CLIENT + "/req/automation";
    private static final Set<TopicSubscription> SUBS = Set.of(new ClientTopicSubscription(TOPIC, 1));
    private ClientSubscriptionServiceImpl service;
    private SubscriptionService routing;
    private SubscriptionPersistenceService persistence;
    private ClientSubscriptionChangesCallback callback;
    private final ArrayDeque<SubscriptionChangedEventMsg> mailbox = new ArrayDeque<>();
    private final AtomicLong writeOffset = new AtomicLong(10);

    @Before
    public void setup() {
        StatsManager stats = mock(StatsManager.class);
        when(stats.createSubscriptionSizeGauge()).thenReturn(new AtomicInteger());
        when(stats.createSubscriptionTrieNodesGauge()).thenReturn(new AtomicLong());
        when(stats.getSubscriptionTimerStats()).thenReturn(mock(SubscriptionTimerStats.class));
        routing = new SubscriptionServiceImpl(new ConcurrentMapSubscriptionTrie<>(stats), stats);
        persistence = mock(SubscriptionPersistenceService.class);
        ClientMqttActorManager actors = mock(ClientMqttActorManager.class);
        doAnswer(invocation -> {
            mailbox.add(new SubscriptionChangedEventMsg(Set.copyOf(invocation.getArgument(1)), invocation.getArgument(2), false));
            return null;
        }).when(actors).processSubscriptionsChanged(eq(CLIENT), anySet(), anyLong());
        doAnswer(invocation -> {
            mailbox.add(new SubscriptionChangedEventMsg(Set.of(), -1, true));
            return null;
        }).when(actors).processSubscriptionWriteCompleted(CLIENT);
        doAnswer(invocation -> {
            ((SubscriptionWriteCallback) invocation.getArgument(2)).onSuccess(writeOffset.incrementAndGet());
            return null;
        }).when(persistence).persistClientSubscriptionsAsync(eq(CLIENT), anySet(), any());
        service = new ClientSubscriptionServiceImpl(persistence, routing,
                mock(SharedSubscriptionProcessor.class), mock(SharedSubscriptionCacheService.class), stats, actors);
        service.init(Map.of());
        ClientSubscriptionConsumer consumer = mock(ClientSubscriptionConsumer.class);
        service.startListening(consumer);
        ArgumentCaptor<ClientSubscriptionChangesCallback> captor = ArgumentCaptor.forClass(ClientSubscriptionChangesCallback.class);
        verify(consumer).listen(captor.capture());
        callback = captor.getValue();
    }

    private void consume(String source, Set<TopicSubscription> snapshot) {
        callback.accept(CLIENT, source, snapshot, source.equals("broker-A") ? 11 : 10);
        flush();
    }

    private void flush() {
        SubscriptionChangesManager manager = new SubscriptionChangesManagerImpl(service);
        while (!mailbox.isEmpty()) {
            manager.processSubscriptionChangedEvent(CLIENT, mailbox.remove());
        }
    }

    @Test
    public void delayedRemoteClearMustNotEraseLaterDurableLocalSubscription() {
        // Broker B has persisted clean-start cleanup. Before A consumes it, the new
        // connection subscribes on A and its complete snapshot is persisted later.
        service.subscribeAndPersist(CLIENT, SUBS);
        assertEquals(1, routing.getSubscriptions(TOPIC).size());
        consume("broker-B", Set.of());
        consume("broker-A", SUBS);
        assertEquals("Every broker must converge to the last durable snapshot", SUBS, service.getClientSubscriptions(CLIENT));
        assertEquals("A command must still have a matching route", 1, routing.getSubscriptions(TOPIC).size());
        verify(persistence, times(1)).persistClientSubscriptionsAsync(eq(CLIENT), anySet(), any());
    }

    @Test
    public void delayedRemoteSubscribeMustNotRestoreLaterDurableLocalUnsubscribe() {
        service.subscribeInternally(CLIENT, SUBS);
        service.unsubscribeAndPersist(CLIENT, Set.of(TOPIC));
        consume("broker-B", SUBS);
        consume("broker-A", Set.of());
        assertTrue(service.getClientSubscriptions(CLIENT).isEmpty());
        assertTrue(routing.getSubscriptions(TOPIC).isEmpty());
        verify(persistence, times(1)).persistClientSubscriptionsAsync(eq(CLIENT), anySet(), any());
    }

    @Test
    public void remoteSnapshotsAlreadyConvergeWithoutEcho() {
        consume("broker-B", SUBS);
        assertEquals(1, routing.getSubscriptions(TOPIC).size());
        consume("broker-B", Set.of());
        assertTrue(routing.getSubscriptions(TOPIC).isEmpty());
        verifyNoInteractions(persistence);
    }

    @Test
    public void localEchoMustNotCreateAdditionalPersistence() {
        service.subscribeAndPersist(CLIENT, SUBS);
        consume("broker-A", SUBS);
        assertEquals(1, service.getClientSubscriptionsCount());
        assertEquals(1, routing.getSubscriptions(TOPIC).size());
        verify(persistence, times(1)).persistClientSubscriptionsAsync(eq(CLIENT), anySet(), any());
    }
    @Test
    public void secondLocalSubscribeDuringDelayedClearRetainsBothFilters() {
        reset(persistence);
        List<SubscriptionWriteCallback> callbacks = new ArrayList<>();
        doAnswer(invocation -> { callbacks.add(invocation.getArgument(2)); return null; })
                .when(persistence).persistClientSubscriptionsAsync(eq(CLIENT), anySet(), any());
        service.subscribeAndPersist(CLIENT, SUBS);
        consume("broker-B", Set.of());
        // The old snapshot must not modify the base of this second mutation.
        String other = "devices/" + CLIENT + "/req/read";
        service.subscribeAndPersist(CLIENT, Set.of(new ClientTopicSubscription(other, 1)));
        callbacks.get(1).onSuccess(12);
        flush();
        callbacks.get(0).onSuccess(11);
        flush();
        callback.accept(CLIENT, "broker-A", SUBS, 11);
        flush();
        assertEquals(1, routing.getSubscriptions(TOPIC).size());
        assertEquals(1, routing.getSubscriptions(other).size());
        Set<TopicSubscription> both = Set.of(new ClientTopicSubscription(TOPIC, 1), new ClientTopicSubscription(other, 1));
        callback.accept(CLIENT, "broker-A", both, 12);
        flush();
        assertEquals(2, service.getClientSubscriptionsCount());
    }

    @Test
    public void producerAckMayFollowItsOwnConsumedSnapshot() {
        reset(persistence);
        ArgumentCaptor<SubscriptionWriteCallback> written = ArgumentCaptor.forClass(SubscriptionWriteCallback.class);
        service.subscribeAndPersist(CLIENT, SUBS);
        verify(persistence).persistClientSubscriptionsAsync(eq(CLIENT), anySet(), written.capture());
        consume("broker-B", Set.of());
        consume("broker-A", SUBS);
        assertEquals(1, routing.getSubscriptions(TOPIC).size());
        written.getValue().onSuccess(11);
        flush();
        assertEquals(1, routing.getSubscriptions(TOPIC).size());
    }

    @Test
    public void synchronousPersistenceFailureRollsBackRoutingAndAllowsNextWrite() {
        reset(persistence);
        BasicCallback result = mock(BasicCallback.class);
        doThrow(new IllegalStateException("fixture send failure"))
                .when(persistence).persistClientSubscriptionsAsync(eq(CLIENT), anySet(), any());
        service.subscribeAndPersist(CLIENT, SUBS, result);
        flush();
        verify(result).onFailure(any(IllegalStateException.class));
        verify(result, never()).onSuccess();
        assertTrue(routing.getSubscriptions(TOPIC).isEmpty());
        reset(persistence);
        doAnswer(invocation -> { ((SubscriptionWriteCallback) invocation.getArgument(2)).onSuccess(11); return null; })
                .when(persistence).persistClientSubscriptionsAsync(eq(CLIENT), anySet(), any());
        service.subscribeAndPersist(CLIENT, SUBS);
        consume("broker-A", SUBS);
        assertEquals(1, routing.getSubscriptions(TOPIC).size());
    }

    @Test
    public void failedUnsubscribeRestoresAcknowledgedRoute() {
        service.subscribeInternally(CLIENT, SUBS);
        reset(persistence);
        doAnswer(invocation -> {
            ((SubscriptionWriteCallback) invocation.getArgument(2)).onFailure(new IllegalStateException("fixture failure"));
            return null;
        }).when(persistence).persistClientSubscriptionsAsync(eq(CLIENT), anySet(), any());
        service.unsubscribeAndPersist(CLIENT, Set.of(TOPIC));
        flush();
        assertEquals(1, routing.getSubscriptions(TOPIC).size());
        assertEquals(SUBS, service.getClientSubscriptions(CLIENT));
    }

    @Test
    public void newerRemoteQosIsAppliedDespiteFilterEquality() {
        service.subscribeAndPersist(CLIENT, SUBS);
        callback.accept(CLIENT, "broker-B", Set.of(new ClientTopicSubscription(TOPIC, 2)), 12);
        flush();
        assertEquals(2, service.getClientSubscriptions(CLIENT).iterator().next().getQos());
    }

    @Test
    public void missingKafkaOffsetMustNotAcknowledgeSubscriptionSuccess() {
        reset(persistence);
        BasicCallback result = mock(BasicCallback.class);
        doAnswer(invocation -> {
            ((SubscriptionWriteCallback) invocation.getArgument(2)).onSuccess(-1);
            return null;
        }).when(persistence).persistClientSubscriptionsAsync(eq(CLIENT), anySet(), any());
        service.subscribeAndPersist(CLIENT, SUBS, result);
        flush();
        verify(result).onFailure(any(IllegalStateException.class));
        verify(result, never()).onSuccess();
        assertTrue(routing.getSubscriptions(TOPIC).isEmpty());
    }

    @Test
    public void administrationWriteCannotInterleaveWithSnapshotApplication() throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var writeStarted = new java.util.concurrent.CountDownLatch(1);
        var writeFinished = new java.util.concurrent.CountDownLatch(1);
        var error = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        Thread reconcile = new Thread(() -> {
            try {
                service.reconcileSubscriptionUpdate(CLIENT, new SubscriptionChangedEventMsg(Set.of(), 10, false), snapshot -> {
                    entered.countDown();
                    try {
                        if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                            throw new AssertionError("test release timeout");
                        }
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(interrupted);
                    }
                    service.clearSubscriptionsInternally(CLIENT);
                });
            } catch (Throwable failure) { error.set(failure); }
        });
        Thread administration = new Thread(() -> {
            try {
                writeStarted.countDown();
                service.subscribeAndPersist(CLIENT, SUBS);
            } catch (Throwable failure) { error.set(failure); }
            finally { writeFinished.countDown(); }
        });
        reconcile.start();
        try {
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
            administration.start();
            assertTrue(writeStarted.await(5, java.util.concurrent.TimeUnit.SECONDS));
            assertFalse("write must wait until the old snapshot finishes applying",
                    writeFinished.await(100, java.util.concurrent.TimeUnit.MILLISECONDS));
        } finally {
            release.countDown();
            reconcile.join(5000);
            administration.join(5000);
        }
        assertFalse(reconcile.isAlive());
        assertFalse(administration.isAlive());
        assertNull(error.get());
        flush();
        assertEquals(1, routing.getSubscriptions(TOPIC).size());
    }

}
