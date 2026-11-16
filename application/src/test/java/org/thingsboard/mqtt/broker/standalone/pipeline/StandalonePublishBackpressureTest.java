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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;
import org.thingsboard.mqtt.broker.common.stats.DefaultStatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsType;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StandalonePublishBackpressureTest {

    private static final long UNLIMITED_BYTES = Long.MAX_VALUE / 200;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final StatsFactory statsFactory = new DefaultStatsFactory(registry);
    // pauses at 8 queued messages and resumes at 5
    private final RacingBudget budget = new RacingBudget();
    private final StandalonePublishBackpressure backpressure = new StandalonePublishBackpressure(budget, statsFactory);

    @Test
    void givenBelowPause_whenPublish_thenNothingIsPaused() {
        Connection connection = connection();
        acquire(7);

        backpressure.onPublish(connection.session());

        verifyNoInteractions(connection.config());
        assertThat(pausedConnections()).isZero();
        assertThat(publishPauses()).isZero();
    }

    @Test
    void givenAtPause_whenPublish_thenOnlyThePublishingChannelIsPaused() {
        Connection publisher = connection();
        Connection other = connection();
        acquire(8);

        backpressure.onPublish(publisher.session());

        verify(publisher.config()).setAutoRead(false);
        verify(publisher.config(), never()).setAutoRead(true);
        verifyNoInteractions(other.config());
        assertThat(pausedConnections()).isEqualTo(1);
        assertThat(publishPauses()).isEqualTo(1);
    }

    @Test
    void givenBudgetFellBetweenTheLevelsAfterAPause_whenAnotherConnectionPublishes_thenItIsPausedUntilTheResume() {
        Connection paused = connection();
        Connection publishing = connection();
        acquire(8);
        backpressure.onPublish(paused.session());
        // below the pause level, not yet down to the resume level
        release(1);

        // left reading, the connection could keep the budget between the levels and the paused one would never resume
        backpressure.onPublish(publishing.session());

        verify(publishing.config()).setAutoRead(false);
        assertThat(pausedConnections()).isEqualTo(2);
        assertThat(publishPauses()).isEqualTo(2);

        release(2);

        for (Connection connection : new Connection[]{paused, publishing}) {
            verify(connection.config()).setAutoRead(true);
            verify(connection.channel()).read();
        }
        assertThat(pausedConnections()).isZero();
    }

    @Test
    void givenBudgetResumedAndBackBetweenTheLevels_whenPublish_thenNothingIsPaused() {
        Connection connection = connection();
        acquire(8);
        release(3);
        // above the resume level again, still below the pause level
        acquire(2);

        backpressure.onPublish(connection.session());

        verifyNoInteractions(connection.config());
        assertThat(pausedConnections()).isZero();
    }

    @Test
    void givenPausedChannels_whenBudgetFallsToResume_thenEachGetsAutoReadAndOneRead() {
        Connection first = connection();
        Connection second = connection();
        acquire(8);
        backpressure.onPublish(first.session());
        backpressure.onPublish(second.session());

        release(2);
        verify(first.config(), never()).setAutoRead(true);
        verify(second.config(), never()).setAutoRead(true);

        release(1);

        for (Connection connection : new Connection[]{first, second}) {
            InOrder order = inOrder(connection.config(), connection.channel());
            order.verify(connection.config()).setAutoRead(false);
            order.verify(connection.config()).setAutoRead(true);
            order.verify(connection.channel()).read();
        }
        assertThat(pausedConnections()).isZero();
        assertThat(publishPauses()).isEqualTo(2);
    }

    @Test
    void givenChannelClosedWhilePaused_whenResume_thenSkippedAndForgotten() {
        Connection connection = connection();
        acquire(8);
        backpressure.onPublish(connection.session());
        when(connection.channel().isActive()).thenReturn(false);

        release(3);

        verify(connection.config(), never()).setAutoRead(true);
        verify(connection.channel(), never()).read();
        assertThat(pausedConnections()).isZero();
    }

    @Test
    void givenPausedSession_whenItPublishesAgain_thenItIsRecordedOnce() {
        Connection connection = connection();
        acquire(8);

        backpressure.onPublish(connection.session());
        backpressure.onPublish(connection.session());

        verify(connection.config()).setAutoRead(false);
        assertThat(pausedConnections()).isEqualTo(1);
        assertThat(publishPauses()).isEqualTo(1);

        release(3);

        verify(connection.config()).setAutoRead(true);
        verify(connection.channel()).read();
    }

    @Test
    void givenResumeBetweenThePauseCheckAndThePause_whenPublish_thenTheSessionIsNotLeftPaused() {
        Connection connection = connection();
        acquire(8);
        // the resume runs while no session is recorded yet, so it cannot resume this one
        budget.afterNextPauseCheck(() -> release(3));

        backpressure.onPublish(connection.session());

        InOrder order = inOrder(connection.config(), connection.channel());
        order.verify(connection.config()).setAutoRead(false);
        order.verify(connection.config()).setAutoRead(true);
        order.verify(connection.channel()).read();
        assertThat(pausedConnections()).isZero();
    }

    @Test
    void givenResumingStopped_whenBudgetFallsToResume_thenReadingStaysPaused() {
        Connection connection = connection();
        acquire(8);
        backpressure.onPublish(connection.session());

        budget.stopResuming();
        release(8);

        verify(connection.config(), never()).setAutoRead(true);
        verify(connection.channel(), never()).read();
    }

    @Test
    void givenResumingStoppedBetweenThePauseCheckAndThePause_whenPublish_thenReadingStaysPaused() {
        Connection connection = connection();
        acquire(8);
        budget.afterNextPauseCheck(() -> {
            budget.stopResuming();
            release(3);
        });

        backpressure.onPublish(connection.session());

        verify(connection.config()).setAutoRead(false);
        verify(connection.config(), never()).setAutoRead(true);
        verify(connection.channel(), never()).read();
    }

    @Test
    void givenResumeFailsForOneChannel_whenResume_thenTheOthersResumeAndTheFailedOneIsForgotten() {
        Logger logger = (Logger) LoggerFactory.getLogger(StandalonePublishBackpressure.class);
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        logged.start();
        logger.addAppender(logged);
        logger.setAdditive(false);
        try {
            Connection failing = connection();
            Connection healthy = connection();
            doThrow(new IllegalStateException("channel failure")).when(failing.config()).setAutoRead(true);
            acquire(8);
            backpressure.onPublish(failing.session());
            backpressure.onPublish(healthy.session());

            release(3);

            verify(healthy.config()).setAutoRead(true);
            verify(healthy.channel()).read();
            assertThat(pausedConnections()).isZero();
            assertThat(logged.list).singleElement().satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
        } finally {
            logger.detachAppender(logged);
            logger.setAdditive(true);
        }
    }

    private void acquire(int messages) {
        for (int i = 0; i < messages; i++) {
            assertThat(budget.tryAcquire(1)).isTrue();
        }
    }

    private void release(int messages) {
        for (int i = 0; i < messages; i++) {
            budget.release(1);
        }
    }

    private double pausedConnections() {
        return registry.get("pausedConnections").gauge().value();
    }

    private double publishPauses() {
        return registry.get(StatsType.PUBLISH_PAUSES.getPrintName()).counter().count();
    }

    private static Connection connection() {
        ChannelConfig config = mock(ChannelConfig.class);
        Channel channel = mock(Channel.class);
        when(channel.config()).thenReturn(config);
        when(channel.isActive()).thenReturn(true);
        ChannelHandlerContext channelCtx = mock(ChannelHandlerContext.class);
        when(channelCtx.channel()).thenReturn(channel);
        ClientSessionCtx session = new ClientSessionCtx();
        session.setChannel(channelCtx);
        return new Connection(session, channel, config);
    }

    private record Connection(ClientSessionCtx session, Channel channel, ChannelConfig config) {
    }

    /**
     * Runs a hook right after the next pause check returns, to place a resume between the check and the pause.
     */
    private static class RacingBudget extends PipelineBudget {

        private Runnable afterNextPauseCheck;

        RacingBudget() {
            super(10, UNLIMITED_BYTES, 80, 50);
        }

        void afterNextPauseCheck(Runnable hook) {
            afterNextPauseCheck = hook;
        }

        @Override
        public boolean paused() {
            boolean paused = super.paused();
            Runnable hook = afterNextPauseCheck;
            afterNextPauseCheck = null;
            if (hook != null) {
                hook.run();
            }
            return paused;
        }

    }

}
