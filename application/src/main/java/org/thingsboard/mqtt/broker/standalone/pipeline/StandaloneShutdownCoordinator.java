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

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttDisconnectMsg;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.exception.PipelineClosedException;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCtxService;
import org.thingsboard.mqtt.broker.service.mqtt.will.LastWillService;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Stops the publish pipeline before every other bean: it stops the budget from resuming reads, stops reading from the
 * clients, publishes the Last Wills of the connected clients, then drains the dispatch stage and the persist stage in
 * that order. One deadline, {@code standalone.shutdown.drain-timeout-ms}, covers the wills and the drain: the drain gets
 * what the wills left of it.
 * <p>
 * A Will Message is published once its connection closes [MQTT-3.1.2-8]. Each client that has a Last Will is
 * disconnected with Server shutting down through its client actor, after the messages the client sent before: a
 * DISCONNECT with reason code 0x00 among them still discards the will. The actor hands a will that is due to the Last
 * Will scheduler and then closes the channel. The coordinator waits until every such channel is being closed and the
 * scheduler has handed those wills to the pipeline, so the drain delivers them. A will with a Will Delay Interval is not
 * due yet: the shutdown does not wait for it, and it is not published, since wills are not kept across a restart. The
 * clients without a will stay connected through the drain, so a subscriber still receives the wills, and are closed
 * when the servers stop.
 * <p>
 * A publish that was not dispatched in time was never acknowledged. One that reaches a drained stage fails with
 * {@link PipelineClosedException}: it is not acknowledged either, and its client stays connected until the server
 * closes the connections; a will that reaches a drained stage is dropped and counted. The final WAL sync and the close
 * of the store stay with the store, which Spring closes last because every store user depends on it.
 */
@Slf4j
@Component
@StandaloneComponent
@RequiredArgsConstructor
public class StandaloneShutdownCoordinator implements SmartLifecycle {

    private static final long DISCONNECT_POLL_NANOS = TimeUnit.MILLISECONDS.toNanos(5);

    private final ClientSessionCtxService clientSessionCtxService;
    private final ClientMqttActorManager clientMqttActorManager;
    private final LastWillService lastWillService;
    private final PipelineBudget budget;
    private final StandalonePublishMsgQueue dispatchStage;
    private final StandaloneDeviceMsgQueueConsumer persistStage;
    private final PipelineProperties properties;

    private volatile boolean running;

    @Override
    public void start() {
        running = true;
    }

