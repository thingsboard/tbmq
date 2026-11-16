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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * [MQTT-3.1.2-22]: the keep-alive stays enforced on a paused connection, which is read once before it is closed, and
 * read every half keep-alive before that, so that a live client's PINGREQ is answered.
 * The times passed to {@code probe} stand for the keep-alive's last packet time of the session; every step that must
 * come later than another is separated from it by a few milliseconds, or by moving the clock forward.
 */
class StandalonePublishBackpressureKeepAliveTest {

    private static final long UNLIMITED_BYTES = Long.MAX_VALUE / 200;
    // half the keep-alive of a session; the margin keeps a step below it whatever real time passes meanwhile
    private static final long READ_INTERVAL_MS = 60_000;
    private static final long MARGIN_MS = 10_000;
    private static final long TICK_MS = 1_000;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final StatsFactory statsFactory = new DefaultStatsFactory(registry);
    // pauses at 8 queued messages and resumes at 5
    private final ParkingBudget budget = new ParkingBudget();
    // the system clock, until a test moves it forward
    private final ForwardClock clock = new ForwardClock();
    private final StandalonePublishBackpressure backpressure =
            new StandalonePublishBackpressure(budget, statsFactory, clock);

    @Test
    void givenSessionNeverPaused_whenProbe_thenClose() {
        Connection connection = connection();

        assertThat(backpressure.probe(connection.sessionId(), now())).isEqualTo(KeepAliveProbe.CLOSE);
        assertThat(backpressure.probe(UUID.randomUUID(), now())).isEqualTo(KeepAliveProbe.CLOSE);

        verify(connection.channel(), never()).read();
    }

    @Test
    void givenPausedSession_whenProbe_thenWaitAfterOneReadAndThenCloseWithoutAnotherRead() {
        Connection connection = pausedConnection();
        long lastPacket = packetTime();

        assertThat(backpressure.probe(connection.sessionId(), lastPacket)).isEqualTo(KeepAliveProbe.WAIT);
        verify(connection.channel()).read();

        assertThat(backpressure.probe(connection.sessionId(), lastPacket)).isEqualTo(KeepAliveProbe.CLOSE);
        verify(connection.channel()).read();
    }

    @Test
    void givenCloseForAProbedSession_whenProbeAgain_thenItStaysPausedForTheResumeAndCloseAgain() {
        Connection connection = pausedConnection();
        long lastPacket = packetTime();
        backpressure.probe(connection.sessionId(), lastPacket);
        assertThat(backpressure.probe(connection.sessionId(), lastPacket)).isEqualTo(KeepAliveProbe.CLOSE);

        // the keep-alive may not close it after all (its refresh lost a race), so a resume must still find it
        assertThat(pausedConnections()).isEqualTo(1);
        assertThat(backpressure.probe(connection.sessionId(), lastPacket)).isEqualTo(KeepAliveProbe.CLOSE);
        release(3);
        verify(connection.config()).setAutoRead(true);
        assertThat(pausedConnections()).isZero();
    }

    @Test
    void givenDataReadAfterTheLastPacket_whenProbe_thenAliveWithoutReadAndTheNextDeadlineProbesAgain() {
        Connection connection = pausedConnection();
        long lastPacket = packetTime();
        backpressure.onChannelRead(connection.session());

        assertThat(backpressure.probe(connection.sessionId(), lastPacket)).isEqualTo(KeepAliveProbe.ALIVE);
        verify(connection.channel(), never()).read();

        // the keep-alive refreshed the session: the data is spent, so the next deadline starts with a read
        long refreshed = packetTime();
        assertThat(backpressure.probe(connection.sessionId(), refreshed)).isEqualTo(KeepAliveProbe.WAIT);
        verify(connection.channel()).read();
        assertThat(pausedConnections()).isEqualTo(1);
    }

    @Test
    void givenDataReadAfterTheProbe_whenProbe_thenAlive() {
        Connection connection = pausedConnection();
        long lastPacket = packetTime();
        assertThat(backpressure.probe(connection.sessionId(), lastPacket)).isEqualTo(KeepAliveProbe.WAIT);
        later();

        backpressure.onChannelRead(connection.session());

        assertThat(backpressure.probe(connection.sessionId(), lastPacket)).isEqualTo(KeepAliveProbe.ALIVE);
        verify(connection.channel()).read();
    }

    // the sequence of the review: probe, the PINGREQ it reads, the session counting it, a client that dies after it
    @Test
    void givenDataCountedBySession_whenNextDeadlineProbes_thenFreshReadAndThenClose() {
        Connection connection = pausedConnection();
        long first = packetTime();
        assertThat(backpressure.probe(connection.sessionId(), first)).isEqualTo(KeepAliveProbe.WAIT);
        later();
        backpressure.onChannelRead(connection.session());
        // the actor counted the packet: the session's last packet time is now later than the data and the probe
        long counted = packetTime();

        assertThat(backpressure.probe(connection.sessionId(), counted)).isEqualTo(KeepAliveProbe.WAIT);
        verify(connection.channel(), times(2)).read();
        assertThat(backpressure.probe(connection.sessionId(), counted)).isEqualTo(KeepAliveProbe.CLOSE);
        verify(connection.channel(), times(2)).read();
    }

