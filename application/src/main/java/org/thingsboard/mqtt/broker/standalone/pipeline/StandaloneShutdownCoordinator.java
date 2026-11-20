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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttDisconnectMsg;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.dao.store.BufferedStoreWrites;
import org.thingsboard.mqtt.broker.exception.PipelineClosedException;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCtxService;
import org.thingsboard.mqtt.broker.service.mqtt.will.LastWillService;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Stops the publish pipeline before every other bean: it refuses new connections with Server unavailable and waits for
 * the connections being accepted, stops the budget from resuming reads, stops reading from the clients and waits for
 * the reads in progress, publishes the Last Wills of the connected clients, then drains the dispatch stage and the
 * persist stage in that order. One deadline, {@code standalone.shutdown.drain-timeout-ms}, covers these waits: the drain
 * gets what the others left of it.
 * <p>
 * The listeners stay open until the servers stop, after this bean. A client accepted after the sessions were taken
 * would be neither paused nor disconnected, and its Last Will would be lost, so a CONNECT is refused from the first
 * step on, and one admitted before it is waited for until its session is registered or it is disconnected.
 * <p>
 * A Will Message is published once its connection closes [MQTT-3.1.2-8]. Each client that has a Last Will is
 * disconnected with Server shutting down through its client actor, after the messages the client sent before: a
 * DISCONNECT with reason code 0x00 among them still discards the will. The actor hands a will that is due to the Last
 * Will scheduler and only then takes it from the will store. The coordinator waits until the disconnect has taken each
 * will from the store — a due will is on the scheduler by then — and until the scheduler has handed those wills to the
 * pipeline, so the drain delivers them. A will whose delay has not passed is not
 * due yet (the delay is the lesser of the Will Delay Interval and the session expiry, so a will of a session that
 * expires at once is due): the shutdown does not wait for it, and it is not published, since wills are not kept across
 * a restart. The clients without a will stay connected through the drain, so a subscriber still receives the wills, and
 * are closed when the servers stop.
 * <p>
 * A publish that was not dispatched in time was never acknowledged. One that reaches a drained stage fails with
 * {@link PipelineClosedException}: it is not acknowledged either, and its client stays connected until the server
 * closes the connections; a will that reaches a drained stage is dropped and counted.
 * <p>
 * After the drain, the writes the store keeps in memory ({@link BufferedStoreWrites}, the per-client latest stats) are
 * flushed, always, even once the deadline has passed: each flush is one batch. A shutdown cut short before it loses
 * them as a kill does, at most one flush interval. The final WAL sync and the close of the store stay with the store,
 * which Spring closes after every bean has stopped, and last of the beans because every store user depends on it.
 */
@Slf4j
@Component
@StandaloneComponent
@RequiredArgsConstructor
public class StandaloneShutdownCoordinator implements SmartLifecycle {

    private static final long POLL_NANOS = TimeUnit.MILLISECONDS.toNanos(5);

    private final ClientSessionCtxService clientSessionCtxService;
    private final ClientMqttActorManager clientMqttActorManager;
    private final LastWillService lastWillService;
    private final PipelineBudget budget;
    private final StandalonePublishMsgQueue dispatchStage;
    private final StandaloneDeviceMsgQueueConsumer persistStage;
    private final PipelineProperties properties;
    private final StandaloneConnectionAdmission admission;
    private final List<BufferedStoreWrites> bufferedWrites;

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
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(properties.getShutdown().getDrainTimeoutMs());
        try {
            admission.close();
            budget.stopResuming();
            awaitConnectsInProgress(deadlineNanos);
            Set<EventLoop> eventLoops = pauseReading();
            awaitReadsInProgress(eventLoops, deadlineNanos);
            publishWills(deadlineNanos);
            drainStages(deadlineNanos);
        } catch (Throwable t) {
            log.error("Failed to drain the publish pipeline on shutdown", t);
        }
        flushBufferedWrites(deadlineNanos);
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

