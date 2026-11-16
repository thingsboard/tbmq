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

/**
 * Stops reading from a client that publishes while the pipeline is at its pause level, and starts reading again from
 * every such client when the pipeline falls to its resume level. A paused client still publishes what was read before
 * the pause. Once the shutdown has called {@link PipelineBudget#stopResuming}, nothing here starts reading again.
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
    private final ConcurrentMap<UUID, ClientSessionCtx> pausedSessions = new ConcurrentHashMap<>();

    public StandalonePublishBackpressure(PipelineBudget budget, StatsFactory statsFactory) {
        this.budget = budget;
        this.publishPauses = statsFactory.createDefaultCounter(StatsType.PUBLISH_PAUSES.getPrintName());
        statsFactory.createGauge(PAUSED_CONNECTIONS_GAUGE, pausedSessions, Map::size);
        budget.onResume(this::resumeAll);
    }

    @Override
    public void onPublish(ClientSessionCtx ctx) {
        UUID sessionId = ctx.getSessionId();
        // only the session's own actor pauses it, so the session cannot be paused between this check and the pause
        if (!budget.abovePause() || pausedSessions.containsKey(sessionId)) {
            return;
        }
        pausedSessions.computeIfAbsent(sessionId, id -> {
            ctx.getChannel().channel().config().setAutoRead(false);
            publishPauses.increment();
            return ctx;
        });
        // While the budget is still paused, its resume runs after the pause above and sees this session. If it is not,
        // the resume may have run between the check and the pause without seeing the session, so it is resumed here.
        budget.resumeUnlessPaused(() -> resume(sessionId));
    }

    @Override
    public void onChannelRead(ClientSessionCtx ctx) {
    }

    @Override
    public KeepAliveProbe probe(UUID sessionId) {
        return KeepAliveProbe.CLOSE;
    }

    // runs on the thread that released the budget
    private void resumeAll() {
        for (UUID sessionId : pausedSessions.keySet()) {
            resume(sessionId);
        }
    }

    // a closed channel is only forgotten; a failure is logged and the session forgotten too, so the others still resume
    private void resume(UUID sessionId) {
        pausedSessions.computeIfPresent(sessionId, (id, ctx) -> {
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

}