    @Test
    void givenDataReadBeforeTheProbeButAlreadyCounted_whenProbe_thenNotAlive() {
        Connection connection = pausedConnection();
        later();
        backpressure.onChannelRead(connection.session());
        long counted = packetTime();

        assertThat(backpressure.probe(connection.sessionId(), counted)).isEqualTo(KeepAliveProbe.WAIT);
        verify(connection.channel()).read();
    }

    @Test
    void givenProbeSpentForAnOlderDeadline_whenProbe_thenFreshWaitAndRead() {
        Connection connection = pausedConnection();
        long first = packetTime();
        backpressure.probe(connection.sessionId(), first);
        // the session was refreshed after that probe, by data this back-pressure did not see
        long refreshed = packetTime();

        assertThat(backpressure.probe(connection.sessionId(), refreshed)).isEqualTo(KeepAliveProbe.WAIT);

        verify(connection.channel(), times(2)).read();
    }

    @Test
    void givenDataOnAnotherSession_whenProbe_thenThisOneIsNotAlive() {
        Connection connection = pausedConnection();
        Connection other = pausedConnection();
        long lastPacket = packetTime();

        backpressure.onChannelRead(other.session());

        assertThat(backpressure.probe(connection.sessionId(), lastPacket)).isEqualTo(KeepAliveProbe.WAIT);
        assertThat(backpressure.probe(other.sessionId(), lastPacket)).isEqualTo(KeepAliveProbe.ALIVE);
    }

    @Test
    void givenDataOnASessionThatIsNotPaused_whenItIsPausedLater_thenTheDataDoesNotCount() {
        Connection connection = connection();
        acquire(8);
        long lastPacket = now();
        later();

        backpressure.onChannelRead(connection.session());
        backpressure.onPublish(connection.session());

        assertThat(backpressure.probe(connection.sessionId(), lastPacket)).isEqualTo(KeepAliveProbe.WAIT);
    }

    @Test
    void givenResumingStopped_whenProbe_thenWaitWithoutReadForEveryProbe() {
        Connection connection = pausedConnection();
        long lastPacket = packetTime();
        budget.stopResuming();

        assertThat(backpressure.probe(connection.sessionId(), lastPacket)).isEqualTo(KeepAliveProbe.WAIT);
        assertThat(backpressure.probe(connection.sessionId(), lastPacket)).isEqualTo(KeepAliveProbe.WAIT);
        assertThat(backpressure.probe(connection.sessionId(), packetTime())).isEqualTo(KeepAliveProbe.WAIT);

        verify(connection.channel(), never()).read();
        assertThat(pausedConnections()).isEqualTo(1);
    }

    @Test
    void givenProbedSession_whenResumingStopsBeforeTheNextProbe_thenItStaysWaitingInsteadOfClosing() {
        Connection connection = pausedConnection();
        long lastPacket = packetTime();
        backpressure.probe(connection.sessionId(), lastPacket);

        budget.stopResuming();

        assertThat(backpressure.probe(connection.sessionId(), lastPacket)).isEqualTo(KeepAliveProbe.WAIT);
    }

    @Test
    void givenResumingStoppedAndSessionNotPaused_whenProbe_thenClose() {
        Connection connection = connection();
        budget.stopResuming();

        assertThat(backpressure.probe(connection.sessionId(), now())).isEqualTo(KeepAliveProbe.CLOSE);
    }

    @Test
    void givenSessionResumed_whenProbe_thenClose() {
        Connection connection = pausedConnection();
        long lastPacket = packetTime();
        backpressure.probe(connection.sessionId(), lastPacket);
        later();
        backpressure.onChannelRead(connection.session());

        release(3);

        verify(connection.channel(), times(2)).read(); // the probe's read and the resume's
        assertThat(pausedConnections()).isZero();
        assertThat(backpressure.probe(connection.sessionId(), lastPacket)).isEqualTo(KeepAliveProbe.CLOSE);
    }

    @Test
    void givenSessionPausedAgainAfterAResume_whenProbe_thenTheOldMarksAreGone() {
        Connection connection = pausedConnection();
        long lastPacket = packetTime();
        backpressure.probe(connection.sessionId(), lastPacket);
        backpressure.onChannelRead(connection.session());
        release(3);
        acquire(3);

        backpressure.onPublish(connection.session());

        assertThat(backpressure.probe(connection.sessionId(), lastPacket)).isEqualTo(KeepAliveProbe.WAIT);
    }

