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
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCtxService;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.concurrent.TimeUnit;

/**
 * Stops the publish pipeline before every other bean: it stops reading from the clients, then drains the dispatch
 * stage and the persist stage in that order within one deadline, {@code standalone.shutdown.drain-timeout-ms}. A
 * publish that was not dispatched in time was never acknowledged. The final WAL sync and the close of the store stay
 * with the store, which Spring closes last because every store user depends on it.
 */
@Slf4j
@Component
@StandaloneComponent
@RequiredArgsConstructor
public class StandaloneShutdownCoordinator implements SmartLifecycle {

    private final ClientSessionCtxService clientSessionCtxService;
    private final StandalonePublishMsgConsumerService dispatchStage;
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
            pauseReading();
            drainStages();
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

    private void drainStages() {
        long timeoutMs = properties.getShutdown().getDrainTimeoutMs();
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        PartitionedWorkers<?> dispatch = dispatchStage.workers();
        PartitionedWorkers<?> persist = persistStage.workers();
        boolean dispatchDrained = drain(dispatch, deadlineNanos);
        boolean persistDrained = drain(persist, deadlineNanos);
        if (!dispatchDrained || !persistDrained) {
            log.warn("The publish pipeline did not drain within {} ms: {} dispatch and {} persist items were left unprocessed "
                    + "(batches in progress not counted); they were never acknowledged", timeoutMs, dispatch.queued(), persist.queued());
        }
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
