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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCtxService;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StandaloneShutdownCoordinatorTest {

    private final ClientSessionCtxService sessions = mock(ClientSessionCtxService.class);
    private final StandalonePublishMsgQueue dispatchStage = mock(StandalonePublishMsgQueue.class);
    private final StandaloneDeviceMsgQueueConsumer persistStage = mock(StandaloneDeviceMsgQueueConsumer.class);
    private final PipelineProperties properties = new PipelineProperties();

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

        InOrder order = inOrder(first, second, dispatch);
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
        return new StandaloneShutdownCoordinator(sessions, dispatchStage, persistStage, properties);
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
        ClientSessionCtx session = mock(ClientSessionCtx.class);
        ChannelHandlerContext channelCtx = mock(ChannelHandlerContext.class);
        Channel channel = mock(Channel.class);
        when(session.getChannel()).thenReturn(channelCtx);
        when(channelCtx.channel()).thenReturn(channel);
        when(channel.config()).thenReturn(config);
        return session;
    }

    private List<ILoggingEvent> warnings() {
        return logged.list.stream().filter(event -> event.getLevel().isGreaterOrEqual(Level.WARN)).toList();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
