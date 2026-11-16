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
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultEventLoop;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttDisconnectMsg;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCtxService;
import org.thingsboard.mqtt.broker.service.mqtt.will.LastWillService;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StandaloneShutdownCoordinatorTest {

    private final ClientSessionCtxService sessions = mock(ClientSessionCtxService.class);
    private final ClientMqttActorManager actors = mock(ClientMqttActorManager.class);
    private final LastWillService lastWills = mock(LastWillService.class);
    private final StandalonePublishMsgQueue dispatchStage = mock(StandalonePublishMsgQueue.class);
    private final StandaloneDeviceMsgQueueConsumer persistStage = mock(StandaloneDeviceMsgQueueConsumer.class);
    private final PipelineProperties properties = new PipelineProperties();
    private final PipelineBudget budget = mock(PipelineBudget.class);
    private final StandaloneConnectionAdmission admission = new StandaloneConnectionAdmission();

    private final Logger logger = (Logger) LoggerFactory.getLogger(StandaloneShutdownCoordinator.class);
    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();
    private Level previousLevel;

    @BeforeEach
    void captureLog() {
        previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        logged.start();
        logger.addAppender(logged);
        logger.setAdditive(false);
    }

    @AfterEach
    void releaseLog() {
        logger.detachAppender(logged);
        logger.setAdditive(true);
        logger.setLevel(previousLevel);
        logged.stop();
    }

    @Test
    void givenSessions_whenStop_thenEveryChannelIsPausedBeforeTheDispatchDrain() {
        ChannelConfig first = mock(ChannelConfig.class);
        ChannelConfig second = mock(ChannelConfig.class);
        List<ClientSessionCtx> sessionList = List.of(sessionOf(first), sessionOf(second));
        when(sessions.getAllClientSessionCtx()).thenReturn(sessionList);
        PartitionedWorkers<?> dispatch = drained();
        PartitionedWorkers<?> persist = drained();
        when(dispatchStage.workers()).thenReturn((PartitionedWorkers) dispatch);
        when(persistStage.workers()).thenReturn((PartitionedWorkers) persist);

        coordinator().stop();

        // the releases of the drain must not resume reading: resuming is stopped before reading is paused
        InOrder order = inOrder(budget, first, second, dispatch);
        order.verify(budget).stopResuming();
        order.verify(first).setAutoRead(false);
        order.verify(second).setAutoRead(false);
        order.verify(dispatch).drain(anyLong());
    }

    @Test
    void givenBothStages_whenStop_thenDispatchDrainsBeforePersist() {
        PartitionedWorkers<?> dispatch = drained();
        PartitionedWorkers<?> persist = drained();
        when(dispatchStage.workers()).thenReturn((PartitionedWorkers) dispatch);
        when(persistStage.workers()).thenReturn((PartitionedWorkers) persist);

        coordinator().stop();

        InOrder order = inOrder(dispatch, persist);
        order.verify(dispatch).drain(anyLong());
        order.verify(persist).drain(anyLong());
        assertThat(warnings()).isEmpty();
        assertThat(logged.list).filteredOn(event -> event.getLevel() == Level.INFO)
                .singleElement().satisfies(event -> assertThat(event.getFormattedMessage()).startsWith("The publish pipeline drained in "));
    }

    @Test
    void givenSlowDispatchDrain_whenStop_thenPersistDrainGetsOnlyWhatIsLeftOfTheSharedDeadline() {
        properties.getShutdown().setDrainTimeoutMs(5000);
        AtomicLong dispatchBudget = new AtomicLong();
        AtomicLong persistBudget = new AtomicLong();
        PartitionedWorkers<?> dispatch = drained();
        PartitionedWorkers<?> persist = drained();
        when(dispatch.drain(anyLong())).thenAnswer(invocation -> {
            dispatchBudget.set(invocation.getArgument(0));
            Thread.sleep(300);
            return true;
        });
        when(persist.drain(anyLong())).thenAnswer(invocation -> {
            persistBudget.set(invocation.getArgument(0));
            return true;
        });
        when(dispatchStage.workers()).thenReturn((PartitionedWorkers) dispatch);
        when(persistStage.workers()).thenReturn((PartitionedWorkers) persist);

        coordinator().stop();

        assertThat(dispatchBudget.get()).isBetween(1L, 5000L);
        // 300 ms went to the dispatch stage: the persist stage gets at most what the dispatch stage was given less that
        assertThat(persistBudget.get()).isBetween(0L, dispatchBudget.get() - 300);
    }

    @Test
    void givenBlockedHandlerAndShortTimeout_whenStop_thenReturnsInTimeAndLogsTheLeftover() throws Exception {
        properties.getShutdown().setDrainTimeoutMs(200);
        CountDownLatch blocked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        PartitionedWorkers<String> dispatch = new PartitionedWorkers<>("test-dispatch", 1, 0, 1);
        PartitionedWorkers<String> persist = idleWorkers();
        try {
            dispatch.start(batch -> {
                blocked.countDown();
                await(release);
            });
            persist.start(batch -> {
            });
            dispatch.submit("t", "in-handler");
            assertThat(blocked.await(10, TimeUnit.SECONDS)).isTrue();
            dispatch.submit("t", "waiting-1");
            dispatch.submit("t", "waiting-2");
            when(dispatchStage.workers()).thenReturn((PartitionedWorkers) dispatch);
            when(persistStage.workers()).thenReturn((PartitionedWorkers) persist);

            long startNanos = System.nanoTime();
            coordinator().stop();
            long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

            // the timeout is 200 ms; the bound is generous for a loaded machine yet far below the 4 s default
            assertThat(tookMs).isLessThan(2000);
            assertThat(warnings()).hasSize(1);
            assertThat(warnings().get(0).getFormattedMessage())
                    .contains("Dispatch stage: 2 queued and a handler still running")
                    .contains("Persist stage: drained");
        } finally {
            release.countDown();
            dispatch.stop();
            persist.stop();
        }
    }

    @Test
    void givenAChannelThatFails_whenStop_thenTheOthersArePausedAndTheDrainsStillRun() {
        ChannelConfig failing = mock(ChannelConfig.class);
        doThrow(new IllegalStateException("boom")).when(failing).setAutoRead(false);
        ChannelConfig healthy = mock(ChannelConfig.class);
        List<ClientSessionCtx> sessionList = List.of(sessionOf(failing), sessionOf(healthy));
        when(sessions.getAllClientSessionCtx()).thenReturn(sessionList);
        PartitionedWorkers<?> dispatch = drained();
        PartitionedWorkers<?> persist = drained();
        when(dispatchStage.workers()).thenReturn((PartitionedWorkers) dispatch);
        when(persistStage.workers()).thenReturn((PartitionedWorkers) persist);

        coordinator().stop();

        verify(healthy).setAutoRead(false);
        verify(dispatch).drain(anyLong());
        verify(persist).drain(anyLong());
    }

    @Test
    void givenADrainThatThrows_whenStop_thenItDoesNotThrow() {
        PartitionedWorkers<?> dispatch = drained();
        PartitionedWorkers<?> persist = drained();
        when(dispatch.drain(anyLong())).thenThrow(new IllegalStateException("boom"));
        when(dispatchStage.workers()).thenReturn((PartitionedWorkers) dispatch);
        when(persistStage.workers()).thenReturn((PartitionedWorkers) persist);

        coordinator().stop();

        verify(persist).drain(anyLong());
    }

    @Test
    void givenASessionWithoutChannel_whenStop_thenItIsSkipped() {
        ClientSessionCtx noChannel = mock(ClientSessionCtx.class);
        when(sessions.getAllClientSessionCtx()).thenReturn(List.of(noChannel));
        PartitionedWorkers<?> dispatch = drained();
        PartitionedWorkers<?> persist = drained();
        when(dispatchStage.workers()).thenReturn((PartitionedWorkers) dispatch);
        when(persistStage.workers()).thenReturn((PartitionedWorkers) persist);

        coordinator().stop();

        verify(dispatch).drain(anyLong());
    }

    @Test
    void givenSessionsWithAndWithoutAWill_whenStop_thenOnlyTheWillHoldersAreDisconnectedAsServerShuttingDownBeforeTheDrain() {
        ChannelConfig withWillConfig = mock(ChannelConfig.class);
        ChannelConfig withoutWillConfig = mock(ChannelConfig.class);
        ClientSessionCtx withWill = willHolder("with-will", withWillConfig);
        ClientSessionCtx withoutWill = sessionOf(withoutWillConfig);
        when(withoutWill.getClientId()).thenReturn("without-will");
        when(withoutWill.getSessionId()).thenReturn(UUID.randomUUID());
        when(sessions.getAllClientSessionCtx()).thenReturn(List.of(withWill, withoutWill));
        disconnectsAtOnce(withWill);
        when(lastWills.dueLastWillsHandled()).thenReturn(CompletableFuture.completedFuture(null));
        PartitionedWorkers<?> dispatch = drained();
        PartitionedWorkers<?> persist = drained();
        when(dispatchStage.workers()).thenReturn((PartitionedWorkers) dispatch);
        when(persistStage.workers()).thenReturn((PartitionedWorkers) persist);

        coordinator().stop();

        ArgumentCaptor<MqttDisconnectMsg> disconnect = ArgumentCaptor.forClass(MqttDisconnectMsg.class);
        verify(actors).disconnectAfterQueuedMsgs(eq("with-will"), disconnect.capture());
        assertThat(disconnect.getValue().getSessionId()).isEqualTo(withWill.getSessionId());
        assertThat(disconnect.getValue().getReason().getType()).isEqualTo(DisconnectReasonType.ON_SERVER_SHUTTING_DOWN);
        verify(actors, never()).disconnectAfterQueuedMsgs(eq("without-will"), any());
        // queued behind what the client sent before the pause, so a DISCONNECT 0x00 it sent still discards the will
        verify(actors, never()).disconnect(any(), any());
        // reads are paused first, so a will holder sends nothing more; its will enters the pipeline before the drain
        InOrder order = inOrder(budget, withWillConfig, withoutWillConfig, actors, lastWills, dispatch);
        order.verify(budget).stopResuming();
        order.verify(withWillConfig).setAutoRead(false);
        order.verify(withoutWillConfig).setAutoRead(false);
        order.verify(actors).disconnectAfterQueuedMsgs(eq("with-will"), any());
        order.verify(lastWills).dueLastWillsHandled();
        order.verify(dispatch).drain(anyLong());
        assertThat(warnings()).isEmpty();
        assertThat(infos()).first().satisfies(event -> assertThat(event.getFormattedMessage())
                .startsWith("Disconnected 1 clients that have a Last Will in "));
    }

    @Test
    void givenNoWillHolders_whenStop_thenNoOneIsDisconnectedAndTheWillsAreNotAwaited() {
        ClientSessionCtx withoutWill = sessionOf(mock(ChannelConfig.class));
        when(withoutWill.getSessionId()).thenReturn(UUID.randomUUID());
        when(sessions.getAllClientSessionCtx()).thenReturn(List.of(withoutWill));
        PartitionedWorkers<?> idle = drained();
        when(dispatchStage.workers()).thenReturn((PartitionedWorkers) idle);
        when(persistStage.workers()).thenReturn((PartitionedWorkers) idle);

        coordinator().stop();

        verifyNoInteractions(actors);
        verify(lastWills, never()).dueLastWillsHandled();
    }

    @Test
    void givenAnActorThatDisconnectsLater_whenStop_thenTheDueWillsAreAwaitedOnlyAfterItHasDisconnected() {
        ClientSessionCtx withWill = willHolder("with-will", mock(ChannelConfig.class));
        when(sessions.getAllClientSessionCtx()).thenReturn(List.of(withWill));
        AtomicBoolean taken = willTakenFlagOf(withWill);
        doAnswer(invocation -> {
            Thread actor = new Thread(() -> {
                sleep(200);
                taken.set(true);
            });
            actor.start();
            return null;
        }).when(actors).disconnectAfterQueuedMsgs(eq("with-will"), any());
        AtomicBoolean takenWhenAwaited = new AtomicBoolean();
        when(lastWills.dueLastWillsHandled()).thenAnswer(invocation -> {
            takenWhenAwaited.set(taken.get());
            return CompletableFuture.completedFuture(null);
        });
        PartitionedWorkers<?> idle = drained();
        when(dispatchStage.workers()).thenReturn((PartitionedWorkers) idle);
        when(persistStage.workers()).thenReturn((PartitionedWorkers) idle);

        coordinator().stop();

        // the disconnect hands a due will to the will scheduler before it takes the will from the store
        assertThat(takenWhenAwaited).isTrue();
        assertThat(warnings()).isEmpty();
    }

    @Test
    void givenAChannelClosedBeforeTheWillIsTaken_whenStop_thenTheDueWillsAreAwaitedOnlyAfterTheWillIsTaken() {
        ClientSessionCtx withWill = willHolder("with-will", mock(ChannelConfig.class));
        when(sessions.getAllClientSessionCtx()).thenReturn(List.of(withWill));
        // closing the channel says nothing about the will: anyone can set the flag, the client's own close among them
        when(withWill.isCloseInitiated()).thenReturn(true);
        AtomicBoolean taken = willTakenFlagOf(withWill);
        doAnswer(invocation -> {
            Thread actor = new Thread(() -> {
                sleep(200);
                taken.set(true);
            });
            actor.start();
            return null;
        }).when(actors).disconnectAfterQueuedMsgs(eq("with-will"), any());
        AtomicBoolean takenWhenAwaited = new AtomicBoolean();
        when(lastWills.dueLastWillsHandled()).thenAnswer(invocation -> {
            takenWhenAwaited.set(taken.get());
            return CompletableFuture.completedFuture(null);
        });
        PartitionedWorkers<?> idle = drained();
        when(dispatchStage.workers()).thenReturn((PartitionedWorkers) idle);
        when(persistStage.workers()).thenReturn((PartitionedWorkers) idle);

        coordinator().stop();

        assertThat(takenWhenAwaited).isTrue();
        assertThat(warnings()).isEmpty();
    }

    @Test
    void givenAWillHolderThatIsNeverDisconnected_whenStop_thenTheWillStepEndsAtTheDeadlineAndTheDrainStillRuns() {
        properties.getShutdown().setDrainTimeoutMs(200);
        ClientSessionCtx withWill = willHolder("with-will", mock(ChannelConfig.class));
        when(sessions.getAllClientSessionCtx()).thenReturn(List.of(withWill));
        when(lastWills.dueLastWillsHandled()).thenReturn(new CompletableFuture<>());
        PartitionedWorkers<?> dispatch = drained();
        PartitionedWorkers<?> persist = drained();
        when(dispatchStage.workers()).thenReturn((PartitionedWorkers) dispatch);
        when(persistStage.workers()).thenReturn((PartitionedWorkers) persist);

        long tookMs = timedStop();

        // the timeout is 200 ms; the bound is generous for a loaded machine yet far below the 4 s default
        assertThat(tookMs).isLessThan(2000);
        verify(dispatch).drain(anyLong());
        verify(persist).drain(anyLong());
        assertThat(warnings()).first().satisfies(event -> assertThat(event.getFormattedMessage())
                .contains("Clients still disconnecting: 1 of 1"));
    }

    @Test
    void givenDueWillsThatAreNeverHandled_whenStop_thenTheWillStepEndsAtTheDeadlineAndTheDrainStillRuns() {
        properties.getShutdown().setDrainTimeoutMs(200);
        ClientSessionCtx withWill = willHolder("with-will", mock(ChannelConfig.class));
        when(sessions.getAllClientSessionCtx()).thenReturn(List.of(withWill));
        disconnectsAtOnce(withWill);
        when(lastWills.dueLastWillsHandled()).thenReturn(new CompletableFuture<>());
        PartitionedWorkers<?> dispatch = drained();
        PartitionedWorkers<?> persist = drained();
        when(dispatchStage.workers()).thenReturn((PartitionedWorkers) dispatch);
        when(persistStage.workers()).thenReturn((PartitionedWorkers) persist);

        long tookMs = timedStop();

        assertThat(tookMs).isLessThan(2000);
        verify(dispatch).drain(anyLong());
        verify(persist).drain(anyLong());
        assertThat(warnings()).first().satisfies(event -> assertThat(event.getFormattedMessage())
                .contains("Clients still disconnecting: 0 of 1")
                .contains("Due wills: not all handed to the pipeline"));
    }

    @Test
    void givenASlowWillStep_whenStop_thenTheDrainGetsOnlyWhatIsLeftOfTheSharedDeadline() {
        properties.getShutdown().setDrainTimeoutMs(5000);
        ClientSessionCtx withWill = willHolder("with-will", mock(ChannelConfig.class));
        when(sessions.getAllClientSessionCtx()).thenReturn(List.of(withWill));
        disconnectsAtOnce(withWill);
        when(lastWills.dueLastWillsHandled()).thenAnswer(invocation -> {
            Thread.sleep(300);
            return CompletableFuture.completedFuture(null);
        });
        AtomicLong dispatchBudget = new AtomicLong();
        PartitionedWorkers<?> dispatch = drained();
        when(dispatch.drain(anyLong())).thenAnswer(invocation -> {
            dispatchBudget.set(invocation.getArgument(0));
            return true;
        });
        PartitionedWorkers<?> persist = drained();
        when(dispatchStage.workers()).thenReturn((PartitionedWorkers) dispatch);
        when(persistStage.workers()).thenReturn((PartitionedWorkers) persist);

        coordinator().stop();

        // 300 ms went to the will step: the dispatch stage gets at most the shared deadline less that
        assertThat(dispatchBudget.get()).isBetween(0L, 5000L - 300);
    }

    @Test
    void givenADisconnectThatFails_whenStop_thenTheOtherWillHoldersAreStillDisconnectedAndTheDrainRuns() {
        ClientSessionCtx failing = willHolder("failing", mock(ChannelConfig.class));
        ClientSessionCtx healthy = willHolder("healthy", mock(ChannelConfig.class));
        when(sessions.getAllClientSessionCtx()).thenReturn(List.of(failing, healthy));
        doThrow(new IllegalStateException("boom")).when(actors).disconnectAfterQueuedMsgs(eq("failing"), any());
        disconnectsAtOnce(healthy);
        when(lastWills.dueLastWillsHandled()).thenReturn(CompletableFuture.completedFuture(null));
        PartitionedWorkers<?> dispatch = drained();
        PartitionedWorkers<?> persist = drained();
        when(dispatchStage.workers()).thenReturn((PartitionedWorkers) dispatch);
        when(persistStage.workers()).thenReturn((PartitionedWorkers) persist);

        coordinator().stop();

        verify(actors).disconnectAfterQueuedMsgs(eq("healthy"), any());
        verify(dispatch).drain(anyLong());
        verify(persist).drain(anyLong());
    }

    @Test
    void givenAWillStepThatThrows_whenStop_thenItDoesNotThrowAndTheDrainRuns() {
        ClientSessionCtx withWill = willHolder("with-will", mock(ChannelConfig.class));
        when(sessions.getAllClientSessionCtx()).thenReturn(List.of(withWill));
        disconnectsAtOnce(withWill);
        when(lastWills.dueLastWillsHandled()).thenThrow(new IllegalStateException("boom"));
        PartitionedWorkers<?> dispatch = drained();
        PartitionedWorkers<?> persist = drained();
        when(dispatchStage.workers()).thenReturn((PartitionedWorkers) dispatch);
        when(persistStage.workers()).thenReturn((PartitionedWorkers) persist);

        coordinator().stop();

        verify(dispatch).drain(anyLong());
        verify(persist).drain(anyLong());
        assertThat(warnings()).anySatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.ERROR));
    }

    @Test
    void givenChannelsOnOneEventLoop_whenStop_thenOneFenceRunsThereAfterTheyArePausedAndBeforeTheWillHoldersAreDisconnected() {
        EventLoop eventLoop = mock(EventLoop.class);
        when(eventLoop.submit(any(Runnable.class))).thenAnswer(invocation -> ImmediateEventExecutor.INSTANCE.newSucceededFuture(null));
        ChannelConfig withWillConfig = mock(ChannelConfig.class);
        ChannelConfig withoutWillConfig = mock(ChannelConfig.class);
        ClientSessionCtx withWill = willHolder("with-will", withWillConfig, eventLoop);
        ClientSessionCtx withoutWill = sessionOf(withoutWillConfig, eventLoop);
        when(sessions.getAllClientSessionCtx()).thenReturn(List.of(withWill, withoutWill));
        disconnectsAtOnce(withWill);
        when(lastWills.dueLastWillsHandled()).thenReturn(CompletableFuture.completedFuture(null));
        PartitionedWorkers<?> idle = drained();
        when(dispatchStage.workers()).thenReturn((PartitionedWorkers) idle);
        when(persistStage.workers()).thenReturn((PartitionedWorkers) idle);

        coordinator().stop();

        InOrder order = inOrder(withWillConfig, withoutWillConfig, eventLoop, actors);
        order.verify(withWillConfig).setAutoRead(false);
        order.verify(withoutWillConfig).setAutoRead(false);
        order.verify(eventLoop).submit(any(Runnable.class));
        order.verify(actors).disconnectAfterQueuedMsgs(eq("with-will"), any());
        verify(eventLoop, times(1)).submit(any(Runnable.class));
        assertThat(warnings()).isEmpty();
    }

    @Test
    void givenAReadInProgress_whenStop_thenTheWillHoldersAreDisconnectedOnlyAfterItHasFinished() throws Exception {
        DefaultEventLoop eventLoop = new DefaultEventLoop();
        try {
            // a read in progress runs on the event loop: channelRead tells the client actor from there
            AtomicBoolean readFinished = new AtomicBoolean();
            CountDownLatch reading = new CountDownLatch(1);
            eventLoop.execute(() -> {
                reading.countDown();
                sleep(200);
                readFinished.set(true);
            });
            assertThat(reading.await(10, TimeUnit.SECONDS)).isTrue();
            ClientSessionCtx withWill = willHolder("with-will", mock(ChannelConfig.class), eventLoop);
            when(sessions.getAllClientSessionCtx()).thenReturn(List.of(withWill));
            AtomicBoolean taken = willTakenFlagOf(withWill);
            AtomicBoolean readFinishedWhenDisconnected = new AtomicBoolean();
            doAnswer(invocation -> {
                readFinishedWhenDisconnected.set(readFinished.get());
                taken.set(true);
                return null;
            }).when(actors).disconnectAfterQueuedMsgs(eq("with-will"), any());
            when(lastWills.dueLastWillsHandled()).thenReturn(CompletableFuture.completedFuture(null));
            PartitionedWorkers<?> idle = drained();
            when(dispatchStage.workers()).thenReturn((PartitionedWorkers) idle);
            when(persistStage.workers()).thenReturn((PartitionedWorkers) idle);

            coordinator().stop();

            assertThat(readFinishedWhenDisconnected).isTrue();
            assertThat(warnings()).isEmpty();
        } finally {
            eventLoop.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        }
    }

    @Test
    void givenAnEventLoopThatNeverRunsTheFence_whenStop_thenTheDrainStillRunsWithinTheDeadline() throws Exception {
        properties.getShutdown().setDrainTimeoutMs(200);
        DefaultEventLoop eventLoop = new DefaultEventLoop();
        CountDownLatch release = new CountDownLatch(1);
        try {
            CountDownLatch blocked = new CountDownLatch(1);
            eventLoop.execute(() -> {
                blocked.countDown();
                await(release);
            });
            assertThat(blocked.await(10, TimeUnit.SECONDS)).isTrue();
            ClientSessionCtx session = sessionOf(mock(ChannelConfig.class), eventLoop);
            when(sessions.getAllClientSessionCtx()).thenReturn(List.of(session));
            PartitionedWorkers<?> dispatch = drained();
            PartitionedWorkers<?> persist = drained();
            when(dispatchStage.workers()).thenReturn((PartitionedWorkers) dispatch);
            when(persistStage.workers()).thenReturn((PartitionedWorkers) persist);

            long tookMs = timedStop();

            // the timeout is 200 ms; the bound is generous for a loaded machine yet far below the 4 s default
            assertThat(tookMs).isLessThan(2000);
            verify(dispatch).drain(anyLong());
            verify(persist).drain(anyLong());
            assertThat(warnings()).first().satisfies(event -> assertThat(event.getFormattedMessage())
                    .startsWith("The reads in progress on 1 of 1 event loops did not finish within 200 ms"));
        } finally {
            release.countDown();
            eventLoop.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        }
    }

    @Test
    void whenStop_thenNewConnectsAreRefusedBeforeTheSessionsAreTaken() {
        AtomicBoolean admittedWhenTaken = new AtomicBoolean(true);
        when(sessions.getAllClientSessionCtx()).thenAnswer(invocation -> {
            admittedWhenTaken.set(admission.tryAdmit(UUID.randomUUID()));
            return List.of();
        });
        PartitionedWorkers<?> idle = drained();
        when(dispatchStage.workers()).thenReturn((PartitionedWorkers) idle);
        when(persistStage.workers()).thenReturn((PartitionedWorkers) idle);

        coordinator().stop();

        assertThat(admittedWhenTaken).isFalse();
    }

    @Test
    void givenAConnectInProgress_whenStop_thenItsSessionIsTakenAfterItRegistered() throws Exception {
        UUID connecting = UUID.randomUUID();
        assertThat(admission.tryAdmit(connecting)).isTrue();
        List<ClientSessionCtx> registered = new CopyOnWriteArrayList<>();
        when(sessions.getAllClientSessionCtx()).thenAnswer(invocation -> List.copyOf(registered));
        ChannelConfig lateConfig = mock(ChannelConfig.class);
        ClientSessionCtx late = willHolder("late", lateConfig);
        disconnectsAtOnce(late);
        when(lastWills.dueLastWillsHandled()).thenReturn(CompletableFuture.completedFuture(null));
        PartitionedWorkers<?> idle = drained();
        when(dispatchStage.workers()).thenReturn((PartitionedWorkers) idle);
        when(persistStage.workers()).thenReturn((PartitionedWorkers) idle);
        // the client actor registers the session, then ends the connect
        Thread actor = new Thread(() -> {
            sleep(200);
            registered.add(late);
            admission.release(connecting);
        });
        actor.start();

        coordinator().stop();
        actor.join(TimeUnit.SECONDS.toMillis(10));

        verify(lateConfig).setAutoRead(false);
        verify(actors).disconnectAfterQueuedMsgs(eq("late"), any());
        assertThat(warnings()).isEmpty();
    }

    @Test
    void givenAConnectThatNeverEnds_whenStop_thenTheWaitEndsAtTheDeadlineAndTheDrainStillRuns() {
        properties.getShutdown().setDrainTimeoutMs(200);
        admission.tryAdmit(UUID.randomUUID());
        PartitionedWorkers<?> dispatch = drained();
        PartitionedWorkers<?> persist = drained();
        when(dispatchStage.workers()).thenReturn((PartitionedWorkers) dispatch);
        when(persistStage.workers()).thenReturn((PartitionedWorkers) persist);

        long tookMs = timedStop();

        // the timeout is 200 ms; the bound is generous for a loaded machine yet far below the 4 s default
        assertThat(tookMs).isLessThan(2000);
        verify(dispatch).drain(anyLong());
        verify(persist).drain(anyLong());
        assertThat(warnings()).first().satisfies(event -> assertThat(event.getFormattedMessage())
                .contains("1 still being accepted"));
    }

    @Test
    void whenCreated_thenItStopsBeforeEveryOtherBean() {
        var coordinator = coordinator();

        assertThat(coordinator.getPhase()).isEqualTo(Integer.MAX_VALUE);
        assertThat(coordinator.isAutoStartup()).isTrue();
        assertThat(coordinator.isRunning()).isFalse();
        coordinator.start();
        assertThat(coordinator.isRunning()).isTrue();
        PartitionedWorkers<?> idle = drained();
        when(dispatchStage.workers()).thenReturn((PartitionedWorkers) idle);
        when(persistStage.workers()).thenReturn((PartitionedWorkers) idle);
        coordinator.stop();
        assertThat(coordinator.isRunning()).isFalse();
    }

    private StandaloneShutdownCoordinator coordinator() {
        return new StandaloneShutdownCoordinator(sessions, actors, lastWills, budget, dispatchStage, persistStage, properties, admission);
    }

    /**
     * A connected session with a Last Will that no disconnect has taken from the will store yet.
     */
    private ClientSessionCtx willHolder(String clientId, ChannelConfig config) {
        return willHolder(clientId, config, null);
    }

    private ClientSessionCtx willHolder(String clientId, ChannelConfig config, EventLoop eventLoop) {
        ClientSessionCtx session = sessionOf(config, eventLoop);
        UUID sessionId = UUID.randomUUID();
        when(session.getClientId()).thenReturn(clientId);
        when(session.getSessionId()).thenReturn(sessionId);
        willTakenFlagOf(session);
        return session;
    }

    /**
     * Set once the disconnect has taken the session's will from the will store: a due will is on the scheduler by then.
     */
    private AtomicBoolean willTakenFlagOf(ClientSessionCtx session) {
        AtomicBoolean taken = new AtomicBoolean();
        when(lastWills.hasLastWill(session.getSessionId())).thenAnswer(invocation -> !taken.get());
        return taken;
    }

    private void disconnectsAtOnce(ClientSessionCtx session) {
        String clientId = session.getClientId();
        AtomicBoolean taken = willTakenFlagOf(session);
        doAnswer(invocation -> {
            taken.set(true);
            return null;
        }).when(actors).disconnectAfterQueuedMsgs(eq(clientId), any());
    }

    private long timedStop() {
        long startNanos = System.nanoTime();
        coordinator().stop();
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    private static PartitionedWorkers<?> drained() {
        PartitionedWorkers<?> workers = mock(PartitionedWorkers.class);
        when(workers.drain(anyLong())).thenReturn(true);
        return workers;
    }

    private static PartitionedWorkers<String> idleWorkers() {
        return new PartitionedWorkers<>("test-idle", 1, 0, 1);
    }

    private static ClientSessionCtx sessionOf(ChannelConfig config) {
        return sessionOf(config, null);
    }

    private static ClientSessionCtx sessionOf(ChannelConfig config, EventLoop eventLoop) {
        ClientSessionCtx session = mock(ClientSessionCtx.class);
        ChannelHandlerContext channelCtx = mock(ChannelHandlerContext.class);
        Channel channel = mock(Channel.class);
        when(session.getChannel()).thenReturn(channelCtx);
        when(channelCtx.channel()).thenReturn(channel);
        when(channel.config()).thenReturn(config);
        when(channel.eventLoop()).thenReturn(eventLoop);
        return session;
    }

    private List<ILoggingEvent> infos() {
        return logged.list.stream().filter(event -> event.getLevel() == Level.INFO).toList();
    }

    private List<ILoggingEvent> warnings() {
        return logged.list.stream().filter(event -> event.getLevel().isGreaterOrEqual(Level.WARN)).toList();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