    /**
     * Never throws: whatever fails is logged and the rest of the shutdown goes on.
     */
    @Override
    public void stop() {
        running = false;
        try {
            budget.stopResuming();
            pauseReading();
            long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(properties.getShutdown().getDrainTimeoutMs());
            publishWills(deadlineNanos);
            drainStages(deadlineNanos);
        } catch (Throwable t) {
            log.error("Failed to drain the publish pipeline on shutdown", t);
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * @return the highest phase, so that this bean stops before every other one
     */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    private void pauseReading() {
        for (ClientSessionCtx session : clientSessionCtxService.getAllClientSessionCtx()) {
            try {
                ChannelHandlerContext channel = session.getChannel();
                if (channel != null) {
                    channel.channel().config().setAutoRead(false);
                }
            } catch (Throwable t) {
                log.warn("[{}] Failed to stop reading from the client on shutdown", session.getClientId(), t);
            }
        }
    }

    /**
     * Never throws, and returns by the deadline.
     */
    private void publishWills(long deadlineNanos) {
        try {
            long startNanos = System.nanoTime();
            List<ClientSessionCtx> disconnecting = disconnectWillHolders();
            if (disconnecting.isEmpty()) {
                return;
            }
            int stillDisconnecting = awaitDisconnects(disconnecting, deadlineNanos);
            boolean dueWillsHandled = awaitDueWills(deadlineNanos);
            if (stillDisconnecting == 0 && dueWillsHandled) {
                log.info("Disconnected {} clients that have a Last Will in {} ms; the wills due now were handed to the publish pipeline",
                        disconnecting.size(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
            } else {
                log.warn("The Last Wills of the clients disconnected on shutdown were not all handed to the publish pipeline within {} ms. "
                                + "Clients still disconnecting: {} of {}. Due wills: {}. A will that reaches the drained pipeline is dropped and counted",
                        properties.getShutdown().getDrainTimeoutMs(), stillDisconnecting, disconnecting.size(),
                        dueWillsHandled ? "handed to the pipeline" : "not all handed to the pipeline");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while publishing the Last Wills on shutdown");
        } catch (Throwable t) {
            log.error("Failed to publish the Last Wills on shutdown", t);
        }
    }

    private List<ClientSessionCtx> disconnectWillHolders() {
        List<ClientSessionCtx> disconnecting = new ArrayList<>();
        for (ClientSessionCtx session : clientSessionCtxService.getAllClientSessionCtx()) {
            try {
                if (lastWillService.hasLastWill(session.getSessionId())) {
                    // behind what the client sent before its reads were paused: a DISCONNECT 0x00 it sent still discards the will
                    clientMqttActorManager.disconnectAfterQueuedMsgs(session.getClientId(), new MqttDisconnectMsg(session.getSessionId(),
                            new DisconnectReason(DisconnectReasonType.ON_SERVER_SHUTTING_DOWN)));
                    disconnecting.add(session);
                }
            } catch (Throwable t) {
                log.warn("[{}] Failed to disconnect the client on shutdown", session.getClientId(), t);
            }
        }
        return disconnecting;
    }

    /**
     * The client actor closes the channel at the end of the disconnect, after it has handed a will that is due to the
     * Last Will scheduler, so a session whose close was initiated has its due will scheduled.
     *
     * @return the number of sessions whose close was still not initiated at the deadline
     */
    private static int awaitDisconnects(List<ClientSessionCtx> sessions, long deadlineNanos) throws InterruptedException {
        List<ClientSessionCtx> pending = new ArrayList<>(sessions);
        while (true) {
            pending.removeIf(ClientSessionCtx::isCloseInitiated);
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (pending.isEmpty() || remainingNanos <= 0) {
                return pending.size();
            }
            TimeUnit.NANOSECONDS.sleep(Math.min(remainingNanos, DISCONNECT_POLL_NANOS));
        }
    }

    private boolean awaitDueWills(long deadlineNanos) throws Exception {
        try {
            lastWillService.dueLastWillsHandled().get(Math.max(0, deadlineNanos - System.nanoTime()), TimeUnit.NANOSECONDS);
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    private void drainStages(long deadlineNanos) {
        long timeoutMs = properties.getShutdown().getDrainTimeoutMs();
        PartitionedWorkers<?> dispatch = dispatchStage.workers();
        PartitionedWorkers<?> persist = persistStage.workers();
        long startNanos = System.nanoTime();
        boolean dispatchDrained = drain(dispatch, deadlineNanos);
        boolean persistDrained = drain(persist, deadlineNanos);
        if (dispatchDrained && persistDrained) {
            log.info("The publish pipeline drained in {} ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
        } else {
            log.warn("The publish pipeline did not drain within {} ms. Dispatch stage: {}. Persist stage: {}. "
                            + "The publishes it still held were never acknowledged",
                    timeoutMs, stageState(dispatchDrained, dispatch), stageState(persistDrained, persist));
        }
    }

    // a stage that did not drain may hold a batch in a handler as well, and the queue size does not count that
    private static String stageState(boolean drained, PartitionedWorkers<?> workers) {
        return drained ? "drained" : workers.queued() + " queued and a handler still running";
    }

    private boolean drain(PartitionedWorkers<?> workers, long deadlineNanos) {
        try {
            long remainingMs = Math.max(0, TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime()));
            return workers.drain(remainingMs);
        } catch (Throwable t) {
            log.error("Failed to drain the workers on shutdown", t);
            return false;
        }
    }

}
