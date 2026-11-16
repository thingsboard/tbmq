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

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.common.stats.DefaultStatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;
import org.thingsboard.mqtt.broker.service.processing.backpressure.KeepAliveProbe;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * [MQTT-3.1.2-22]: the keep-alive stays enforced on a paused connection, which is read once before it is closed.
 */
class StandalonePublishBackpressureKeepAliveTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final StatsFactory statsFactory = new DefaultStatsFactory(registry);
    // pauses at 8 queued messages and resumes at 5
    private final PipelineBudget budget = new PipelineBudget(10, Long.MAX_VALUE / 200, 80, 50);
    private final StandalonePublishBackpressure backpressure = new StandalonePublishBackpressure(budget, statsFactory);

    @Test
    void givenSessionNeverPaused_whenProbe_thenClose() {
        Connection connection = connection();

        assertThat(backpressure.probe(connection.sessionId())).isEqualTo(KeepAliveProbe.CLOSE);
        assertThat(backpressure.probe(UUID.randomUUID())).isEqualTo(KeepAliveProbe.CLOSE);

        verify(connection.channel(), never()).read();
    }

    @Test
    void givenPausedSession_whenProbe_thenWaitAfterOneReadAndThenCloseWithoutAnotherRead() {
        Connection connection = pausedConnection();

        assertThat(backpressure.probe(connection.sessionId())).isEqualTo(KeepAliveProbe.WAIT);
        verify(connection.channel()).read();

        assertThat(backpressure.probe(connection.sessionId())).isEqualTo(KeepAliveProbe.CLOSE);
        verify(connection.channel()).read();
    }

    @Test
    void givenProbedSessionThatIsClosed_whenProbeAgain_thenItIsNoLongerHeldPaused() {
        Connection connection = pausedConnection();
        backpressure.probe(connection.sessionId());
        backpressure.probe(connection.sessionId());

        assertThat(pausedConnections()).isZero();
        assertThat(backpressure.probe(connection.sessionId())).isEqualTo(KeepAliveProbe.CLOSE);
    }

    @Test
    void givenDataArrivedAfterThePause_whenProbe_thenAliveWithoutRead() {
        Connection connection = pausedConnection();

        backpressure.onChannelRead(connection.session());

        assertThat(backpressure.probe(connection.sessionId())).isEqualTo(KeepAliveProbe.ALIVE);
        verify(connection.channel(), never()).read();
        // the mark was taken: the next probe starts over with a read
        assertThat(backpressure.probe(connection.sessionId())).isEqualTo(KeepAliveProbe.WAIT);
        verify(connection.channel()).read();
        assertThat(pausedConnections()).isEqualTo(1);
    }

    @Test
    void givenDataArrivedAfterTheProbe_whenProbe_thenAliveAndTheProbedMarkIsCleared() {
        Connection connection = pausedConnection();
        assertThat(backpressure.probe(connection.sessionId())).isEqualTo(KeepAliveProbe.WAIT);

        backpressure.onChannelRead(connection.session());

        assertThat(backpressure.probe(connection.sessionId())).isEqualTo(KeepAliveProbe.ALIVE);
        assertThat(backpressure.probe(connection.sessionId())).isEqualTo(KeepAliveProbe.WAIT);
        verify(connection.channel(), times(2)).read();
    }

    @Test
    void givenDataOnAnotherSession_whenProbe_thenThisOneIsNotAlive() {
        Connection connection = pausedConnection();
        Connection other = pausedConnection();

        backpressure.onChannelRead(other.session());

        assertThat(backpressure.probe(connection.sessionId())).isEqualTo(KeepAliveProbe.WAIT);
        assertThat(backpressure.probe(other.sessionId())).isEqualTo(KeepAliveProbe.ALIVE);
    }

    @Test
    void givenDataOnASessionThatIsNotPaused_whenItIsPausedLater_thenTheDataDoesNotCount() {
        Connection connection = connection();
        for (int i = 0; i < 8; i++) {
            assertThat(budget.tryAcquire(1)).isTrue();
        }

        backpressure.onChannelRead(connection.session());
        backpressure.onPublish(connection.session());

        assertThat(backpressure.probe(connection.sessionId())).isEqualTo(KeepAliveProbe.WAIT);
    }

    @Test
    void givenResumingStopped_whenProbe_thenWaitWithoutReadForEveryProbe() {
        Connection connection = pausedConnection();
        budget.stopResuming();

        assertThat(backpressure.probe(connection.sessionId())).isEqualTo(KeepAliveProbe.WAIT);
        assertThat(backpressure.probe(connection.sessionId())).isEqualTo(KeepAliveProbe.WAIT);
        assertThat(backpressure.probe(connection.sessionId())).isEqualTo(KeepAliveProbe.WAIT);

        verify(connection.channel(), never()).read();
        assertThat(pausedConnections()).isEqualTo(1);
    }

    @Test
    void givenProbedSession_whenResumingStopsBeforeTheNextProbe_thenItStaysWaitingInsteadOfClosing() {
        Connection connection = pausedConnection();
        backpressure.probe(connection.sessionId());

        budget.stopResuming();

        assertThat(backpressure.probe(connection.sessionId())).isEqualTo(KeepAliveProbe.WAIT);
    }

    @Test
    void givenSessionResumed_whenProbe_thenClose() {
        Connection connection = pausedConnection();
        backpressure.probe(connection.sessionId());
        backpressure.onChannelRead(connection.session());

        for (int i = 0; i < 3; i++) {
            budget.release(1);
        }

        verify(connection.channel(), times(2)).read(); // the probe's read and the resume's
        assertThat(pausedConnections()).isZero();
        assertThat(backpressure.probe(connection.sessionId())).isEqualTo(KeepAliveProbe.CLOSE);
    }

    @Test
    void givenSessionPausedAgainAfterAResume_whenProbe_thenTheOldMarksAreGone() {
        Connection connection = pausedConnection();
        backpressure.probe(connection.sessionId());
        backpressure.onChannelRead(connection.session());
        for (int i = 0; i < 3; i++) {
            budget.release(1);
        }
        for (int i = 0; i < 3; i++) {
            assertThat(budget.tryAcquire(1)).isTrue();
        }

        backpressure.onPublish(connection.session());

        assertThat(backpressure.probe(connection.sessionId())).isEqualTo(KeepAliveProbe.WAIT);
    }

    @Test
    void givenInactiveChannel_whenProbe_thenCloseAndForgotten() {
        Connection connection = pausedConnection();
        when(connection.channel().isActive()).thenReturn(false);

        assertThat(backpressure.probe(connection.sessionId())).isEqualTo(KeepAliveProbe.CLOSE);

        verify(connection.channel(), never()).read();
        assertThat(pausedConnections()).isZero();
    }

    @Test
    void givenReadFails_whenProbe_thenWaitAndTheNextProbeCloses() {
        Connection connection = pausedConnection();
        doThrow(new IllegalStateException("channel failure")).when(connection.channel()).read();

        assertThat(backpressure.probe(connection.sessionId())).isEqualTo(KeepAliveProbe.WAIT);
        assertThat(backpressure.probe(connection.sessionId())).isEqualTo(KeepAliveProbe.CLOSE);
    }

    private Connection pausedConnection() {
        Connection connection = connection();
        // tops the budget up to the pause level (8 of 10) once; later calls add nothing
        while (!budget.abovePause()) {
            assertThat(budget.tryAcquire(1)).isTrue();
        }
        backpressure.onPublish(connection.session());
        return connection;
    }

    private double pausedConnections() {
        return registry.get("pausedConnections").gauge().value();
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
        return new Connection(session, channel);
    }

    private record Connection(ClientSessionCtx session, Channel channel) {

        UUID sessionId() {
            return session.getSessionId();
        }

    }

}