    @Test
    void givenInactiveChannel_whenProbe_thenCloseWithoutRead() {
        Connection connection = pausedConnection();
        when(connection.channel().isActive()).thenReturn(false);

        assertThat(backpressure.probe(connection.sessionId(), packetTime())).isEqualTo(KeepAliveProbe.CLOSE);

        verify(connection.channel(), never()).read();
    }

    @Test
    void givenReadFails_whenProbe_thenWaitAndTheNextProbeCloses() {
        Connection connection = pausedConnection();
        doThrow(new IllegalStateException("channel failure")).when(connection.channel()).read();
        long lastPacket = packetTime();

        assertThat(backpressure.probe(connection.sessionId(), lastPacket)).isEqualTo(KeepAliveProbe.WAIT);
        assertThat(backpressure.probe(connection.sessionId(), lastPacket)).isEqualTo(KeepAliveProbe.CLOSE);
    }

    @Test
    void givenPausedSession_whenReadIfDueBeforeTheInterval_thenNoRead() {
        Connection connection = pausedConnection();
        clock.advance(READ_INTERVAL_MS - MARGIN_MS);

        backpressure.readIfDue(connection.sessionId(), READ_INTERVAL_MS);

        verify(connection.channel(), never()).read();
    }

    @Test
    void givenPausedSession_whenTheIntervalPassed_thenOneReadAndNoOtherBeforeTheNextInterval() {
        Connection connection = pausedConnection();
        clock.advance(READ_INTERVAL_MS);

        backpressure.readIfDue(connection.sessionId(), READ_INTERVAL_MS);
        backpressure.readIfDue(connection.sessionId(), READ_INTERVAL_MS);
        verify(connection.channel()).read();

        clock.advance(READ_INTERVAL_MS - MARGIN_MS);
        backpressure.readIfDue(connection.sessionId(), READ_INTERVAL_MS);
        verify(connection.channel()).read();

        clock.advance(MARGIN_MS);
        backpressure.readIfDue(connection.sessionId(), READ_INTERVAL_MS);
        verify(connection.channel(), times(2)).read();
        assertThat(pausedConnections()).isEqualTo(1);
    }

    @Test
    void givenProbeRead_whenReadIfDueBeforeAnIntervalPassedSinceIt_thenNoRead() {
        Connection connection = pausedConnection();
        clock.advance(READ_INTERVAL_MS - MARGIN_MS);
        assertThat(backpressure.probe(connection.sessionId(), packetTime())).isEqualTo(KeepAliveProbe.WAIT);
        verify(connection.channel()).read();

        clock.advance(READ_INTERVAL_MS - MARGIN_MS);
        backpressure.readIfDue(connection.sessionId(), READ_INTERVAL_MS);

        verify(connection.channel()).read();
    }

    @Test
    void givenResumingStopped_whenReadIfDue_thenNoRead() {
        Connection connection = pausedConnection();
        budget.stopResuming();
        clock.advance(READ_INTERVAL_MS);

        backpressure.readIfDue(connection.sessionId(), READ_INTERVAL_MS);

        verify(connection.channel(), never()).read();
    }

    @Test
    void givenSessionNotPaused_whenReadIfDue_thenNoRead() {
        Connection connection = connection();
        Connection resumed = pausedConnection();
        release(3);
        clock.advance(READ_INTERVAL_MS);

        backpressure.readIfDue(connection.sessionId(), READ_INTERVAL_MS);
        backpressure.readIfDue(resumed.sessionId(), READ_INTERVAL_MS);
        backpressure.readIfDue(UUID.randomUUID(), READ_INTERVAL_MS);

        verify(connection.channel(), never()).read();
        verify(resumed.channel()).read(); // the resume's own read
    }

    @Test
    void givenInactiveChannel_whenReadIfDue_thenNoRead() {
        Connection connection = pausedConnection();
        when(connection.channel().isActive()).thenReturn(false);
        clock.advance(READ_INTERVAL_MS);

        backpressure.readIfDue(connection.sessionId(), READ_INTERVAL_MS);

        verify(connection.channel(), never()).read();
    }

    // a read that finds nothing is just a read: the deadline probes and closes a dead client as before
    @Test
    void givenReadsThatFoundNothing_whenTheDeadlineComes_thenWaitAfterOneReadAndThenClose() {
        Connection connection = pausedConnection();
        long lastPacket = clock.millis();
        for (int i = 1; i <= 2; i++) {
            clock.advance(READ_INTERVAL_MS);
            backpressure.readIfDue(connection.sessionId(), READ_INTERVAL_MS);
            verify(connection.channel(), times(i)).read();
        }
        clock.advance(READ_INTERVAL_MS);

        assertThat(backpressure.probe(connection.sessionId(), lastPacket)).isEqualTo(KeepAliveProbe.WAIT);
        verify(connection.channel(), times(3)).read();
        clock.advance(TICK_MS);
        assertThat(backpressure.probe(connection.sessionId(), lastPacket)).isEqualTo(KeepAliveProbe.CLOSE);
        verify(connection.channel(), times(3)).read();
    }