    /**
     * Waits, within the deadline, for the CONNECTs admitted before new ones were refused: each ends once its session is
     * registered, so the steps after this one find it, or once it is disconnected. Never throws, and returns by the
     * deadline.
     */
    private void awaitConnectsInProgress(long deadlineNanos) {
        try {
            while (true) {
                int inFlight = admission.inFlight();
                long remainingNanos = deadlineNanos - System.nanoTime();
                if (inFlight == 0) {
                    return;
                }
                if (remainingNanos <= 0) {
                    log.warn("The connections being accepted on shutdown did not finish within {} ms: {} still being accepted; "
                            + "one of them with a Last Will may lose it", properties.getShutdown().getDrainTimeoutMs(), inFlight);
                    return;
                }
                TimeUnit.NANOSECONDS.sleep(Math.min(remainingNanos, POLL_NANOS));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for the connections being accepted on shutdown");
        } catch (Throwable t) {
            log.error("Failed to wait for the connections being accepted on shutdown", t);
        }
    }

    /**
     * @return the event loops of the paused channels
     */
    private Set<EventLoop> pauseReading() {
        Set<EventLoop> eventLoops = new HashSet<>();
        for (ClientSessionCtx session : clientSessionCtxService.getAllClientSessionCtx()) {
            try {
                ChannelHandlerContext channelCtx = session.getChannel();
                if (channelCtx != null) {
                    Channel channel = channelCtx.channel();
                    channel.config().setAutoRead(false);
                    EventLoop eventLoop = channel.eventLoop();
                    if (eventLoop != null) {
                        eventLoops.add(eventLoop);
                    }
                }
            } catch (Throwable t) {
                log.warn("[{}] Failed to stop reading from the client on shutdown", session.getClientId(), t);
            }
        }
        return eventLoops;
    }

    /**
     * Runs an empty task on each event loop and waits for them, within the deadline. A read that was in progress when
     * reading was paused runs on the event loop before that task, and {@code channelRead} tells the client actor
     * there, so every packet it decoded is in the actor's mailbox once the task has run. So the disconnects of the will
     * step are queued behind what the clients had sent when reading was paused. Two reads can still happen after the
     * task has run. A keep-alive read that was issued before the budget stopped resuming, on a socket that had nothing
     * to read, stays pending and can take one buffer. And on a channel with auto-read off, Netty's
     * {@code ByteToMessageDecoder.channelReadComplete} still calls {@code ctx.read()} when a read decoded no complete
     * message, such as a packet cut at the end of the buffer. Both only bring in bytes the server had not read when the
     * shutdown began, so a packet read that late may reach the actor behind the disconnect. Never throws, and returns
     * by the deadline.
     */
    private void awaitReadsInProgress(Set<EventLoop> eventLoops, long deadlineNanos) {
        if (eventLoops.isEmpty()) {
            return;
        }
        try {
            List<Future<?>> fences = new ArrayList<>(eventLoops.size());
            int notFinished = 0;
            for (EventLoop eventLoop : eventLoops) {
                try {
                    fences.add(eventLoop.submit(() -> {
                    }));
                } catch (Throwable t) {
                    log.warn("Failed to wait for the reads in progress on an event loop on shutdown", t);
                    notFinished++;
                }
            }
            for (Future<?> fence : fences) {
                if (!fence.await(Math.max(0, deadlineNanos - System.nanoTime()), TimeUnit.NANOSECONDS)) {
                    notFinished++;
                }
            }
            if (notFinished > 0) {
                log.warn("The reads in progress on {} of {} event loops did not finish within {} ms: "
                                + "a packet they decode may reach its client actor after the shutdown disconnect",
                        notFinished, eventLoops.size(), properties.getShutdown().getDrainTimeoutMs());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for the reads in progress on shutdown");
        } catch (Throwable t) {
            log.error("Failed to wait for the reads in progress on shutdown", t);
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
     * The disconnect takes the will from the will store only after it has handed a due will to the Last Will
     * scheduler, so a session that no longer has a will has its due will scheduled. A closed channel says nothing
     * about the will: the client can close it first.
     *
     * @return the number of sessions that still had a will at the deadline
     */
    private int awaitDisconnects(List<ClientSessionCtx> sessions, long deadlineNanos) throws InterruptedException {
        List<ClientSessionCtx> pending = new ArrayList<>(sessions);
        while (true) {
            pending.removeIf(session -> !lastWillService.hasLastWill(session.getSessionId()));
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (pending.isEmpty() || remainingNanos <= 0) {
                return pending.size();
            }
            TimeUnit.NANOSECONDS.sleep(Math.min(remainingNanos, POLL_NANOS));
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

    /**
     * Never throws: a flush that fails is logged and the next one still runs.
     */
    private void flushBufferedWrites(long deadlineNanos) {
        for (BufferedStoreWrites writes : bufferedWrites) {
            String name = writes.getClass().getSimpleName();
            try {
                long startNanos = System.nanoTime();
                writes.flush();
                long endNanos = System.nanoTime();
                if (endNanos - deadlineNanos > 0) {
                    log.warn("The flush of {} on shutdown took {} ms and ended {} ms after the drain deadline", name,
                            TimeUnit.NANOSECONDS.toMillis(endNanos - startNanos), TimeUnit.NANOSECONDS.toMillis(endNanos - deadlineNanos));
                }
            } catch (Throwable t) {
                log.error("Failed to flush {} on shutdown", name, t);
            }
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
