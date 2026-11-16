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

import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.stats.DefaultCounter;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsType;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.service.processing.backpressure.KeepAliveProbe;
import org.thingsboard.mqtt.broker.service.processing.backpressure.PublishBackpressure;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Stops reading from a client that publishes while the pipeline is paused, from the moment it reaches its pause level
 * until it falls back to its resume level, and starts reading again from every such client when it does. A paused
 * client still publishes what was read before the pause. Once the shutdown has called
 * {@link PipelineBudget#stopResuming}, nothing here starts reading again.
 */
@Slf4j
@Component
@StandaloneComponent
public class StandalonePublishBackpressure implements PublishBackpressure {

    static final String PAUSED_CONNECTIONS_GAUGE = "pausedConnections";

    private final PipelineBudget budget;
    private final DefaultCounter publishPauses;
    // the sessions paused here; an entry and its autoRead change are made together, under the entry's lock, so a
    // resume cannot overtake a newer pause of the same session
    private final ConcurrentMap<UUID, PausedSession> pausedSessions = new ConcurrentHashMap<>();

    public StandalonePublishBackpressure(PipelineBudget budget, StatsFactory statsFactory) {
        this.budget = budget;
        this.publishPauses = statsFactory.createDefaultCounter(StatsType.PUBLISH_PAUSES.getPrintName());
        statsFactory.createGauge(PAUSED_CONNECTIONS_GAUGE, pausedSessions, Map::size);
        budget.onResume(this::resumeAll);
    }

    @Override
    public void onPublish(ClientSessionCtx ctx) {
        UUID sessionId = ctx.getSessionId();
        // Paused from the pause level all the way down to the resume level, not only at or above the pause level: a
        // client left reading in between could hold the pipeline there, and the clients already paused would never
        // resume.
        // Only the session's own actor pauses it, so the session cannot be paused between this check and the pause.
        if (!budget.paused() || pausedSessions.containsKey(sessionId)) {
            return;
        }
        pausedSessions.computeIfAbsent(sessionId, id -> {
            ctx.getChannel().channel().config().setAutoRead(false);
            publishPauses.increment();
            return new PausedSession(ctx);
        });
        // While the budget is still paused, its resume runs after the pause above and sees this session. If it is not,
        // the resume may have run between the check and the pause without seeing the session, so it is resumed here.
        budget.resumeUnlessPaused(() -> resume(sessionId));
    }

    // runs on the event loop for every inbound packet, so a session that is not paused costs one lookup at most
    @Override
    public void onChannelRead(ClientSessionCtx ctx) {
        if (pausedSessions.isEmpty()) {
            return;
        }
        PausedSession paused = pausedSessions.get(ctx.getSessionId());
        if (paused != null) {
            paused.lastReadTime = System.currentTimeMillis();
        }
    }

    /**
     * [MQTT-3.1.2-22] The keep-alive stays enforced on a paused connection, which does not read what the client sent:
     * the first probe after a packet reads once, and the next one closes the session unless data was read meanwhile.
     */
    @Override
    public KeepAliveProbe probe(UUID sessionId, long lastPacketTime) {
        if (pausedSessions.isEmpty()) {
            return KeepAliveProbe.CLOSE;
        }
        AtomicReference<KeepAliveProbe> outcome = new AtomicReference<>(KeepAliveProbe.CLOSE);
        // The budget's lock is taken before the entry's, as a resume takes them: an entry's lock held while waiting for
        // the budget's could deadlock with a resume and a shutdown queued for the write lock between them.
        boolean ran = budget.runUnlessResumingStopped(() ->
                pausedSessions.computeIfPresent(sessionId, (id, paused) -> {
                    outcome.set(probe(paused, lastPacketTime));
                    // only a resume takes a session out of the paused set: a session the keep-alive did not close
                    // after all (its refresh lost a race) must still be resumed
                    return paused;
                }));
        if (!ran) {
            // the shutdown closes the connections itself, and the reads stay paused through its drain
            return pausedSessions.containsKey(sessionId) ? KeepAliveProbe.WAIT : KeepAliveProbe.CLOSE;
        }
        return outcome.get();
    }

    // Both times are compared with the keep-alive's last packet time, on the same clock, so that data the actor already
    // counted, and a probe it already answered, do not count again for a later deadline.
    private KeepAliveProbe probe(PausedSession paused, long lastPacketTime) {
        if (paused.lastReadTime > lastPacketTime) {
            // strict: a packet read at the very time of the last packet time was most likely that packet
            return KeepAliveProbe.ALIVE;
        }
        if (paused.probeTime > lastPacketTime) {
            // probed after the last known packet, and nothing was read since
            return KeepAliveProbe.CLOSE;
        }
        // not probed yet, or the probe is not newer than the last packet (non-strict: the packet that refreshed the
        // session may have been read by that very probe), so that it belongs to an older deadline
        if (!paused.ctx.getChannel().channel().isActive()) {
            return KeepAliveProbe.CLOSE;
        }
        paused.probeTime = System.currentTimeMillis();
        read(paused.ctx);
        return KeepAliveProbe.WAIT;
    }

    private void read(ClientSessionCtx ctx) {
        try {
            ctx.getChannel().channel().read();
        } catch (Exception e) {
            // the next keep-alive check closes the session, as no data arrived
            log.warn("[{}][{}] Failed to read once from the paused client", ctx.getClientId(), ctx.getSessionId(), e);
        }
    }

    // runs on the thread that released the budget
    private void resumeAll() {
        for (UUID sessionId : pausedSessions.keySet()) {
            resume(sessionId);
        }
    }

    // a closed channel is only forgotten; a failure is logged and the session forgotten too, so the others still resume
    private void resume(UUID sessionId) {
        pausedSessions.computeIfPresent(sessionId, (id, paused) -> {
            ClientSessionCtx ctx = paused.ctx;
            try {
                Channel channel = ctx.getChannel().channel();
                if (channel.isActive()) {
                    channel.config().setAutoRead(true);
                    channel.read();
                }
            } catch (Exception e) {
                log.warn("[{}][{}] Failed to resume reading from the client", ctx.getClientId(), id, e);
            }
            return null;
        });
    }

    private static final class PausedSession {

        private final ClientSessionCtx ctx;
        // when the event loop last read a packet on the paused connection; 0 for none
        private volatile long lastReadTime;
        // when the keep-alive last read once from the paused connection; 0 for none; only touched under the entry's lock
        private long probeTime;

        private PausedSession(ClientSessionCtx ctx) {
            this.ctx = ctx;
        }

    }

}