    // a read that finds a packet makes the client alive, as the probe's own read does
    @Test
    void givenReadThatFoundAPacket_whenTheDeadlineComes_thenAlive() {
        Connection connection = pausedConnection();
        long lastPacket = clock.millis();
        clock.advance(READ_INTERVAL_MS);
        backpressure.readIfDue(connection.sessionId(), READ_INTERVAL_MS);
        backpressure.onChannelRead(connection.session());
        clock.advance(2 * READ_INTERVAL_MS);

        assertThat(backpressure.probe(connection.sessionId(), lastPacket)).isEqualTo(KeepAliveProbe.ALIVE);
        verify(connection.channel()).read();
    }

    // A probe takes the budget's lock before the session's entry lock, as a resume does. If it took them the other way
    // round, a probe holding the entry and waiting for the budget's read lock behind a queued shutdown (the write lock)
    // would wait for a resume that holds the read lock and waits for the entry: the three threads would deadlock.
    @Test
    void givenProbeResumeAndShutdownRacing_whenTheyMeetOnTheLocks_thenNoneDeadlocks() throws Exception {
        Connection connection = pausedConnection();
        long lastPacket = packetTime();
        CountDownLatch probeParked = new CountDownLatch(1);
        CountDownLatch letProbeGo = new CountDownLatch(1);
        AtomicReference<KeepAliveProbe> probeResult = new AtomicReference<>();
        Thread probeThread = Thread.ofPlatform().unstarted(() -> probeResult.set(backpressure.probe(connection.sessionId(), lastPacket)));
        budget.parkOnBudgetCall(probeThread, probeParked, letProbeGo);
        Thread resume = Thread.ofPlatform().unstarted(() -> release(3));
        Thread shutdown = Thread.ofPlatform().unstarted(budget::stopResuming);
        try {
            probeThread.start();
            assertThat(probeParked.await(10, TimeUnit.SECONDS)).isTrue();

            // the resume holds the budget's read lock and needs the session's entry; the shutdown queues for the write lock
            resume.start();
            awaitBlockedOrDone(resume);
            shutdown.start();
            awaitBlockedOrDone(shutdown);
            letProbeGo.countDown();

            probeThread.join(10_000);
            resume.join(10_000);
            shutdown.join(10_000);
            assertThat(probeThread.isAlive()).as("probe thread").isFalse();
            assertThat(resume.isAlive()).as("resume thread").isFalse();
            assertThat(shutdown.isAlive()).as("shutdown thread").isFalse();
        } finally {
            letProbeGo.countDown();
        }
        // the resume ran first and forgot the session, and the shutdown had stopped resuming when the probe went on
        assertThat(probeResult.get()).isEqualTo(KeepAliveProbe.CLOSE);
    }

    private static void awaitBlockedOrDone(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Thread.State state = thread.getState();
            if (state == Thread.State.TERMINATED || state == Thread.State.BLOCKED || state == Thread.State.WAITING) {
                return;
            }
            Thread.sleep(1);
        }
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

    private static long now() {
        return System.currentTimeMillis();
    }

    // a last packet time, then a pause, so that what follows is later than it
    private static long packetTime() {
        long time = System.currentTimeMillis();
        later();
        return time;
    }

    private static void later() {
        try {
            Thread.sleep(3);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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

        UUID sessionId() {
            return session.getSessionId();
        }

    }

    /**
     * The system clock, moved forward by what the test adds.
     */
    private static final class ForwardClock extends Clock {

        private final AtomicLong offset = new AtomicLong();

        void advance(long millis) {
            offset.addAndGet(millis);
        }

        @Override
        public long millis() {
            return System.currentTimeMillis() + offset.get();
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis());
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

    }

    /**
     * Parks one thread on its first call of {@link #runUnlessResumingStopped}, before it takes any lock of the budget.
     */
    private static class ParkingBudget extends PipelineBudget {

        private volatile Thread parkedThread;
        private volatile CountDownLatch parked;
        private volatile CountDownLatch release;

        ParkingBudget() {
            super(10, UNLIMITED_BYTES, 80, 50);
        }

        void parkOnBudgetCall(Thread thread, CountDownLatch parked, CountDownLatch release) {
            this.parkedThread = thread;
            this.parked = parked;
            this.release = release;
        }

        @Override
        public boolean runUnlessResumingStopped(Runnable action) {
            if (Thread.currentThread() == parkedThread) {
                parkedThread = null;
                parked.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return super.runUnlessResumingStopped(action);
        }

    }

}
