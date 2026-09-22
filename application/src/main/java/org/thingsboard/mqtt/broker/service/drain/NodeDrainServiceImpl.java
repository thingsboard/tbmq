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
package org.thingsboard.mqtt.broker.service.drain;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttDisconnectMsg;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCtxService;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;

import java.util.Iterator;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.thingsboard.mqtt.broker.session.DisconnectReasonType.ON_SERVER_SHUTTING_DOWN;

@Slf4j
@Service
public class NodeDrainServiceImpl implements NodeDrainService {

    private final ClientSessionCtxService sessionCtxService;
    private final ClientMqttActorManager clientMqttActorManager;
    private final NodeDrainSettings settings;
    private final ScheduledExecutorService executor;
    private final AtomicReference<NodeDrainState> state = new AtomicReference<>(NodeDrainState.ACTIVE);
    private final AtomicInteger initialSessions = new AtomicInteger();
    private final AtomicInteger disconnectRequests = new AtomicInteger();
    private final AtomicLong startedAt = new AtomicLong();
    private final AtomicLong completedAt = new AtomicLong();
    private Iterator<ClientSessionCtx> sessionsToDrain;

    public NodeDrainServiceImpl(ClientSessionCtxService sessionCtxService,
                                ClientMqttActorManager clientMqttActorManager,
                                NodeDrainSettings settings) {
        this.sessionCtxService = sessionCtxService;
        this.clientMqttActorManager = clientMqttActorManager;
        this.settings = settings;
        this.executor = Executors.newSingleThreadScheduledExecutor(ThingsBoardThreadFactory.forName("node-drain"));
    }

    @Override
    public NodeDrainStatus startDrain() {
        if (!state.compareAndSet(NodeDrainState.ACTIVE, NodeDrainState.DRAINING)) {
            return getStatus();
        }

        long now = System.currentTimeMillis();
        startedAt.set(now);
        int sessionCount = sessionCtxService.getSessionsCount();
        initialSessions.set(sessionCount);
        log.info("Starting node drain with {} active MQTT sessions", sessionCount);

        executor.schedule(this::drainBatchSafely, Math.max(0, settings.getLoadBalancerWaitMs()), TimeUnit.MILLISECONDS);
        return status(sessionCount);
    }

    @Override
    public NodeDrainStatus getStatus() {
        return status(sessionCtxService.getSessionsCount());
    }

    @Override
    public boolean isDraining() {
        return state.get() != NodeDrainState.ACTIVE;
    }

    private NodeDrainStatus status(int remainingSessions) {
        return new NodeDrainStatus(state.get(), initialSessions.get(), remainingSessions, disconnectRequests.get(),
                startedAt.get(), completedAt.get());
    }

    private void drainBatchSafely() {
        try {
            drainBatch();
        } catch (Throwable t) {
            log.error("Unexpected node drain failure", t);
            scheduleNextBatch();
        }
    }

    private void drainBatch() {
        if (state.get() != NodeDrainState.DRAINING) {
            return;
        }

        int remainingSessions = sessionCtxService.getSessionsCount();
        if (remainingSessions == 0) {
            complete(NodeDrainState.DRAINED);
            return;
        }
        if (System.currentTimeMillis() - startedAt.get() >= Math.max(1, settings.getTimeoutMs())) {
            complete(NodeDrainState.TIMED_OUT);
            return;
        }

        if (sessionsToDrain == null) {
            sessionsToDrain = sessionCtxService.getAllClientSessionCtx().iterator();
        }

        int batchSize = Math.max(1, settings.getBatchSize());
        int processed = 0;
        int submitted = 0;
        while (processed < batchSize && sessionsToDrain.hasNext()) {
            ClientSessionCtx session = sessionsToDrain.next();
            processed++;
            try {
                clientMqttActorManager.disconnect(session.getClientId(), new MqttDisconnectMsg(session.getSessionId(),
                        new DisconnectReason(ON_SERVER_SHUTTING_DOWN)));
                disconnectRequests.incrementAndGet();
                submitted++;
            } catch (RuntimeException e) {
                log.warn("[{}][{}] Failed to request disconnect during node drain",
                        session.getClientId(), session.getSessionId(), e);
            }
        }
        log.debug("Node drain requested {} disconnects in this batch; {} sessions remain", submitted, remainingSessions);
        scheduleNextBatch();
    }

    private void scheduleNextBatch() {
        if (state.get() == NodeDrainState.DRAINING) {
            try {
                executor.schedule(this::drainBatchSafely, Math.max(1, settings.getBatchIntervalMs()), TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException e) {
                if (!executor.isShutdown()) {
                    throw e;
                }
            }
        }
    }

    private void complete(NodeDrainState terminalState) {
        if (state.compareAndSet(NodeDrainState.DRAINING, terminalState)) {
            completedAt.set(System.currentTimeMillis());
            log.info("Node drain finished with state {}. Requested disconnects: {}, remaining sessions: {}",
                    terminalState, disconnectRequests.get(), sessionCtxService.getSessionsCount());
        }
    }

    @PreDestroy
    public void destroy() {
        executor.shutdownNow();
    }

}
