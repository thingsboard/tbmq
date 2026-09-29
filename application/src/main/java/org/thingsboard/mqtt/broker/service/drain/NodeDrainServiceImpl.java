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
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
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

import static org.thingsboard.mqtt.broker.session.DisconnectReasonType.ON_USE_ANOTHER_SERVER;

@Slf4j
@Service
public class NodeDrainServiceImpl implements NodeDrainService {

    private final ClientSessionCtxService sessionCtxService;
    private final ClientMqttActorManager clientMqttActorManager;
    private final ServiceInfoProvider serviceInfoProvider;
    private final NodeDrainSettings settings;
    private final ScheduledExecutorService executor;
    private final AtomicReference<NodeDrainState> state = new AtomicReference<>(NodeDrainState.ACTIVE);
    private final AtomicInteger initialSessions = new AtomicInteger();
    private final AtomicInteger disconnectRequests = new AtomicInteger();
    private final AtomicLong startedAt = new AtomicLong();
    private final AtomicLong completedAt = new AtomicLong();
    private final AtomicLong operationGeneration = new AtomicLong();
    private Iterator<ClientSessionCtx> sessionsToDrain;

    public NodeDrainServiceImpl(ClientSessionCtxService sessionCtxService,
                                ClientMqttActorManager clientMqttActorManager,
                                ServiceInfoProvider serviceInfoProvider,
                                NodeDrainSettings settings) {
        this.sessionCtxService = sessionCtxService;
        this.clientMqttActorManager = clientMqttActorManager;
        this.serviceInfoProvider = serviceInfoProvider;
        this.settings = settings;
        this.executor = Executors.newSingleThreadScheduledExecutor(ThingsBoardThreadFactory.forName("node-drain"));
    }

    @Override
    public synchronized NodeDrainStatus startDrain() {
        if (!state.compareAndSet(NodeDrainState.ACTIVE, NodeDrainState.DRAINING)) {
            return getStatus();
        }

        long now = System.currentTimeMillis();
        long generation = operationGeneration.incrementAndGet();
        startedAt.set(now);
        completedAt.set(0);
        disconnectRequests.set(0);
        sessionsToDrain = null;
        int sessionCount = sessionCtxService.getSessionsCount();
        initialSessions.set(sessionCount);
        log.info("Starting node drain with {} active MQTT sessions", sessionCount);

        executor.schedule(() -> drainBatchSafely(generation),
                Math.max(0, settings.getLoadBalancerWaitMs()), TimeUnit.MILLISECONDS);
        return status(sessionCount);
    }

    @Override
    public synchronized NodeDrainStatus cancelDrain() {
        operationGeneration.incrementAndGet();
        state.set(NodeDrainState.ACTIVE);
        sessionsToDrain = null;
        initialSessions.set(0);
        disconnectRequests.set(0);
        startedAt.set(0);
        completedAt.set(0);
        log.info("Node drain cancelled; accepting new MQTT connections");
        return status(sessionCtxService.getSessionsCount());
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
        return new NodeDrainStatus(serviceInfoProvider.getServiceId(), state.get(), initialSessions.get(),
                remainingSessions, disconnectRequests.get(), startedAt.get(), completedAt.get());
    }

    private void drainBatchSafely(long generation) {
        try {
            drainBatch(generation);
        } catch (Throwable t) {
            log.error("Unexpected node drain failure", t);
            scheduleNextBatch(generation);
        }
    }

    private synchronized void drainBatch(long generation) {
        if (!isCurrentDrain(generation)) {
            return;
        }

        int remainingSessions = sessionCtxService.getSessionsCount();
        if (remainingSessions == 0) {
            complete(NodeDrainState.DRAINED, generation);
            return;
        }
        if (System.currentTimeMillis() - startedAt.get() >= Math.max(1, settings.getTimeoutMs())) {
            complete(NodeDrainState.TIMED_OUT, generation);
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
                        new DisconnectReason(ON_USE_ANOTHER_SERVER)));
                disconnectRequests.incrementAndGet();
                submitted++;
            } catch (RuntimeException e) {
                log.warn("[{}][{}] Failed to request disconnect during node drain",
                        session.getClientId(), session.getSessionId(), e);
            }
        }
        if (!sessionsToDrain.hasNext()) {
            sessionsToDrain = null;
        }
        log.debug("Node drain requested {} disconnects in this batch; {} sessions remain", submitted, remainingSessions);
        scheduleNextBatch(generation);
    }

    private void scheduleNextBatch(long generation) {
        if (isCurrentDrain(generation)) {
            try {
                executor.schedule(() -> drainBatchSafely(generation),
                        Math.max(1, settings.getBatchIntervalMs()), TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException e) {
                if (!executor.isShutdown()) {
                    throw e;
                }
            }
        }
    }

    private boolean isCurrentDrain(long generation) {
        return state.get() == NodeDrainState.DRAINING && operationGeneration.get() == generation;
    }

    private void complete(NodeDrainState terminalState, long generation) {
        if (operationGeneration.get() == generation && state.compareAndSet(NodeDrainState.DRAINING, terminalState)) {
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
